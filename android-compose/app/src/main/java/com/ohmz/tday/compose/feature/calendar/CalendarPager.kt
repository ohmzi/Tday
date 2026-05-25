package com.ohmz.tday.compose.feature.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.LocalDate

internal data class CalendarTodayJumpRequest(
    val id: Int,
    val targetDate: LocalDate,
)

internal enum class CalendarPagerSlot {
    PREVIOUS,
    CURRENT,
    NEXT,
}

internal data class CalendarPagerPage<T>(
    val slot: CalendarPagerSlot,
    val value: T,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun <T> CalendarPagingContent(
    pages: List<CalendarPagerPage<T>>,
    pagerState: PagerState,
    centerPageIndex: Int,
    onSettledAwayFromCenter: (CalendarPagerSlot) -> Unit,
    modifier: Modifier = Modifier,
    pageContent: @Composable (T) -> Unit,
) {
    var handledSettledPage by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(centerPageIndex, pages) {
        if (pagerState.currentPage != centerPageIndex) {
            pagerState.scrollToPage(centerPageIndex)
        }
    }

    LaunchedEffect(pagerState.settledPage, centerPageIndex, pages) {
        val settledPage = pagerState.settledPage
        if (settledPage == centerPageIndex) {
            handledSettledPage = null
            return@LaunchedEffect
        }
        if (handledSettledPage != null) return@LaunchedEffect
        val settledSlot = pages.getOrNull(settledPage)?.slot ?: return@LaunchedEffect
        handledSettledPage = settledPage
        onSettledAwayFromCenter(settledSlot)
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        key = { page ->
            pages.getOrNull(page)?.let { calendarPage ->
                "${calendarPage.slot}:${calendarPage.value}"
            } ?: page
        },
        beyondViewportPageCount = 1,
    ) { page ->
        pages.getOrNull(page)?.let { calendarPage ->
            pageContent(calendarPage.value)
        }
    }
}

internal fun <T> List<CalendarPagerPage<T>>.indexOfSlot(slot: CalendarPagerSlot): Int =
    indexOfFirst { it.slot == slot }
