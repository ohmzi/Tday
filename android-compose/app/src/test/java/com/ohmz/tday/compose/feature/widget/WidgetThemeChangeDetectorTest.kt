package com.ohmz.tday.compose.feature.widget

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the trigger condition for [TdayApplication.onConfigurationChanged]: a widget repaint must
 * fire exactly when the night-mode bits differ, and stay quiet otherwise so an unrelated
 * orientation/keyboard/locale delta doesn't force a redundant re-render on every config change.
 */
class WidgetThemeChangeDetectorTest {

    @Test
    fun `flips true when night mode turns on`() {
        assertTrue(didNightModeFlip(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES))
    }

    @Test
    fun `flips true when night mode turns off`() {
        assertTrue(didNightModeFlip(Configuration.UI_MODE_NIGHT_YES, Configuration.UI_MODE_NIGHT_NO))
    }

    @Test
    fun `no flip when night bit is unchanged`() {
        assertFalse(didNightModeFlip(Configuration.UI_MODE_NIGHT_YES, Configuration.UI_MODE_NIGHT_YES))
        assertFalse(didNightModeFlip(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_NO))
    }

    @Test
    fun `no flip when only an unrelated uiMode bit changes`() {
        val old = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
        val new = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_CAR
        assertFalse(didNightModeFlip(old, new))
    }

    @Test
    fun `flips true when night bit changes alongside an unrelated uiMode bit`() {
        val old = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL
        val new = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_CAR
        assertTrue(didNightModeFlip(old, new))
    }
}
