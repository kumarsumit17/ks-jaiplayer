---
type: Plan
title: Per-platform optimization plan
description: Approved plan for making each KS JaiPlayer build (macOS, Windows, Android phone, Google TV) its own app with its own version, tuned to its OS for look, input, performance, media stack, packaging, OS integrations and accessibility.
tags: [plan, platforms, macos, windows, android, google-tv, accessibility, performance]
---

# Per-platform optimization plan

**Status: approved by the owner on 2026-11-25.** Versions: one tag per platform (`android-v`, `google-tv-v`, `mac-v`, `windows-v`), with a plain `vX.Y.Z` still releasing all four (suite rule, KS-Suite [versioning-signing](https://github.com/kumasumit/KS-Suite/blob/main/specs/versioning-signing.md), "Platform tags"). Decisions 2 to 5 are recorded in section 3. Built steps are marked under section 4.

Goal: each build is the best media player on its platform, not one lowest-common-denominator build. The shared player core, plugin API and interface stay in `shared/` (written once); what differs per OS goes in that platform's folder, or in `shared/src/androidMain` when both Android apps need it (see [Constraints](constraints.md), [Module layout](module-layout.md)).

## 1. Audit: what each build does today (v1.2.x, 2026-11-25)

| | macOS (`desktop/mac`) | Windows (`desktop/windows`) | Android phone (`mobile/android`) | Google TV (`tv/google-tv`) |
|---|---|---|---|---|
| Entry point | `Main.kt`: screen menu bar, app name, Finder "Open With" (`APP_OPEN_FILE`), `AppleLanguages`, native folder dialog, ⌘ shortcuts | `Main.kt`: command-line file only, `Locale.getDefault()` | Launcher entry only; everything in `shared/src/androidMain` | Leanback entry, banner, `FORM_FACTOR=tv`; everything else shared |
| Look | Compose UI, dark Aqua window, system menu bar | Compose UI inside a Swing frame; Swing (Metal) menu bar and `JFileChooser` for folders; light title bar | Edge-to-edge, immersive in the player | 1.4× type, 48/27 dp overscan margins, focus rings |
| Input | Keyboard map shared with TV (`Keys.kt`), ⌘O/⌘⇧O/⌘W, ⌃⌘F full screen, drag and drop | Same map, Ctrl shortcuts, F11, drag and drop | Tap shows controls; double tap left/right seeks 10 s | D-pad map, controls take focus, Back closes |
| Engine | libVLC 3.0.24 (bundled, LGPL plugins) through JNA; frames as YUV planes to the GPU ([Smooth playback](smoothness.md)) | Same | Media3 ExoPlayer 1.11.1, default `ExoPlayer.Builder` | Same as phone |
| Hardware decoding | **Off**: `--avcodec-hw=none`, because subtitles cannot be blended onto VideoToolbox frames through callbacks | libVLC default (`any`); not verified or measured | Device decoders (MediaCodec); no decoder fallback set | Same; no frame-rate matching choice, passthrough not checked |
| Audio focus | n/a | n/a | **Not handled**: no `AudioAttributes`, keeps playing over calls and other apps; no "becoming noisy" pause | Same |
| Media session | **None**: no Now Playing, media keys work only while the window has focus | **None**: no SMTC, no media flyout | **None**: no lock-screen or notification controls; PiP has no play/pause buttons | **None**: remote media keys only reach the app in front; no Assistant "pause" |
| Picture-in-picture | n/a | n/a | `onUserLeaveHint` while playing; no `setAutoEnterEnabled`, no actions | Not used (TV) |
| Keep awake while playing | Not done (display may sleep) | Not done | `FLAG_KEEP_SCREEN_ON` in the player | Same |
| File types | `document-types.plist` (Open With) | **No file associations** in the msi; no single instance (a second "Open with" starts a second app) | `ACTION_VIEW` `video/*` `audio/*` (content, file, http, https) | Same plus in-app media browser |
| Recent files | Recent files plugin (in app) | Same; nothing in the taskbar jump list | Same | Same |
| Packaging | dmg (arm64; Intel dmg from the release workflow), jlink `java.prefs java.desktop jdk.unsupported`, ProGuard off | msi, same modules, ProGuard off | APK + AAB, **R8 off** (`isMinifyEnabled = false`), no native libraries | Same; versionCode + 1,000,000,000 |
| Version | One tag for all four builds (`vX.Y.Z`) | Same | Same | Same + offset |
| Accessibility | Labels on some controls only (`contentDescription` on two); the player's controls are taken out of keyboard focus (`desktopNoFocus`) | Same, and **`jdk.accessibility` is not in the runtime**, so the Java Access Bridge (Narrator, NVDA) cannot see the app | TalkBack sees Compose semantics; same label gaps | Same; focus order follows layout |

Other findings: `desktop/mac/hs_err_pid46603.log` (a JVM crash log, git-ignored) shows a native crash during development; worth a look in the macOS step. The release workflow still calls ks-utility's workflow at `@v0.7.0` while the submodule is `v0.9.0`; the separate Google TV builds and AABs it needs are in v0.7.0, so this is fine, but it moves with the versioning step.

## 2. The plan, per platform

Each item names its folder. "New dependency" items are reported as [Dependencies](dependencies.md) requires.

### 2.1 macOS (`desktop/mac/`)

- **Look and feel and input**
  - Transparent title bar with the content under it (`apple.awt.fullWindowContent`, `apple.awt.transparentTitleBar`), so the video fills the window like QuickTime; traffic lights stay.
  - Native full screen (green button and ⌃⌘F) into its own Space; already `WindowPlacement.Fullscreen`, checked.
  - Menu bar completed to macOS conventions: **File ▸ Open Recent** (from the Recent files plugin, with Clear Menu), **Window** menu (Minimize ⌘M, Zoom), **Help** opens the GitHub page; ⌘, opens Settings through the system app menu (already `systemAppMenu`).
  - Dock menu with play/pause and the recent files.
  - Trackpad: two-finger horizontal swipe seeks, pinch toggles full screen.
- **Performance and startup**: measure cold start to first frame on Apple silicon (target under 1.5 s); load libVLC in parallel with the first window; try an AppCDS archive created at build time (JDK 17 `-XX:ArchiveClassesAtExit`, then `-XX:SharedArchiveFile` in the app's `jvmArgs`); keep it only if it saves more than 20%.
- **Hardware decoding**: try VideoToolbox with copy-back to system memory (decoder output converted to `I420` before the subtitle blend) and check that subtitles still draw. Keep it only if subtitles work and CPU use for 4K HEVC drops; otherwise software stays, as today. Measured with `JAIPLAYER_PERF=1`.
- **Packaging and size**: universal or per-architecture dmg (now arm64 + Intel), report sizes per release; check which libVLC plugins are never used by a media player (e.g. capture, streaming output) and leave them out; try ProGuard on the release build (Compose Desktop supports it) and keep it only if playback tests pass.
- **OS integrations** (all through JNA, already a dependency; no native code to build):
  - **Now Playing and media keys**: `MPNowPlayingInfoCenter` (title, duration, position, rate) and `MPRemoteCommandCenter` (play, pause, toggle, next, previous, skip ±10 s, change position), through the Objective-C runtime (`objc_msgSend`, a runtime class with JNA callbacks as the target/action). The Control Centre, the menu-bar Now Playing and the keyboard's media keys then work while the app is in the background, and AirPods' controls too.
  - **Display stays awake while playing**: `IOPMAssertionCreateWithName` (`PreventUserIdleDisplaySleep`), released on pause and close.
  - Hand-off point: a `MediaSessionBridge` interface in `shared/src/desktopMain` that the entry point gives to `runDesktopApp`; the macOS implementation lives in `desktop/mac/`.
- **Accessibility**: VoiceOver labels on every control, the slider's value read as time, keyboard focus on controls with Tab (arrows still seek when the video has focus), Reduce Motion honoured for the controls' fade.

### 2.2 Windows (`desktop/windows/`)

- **Look and feel and input**
  - The system look and feel for the Swing parts (menu bar, dialogs): `UIManager.getSystemLookAndFeelClassName()` before the window opens.
  - Dark title bar to match the app (`DwmSetWindowAttribute`, `DWMWA_USE_IMMERSIVE_DARK_MODE`) and the Mica backdrop where Windows 11 supports it.
  - The native folder picker (`IFileOpenDialog` with `FOS_PICKFOLDERS`) instead of `JFileChooser`.
  - Windows keys: Ctrl+O, Ctrl+Shift+O, Ctrl+W, F11 and Alt+Enter for full screen, Esc leaves it, Alt+F4 quits; the mouse wheel over the video changes volume; double click toggles full screen (already).
- **Performance and startup**: the same measurement and AppCDS check as macOS, on CI and on the owner's Windows PC.
- **Hardware decoding**: check that libVLC uses D3D11VA (or DXVA2) with copy-back through the callbacks, and set `--avcodec-hw=d3d11va` explicitly if it does not; measure 4K HEVC CPU use. Software stays the fallback.
- **Packaging and size**: file associations in the msi (Compose `windows { fileAssociation(…) }` for the same types as the macOS plist), so "Open with" and double click work; the same plugin trimming and ProGuard check as macOS; per-user install by default so no admin prompt (`perUserInstall`).
- **OS integrations**
  - **Single instance**: a second launch (Explorer, "Open with") hands its files to the running app over a local loopback socket and exits; the window comes to the front.
  - **Jump list**: `SHAddToRecentDocs` for each opened file, so the taskbar and Start jump list show recent videos once the file associations exist.
  - **System Media Transport Controls (SMTC)**: title, position, play/pause/next/previous through `ISystemMediaTransportControlsInterop::GetForWindow` (WinRT through JNA COM calls). This is the largest Windows item; it gets its own step and may come after the others.
  - Taskbar thumbnail buttons (previous, play/pause, next) through `ITaskbarList3`, after SMTC.
  - Display stays awake while playing: `SetThreadExecutionState(ES_DISPLAY_REQUIRED | ES_CONTINUOUS)`.
- **Accessibility**: add `jdk.accessibility` to the runtime modules so Narrator and NVDA see the app; the same labels and Tab focus as macOS; Windows High Contrast detected and the theme switched to a high-contrast palette.

### 2.3 Android phone (`mobile/android/`, shared Android code in `shared/src/androidMain`)

- **Look and feel and input (touch)**
  - Gestures on the video: horizontal drag seeks with a time preview, vertical drag on the right half changes volume and on the left half brightness, pinch switches fit/fill (crop to the screen), long press plays at 2× while held.
  - Landscape lock while a video plays (follows the video's aspect), predictive back, Material You dynamic colour on Android 12+ outside the player.
- **Performance and startup**: R8 and resource shrinking on the `release` and `play` builds (keep rules for JNA-free Android code are simple); a baseline profile for start-up and the player screen (Compose already brings `profileinstaller`); target cold start under 800 ms on a mid-range phone.
- **Hardware decoding and media stack**: `DefaultRenderersFactory` with decoder fallback on (`setEnableDecoderFallback(true)`), so a file the first hardware decoder rejects tries the next one; `AudioAttributes` (`USAGE_MEDIA`, `CONTENT_TYPE_MOVIE`) with `handleAudioFocus = true` and `setHandleAudioBecomingNoisy(true)` (pause when headphones are unplugged). No FFmpeg extension (native code, licences): formats the phone cannot decode stay "cannot open".
- **Packaging and size**: R8 (above); report APK/AAB size per release; no native libraries today, so no ABI splits are needed.
- **OS integrations**
  - **Media session** (`androidx.media3:media3-session`, new dependency): lock-screen and notification controls, Bluetooth headset buttons, Android Auto-free media controls in the quick settings panel, and play/pause/next actions in picture-in-picture.
  - **Picture-in-picture**: `setAutoEnterEnabled(true)` and `setSeamlessResizeEnabled` on Android 12+ (smooth swipe-up to PiP), the old `onUserLeaveHint` path kept for Android 8–11; the source rectangle hint so the animation starts from the video.
  - Background audio for audio files (playback continues with the screen off) through a `MediaSessionService` with the `mediaPlayback` foreground-service type; video pauses as today. Needs `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and a Play Console declaration; the owner decides (section 3).
- **Accessibility**: TalkBack labels and actions on every control (custom actions "seek forward 10 s" on the slider), the system caption style (`CaptioningManager`) for subtitles, font scale up to 200% without clipping, touch targets at least 48 dp.

### 2.4 Google TV (`tv/google-tv/`, shared Android code in `shared/src/androidMain`)

- **Look and feel and input (D-pad, 10-foot)**
  - Keep the 1.4× scale and overscan margins; check every screen at 32–100 inch (720p tvdpi to 4K).
  - Hold left/right to seek faster (10 s, then 30 s, then 1 min steps) with a thumbnail-free time preview; up opens Tracks, down shows the controls; the Back rules stay.
  - Playback speed and tracks in a side panel that keeps focus inside it.
  - Follow the Google TV app quality guidelines: no touch-only actions, nothing needing a pointer, a visible focus on every focusable element, the banner (already).
- **Performance and startup**: the same R8 and baseline profile as the phone (they share the code); target cold start under 1.5 s on a Chromecast with Google TV class device; the media browser loads folders page by page.
- **Hardware decoding and media stack**:
  - **Frame-rate matching**: a setting "Match the video's frame rate" (off / seamless only / always), through `setVideoChangeFrameRateStrategy`; default seamless only, as Google TV recommends.
  - **Audio passthrough**: check that AC-3, E-AC-3 and DTS go to an AV receiver or TV over HDMI (ExoPlayer's `AudioCapabilities`), and note that a speed other than 1× decodes instead.
  - **HDR**: a `SurfaceView` (already Media3's default) so HDR10/HLG/Dolby Vision reach the display; check the surface type stays `SurfaceView`.
  - Tunnelled playback for 4K on low-end TVs: tried behind a setting, kept only if it helps on the owner's TV.
- **Packaging and size**: R8 (above); the TV AAB carries no phone-only resources.
- **OS integrations**
  - **Media session** (same `media3-session`): the remote's play/pause and the Google Assistant ("pause", "skip forward 30 seconds") reach the app; the TV's "now playing" card shows the title.
  - **Continue watching** on the Google TV home screen through the Watch Next channel (`androidx.tvprovider`, new dependency), for files with a saved position. Optional; the owner decides (section 3).
  - Leanback rules kept: `android.software.leanback` required, touchscreen not required, `LEANBACK_LAUNCHER`, banner 320×180, no portrait.
- **Accessibility**: TalkBack on TV reads every focused control; focus order matches the visual order; captions follow the TV's caption settings; high-contrast text honoured.

### 2.5 Shared (`shared/`)

- `MediaSessionBridge` (desktop) and the Media3 session (Android) are fed from one place: `JaiPlayerState`'s playlist and the engine's `PlaybackState`, so every platform shows the same title, position and commands.
- A per-platform settings section ("This device"): frame-rate matching (TV), background audio (phone), hardware decoding (desktop, for diagnosis).
- New interface text goes into `shared/src/commonMain/lang/en.json` and `hi.json` with the Hindi style guide; the language-file check runs in the tests.
- The keyboard map stays one map (`Keys.kt`); platform gestures only add to it.

## 3. Decisions for the owner

1. **Versions: decided, one tag per platform.** `android-vX.Y.Z`, `google-tv-vX.Y.Z`, `mac-vX.Y.Z` and `windows-vX.Y.Z` each build and release only their platform; a plain `vX.Y.Z` still releases all four. A platform's version is the higher of its own latest tag and the latest plain tag. On 2026-11-25 the owner briefly chose one shared version instead (0368fb6), then went back to platform tags (KS-Suite d373527); no release was made with platform tags in between. **Bridge release:** v1.6.0 was a plain release whose builds already read the release list (the updater change of 451acf7 was kept), so installed v1.6.0 copies find platform releases and no further bridge release is needed.
2. **Phone background audio: decided yes (2026-11-25).** Audio files keep playing with the screen off or the app in the background, through a `MediaSessionService` with the `mediaPlayback` foreground-service type and its Play Console declaration (section 2.3). Video still pauses, as picture-in-picture covers it.
3. **Continue watching on Google TV: decided yes (2026-11-25).** The Watch Next channel (`androidx.tvprovider`) lists unfinished files on the Google TV home screen (section 2.4).
4. **New dependencies: decided.** `androidx.media3:media3-session` (added in step 2) and `androidx.tvprovider` (step 3, since 3 is yes). Desktop items use JNA, already a dependency.
5. **Windows SMTC: decided later (2026-11-25).** It comes after the other Windows items ship, in its own step; taskbar thumbnail buttons follow it.

## 4. Build order and testing

Each step is one or more commits on `dev`, tested on its platform before the next. Nothing is pushed or tagged without the owner's OK.

| Step | What | Where | Tested on |
|---|---|---|---|
| 1 | Versioning per platform | suite `specs/`, ks-utility (v0.10.0), this repo's workflow and `build.gradle.kts` | ks-utility tests; a rehearsal run per platform |
| 2 | Android media stack: audio focus, noisy, decoder fallback, media session, PiP auto-enter and actions, R8, baseline profile | `shared/src/androidMain`, root build | Phone emulator; every TV emulator 32–100 inch (`scripts/emulators.sh`) |
| 3 | Google TV: hold-to-seek, frame-rate setting, passthrough and HDR checks, side panels, (Watch Next) | `shared/`, `tv/google-tv` | Every TV emulator; the owner's 55 inch TV for passthrough and frame rate |
| 4 | Phone: gestures, landscape, PiP polish, (background audio) | `shared/src/androidMain`, `mobile/android` | Phone emulator, a real phone if available |
| 5 | macOS: title bar, menus, Now Playing and media keys, stay awake, VideoToolbox check, startup | `desktop/mac`, `shared/src/desktopMain` | This Mac (`:desktop:mac:run`, the dmg), `JAIPLAYER_PERF=1` |
| 6 | Windows: look, file associations, single instance, jump list, stay awake, `jdk.accessibility`, hardware decoding check, (SMTC) | `desktop/windows`, `shared/src/desktopMain` | CI build of the msi; hand check by the owner on Windows (never blocks a release) |
| 7 | Accessibility pass on all four, interface text in English and Hindi | `shared/`, each platform | TalkBack (phone, TV), VoiceOver (Mac), Narrator by the owner |

**Built so far (2026-11-25):**
- Step 1: tags `android-vX.Y.Z`, `google-tv-vX.Y.Z`, `mac-vX.Y.Z` and `windows-vX.Y.Z` release one platform, while a plain `vX.Y.Z` still releases all of them (ks-utility v0.10.0, `platform` input for rehearsals). Each platform's version is the higher of its own latest tag and the latest plain tag (`appVersionName.<platform>`). The updater reads the release list, so a platform release does not have to be "latest". Undone for a day by 0368fb6 (one shared version) and restored when the owner brought platform tags back; v1.6.0, released in between with a plain tag, is the bridge release.
- Step 2, partly: audio focus, pause when headphones are unplugged, decoder fallback, the media session (headset, Bluetooth, remote and Assistant controls, now playing), picture-in-picture auto-enter on Android 12+, and R8 on the release and Play builds (APK about 9 MB → 1.8 MB). Tested on the phone emulator with the R8 build: playback, media-key pause/play, Home into picture-in-picture, no errors. Tested on the 1080p TV emulator (`ks-tv-36`) with the R8 build: playback, the title in the session, the remote's play/pause and the session's play command, the controls with focus, no errors. Every TV size, with the R8 release build signed with the debug key (the owner allowed the batch run): 32, 43, 50, 55, 65, 75 and 85 inch all play, the remote's play/pause works, the controls lay out the same, and nothing crashed. At 100 inch the screenshot showed the video still paused at 0:00, probably the slow 4K emulator's timing; it needs a recheck. Picture-in-picture buttons: on Android 13 and later the window shows play/pause (and previous/next for a series) from the media session; on Android 8 to 12 the app adds its own buttons (back 10 s, play/pause, forward 10 s; `PipActions.kt`), never both. Tested on the Android 16 phone emulator with the R8 build: Home moves the video into the window, its pause and play reach the player, no duplicate buttons. The Android 8 to 12 buttons are built but not run, because no emulator image older than Android 16 is installed. Baseline profile: the APK already carries the profiles Compose and Media3 ship, which Google Play and `profileinstaller` compile. Cold start on the phone emulator, median of five: about 650 ms uncompiled, about 180 ms with them, well under the 800 ms target. So no app-specific profile generator (a benchmark module and new test dependencies) is added for now; it can come later if a real device misses the target. Still to do in step 2: the 100 inch recheck.
- Step 3 (Google TV), built: **Continue watching**: a built-in Resume plugin remembers where each long file was left and opens it there (all platforms); on Google TV the unfinished files (up to 10) go to the home screen's Watch Next row (`WatchNext.kt`, `androidx.tvprovider`), each with a frame from where it was left (served read only by `ThumbnailProvider`) and its progress; one the viewer removes comes back only after it is watched again. **Hold to seek**: holding left or right (or an arrow key on desktop) moves 10 s per repeat, then 30 s, then 1 min (`SeekHold`); controls brought up by a seek key open on the seek bar, so holding keeps seeking. **Up and down** on the remote show the controls (the remote's own volume keys change the TV's volume). **Frame-rate matching**: a "This device" switch in Settings, on by default (Media3's seamless-only strategy; Media3 offers no "always"). **HDR**: the video stays on a `SurfaceView`. **Audio passthrough**: Media3 sends AC-3, E-AC-3 and DTS to the TV or receiver when the HDMI chain reports it (its default `AudioCapabilities`); a speed other than 1× decodes instead. Tested on the 55 inch emulator (4K) with the R8 build: resume at the saved position, the file in Google TV's Play Next row with its frame and progress bar, eight quick right presses moved 100 s. Not done: playback speed and tracks in a side panel (the menus keep focus inside them already), tunnelled playback (needs the owner's TV), passthrough on a real receiver (hand check).
- Step 4 (phone), built: **background audio** (owner's decision): audio files keep playing with the screen off or the app in the background through `PlaybackService` (Media3 `MediaSessionService`, foreground-service type `mediaPlayback`, the system media notification), with a "This device" switch; video still pauses. **Gestures** on the video (`PhoneGestures.kt`): drag sideways to seek (the width is 90 s) with the target time shown, drag up/down on the right half for volume and on the left half for the window's brightness (given back when the player closes), pinch to fill or fit the screen, long press for 2× while held, double tap to seek 10 s (already). **Orientation**: a video plays in its picture's orientation, either way round by the sensor. **Predictive back** is on. Tested on the Android 16 phone emulator with the R8 build: playback continued after Home and with the screen off (foreground service, media notification), a 16:9 video turned the screen to landscape, a half-width drag seeked 45 s, a long press played at 2× and went back to 1×. Not done: Material You colours (the suite keeps its own shadcn look), the picture-in-picture source rectangle hint. The Play Console needs the `FOREGROUND_SERVICE_MEDIA_PLAYBACK` declaration ([Google Play](google-play.md)).
- Step 5 (macOS), built: **Now Playing and media keys** (`MacIntegration.kt`, through JNA and the Objective-C runtime): Control Centre and the menu bar's Now Playing show the title, length, position and rate, and the keyboard's media keys, AirPods and Control Centre play, pause, skip ±10 s and go to the next or previous episode (`MPRemoteCommandCenter`, next/previous enabled only when the series has one). **The display stays awake** while a video plays (IOKit `PreventUserIdleDisplaySleep`), released on pause and quit. **Window and Help menus** (Minimize ⌘M, Zoom; KS JaiPlayer on GitHub). The shared hand-off point is `DesktopIntegration` in `shared/src/desktopMain`. Tested on this Mac: `pmset -g assertions` shows the assertion while playing and none after quitting; the system's Now Playing reported the title, length and rate; a simulated play/pause media key reached the player. Not done yet: transparent title bar, Dock menu, trackpad gestures, AppCDS start-up, VideoToolbox, ProGuard.

Windows cannot be run from this Mac, so step 6 is checked by its CI build, its unit tests and the owner's hand check.
