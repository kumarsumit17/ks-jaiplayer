---
type: Plan
title: macOS playback
description: How JaiPlayer's playback cost on macOS is measured, the results of Job 2 of the playback optimization brief (10-bit frame path), and the native video layer and true HDR trial.
tags: [macos, performance, playback, libvlc]
---

# macOS playback

Job 2 of the brief "Playback Optimization and StreamFlix Readiness". The [Android rule](plan-android-cpu.md) applies: compare on the whole device, with the same file, machine and method.

## Method
- MacBook, Apple M5 (10 cores), macOS 27.2.1, on battery, sound muted, nothing else building or playing.
- Release app images (`createReleaseDistributable`, DEV variant): **before** is `5cdace2`, **after** is the change, built the same way. Run from the executable with the file as argument and `JAIPLAYER_PERF=1`.
- The window is set to 1280×748; each run warms up for about 14 s and is then measured for 30 s. Runs alternate before and after, 2 rounds. A run with the window covered, or with another busy process, is thrown away.
- **Whole device**: busy time of all CPUs (`host_statistics`), percent of one core (1000 % in all). **App**, `VTDecoderXPCService`, `WindowServer`, `coreaudiod`: CPU time from `ps`. **GPU**: the mean of `ioreg`'s "Device Utilization %". **Memory**: `footprint`.
- Clips (made with ffmpeg, test pattern): 4K30 HEVC 10-bit 20 Mb/s without subtitles (VideoToolbox), the same with a subtitle track shown (software decoding), 1080p60 H.264 10 Mb/s (VideoToolbox, the control).
- Whole-system power (`AccumulatedSystemLoad`) was too noisy over 30 s to show a difference (5.7 to 17.8 W) and is not used.

## Change
libVLC 3 proposes a chroma to the video callbacks; the app always asked for 8-bit `I420`. For VideoToolbox's 10-bit frames (`CVPP`) it now asks for `P010`, a plain copy, and the GPU shader reads the 16-bit planes ([Smooth playback](smoothness.md)).

## Results
Two rounds each; all runs showed every frame, with no crash.

| Case | | App CPU | Whole device | GPU | Memory |
|---|---|---|---|---|---|
| 4K 10-bit, VideoToolbox | before | 50.7–51.3 % | 123–134 % | 20–42 % | 490–530 MB |
| | after (P010) | **33.2–34.6 %** | **116–120 %** | 35–37 % | 567–600 MB |
| 4K 10-bit + subtitles, software | before | 71.7–72.6 % | 136–141 % | 19–23 % | 1.57–1.62 GB |
| | tried: 16-bit planar to the GPU (`I0AL`) | 81.7–81.8 % | 156–165 % | 37–47 % | 1.69 GB |
| | after (converted to `I420` as before) | 70.9 % (1 clean round) | 141 % | 19 % | 1.56 GB |
| 1080p60 8-bit, VideoToolbox (path unchanged) | before | 32.9–33.5 % | 133–140 % | 30 % | 470 MB |
| | with the change | 31.1–31.7 % | 126–132 % | 29–30 % | 405 MB |

## Findings and decisions
- **Kept: P010.** App CPU for 4K 10-bit falls by about a third (17 points), the whole device by about 10 points. The cost: about 70 MB more memory (frames twice the size) and, in most runs, a busier GPU. Battery effect not proven either way.
- **Not kept: 16-bit planar from software decoding.** libVLC's conversion to 8-bit costs less than uploading frames twice the size: +10 CPU points and +15–25 for the whole device when it was tried.
- 8-bit video is unchanged.
- Colours: P010 of 3840 or 2160 and larger now uses BT.2020 weights (it used BT.709 before). HDR tone mapping came later (see "HDR colour").
- The test pattern is one kind of content; real films, Windows and Intel Macs are not measured.

## Against VLC and IINA

JaiPlayer DEV `63e650c` (CI build), VLC 3.0.24 and IINA 1.5.1 with their default settings, method as above (VLC and IINA have their own window chrome; video about 1280×720 in each). Files: 1080p60 H.264 10 Mb/s with AAC; Blu-ray style 1080p23.976 H.264 36 Mb/s with AC-3 5.1; 4K23.976 HEVC 10-bit HDR10-tagged 61 Mb/s with E-AC-3 5.1 (all test pattern). Clean runs only, 2 or 3 each; VLC on 4K has one.

| File | Player | App CPU | Whole device | GPU | Memory | Sound starts after |
|---|---|---|---|---|---|---|
| 1080p60 | JaiPlayer | 30–32 % | 126–138 % | 28–31 % | 406–424 MB | 0.85–1.3 s |
| | VLC | **12–13 %** | **98–121 %** | **5 %** | **174–188 MB** | 1.1–1.2 s |
| | IINA | 27 % | 138–151 % | 60–62 % | 531–555 MB | **0.7–1.0 s** |
| Blu-ray style | JaiPlayer | 18 % | 86–88 % | 13–15 % | 413–458 MB | 1.2–1.3 s |
| | VLC | **9 %** | **76–82 %** | **1–3 %** | **175 MB** | 1.1 s |
| | IINA | 13–14 % | 95–96 % | 28 % | 372–380 MB | **0.7 s** |
| 4K HDR | JaiPlayer | 34–36 % | 118–126 % | 35–37 % | 592–667 MB | 1.2–1.6 s |
| | VLC | **12 %** | **88 %** | **8 %** | **176–184 MB** | 1.1 s |
| | IINA | 15–17 % | 98–119 % | 37–62 % | 574–695 MB | **0.7–0.9 s** |

- **Size** (installed): JaiPlayer DEV 195 MB (dmg 83 MB), VLC 148 MB, IINA 197 MB; production 1.11.3 is 230 MB.
- **Stability**: no crash and no exit in any run; JaiPlayer showed every frame in uncovered runs. Runs are about a minute each; there is no long soak or seek stress on the Mac.
- **Picture**: on the HDR-tagged clip JaiPlayer's colours are paler than VLC's and IINA's: it has no HDR tone mapping. The clip is a synthetic pattern, so a real HDR film is still to be compared.
- **Reading it**: VLC costs the least because it draws decoder frames itself, while JaiPlayer copies every frame into memory and uploads it to the GPU. JaiPlayer is level with IINA at 1080p60 and behind it on 4K and start time.
- "Sound starts after" is the time from launch to the player's first power assertion for audio output, one method for all three. Power draw was too noisy to compare.

## HDR colour (P0-B of the [Playback plan](plan-playback.md))

- **Not new:** 1.11.3 shows the same washed-out picture as DEV `63e650c` on the Jellyfin HDR10 clips (window screenshots, 1080p and 4K HEVC 10-bit, PQ, BT.2020): the PQ signal was drawn as if it were SDR.
- **Change:** the colour description is read from the file (`VideoColour`, MP4/MOV `colr`/`mdcv`/`clli`, Matroska `Colour`), since libVLC 3 does not report it. HDR and BT.2020 frames use a second variant of the existing frame shader (`PlatformDesktop.kt`): PQ or HLG to linear light (1.0 = 100 nits), highlight desaturation (0.5, exponent 2), the Hable curve from the peak (MaxCLL, else the mastering peak, else 10,000 nits for PQ and 1,000 for HLG, as VLC 3), BT.2020 to BT.709, sRGB. All on the GPU in the same pass; no frame copy or CPU conversion. SDR in BT.709 keeps the unchanged shader. The curve was chosen against VLC: on the same source frame, mean colour of the picture within (−2, 0, −7) of VLC 3.0.24's.
- **Colour test:** `VideoColourTest` draws PQ (with and without a peak) and HLG colours through the real shader and matches `HdrReference`, the same maths written separately in double precision, within 3 of 255 for P010 and 6 for I420 (rounded to 8 bits as libVLC does); `VideoColourReadTest` covers the MP4 and Matroska readers. 71 desktop tests, none failing.
- **Against VLC (screenshots, same window size, frames about a second apart):** mean colour of the picture (non-black pixels) 1080p (107, 83, 148) against VLC (114, 82, 161), 4K (104, 84, 148) against (112, 83, 161); saturation (mean channel spread) 64–65 against 79, before 37–38.
- **Cost (MacBook M5 on battery, 30 s windows, clean runs, every frame drawn):**

| File | Build | App CPU | Whole Mac | GPU | Memory |
|---|---|---|---|---|---|
| 4K HDR10 | before | 50.9–52.9 % | 113–133 % | 44.7–59.1 % | 603–631 MB |
| | tone-mapped | 52.1–58.8 % | 110–134 % | 50.3–57.5 % | 600–614 MB |
| 1080p HDR10 | before | 36.3–36.5 % | 86–104 % | 43.4–44.3 % | 485–492 MB |
| | tone-mapped | 35.3–36.8 % | 102–112 % | 46.8–47.7 % | 479–484 MB |
| 1080p SDR (Sintel) | before | 18.1–18.8 % | 81–84 % | 14.1 % | 463–477 MB |
| | tone-mapped build | 18.2–18.5 % | 73–87 % | 10.2–14.0 % | 462–494 MB |

  Interleaved 4K round: whole Mac 113–114 % before, 110–111 % after. No difference beyond noise; 1080p GPU about 3 points higher.
- **Limits:** a file whose container states no colours (some MP4s, http streams) still shows the old picture; the HEVC bitstream's own colour description is not read. The clips are synthetic patterns; real HDR films are not tested. Windows not tested (same shader).
- **True HDR output (not built):** IINA sends HDR to the display (EDR). JaiPlayer would need an extended-range drawing surface: a `CAMetalLayer` with `wantsExtendedDynamicRangeContent`, a linear or PQ colour space and a half-float pixel format, with tone mapping only to the display's current EDR headroom (`NSScreen.maximumExtendedDynamicRangeColorComponentValue`). Compose's Skia surface is 8-bit sRGB, so this belongs with the native video view trial (P1-C); on Windows it needs a DXGI HDR10 swap chain.

## Native video layer trial (2026-11-28)

Owner-approved feasibility trial: show video without the app copying, converting and uploading every frame, and show true HDR. Trial code only, off unless `JAIPLAYER_VIDEO_LAYER` is set, never in an installer: `desktop/mac/trial/JaiVideoLayer.m` (Apple APIs only, built by its `build.sh`, 83 KB for arm64) and `vlc/NativeVideoLayer.kt`. libVLC 3 stays; IINA was used only as a behaviour reference. Each route puts a native layer directly under Compose's drawing layer (`compose.interop.blending` makes Compose see-through, and the video surface clears a hole); libVLC's video callbacks are C functions in the helper, so the JVM does nothing per frame.

- **A, `avsb`:** libVLC writes each frame into an IOSurface-backed `CVPixelBuffer` (P010 for 10-bit, NV12 for 8-bit). The buffer is tagged with the file's primaries, transfer, matrix and MP4 `mdcv`/`clli`, and an `AVSampleBufferDisplayLayer` shows it. macOS does the YUV conversion, HDR (EDR) and tone mapping.
- **B, `metal`:** the same buffers as Metal textures (no copy) go through our YUV shader onto a `CAMetalLayer`. For HDR the layer is RGBA16Float with `wantsExtendedDynamicRangeContent`, a BT.2100 PQ or HLG colour space and `CAEDRMetadata` (HDR10 from the file's metadata, or HLG), so macOS maps to the headroom.
- **C, `vout`:** libVLC's own `caopengllayer` output with `glconv_cvpx` draws VideoToolbox frames itself (no copy at all). VLC 3.0.24 hands over an NSView; the trial moves its layer under Compose, keeps the view's size in step with the window, and answers the view's backing-scale questions for Retina. The bundled libVLC lacks those plugins, so C ran on VLC.app 3.0.24's libVLC (`JAIPLAYER_VLC_PATH`).

**Measured** (MacBook M5 on mains power and charging, so no battery drain; method and files as above; 2 alternating rounds each; same app image with and without the flag):

| File | Path | App CPU | GPU | WindowServer | Memory |
|---|---|---|---|---|---|
| 4K HDR10 60 fps | today | 47–57 % | 46–56 % | 18–30 % | 620–653 MB |
| | A | 33–35 % | **2 %** | 16–17 % | 631–675 MB |
| | B | 36–37 % | 10–12 % | 20–25 % | 837–861 MB |
| | C | **21–22 %** | 13–16 % | 10–14 % | **531–540 MB** |
| 1080p HDR10 60 fps | today | 32–33 % | 35 % | 16 % | 477–485 MB |
| | A | 18 % | **2 %** | 17 % | 470–500 MB |
| | B | 20–21 % | 7–8 % | 20 % | 524–545 MB |
| | C | **15–16 %** | 11–24 % | 10–25 % | 511–548 MB |
| Sintel 1080p SDR 24 fps | today | 18 % | 4 % | 10 % | 456–470 MB |
| | A | **12 %** | **2 %** | 12 % | 438–459 MB |
| | B | 13 % | 3–4 % | 9 % | 467–481 MB |
| | C | 12–14 % | 7–14 % | 11–21 % | 482–499 MB |

- **Overlap:** another session played video in the same DEV app (same bundle id) and in VLC in short bursts during the later runs, so the route C rows and the window checks may include its load. GPU and WindowServer are whole-Mac counters, so they are the figures most affected; app CPU is per process. Repeat C before relying on its GPU figures.
- **Frames:** every run showed every decoded frame (A and B), and C lost none at 1×. Time to first picture: today 0.96–1.00 s, A 0.96–0.98 s, B 0.97–1.07 s, C 1.65–1.85 s (VLC.app's larger plugin set loads more slowly; libVLC is ready after 1.1–1.3 s instead of 0.7 s). Whole-Mac CPU was too noisy this session to rank (other apps ran).
- **Speed, seek and pause** (`JAIPLAYER_TRIAL_STEPS`, 24, 30 and 60 fps files): 0.25× to 4× play with frame intervals steady (standard deviation under 2 ms at 1×, 2× and 4×), and seek, pause and resume work in all three routes. At 4× on 60 fps, A hands the layer 233 frames a second and B 118 (more than the screen shows: a full build should drop to the display rate); 4K software decoding tops out at 53 fps in both.
- **Under the controls:** the controls, title and seek bar draw over the video in all routes, and embedded text subtitles show (libVLC blends them in A and B; C draws them itself). Resize and full screen follow the window in all routes. Clicking the controls, image subtitles (PGS) and moving between displays were not tried (one display, and no window-control approval during the run).
- **Covered or minimized:** playback goes on in all routes. A stops drawing like today's path (WindowServer 4 %, GPU 5 %). B keeps rendering (GPU 12 %) until it watches occlusion. C partly (WindowServer 25 %).
- **Colour, 1080p HDR10** (mean of non-black pixels in sRGB, saturation as mean channel spread; frames a few seconds apart): QuickTime (107, 89, 201) spread 118, A (131, 81, 197) 116, B (142, 77, 194) 117. Both match QuickTime's saturation, so macOS's own tone mapping is used. C (108, 76, 166) 90 matches VLC (116, 73, 165) 92, since it is VLC's tone mapping. Today's shader gives (113, 83, 154) 71. A file without colour information gets today's guess (10-bit UHD BT.2020, HD BT.709) in A and B. In B an untagged SDR file looks lighter than in A (its SDR colour space needs fixing).
- **True HDR not measurable here:** this Mac's built-in Liquid Retina is not an XDR display. Its EDR headroom is 2.0 at most and stayed at 1.0 during playback, so A and B showed tone-mapped SDR. The comparison with IINA and QuickTime by eye on an HDR display, and the 20-minute real 4K HDR film (no film folder named yet), remain.
- **Picture-in-Picture:** A can use the system's (`AVPictureInPictureController` with a sample-buffer content source, macOS 12+); B and C have none built in.
- **Unchanged by construction:** audio effects and presets (no audio code touched). http and HLS: the desktop engine still opens only local files, a separate item (P2) that no route changes.

**Go / no-go:** A **go** (lowest GPU, app CPU 30–45 % lower, same start, macOS HDR and tone mapping, system PiP, stops drawing when hidden, bundled libVLC unchanged). B **go as a fallback** only if the tone mapping must be our own: more memory, keeps drawing when hidden. C **no-go**: least CPU and memory, but SDR only (libVLC 3 has no EDR output), and it needs extra libVLC plugins (caopengllayer, glconv_cvpx, OpenGL, which Apple has deprecated). It also needs fragile workarounds (moving a view's layer, Retina override), slower start-up, and no fill mode.

**Recommended:** route A for SDR and HDR alike. What remains copied is libVLC's one copy of each VideoToolbox frame into the IOSurface (no conversion, no upload). Removing that too would need libVLC 4 or a decoder of our own.

**Full implementation, size and risk** (about 1–2 weeks):
- Build the helper in packaging (xcrun clang on the macOS runner), or call the Objective-C runtime through JNA to avoid a native build.
- Read Matroska HDR metadata.
- Drop frames above the display rate.
- Add fill mode and PiP.
- Keep Compose's see-through mode working: `compose.interop.blending` is experimental, and the layer is found by Skiko's class name `AWTMetalLayer`. Compose updates could break this; a test that checks the layer tree would catch it.
- The current shader path stays as the fallback and for Windows.

**Windows:** what carries over is the flag, the see-through hole in Compose, reading colours and HDR metadata, and the step script. The native part does not: a D3D11 swap chain under the window (`SetColorSpace1` and `SetHDRMetaData` for HDR10, or DirectComposition) plays A's role. libVLC 3's own direct3d11 output in a child window would play C's, and on Windows that one can send HDR.

## Left for later
- 4K 10-bit with subtitles needs about 1.6 GB of memory and twice the CPU of hardware decoding, because subtitles force software decoding. Drawing subtitles in the app over hardware-decoded frames would remove that, and is a larger change.
