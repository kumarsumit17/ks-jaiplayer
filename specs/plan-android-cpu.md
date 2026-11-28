---
type: Plan
title: Android playback CPU
description: How JaiPlayer's CPU use during Android playback is measured (whole device first), how it compares with other players, and what is ruled in or out.
tags: [android, performance, battery, media3]
---

# Android playback CPU

Golden rule (the owner's brief "Playback Optimization and StreamFlix Readiness", 2026-11-28, which
replaces the first CPU brief): optimize what the whole device spends, not which process the CPU is
counted in.

## Rule: compare players on the whole device

Never compare players with different architectures on app-process CPU alone. Media3 runs its whole
pipeline inside the app; an app that plays through Android's MediaPlayer (MX Player) runs it in
`mediaserver`, so its app process looks cheap while the device works as hard. Compare whole-device
CPU, or the app plus the system media processes (`mediaserver`, the codec HAL `c2@…-service`,
`media.swcodec`, surfaceflinger), always with the same file, device and method.

The first comparison broke this rule: "MX Player is about 4× more CPU-efficient" counted the app
process only and was wrong. No app-only CPU targets apply.

## Comparison (Pixel 11, Android 17, 1080p60 H.264, 1×)

| | Whole device | Player pipeline |
|---|---|---|
| JaiPlayer DEV 1.12.0-dev+fe14942 | 139 %, 151 % | about 52 %, all in the app (ExoPlayer runs in-process) |
| MX Player | 157 %, 177 % (an ad's WebView about 12 % of it) | app about 11 % + `mediaserver` 34 % |

The two are broadly level. Both spend the same outside the player: codec HAL 16 %, surfaceflinger
15 %, `media.swcodec` (AAC) 6 %. The phone at rest: 29–31 %. VLC was not measured this way (its
speed is set to 2× for all videos on that phone).

## Method

- **Whole device** (the comparison figure): busy time of all CPUs from the first line of `/proc/stat`
  (total minus idle and iowait) over 20 s, divided by CLK_TCK (100): **percent of one core**, out of
  700 % on the Pixel 11. The busiest processes in the same window from `top -b -n 2 -d 20 -s 9`.
- **App and threads** (where JaiPlayer's own time goes): `utime + stime` from `/proc/<pid>/stat` and
  `/proc/<pid>/task/*/stat` over 20 s, percent of one core.
- Memory: TOTAL PSS from `dumpsys meminfo`.
- Files opened with `ACTION_VIEW`, an explicit package and `--grant-read-uri-permission` on a
  MediaStore URI, from the start, controls hidden, 2 rounds, sampled after 8 s; phone charging,
  other players stopped.
- Builds: release-equivalent only (R8, not debuggable). The DEV 1.12.0 APK is (one minified
  `classes.dex`, baseline profile). A debug build costs much more (57–70 % app on the same file)
  and is never compared with a release build.

## Decoders: hardware, confirmed by the codec's flags

`dumpsys media.player` lists each codec's attributes, the ones behind
`MediaCodecInfo.isHardwareAccelerated()` and `isSoftwareOnly()`:

| Decoder | Used for | hw-accelerated | software-only | vendor |
|---|---|---|---|---|
| `c2.google.avc.decoder` | H.264 | 1 | 0 | 1 |
| `c2.google.hevc.decoder` | HEVC, incl. 4K 10-bit | 1 | 0 | 1 |
| `c2.android.hevc.decoder` (not used) | — | 0 | 1 | 0 |

## JaiPlayer's own time (app process, % of one core)

Not a comparison with other players; it shows where JaiPlayer spends its share.

- 1080p60 H.264 at 1×: 49–52 %; 4K30 HEVC 10-bit: 39 %; PSS 145 MB.
- Threads at 1080p60: ExoPlayer:Playback 10–15, MediaCodec_loop 8–11 + 4–5, CodecLooper 3–9,
  binder threads about 2 each, main thread 3, RenderThread about 0: the Media3 and MediaCodec pipeline.
- It follows the frames handled: at 0.5×, 1×, 2× and 4× (speed accurate to 0.5 %) the app uses
  29, 46, 61 and 100 %. Whether that is worse than MX at 4× needs the whole-device method (Job 3).

## Ruled out, with evidence

- Software decoding: both decoders are hardware (table above).
- Indirect rendering: video goes to a SurfaceView (`PlayerSurface`, `SURFACE_TYPE_SURFACE_VIEW`).
- UI work per frame: gfxinfo shows 0 frames in 10 s with controls hidden; main thread 3 %,
  RenderThread about 0 %.
- Position polling: one 250 ms refresh on the main thread, inside that 3 %.
- Media3 dynamic scheduling alone (`experimentalSetDynamicSchedulingEnabled`): no change, 57–65 %
  with and without it in alternating debug builds.

## Decisions

- Media3 ExoPlayer stays the Android and Google TV engine; Android's MediaPlayer only moves CPU into
  a system process and gives up control over streaming, tracks and speed.
- One playback engine per platform.
- The automatic landscape orientation stays as it is (a separate decision).

## Next (Job 3 of the brief)

Measure JaiPlayer and MX Player at 4× on the Pixel 11 with the whole-device method (device, app,
`mediaserver`), report, and continue to profiling and fixes only if JaiPlayer is meaningfully worse
or a profile shows clear waste. The first brief's Phase 2 (six proposed fixes) is replaced by this.
