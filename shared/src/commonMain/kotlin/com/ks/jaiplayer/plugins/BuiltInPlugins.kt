package com.ks.jaiplayer.plugins

import com.ks.jaiplayer.plugin.JaiPlugin
import com.ks.jaiplayer.plugins.recent.RecentFilesPlugin
import com.ks.jaiplayer.plugins.tracks.TracksPlugin

/** The plugins every build of KS JaiPlayer ships with. An entry point may pass fewer. */
fun builtInPlugins(): List<JaiPlugin> = listOf(RecentFilesPlugin(), TracksPlugin())
