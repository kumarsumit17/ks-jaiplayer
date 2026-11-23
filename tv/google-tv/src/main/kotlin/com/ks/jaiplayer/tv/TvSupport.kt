package com.ks.jaiplayer.tv

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

/** Whether the app runs on a TV (Google TV, Android TV); the layout switches at runtime from this. */
fun isTelevision(context: Context): Boolean {
    val uiMode = context.getSystemService(UiModeManager::class.java)?.currentModeType
    return uiMode == Configuration.UI_MODE_TYPE_TELEVISION
}
