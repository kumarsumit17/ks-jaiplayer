---
type: Plan
title: Progress
description: KS JaiPlayer's checkpoint: phase, work done, pending tasks, blockers and the exact next action.
tags: [plan, progress, checkpoint]
---

# Progress

Updated 2026-11-27. KS JaiPlayer is the suite's active app ([KS-Suite progress](https://github.com/kumasumit/ks-suite/blob/main/specs/progress.md)).

- **Phase:** 1.12.0 on `dev`; DEV builds, then the release pull request and the owner's test.
- **Production:** v1.11.3 (phone, Google TV, macOS), `windows-v1.8.0`. **ks-utility:** v0.19.0.
- **Class:** C, confirmed ([Audit](plan-standards.md)).
- **In 1.12.0:** lighter desktop app (GPU texture cache 64 MB, 99 unused libVLC plugins out, no other-chip Skia library on macOS); DEV variant side by side ([DEV builds side by side](dev-variant.md)); [Audio effects](audio-effects.md); on phones the player bar's speed, episode and plugin buttons on a second row (the audio-track button was cut off before).
- **Tests:** 52 desktop tests green, including `AudioEffectsTest` and `VlcEqualizerIntegrationTest` (bundled libVLC). Phone emulator: stereo WAV and 5.1 AAC play with Night mode and Dialogue 10, the panel opens, Custom shows the equalizer and is saved.
- **DEV builds:** latest run 46055963743 (`fe14942`), all green, APKs signed by the DEV key (`ec96…ef9a`), arm64 dmg installed on the owner's Mac next to production. Earlier: 46044658210 (`13df3b4`), 46047978458 (`2fb2afd`).
- **Size and speed (DEV `fe14942` vs v1.11.3):** phone APK +18 KB; dmg arm64 83.3 vs 103.3 MB, x64 89.1 vs 113.1 MB, msi 82.2 vs 96.9 MB. macOS start-up 0.82–0.85 s vs 0.88 s; playing footprint 497 MB at 1080p, 514 MB at 4K (before the GPU cache limit about 739 and 721 MB); CPU unchanged. Against VLC 3.0.24 / IINA 1.5.1 on the same files: JaiPlayer starts fastest, VLC uses less memory and CPU while playing (frame copy design). Android (Pixel 11): whole-device CPU level with MX Player at 1080p60 1× ([Android playback CPU](plan-android-cpu.md)); Google TV and Windows: not measured.
- **Pending:** the owner's manual test. Hindi terms are on ks-utility `dev` (1a19951) for owner review.
- **Owner:** manual test of the DEV builds (side by side, every audio preset per platform, Windows and real devices); approval in the pull request. Recommended: Dependabot alerts on.
- **Release pull request:** `dev` → `main`, open, waiting for the owner's test and approval (link in the KS-Suite progress row).
- **Next action:** wait for the owner's feedback; fix on `dev` with new DEV builds, or on approval fast-forward `main` and tag the approved platforms.
