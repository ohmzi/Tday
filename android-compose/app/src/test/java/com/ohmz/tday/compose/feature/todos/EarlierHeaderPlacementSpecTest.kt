package com.ohmz.tday.compose.feature.todos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [earlierHeaderSkipsPlacementSpec] is the pure decision half of the fix for
 * the header/illustration/row overlap seen on the Priority screen (and every
 * other mode sharing this composition) mid hand-off: `TimelineSectionHeader`'s
 * `Modifier.animateItem` chasing the "today-earlier-empty-scene" item's own
 * `AnimatedVisibility`-driven height with a second, independently-clocked
 * `placementSpec` lags behind that item's real, already-smooth size for the
 * whole transition, long enough for the header to render on top of either
 * the illustration (mid-expand) or Earlier's first row (mid-collapse). This
 * cannot assert the pixel-level "no overlap" outcome itself — the module has
 * no Compose UI test harness or device to actually lay the two boxes out and
 * measure them — but it pins the boolean the real fix is built on: exactly
 * when the header's `placementSpec` is dropped, in a way that would fail if
 * a future change re-widened (or accidentally narrowed) that window.
 */
class EarlierHeaderPlacementSpecTest {

    @Test
    fun `Earlier's own header skips placementSpec while the illustration item is present`() {
        assertTrue(
            earlierHeaderSkipsPlacementSpec(
                section = EARLIER_SECTION_KEY,
                earlierIllustrationPresent = true,
            ),
        )
    }

    @Test
    fun `Earlier's header keeps placementSpec once the illustration item is gone`() {
        // The illustration item stops existing the moment the scope holds a
        // real pending task again (or search/loading takes over) -- at that
        // point something else entirely can appear above Earlier (Morning,
        // a scaffold day section), and the header needs its ordinary smooth
        // catch-up for that the same as any other displaced header.
        assertFalse(
            earlierHeaderSkipsPlacementSpec(
                section = EARLIER_SECTION_KEY,
                earlierIllustrationPresent = false,
            ),
        )
    }

    @Test
    fun `a non-Earlier header never skips placementSpec, even if the flag is somehow set`() {
        // The illustration item only ever sits directly above Earlier's own
        // header -- Morning/Afternoon/Tonight (and every scaffold day
        // section) are filtered to nothing for as long as it exists (see
        // TodoTimelineSectionsTest's "surfaces just Earlier" cases). This
        // pins that the gate is keyed on the section, not just the flag, so
        // a caller passing the wrong section can never silently disable
        // placement smoothing somewhere it is still needed.
        assertFalse(
            earlierHeaderSkipsPlacementSpec(
                section = "day-2026-09-11",
                earlierIllustrationPresent = true,
            ),
        )
    }
}
