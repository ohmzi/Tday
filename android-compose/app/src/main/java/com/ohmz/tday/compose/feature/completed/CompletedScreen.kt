package com.ohmz.tday.compose.feature.completed

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.text.flattenNotesToPlainText
import com.ohmz.tday.compose.core.ui.EmptyTaskWatermark
import com.ohmz.tday.compose.core.ui.FeedAnswer
import com.ohmz.tday.compose.core.ui.LocalSnackbarManager
import com.ohmz.tday.compose.core.ui.TaskSwipeActionButton
import com.ohmz.tday.compose.core.ui.TaskSwipeSlot
import com.ohmz.tday.compose.core.ui.TaskSwipeSlotBackHandler
import com.ohmz.tday.compose.core.ui.TdayEmptyState
import com.ohmz.tday.compose.core.ui.TdayFeedItemMotion
import com.ohmz.tday.compose.core.ui.TdayHaptics
import com.ohmz.tday.compose.core.ui.TdayHeroToolbar
import com.ohmz.tday.compose.core.ui.TdayMotionTokens
import com.ohmz.tday.compose.core.ui.TdaySearchCapsule
import com.ohmz.tday.compose.core.ui.TdayTaskRowSkeleton
import com.ohmz.tday.compose.core.ui.TdayTaskRowSkeletonGroup
import com.ohmz.tday.compose.core.ui.animateTaskSwipeOffsetAsState
import com.ohmz.tday.compose.core.ui.feedAnswer
import com.ohmz.tday.compose.core.ui.rememberLazyListHeroTitleCollapse
import com.ohmz.tday.compose.core.ui.rememberTaskRowFirstLineAlignment
import com.ohmz.tday.compose.core.ui.rememberTaskStrikeProgress
import com.ohmz.tday.compose.core.ui.rememberTaskSwipeRevealState
import com.ohmz.tday.compose.core.ui.rememberTdayMotionEnabled
import com.ohmz.tday.compose.core.ui.rememberTdayMotionScale
import com.ohmz.tday.compose.core.ui.rememberTdayTaskRowSkeletonMounted
import com.ohmz.tday.compose.core.ui.scaledDelay
import com.ohmz.tday.compose.core.ui.shouldCloseSwipeRow
import com.ohmz.tday.compose.core.ui.swipeSlotAfterRowDisclaim
import com.ohmz.tday.compose.core.ui.taskCopyText
import com.ohmz.tday.compose.core.ui.taskStrikethrough
import com.ohmz.tday.compose.core.ui.tdayBarButtonContainerColor
import com.ohmz.tday.compose.core.ui.tdayClosesSwipeRowOnOutsideTap
import com.ohmz.tday.compose.core.ui.tdayHeroTitleItem
import com.ohmz.tday.compose.core.ui.TdayHeroTitleMetrics
import com.ohmz.tday.compose.core.ui.tdayClosesSearchOnOutsideTap
import com.ohmz.tday.compose.core.ui.tdayPressable
import com.ohmz.tday.compose.ui.component.CreateTaskBottomSheet
import com.ohmz.tday.compose.ui.component.rememberEditSheetTarget
import com.ohmz.tday.compose.ui.theme.TdayCompletedTitleAccent
import com.ohmz.tday.compose.ui.theme.TdayDimens
import com.ohmz.tday.compose.ui.theme.TdayFloaterAccent
import com.ohmz.tday.compose.ui.theme.TdaySwipeCopyBackground
import com.ohmz.tday.compose.ui.theme.TdaySwipeDeleteBackground
import com.ohmz.tday.compose.ui.theme.TdaySwipeEditBackground
import com.ohmz.tday.compose.ui.theme.TdayTaskCompleteAccent
import com.ohmz.tday.compose.ui.theme.tdayListAccentColor
import com.ohmz.tday.compose.ui.theme.tdayListIconForList
import com.ohmz.tday.compose.ui.theme.tdayPriorityColor
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CompletedTimelineSameDateTaskSpacing = 2.dp
private val CompletedTimelineDateGroupSpacing = 6.dp
private val CompletedTimelineSectionTopSpacing = 6.dp
private val CompletedTimelineHeaderBodySpacing = 2.dp
private val CompletedTimelineCollapsedSectionSpacing = 4.dp
private val CompletedSwipeRowHeight = 56.dp

// The rest of what this screen draws that the scale has no rung for, named here rather than
// snapped onto a neighbouring step. Nearly all of it is one task row, and the names are the ones
// the calendar's and the root feed's copies of that row already carry — three copies of the same
// row that nothing will ever move together unless they answer to the same vocabulary first.

/** The section header's chevron. Named apart from the trailing badges at the same 18, because
 *  one is a control and the others are read-only marks, and nothing would resize both. */
private val CompletedSectionChevronSize = 18.dp

// The pills behind a swiped row: Edit + Copy + Delete, at the 3-pill width used elsewhere.
private val CompletedSwipeRevealWidth = 256.dp
private val CompletedSwipeActionSpacing = 16.dp

// A history row's content: the title column's inset, the meta line under it, its trailing badges.
private val CompletedRowTitleStartPadding = 10.dp
private val CompletedRowMetaSpacing = 5.dp
private val CompletedRowMetaIconSize = 13.dp
private val CompletedRowTrailingIconSize = 18.dp

// The restore toggle. The ripple is bounded, so its radius is half the circle it fills: change one
// without the other and the ripple either stops short of the edge or is clipped by it.
private val CompletedRestoreToggleSize = 28.dp
private val CompletedRestoreToggleRippleRadius = 14.dp
private val CompletedRestoreToggleIconSize = 24.dp

/** How far a restoring row lifts as it fades — the rise the calendar and the root feed play when a
 *  task leaves in the other direction. */
private val CompletedRestoreRiseOffsetY = (-10).dp

// The toolbar's circular buttons, which are Settings' and the calendar's button copied again.
// The press sink they used to name here went with the hand-written press: `tdayPressable`
// owns it now, as `TdayPress.SinkOffset`.
private val CompletedBarButtonIconSize = 22.dp

/**
 * The check-off's beats, run backwards — the same four every task row in every
 * client plays, only in the direction that puts a task back.
 *
 * This screen kept a third set for a long time, 180 / 180, which meant un-ticking
 * a task here took 620ms while un-ticking the same task from the calendar's own
 * Completed list took 780ms: the same app, the same control, the same direction.
 * Read against `CALENDAR_TASK_COMPLETION_*_MS` in `CalendarScreen.kt`,
 * `TASK_COMPLETION_*_MS` in `TodoListScreen.kt` and `taskCompletionTiming.ts`.
 *
 * The first two are gaps rather than motions — nobody watches the wait between
 * the tick clearing and the rule lifting — which is why they stay plain numbers
 * while the fade, which somebody does watch, reads its rung.
 *
 * All three legs go through [scaledDelay] rather than `delay`: each gap is only
 * here to let the beat before it land, and those beats are already on the
 * animator's clock. Backwards makes no difference to that — the argument is
 * written out once, against `TASK_COMPLETION_CHECK_TO_STRIKE_MS` in
 * `TodoListScreen.kt`.
 */
private const val COMPLETED_RESTORE_UNCHECK_TO_UNSTRIKE_MS = 160L
private const val COMPLETED_RESTORE_UNSTRIKE_TO_FADE_MS = 360L
private val COMPLETED_RESTORE_FADE_MS = TdayMotionTokens.Durations.Change.toLong()

private fun completedTaskBottomSpacing(
    itemIndex: Int,
    lastIndex: Int,
    showDateDivider: Boolean,
) = if (showDateDivider || itemIndex == lastIndex) {
    CompletedTimelineDateGroupSpacing
} else {
    CompletedTimelineSameDateTaskSpacing
}

private enum class CompletedRestorePhase {
    Completed,
    Unchecked,
    Unstruck,
    Fading,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletedScreen(
    uiState: CompletedUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onUncomplete: (CompletedItem) -> Unit,
    onDelete: (CompletedItem) -> Unit,
    onUpdateTask: (CompletedItem, CreateTaskPayload) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    // Scoped search: the history this screen is showing, and nothing else. The
    // field takes the toolbar row the way the list-detail screens hand theirs
    // over, so there is no second bar for it to live in.
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    // TdayHeroToolbar's row height, for the outside-tap guard: the bar is an
    // overlay on the same box as the content, so "below the bar" has to be
    // measured rather than inferred from the hierarchy.
    val pinnedToolbarHeightPx = with(LocalDensity.current) {
        TdayHeroTitleMetrics.ToolbarHeight.toPx()
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchNeedsFocus by remember { mutableStateOf(false) }
    val normalizedSearchQuery = remember(searchQuery) {
        searchQuery.trim().lowercase(Locale.getDefault())
    }
    val searchActive = searchExpanded && normalizedSearchQuery.isNotBlank()
    val closeSearch = {
        searchExpanded = false
        searchQuery = ""
        searchNeedsFocus = false
    }
    val visibleItems = remember(uiState.items, searchActive, normalizedSearchQuery) {
        if (!searchActive) {
            uiState.items
        } else {
            // The same two fields the web completed page and the list-detail
            // screens match on: the title and the notes flattened out of their
            // rich-text form.
            uiState.items.filter { completed ->
                completed.title.lowercase(Locale.getDefault())
                    .contains(normalizedSearchQuery) ||
                        flattenNotesToPlainText(completed.description)
                            .lowercase(Locale.getDefault())
                            .contains(normalizedSearchQuery)
            }
        }
    }
    val timelineSections = remember(visibleItems) {
        buildCompletedTimelineSections(visibleItems)
    }
    // Two readings of one rule, because the two lists below are different
    // questions: the scene answers for what is VISIBLE (a live query narrows it),
    // the placeholder answers for the store itself (a query is answered locally
    // and must never flash a placeholder per keystroke). Neither reads
    // `isLoading` any more -- see [feedAnswer] for why that flag meant the
    // opposite of what every gate like this was using it for.
    val completedAnswer = feedAnswer(
        storeRead = uiState.hasHydratedSnapshot,
        rowsEmpty = visibleItems.isEmpty(),
        firstAnswerLanded = uiState.firstAnswerLanded,
    )
    val completedStoreAnswer = feedAnswer(
        storeRead = uiState.hasHydratedSnapshot,
        rowsEmpty = uiState.items.isEmpty(),
        firstAnswerLanded = uiState.firstAnswerLanded,
    )
    val showEmptyState = completedAnswer == FeedAnswer.Empty
    val heroCollapse = rememberLazyListHeroTitleCollapse(listState = listState)
    val completedTitle = stringResource(R.string.completed_title)
    val completedIcon = ImageVector.vectorResource(R.drawable.ic_lucide_circle_check_big)
    var collapsedSectionKeys by rememberSaveable {
        mutableStateOf(emptySet<String>())
    }
    var editTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    // The screen's one swipe slot. `remember`, never `rememberSaveable`: the rows'
    // own reveal states are plain `remember`, so a restored id named a row that
    // had rebuilt closed. The full argument, and the reason this is a holder
    // rather than a hoisted `String?`, is at [TaskSwipeSlot].
    val swipeSlot = remember { TaskSwipeSlot() }
    val editTarget = rememberEditSheetTarget(
        id = editTargetId,
        current = remember(editTargetId, uiState.items) {
            editTargetId?.let { targetId -> uiState.items.firstOrNull { it.id == targetId } }
        },
    )
    // The open row's task leaving the feed hands the slot back. Read through
    // `snapshotFlow` rather than as an effect key so that no read of the slot
    // happens in this composable's body -- see [TaskSwipeSlot].
    LaunchedEffect(uiState.items, swipeSlot) {
        snapshotFlow { swipeSlot.openId }.collect { openId ->
            if (openId != null && uiState.items.none { it.id == openId }) {
                swipeSlot.openId = null
            }
        }
    }
    // A scroll closes the row, at the moment the list starts moving: an open row
    // is content and travels with the list, so one left open through a scroll
    // puts an armed Delete pill under a thumb now aimed at a different task. It
    // is also the only trigger that catches a vertical drag beginning on the
    // open row itself. Argued in full on `TodoListScreen`'s copy.
    LaunchedEffect(listState, swipeSlot) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && swipeSlot.openId != null) swipeSlot.openId = null
        }
    }
    BackHandler(enabled = searchExpanded) {
        closeSearch()
    }
    // After the search handler, because later registration wins in the back
    // dispatcher: a revealed row is the innermost state back can be in.
    TaskSwipeSlotBackHandler(slot = swipeSlot)
    // The placeholder, and how long its lazy item outlives it. Hoisted because
    // `LazyListScope` is not a composition; same window as the timeline's copy,
    // and deliberately the same call rather than an "it does not matter here"
    // — this list happens to space at 0 dp, so the gap a permanent mount leaves
    // costs nothing today, and the next person to give it spacing would inherit
    // the timeline's bug without a line anywhere saying they had.
    val completedFeedSkeletonVisible = completedStoreAnswer == FeedAnswer.AwaitingFirst
    val completedFeedSkeletonMounted =
        rememberTdayTaskRowSkeletonMounted(completedFeedSkeletonVisible)

    Scaffold(
        // One interceptor per screen, at the outermost composable so the header,
        // the FAB and the gaps between rows are all inside it. It observes and
        // never consumes -- see `tdayClosesSwipeRowOnOutsideTap`.
        modifier = Modifier.tdayClosesSwipeRowOnOutsideTap(
            slot = swipeSlot,
            close = { swipeSlot.openId = null },
        ),
        containerColor = colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    // Tap the history and the field goes away, as on the root
                    // feeds. The toolbar is an overlay on this same box, so the
                    // guard is its row height rather than a reported rect.
                    .tdayClosesSearchOnOutsideTap(
                        isSearchOpen = searchExpanded,
                        barHeightPx = pinnedToolbarHeightPx,
                        close = closeSearch,
                    ),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    // No top padding: the hero item reserves the bar's height
                    // itself, so the scroll offset is a clean count from the top.
                    contentPadding = PaddingValues(
                        start = TdayDimens.ContentPaddingHorizontal,
                        end = TdayDimens.ContentPaddingHorizontal,
                        bottom = TdayDimens.SpacingXxs,
                    ),
                    verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingNone),
                ) {
                    tdayHeroTitleItem(
                        title = completedTitle,
                        icon = completedIcon,
                        accentColor = COMPLETED_TITLE_COLOR,
                        titleColor = COMPLETED_TITLE_COLOR,
                        collapseProgress = heroCollapse.progress,
                    )
                    timelineSections.forEachIndexed { sectionIndex, section ->
                        // A live query outranks a shut month: history opens with
                        // older months collapsed, and a task the search turns up
                        // inside one must not stay hidden behind its header. Both
                        // the timeline screens and web make the same call.
                        val isCollapsed = !searchActive &&
                                collapsedSectionKeys.contains(section.key)
                        item(key = "completed-header-${section.key}") {
                            CompletedTimelineSectionHeader(
                                modifier = Modifier
                                    // Placement and nothing else: a month header is
                                    // never added or removed by a check-off, only
                                    // displaced by one. That is [TdayFeedItemMotion]'s
                                    // rule 1, which this site was already obeying by
                                    // hand at the same 320.
                                    .animateItem(
                                        fadeInSpec = null,
                                        placementSpec = TdayFeedItemMotion.Placement,
                                        fadeOutSpec = null,
                                    )
                                    .padding(
                                        top = if (sectionIndex == 0) {
                                            TdayDimens.SpacingNone
                                        } else {
                                            CompletedTimelineSectionTopSpacing
                                        },
                                        bottom = if (isCollapsed) {
                                            CompletedTimelineCollapsedSectionSpacing
                                        } else {
                                            CompletedTimelineHeaderBodySpacing
                                        },
                                    ),
                                section = section,
                                isCollapsed = isCollapsed,
                                onHeaderClick = {
                                    collapsedSectionKeys =
                                        if (isCollapsed) {
                                            collapsedSectionKeys - section.key
                                        } else {
                                            collapsedSectionKeys + section.key
                                        }
                                },
                            )
                        }
                        if (!isCollapsed) {
                            section.items.forEachIndexed { itemIndex, completed ->
                                val showCompletedDateDivider = shouldShowDateDivider(
                                    afterItemIndex = itemIndex,
                                    inSectionIndex = sectionIndex,
                                    sections = timelineSections,
                                    collapsedSectionKeys = collapsedSectionKeys,
                                )
                                item(key = "completed-row-${section.key}-${completed.id}") {
                                    CompletedSwipeRow(
                                        modifier = Modifier
                                            .animateItem(
                                                fadeInSpec = TdayFeedItemMotion.FadeIn,
                                                placementSpec = TdayFeedItemMotion.Placement,
                                                fadeOutSpec = TdayFeedItemMotion.FadeOut,
                                            )
                                            .padding(
                                                bottom = completedTaskBottomSpacing(
                                                    itemIndex = itemIndex,
                                                    lastIndex = section.items.lastIndex,
                                                    showDateDivider = showCompletedDateDivider,
                                                ),
                                            ),
                                        item = completed,
                                        // Floater lists are a separate namespace from
                                        // scheduled-task lists (uiState.lists) — resolve
                                        // each row's icon/color against the set it
                                        // actually belongs to.
                                        lists = if (completed.isFloater) uiState.floaterLists else uiState.lists,
                                        showDateDivider = showCompletedDateDivider,
                                        onInfo = { editTargetId = completed.id },
                                        onDelete = { onDelete(completed) },
                                        onUncomplete = { onUncomplete(completed) },
                                        swipeSlot = swipeSlot,
                                    )
                                }
                            }
                        }
                    }

                    // This screen said "Loading" as a centred `displaySmall`
                    // ExtraBold word with 290 dp of padding around it — the
                    // timeline said the same thing in a card at body size, and
                    // neither looked anything like the feed that replaced it.
                    // Both are the skeleton now, at the row's own geometry.
                    //
                    // Mounted for a window and hidden by `visible`: removing
                    // the item on the loading flag would leave the exit nothing
                    // to play on, and the exit is what makes this a hand-over
                    // rather than a cut. See the timeline's copy for why the
                    // fade is paired with a shrink.
                    if (completedFeedSkeletonMounted) {
                        item(
                            key = "completed-feed-skeleton",
                            contentType = "completed-feed-skeleton",
                        ) {
                            AnimatedVisibility(
                                visible = completedFeedSkeletonVisible,
                                enter = EnterTransition.None,
                                exit = if (rememberTdayMotionEnabled()) {
                                    fadeOut(animationSpec = TdayTaskRowSkeleton.handoff()) +
                                        shrinkVertically(
                                            animationSpec = TdayTaskRowSkeleton.handoff(),
                                        )
                                } else {
                                    ExitTransition.None
                                },
                            ) {
                                TdayTaskRowSkeletonGroup()
                            }
                        }
                    }

                    if (showEmptyState) {
                        item(key = "completed-empty", contentType = "completed-empty") {
                            if (searchActive) {
                                TdayEmptyState(
                                    icon = R.drawable.ic_lucide_search,
                                    accentColor = COMPLETED_TITLE_COLOR,
                                    title = stringResource(R.string.scheduled_task_home_search_no_results),
                                    description = stringResource(R.string.search_no_results_body),
                                    modifier = Modifier.padding(vertical = TdayDimens.Spacing3xl),
                                )
                            } else {
                                TdayEmptyState(
                                    icon = R.drawable.ic_lucide_circle_check_big,
                                    accentColor = COMPLETED_TITLE_COLOR,
                                    title = stringResource(R.string.completed_empty),
                                    description = stringResource(R.string.completed_empty_body),
                                    modifier = Modifier.padding(vertical = TdayDimens.Spacing3xl),
                                )
                            }
                        }
                    }

                    // Keyed, because a load failure genuinely adds and removes a
                    // row here and `animateItem` cannot animate either on an item
                    // whose identity is its index. All three specs, not placement
                    // alone: the card is added and removed rather than displaced,
                    // and [TdayFeedItemMotion] is the clock the rows above it
                    // name too — a card that appears in one frame while its
                    // neighbours are mid-travel is the defect.
                    uiState.errorMessage?.let { message ->
                        item(key = "error-retry", contentType = "error_retry") {
                            val errorCardMotionEnabled = rememberTdayMotionEnabled()
                            com.ohmz.tday.compose.core.ui.ErrorRetryCard(
                                message = message,
                                onRetry = onRefresh,
                                modifier = if (errorCardMotionEnabled) {
                                    Modifier.animateItem(
                                        fadeInSpec = TdayFeedItemMotion.FadeIn,
                                        placementSpec = TdayFeedItemMotion.Placement,
                                        fadeOutSpec = TdayFeedItemMotion.FadeOut,
                                    )
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(TdayDimens.BottomScrollSpacer)) }
                }
            }

            // Page texture, not an empty state — but the empty scene is an
            // illustration of its own, and stacking the two put a 212dp glyph
            // behind a picture of the same glyph.
            if (!showEmptyState) {
                EmptyTaskWatermark(
                    iconRes = R.drawable.ic_lucide_circle_check_big,
                    accentColor = COMPLETED_TITLE_COLOR,
                )
            }

            // Last, so it draws over the content passing behind it.
            TdayHeroToolbar(
                title = completedTitle,
                titleColor = COMPLETED_TITLE_COLOR,
                collapseProgress = heroCollapse.progress,
                // Gone while the field is up: a back chevron beside an open
                // search is a second way out that leaves the screen rather than
                // the query, and it costs the field the width that makes a
                // placeholder readable.
                onBack = if (searchExpanded) null else onBack,
                backContentDescription = stringResource(R.string.action_back),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(padding),
                titleSuppressed = searchExpanded,
            ) {
                if (searchExpanded) {
                    // The field takes the WHOLE bar — back chevron, title and
                    // action cluster all give way to it, as they do on the root
                    // feeds and on iOS's TimelineTopBar.
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(searchNeedsFocus) {
                        if (!searchNeedsFocus) return@LaunchedEffect
                        // Consumed on the way in, so returning to a screen that
                        // still has the field open does not re-open the
                        // keyboard with it.
                        searchNeedsFocus = false
                        focusRequester.requestFocus()
                    }
                    TdaySearchCapsule(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = stringResource(R.string.action_search_in, completedTitle),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        // The one control in the row, so its X leaves the search
                        // — and leaving clears the query on the way out.
                        onClose = closeSearch,
                        trailingContentDescription = stringResource(R.string.action_close_search),
                    )
                } else if (uiState.items.isNotEmpty()) {
                    // No magnifier over an empty history: there is no set for a
                    // query to narrow, and the button would only raise a keyboard
                    // over the empty-state scene, which is the whole of what the
                    // screen has to say.
                    CompletedBarButton(
                        // Only opens: the bar hands its row over to the field,
                        // so this button is not on screen to be tapped again.
                        onClick = {
                            searchExpanded = true
                            searchNeedsFocus = true
                        },
                        icon = ImageVector.vectorResource(R.drawable.ic_lucide_search),
                        contentDescription = stringResource(R.string.action_search),
                    )
                }
            }
        }
    }

    editTarget?.let { completed ->
        val editableLists = if (completed.isFloater) uiState.floaterLists else uiState.lists
        CreateTaskBottomSheet(
            lists = editableLists,
            editingTask = completed.toEditableTodo(editableLists),
            defaultListId = completed.resolveListId(editableLists),
            // Floaters have no due date — hide the schedule controls the same
            // way the live Floater tab's own edit sheet does.
            defaultScheduled = !completed.isFloater,
            showScheduleControls = !completed.isFloater,
            onDismiss = { editTargetId = null },
            onCreateTask = { _ -> },
            onUpdateTask = { _, payload -> onUpdateTask(completed, payload) },
        )
    }
}

@Composable
private fun CompletedTimelineSectionHeader(
    modifier: Modifier = Modifier,
    section: CompletedSection,
    isCollapsed: Boolean,
    onHeaderClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val headerInteractionSource = remember { MutableInteractionSource() }
    val isHeaderPressed by headerInteractionSource.collectIsPressedAsState()
    val collapseChevronRotation by animateFloatAsState(
        targetValue = if (isCollapsed) -90f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "completedSectionChevronRotation",
    )
    val baseHeaderColor = colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
    val headerTextColor = if (isHeaderPressed) {
        androidx.compose.ui.graphics.lerp(baseHeaderColor, colorScheme.onSurface, 0.16f)
    } else {
        baseHeaderColor
    }
    val baseChevronColor = colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    val chevronColor = if (isHeaderPressed) {
        androidx.compose.ui.graphics.lerp(baseChevronColor, colorScheme.onSurface, 0.16f)
    } else {
        baseChevronColor
    }
    Column(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = headerInteractionSource,
                    indication = null,
                    onClick = onHeaderClick,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = section.title,
                color = headerTextColor,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_down),
                contentDescription = if (isCollapsed) {
                    stringResource(R.string.action_expand_section)
                } else {
                    stringResource(R.string.action_collapse_section)
                },
                tint = chevronColor,
                modifier = Modifier
                    .padding(start = TdayDimens.SpacingSm)
                    .size(CompletedSectionChevronSize)
                    .graphicsLayer { rotationZ = collapseChevronRotation },
            )
        }
    }
}

@Composable
private fun CompletedSwipeRow(
    modifier: Modifier = Modifier,
    item: CompletedItem,
    lists: List<ListSummary>,
    showDateDivider: Boolean,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    onUncomplete: () -> Unit,
    swipeSlot: TaskSwipeSlot,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    // Edit + Copy + Delete: matches the 3-pill width used elsewhere (see
    // SwipeTaskRow.revealWidth).
    val swipeRevealState =
        rememberTaskSwipeRevealState(item.id, revealWidth = CompletedSwipeRevealWidth)
    val clipboardManager = LocalClipboardManager.current
    val snackbarManager = LocalSnackbarManager.current
    val copyContext = LocalContext.current
    val copiedMessage = stringResource(R.string.task_copied_toast)
    val copyFailedMessage = stringResource(R.string.task_copy_failed_toast)
    var restorePhase by remember(item.id) { mutableStateOf(CompletedRestorePhase.Completed) }
    fun claimSwipeSlot() {
        if (swipeSlot.openId != item.id) {
            swipeSlot.openId = item.id
        }
    }

    // This row handing back the slot it holds, and only that -- see
    // [swipeSlotAfterRowDisclaim] for why it is guarded and for the revoke
    // that deliberately is not.
    fun closeSwipeSlot() {
        swipeRevealState.close()
        swipeSlot.openId = swipeSlotAfterRowDisclaim(swipeSlot.openId, item.id)
    }
    // Hoisted above the reveal's own animation because that is now one of its
    // callers: with the app's Reduce Motion switch on, a close draws its
    // finished state instead of springing to it. One read, two uses -- the
    // switch and the row can never disagree about the same device.
    val restoreMotionScale = rememberTdayMotionScale()
    val animatedOffsetX by animateTaskSwipeOffsetAsState(
        state = swipeRevealState,
        label = "completedSwipeOffset",
        scale = restoreMotionScale,
    )
    val actionRevealProgress = swipeRevealState.revealProgress(animatedOffsetX)
    val showCompletedCheckmark = restorePhase == CompletedRestorePhase.Completed
    val showStrikethrough =
        restorePhase == CompletedRestorePhase.Completed || restorePhase == CompletedRestorePhase.Unchecked
    val isFading = restorePhase == CompletedRestorePhase.Fading
    val isRestoring = restorePhase != CompletedRestorePhase.Completed
    val restoreMotionEnabled = rememberTdayMotionEnabled()
    // Gated like the beats in front of it. The last leg of the restore is timed
    // against this fade, so a fade still running while its own wait had been zeroed
    // would pull the row out of the list at full opacity — exactly the pop that leg
    // exists to prevent.
    val rowAlpha by animateFloatAsState(
        targetValue = if (isFading) 0f else 1f,
        animationSpec = if (restoreMotionEnabled) {
            tween(
                durationMillis = COMPLETED_RESTORE_FADE_MS.toInt(),
                easing = TdayMotionTokens.Easings.Standard,
            )
        } else {
            snap()
        },
        label = "completedRestoreRowAlpha",
    )
    val rowScale by animateFloatAsState(
        targetValue = if (isFading) 0.985f else 1f,
        animationSpec = if (restoreMotionEnabled) {
            tween(
                durationMillis = COMPLETED_RESTORE_FADE_MS.toInt(),
                easing = TdayMotionTokens.Easings.Standard,
            )
        } else {
            snap()
        },
        label = "completedRestoreRowScale",
    )
    val rowOffsetY by animateDpAsState(
        targetValue = if (isFading) CompletedRestoreRiseOffsetY else TdayDimens.SpacingNone,
        animationSpec = if (restoreMotionEnabled) {
            tween(
                durationMillis = COMPLETED_RESTORE_FADE_MS.toInt(),
                easing = TdayMotionTokens.Easings.Standard,
            )
        } else {
            snap()
        },
        label = "completedRestoreRowOffsetY",
    )
    // The rule retracts the way it swept. `animateFloatAsState` starts AT its
    // target, so a row that was already complete when the screen opened is simply
    // drawn struck — the sweep only ever plays for the tap that asked for it.
    val titleStrikeProgress =
        rememberTaskStrikeProgress(showStrikethrough, "completedRestoreTitleStrike")
    var titleLayoutResult by remember(item.id) { mutableStateOf<TextLayoutResult?>(null) }
    // The number behind that switch, for this row's waits rather than its specs:
    // the hint's two holds and the three legs of the restore are gaps between
    // beats this row gates on [restoreMotionEnabled], which is what makes the
    // app's own scale the right clock for them. See [scaledDelay].
    // The two beats this row cut straight to. The tint answers the finger, so it is
    // Quick; the title colour travels with the rule crossing it, so Emphasis — and
    // Emphasis is also what the rule itself runs on, which is the point: a colour
    // that settled first would read as two events rather than one.
    val restoreToggleTint by animateColorAsState(
        targetValue = if (showCompletedCheckmark) {
            TdayTaskCompleteAccent
        } else {
            colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
        },
        animationSpec = if (restoreMotionEnabled) {
            tween(
                durationMillis = TdayMotionTokens.Durations.Quick,
                easing = TdayMotionTokens.Easings.Standard,
            )
        } else {
            snap()
        },
        label = "completedRestoreToggleTint",
    )
    val titleColor by animateColorAsState(
        targetValue = if (showStrikethrough) {
            colorScheme.onSurface.copy(alpha = 0.78f)
        } else {
            colorScheme.onSurface
        },
        animationSpec = if (restoreMotionEnabled) {
            tween(
                durationMillis = TdayMotionTokens.Durations.Emphasis,
                easing = TdayMotionTokens.Easings.Standard,
            )
        } else {
            snap()
        },
        label = "completedRestoreTitleColor",
    )
    val completedAtText = COMPLETED_ROW_TIME_FORMATTER
        .withZone(ZoneId.systemDefault())
        .format(item.completedAt ?: item.due ?: Instant.EPOCH)
    val listMeta = item.resolveListSummary(lists)
    val listIndicatorColor = listMeta?.color?.let(::tdayListAccentColor)
        ?: item.listColor?.let(::tdayListAccentColor)
        ?: colorScheme.onSurfaceVariant.copy(alpha = 0.86f)
    val showListIndicator = !item.listName.isNullOrBlank() || listMeta != null
    val priorityIcon = priorityIconFor(item.priority)
    val showPriorityIcon = priorityIcon != null
    val rowShape = RoundedCornerShape(TdayDimens.RadiusRow)
    val foregroundColor = colorScheme.background
    // This row's toggle is 28 dp against a 24 sp title line, so the derivation
    // answers with a 2 dp text inset rather than the task list's 12 — the same
    // call, a different row, and no number written down twice.
    val firstLine = rememberTaskRowFirstLineAlignment(
        titleStyle = MaterialTheme.typography.titleMedium,
        controlHeight = CompletedRestoreToggleSize,
    )
    // The row's whole subscription to the screen's slot, and the only place it
    // reads it -- outside composition, so no row recomposes when another opens
    // or closes. [shouldCloseSwipeRow] deliberately carries no `openId != null`
    // clause: that guard meant the slot could be handed on but never revoked,
    // and every dismissal is a write of `null`. The close is the same
    // `TaskSwipeMotion.Release` rung the open uses, and is silent by the
    // argument at `TaskSwipeRevealState.settle`.
    LaunchedEffect(swipeSlot, item.id) {
        snapshotFlow { swipeSlot.openId }.collect { openId ->
            if (shouldCloseSwipeRow(openId, item.id, swipeRevealState.isOpenOrDragging)) {
                swipeRevealState.close()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = rowAlpha
                scaleX = rowScale
                scaleY = rowScale
                translationY = rowOffsetY.toPx()
            },
        verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingXs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CompletedSwipeRowHeight),
        ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = TdayDimens.SpacingXxs),
                    horizontalArrangement = Arrangement.spacedBy(CompletedSwipeActionSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TaskSwipeActionButton(
                        icon = R.drawable.ic_lucide_square_pen,
                        contentDescription = stringResource(R.string.action_edit_task),
                        label = stringResource(R.string.action_edit),
                        tint = Color.White,
                        background = TdaySwipeEditBackground,
                        revealProgress = actionRevealProgress,
                        revealDelay = 0.62f,
                        onClick = {
                            TdayHaptics.buttonPress(view)
                            closeSwipeSlot()
                            onInfo()
                        },
                    )
                    TaskSwipeActionButton(
                        icon = R.drawable.ic_lucide_copy,
                        contentDescription = stringResource(R.string.action_copy_task),
                        label = stringResource(R.string.action_copy),
                        tint = Color.White,
                        background = TdaySwipeCopyBackground,
                        revealProgress = actionRevealProgress,
                        revealDelay = 0.40f,
                        onClick = {
                            TdayHaptics.buttonPress(view)
                            closeSwipeSlot()
                            runCatching {
                                clipboardManager.setText(AnnotatedString(taskCopyText(copyContext, item)))
                            }.onSuccess {
                                snackbarManager?.showSuccess(copiedMessage)
                            }.onFailure {
                                snackbarManager?.showError(copyFailedMessage)
                            }
                        },
                    )
                    TaskSwipeActionButton(
                        icon = R.drawable.ic_lucide_trash,
                        contentDescription = stringResource(R.string.action_delete_task),
                        label = stringResource(R.string.action_delete),
                        tint = Color.White,
                        background = TdaySwipeDeleteBackground,
                        revealProgress = actionRevealProgress,
                        revealDelay = 0.04f,
                        onClick = {
                            TdayHaptics.destructive(view)
                            closeSwipeSlot()
                            onDelete()
                        },
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = animatedOffsetX }
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                if (delta < 0f || swipeRevealState.isOpenOrDragging) {
                                    claimSwipeSlot()
                                }
                                // The detent, under the finger: the row has just committed to
                                // opening and says so. Fired bare — no preference read and no
                                // motion-scale check. `performHapticFeedback` already answers to
                                // the system's own touch-feedback switch, which is why the two
                                // native clients keep no switch of their own and web grows one
                                // (docs/motion/LEDGER.md:1277), and reduce motion silences
                                // animation, not feedback. The cost is named in full at
                                // `TaskSwipeRevealState.dragBy`: cross the detent, drag back,
                                // release closed, and you felt a reveal that did not happen.
                                if (swipeRevealState.dragBy(delta)) TdayHaptics.reveal(view)
                                if (!swipeRevealState.isOpenOrDragging && swipeSlot.openId == item.id) {
                                    swipeSlot.openId = null
                                }
                            },
                            onDragStopped = { velocity ->
                                // The other arm of the same event. A fling opens the row from
                                // under the distance threshold, so without this the fastest
                                // swipe in the app would be the only silent one; `settle`
                                // answers false when the detent already fired, and false on
                                // every close.
                                if (swipeRevealState.settle(velocity)) TdayHaptics.reveal(view)
                                if (swipeRevealState.isOpenOrDragging) {
                                    claimSwipeSlot()
                                } else if (swipeSlot.openId == item.id) {
                                    swipeSlot.openId = null
                                }
                            },
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (swipeRevealState.isOpenOrDragging) {
                                closeSwipeSlot()
                            } else if (!swipeRevealState.isHinting && !isRestoring) {
                                claimSwipeSlot()
                                coroutineScope.launch {
                                    swipeRevealState.playHint(restoreMotionScale)
                                    if (swipeSlot.openId == item.id && !swipeRevealState.isOpenOrDragging) {
                                        swipeSlot.openId = null
                                    }
                                }
                            }
                        },
                    shape = rowShape,
                    colors = CardDefaults.cardColors(containerColor = foregroundColor),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = TdayDimens.CardElevationDefault,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            // Stacked from the top, then the whole block put back in
                            // the middle of the card. `CompletedSwipeRowHeight` is a
                            // fixed 56 dp, so top-aligning alone would have lifted
                            // every row's content off its own centre — a 4 dp move on
                            // the ~97% of rows whose title fits one line, which is the
                            // whole feed paying for the fix to the few that wrap.
                            // `wrapContentHeight` measures the content, centres it, and
                            // leaves `Alignment.Top` to do its work INSIDE that block.
                            .wrapContentHeight(Alignment.CenterVertically)
                            .padding(
                                horizontal = TdayDimens.SpacingXs,
                                vertical = TdayDimens.SpacingXxs,
                            ),
                        verticalAlignment = Alignment.Top,
                    ) {
                        CompletedCircularToggleIcon(
                            // The toggle is the title's bullet, so it takes the first line's
                            // centre the same way the text column does — see `topInsetFor`.
                            modifier = Modifier.padding(
                                top = firstLine.topInsetFor(CompletedRestoreToggleSize),
                            ),
                            imageVector = if (showCompletedCheckmark) {
                                ImageVector.vectorResource(R.drawable.ic_lucide_circle_check_big)
                            } else {
                                ImageVector.vectorResource(R.drawable.ic_lucide_circle)
                            },
                            contentDescription = stringResource(R.string.label_undo_complete),
                            tint = restoreToggleTint,
                            enabled = !isRestoring,
                            onClick = {
                                TdayHaptics.toggle(view, on = false)
                                closeSwipeSlot()
                                coroutineScope.launch {
                                    restorePhase = CompletedRestorePhase.Unchecked
                                    scaledDelay(
                                        COMPLETED_RESTORE_UNCHECK_TO_UNSTRIKE_MS,
                                        restoreMotionScale,
                                    )
                                    restorePhase = CompletedRestorePhase.Unstruck
                                    scaledDelay(
                                        COMPLETED_RESTORE_UNSTRIKE_TO_FADE_MS,
                                        restoreMotionScale,
                                    )
                                    restorePhase = CompletedRestorePhase.Fading
                                    scaledDelay(
                                        COMPLETED_RESTORE_FADE_MS,
                                        restoreMotionScale,
                                    )
                                    onUncomplete()
                                }
                            },
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(
                                    start = CompletedRowTitleStartPadding,
                                    top = firstLine.titleTopInset,
                                ),
                        ) {
                            Text(
                                text = item.title,
                                // Drawn rather than declared, the same as every other task
                                // row: `TextDecoration.LineThrough` is a boolean, so the
                                // beat the user asked for — the rule coming off — happened
                                // in one frame in the middle of a 780ms sequence whose
                                // every other beat was tweened. `taskStrikethrough` argues
                                // the mechanism where it lives.
                                modifier = Modifier.taskStrikethrough(
                                    progress = titleStrikeProgress,
                                    layout = titleLayoutResult,
                                    color = titleColor,
                                    thickness = TdayDimens.BorderWidthThick,
                                ),
                                color = titleColor,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 2,
                                onTextLayout = { titleLayoutResult = it },
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(CompletedRowMetaSpacing),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (item.isFloater) {
                                    // The app's one existing floater marker (leaf + teal),
                                    // reused here so a floater reads as one at a glance
                                    // even interleaved with todos in the same timeline —
                                    // same glyph/color as the Floater root-feed tab.
                                    Icon(
                                        imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_leaf),
                                        contentDescription = stringResource(R.string.root_feed_tab_floater),
                                        tint = TdayFloaterAccent,
                                        modifier = Modifier.size(CompletedRowMetaIconSize),
                                    )
                                }
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_clock),
                                    contentDescription = null,
                                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
                                    modifier = Modifier.size(CompletedRowMetaIconSize),
                                )
                                Text(
                                    text = completedAtText,
                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                )
                            }
                        }

                        if (showPriorityIcon) {
                            Row(
                                modifier = Modifier.padding(
                                    // The flag is an annotation ON the task, so it
                                    // reads with the title's first line exactly as
                                    // the toggle does. Centring it across a wrapped
                                    // title left it floating in the same gap.
                                    top = firstLine.topInsetFor(CompletedRowTrailingIconSize),
                                    end = TdayDimens.Spacing3xl,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(TdayDimens.SpacingMd),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (showListIndicator) {
                                    Icon(
                                        imageVector = tdayListIconForList(listMeta?.iconKey, listMeta?.name),
                                        contentDescription = stringResource(R.string.label_task_list),
                                        tint = listIndicatorColor,
                                        modifier = Modifier.size(CompletedRowTrailingIconSize),
                                    )
                                }
                                Icon(
                                    imageVector = priorityIcon
                                        ?: ImageVector.vectorResource(R.drawable.ic_lucide_flag),
                                    contentDescription = stringResource(R.string.label_priority_task),
                                    tint = tdayPriorityColor(item.priority),
                                    modifier = Modifier.size(CompletedRowTrailingIconSize),
                                )
                            }
                        } else if (showListIndicator) {
                            Icon(
                                imageVector = tdayListIconForList(listMeta?.iconKey, listMeta?.name),
                                contentDescription = stringResource(R.string.label_task_list),
                                tint = listIndicatorColor,
                                modifier = Modifier
                                    .padding(
                                        top = firstLine.topInsetFor(CompletedRowTrailingIconSize),
                                        end = TdayDimens.Spacing3xl,
                                    )
                                    .size(CompletedRowTrailingIconSize),
                            )
                        }
                    }
                }
            }
        if (showDateDivider) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TdayDimens.BorderWidth)
                    .background(colorScheme.outlineVariant.copy(alpha = 0.58f)),
            )
        }
    }
}

@Composable
private fun CompletedCircularToggleIcon(
    imageVector: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        // Outside `size`, so the row's first-line inset moves the 28 dp disc
        // without making it a smaller one.
        modifier = modifier
            .size(CompletedRestoreToggleSize)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    radius = CompletedRestoreToggleRippleRadius,
                ),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Crossed over rather than swapped, for the reason the task list's own
        // toggle gives: here the glyph is the whole control, so a one-frame swap
        // is the control hard-cutting.
        Crossfade(
            targetState = imageVector,
            animationSpec = if (rememberTdayMotionEnabled()) {
                tween(
                    durationMillis = TdayMotionTokens.Durations.Quick,
                    easing = TdayMotionTokens.Easings.Standard,
                )
            } else {
                snap()
            },
            label = "completedRestoreToggleGlyph",
        ) { glyph ->
            Icon(
                imageVector = glyph,
                contentDescription = contentDescription.takeIf { glyph == imageVector },
                tint = tint,
                modifier = Modifier.size(CompletedRestoreToggleIconSize),
            )
        }
    }
}

/**
 * The circle this screen's toolbar actions sit in.
 *
 * A local copy of the timeline screen's `TodayHeaderButton`, which is private to
 * `TodoListScreen` and has no shared home yet — the fill, the size and the lift
 * come from the same tokens as the back button beside it, so the two match
 * whatever the scheme does with them.
 */
@Composable
private fun CompletedBarButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = Modifier
            .tdayPressable(interactionSource, scale = TdayMotionTokens.PressScales.Bar),
        onClick = {
            TdayHaptics.buttonPress(view)
            onClick()
        },
        interactionSource = interactionSource,
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = tdayBarButtonContainerColor()),
        elevation = CardDefaults.cardElevation(
            defaultElevation = TdayDimens.BarButtonElevation,
            pressedElevation = TdayDimens.CardElevationDefault,
        ),
    ) {
        Box(
            modifier = Modifier.size(TdayDimens.FabSize),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(CompletedBarButtonIconSize),
            )
        }
    }
}

@Composable
private fun priorityIconFor(priority: String): ImageVector? {
    return when (priority.trim().lowercase(Locale.getDefault())) {
        "medium" -> ImageVector.vectorResource(R.drawable.ic_lucide_flag_filled)
        "high", "urgent", "important" -> ImageVector.vectorResource(R.drawable.ic_lucide_flag_filled)
        else -> null
    }
}

private fun CompletedItem.resolveListSummary(lists: List<ListSummary>): ListSummary? {
    val name = listName?.trim()?.lowercase(Locale.getDefault()) ?: return null
    return lists.firstOrNull { it.name.trim().lowercase(Locale.getDefault()) == name }
}

private fun CompletedItem.resolveListId(lists: List<ListSummary>): String? {
    return resolveListSummary(lists)?.id
}

private fun CompletedItem.toEditableTodo(lists: List<ListSummary>): TodoItem {
    val resolvedListId = resolveListId(lists)
    val canonical = originalTodoId ?: id
    return TodoItem(
        id = canonical,
        canonicalId = canonical,
        title = title,
        description = description,
        priority = priority,
        due = due,
        rrule = rrule,
        instanceDate = instanceDate,
        pinned = false,
        completed = true,
        listId = resolvedListId,
        updatedAt = completedAt,
    )
}

private data class CompletedSection(
    val key: String,
    val title: String,
    val items: List<CompletedItem>,
)

private fun shouldShowDateDivider(
    afterItemIndex: Int,
    inSectionIndex: Int,
    sections: List<CompletedSection>,
    collapsedSectionKeys: Set<String>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean {
    val section = sections.getOrNull(inSectionIndex) ?: return false
    val currentItem = section.items.getOrNull(afterItemIndex) ?: return false
    val nextItemInSection = section.items.getOrNull(afterItemIndex + 1)
    if (nextItemInSection != null) {
        return !currentItem.completedDate()
            .isSameLocalDayAs(nextItemInSection.completedDate(), zoneId)
    }

    val nextVisibleItem = sections
        .asSequence()
        .drop(inSectionIndex + 1)
        .filter { it.key !in collapsedSectionKeys }
        .flatMap { it.items.asSequence() }
        .firstOrNull()
        ?: return false

    return !currentItem.completedDate().isSameLocalDayAs(nextVisibleItem.completedDate(), zoneId)
}

private fun CompletedItem.completedDate() = completedAt ?: due ?: Instant.EPOCH

private fun Instant.isSameLocalDayAs(other: Instant, zoneId: ZoneId): Boolean =
    LocalDate.ofInstant(this, zoneId) == LocalDate.ofInstant(other, zoneId)

private fun buildCompletedTimelineSections(
    items: List<CompletedItem>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<CompletedSection> {
    val groupedByDate = items.groupBy { item ->
        LocalDate.ofInstant(item.completedAt ?: item.due ?: Instant.EPOCH, zoneId)
    }

    return groupedByDate.keys
        .sortedDescending()
        .map { date ->
            val sectionItems = groupedByDate[date].orEmpty().sortedWith(
                compareByDescending<CompletedItem> { it.completedAt ?: it.due ?: Instant.EPOCH }
                    .thenBy { it.title.lowercase(Locale.getDefault()) }
                    .thenBy { it.id },
            )
            CompletedSection(
                key = "completed-$date",
                title = date.format(COMPLETED_SECTION_FORMATTER),
                items = sectionItems,
            )
        }
}

private val COMPLETED_TITLE_COLOR = TdayCompletedTitleAccent
private val COMPLETED_SECTION_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
private val COMPLETED_ROW_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
