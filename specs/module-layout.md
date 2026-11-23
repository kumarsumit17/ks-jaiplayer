---
type: Reference
title: Module layout
description: Which Kotlin Multiplatform module holds what.
tags: [modules, kmp, architecture]
---

# Module layout

| Gradle project | Folder | Holds |
|---|---|---|
| `:shared` | `shared/src/commonMain` | `PlayerEngine`, `PlaybackState`, `PlayerController`, `Playlist`, the plugin API and built-in plugins, the whole Compose UI, language files (`lang/`). **No platform imports.** |
| | `shared/src/jvmSharedMain` | JVM code for desktop and Android: `GitHubUpdater` (app updates, ks-utility) |
| | `shared/src/desktopMain` | `VlcPlayerEngine` and its JNA binding (`vlc/`), the video surface, `runDesktopApp` |
| | `shared/src/androidMain` | `ExoPlayerEngine`, the video surface |
| `:desktop:mac` | `desktop/mac/` | macOS entry point and dmg packaging |
| `:desktop:windows` | `desktop/windows/` | Windows entry point and msi packaging |
| `:mobile:android` | `mobile/android/` | The one APK for phones and Google TV |
| `:tv:google-tv` | `tv/google-tv/` | Android library merged into the APK: leanback launcher entry, banner, TV detection |
| — | `mobile/ios/` | Empty: not a target |
