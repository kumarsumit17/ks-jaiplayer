---
type: Plan
title: Plan for the first milestone
description: The playback engine on each platform, the shared player interface and UI, the first feature set, languages, packaging, build order and what is left for later.
tags: [plan, engines, architecture, milestone]
status: approved
updated: 2026-11-23
---

# Plan for the first milestone

> **Changes made while building (2026-11-23), by the owner's requests:** a minimal core with plugins ([Plugins](plugins.md)); TV series (a folder of episodes played one after another) and playback speed in the core; app updates from GitHub (ks-utility `v0.3.0`); one release workflow shared by every Kotlin app, in ks-utility. Two findings: on macOS libVLC must decode in software, because subtitles cannot be blended onto VideoToolbox frames that arrive through callbacks; and jpackage refuses a macOS version that starts with 0, so the first release is **v1.0.0**. The sections below are updated to match.

KS JaiPlayer plays local video and audio files on macOS, Windows, Android and Google TV. One Kotlin code base holds the player logic and the interface; each platform adds only its playback engine, its entry point and its packaging. macOS is built first. IINA was the reference for what a good player offers (a quiet window that is all video, controls that appear on demand, keyboard first, drag a file in to play); the design, code and assets here are our own.

## 1. Playback engine per platform

| Platform | Engine | Licence | Why |
|---|---|---|---|
| macOS | **libVLC 3.x**, through our own small Kotlin binding over JNA | libVLC LGPL-2.1+, JNA Apache-2.0 (dual with LGPL-2.1) | Plays almost every container and codec (MKV, HEVC, AV1, every common subtitle format) with hardware decoding, and is reachable from the JVM without writing native code. |
| Windows | the same libVLC engine (same Kotlin code) | as above | One desktop engine for both systems. |
| Android (phone) | **Media3 ExoPlayer** (`media3-exoplayer`, `media3-ui-compose`) | Apache-2.0 | The platform's own engine: hardware decoders, battery-friendly, picture-in-picture and media session support. |
| Google TV | Media3 ExoPlayer, in the same APK as the phone | Apache-2.0 | Same reasons; TV decoders are only reachable through MediaCodec. |

### Desktop: why our own libVLC binding and not vlcj

The repository named **vlcj** until now. vlcj is licensed **GPL-3.0** (with a paid commercial licence). Shipping it would make every desktop build of this MIT-licensed app a GPL-3.0 work. libVLC itself is LGPL-2.1+, which allows dynamic linking from an MIT app. The binding we need is small: about 40 libVLC functions (media, player, time, volume, tracks, subtitles, video callbacks), declared as a JNA interface in `shared/src/desktopMain`. The video reaches Compose through libVLC's video callbacks (`libvlc_video_set_callbacks`): libVLC decodes into a buffer we own and the frame is drawn as an image. This is also how vlcj draws on macOS, where embedding a native window in Java no longer works, so nothing is lost.

Alternatives looked at:

- **libmpv**: excellent player core, but default builds are GPL-2.0+, on macOS it needs its OpenGL render API (more native glue), and no prebuilt LGPL macOS library is widely installed.
- **AVFoundation (macOS) / Media Foundation (Windows)**: native and efficient, but not reachable from the JVM without native glue code (the suite rule is Kotlin only), and they do not play MKV or most subtitle formats.
- **JavaFX Media, GStreamer**: too few formats, or another large runtime to install.

### libVLC on the user's machine

For this milestone the desktop app uses an installed VLC 3.x (`/Applications/VLC.app` on macOS, `C:\Program Files\VideoLAN\VLC` on Windows, or a folder set in `JAIPLAYER_VLC_PATH`), as the constraints already say. If it is not found the app shows a clear message with a link to videolan.org and does not crash. Bundling libVLC inside the dmg and the Windows installer is planned for later (LGPL allows it with the licence text and a source offer; the bundle grows by about 100 MB).

## 2. One shared player interface

```
shared/src/commonMain   PlayerEngine (interface), PlaybackState, Track, PlayerController,
                        PlayerAction, RecentFiles, SettingsStore (interface), the whole UI
shared/src/desktopMain  VlcPlayerEngine (JNA binding), VideoSurface (frames → Compose),
                        libVLC discovery, desktop SettingsStore (java.util.prefs)
shared/src/androidMain  ExoPlayerEngine (Media3), VideoSurface (PlayerSurface),
                        Android SettingsStore (SharedPreferences)
```

- `PlayerEngine` is a plain Kotlin interface in `commonMain`: `open(media)`, `play()`, `pause()`, `seekTo(ms)`, `setVolume(0..100)`, `setMuted()`, `selectAudioTrack(id)`, `selectSubtitleTrack(id or none)`, `addSubtitleFile(uri)`, `release()`, and one `StateFlow<PlaybackState>` (status, position, duration, volume, muted, audio and subtitle tracks, the chosen ones, error).
- The platforms supply the `actual` parts: the engine implementation and an `expect` composable `VideoSurface(engine)` that draws the picture. `commonMain` never imports ExoPlayer or libVLC.
- `PlayerController` (common, unit-tested) turns user actions into engine calls: play/pause toggling, seek steps, volume steps with clamping, track cycling, and when the controls hide.
- `PlayerAction` is the one mapping of keys to actions, shared by the desktop keyboard and the TV remote:

| Key (desktop) | Remote (TV) | Action |
|---|---|---|
| Space | Centre / Play-Pause | Play or pause |
| ← / → | ← / → | Back / forward 10 s (Shift: 60 s on desktop) |
| ↑ / ↓ | ↑ / ↓ | Volume up / down 5 (on TV, with the controls shown, ↑/↓ move focus instead) |
| F | (always full screen) | Full screen on / off |
| M | — | Mute |
| S / A | — | Next subtitle / next audio track |
| Esc | Back | Leave full screen, hide controls, then go back |

## 3. Shared interface (Compose Multiplatform)

Written once in `shared/src/commonMain` with Compose Multiplatform and Material 3:

- **Home**: an "Open file" button, the recent files list (newest first), and a hint that files can be dropped in.
- **Player**: the video fills the window; a controls bar fades in on mouse move, tap or any key and fades out after 3 seconds of playback: play/pause, the time and a seek bar, volume and mute, subtitle and audio track menus, full screen. Errors show as one line over the video, never as a crash.
- Dark theme only for the player (video looks best on black); the KS-Suite colours for accents.

What differs per platform:

| | macOS / Windows | Android phone | Google TV |
|---|---|---|---|
| Input | Mouse, keyboard shortcuts above, drag and drop a file onto the window, menus | Touch: tap shows controls, double-tap left/right seeks, swipe on the seek bar | D-pad and remote only; every control focusable with a visible focus ring |
| Window | Menu bar (File → Open, Open Recent; Playback; Audio; Subtitles; View → Full Screen); native full screen | Immersive full screen while playing, picture-in-picture on Home | Always full screen; 10-foot sizes; 48 dp side / 27 dp top-bottom overscan margins (suite TV UX convention) |
| Open a file | Native file dialog; "Open With" from Finder / Explorer; command-line argument | System file picker (Storage Access Framework); `ACTION_VIEW` for `video/*` and `audio/*` | `ACTION_VIEW` from other apps (`ks-streamflix`, `ks-tv-launcher`, file managers); system file picker where the TV has one |
| Entry | `desktop/mac`, `desktop/windows` | `mobile/android` | `tv/google-tv` (same APK, leanback launcher entry with a banner) |

The TV layout switches at runtime from `UiModeManager`, so one APK serves phone and TV; `android.software.leanback` and `android.hardware.touchscreen` are declared with `required="false"`.

## 4. First feature set

1. Open a local file (dialog, drag and drop, Open With / `ACTION_VIEW`).
2. Play / pause.
3. Seek: seek bar and keyboard/remote steps.
4. Volume and mute.
5. Full screen.
6. Subtitles: choose an embedded track or none; load an external `.srt`/`.ass`/`.vtt` file.
7. Audio track choice.
8. Recent files: the last 20, newest first, kept in the app's settings; a file that no longer exists is shown greyed and can be removed (a plugin).
9. Language: English and Hindi, in Settings next to "Same as the system".
10. TV series: open a folder (or drop one, or several files); every video in it and its season subfolders plays one after another in natural order (Episode 2 before Episode 10), with previous, next and an episode list. Keys N and P, media next/previous.
11. Playback speed: 0.5× to 2× from the control bar and the Playback menu; keys [ and ]. The speed carries over from one episode to the next.
12. App updates: Settings › App updates, as ks-utility's `specs/app-update.md` defines.

## 5. English and Hindi through `ks-utility`

- The app's text is in `shared/src/commonMain/lang/en.json` and `hi.json`, in the suite translation format; shared words (Open, Settings, Cancel, …) come from `ks-utility`'s catalogue.
- A Gradle task runs `ks-utility`'s `lang.mjs android` on the shared catalogue and the app's files, writing Android-style `strings.xml` files that **Compose Multiplatform resources** read on every platform (desktop and Android alike). They are build output, not committed. A second task runs `lang.mjs check` as part of `./gradlew check`. Both need Node 23.6+ on the build machine; CI installs it.
- The language rule: follow the system (on desktop the first supported language in the system's preferred list), allow a choice in Settings, remember it. On Android the choice uses the system per-app language setting (`locales_config.xml`).
- This needs `ks-utility` **v0.2.4** (the language files arrived in v0.2.0); the app pins v0.1.0 now, so the pin moves in its own commit.

## 6. Module layout and builds

| Gradle project | Folder | Holds |
|---|---|---|
| `:shared` | `shared/` | Kotlin Multiplatform module: common code, `desktopMain`, `androidMain` |
| `:desktop:mac` | `desktop/mac/` | macOS entry point (menu bar, Finder "Open With", VLC.app discovery, software decoding), dmg packaging |
| `:desktop:windows` | `desktop/windows/` | Windows entry point (VLC registry discovery, command-line file), msi packaging |
| `:mobile:android` | `mobile/android/` | The one APK: manifest, activity, phone layout, picture-in-picture |
| `:tv:google-tv` | `tv/google-tv/` | Android library with the TV layout and the leanback banner, used by the APK |

The empty `androidApp/` and `desktopApp/` skeletons are removed. `mobile/ios/` stays empty (not a target). `ks-utility` is included with `includeBuild("ks-utility/kotlin")` and used only from `desktopMain`, `androidMain` and the app modules.

Stack versions (latest stable at the time of writing): Kotlin 2.4.21 (same as `ks-utility`), Compose Multiplatform 1.12.2, Android Gradle Plugin 9.5.2, Media3 1.11.1, JNA 5.19.1, kotlinx-coroutines 1.11.0, JDK 17. Each new dependency is reported and logged as [Dependencies](dependencies.md) asks.

## 7. Packaging

| Platform | Output | Built by | Notes |
|---|---|---|---|
| macOS | `.dmg` with `KS JaiPlayer.app` | `./gradlew :desktop:mac:packageReleaseDmg` (Compose / jpackage) on a Mac | Bundles its own Java runtime. Unsigned for now: the first open needs right-click → Open. Developer ID signing and notarisation later. |
| Windows | `.msi` | `./gradlew :desktop:windows:packageReleaseMsi` on Windows (CI) | Unsigned for now (SmartScreen warns). |
| Android + Google TV | one `.apk` (and `.aab`) | `./gradlew :mobile:android:assembleRelease` | Signed in CI from repository secrets; version from the tag. |

Releases run ks-utility's shared release workflow (`kotlin-app-release.yml`), called from `.github/workflows/release.yml`; versions come from the tag (`-PversionName`, `-PversionCode`), and the files are named `ks-jaiplayer-v<version>.apk`, `-macos-arm64.dmg`, `-windows-x64.msi` for the update check.

## 8. Build order

1. Move the `ks-utility` pin to v0.2.4 (own commit).
2. Gradle build: settings, version catalog, wrapper, the module projects; language-file tasks.
3. Shared core: `PlayerEngine`, `PlaybackState`, `PlayerController`, `PlayerAction`, `RecentFiles`, time formatting, with unit tests.
4. Shared UI.
5. macOS: libVLC binding and engine, video surface, entry point, menus, drag and drop; run it on this Mac with a real file; package a dmg.
6. Windows: entry point and packaging (built and run in CI only; this Mac cannot).
7. Android and Google TV: ExoPlayer engine, activity, phone and TV layouts, picture-in-picture; build the APK; run on an emulator if one is installed.

**First milestone is done when** this plan is approved and the macOS build opens and plays a local video with all nine features above, on shared code ready for the other three, with builds and tests passing.

## 9. Left for later

- Bundle libVLC in the dmg and msi so VLC need not be installed.
- Code signing and notarisation (Developer ID on macOS, Authenticode on Windows).
- Folder and playlist playback, next/previous, resume where you left off.
- Playback speed, A-B loop, frame step, screenshots, chapters.
- Subtitle styling, size and delay; audio delay; equaliser.
- Network streams (URLs) and `ks-streamflix` hand-off testing on real TVs.
- Thumbnails on the seek bar, a GPU video path for 4K on desktop.
- Media session / notification controls on Android, the Media3 FFmpeg extension for audio codecs MediaCodec lacks.
- An in-app browser of the device's videos for TVs without a system file picker.
- iOS is not a target.
