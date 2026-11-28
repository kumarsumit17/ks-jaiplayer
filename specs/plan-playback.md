---
type: Plan
title: Playback plan
description: The consolidated, owner-approved playback plan (2026-11-28) in phases with gates; it replaces the "Playback Optimization and StreamFlix Readiness" brief.
tags: [plan, playback, performance, audio, hdr]
---

# Playback plan

Approved by the owner on 2026-11-28. Measurements already taken are in [Android playback
CPU](plan-android-cpu.md) and [macOS playback](plan-macos-playback.md).

## Rules for every phase

- Work only in ks-jaiplayer on `dev`; commit and push after every finished step; stop at the end of
  each phase with a report of under 10 lines and wait for the owner.
- Correctness and format support come before CPU gains.
- Keep a change only if before-and-after measurements (same file, device, method, 2+ rounds, real
  media where available) show a gain beyond noise and playback quality is unchanged.
- Media3 stays on Android and Google TV, libVLC on desktop; Windows unchanged until benchmarked.
- Protect the 2 MB Android size, low memory, fast start and smooth playback.
- Playback stays behind the engine interface, free of app UI, so Streamflix clients can reuse it.
- Needs in ks-utility or ks-streamflix are reported as handoffs, never changed here.
- Real films: 1080p, 4K HDR, and AC-3 / E-AC-3 audio if available, 20 minutes or longer each. The
  folder is still to be named by the owner.

## P0 (current)

### P0-A Android Dolby audio

1. Reproduce the AC-3 and E-AC-3 failure with real files on DEV and on production 1.11.3. Report
   whether it is a regression or an existing limitation, and why the audio failure stops the video.
2. An audio decoder failure never stops or crashes playback: select another playable audio track;
   if none, continue video only with a clear message. Hardware video decoding stays on.

Accept when both real files keep playing video on the phone and Google TV emulators, the fallback
and the message are covered by tests, and the regression question is answered with evidence.

### P0-B macOS HDR colour

1. State whether the pale HDR picture is new or also in 1.11.3 (screenshots of the JaiPlayer window
   only).
2. HDR-to-SDR tone mapping (PQ, HLG, BT.2020) in the existing frame shader. Report what true HDR
   output on an HDR display needs; do not build it.

Accept when a colour test matches a reference within a stated tolerance, screenshots are close to
VLC's, and CPU and GPU are not worse beyond noise.

**Gate 1:** stop after P0; the owner decides the 1.12.0 release scope. No DEV build, tag or release
before that.

## Later phases (each only on the owner's word)

- **P1-A Audio compatibility** (report first, no code): AC-3, E-AC-3, DTS, TrueHD, AAC, FLAC, MP3
  and Opus on Android, Google TV, macOS and Windows. Options: native decoding, passthrough
  (validated on Google TV), a software decoder limited to the missing codecs, an optional
  downloadable component; for each, licence and distribution terms, installed size, CPU, memory,
  battery, and how far it reduces Streamflix server-side audio conversion. No complete multimedia
  engine. Recommend one. **Gate 2.**
- **P1-B macOS baseline:** 20 minutes of each real film in JaiPlayer DEV, VLC and IINA under equal
  conditions: app CPU, whole-Mac CPU, GPU, memory at start and end, battery drain, time to first
  picture, dropped frames, stability. Verify VideoToolbox decoding independently of drawing.
- **P1-C Native video view trial.** Stop before it and ask the owner to switch to Opus High; its
  prompt comes separately. **Gate 3:** go or no-go.
- **P1-D Android CPU:** compare with VLC at equal frame delivery and decoder configuration, with a
  per-process breakdown (app, mediaserver, codec service, surfaceflinger) and the display refresh
  rate during playback; then 4× against VLC and MX Player without losing speed accuracy or frames.
  Change code only for a clear measured cause.
- **P2:** skip drawing when the window is hidden or minimized (audio and picture-in-picture
  unaffected); JVM and native memory tuning from measurements only, with no limits that cause
  instability or heavy garbage collection; Streamflix compatibility (http and HLS links, start
  position, progress back); final real-film benchmarks; then the DEV build for the owner's test.
  **Gate 4.**
- **P3:** further size reduction, only if asked.
