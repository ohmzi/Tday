package com.ohmz.tday.compose.feature.calendar

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * The pure decision half of the calendar's three pagers: what a "Today" tap
 * asks the pager to do, and which date a settled page is allowed to select.
 *
 * `CalendarScreen` keeps the effectful half — writing `scrollRequest`, calling
 * `onSelectDate`, dropping a breadcrumb — the same division [quickDeferOptions]
 * and `decideSectionHeaderToggleAction` already draw elsewhere in this module.
 * The reason to draw it here specifically is that both of the bugs these
 * functions close are *arithmetic* bugs wearing a motion costume: a jump that
 * moves a page without moving the selection, and a settle that computes a date
 * the screen is not allowed to show and then says nothing. Neither needs a
 * device or a frame clock to reproduce — they need a date and an assertion,
 * which is what the unit tests beside this file give them.
 */

private const val DaysPerWeek = 7

/** The first day of [date]'s week, Sunday-first, matching the week grid. */
internal fun startOfWeek(date: LocalDate): LocalDate {
    val sundayOffset = date.dayOfWeek.value % 7
    return date.minusDays(sundayOffset.toLong())
}

/**
 * What a tap on the calendar's "Today" button should do to the pager in view.
 *
 * The distinction that matters is whether today already lives on the page the
 * user is looking at. If it does, the selection is the whole job and nothing
 * moves. If it does not, the page has to travel — and the selection has to
 * travel with it, which is the part that was missing.
 */
internal sealed interface CalendarTodayJump {

    /** Today is on the page in view: select it now, leave the pager alone. */
    data class SelectNow(val date: LocalDate) : CalendarTodayJump

    /**
     * Today is on another page: sweep there, and select [date] when the sweep
     * lands. [date] has to be carried across rather than re-derived on arrival,
     * because a page only knows which month or which week it is — the month
     * pager's settle knows no day at all, and the week pager's settle knows
     * only the weekday the user happened to be sitting on before they tapped.
     */
    data class PageThenSelect(val page: Int, val date: LocalDate) : CalendarTodayJump
}

/**
 * The one rule all three pagers share, expressed on pages rather than on months
 * or weeks or days.
 *
 * Comparing pages rather than periods also closes a small hole in the previous
 * per-pager comparisons: a target date far enough out to be clamped to the last
 * page could differ from the visible period while resolving to the page already
 * on screen, which used to produce a scroll request that scrolled nowhere and
 * therefore settled nothing — so the selection never happened.
 */
internal fun decideTodayJump(
    targetDate: LocalDate,
    targetPage: Int,
    currentPage: Int,
): CalendarTodayJump = if (targetPage == currentPage) {
    CalendarTodayJump.SelectNow(targetDate)
} else {
    CalendarTodayJump.PageThenSelect(targetPage, targetDate)
}

/**
 * Page arithmetic clamped in `Long` before the narrowing to `Int`.
 *
 * The pagers are finite windows onto an infinite calendar (240 months, 1040
 * weeks, 3650 days), so every index has to be clamped anyway; doing it before
 * `toInt()` rather than after means a date far enough out to overflow 32 bits
 * lands on the last page instead of wrapping to an arbitrary one.
 */
private fun calendarPageIndex(unitsFromFirstPage: Long, pageCount: Int): Int =
    unitsFromFirstPage.coerceIn(0L, (pageCount - 1).toLong()).toInt()

/** [decideTodayJump] for the month pager. */
internal fun monthPagerTodayJump(
    minNavigableMonth: YearMonth,
    currentPage: Int,
    targetDate: LocalDate,
    pageCount: Int = CalendarMonthPagerPageCount,
): CalendarTodayJump = decideTodayJump(
    targetDate = targetDate,
    targetPage = calendarPageIndex(
        ChronoUnit.MONTHS.between(minNavigableMonth, YearMonth.from(targetDate)),
        pageCount,
    ),
    currentPage = currentPage,
)

/** [decideTodayJump] for the week pager. */
internal fun weekPagerTodayJump(
    minWeekStart: LocalDate,
    currentPage: Int,
    targetDate: LocalDate,
    pageCount: Int = CalendarWeekPagerPageCount,
): CalendarTodayJump = decideTodayJump(
    targetDate = targetDate,
    targetPage = calendarPageIndex(
        ChronoUnit.WEEKS.between(minWeekStart, startOfWeek(targetDate)),
        pageCount,
    ),
    currentPage = currentPage,
)

/** [decideTodayJump] for the day pager. */
internal fun dayPagerTodayJump(
    minDate: LocalDate,
    currentPage: Int,
    targetDate: LocalDate,
    pageCount: Int = CalendarDayPagerPageCount,
): CalendarTodayJump = decideTodayJump(
    targetDate = targetDate,
    targetPage = calendarPageIndex(
        ChronoUnit.DAYS.between(minDate, targetDate),
        pageCount,
    ),
    currentPage = currentPage,
)

/** The Sunday a given week-pager page starts on. */
internal fun weekPageStart(minWeekStart: LocalDate, page: Int): LocalDate =
    minWeekStart.plusWeeks(page.toLong())

/**
 * Which date the month pager should select when page [page] settles, or `null`
 * when the settle should only move the visible month.
 *
 * A settled month page normally carries no day with it — swiping to November
 * means "show me November", not "select a day in November", and the screen is
 * right to leave the selection alone. The exception is a Today jump that had to
 * cross months: it handed the page to the pager and nothing else, so unless the
 * date it was carrying is applied here, the sweep finishes with the grid on
 * this month and `selectedDate` still on the month the user left. That is not a
 * cosmetic mismatch — `selectedDate` is what the "Tasks due …" heading reads,
 * what the day list below it reads, and what the `+` button prefills a new
 * task's due date from, so the jump would quietly arm the create sheet with a
 * date from a month the user is no longer looking at.
 *
 * [pendingJumpDate] is only ever today, and the screen's floor is today's own
 * month, so there is no "can I select this" question to ask here the way the
 * week pager below has to ask it.
 */
internal fun monthPageSettleSelection(
    minNavigableMonth: YearMonth,
    page: Int,
    pendingJumpDate: LocalDate?,
): LocalDate? {
    val pageMonth = minNavigableMonth.plusMonths(page.toLong())
    return pendingJumpDate?.takeIf { YearMonth.from(it) == pageMonth }
}

/**
 * Which date the week pager should select when page [page] settles, or `null`
 * when this page holds no date the screen is allowed to select at all.
 *
 * Three things decide it, in order:
 *
 * 1. A Today jump waiting on this page wins, for the reason spelled out on
 *    [monthPageSettleSelection] — plus one the month pager does not have: the
 *    week pager's own arithmetic would land on today's week carrying the
 *    weekday the user was sitting on when they tapped, so a Tuesday selection
 *    and a Thursday "today" would sweep to the right week and select Tuesday.
 *
 * 2. Otherwise the same weekday the user already had selected, one week over,
 *    which is what an ordinary swipe means.
 *
 * 3. Otherwise the selectable day on this page nearest to that one — because
 *    page 0's week starts on the Sunday on or before the 1st of the current
 *    month, which is usually in the *previous* month, and the screen's floor
 *    (`YearMonth.from(date) >= minNavigableMonth`) refuses those leading days.
 *    A Sunday-selected user swiping back to page 0 therefore asked for a date
 *    the screen will not show. The old code answered that by doing nothing at
 *    all, which left the pager parked on the first week while the header, the
 *    day list and the `+` prefill all went on describing the week before it —
 *    a silent decline with no way for the user to tell the swipe had failed.
 *    Clamping forward to the 1st keeps the page and the selection agreeing.
 *
 * `null` — every day on the page refused — cannot happen with that floor, since
 * page 0's week always contains the 1st of the current month and every later
 * page is wholly above it. It is still distinguished from a date rather than
 * folded into one so the caller has something to handle: parking on a page the
 * screen cannot describe is the failure mode being fixed, not a fallback.
 */
internal fun weekPageSettleSelection(
    minWeekStart: LocalDate,
    page: Int,
    preferredDayOffset: Long,
    pendingJumpDate: LocalDate?,
    canSelectDate: (LocalDate) -> Boolean,
): LocalDate? {
    val pageStart = weekPageStart(minWeekStart, page)
    val days = List(DaysPerWeek) { index -> pageStart.plusDays(index.toLong()) }
    val jumpTarget = pendingJumpDate?.takeIf { it in days && canSelectDate(it) }
    if (jumpTarget != null) return jumpTarget
    val preferred = pageStart.plusDays(preferredDayOffset.coerceIn(0L, (DaysPerWeek - 1).toLong()))
    if (canSelectDate(preferred)) return preferred
    return days
        .filter(canSelectDate)
        .minByOrNull { abs(ChronoUnit.DAYS.between(preferred, it)) }
}
