package com.aihomecloud.ahcplayer.data.prefs

import android.content.Context

private const val PREFS_NAME = "ahc_settings"
class AppPreferences(context: Context) {
    private val prefs = SecurePrefs.create(context, PREFS_NAME)

}
