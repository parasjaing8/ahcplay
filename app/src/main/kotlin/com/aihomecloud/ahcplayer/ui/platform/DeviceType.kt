package com.aihomecloud.ahcplayer.ui.platform

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Whether this app is driving a television.
 *
 * The answer changes behaviour that is not merely cosmetic. On TV the profile row auto-selects
 * after a timer, because there is no touch and a D-pad user expects the highlighted item to
 * open. Get that wrong on a handheld and the app opens a profile nobody chose — which has
 * already happened once here, and the profile it opened was PIN-protected. So a device that
 * misreports itself needs a way out that does not involve shipping a new build.
 */
enum class TvOverride {
    /** Trust the device. */
    AUTO,

    /** The user says this is a TV, whatever the device claims. */
    FORCE_TV,

    /** The user says this is not a TV, whatever the device claims. */
    FORCE_TOUCH,
}

/**
 * The whole decision, as a pure function.
 *
 * Deliberately takes booleans rather than a [Context] so the truth table can be tested without
 * an emulator. Every real-world combination that matters is a row in `DeviceTypeTest`.
 *
 * [hasLeanback] is primary: `FEATURE_LEANBACK` is what Android TV, Google TV and Fire TV all
 * declare, and it is what the launcher category keys on. [isTelevisionUiMode] is the secondary
 * signal, for a device that presents a TV UI mode without declaring the feature.
 *
 * **`WindowSizeClass` is deliberately not an input.** Screen size cannot tell a TV from a
 * tablet — a 10" tablet and a 55" television land in the same width class, and a phone mirrored
 * to a TV lands in the phone's. Sizing the UI and choosing the input model are different
 * questions, and only one of them is about pixels.
 */
fun resolveIsTv(
    hasLeanback: Boolean,
    isTelevisionUiMode: Boolean,
    override: TvOverride,
): Boolean = when (override) {
    TvOverride.FORCE_TV -> true
    TvOverride.FORCE_TOUCH -> false
    TvOverride.AUTO -> hasLeanback || isTelevisionUiMode
}

/** Reads the two device signals and applies [override]. */
fun Context.isTelevision(override: TvOverride = TvOverride.AUTO): Boolean = resolveIsTv(
    hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
    isTelevisionUiMode = isTelevisionUiMode(),
    override = override,
)

private fun Context.isTelevisionUiMode(): Boolean {
    val fromManager = (getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)
        ?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    // The manager is the reliable source, but it can be absent on odd builds; the configuration
    // carries the same bits and costs nothing to check.
    val fromConfig = (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
        Configuration.UI_MODE_TYPE_TELEVISION
    return fromManager || fromConfig
}
