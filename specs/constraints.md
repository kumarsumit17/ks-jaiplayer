---
type: Policy
title: Constraints
description: The platform constraints every change to KS JaiPlayer must keep.
tags: [constraints, policy]
status: draft
---

# Constraints

- Platform code goes behind an `expect`/`actual` `PlayerEngine` interface. Never call ExoPlayer or VLCJ from `commonMain`.
- **One Android APK serves phone and TV.** Declare `android.software.leanback` and `android.hardware.touchscreen` with `required="false"`. The TV layout switches at runtime from `UiModeManager`. TV screens must be D-pad navigable.
- **Desktop needs VLC installed** (libVLC 3.x). Show a clear message if native discovery fails. Don't crash.
- Keyboard shortcuts on desktop (space, arrows, F for fullscreen) must match the remote's D-pad actions on TV.
- Implement the `ACTION_VIEW` intent for `video/*` and `audio/*` so other KS apps (`ks-streamflix`, `ks-tv-launcher`) can hand off playback.

These rules are also in `CLAUDE.md`, where they are loaded into every AI-assisted session; change both together.
