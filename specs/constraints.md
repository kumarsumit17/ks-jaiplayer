---
type: Policy
title: Constraints
description: The platform constraints every change to KS JaiPlayer must keep.
tags: [constraints, policy]
---

# Constraints

- Platform code goes behind the `PlayerEngine` interface (`commonMain`) with `expect`/`actual` `VideoSurface`. Never call ExoPlayer or libVLC from `commonMain`.
- **Separate phone and Google TV apps** (from v1.2.0). `mobile/android` and `tv/google-tv` build two APKs and two AABs with the same package name, version and keystore; the TV app's versionCode adds 1,000,000,000, because Google Play needs a different code for each bundle of one app, and a lower code also stops the phone build from being installed over the TV build. The TV app requires `android.software.leanback`, does not require `android.hardware.touchscreen`, and is marked by the `com.ks.jaiplayer.FORM_FACTOR` meta-data; the layout follows the app, not the device. TV screens must be D-pad navigable. Each app updates to its own build: ks-utility `UpdatePlatform.GOOGLE_TV` (`…-google-tv.apk`) or `ANDROID`.
- **Google TV opens files without a system picker.** Many TVs have none, so in the TV app "Open file…" and "Open series or folder…" open the in-app media browser (`MediaBrowser`, fed by `AndroidMediaLibrary` from MediaStore, which also indexes USB drives): folders, then a folder's files in natural order, all D-pad navigable. It asks for `READ_MEDIA_VIDEO` and `READ_MEDIA_AUDIO` (`READ_EXTERNAL_STORAGE` up to Android 12) when it first opens. The phone app keeps the system picker.
- **Google Play builds have no in-app updater.** Play forbids an app updating itself and the `REQUEST_INSTALL_PACKAGES` permission, so the AABs come from the `play` build type (root `build.gradle.kts`, manifest `shared/android-play/AndroidManifest.xml`): no install permission, no update FileProvider, and a `com.ks.jaiplayer.STORE=play` marker that starts the app without the updater. The APKs on GitHub keep the updater. The release workflow sets `play-build-type: play` (ks-utility v0.7.0).
- **Desktop installers carry libVLC 3.x** with only its LGPL plugins (`gradle/bundled-vlc.gradle.kts`, `vlc/VlcBundle.kt`; see [Bundled libVLC](plan-bundled-vlc-and-plugins.md)). The app plays only through that copy and never looks for an installed VLC; `JAIPLAYER_VLC_PATH` replaces it for diagnosis only. If it fails to load, show a clear message (reinstall the app). Don't crash. On macOS libVLC decodes in software (`MAC_VLC_ARGS`), because subtitles cannot be blended onto VideoToolbox frames delivered through callbacks.
- **Minimal core.** The core does open (file, series folder), play/pause, seek, volume, full screen, series playback and speed. Every other feature is a plugin ([Plugins](plugins.md)).
- Keyboard shortcuts on desktop (space, arrows, F for fullscreen) must match the remote's D-pad actions on TV.
- Implement the `ACTION_VIEW` intent for `video/*` and `audio/*` so other KS apps (`ks-streamflix`, `ks-tv-launcher`) can hand off playback. It accepts `content`, `file`, `http` and `https` URIs; an http(s) link must play without extra headers (ks-streamflix sends signed links), plain http is allowed for home servers, and `EXTRA_TITLE` names it.

These rules are also in `CLAUDE.md`, where they are loaded into every AI-assisted session; change both together.
