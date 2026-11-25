# 2026-11-25

* **Update**: [Dependencies](dependencies.md): new dependency `androidx.tvprovider:tvprovider` 1.1.0 (Apache-2.0), owner's decision 3 in the per-platform plan: writes the Google TV Watch Next row, which has no platform API without it. Adds only `jspecify` 1.0.0; `androidx.annotation` and `androidx.core` were already in the tree.
* **Update**: [Per-platform optimization plan](plan-per-platform.md): step 3 (Google TV) built: Continue watching row, hold to seek, up/down show the controls, frame-rate matching switch. [Plugins](plugins.md): new built-in Resume plugin.
* **Update**: [Per-platform optimization plan](plan-per-platform.md): owner's decisions. Background audio on the phone and the Continue watching row on Google TV: yes (`androidx.tvprovider` added). Windows SMTC: after the other Windows items. [Bundled libVLC and third-party plugins](plan-bundled-vlc-and-plugins.md): section 2 (desktop plugin jars) approved.
* **Update**: [Per-platform optimization plan](plan-per-platform.md): back to one version for every platform (new suite rule). One plain `vX.Y.Z` tag releases all four builds; the per-platform tags and the bridge release are dropped. Each build is still tuned to its platform.
* **Update**: [Per-platform optimization plan](plan-per-platform.md): picture-in-picture buttons (media session on Android 13+, the app's own on Android 8 to 12); cold start measured with the library baseline profiles (about 180 ms on the emulator), so no app-specific profile generator for now.
* **Update**: [Per-platform optimization plan](plan-per-platform.md): TV results recorded. The R8 build plays on the 32 to 85 inch emulators with remote play/pause and no crashes; 100 inch needs a recheck.
* **Update**: [Dependencies](dependencies.md): `androidx.media3:media3-session` 1.11.1 added for the media session of both Android apps (headset, Bluetooth, TV remote, Assistant and picture-in-picture controls); it adds `androidx.media:media` 1.7.0, the rest (media3-common, Guava) was already there. [Constraints](constraints.md): Android audio focus, media session, decoder fallback, picture-in-picture auto-enter and R8. [Per-platform plan](plan-per-platform.md): step 2 tested on the 1080p TV emulator.
* **Update**: [Per-platform plan](plan-per-platform.md): step 1 built (one tag per platform, ks-utility v0.10.0) and step 2 partly built (Android audio focus, media session, decoder fallback, picture-in-picture auto-enter, R8).
* **Creation**: [Desktop engine benchmark](engine-benchmark.md) added: libVLC uses about half the CPU of libmpv in the app's frame path on macOS; the desktop builds stay on libVLC.
* **Update**: [Bundled libVLC](plan-bundled-vlc-and-plugins.md): libVLC's plugin cache is written into the finished app, so the first start after install takes about 1 s instead of over a minute.
* **Creation**: [Per-platform optimization plan](plan-per-platform.md) added: an audit of each build and a per-platform plan (look and input, startup, hardware decoding, packaging, media sessions and OS integrations, accessibility) with an own version per platform; approved by the owner with one tag per platform (`mac-v`, `windows-v`, `android-v`, `google-tv-v`).
* **Update**: [Constraints](constraints.md), [Third-party licences](licences.md) and [Bundled libVLC](plan-bundled-vlc-and-plugins.md): the desktop app plays only through its bundled libVLC and no longer looks for an installed VLC (owner's decision). `JAIPLAYER_VLC_PATH` stays for diagnosis; `-PnoBundledVlc` is gone.
* **Update**: `ks-utility` moved from `v0.7.0` to `v0.9.0` (more languages, the translation tools; [ks-utility translation tools](https://github.com/kumarsumit17/ks-utility/blob/main/specs/translating.md)).
* **Creation**: [Smooth playback](smoothness.md) added: desktop frames as YUV planes converted on the GPU through three rotating buffers (no copies, no shared waiting), coalesced seeks with live scrubbing, playback speed 0.25×–4× with a fine 0.05× control, the `JAIPLAYER_PERF` measurements and a speed test.
* **Update**: [Bundled libVLC and third-party plugins](plan-bundled-vlc-and-plugins.md): section 1 built (owner chose option a). The desktop installers carry libVLC 3.0.24 with only its LGPL plugins, selected from each plugin's declared licence plus a list of plugins that link GPL libraries; `COPYING.LIB` and `PLUGINS.txt` ship with it. Section 2 still awaits the owner.
* **Update**: [Constraints](constraints.md) and [Third-party licences](licences.md): desktop no longer needs VLC installed; the loader tries `JAIPLAYER_VLC_PATH`, an installed VLC, then the bundled copy.

# 2026-11-24

* **Creation**: [Bundled libVLC and third-party plugins](plan-bundled-vlc-and-plugins.md) added: proposal for bundling libVLC (LGPL plugins only, recommended) and desktop-only third-party plugin jars, awaiting the owner's choice.
* **Creation**: [Google Play](google-play.md) added: the Play bundles, the listing in `shared/play-listing/` (English and Hindi text, icon, feature graphic, TV banner, phone and TV screenshots), `PRIVACY.md`, the Play Console answers and the owner's steps.
* **Update**: [Constraints](constraints.md): the AABs are the Google Play build (`play` build type: no in-app updater, no `REQUEST_INSTALL_PACKAGES`); the APKs keep the updater. Releases also build an Intel dmg (ks-utility v0.7.0 `mac-intel`).
* **Update**: [Constraints](constraints.md): the Google TV app opens files through an in-app media browser (MediaStore, USB drives included, D-pad navigable) instead of the system picker many TVs lack; http(s) streams in Recent files show their host instead of "File not found".
* **Update**: [Constraints](constraints.md) and [Module layout](module-layout.md): separate phone and Google TV apps from v1.2.0. `mobile/android` and `tv/google-tv` are application modules with the same package name and keystore (TV versionCode + 1,000,000,000 for Google Play); the activity, manifest, pickers and updates moved to `shared/src/androidMain`; each app updates to its own build (ks-utility v0.6.0 `GOOGLE_TV`). v1.1.0 shipped the new update checker with one APK first, so installed copies choose the right file.

# 2026-11-23

* **Update**: [Constraints](constraints.md): `ACTION_VIEW` also accepts `http`/`https` stream links (ks-streamflix's "Open in KS JaiPlayer"), with plain http allowed and `EXTRA_TITLE` as the name.
* **Update**: Release: tag `v1.0.0` was never released: its run built and verified every file, but the shared workflow (ks-utility `v0.4.0`) named the signed APK `-UNSIGNED` and the publish check stopped it. The first release is `v1.0.1`, with ks-utility `v0.4.1`.
* **Creation**: [Plugins](plugins.md) added: a minimal core, the `JaiPlugin` API, the built-in Recent files and Tracks plugins.
* **Update**: [Plan for the first milestone](plan-v1.md): plugins, TV series and playback speed in the core, app updates, the shared release workflow, software decoding on macOS, first release v1.0.0.
* **Update**: [Overview](overview.md), [Constraints](constraints.md), [Module layout](module-layout.md): the built layout and engines (libVLC through JNA instead of vlcj).
* **Update**: [Dependencies](dependencies.md): first dependencies added, each with its reason: Compose Multiplatform (runtime, foundation, ui, material3 1.9.0, resources) for the one shared interface; kotlinx-coroutines (core, swing, android) for the engines' threads; JNA 5.19.1 to call libVLC without native code; Media3 1.11.1 (exoplayer, ui-compose) for Android and TV playback; androidx.activity-compose for the Android entry point. ks-utility pinned to v0.3.0 (language files, app updates).
* **Creation**: [Plan for the first milestone](plan-v1.md) approved by the owner: libVLC through our own JNA binding on desktop (instead of GPL-3.0 vlcj), Media3 ExoPlayer on Android and Google TV, one shared `PlayerEngine` interface and Compose Multiplatform UI.
* **Creation**: [Third-party licences](licences.md) added.

# 2026-11-21

* **Update**: [Dependencies](dependencies.md): `ks-utility` added as a git submodule for shared generic utilities.
* **Initialization**: `specs/` created as an Open Knowledge Format (OKF) v0.2 bundle with this log and `index.md`.
* **Creation**: [Overview](overview.md), [Constraints](constraints.md), [Module layout](module-layout.md) added as drafts, from `README.md` and `CLAUDE.md`.
* **Creation**: [Dependencies](dependencies.md) added: third-party dependencies are kept to the minimum.
