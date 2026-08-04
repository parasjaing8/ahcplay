package com.aihomecloud.ahcplayer.data.prefs

import android.content.Context
import com.aihomecloud.ahcplayer.ui.platform.TvOverride

private const val PREFS_NAME = "ahc_settings"
private const val KEY_TV_OVERRIDE = "tv_override"

class AppPreferences(context: Context) {
    private val prefs = SecurePrefs.create(context, PREFS_NAME)

    /**
     * The user's answer to "is this a television?", overriding what the device reports.
     *
     * Persisted because the situation it exists for — a device that misreports
     * `FEATURE_LEANBACK` — does not go away when the app restarts, and asking the user to
     * re-answer on every launch would be worse than the bug.
     *
     * Unknown or corrupt stored values fall back to [TvOverride.AUTO] rather than throwing:
     * a bad preference should degrade to trusting the device, not prevent the app starting.
     */
    var tvOverride: TvOverride
        get() = runCatching { TvOverride.valueOf(prefs.getString(KEY_TV_OVERRIDE, null) ?: "") }
            .getOrDefault(TvOverride.AUTO)
        set(value) = prefs.edit().putString(KEY_TV_OVERRIDE, value.name).apply()
}
