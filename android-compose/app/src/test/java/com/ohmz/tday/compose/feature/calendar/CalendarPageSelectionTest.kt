package com.ohmz.tday.compose.feature.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * The two defects `CalendarPageSelection.kt` closes, pinned on the decisions
 * themselves because the module has no Compose UI harness and no device: the
 * pagers' motion is untestable here, but the arithmetic underneath it — which
 * is where both bugs actually live — is not.
 *
 * The fixture is the real one. `CalendarScreen` floors navigation at the
 * current month (`YearMonth.from(date) >= minNavigableMonth`) and starts the
 * week pager at the Sunday on or before the 1st of that month, so with a
 * September 2026 floor page 0 of the week pager is **Sun 30 Aug – Sat 5 Sep**:
 * two of its seven days sit below the floor. That is not a contrived date, it
 * is what seven months in twelve look like.
 */
class CalendarPageSelectionTest {

    private val floorMonth = YearMonth.of(2026, 9)

    /** Sun 30 Aug 2026 — page 0 of the week pager, and in the previous month. */
    private val minWeekStart = startOfWeek(floorMonth.atDay(1))

    /** Sat 12 Sep 2026. Its week (Sun 6 Sep) is week-pager page 1. */
    private val today = LocalDate.of(2026, 9, 12)

    private val canSelectDate: (LocalDate) -> Boolean =
        { date -> YearMonth.from(date) >= floorMonth }

    @Test
    fun `page 0 of the week pager really does start in the previous month`() {
        // The premise of every week case below. If a future change moves the
        // pager's origin to the 1st itself this assertion is the one that says
        // so, rather than the clamping cases quietly becoming vacuous.
        assertEquals(LocalDate.of(2026, 8, 30), minWeekStart)
        assertFalse(canSelectDate(minWeekStart))
        assertTrue(canSelectDate(minWeekStart.plusDays(2)))
    }

    // --- and-calendar-today-jump-loses-selection ---------------------------

    @Test
    fun `a cross-month Today jump carries today onto the month page it lands on`() {
        // Looking at December, with 10 Dec selected; tapping Today.
        val visibleMonth = YearMonth.of(2026, 12)
        val currentPage = 3

        val jump = monthPagerTodayJump(floorMonth, currentPage, today)
        assertEquals(CalendarTodayJump.PageThenSelect(page = 0, date = today), jump)
        val landedOn = (jump as CalendarTodayJump.PageThenSelect).page

        // The sweep lands on page 0 and the settle fires. This is the assertion
        // the bug failed: the month settle carries no day of its own, so before
        // the pending target existed it moved the grid to September and left
        // `selectedDate` on 10 December — which is what the "Tasks due …"
        // heading reads, what the day list under it reads, and what the `+`
        // button prefills a new task's due date from.
        val settled = monthPageSettleSelection(
            minNavigableMonth = floorMonth,
            page = landedOn,
            pendingJumpDate = jump.date,
        )
        assertEquals(today, settled)
        assertNotEquals(visibleMonth, settled?.let { YearMonth.from(it) })
    }

    @Test
    fun `an ordinary month swipe still selects nothing`() {
        // Swiping to November means "show me November", not "select a day in
        // November": the settle moves the visible month and the selection is
        // deliberately left where the user put it.
        assertNull(monthPageSettleSelection(floorMonth, page = 2, pendingJumpDate = null))
    }

    @Test
    fun `a Today sweep grabbed mid-flight does not select a day in the month it stopped on`() {
        // The pending target is consumed by whichever settle fires first; a
        // finger that hijacks the sweep and drops it on November must not have
        // 12 September selected out from under it.
        assertNull(monthPageSettleSelection(floorMonth, page = 2, pendingJumpDate = today))
    }

    @Test
    fun `a Today tap inside the visible month selects without moving the pager`() {
        val jump = monthPagerTodayJump(floorMonth, currentPage = 0, targetDate = today)
        assertEquals(CalendarTodayJump.SelectNow(today), jump)
    }

    @Test
    fun `a cross-week Today jump selects today, not the weekday that was selected`() {
        // Wed 16 Sep selected — week of Sun 13 Sep, week-pager page 2.
        val selectedDate = LocalDate.of(2026, 9, 16)
        val selectedDayOffset = (selectedDate.dayOfWeek.value % 7).toLong()
        val currentPage = 2

        val jump = weekPagerTodayJump(minWeekStart, currentPage, today)
        assertEquals(CalendarTodayJump.PageThenSelect(page = 1, date = today), jump)
        val landedOn = (jump as CalendarTodayJump.PageThenSelect).page

        // What the settle used to compute on its own: this page's Sunday plus
        // the weekday the user came in on. It sweeps to the right week and then
        // selects the wrong day in it — Wed 9 Sep, three days off today.
        val weekdayCarriedOver = weekPageStart(minWeekStart, landedOn)
            .plusDays(selectedDayOffset)
        assertEquals(LocalDate.of(2026, 9, 9), weekdayCarriedOver)
        assertNotEquals(today, weekdayCarriedOver)

        assertEquals(
            today,
            weekPageSettleSelection(
                minWeekStart = minWeekStart,
                page = landedOn,
                preferredDayOffset = selectedDayOffset,
                pendingJumpDate = jump.date,
                canSelectDate = canSelectDate,
            ),
        )
    }

    @Test
    fun `a Today tap inside the visible week selects without moving the pager`() {
        // Sun 6 Sep selected: today is already on this page, so nothing sweeps.
        val jump = weekPagerTodayJump(minWeekStart, currentPage = 1, targetDate = today)
        assertEquals(CalendarTodayJump.SelectNow(today), jump)
    }

    @Test
    fun `the day pager jumps by page and needs no date carried with it`() {
        val minDate = floorMonth.atDay(1)
        assertEquals(
            CalendarTodayJump.PageThenSelect(page = 11, date = today),
            dayPagerTodayJump(minDate, currentPage = 20, targetDate = today),
        )
        assertEquals(
            CalendarTodayJump.SelectNow(today),
            dayPagerTodayJump(minDate, currentPage = 11, targetDate = today),
        )
    }

    @Test
    fun `a target clamped onto the page already in view selects instead of scrolling`() {
        // A scroll request to the page you are already on scrolls nowhere, so
        // it never settles, so nothing ever selects. Comparing pages rather
        // than periods makes that case a plain selection.
        assertEquals(
            CalendarTodayJump.SelectNow(today),
            decideTodayJump(targetDate = today, targetPage = 4, currentPage = 4),
        )
    }

    // --- and-week-page-settle-declines-silently ----------------------------

    @Test
    fun `settling on page 0 with a Sunday selected clamps forward instead of declining`() {
        // Sun 6 Sep selected, swiping back one week to page 0. The weekday the
        // user is holding maps to Sun 30 Aug, which is below the floor — the
        // old settle checked exactly that and then returned without doing
        // anything, leaving the pager parked on 30 Aug – 5 Sep while the
        // header, the day list and the `+` prefill all went on describing the
        // week after it. Nothing on screen said the swipe had been refused.
        val preferredDayOffset = 0L
        assertFalse(canSelectDate(weekPageStart(minWeekStart, 0).plusDays(preferredDayOffset)))

        assertEquals(
            LocalDate.of(2026, 9, 1),
            weekPageSettleSelection(
                minWeekStart = minWeekStart,
                page = 0,
                preferredDayOffset = preferredDayOffset,
                pendingJumpDate = null,
                canSelectDate = canSelectDate,
            ),
        )
    }

    @Test
    fun `settling on page 0 keeps the weekday when that weekday is above the floor`() {
        // Sat 5 Sep is on page 0 and above the floor, so a Saturday-selected
        // user swiping back gets the Saturday they asked for. Clamping is for
        // the days the screen refuses, not for the whole page.
        assertEquals(
            LocalDate.of(2026, 9, 5),
            weekPageSettleSelection(
                minWeekStart = minWeekStart,
                page = 0,
                preferredDayOffset = 6L,
                pendingJumpDate = null,
                canSelectDate = canSelectDate,
            ),
        )
    }

    @Test
    fun `an ordinary forward swipe keeps the selected weekday untouched`() {
        assertEquals(
            LocalDate.of(2026, 9, 16),
            weekPageSettleSelection(
                minWeekStart = minWeekStart,
                page = 2,
                preferredDayOffset = 3L,
                pendingJumpDate = null,
                canSelectDate = canSelectDate,
            ),
        )
    }

    @Test
    fun `a page with nothing selectable on it reports that rather than picking a date`() {
        // Unreachable with the real floor — page 0's week always contains the
        // 1st — but the caller has to be handed something it can act on, since
        // parking on a page the screen cannot describe is the failure being
        // fixed rather than an acceptable fallback.
        assertNull(
            weekPageSettleSelection(
                minWeekStart = minWeekStart,
                page = 0,
                preferredDayOffset = 0L,
                pendingJumpDate = today,
                canSelectDate = { false },
            ),
        )
    }

    @Test
    fun `a jump target the screen refuses does not override the floor`() {
        // The pending date is only ever today and the floor is today's own
        // month, so this cannot happen in the app — but the settle must not be
        // a back door around `canSelectDate` if a future caller widens it.
        assertEquals(
            LocalDate.of(2026, 9, 1),
            weekPageSettleSelection(
                minWeekStart = minWeekStart,
                page = 0,
                preferredDayOffset = 0L,
                pendingJumpDate = LocalDate.of(2026, 8, 31),
                canSelectDate = canSelectDate,
            ),
        )
    }
}
