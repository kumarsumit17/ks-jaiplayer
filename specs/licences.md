---
type: Reference
title: Third-party licences
description: The licence of every playback engine and library KS JaiPlayer uses or considered, and whether it fits the app's MIT licence.
tags: [licences, dependencies, engines]
---

# Third-party licences

KS JaiPlayer is MIT-licensed. A component fits when its licence lets an MIT app link and ship it without changing the app's own licence. Checked 2026-11-23.

| Component | Used for | Licence | Fits | How it is used |
|---|---|---|---|---|
| libVLC 3.x (VideoLAN) | Desktop playback | LGPL-2.1-or-later | Yes | Bundled in the desktop installers as separate libraries (libVLC 3.0.24 and only the plugins that declare LGPL and link no GPL library), with `vlc/COPYING.LIB`, `vlc/PLUGINS.txt` (every plugin kept or left out, and why) and the source links. The app uses only this copy, never an installed VLC. Never statically linked. See [Bundled libVLC](plan-bundled-vlc-and-plugins.md). |
| JNA | Calling libVLC from Kotlin | Apache-2.0 or LGPL-2.1 (dual) | Yes | Used under Apache-2.0. |
| Media3 ExoPlayer, Media3 UI Compose | Android and Google TV playback | Apache-2.0 | Yes | |
| Compose Multiplatform, Kotlin, kotlinx-coroutines, AndroidX (including `media3-session` and `tvprovider`) | UI and runtime; media session; Google TV Watch Next | Apache-2.0 | Yes | |
| vlcj | (not used) | GPL-3.0, or a paid commercial licence | No | Would make the shipped desktop app GPL-3.0. Replaced by our own libVLC binding. |
| libmpv | (not used) | GPL-2.0-or-later by default; LGPL-2.1-or-later only in special builds | Only an LGPL build | Not chosen, see [Plan](plan-v1.md). |
| FFmpeg 6.0.1 (`ac3` and `eac3` decoders, libavutil, libswresample) | AC-3 and E-AC-3 sound on Android and Google TV when the device has no working decoder | LGPL-2.1-or-later | Yes, with the obligations below | Built from the pinned source release by `shared/ffmpeg/build-ffmpeg.sh` (no `--enable-gpl`, no `--enable-nonfree`) into `libffmpegJNI.so`, a separate shared library |
| AndroidX Media3 `decoder_ffmpeg` (ported to Kotlin; its JNI file and build script adapted) | The renderer and decoder around FFmpeg | Apache-2.0 | Yes | Source in `shared/ffmpeg`, with its origin and changes noted in each file. The `media3-decoder` library it builds on comes with ExoPlayer |
| IINA | Reference for features only | GPL-3.0 | n/a | No code, icons, screenshots, layout or assets are taken from it. |

## FFmpeg in the Android and Google TV apps (LGPL-2.1)

Checked 2026-11-28. What every release must keep:

- **Licence and notice:** `assets/licences/ffmpeg/COPYING.LGPLv2.1` and `NOTICE.txt` are in each APK and AAB (about 10 KB), naming FFmpeg, its licence, the exact source and how it is built.
- **Source:** the unmodified FFmpeg 6.0.1 release from ffmpeg.org (SHA-256 pinned in `build-ffmpeg.sh`; the release's GPG signature was checked with FFmpeg's release key when it was pinned). If that download ever disappears, the same tarball must be offered from the project.
- **Rebuild and replace:** FFmpeg is only linked into `libffmpegJNI.so`, whose complete source and build scripts are in this public repository, so the library can be rebuilt with a changed FFmpeg and put into the app (re-signed by whoever builds it). Nothing in the app prevents this.
- **No GPL or non-free parts:** the configure options in `build-ffmpeg.sh` stay as they are; adding a decoder or option needs this page updated.
- **Google Play:** the notice travels inside the bundle; no separate step.

## Patents (AC-3 and E-AC-3)

Not legal advice; collected 2026-11-28 from public sources. Dolby's AC-3 patents are reported expired in 2017 ([FFmpeg developers](https://ffmpeg.org/pipermail/ffmpeg-devel/2018-October/234947.html)). The last US E-AC-3 patent (US7516064) is reported to have expired on 30 January 2026 ([Phoronix](https://www.phoronix.com/news/Dolby-Digital-Plus-E-AC3-2026), from Google Patents); other countries were not checked. "Dolby" and its logos are trademarks: the app uses no Dolby branding and claims no Dolby certification. DTS and TrueHD decoders are not included. A legal check is recommended before a store release.
