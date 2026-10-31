package com.ohmz.tday.compose.feature.widget

import androidx.compose.ui.graphics.toArgb
import com.ohmz.tday.compose.ui.theme.tdayListAccentColorOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The floater colours the live data uses must resolve to a real accent in the widget's palette,
 * or the "+" falls back to the kind's pale accent instead of the list's colour.
 */
class WidgetListPaletteTest {
    private val floaterKeys = listOf("PINK", "GOLD", "LIME", "YELLOW", "CORAL", "RED")

    @Test
    fun `every floater list colour key resolves to an accent`() {
        floaterKeys.forEach { key ->
            assertNotNull("key $key should resolve", tdayListAccentColorOrNull(key))
        }
    }

    @Test
    fun `keys resolve case-insensitively and with legacy aliases`() {
        assertEquals(
            tdayListAccentColorOrNull("LIME")?.toArgb(),
            tdayListAccentColorOrNull(" lime ")?.toArgb(),
        )
        assertEquals(
            tdayListAccentColorOrNull("LIME")?.toArgb(),
            tdayListAccentColorOrNull("GREEN")?.toArgb(),
        )
        assertEquals(
            tdayListAccentColorOrNull("SLATE")?.toArgb(),
            tdayListAccentColorOrNull("GRAY")?.toArgb(),
        )
    }
}
