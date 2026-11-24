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
| `:mobile:android` | `mobile/android/` | The phone app: `<app>-v<version>.apk` and `.aab`, with the phone launcher entry |
| `:tv:google-tv` | `tv/google-tv/` | The Google TV app: `<app>-v<version>-google-tv.apk` and `.aab`, with the Leanback launcher entry and banner |

Both Android apps run the same code from `shared/src/androidMain` (activity, manifest, pickers, picture-in-picture, updates); package name, version and signing are set once for both in the root `build.gradle.kts`.
| — | `mobile/ios/` | Empty: not a target |
