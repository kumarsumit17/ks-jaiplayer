---
type: Plan
title: Progress
description: KS JaiPlayer's checkpoint: phase, work done, pending tasks, blockers and the exact next action.
tags: [plan, progress, checkpoint]
---

# Progress

Updated 2026-11-28. KS JaiPlayer is the suite's active app ([KS-Suite progress](https://github.com/kumasumit/ks-suite/blob/main/specs/progress.md)).

- **Phase:** 1.12.0 on `dev`; DEV builds, then the release pull request and the owner's test.
- **Production:** v1.11.3 (phone, Google TV, macOS), `windows-v1.8.0`. **ks-utility:** v0.19.0.
- **Class:** C, confirmed ([Audit](plan-standards.md)).
- **In 1.12.0:** lighter desktop app (GPU texture cache 64 MB, 99 unused libVLC plugins out, no other-chip Skia library on macOS); DEV variant side by side ([DEV builds side by side](dev-variant.md)); [Audio effects](audio-effects.md); on phones the player bar's speed, episode and plugin buttons on a second row (the audio-track button was cut off before).
- **Tests:** 71 desktop tests, none failing (2026-11-28), including `AudioEffectsTest` and `VlcEqualizerIntegrationTest` (bundled libVLC). Phone emulator: stereo WAV and 5.1 AAC play with Night mode and Dialogue 10, the panel opens, Custom shows the equalizer and is saved.
- **DEV builds:** release candidate run 46152779805 (`b9d0bd4`, ks-utility v0.19.0, workflow at `@main`; Gate 1 scope: PR #6 + P0-A + P0-B); APKs signed by the DEV key (`CN=KS-Suite DEV`), `com.ks.jaiplayer.dev`; the macOS DEV app `com.ks.jaiplayer.dev`, ad hoc. Before it 46138270644 (`63e650c`), all green: dev-android, dev-windows, dev-macos-arm64, dev-macos-x64. Before it 46108381085 (`47b0979`), which adds over `fe14942`: macOS DEV opens files from Open With, Android release builds profileable, audio description strings. Earlier: 46055963743 (`fe14942`), 46047978458 (`2fb2afd`), 46044658210 (`13df3b4`).
- **Playback work:** follows the [Playback plan](plan-playback.md); P0 in progress, then Gate 1 (no DEV build, tag or release before it). From the earlier brief: Job 1 done ([Android playback CPU](plan-android-cpu.md)); Job 2 done ([macOS playback](plan-macos-playback.md), 63e650c).
- **Size and speed (DEV `fe14942` vs v1.11.3):** phone APK +18 KB; dmg arm64 83.3 vs 103.3 MB, x64 89.1 vs 113.1 MB, msi 82.2 vs 96.9 MB. macOS start-up 0.82–0.85 s vs 0.88 s; playing footprint 497 MB at 1080p, 514 MB at 4K (before the GPU cache limit about 739 and 721 MB); CPU unchanged. Against VLC 3.0.24 / IINA 1.5.1 on the same files: JaiPlayer starts fastest, VLC uses less memory and CPU while playing (frame copy design). Android (Pixel 11): whole-device CPU level with MX Player at 1080p60 1× ([Android playback CPU](plan-android-cpu.md)); Against VLC 3.7.2 and MX Player on the Pixel 11 (`63e650c`): least memory, fewest dropped frames, fastest start; VLC uses less CPU. Google TV and Windows: not measured.
- **P0-A done (`ae9c4a1`):** unplayable audio no longer stops a video; the Pixel 11 Dolby failure is also in 1.11.3 ([details](plan-android-cpu.md)). **P0-B done:** HDR tone mapping on desktop ([details](plan-macos-playback.md)). **At Gate 1:** no DEV build of these fixes yet.
- **Pending:** the owner's manual test. Hindi terms are on ks-utility `dev` (1a19951) for owner review.
- **Owner:** manual test of the DEV builds (side by side, every audio preset per platform, Windows and real devices); approval in the pull request. Recommended: Dependabot alerts on.
- **Release pull request:** `dev` → `main`, open, waiting for the owner's test and approval (link in the KS-Suite progress row).
- **Release candidate tested (2026-11-28):** run 46152779805 on Mac and Pixel 11, DEV beside 1.11.3: 1080p film, AC-3 with and without a second track, 4K HDR colour, subtitles, all 9 presets, seek, pause, speed pass; http plays on Android; desktop cannot open http links (as in 1.11.3, for P2). Details in PR #6.
- **Next action:** AC-3/E-AC-3 software decoder (owner, 2026-11-28): code and size done (+0.66 MB arm64, +0.53 MB armeabi-v7a); next the DEV build, the device tests and PR #6. Then the macOS native video and HDR trial. No merge, tag or release.

## Completion criteria

From the [Playback plan](plan-playback.md).

| # | Criterion | Status | Evidence so far |
|---|---|---|---|
| 1 | HDR colour correct | open | Desktop tone mapping done, close to VLC; files without colour metadata, real HDR films, Windows still open ([macOS](plan-macos-playback.md)) |
| 2 | AC-3/E-AC-3 resolved, codec matrix validated | open | Video no longer stops (`ae9c4a1`); [matrix](plan-audio-codecs.md) made on Pixel 11, emulators and macOS (Windows, real TV open); no AC-3/E-AC-3 sound on Android yet (Gate 2) |
| 3 | Native video view trial and approved solution | open | Not started (P1-C) |
| 4 | CPU, GPU, memory, battery optimized from measurements | open | Part: [macOS](plan-macos-playback.md), [Android](plan-android-cpu.md); battery not proven |
| 5 | Android 4× investigation | open | Not started (P1-D) |
| 6 | Audio enhancement presets validated | open | Automated tests and emulator only; owner listening test pending |
| 7 | Hardware-accelerated playback validated | open | Part: Pixel 11 decoder flags, macOS VideoToolbox run; TV and Windows open |
| 8 | Subtitles, audio tracks, seeking, speed, background playback | open | Not validated as a set |
| 9 | Local files, HTTP and HLS | open | Local files only in recent runs |
| 10 | Real films tested | open | Sintel only, short runs |
| 11 | Size, start-up, smoothness preserved | open | Measured for `63e650c`; re-check at release |
| 12 | Android, Google TV, macOS, Windows validated | open | Windows and real Google TV open |
| 13 | Versioned playback interface for Streamflix | open | Waits for the specification (P2-M) |
| 14 | DEV builds for all platforms | open | `63e650c` has them; the final build is still to come |
| 15 | Owner's manual test and PR approval | open | Pending |
| 16 | Production release published and verified | open | Pending |
