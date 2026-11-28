---
type: Plan
title: Android CPU optimization
description: How JaiPlayer's CPU use during Android playback is measured, what has been ruled in or out, and what is left before any playback code changes.
tags: [android, performance, battery, media3]
---

# Android CPU optimization

The owner's brief (2026-11-28): lower JaiPlayer's CPU use, battery drain and heat during Android playback
without losing its low memory use, quality, features or stability, before any new heavy feature.
Phase 1 measures and diagnoses only; playback code changes come after it, with the owner's go-ahead.

## Baseline (Pixel 11, Android 17, DEV 1.12.0-dev+fe14942)

| File | JaiPlayer | VLC | MX Player |
|---|---|---|---|
| 1080p60 H.264 | 49–50 %, 145 MB | 47–55 %, 159 MB | 11–17 %, 296–389 MB |
| 4K30 HEVC 10-bit | 39 %, 145 MB | 28 %, 179 MB | 9 %, 285–295 MB |

## Method (used for every measurement)

- CPU: `utime + stime` from `/proc/<pid>/stat` over 20 s, divided by CLK_TCK (100). **Percent of one
  core**, app process only; work in the system codec and media processes is not counted. Per thread:
  the same from `/proc/<pid>/task/*/stat`.
- Memory: TOTAL PSS from `dumpsys meminfo`.
- Opened with `ACTION_VIEW` and an explicit package, from the start, controls hidden, 2 rounds,
  sampled after 8 s of playback.
- Build: the DEV APK is release-equivalent (R8, one `classes.dex`, baseline profile, not debuggable
  or profileable). Method traces need a profileable build. A debug build costs more (57–70 % on the
  same file), so it serves only for A/B tests against another debug build.

## Ruled out, with evidence

- Software H.264 decoding: the decoder is `c2.google.avc.decoder` (hardware, the one MX uses).
- Indirect rendering: video goes to a SurfaceView (`PlayerSurface`, `SURFACE_TYPE_SURFACE_VIEW`).
- UI work per frame: gfxinfo shows 0 frames in 10 s with controls hidden; main thread 3 %,
  RenderThread about 0 %.
- Position polling: one 250 ms refresh on the main thread, inside that 3 %.
- Media3 dynamic scheduling (`experimentalSetDynamicSchedulingEnabled`): no change, 57–65 % with and
  without it in alternating debug builds; not kept.

## Where the time goes (DEV, 1080p60, % of one core)

ExoPlayer:Playback 10–15, MediaCodec_loop 8–11 + 4–5, CodecLooper 3–9, binder threads about 2 each:
the Media3 and MediaCodec pipeline, about 36–50 % in all.

## Open

- [ ] Whole-device CPU (all processes) for JaiPlayer, MX Player and VLC: whether MX's low figure is
      work moved into a system process (MediaPlayer runs its pipeline in mediaserver) rather than less work.
- [ ] Decoder chosen for 4K HEVC 10-bit.
- [ ] Audio processing: `EffectsAudioProcessor` copies every PCM sample even with no effect on;
      measure, and bypass it when no effect is active if it costs anything.
- [ ] Controls visible against hidden; dropped frames; system media-process CPU.
- [ ] Cost inside the Media3 pipeline itself (the brief's last suspect): method trace of
      ExoPlayer:Playback with a profileable build.
