package com.aihomecloud.ahcplayer.data.prefs

import android.content.Context

private const val PREFS_NAME = "ahc_settings"
private const val KEY_TMDB_API_KEY = "tmdb_api_key"

class AppPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTmdbApiKey(): String? = prefs.getString(KEY_TMDB_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun setTmdbApiKey(key: String?) {
        if (key.isNullOrBlank()) {
            prefs.edit().remove(KEY_TMDB_API_KEY).apply()
        } else {
            prefs.edit().putString(KEY_TMDB_API_KEY, key.trim()).apply()
        }
    }
}
