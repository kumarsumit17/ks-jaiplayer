// Feasibility trial only (specs/plan-macos-playback.md, "Native video layer trial"): never part
// of an installer. A native video layer under the Compose window, fed by libVLC's video callbacks
// as C functions (no JVM work per frame), built from Apple's documentation.
//
// Route A: libVLC writes each frame into an IOSurface-backed CVPixelBuffer (NV12 or P010) tagged
// with the file's colours and HDR metadata; an AVSampleBufferDisplayLayer shows it, so macOS
// does the colour conversion, HDR (EDR) and tone mapping.
//
// Route B (JAIPLAYER_VIDEO_LAYER=metal): the same buffers as Metal textures (no copy), turned into
// R'G'B' by our shader on a CAMetalLayer in the video's own colour space (BT.2100 PQ or HLG for
// HDR) with EDR metadata, so macOS maps it to the display's headroom.
//
// Route C (JAIPLAYER_VIDEO_LAYER=vout): no callbacks; libVLC's own caopengllayer video output
// draws VideoToolbox frames itself (glconv_cvpx, no copy) into a layer handed to it through
// libvlc_media_player_set_nsobject, placed under the Compose window.

#import <AppKit/AppKit.h>
#import <AVFoundation/AVFoundation.h>
#import <CoreMedia/CoreMedia.h>
#import <CoreVideo/CoreVideo.h>
#import <QuartzCore/QuartzCore.h>
#import <Metal/Metal.h>
#include <stdatomic.h>
#include <objc/runtime.h>
#include <mach/mach_time.h>
#include <math.h>

typedef struct { CVPixelBufferRef buffer; int locked; } JvlPicture;

/** libVLC's lock/unlock/display order differs by case, so pictures live in a ring: a slot's buffer is let go only when the slot comes round again. */
#define SLOTS 8

typedef struct {
    int route; // 1: AVSampleBufferDisplayLayer, 2: CAMetalLayer, 3: libVLC's own layer
    id voutHost;
    CALayer *layer;
    AVSampleBufferDisplayLayer *sb;
    CAMetalLayer *metal;
    id<MTLDevice> device;
    id<MTLCommandQueue> queue;
    id<MTLRenderPipelineState> pipe;
    CVMetalTextureCacheRef cache;
    int metalColour; // the colour state the Metal layer is set up for
    JvlPicture slots[SLOTS];
    unsigned next;
    CVPixelBufferPoolRef pool;
    CMVideoFormatDescriptionRef desc;
    int width, height;
    int primaries, transfer, matrix, fullRange;
    CFDataRef mastering, lightLevel;
    _Atomic long decoded, displayed, failed;
    // Intervals between frames handed to the screen, since the last jvl_stats (microseconds).
    uint64_t lastShown; long intervals; double sum, sumSq, maxInterval;
    int fill, visible;
} JVL;

static void onMain(dispatch_block_t b) {
    if ([NSThread isMainThread]) b(); else dispatch_async(dispatch_get_main_queue(), b);
}

static void dump(CALayer *l, int depth) {
    NSRect f = l.frame;
    fprintf(stderr, "jvl %*s%s opaque=%d hidden=%d frame=%.0fx%.0f+%.0f+%.0f\n", depth * 2, "",
            class_getName([l class]), l.opaque, l.hidden, f.size.width, f.size.height, f.origin.x, f.origin.y);
    for (CALayer *s in l.sublayers) dump(s, depth + 1);
}

/** The app's main window: the visible one with the largest content. */
static NSWindow *mainWindow(void) {
    NSWindow *best = nil;
    for (NSWindow *w in NSApp.windows) {
        if (!w.visible || [w isKindOfClass:[NSPanel class]]) continue;
        if (!best || w.frame.size.width * w.frame.size.height > best.frame.size.width * best.frame.size.height) best = w;
    }
    return best;
}

/** Puts the video layer directly under the layer Compose draws into, and makes that one see-through. */
static void attach(JVL *v) {
    NSWindow *w = mainWindow();
    CALayer *root = w.contentView.layer;
    if (!root) {
        // The window is not up yet: try again shortly (main thread).
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 200 * NSEC_PER_MSEC), dispatch_get_main_queue(), ^{ if (!v->layer.superlayer) attach(v); });
        return;
    }
    if (getenv("JAIPLAYER_VIDEO_LAYER_DUMP")) dump(root, 0);
    // Compose (Skiko) draws into a CAMetalLayer of its own class; insert below it.
    CALayer *compose = nil;
    NSMutableArray *stack = [NSMutableArray arrayWithObject:root];
    while (stack.count && !compose) {
        CALayer *l = stack.lastObject; [stack removeLastObject];
        if ([l isKindOfClass:[CAMetalLayer class]] && strstr(class_getName([l class]), "AWTMetalLayer")) compose = l;
        for (CALayer *s in l.sublayers) [stack addObject:s];
    }
    CALayer *parent = compose ? compose.superlayer : root;
    [CATransaction begin];
    [CATransaction setDisableActions:YES];
    v->layer.frame = parent.bounds;
    v->layer.autoresizingMask = kCALayerWidthSizable | kCALayerHeightSizable;
    if (compose) {
        [parent insertSublayer:v->layer below:compose];
        compose.opaque = NO;
    } else {
        [parent insertSublayer:v->layer atIndex:0];
    }
    [CATransaction commit];
    fprintf(stderr, "jvl: attached under %s\n", compose ? "the Compose layer" : "the window layer");
}

// ---- Route B: Metal ----

static NSString *const SHADER = @
"#include <metal_stdlib>\n"
"using namespace metal;\n"
"struct V { float4 pos [[position]]; float2 uv; };\n"
"vertex V vs(uint id [[vertex_id]]) {\n"
"  float2 p = float2((id << 1) & 2, id & 2); V o; o.pos = float4(p * 2.0 - 1.0, 0.0, 1.0); o.pos.y = -o.pos.y; o.uv = p; return o; }\n"
"struct U { float4 range; float4 matrix; };\n"
"fragment float4 fs(V in [[stage_in]], texture2d<float> luma [[texture(0)]], texture2d<float> chroma [[texture(1)]], constant U &u [[buffer(0)]]) {\n"
"  constexpr sampler s(filter::linear, address::clamp_to_edge);\n"
"  float y = (luma.sample(s, in.uv).r - u.range.x) * u.range.y;\n"
"  float2 c = (chroma.sample(s, in.uv).rg - u.range.z) * u.range.w;\n"
"  float3 rgb = float3(y + u.matrix.x * c.y, y - u.matrix.y * c.x - u.matrix.z * c.y, y + u.matrix.w * c.x);\n"
"  return float4(saturate(rgb), 1.0); }\n";

static void setupMetal(JVL *v) {
    v->device = MTLCreateSystemDefaultDevice();
    v->queue = [v->device newCommandQueue];
    NSError *err = nil;
    id<MTLLibrary> lib = [v->device newLibraryWithSource:SHADER options:nil error:&err];
    if (!lib) { fprintf(stderr, "jvl: shader: %s\n", err.description.UTF8String); return; }
    MTLRenderPipelineDescriptor *d = [MTLRenderPipelineDescriptor new];
    d.vertexFunction = [lib newFunctionWithName:@"vs"];
    d.fragmentFunction = [lib newFunctionWithName:@"fs"];
    d.colorAttachments[0].pixelFormat = MTLPixelFormatRGBA16Float;
    v->pipe = [v->device newRenderPipelineStateWithDescriptor:d error:&err];
    CVMetalTextureCacheCreate(NULL, NULL, v->device, NULL, &v->cache);
    v->metal = [CAMetalLayer layer];
    v->metal.device = v->device;
    v->metal.pixelFormat = MTLPixelFormatRGBA16Float;
    v->metal.framebufferOnly = YES;
    v->metal.contentsGravity = kCAGravityResizeAspect;
    v->metal.opaque = YES;
    v->metal.maximumDrawableCount = 3;
    v->layer = v->metal;
}

/** The layer's colour space and EDR metadata for the file: PQ or HLG (BT.2100) for HDR, else SDR. */
static void colourMetal(JVL *v, int uhd) {
    int trc = v->transfer, prim = v->primaries;
    int state = trc * 1000 + prim + (v->mastering ? 100000 : 0);
    if (state == v->metalColour) return;
    v->metalColour = state;
    CGColorSpaceRef space;
    CAEDRMetadata *edr = nil;
    if (trc == 16) {
        space = CGColorSpaceCreateWithName(kCGColorSpaceITUR_2100_PQ);
        edr = v->mastering
            ? [CAEDRMetadata HDR10MetadataWithDisplayInfo:(__bridge NSData *)v->mastering contentInfo:(__bridge NSData *)v->lightLevel opticalOutputScale:100]
            : [CAEDRMetadata HDR10MetadataWithMinLuminance:0.005 maxLuminance:1000 opticalOutputScale:100];
    } else if (trc == 18) {
        space = CGColorSpaceCreateWithName(kCGColorSpaceITUR_2100_HLG);
        edr = [CAEDRMetadata HLGMetadata];
    } else {
        space = CGColorSpaceCreateWithName(prim == 9 || (prim == 0 && uhd) ? kCGColorSpaceITUR_2020 : kCGColorSpaceITUR_709);
    }
    v->metal.wantsExtendedDynamicRangeContent = edr != nil;
    v->metal.EDRMetadata = edr;
    v->metal.colorspace = space;
    CGColorSpaceRelease(space);
    fprintf(stderr, "jvl: metal layer transfer %d primaries %d edr %d\n", trc, prim, edr != nil);
}

static void drawMetal(JVL *v, CVPixelBufferRef b) {
    if (!v->pipe) return;
    OSType f = CVPixelBufferGetPixelFormatType(b);
    BOOL ten = f == kCVPixelFormatType_420YpCbCr10BiPlanarVideoRange || f == kCVPixelFormatType_420YpCbCr10BiPlanarFullRange;
    BOOL full = f == kCVPixelFormatType_420YpCbCr10BiPlanarFullRange || f == kCVPixelFormatType_420YpCbCr8BiPlanarFullRange;
    int w = (int)CVPixelBufferGetWidth(b), h = (int)CVPixelBufferGetHeight(b);
    BOOL uhd = ten && (w >= 3840 || h >= 2160);
    colourMetal(v, uhd);
    CVMetalTextureRef ty = NULL, tc = NULL;
    CVMetalTextureCacheCreateTextureFromImage(NULL, v->cache, b, NULL, ten ? MTLPixelFormatR16Unorm : MTLPixelFormatR8Unorm, w, h, 0, &ty);
    CVMetalTextureCacheCreateTextureFromImage(NULL, v->cache, b, NULL, ten ? MTLPixelFormatRG16Unorm : MTLPixelFormatRG8Unorm, (w + 1) / 2, (h + 1) / 2, 1, &tc);
    if (!ty || !tc) { if (ty) CFRelease(ty); if (tc) CFRelease(tc); v->failed++; return; }
    struct { float range[4]; float matrix[4]; } u;
    if (ten) {
        float r[4] = { full ? 0 : 4096.f / 65535, full ? 65535.f / 65472 : 65535.f / (876 * 64), 32768.f / 65535, full ? 65535.f / 65472 : 65535.f / (896 * 64) };
        memcpy(u.range, r, sizeof r);
    } else {
        float r[4] = { full ? 0 : 16.f / 255, full ? 1 : 255.f / 219, 128.f / 255, full ? 1 : 255.f / 224 };
        memcpy(u.range, r, sizeof r);
    }
    int mtx = v->matrix;
    if (mtx == 0 || mtx == 2) mtx = uhd ? 9 : (h >= 720 ? 1 : 6);
    float bt2020[4] = { 1.4746f, 0.16455f, 0.57135f, 1.8814f }, bt709[4] = { 1.5748f, 0.18732f, 0.46812f, 1.8556f }, bt601[4] = { 1.402f, 0.34414f, 0.71414f, 1.772f };
    memcpy(u.matrix, mtx == 9 || mtx == 10 ? bt2020 : mtx == 1 ? bt709 : bt601, sizeof u.matrix);
    if (v->metal.drawableSize.width != w || v->metal.drawableSize.height != h) v->metal.drawableSize = CGSizeMake(w, h);
    id<CAMetalDrawable> drawable = [v->metal nextDrawable];
    if (!drawable) { CFRelease(ty); CFRelease(tc); v->failed++; return; }
    MTLRenderPassDescriptor *pass = [MTLRenderPassDescriptor renderPassDescriptor];
    pass.colorAttachments[0].texture = drawable.texture;
    pass.colorAttachments[0].loadAction = MTLLoadActionDontCare;
    pass.colorAttachments[0].storeAction = MTLStoreActionStore;
    id<MTLCommandBuffer> cmd = [v->queue commandBuffer];
    id<MTLRenderCommandEncoder> enc = [cmd renderCommandEncoderWithDescriptor:pass];
    [enc setRenderPipelineState:v->pipe];
    [enc setFragmentTexture:CVMetalTextureGetTexture(ty) atIndex:0];
    [enc setFragmentTexture:CVMetalTextureGetTexture(tc) atIndex:1];
    [enc setFragmentBytes:&u length:sizeof u atIndex:0];
    [enc drawPrimitives:MTLPrimitiveTypeTriangle vertexStart:0 vertexCount:3];
    [enc endEncoding];
    // The textures (and the pixel buffer under them) stay alive until the GPU is done.
    CVPixelBufferRetain(b);
    [cmd addCompletedHandler:^(id<MTLCommandBuffer> c) { CFRelease(ty); CFRelease(tc); CVPixelBufferRelease(b); }];
    [cmd presentDrawable:drawable];
    [cmd commit];
    v->displayed++;
}

/**
 * libVLC 3 accepts a drawable that is an NSView or adopts this protocol (matched by name at run
 * time); its caopengllayer output then calls addVoutLayer:/removeVoutLayer: when they exist.
 */
@protocol VLCOpenGLVideoViewEmbedding <NSObject>
- (void)addVoutSubview:(NSView *)view;
- (void)removeVoutSubview:(NSView *)view;
@end

@interface JvlVoutHost : NSObject <VLCOpenGLVideoViewEmbedding>
@property(nonatomic, strong) CALayer *host;
@property(nonatomic, strong) NSView *view;
@end

/** The view has no window, so it would think the screen is 1×; trial only: answer in the screen's scale. */
static NSSize backingSize(id self, SEL _cmd, NSSize s) { CGFloat k = NSScreen.mainScreen.backingScaleFactor; return NSMakeSize(s.width * k, s.height * k); }
static NSRect backingRect(id self, SEL _cmd, NSRect r) { CGFloat k = NSScreen.mainScreen.backingScaleFactor; return NSMakeRect(r.origin.x * k, r.origin.y * k, r.size.width * k, r.size.height * k); }
static void retina(Class c) {
    Method ms = class_getInstanceMethod(c, @selector(convertSizeToBacking:));
    Method mr = class_getInstanceMethod(c, @selector(convertRectToBacking:));
    if (!class_addMethod(c, @selector(convertSizeToBacking:), (IMP)backingSize, method_getTypeEncoding(ms))) method_setImplementation(class_getInstanceMethod(c, @selector(convertSizeToBacking:)), (IMP)backingSize);
    if (!class_addMethod(c, @selector(convertRectToBacking:), (IMP)backingRect, method_getTypeEncoding(mr))) method_setImplementation(class_getInstanceMethod(c, @selector(convertRectToBacking:)), (IMP)backingRect);
}

@implementation JvlVoutHost
- (void)addVoutLayer:(CALayer *)l {
    CALayer *host = self.host;
    dispatch_async(dispatch_get_main_queue(), ^{
        [CATransaction begin];
        [CATransaction setDisableActions:YES];
        l.frame = host.bounds;
        l.autoresizingMask = kCALayerWidthSizable | kCALayerHeightSizable;
        [host addSublayer:l];
        [CATransaction commit];
        fprintf(stderr, "jvl: libVLC layer %s added\n", class_getName([l class]));
    });
}
/**
 * VLC 3.0.24's caopengllayer hosts its CAOpenGLLayer in a view and offers the view. The view is
 * kept (it owns the drawing), and only its layer goes under the Compose window.
 */
- (void)addVoutSubview:(NSView *)view {
    self.view = view;
    dispatch_async(dispatch_get_main_queue(), ^{
        if (!view.layer) view.wantsLayer = YES;
        CALayer *l = view.layer;
        fprintf(stderr, "jvl: libVLC view %s, layer %s\n", class_getName([view class]), class_getName([l class]));
        [l removeFromSuperlayer];
        retina([view class]);
        l.contentsScale = NSScreen.mainScreen.backingScaleFactor;
        fprintf(stderr, "jvl: libVLC layer scale %.1f\n", l.contentsScale);
        // libVLC sizes the picture from the view, so the view follows the window's size.
        view.frame = self.host.bounds;
        [[NSNotificationCenter defaultCenter] addObserverForName:NSWindowDidResizeNotification object:nil queue:nil usingBlock:^(NSNotification *n) {
            view.frame = self.host.bounds;
            l.frame = self.host.bounds;
        }];
        [self addVoutLayer:l];
    });
}
- (void)removeVoutSubview:(NSView *)view {
    dispatch_async(dispatch_get_main_queue(), ^{ [view.layer removeFromSuperlayer]; });
    self.view = nil;
}
- (void)removeVoutLayer:(CALayer *)l {
    dispatch_async(dispatch_get_main_queue(), ^{ [l removeFromSuperlayer]; });
}
@end

/** Route C: the object to give libvlc_media_player_set_nsobject. */
void *jvl_vout_host(void *h) { return (__bridge void *)((JVL *)h)->voutHost; }

void *jvl_create(void) {
    JVL *v = calloc(1, sizeof(JVL));
    v->visible = 1;
    dispatch_sync(dispatch_get_main_queue(), ^{
        const char *route = getenv("JAIPLAYER_VIDEO_LAYER");
        v->route = route && strcmp(route, "metal") == 0 ? 2 : route && strcmp(route, "vout") == 0 ? 3 : 1;
        if (v->route == 2) setupMetal(v);
        else if (v->route == 3) {
            v->layer = [CALayer layer];
            JvlVoutHost *host = [JvlVoutHost new];
            host.host = v->layer;
            v->voutHost = host;
        } else {
            v->sb = [[AVSampleBufferDisplayLayer alloc] init];
            v->sb.videoGravity = AVLayerVideoGravityResizeAspect;
            v->layer = v->sb;
        }
        v->layer.backgroundColor = CGColorGetConstantColor(kCGColorBlack);
        CFRetain((__bridge CFTypeRef)v->layer);
        attach(v);
    });
    return v;
}

/** Shows or hides the layer (the player screen is open or not) and sets fill or fit. */
void jvl_show(void *h, int visible, int fill) {
    JVL *v = h;
    v->visible = visible; v->fill = fill;
    onMain(^{
        [CATransaction begin];
        [CATransaction setDisableActions:YES];
        if (!v->layer.superlayer) attach(v);
        v->layer.hidden = !visible;
        if (v->sb) v->sb.videoGravity = fill ? AVLayerVideoGravityResizeAspectFill : AVLayerVideoGravityResizeAspect;
        else v->layer.contentsGravity = fill ? kCAGravityResizeAspectFill : kCAGravityResizeAspect;
        [CATransaction commit];
        if (!visible) [v->sb flushAndRemoveImage];
    });
}

/**
 * The colours the file states (ISO/IEC 23091-2 code points; 0 = not stated) and its raw SMPTE ST
 * 2086 mastering (24 bytes) and content light level (4 bytes) metadata, or null.
 */
void jvl_set_colour(void *h, int primaries, int transfer, int matrix, int fullRange,
                    const uint8_t *mastering, int masteringBytes, const uint8_t *lightLevel, int lightBytes) {
    JVL *v = h;
    v->primaries = primaries; v->transfer = transfer; v->matrix = matrix; v->fullRange = fullRange;
    if (v->mastering) CFRelease(v->mastering);
    if (v->lightLevel) CFRelease(v->lightLevel);
    v->mastering = masteringBytes == 24 ? CFDataCreate(NULL, mastering, 24) : NULL;
    v->lightLevel = lightBytes == 4 ? CFDataCreate(NULL, lightLevel, 4) : NULL;
}

/** Decoded, displayed, failed, width, height, then since the last call: intervals, longest and standard deviation (µs). */
void jvl_stats(void *h, long *out) {
    JVL *v = h;
    out[0] = v->decoded; out[1] = v->displayed; out[2] = v->failed; out[3] = v->width; out[4] = v->height;
    long n = v->intervals;
    double mean = n ? v->sum / n : 0;
    out[5] = n; out[6] = (long)v->maxInterval; out[7] = n > 1 ? (long)sqrt(fmax(0, v->sumSq / n - mean * mean)) : 0;
    v->intervals = 0; v->sum = v->sumSq = v->maxInterval = 0;
}

static void noteShown(JVL *v) {
    static mach_timebase_info_data_t tb;
    if (!tb.denom) mach_timebase_info(&tb);
    uint64_t now = mach_absolute_time();
    if (v->lastShown) {
        double us = (double)(now - v->lastShown) * tb.numer / tb.denom / 1000.0;
        v->intervals++; v->sum += us; v->sumSq += us * us; if (us > v->maxInterval) v->maxInterval = us;
    }
    v->lastShown = now;
}

// ---- libVLC video callbacks (libvlc_video_set_format_callbacks / set_callbacks) ----

unsigned jvl_format(void **opaque, char *chroma, unsigned *width, unsigned *height, unsigned *pitches, unsigned *lines) {
    JVL *v = *opaque;
    int tenBit = memcmp(chroma, "CVPP", 4) == 0 || memcmp(chroma, "P010", 4) == 0 || memcmp(chroma, "I0AL", 4) == 0
        || memcmp(chroma, "I42A", 4) == 0 || memcmp(chroma, "I0AB", 4) == 0;
    fprintf(stderr, "jaiplayer-video chroma %.4s -> %s %ux%u (native layer)\n", chroma, tenBit ? "P010" : "NV12", *width, *height);
    memcpy(chroma, tenBit ? "P010" : "NV12", 4);
    int w = *width, hgt = *height;
    int alignedLines = (hgt + 15) / 16 * 16;
    OSType format = tenBit
        ? (v->fullRange ? kCVPixelFormatType_420YpCbCr10BiPlanarFullRange : kCVPixelFormatType_420YpCbCr10BiPlanarVideoRange)
        : (v->fullRange ? kCVPixelFormatType_420YpCbCr8BiPlanarFullRange : kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange);
    NSDictionary *attrs = @{
        (id)kCVPixelBufferPixelFormatTypeKey: @(format),
        (id)kCVPixelBufferWidthKey: @(w),
        (id)kCVPixelBufferHeightKey: @(hgt),
        (id)kCVPixelBufferExtendedPixelsBottomKey: @(alignedLines - hgt),
        (id)kCVPixelBufferBytesPerRowAlignmentKey: @64,
        (id)kCVPixelBufferIOSurfacePropertiesKey: @{},
        (id)kCVPixelBufferMetalCompatibilityKey: @YES,
    };
    if (v->pool) CVPixelBufferPoolRelease(v->pool);
    v->pool = NULL;
    if (CVPixelBufferPoolCreate(NULL, NULL, (__bridge CFDictionaryRef)attrs, &v->pool) != kCVReturnSuccess) return 0;
    CVPixelBufferRef probe = NULL;
    if (CVPixelBufferPoolCreatePixelBuffer(NULL, v->pool, &probe) != kCVReturnSuccess) return 0;
    pitches[0] = (unsigned)CVPixelBufferGetBytesPerRowOfPlane(probe, 0);
    pitches[1] = (unsigned)CVPixelBufferGetBytesPerRowOfPlane(probe, 1);
    lines[0] = alignedLines;
    lines[1] = alignedLines / 2;
    CVPixelBufferRelease(probe);
    v->width = w; v->height = hgt;
    if (v->desc) { CFRelease(v->desc); v->desc = NULL; }
    return 1;
}

void jvl_cleanup(void *opaque) {
    JVL *v = opaque;
    v->width = 0; v->height = 0;
    onMain(^{ [v->sb flushAndRemoveImage]; });
}

void *jvl_lock(void *opaque, void **planes) {
    JVL *v = opaque;
    JvlPicture *p = &v->slots[v->next++ % SLOTS];
    if (p->locked) CVPixelBufferUnlockBaseAddress(p->buffer, 0);
    if (p->buffer) CVPixelBufferRelease(p->buffer);
    p->buffer = NULL; p->locked = 0;
    if (!v->pool || CVPixelBufferPoolCreatePixelBuffer(NULL, v->pool, &p->buffer) != kCVReturnSuccess) {
        static uint8_t scratch[16];
        planes[0] = planes[1] = scratch; // Should not happen; keeps libVLC from writing to NULL.
        return p;
    }
    CVPixelBufferLockBaseAddress(p->buffer, 0);
    p->locked = 1;
    planes[0] = CVPixelBufferGetBaseAddressOfPlane(p->buffer, 0);
    planes[1] = CVPixelBufferGetBaseAddressOfPlane(p->buffer, 1);
    planes[2] = NULL;
    return p;
}

static void tag(JVL *v, CVPixelBufferRef b) {
    int prim = v->primaries, trc = v->transfer, mtx = v->matrix;
    BOOL tenBit = CVPixelBufferGetPixelFormatType(b) == kCVPixelFormatType_420YpCbCr10BiPlanarVideoRange
        || CVPixelBufferGetPixelFormatType(b) == kCVPixelFormatType_420YpCbCr10BiPlanarFullRange;
    // Not stated: as the current shader path guesses (10-bit UHD is BT.2020, HD BT.709, SD BT.601).
    BOOL uhd = tenBit && (v->width >= 3840 || v->height >= 2160);
    if (mtx == 0 || mtx == 2) mtx = uhd ? 9 : (v->height >= 720 ? 1 : 6);
    if (prim == 0 || prim == 2) prim = uhd ? 9 : (v->height >= 720 ? 1 : 6);
    if (trc == 0 || trc == 2) trc = 1;
    CFStringRef p = CVColorPrimariesGetStringForIntegerCodePoint(prim);
    CFStringRef t = CVTransferFunctionGetStringForIntegerCodePoint(trc);
    CFStringRef m = CVYCbCrMatrixGetStringForIntegerCodePoint(mtx);
    if (p) CVBufferSetAttachment(b, kCVImageBufferColorPrimariesKey, p, kCVAttachmentMode_ShouldPropagate);
    if (t) CVBufferSetAttachment(b, kCVImageBufferTransferFunctionKey, t, kCVAttachmentMode_ShouldPropagate);
    if (m) CVBufferSetAttachment(b, kCVImageBufferYCbCrMatrixKey, m, kCVAttachmentMode_ShouldPropagate);
    if (v->mastering) CVBufferSetAttachment(b, kCVImageBufferMasteringDisplayColorVolumeKey, v->mastering, kCVAttachmentMode_ShouldPropagate);
    if (v->lightLevel) CVBufferSetAttachment(b, kCVImageBufferContentLightLevelInfoKey, v->lightLevel, kCVAttachmentMode_ShouldPropagate);
}

void jvl_unlock(void *opaque, void *picture, void *const *planes) {
    JvlPicture *p = picture;
    if (p->locked) CVPixelBufferUnlockBaseAddress(p->buffer, 0);
    p->locked = 0;
    ((JVL *)opaque)->decoded++;
}

void jvl_display(void *opaque, void *picture) {
    JVL *v = opaque;
    JvlPicture *p = picture;
    if (!p->buffer) return;
    // libVLC's vmem calls display for the frame, then unlock when the picture goes back to its
    // pool; the frame is finished by now.
    if (p->locked) { CVPixelBufferUnlockBaseAddress(p->buffer, 0); p->locked = 0; }
    noteShown(v);
    if (v->route == 2) { drawMetal(v, p->buffer); return; }
    tag(v, p->buffer);
    if (!v->desc || !CMVideoFormatDescriptionMatchesImageBuffer(v->desc, p->buffer)) {
        if (v->desc) CFRelease(v->desc);
        v->desc = NULL;
        CMVideoFormatDescriptionCreateForImageBuffer(NULL, p->buffer, &v->desc);
    }
    CMSampleTimingInfo timing = { kCMTimeInvalid, CMClockGetTime(CMClockGetHostTimeClock()), kCMTimeInvalid };
    CMSampleBufferRef sample = NULL;
    if (v->desc && CMSampleBufferCreateReadyWithImageBuffer(NULL, p->buffer, v->desc, &timing, &sample) == noErr) {
        CFArrayRef att = CMSampleBufferGetSampleAttachmentsArray(sample, true);
        CFDictionarySetValue((CFMutableDictionaryRef)CFArrayGetValueAtIndex(att, 0), kCMSampleAttachmentKey_DisplayImmediately, kCFBooleanTrue);
        if (v->sb.status == AVQueuedSampleBufferRenderingStatusFailed) {
            v->failed++;
            fprintf(stderr, "jvl: layer failed: %s\n", v->sb.error.description.UTF8String);
            [v->sb flush];
        }
        [v->sb enqueueSampleBuffer:sample];
        CFRelease(sample);
        v->displayed++;
    }
}

