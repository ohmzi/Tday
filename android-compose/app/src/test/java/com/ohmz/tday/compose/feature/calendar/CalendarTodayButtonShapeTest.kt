package com.ohmz.tday.compose.feature.calendar

import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.ui.theme.TdayDimens
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Today control's outline, asserted where it can be.
 *
 * There is no emulator in this loop, so nothing here looks at a pixel — and the
 * shape claims do not need one, because "circle" and "rounded rectangle" are both
 * statements about a radius and a box that a JVM can check. What it cannot check
 * is the shadow, so the shadow is checked the only way it can be: by pinning the
 * two structural facts the shadow is a consequence of. Material3's `Surface` builds
 * the shadow, the fill and the clip from ONE `shape` — `graphicsLayer(shadow-
 * Elevation, shape)`, `background(shape)`, `clip(shape)`, verified in
 * `material3-release.aar`'s bytecode — so the outline test below IS the shadow
 * test; and `animateContentSize()` prepends a rectangular `clipToBounds()`, which
 * on the Card's own modifier crops that shadow away, so where it sits is pinned
 * too.
 *
 * Two kinds of claim, and they are deliberately different in kind.
 *
 * The ARITHMETIC claims pin what the numbers have to be — 28 against a 56dp box,
 * 12dp of straight edge left at each end when expanded — rather than re-deriving
 * the expected value from the constant under test. `EarlierIllustrationMotionTest`
 * names that trap: comparing a value back against its own definition passes no
 * matter what either one becomes.
 *
 * The SOURCE claims read `CalendarScreen.kt` as text, the way
 * `FeatureDimensBudgetTest` and `WidgetCornerRadiusTest` do, because two of the
 * things that must hold are facts about how a line is WRITTEN rather than what it
 * evaluates to: that the collapsed radius is derived from `FabSize` instead of
 * typed as 28, and that the clip is inside the Card instead of around it. Both
 * would survive every arithmetic assertion here and still be wrong.
 */
class CalendarTodayButtonShapeTest {

    @Test
    fun `the collapsed control is a true circle, and stays one if FabSize moves`() {
        // A circle is a relationship: the corner is half the box. The box is square
        // because the collapsed caps animate to SpacingNone, so `height` and the
        // `sizeIn` floor — both FabSize — are the whole of it.
        assertEquals(TdayDimens.FabSize, CalendarTodayCollapsedCornerRadius * 2)
        // And what that is today, written out rather than re-derived: 56 -> 28.
        assertEquals(56.dp, TdayDimens.FabSize)
        assertEquals(28.dp, CalendarTodayCollapsedCornerRadius)
    }

    @Test
    fun `the collapsed radius is derived from FabSize rather than typed`() {
        // The assertion above holds for `28.dp` written out by hand, right up until
        // someone moves FabSize — at which point the control quietly stops being a
        // circle beside two that still are. Only the source says which it is.
        assertTrue(
            "CalendarTodayCollapsedCornerRadius must be derived from TdayDimens.FabSize. A typed " +
                "28 satisfies the arithmetic today and silently stops being a circle the moment " +
                "FabSize moves",
            DERIVED_COLLAPSED_RADIUS.containsMatchIn(calendarScreenSource),
        )
    }

    @Test
    fun `the expanded control is a rectangle with corners rather than a stadium`() {
        // A stadium is the case where the corner reaches half the height and the
        // straight edge runs out. 56 - 2x22 leaves 12dp of straight vertical edge at
        // each end, which is what makes the ends read as corners.
        assertEquals(
            12.dp,
            TdayDimens.FabSize - CalendarTodayExpandedCornerRadius * 2,
        )
        assertTrue(
            "an expanded corner of $CalendarTodayExpandedCornerRadius on a ${TdayDimens.FabSize} " +
                "box is at or past half the height, which Compose clamps back to the stadium the " +
                "report asked to be rid of",
            CalendarTodayExpandedCornerRadius < CalendarTodayCollapsedCornerRadius,
        )
        // The two ends are different numbers, which is the whole reason the radius is
        // animated rather than fixed. A single rung would make the collapsed control a
        // rounded square beside two circles.
        assertNotEquals(CalendarTodayCollapsedCornerRadius, CalendarTodayExpandedCornerRadius)
    }

    @Test
    fun `the expanded corner is the one the segmented track under it already uses`() {
        // The Month/Week/Day track sits directly below this control in the same frame
        // and is the only other wide rounded rectangle in it. The radius was chosen to
        // match it rather than to be a fourth number, so if the track is ever retuned
        // this fails and somebody re-argues the pair instead of letting them drift.
        val trackRadius = SEGMENTED_CONTAINER_SHAPE.find(segmentedSliderSource)
            ?: error("TdaySegmentedSlider.kt no longer declares `val containerShape = RoundedCornerShape(<n>.dp)`")
        assertEquals(
            "the segmented track's corner moved to ${trackRadius.groupValues[1]}dp. The Today " +
                "control took RadiusField to match it; re-argue both or move both",
            TdayDimens.RadiusField,
            trackRadius.groupValues[1].toFloat().dp,
        )
        assertEquals(TdayDimens.RadiusField, CalendarTodayExpandedCornerRadius)
    }

    @Test
    fun `the Card draws the animated corner and no longer draws a circle`() {
        assertTrue(
            "CalendarTodayButton's Card must take the animated corner, or the outline and the " +
                "shadow Material3 derives from it go back to a stadium",
            todayButtonSource.contains("shape = RoundedCornerShape(cornerRadius)"),
        )
        assertTrue(
            "CalendarTodayButton must not use CircleShape: on the expanded 90.6 x 56 box it is a " +
                "stadium, not a circle",
            !todayButtonSource.contains("CircleShape"),
        )
    }

    @Test
    fun `the content-size clip sits inside the Card rather than around its shadow`() {
        // animateContentSize() == clipToBounds().then(SizeAnimationModifierElement) —
        // a rectangular graphicsLayer(clip = true). On the Card's own modifier it wraps
        // the shadow layer and crops everything the control draws outside its bounds,
        // which is the entire shadow bar the wedges that fall in the corners of the
        // bounding rectangle. On the Row it wraps only the icon and the word.
        val clip = todayButtonSource.indexOf(".animateContentSize()")
        val row = todayButtonSource.indexOf("Row(")
        assertTrue("CalendarTodayButton no longer calls animateContentSize()", clip >= 0)
        assertTrue("CalendarTodayButton no longer has the Row this test is reading", row >= 0)
        assertTrue(
            "animateContentSize() is back above the Row, which puts its rectangular clipToBounds " +
                "outside the Card's shadow layer and crops the shadow to four corner wedges",
            clip > row,
        )
    }

    private companion object {
        val composeSourceRoot: File = generateSequence(File(".").canonicalFile) { it.parentFile }
            .map { File(it, "app/src/main/java/com/ohmz/tday/compose") }
            .firstOrNull { it.isDirectory }
            ?: error("could not locate the compose source root from ${File(".").canonicalPath}")

        val calendarScreenSource: String =
            File(composeSourceRoot, "feature/calendar/CalendarScreen.kt").readText()

        val segmentedSliderSource: String =
            File(composeSourceRoot, "ui/component/TdaySegmentedSlider.kt").readText()

        /** `CalendarTodayButton`'s body alone: the file has other Cards and other shapes. */
        val todayButtonSource: String = calendarScreenSource
            .substringAfter("private fun CalendarTodayButton(")
            .substringBefore("\n@Composable")

        val DERIVED_COLLAPSED_RADIUS =
            Regex("""val CalendarTodayCollapsedCornerRadius\s*=\s*TdayDimens\.FabSize\s*/\s*2""")

        val SEGMENTED_CONTAINER_SHAPE =
            Regex("""val containerShape = RoundedCornerShape\((\d+(?:\.\d+)?)\.dp\)""")
    }
}
