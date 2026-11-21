---
type: Reference
title: Module layout
description: Which Kotlin Multiplatform module holds what.
tags: [modules, kmp, architecture]
status: draft
---

# Module layout

| Module | Purpose |
|---|---|
| `shared/src/commonMain` | UI, state, playlist and library logic. **No platform imports.** |
| `shared/src/androidMain` | `actual` implementations: ExoPlayer engine, Android file access |
| `shared/src/desktopMain` | `actual` implementations: VLCJ engine, desktop file access |
| `androidApp/` | Android + Google TV entry point (one APK) |
| `desktopApp/` | Windows / macOS entry point and native packaging |
