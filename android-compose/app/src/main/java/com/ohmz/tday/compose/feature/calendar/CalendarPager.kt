package com.ohmz.tday.compose.feature.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate

internal data class CalendarTodayJumpRequest(
    val id: Int,
    val targetDate: LocalDate,
)

internal data class CalendarPagerScrollRequest(
    val id: Int,
    val page: Int,
)

private const val CalendarPagerPreloadRadius = 1

/**
 * Drives one [CalendarPagerScrollRequest] to its page and reports it handled on every exit,
 * including the exits that never reach the page.
 *
 * The report is not a notification. The calendar cards treat an outstanding request as "paging in
 * progress" and disable both chevrons until they are told it is done, so this callback is the only
 * thing that hands the header its controls back.
 *
 * [animateToPage] takes the pager's scroll mutex at `MutatePriority.Default`. A finger landing on
 * the pager mid-slide takes that same mutex at `MutatePriority.UserInput`, which cancels the
 * animation and unwinds a `CancellationException` out through this call. With the report written
 * after the animation it was simply skipped on that path: `scrollRequest` stayed non-null,
 * `isPagingAtRest` stayed false, and both chevrons stayed dead for the rest of the session.
 * Touching a pager that is already sliding is something a thumb does by accident, so the calendar
 * was losing its header navigation to an ordinary gesture and only a process death gave it back.
 *
 * Hence the `finally`, which covers all three ways out: the scroll landed, a drag took the pager
 * away from us, or the composable left composition. Clearing is the safe direction in every one of
 * them — a cleared request means enabled chevrons, and the worst a spurious clear can do is let the
 * user ask for the next page again.
 *
 * [onHandled] is an ordinary synchronous callback, which is why there is no `NonCancellable` here:
 * a non-suspending `finally` body runs to completion inside an already-cancelled coroutine, and
 * `withContext(NonCancellable)` would only buy the right to suspend, which this does not do.
 */
internal suspend fun runCalendarPagerScrollRequest(
    requestId: Int,
    targetPage: Int,
    currentPage: () -> Int,
    animateToPage: suspend (Int) -> Unit,
    onHandled: (Int) -> Unit,
) {
    try {
        if (currentPage() != targetPage) {
            animateToPage(targetPage)
        }
    } finally {
        onHandled(requestId)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CalendarPagingContent(
    pageCount: Int,
    currentPage: Int,
    onPageSettled: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollRequest: CalendarPagerScrollRequest? = null,
    onScrollRequestHandled: (Int) -> Unit = {},
    pageKey: (Int) -> Any = { it },
    pageContent: @Composable (Int) -> Unit,
) {
    val boundedPageCount = pageCount.coerceAtLeast(1)
    val targetPage = currentPage.coerceIn(0, boundedPageCount - 1)
    val pagerState = rememberPagerState(initialPage = targetPage) { boundedPageCount }
    val latestTargetPage by rememberUpdatedState(targetPage)
    val latestOnPageSettled by rememberUpdatedState(onPageSettled)
    val latestOnScrollRequestHandled by rememberUpdatedState(onScrollRequestHandled)

    LaunchedEffect(targetPage, boundedPageCount) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(scrollRequest?.id, boundedPageCount) {
        val request = scrollRequest ?: return@LaunchedEffect
        runCalendarPagerScrollRequest(
            requestId = request.id,
            targetPage = request.page.coerceIn(0, boundedPageCount - 1),
            currentPage = { pagerState.currentPage },
            animateToPage = { page -> pagerState.animateScrollToPage(page) },
            onHandled = { id -> latestOnScrollRequestHandled(id) },
        )
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settledPage ->
                if (settledPage != latestTargetPage) {
                    latestOnPageSettled(settledPage)
                }
            }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        key = pageKey,
        beyondViewportPageCount = (boundedPageCount - 1).coerceAtMost(CalendarPagerPreloadRadius),
    ) { page ->
        pageContent(page)
    }
}
