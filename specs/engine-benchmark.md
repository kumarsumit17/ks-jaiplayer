---
type: Reference
title: Desktop engine benchmark (libVLC vs libmpv)
description: Measured comparison of libVLC and libmpv on macOS in KS JaiPlayer's own frame path, and the decision to stay on libVLC for the desktop builds.
tags: [desktop, macos, libvlc, libmpv, performance, benchmark]
---

# Desktop engine benchmark (libVLC vs libmpv)

Asked by the owner on 2026-11-25: which engine is faster and more stable, libVLC (what the desktop builds use) or libmpv (what IINA uses)?

## How it was measured
- `shared/src/desktopTest/.../vlc/EngineBenchmarkTest.kt`, run only when `JAIPLAYER_BENCH` is set (see the file). It plays each file for 3 s, then measures 15 s: CPU time of the process, pictures handed to the app per second, pictures dropped, and time from open to the first picture.
- Both engines hand decoded pictures to the app in memory, the way the app draws them today. libVLC: the bundled 3.0.24 with the macOS app's options (software decoding, YUV planes). libmpv: 0.41 through its software render API (`vo=libmpv`, BGRA output), once with software decoding and once with VideoToolbox (`hwdec=videotoolbox-copy`). The libmpv used was IINA's own build, loaded from IINA.app for this local test only; nothing of it is in the repo or the app.
- Apple-silicon Mac, two rounds alternating engines, while other sessions ran an emulator and builds (load 6–13), so the numbers are indicative.

## Results (two rounds)
| File | Engine | First picture | Pictures/s | CPU (one core = 100%) | Dropped |
|---|---|---|---|---|---|
| 1080p60 H.264 | libVLC | 270–374 ms | 60 | **31–34%** | 0–3 |
| | libmpv, software decode | 298–357 ms | 60 | 61% | 0 |
| | libmpv, VideoToolbox | 374–399 ms | 60 | 55–71% | 0 |
| 4K30 HEVC | libVLC | 206–214 ms | 30 | **54–57%** | 0 |
| | libmpv, software decode | 263–279 ms | 30 | 94–95% | 0 |
| | libmpv, VideoToolbox | 332–333 ms | 30 | 83–88% | 0 |

## Findings
- Both play everything smoothly at full frame rate. libVLC uses about **half the CPU** of libmpv and shows the first picture sooner, in every case.
- The reason is the frame path, not the decoder (both use FFmpeg): libVLC hands over YUV planes that the GPU converts, while libmpv's software render API must scale and convert every picture to RGB on the CPU. VideoToolbox decoding saves some decoding work but the copy back and the conversion remain.
- libmpv is faster only when it renders on the GPU itself (its OpenGL render API, as IINA does natively). In a Compose Desktop app that needs an OpenGL view beside Compose's Metal/Direct3D surface: much more native code, and an LGPL build of mpv and FFmpeg made by us for each chip and Windows (IINA's are GPL).
- libmpv also needs about 46 libraries (IINA ships 108 MB), against libVLC's 2 libraries plus plugins with the plugin cache.

## Decision
Stay on libVLC for macOS and Windows. Keeping both engines is not worth it (double size, two engines to test per release, nothing gained in this frame path). The next speed gain is VideoToolbox / D3D11VA decoding inside libVLC, with subtitles still drawn, which is in the [per-platform plan](plan-per-platform.md). Revisit libmpv only if the app moves to drawing video in its own GPU view.
