# 2026-11-24

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
