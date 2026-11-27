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
- **In 1.12.0:** DEV variant side by side ([DEV builds side by side](dev-variant.md)); [Audio effects](audio-effects.md); on phones the player bar's speed, episode and plugin buttons on a second row (the audio-track button was cut off before).
- **Tests:** 52 desktop tests green, including `AudioEffectsTest` and `VlcEqualizerIntegrationTest` (bundled libVLC). Phone emulator: stereo WAV and 5.1 AAC play with Night mode and Dialogue 10, the panel opens, Custom shows the equalizer and is saved.
- **DEV builds:** run 46044658210 (`13df3b4`, DEV variant only) green; APKs signed by the DEV key (SHA-256 `ec96…ef9a`), `com.ks.jaiplayer.dev`. Installed on the owner's Mac next to production. Run 46047978458 (`2fb2afd`, with audio effects) green, same DEV key; its dmg installed on the owner's Mac (replacing the first DEV build).
- **Size (DEV `2fb2afd` vs v1.11.3):** phone APK 2,002,484 vs 1,984,228 bytes (+18 KB, audio effects); TV APK 2,006,585 vs 1,988,504; dmg arm64 103.4 vs 103.3 MB; msi 96.2 vs 96.9 MB. Memory and cold start: not yet measured side by side (production was running with 42 minutes' idle memory compression, not comparable).
- **Pending:** cold start and memory side by side; the owner's manual test. Hindi terms are on ks-utility `dev` (1a19951) for owner review.
- **Owner:** manual test of the DEV builds (side by side, every audio preset per platform, Windows and real devices); approval in the pull request. Recommended: Dependabot alerts on.
- **Release pull request:** `dev` → `main`, open, waiting for the owner's test and approval (link in the KS-Suite progress row).
- **Next action:** wait for the owner's feedback; fix on `dev` with new DEV builds, or on approval fast-forward `main` and tag the approved platforms.
