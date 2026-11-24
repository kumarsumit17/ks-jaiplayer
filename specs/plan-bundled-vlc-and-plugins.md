---
type: Plan
title: Bundled libVLC and third-party plugins
description: Proposal, awaiting the owner's choice, for shipping libVLC inside the desktop apps and for letting people add their own plugins.
tags: [plan, desktop, libvlc, plugins, licences]
---

# Bundled libVLC and third-party plugins

Status: **proposed** (2026-11-24). Two decisions for the owner; nothing is built yet.

## 1. libVLC inside the desktop apps
Today the Mac and Windows apps need VLC 3.x installed and find it at run time ([Constraints](constraints.md)). Bundling makes the app work right after install.

**How.** The release workflow downloads the official VLC 3.0.x builds from videolan.org at a pinned version, checks their SHA-256, and copies libVLC and its plugins into the app through jpackage's app resources. macOS: `libvlc.dylib`, `libvlccore.dylib` and `plugins/`, from the Apple-silicon and the Intel VLC. Windows: `libvlc.dll`, `libvlccore.dll` and `plugins\` from the 64-bit zip. The loader tries the bundled copy first and an installed VLC second. Each installer grows by about 80–100 MB.

**Licences, the decision.** libVLC and most of VLC's plugins are LGPL-2.1-or-later, which fits the app's MIT licence when shipped as separate libraries with their licence text and a source offer ([Third-party licences](licences.md)). A few of VLC's plugins are GPL, because of the libraries they link. Options:
- a. **Bundle only the LGPL plugins** *(recommended)*. Plugins whose licence is GPL, or unclear, are left out. Formats they handle may need an installed VLC, which the loader still finds. The app stays MIT without question. The installer ships `COPYING.LIB`, the plugin list with licences, and a link to the exact VLC source.
- b. **Bundle every plugin**, with all licence texts and the source link. Most formats work out of the box, but the GPL plugins put the shipped bundle in a grey area for an MIT app.
- c. **Don't bundle**: keep requiring an installed VLC (today's behaviour).

## 2. Third-party plugins
The app is a small core plus `JaiPlugin`s ([Plugins](plugins.md)); today all plugins are built in.

**Proposal.**
- **Desktop only.** A plugin is a `.jar` in the app's plugins folder (`~/Library/Application Support/KS JaiPlayer/plugins`, `%APPDATA%\KS JaiPlayer\plugins`), found with Java's `ServiceLoader` through `JaiPlugin`.
- **Versioned API.** `JaiPlugin` gets an `apiVersion`; a plugin built for another major version is not loaded, and Settings says why.
- **Visible and switchable.** Settings lists every plugin, built-in or added, with an on/off switch. Added plugins are off until the user turns them on. A plain warning says a plugin runs with the app's full rights.
- **No store, no downloads.** The user copies the file in by hand.
- **Not on Android or Google TV.** Loading code from files breaks Google Play's rules, so the Android apps keep built-in plugins only.
- **For plugin authors:** a small sample plugin and a page on writing one.

## To do after the choice
Build, test on macOS (both chips on CI) and Windows (CI), and release, following the [release cycle](https://github.com/kumarsumit17/KS-Suite/blob/main/specs/release-cycle.md). Hand checks don't block the release.
