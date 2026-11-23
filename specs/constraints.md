---
type: Policy
title: Constraints
description: The platform constraints every change to KS JaiPlayer must keep.
tags: [constraints, policy]
---

# Constraints

- Platform code goes behind the `PlayerEngine` interface (`commonMain`) with `expect`/`actual` `VideoSurface`. Never call ExoPlayer or libVLC from `commonMain`.
- **One Android APK serves phone and TV.** Declare `android.software.leanback` and `android.hardware.touchscreen` with `required="false"`. The TV layout switches at runtime from `UiModeManager`. TV screens must be D-pad navigable.
- **Desktop needs VLC installed** (libVLC 3.x). Show a clear message if native discovery fails. Don't crash. On macOS libVLC decodes in software (`MAC_VLC_ARGS`), because subtitles cannot be blended onto VideoToolbox frames delivered through callbacks.
- **Minimal core.** The core does open (file, series folder), play/pause, seek, volume, full screen, series playback and speed. Every other feature is a plugin ([Plugins](plugins.md)).
- Keyboard shortcuts on desktop (space, arrows, F for fullscreen) must match the remote's D-pad actions on TV.
- Implement the `ACTION_VIEW` intent for `video/*` and `audio/*` so other KS apps (`ks-streamflix`, `ks-tv-launcher`) can hand off playback. It accepts `content`, `file`, `http` and `https` URIs; an http(s) link must play without extra headers (ks-streamflix sends signed links), plain http is allowed for home servers, and `EXTRA_TITLE` names it.

These rules are also in `CLAUDE.md`, where they are loaded into every AI-assisted session; change both together.
