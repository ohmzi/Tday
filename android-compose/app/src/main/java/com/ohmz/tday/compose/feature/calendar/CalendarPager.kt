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

    LaunchedEffect(targetPage, boundedPageCount) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(scrollRequest?.id, boundedPageCount) {
        val request = scrollRequest ?: return@LaunchedEffect
        val requestedPage = request.page.coerceIn(0, boundedPageCount - 1)
        if (pagerState.currentPage != requestedPage) {
            pagerState.animateScrollToPage(requestedPage)
        }
        onScrollRequestHandled(request.id)
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
