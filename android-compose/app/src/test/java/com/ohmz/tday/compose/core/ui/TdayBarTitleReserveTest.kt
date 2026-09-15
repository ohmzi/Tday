package com.ohmz.tday.compose.core.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The docked toolbar title's width arithmetic, for the seven screens that share
 * [TdayHeroToolbar].
 *
 * Plain JUnit and no Robolectric, which is why [tdayBarTitleReserve] and
 * [tdayBarTitleScale] are `internal` and take Dp rather than reading a
 * composition: there is no emulator and no Compose UI test in this module, so a
 * layout claim here can only be proven as arithmetic. A `Text` can be asked to
 * draw; it cannot be asked what it decided.
 *
 * ## Where the numbers come from, and how far they can be pushed
 *
 * The title widths are arithmetic, not guesses. `res/font/nunito_wght.ttf` is a
 * variable font at 1000 units per em; `ui/theme/Type.kt` maps
 * `FontWeight.ExtraBold` to visual weight 800. Instantiate that font at wght
 * 800, sum the `hmtx` advances for the string and add the GPOS pair kerning,
 * scale by 32/1000, and you get the constants below. No `letterSpacing` is set
 * anywhere on this path, so there is no tracking to add.
 *
 * They are Nunito's widths, and the docked title does not resolve Nunito: there
 * is no `fontFamily` anywhere on that path, so it draws in `FontFamily.Default`
 * — the device's own system face. The reserve doc carries the full argument and
 * the debt. What it means HERE is that these are inputs of a plausible size, not
 * a forecast: every test below is a statement about the pure function's
 * behaviour at a given width, which is the only thing this module can prove
 * without an emulator. Read them as "a bar this wide, given a title this wide,
 * must do this". The device's own widths arrive at runtime from a `TextMeasurer`
 * and go through the same two functions.
 *
 * The bar geometry is read off [TdayHeroToolbar]: `.onSizeChanged` sits AFTER
 * `.padding(start = HorizontalPadding, end = HorizontalPadding)` in the modifier
 * chain, so `barWidth` is the CONTENT box — the device width less 2 × 18dp. The
 * leading slot is `TdayDimens.FabSize` when there is a back button. The trailing
 * slot is whatever the caller's action Row measured, which is n × 56dp circles
 * with (n − 1) × 8dp between them.
 */
class TdayBarTitleReserveTest {

    // Device widths, as the bar's CONTENT box. 412 is the phone the bug was
    // reported from; 360 is the long-standing Android baseline and the width
    // most of the remaining squeeze lives at.
    private val bar412 = 412.dp - 36.dp
    private val bar360 = 360.dp - 36.dp

    private val backButton = 56.dp

    /** n circle actions at 56dp with 8dp between them, as the bar's Row lays them out. */
    private fun actions(n: Int): Dp = if (n <= 0) 0.dp else (56 * n + 8 * (n - 1)).dp

    // Nunito at 32sp / wght 800, per the note above: representative widths, not
    // a forecast of what a handset's own face will measure.
    private val calendar = 136.7.dp
    private val completed = 165.4.dp
    private val settings = 126.2.dp
    private val howToAndTips = 226.9.dp
    private val morningSweep = 237.4.dp
    private val appVersion = 186.0.dp
    private val today = 90.8.dp
    private val scheduled = 158.4.dp
    private val allTasks = 139.0.dp
    private val overdue = 128.6.dp

    private fun room(r: TdayBarTitleReserve, barWidth: Dp): Dp = barWidth - r.start - r.end

    private fun assertDp(expected: Dp, actual: Dp) =
        assertEquals(expected.value.toDouble(), actual.value.toDouble(), 0.05)

    /**
     * THE REPORTED BUG. Calendar on a 412dp phone: back button, then a search
     * circle and the Today pill, which has collapsed to a circle by the time the
     * docked title is visible at all (`showLabel` goes false at collapse 0.5,
     * the title reveals from 0.82).
     *
     * The old rule mirrored the wider side — max(56, 120) + 8 = 128 each — and
     * handed "Calendar" 120dp for a word that wants 136.7. That is the "Cale…"
     * from the screenshot. It is asserted here as the thing that must NOT happen.
     */
    @Test
    fun `calendar at 412dp fits its title in full`() {
        val reserve = tdayBarTitleReserve(bar412, backButton, actions(2), calendar)

        assertDp(64.dp, reserve.start)
        assertDp(128.dp, reserve.end)
        assertDp(184.dp, room(reserve, bar412))
        assertTrue(room(reserve, bar412) >= calendar)
        assertEquals(1f, tdayBarTitleScale(calendar, room(reserve, bar412)), 0f)

        // The branch the old gate would have taken, named so a regression is
        // recognisable rather than merely red: mirrored, 128 a side, 120 left.
        assertFalse("must not mirror when mirroring is what truncates", reserve.start == reserve.end)
        assertTrue(120.dp < calendar)
    }

    /**
     * The centring is not thrown away, it is spent. A title that fits the
     * mirrored reserve still gets it, because the docked copy crossfades with
     * the block's copy centred on the BAR and going off-centre is visible during
     * the handoff. "Today" is the shortest of the real titles and the clearest
     * case.
     */
    @Test
    fun `a title that fits stays centred on the bar`() {
        val reserve = tdayBarTitleReserve(bar412, backButton, actions(2), today)

        assertDp(128.dp, reserve.start)
        assertEquals(reserve.start, reserve.end)
        assertDp(120.dp, room(reserve, bar412))
        assertTrue(room(reserve, bar412) >= today)
    }

    /**
     * The six screens that were already fitting must be untouched — same reserve,
     * same centring, no shrink. This is the half of the change that has to be
     * invisible.
     */
    @Test
    fun `the screens that already fitted are unchanged at 412dp`() {
        val oneAction = listOf(completed to 1, settings to 1, howToAndTips to 1)
        for ((title, n) in oneAction) {
            val reserve = tdayBarTitleReserve(bar412, backButton, actions(n), title)
            assertEquals("$title should stay centred", reserve.start, reserve.end)
            assertDp(64.dp, reserve.start)
            assertDp(248.dp, room(reserve, bar412))
            assertEquals(1f, tdayBarTitleScale(title, room(reserve, bar412)), 0f)
        }

        // The two bars that pass no actions at all. The mirrored reserve still
        // charges them for a phantom right-hand cluster the size of the back
        // button, which is correct: it is what keeps the title on the bar's
        // centre line. Both fit at this width, so nothing is spent to find out.
        for (title in listOf(morningSweep, appVersion)) {
            val reserve = tdayBarTitleReserve(bar412, backButton, actions(0), title)
            assertEquals(reserve.start, reserve.end)
            assertDp(248.dp, room(reserve, bar412))
            assertTrue(room(reserve, bar412) >= title)
        }
    }

    /**
     * The narrow phone, where the fix does the most work. At 360dp the mirrored
     * reserve leaves "Morning Sweep" 196dp for a name that wants 237.4 — it used
     * to ellipsize on a bar with NO actions on it at all, having been charged
     * 56dp of reserve for an empty cluster. Per-side gives it 252 and it fits
     * outright, with no shrink.
     */
    @Test
    fun `an empty action cluster stops being charged for at 360dp`() {
        val reserve = tdayBarTitleReserve(bar360, backButton, actions(0), morningSweep)

        assertDp(64.dp, reserve.start)
        assertDp(8.dp, reserve.end)
        assertDp(252.dp, room(reserve, bar360))
        assertTrue(room(reserve, bar360) >= morningSweep)
        assertEquals(1f, tdayBarTitleScale(morningSweep, room(reserve, bar360)), 0f)

        // The old mirrored answer, for contrast.
        assertTrue(196.dp < morningSweep)
    }

    /**
     * Where the reserve runs out and the shrink takes over. At 360dp Calendar's
     * bar is honestly too narrow: 132dp of free space against 136.7 of word,
     * with no centring left to sell. The title yields about 3%, which is deep
     * inside the floor, and stays whole.
     */
    @Test
    fun `calendar at 360dp shrinks rather than clips`() {
        val reserve = tdayBarTitleReserve(bar360, backButton, actions(2), calendar)
        val available = room(reserve, bar360)

        assertDp(132.dp, available)
        assertTrue("the reserve alone cannot save it here", available < calendar)

        val scale = tdayBarTitleScale(calendar, available)
        assertEquals(132.0 / 136.7, scale.toDouble(), 0.001)
        assertTrue("well inside the floor", scale > TdayHeroTitleMetrics.DockedTitleMinScale)
        // Whole, which is the point: the scaled word fits the room it was given.
        assertTrue(calendar.value * scale <= available.value + 0.01f)
    }

    /**
     * The floor. A title long enough that even the per-side reserve plus the
     * full shrink cannot hold it stops shrinking and ellipsizes instead, rather
     * than degrading to a size that no longer reads as the title it handed off
     * from. "Scheduled" in a 56dp-wide slot is the stand-in.
     */
    @Test
    fun `the shrink stops at the floor and leaves the rest to the ellipsis`() {
        val scale = tdayBarTitleScale(scheduled, 56.dp)

        assertEquals(TdayHeroTitleMetrics.DockedTitleMinScale, scale, 0f)
        // Still overflowing at the floor, which is exactly when the Text's own
        // `TextOverflow.Ellipsis` is meant to be the answer.
        assertTrue(scheduled.value * scale > 56f)
    }

    /**
     * THE SEVENTH SCREEN, and the limit of what this change buys.
     *
     * TodoListScreen is not a one-action bar. Every ordinary scope carries three
     * circles — search, summarize, select (`TodoListScreen.kt`, `topBarActions`;
     * summarize while the AI summary setting is on, which is its default) — and
     * the overdue scope and list detail carry four. Three is 184dp of
     * cluster, so the mirrored reserve there is 192 a side and wants 384 of a
     * 324dp bar: NEGATIVE room. Which is the point of this test. Those bars were
     * on the per-side branch before this change and are on it after, for the same
     * reason and with the same numbers; the reserve fix is a no-op for them.
     *
     * So what they get is the shrink, and it is not enough at 360: 68dp of room
     * against "Scheduled" wanting 158.4 asks for 0.43 and the floor stops it at
     * 0.72, which still overflows, so the `Text` ellipsizes. That is this bar
     * behaving as designed at a width it cannot serve, and it is pinned here so
     * the honest claim is the one on record: the fix makes the six
     * single-purpose screens whole and Calendar's bar whole at 412, and leaves
     * TodoListScreen's busiest scopes short. The lever left is the cluster, not
     * this function.
     */
    @Test
    fun `the three-action list scopes are untouched by the reserve and lean on the shrink`() {
        for (barWidth in listOf(bar360, bar412)) {
            val reserve = tdayBarTitleReserve(barWidth, backButton, actions(3), scheduled)
            val old = tdayBarTitleReserve(barWidth, backButton, actions(3), 0.dp)

            // Identical to the old rule, because mirrored never fitted here
            // either: 324 (or 376) − 2 × 192 is negative before any title is
            // considered.
            assertEquals(old.start, reserve.start)
            assertEquals(old.end, reserve.end)
            assertDp(64.dp, reserve.start)
            assertDp(192.dp, reserve.end)
            assertTrue(reserve.hasRoom)
        }

        // 412dp: the shrink alone is enough, and every scope name stays whole.
        val wide = room(tdayBarTitleReserve(bar412, backButton, actions(3), scheduled), bar412)
        assertDp(120.dp, wide)
        for (title in listOf(today, allTasks, scheduled, overdue)) {
            val scale = tdayBarTitleScale(title, wide)
            assertTrue(
                "$title should not reach the floor at 412dp",
                scale > TdayHeroTitleMetrics.DockedTitleMinScale,
            )
            assertTrue(title.value * scale <= wide.value + 0.01f)
        }

        // 360dp: it is not. "Scheduled" bottoms out and the ellipsis takes the
        // rest — asserted rather than hoped for, because the commit message is
        // only allowed to claim what this says.
        val narrow = room(tdayBarTitleReserve(bar360, backButton, actions(3), scheduled), bar360)
        assertDp(68.dp, narrow)
        val floored = tdayBarTitleScale(scheduled, narrow)
        assertEquals(TdayHeroTitleMetrics.DockedTitleMinScale, floored, 0f)
        assertTrue("still overflowing at the floor", scheduled.value * floored > narrow.value)
        // "Today" is short enough to survive the same bar, which is why the
        // screen is not uniformly broken — only its longer scopes are.
        assertTrue(today.value * tdayBarTitleScale(today, narrow) <= narrow.value + 0.01f)
    }

    /**
     * The give-up, unchanged. `DockedTitleMinWidth` decides whether a title is
     * shown at all, and the fix deliberately did not touch that — raising it
     * would have been the wrong repair, since it gates visibility rather than
     * fit. TodoListScreen's overdue bar carries four trailing circles; at 360dp
     * that leaves 4dp and the bar correctly carries no title.
     */
    @Test
    fun `a bar with no room left still carries no title`() {
        val reserve = tdayBarTitleReserve(bar360, backButton, actions(4), scheduled)

        assertDp(4.dp, room(reserve, bar360))
        assertFalse(reserve.hasRoom)

        // And the 56dp case just above it still shows its stump, as before.
        val stump = tdayBarTitleReserve(bar412, backButton, actions(4), scheduled)
        assertDp(56.dp, room(stump, bar412))
        assertTrue(stump.hasRoom)
    }

    /**
     * The first frame. `barWidth` arrives from `onSizeChanged` and the title's
     * measurement from a `TextMeasurer`, so both can be absent for a composition.
     * Neither absence may produce a reserve that overlaps the buttons.
     */
    @Test
    fun `an unmeasured bar or title falls back safely`() {
        val unmeasuredBar = tdayBarTitleReserve(0.dp, backButton, actions(2), calendar)
        assertDp(backButton, unmeasuredBar.start)
        assertDp(actions(2), unmeasuredBar.end)

        // An unmeasured title reads as fitting anything, so the mirrored branch
        // holds — which is precisely what the bar did before this change, and so
        // is what it should settle FROM rather than flicker through.
        val unmeasuredTitle = tdayBarTitleReserve(bar412, backButton, actions(2), 0.dp)
        assertEquals(unmeasuredTitle.start, unmeasuredTitle.end)
        assertDp(128.dp, unmeasuredTitle.start)
    }

    /**
     * The safety property behind the whole change, swept rather than sampled:
     * making the reserve title-aware can only ever GIVE the title width, never
     * take it. Per-side reserves `leading + trailing`; mirrored reserves
     * `2 × max(leading, trailing)`, which is never smaller. So no screen that
     * fitted before can stop fitting, whatever its title and whatever its bar.
     */
    @Test
    fun `the new branch is never narrower than the old one`() {
        for (barDp in 280..840 step 4) {
            val barWidth = barDp.dp
            for (n in 0..5) {
                for (titleDp in 0..400 step 10) {
                    val reserve = tdayBarTitleReserve(barWidth, backButton, actions(n), titleDp.dp)
                    val old = tdayBarTitleReserve(barWidth, backButton, actions(n), 0.dp)
                    assertTrue(
                        "bar=$barDp actions=$n title=$titleDp",
                        room(reserve, barWidth) >= room(old, barWidth) - 0.001.dp,
                    )
                    // And it never reserves less than the controls that are
                    // actually sitting there, which is what stops the title
                    // painting across them.
                    assertTrue(reserve.start >= backButton)
                    assertTrue(reserve.end >= actions(n))
                }
            }
        }
    }

    /** The shrink is bounded at both ends, for every input the bar can produce. */
    @Test
    fun `the scale stays within its bounds`() {
        for (roomDp in 0..400 step 3) {
            for (titleDp in 0..500 step 7) {
                val scale = tdayBarTitleScale(titleDp.dp, roomDp.dp)
                assertTrue("room=$roomDp title=$titleDp", scale >= TdayHeroTitleMetrics.DockedTitleMinScale)
                assertTrue("room=$roomDp title=$titleDp", scale <= 1f)
                // Never shrinks a title that already fits: the handoff depends
                // on the docked copy being the same size as the block's copy
                // wherever it possibly can be.
                if (titleDp <= roomDp) assertEquals(1f, scale, 0f)
            }
        }
    }
}
