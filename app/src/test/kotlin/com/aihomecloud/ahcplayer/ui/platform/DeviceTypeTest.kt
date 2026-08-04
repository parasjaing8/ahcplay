package com.aihomecloud.ahcplayer.ui.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The TV-detection truth table.
 *
 * This is worth testing because the consequence is not cosmetic: `isTv` gates a timer that
 * auto-opens the highlighted profile. Answering "yes" on a handheld opens a profile the user
 * never chose — which has happened here once already, on a PIN-protected profile.
 */
class DeviceTypeTest {

    // --- AUTO: trust the device -------------------------------------------

    @Test
    fun `leanback alone is enough`() {
        // Android TV, Google TV and the household Fire TV Stick 4K all report this.
        assertTrue(resolveIsTv(hasLeanback = true, isTelevisionUiMode = false, override = TvOverride.AUTO))
    }

    @Test
    fun `television ui mode alone is enough`() {
        // The secondary signal exists for devices presenting a TV UI without the feature flag.
        assertTrue(resolveIsTv(hasLeanback = false, isTelevisionUiMode = true, override = TvOverride.AUTO))
    }

    @Test
    fun `a phone or tablet reporting neither is not a TV`() {
        assertFalse(resolveIsTv(hasLeanback = false, isTelevisionUiMode = false, override = TvOverride.AUTO))
    }

    // --- the override, which is the whole point of the feature -------------

    @Test
    fun `force TV wins over a device claiming it is not one`() {
        assertTrue(resolveIsTv(hasLeanback = false, isTelevisionUiMode = false, override = TvOverride.FORCE_TV))
    }

    @Test
    fun `force touch wins over a device claiming leanback`() {
        // The failure this exists for: a handheld that misreports FEATURE_LEANBACK would
        // otherwise auto-open a profile. The user must be able to stop that without a new build.
        assertFalse(resolveIsTv(hasLeanback = true, isTelevisionUiMode = true, override = TvOverride.FORCE_TOUCH))
    }

    @Test
    fun `every combination is decided by the override when one is set`() {
        for (leanback in listOf(true, false)) {
            for (uiMode in listOf(true, false)) {
                assertTrue(resolveIsTv(leanback, uiMode, TvOverride.FORCE_TV))
                assertFalse(resolveIsTv(leanback, uiMode, TvOverride.FORCE_TOUCH))
            }
        }
    }
}
