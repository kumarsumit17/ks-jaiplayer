---
type: Plan
title: macOS playback
description: How JaiPlayer's playback cost on macOS is measured, and the results of Job 2 of the playback optimization brief (10-bit frame path).
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

## Left for later
- 4K 10-bit with subtitles needs about 1.6 GB of memory and twice the CPU of hardware decoding, because subtitles force software decoding. Drawing subtitles in the app over hardware-decoded frames would remove that, and is a larger change.
