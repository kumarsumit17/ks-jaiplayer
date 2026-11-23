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
| libVLC 3.x (VideoLAN) | Desktop playback | LGPL-2.1-or-later | Yes | Loaded at run time from an installed VLC; never statically linked. When it is bundled later, the installer ships its licence text and the source offer. |
| JNA | Calling libVLC from Kotlin | Apache-2.0 or LGPL-2.1 (dual) | Yes | Used under Apache-2.0. |
| Media3 ExoPlayer, Media3 UI Compose | Android and Google TV playback | Apache-2.0 | Yes | |
| Compose Multiplatform, Kotlin, kotlinx-coroutines, AndroidX | UI and runtime | Apache-2.0 | Yes | |
| vlcj | (not used) | GPL-3.0, or a paid commercial licence | No | Would make the shipped desktop app GPL-3.0. Replaced by our own libVLC binding. |
| libmpv | (not used) | GPL-2.0-or-later by default; LGPL-2.1-or-later only in special builds | Only an LGPL build | Not chosen, see [Plan](plan-v1.md). |
| IINA | Reference for features only | GPL-3.0 | n/a | No code, icons, screenshots, layout or assets are taken from it. |
