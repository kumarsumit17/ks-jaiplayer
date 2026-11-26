---
type: Playbook
title: Writing a desktop plugin
description: How to build a third-party KS JaiPlayer plugin as a jar for the macOS and Windows apps, and how the app finds, switches and loads it.
tags: [plugins, desktop, playbook, api]
---

# Writing a desktop plugin

The macOS and Windows apps load plugins from jars the user copies in. The Android and Google TV apps keep their built-in plugins only, because Google Play does not allow loading code from files. The plugin API is the one the built-in plugins use ([Plugins](plugins.md)).

## The jar
- A class that implements `com.ks.jaiplayer.plugin.JaiPlugin`, with a constructor that takes no arguments. Give it an `id`, a `name`, and keep `apiVersion` at the default (`PLUGIN_API_VERSION`, now **1**).
- `META-INF/services/com.ks.jaiplayer.plugin.JaiPlugin` naming that class (Java's `ServiceLoader`).
- Manifest attributes, read **without running any of the jar's code**:
  - `JaiPlayer-Plugin-Id`: the plugin's id, lowercase letters, digits, dots and dashes (for example `sample.loop`); it must match `JaiPlugin.id` and must not be a built-in id;
  - `JaiPlayer-Plugin-Name`: the name shown in Settings;
  - `JaiPlayer-Plugin-Api`: the API version it was built for.
- Compile against the app (`compileOnly`) and bundle nothing the app already has (Kotlin, Compose, coroutines, JNA).

The sample is `desktop/plugin-sample/` (Loop: the L key or Playback › Loop starts a file again when it ends). Build it with `./gradlew :desktop:plugin-sample:jar`; the jar is in `desktop/plugin-sample/build/libs/`.

## What a plugin can do
Through `PluginContext`: add a control to the player bar, a home-screen section, a desktop menu entry, a key binding (core keys cannot be taken over), listen for opened files, read the engine's state and use the controller, and keep its own settings (`plugin.<id>.*`). See `shared/src/commonMain/kotlin/com/ks/jaiplayer/plugin/Plugin.kt`.

## How the app handles it
- **Folder**: macOS `~/Library/Application Support/KS JaiPlayer/plugins`, Windows `%APPDATA%\KS JaiPlayer\plugins`. Settings › Plugins › Open folder opens it.
- **Off until turned on**: Settings lists every plugin, built-in or added, with a switch. Built-in plugins start on, added ones off, with a plain warning that an added plugin runs with all of the app's rights. Changes apply the next time the app starts.
- **Loading**: only plugins that are on and whose `JaiPlayer-Plugin-Api` equals the app's version are loaded, each jar in its own class loader. A jar without the manifest attributes, with a built-in id, or for another API version is listed with the reason and cannot be switched on. A plugin that fails to load is skipped and the app starts without it.
- **No store, no downloads**: the user copies the file in by hand.

## When the API changes
`PLUGIN_API_VERSION` goes up when `JaiPlugin` or `PluginContext` change in a way that breaks existing plugins. Plugins built for the old version then show "Made for another version of KS JaiPlayer" until their author rebuilds them.
