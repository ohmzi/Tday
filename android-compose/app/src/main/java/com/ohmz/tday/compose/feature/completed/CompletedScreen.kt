package com.ohmz.tday.compose.feature.completed

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.navigation.CompletedScope
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
import com.ohmz.tday.compose.core.ui.WatermarkGlyphSize
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
import com.ohmz.tday.compose.ui.component.TdaySegmentedSlider
import com.ohmz.tday.compose.ui.component.TdaySegmentedSliderMotion
import com.ohmz.tday.compose.ui.component.rememberEditSheetTarget
import com.ohmz.tday.compose.ui.theme.TdayCompletedTileAccent
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

// The tab strip's own breathing room. The hero block above it already leaves the settled
// content gap; this is the distance from the title's block to the control and from the
// control to the first section header, and neither is a TdayDimens step.
private val CompletedScopeTabsTopSpacing = 4.dp
private val CompletedScopeTabsBottomSpacing = 10.dp

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
    initialScope: CompletedScope,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onUncomplete: (CompletedItem) -> Unit,
    onDelete: (CompletedItem) -> Unit,
    onUpdateTask: (CompletedItem, CreateTaskPayload) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    // Which of the history's two tabs is on screen. The initial value is the route's own
    // argument — the board the user came through — and after that it is the user's. Keyed
    // on `initialScope` rather than seeded once, so an arrival on the other board's tile
    // opens where that tile says.
    var scope by rememberSaveable(initialScope) { mutableStateOf(initialScope) }
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
    // The active tab's own rows, and nothing else's: this is the whole of the split. Each
    // list already comes from its own half of the cache, so no filter on `isFloater` is
    // written here — a partition of a merged list would be a second definition of what a
    // floater is, and the tab is the only thing that decides which list is drawn.
    val scopeItems = uiState.itemsFor(scope)
    val visibleItems = remember(scopeItems, scope, searchActive, normalizedSearchQuery) {
        if (!searchActive) {
            scopeItems
        } else {
            // The same two fields the web completed page and the list-detail
            // screens match on — the title and the notes flattened out of their
            // rich-text form — plus the list name, which only web's FLOATER tab
            // matches (`CompletedFloaterContainer.tsx`); its scheduled twin has
            // no such term.
            scopeItems.filter { completed ->
                completed.title.lowercase(Locale.getDefault())
                    .contains(normalizedSearchQuery) ||
                        flattenNotesToPlainText(completed.description)
                            .lowercase(Locale.getDefault())
                            .contains(normalizedSearchQuery) ||
                        (
                            scope == CompletedScope.Floater &&
                                completed.listName.orEmpty()
                                    .lowercase(Locale.getDefault())
                                    .contains(normalizedSearchQuery)
                            )
            }
        }
    }
    // The tab scopes every key the list below is built out of. Without it, two tabs sharing
    // a day would share a section key, and a month collapsed on one tab would close the
    // other tab's header the moment the user switched.
    val timelineSections = remember(visibleItems, scope) {
        buildCompletedTimelineSections(visibleItems, scope = scope)
    }
    // Two readings of one rule, because the two lists below are different
    // questions: the scene answers for what is VISIBLE (a live query narrows it),
    // the placeholder answers for the store itself (a query is answered locally
    // and must never flash a placeholder per keystroke). Neither reads
    // `isLoading` any more -- see [feedAnswer] for why that flag meant the
    // opposite of what every gate like this was using it for.
    //
    // Both read the ACTIVE TAB, which is the whole of what the split changes here: a
    // Floater tab with nothing in it may not answer with the scheduled history's
    // emptiness, and the placeholder may not be held up by the other tab's store.
    val completedAnswer = feedAnswer(
        storeRead = uiState.hasHydratedSnapshot,
        rowsEmpty = visibleItems.isEmpty(),
        firstAnswerLanded = uiState.firstAnswerLanded,
    )
    val completedStoreAnswer = feedAnswer(
        storeRead = uiState.hasHydratedSnapshot,
        rowsEmpty = scopeItems.isEmpty(),
        firstAnswerLanded = uiState.firstAnswerLanded,
    )
    val showEmptyState = completedAnswer == FeedAnswer.Empty
    val heroCollapse = rememberLazyListHeroTitleCollapse(listState = listState)
    val completedTitle = stringResource(R.string.completed_title)
    // Each tab's own accent, which is what web gives them: `nativeScreenAccentColors`
    // pairs the completed history's green (#719F84) with the Floater board's teal
    // (#4D8F83), and `CompletedFloaterContainer` hands the teal to its own header,
    // watermark and empty state. Android already carries both — `TdayCompletedTileAccent`
    // and `TdayFloaterAccent`, the same two values — so the pair costs nothing here.
    //
    // It tints the mark and everything the mark is drawn in — the hero's front glyph, its
    // echo, the page watermark, the empty state's badge — and the tab strip. Those are the
    // four sites web's one accent reaches on this page, and before this it reached only the
    // first two on the natives, so the page drew its own mark in two colours at once (a
    // green or teal hero over a slate watermark and a slate badge).
    //
    // The disc's wash, the hero title and the toolbar keep [COMPLETED_TITLE_COLOR], the
    // page's own slate chrome. The wash is the one of those that touches the mark, and it
    // is left alone deliberately: on the natives it sits directly above a slate title,
    // and re-tinting it alone would put a green disc over a slate title where web has the
    // two the same colour. Re-colouring the chrome is a change to a shape this did not
    // come to make.
    val activeScopeAccent = when (scope) {
        CompletedScope.Tasks -> TdayCompletedTileAccent
        CompletedScope.Floater -> TdayFloaterAccent
    }
    // The tab switch scrolls the new tab to its own top — web's `scrollCompletedToTop()`.
    val scrollScope = rememberCoroutineScope()
    // The hero disc's echo, and nothing else — the mark's front glyph is the
    // composite below, which the echo is deliberately not part of. The disc
    // clips the echo, and the clip runs through the middle of the glyph: a
    // rectilinear calendar under that arc is cut into bars rather than arcs,
    // while the check's round-capped tail merely bleeds, so the echo is the
    // check alone.
    val completedEchoIcon = ImageVector.vectorResource(R.drawable.ic_lucide_check)
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
        // The edit sheet's target is looked up across BOTH tabs: the sheet outlives a tab
        // switch only in principle, but a lookup scoped to the visible list would silently
        // drop an edit the user opened, and neither list is expensive to search.
        current = remember(editTargetId, uiState.todoItems, uiState.floaterItems) {
            editTargetId?.let { targetId ->
                uiState.todoItems.firstOrNull { it.id == targetId }
                    ?: uiState.floaterItems.firstOrNull { it.id == targetId }
            }
        },
    )
    // The open row's task leaving the feed hands the slot back. Read through
    // `snapshotFlow` rather than as an effect key so that no read of the slot
    // happens in this composable's body -- see [TaskSwipeSlot].
    LaunchedEffect(uiState.todoItems, uiState.floaterItems, swipeSlot) {
        snapshotFlow { swipeSlot.openId }.collect { openId ->
            if (openId != null && uiState.itemsFor(scope).none { it.id == openId }) {
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
                        // The echo's glyph only — see `completedEchoIcon`, and
                        // `frontMark` for the mark itself.
                        icon = completedEchoIcon,
                        accentColor = COMPLETED_TITLE_COLOR,
                        titleColor = COMPLETED_TITLE_COLOR,
                        collapseProgress = heroCollapse.progress,
                        frontMark = {
                            CompletedMark(
                                size = TdayHeroTitleMetrics.MarkGlyph,
                                tint = activeScopeAccent,
                            )
                        },
                        // The echo is a drawing of the mark, so it takes the
                        // mark's colour rather than the disc's slate chrome:
                        // left on `accentColor` the disc drew the same check
                        // twice in two colours, where the web draws both from its
                        // one accent.
                        echoColor = activeScopeAccent,
                    )
                    // The two tabs, directly under the title and inside the block that
                    // scrolls away — the slot web hands this control through
                    // `NativePageHeader`'s `beneathTitle`, and the position the calendar
                    // already puts its own segmented strip in on this client.
                    item(key = "completed-tab-strip", contentType = "completed-tab-strip") {
                        CompletedScopeTabs(
                            scope = scope,
                            todoCount = uiState.todoItems.size,
                            floaterCount = uiState.floaterItems.size,
                            accentColor = activeScopeAccent,
                            onScopeSelected = { next ->
                                if (next == scope) return@CompletedScopeTabs
                                // The selection haptic is the slider's own, fired on a tap
                                // that is not the selected option — the same single tick
                                // web's `hapticTick()` makes in its own handler.
                                //
                                // The history is a different list on each tab, so the new
                                // one opens at its own top rather than at the other tab's
                                // scroll offset — web's `scrollCompletedToTop()`. The
                                // query goes with it, for web's reason too: its two tabs
                                // are two independent screens, so the query belongs to the
                                // screen rather than to the page, and a fresh one starts
                                // empty.
                                scope = next
                                searchQuery = ""
                                searchNeedsFocus = false
                                scrollScope.launch { listState.scrollToItem(0) }
                            },
                        )
                    }
                    timelineSections.forEachIndexed { sectionIndex, section ->
                        // A live query outranks a shut month: history opens with
                        // older months collapsed, and a task the search turns up
                        // inside one must not stay hidden behind its header. Both
                        // the timeline screens and web make the same call.
                        val isCollapsed = !searchActive &&
                                collapsedSectionKeys.contains(section.key)
                        item(key = "completed-header-${section.key}") {
                            // Read through the gate, like the error card below and the
                            // empty scene's exit above: the tab is part of this section's
                            // key, so a switch replaces every item in one frame, and an
                            // ungated `animateItem` here would slide the arriving tab's
                            // headers into place while the thumb snapped — a switch that
                            // is half a cut. Reduce Motion is a clean cut at both ends.
                            val headerMotionEnabled = rememberTdayMotionEnabled()
                            // Placement and nothing else: a month header is never added
                            // or removed by a check-off, only displaced by one. That is
                            // [TdayFeedItemMotion]'s rule 1, which this site was already
                            // obeying by hand at the same 320.
                            val headerMotionModifier = if (headerMotionEnabled) {
                                Modifier.animateItem(
                                    fadeInSpec = null,
                                    placementSpec = TdayFeedItemMotion.Placement,
                                    fadeOutSpec = null,
                                )
                            } else {
                                Modifier
                            }
                            CompletedTimelineSectionHeader(
                                modifier = headerMotionModifier
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
                                    // Same gate as the header above, and for the same
                                    // reason: a tab switch re-keys every row, and the
                                    // three specs this row runs for a check-off are motion
                                    // the app's Reduce Motion switch owns. With the gate on,
                                    // the switch is a placement-only, no-fade replacement.
                                    val rowMotionEnabled = rememberTdayMotionEnabled()
                                    val rowMotionModifier = if (rowMotionEnabled) {
                                        Modifier.animateItem(
                                            fadeInSpec = TdayFeedItemMotion.FadeIn,
                                            placementSpec = TdayFeedItemMotion.Placement,
                                            fadeOutSpec = TdayFeedItemMotion.FadeOut,
                                        )
                                    } else {
                                        Modifier
                                    }
                                    CompletedSwipeRow(
                                        modifier = rowMotionModifier
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
                        // Keyed by tab as well as by kind: the two tabs' scenes carry
                        // different copy, and a lazy item that keeps its identity across a
                        // switch would hand the new tab the old one's title for a frame.
                        item(
                            key = "completed-empty-${scope.wire}",
                            contentType = "completed-empty",
                        ) {
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
                                    // The fallback for a badge drawn as an
                                    // asset; `markContent` is what actually
                                    // draws here. Kept in step with it so the
                                    // two paths cannot silently diverge.
                                    icon = R.drawable.ic_lucide_calendar_check,
                                    // The tab's accent, not the page's slate —
                                    // the mark's other two sites on this page
                                    // (the hero and the watermark) are the tab's
                                    // colour, and web draws all three of its own
                                    // from the one accent. A slate disc under a
                                    // tab-coloured mark was this page drawing its
                                    // mark in two colours at once.
                                    accentColor = activeScopeAccent,
                                    // Two scenes, not one with a swapped word: web keeps a
                                    // Floater empty state of its own
                                    // (`completed.floaterEmpty` / `floaterEmptyBody`) beside
                                    // the scheduled one, because "Tick something off and it
                                    // will land here" is only half true on a tab where the
                                    // way in is the Floater board rather than the schedule.
                                    title = stringResource(
                                        when (scope) {
                                            CompletedScope.Tasks -> R.string.completed_empty
                                            CompletedScope.Floater -> R.string.completed_floater_empty
                                        },
                                    ),
                                    description = stringResource(
                                        when (scope) {
                                            CompletedScope.Tasks -> R.string.completed_empty_body
                                            CompletedScope.Floater -> R.string.completed_floater_empty_body
                                        },
                                    ),
                                    modifier = Modifier.padding(vertical = TdayDimens.Spacing3xl),
                                    markContent = {
                                        CompletedMark(
                                            size = COMPLETED_MARK_BADGE_SIZE,
                                            rearAlpha = COMPLETED_MARK_BADGE_REAR_ALPHA,
                                        )
                                    },
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
                    // The fallback for a watermark drawn as an asset;
                    // `markContent` is what actually draws here.
                    iconRes = R.drawable.ic_lucide_calendar_check,
                    // The tab's accent, for the badge's reason above: the
                    // watermark is the same mark at another size, and it was the
                    // one site still wearing the page's slate while the hero and
                    // the badge wore the tab's colour.
                    accentColor = activeScopeAccent,
                    markContent = {
                        CompletedMark(
                            size = WatermarkGlyphSize,
                            rearAlpha = COMPLETED_MARK_WATERMARK_REAR_ALPHA,
                        )
                    },
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
                } else if (scopeItems.isNotEmpty()) {
                    // No magnifier over an empty history: there is no set for a
                    // query to narrow, and the button would only raise a keyboard
                    // over the empty-state scene, which is the whole of what the
                    // screen has to say. Read against the ACTIVE TAB, so an empty
                    // Floater tab gets no magnifier even while the scheduled
                    // history behind it is full — the same per-tab gate web's two
                    // containers each write for themselves.
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
    scope: CompletedScope,
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
                // The tab is part of the key, and it is load-bearing: the two tabs draw
                // into one LazyColumn, and a day that both histories have rows for would
                // otherwise be one section key twice — a duplicate key in a lazy layout,
                // and a month collapsed on one tab closing the other tab's header.
                key = "completed-${scope.wire}-$date",
                title = date.format(COMPLETED_SECTION_FORMATTER),
                items = sectionItems,
            )
        }
}

/**
 * The completion history's two tabs.
 *
 * The control is [TdaySegmentedSlider] — the one this app draws on the calendar and twice
 * on Settings — rather than a second segmented control written for this screen, because
 * web's own argument for its Completed strip is that it is "the same segmented control
 * `SettingsPage` draws twice". Its rung here is [TdaySegmentedSliderMotion.Enter], which is
 * the rung web picked for this control and argues for at the call site.
 *
 * Both counts are the FULL length of their own history and never the search-narrowed one,
 * which is web's rule too (`CompletedContainer.tsx` reads `completedTodos.length`, not the
 * filtered list): a tab's badge says how much history it holds, not how much of it the
 * current query happens to match.
 */
@Composable
private fun CompletedScopeTabs(
    scope: CompletedScope,
    todoCount: Int,
    floaterCount: Int,
    accentColor: Color,
    onScopeSelected: (CompletedScope) -> Unit,
) {
    val scheduledLabel = stringResource(R.string.root_feed_tab_scheduled_task_home)
    val floaterLabel = stringResource(R.string.root_feed_tab_floater)
    TdaySegmentedSlider(
        options = CompletedScope.entries,
        selectedOption = scope,
        onOptionSelected = onScopeSelected,
        modifier = Modifier.padding(
            top = CompletedScopeTabsTopSpacing,
            bottom = CompletedScopeTabsBottomSpacing,
        ),
        accentColor = accentColor,
        // The dock's own two labels, reused rather than respelled: the tab mirrors the
        // board it mirrors, so it should not be able to drift from it.
        label = { option ->
            when (option) {
                CompletedScope.Tasks -> scheduledLabel
                CompletedScope.Floater -> floaterLabel
            }
        },
        badge = { option ->
            when (option) {
                CompletedScope.Tasks -> todoCount.toString()
                CompletedScope.Floater -> floaterCount.toString()
            }
        },
        selectorAnimationSpec = TdaySegmentedSliderMotion.Enter,
    )
}

private val COMPLETED_TITLE_COLOR = TdayCompletedTitleAccent

/**
 * The Completion-history page's mark: one green check, with the Floater's leaf
 * and the Scheduled board's `calendar-check` stacked behind it as a single faint
 * plate. The two behind read as depth under the check rather than as two more
 * icons, which is the arrangement the page was asked for.
 *
 * A drawing and not an asset, because there is no compositing primitive to reach
 * for: three `Icon`s in one `Box` is the whole thing. But there is no compositing
 * primitive to reach for, so it is built once — here — and handed to all three of
 * the page's own mark sites (the hero disc, the page watermark, the empty state's
 * badge) through the mark slot each shared component carries. A composite that
 * reached only the hero would leave the page drawing two different marks.
 *
 * The three glyphs are concentric but NOT the same size — [COMPLETED_MARK_LEAF_SCALE]
 * and [COMPLETED_MARK_CALENDAR_SCALE] say why, and what it costs at the one pair
 * of contours no pair of scales can separate.
 *
 * The check takes [TdayCompletedTileAccent] — the same green the Scheduled and
 * Floater boards' Completed tile is, and the tile the user arrives through. The
 * page's own chrome (its title, its toolbar) wears
 * [TdayCompletedTitleAccent], a slate; the mark is green on purpose, because a
 * check is what the page is *about* rather than a piece of its chrome. The two
 * behind it are that one green at [rearAlpha] and never a second colour.
 *
 * @param size the box all three glyphs are drawn in.
 * @param tint the check's colour; each glyph behind it is the same colour at
 *   [rearAlpha]. `Color.Unspecified` means "the colour my host is standing in",
 *   which is how a host whose own tint is computed somewhere the caller cannot
 *   see — the page watermark's blend, the empty state's white — says what colour
 *   to draw in without a call site re-deriving it. It is resolved to
 *   `LocalContentColor` here, in [CompletedMark], and never handed on as
 *   unspecified: Material3's `Icon(painter, …)` reads `Color.Unspecified` as
 *   *no colour filter*, not as "inherit", and these three vendored drawables are
 *   white — so an unspecified tint draws white, at whatever alpha the host asked
 *   for, which on a light background is nothing at all.
 * @param rearAlpha the pair behind the check, as a fraction of the front one, so
 *   it survives an opacity the host puts on the whole mark.
 */
@Composable
private fun CompletedMark(
    size: Dp,
    tint: Color = Color.Unspecified,
    rearAlpha: Float = COMPLETED_MARK_REAR_ALPHA,
) {
    val resolvedTint = tint.takeOrElse { LocalContentColor.current }
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        CompletedMarkLayer(
            iconRes = R.drawable.ic_lucide_calendar_check,
            size = size,
            scale = COMPLETED_MARK_CALENDAR_SCALE,
            tint = resolvedTint,
            alpha = rearAlpha,
        )
        CompletedMarkLayer(
            iconRes = R.drawable.ic_lucide_leaf,
            size = size,
            scale = COMPLETED_MARK_LEAF_SCALE,
            offsetX = COMPLETED_MARK_LEAF_OFFSET_X,
            offsetY = COMPLETED_MARK_LEAF_OFFSET_Y,
            tint = resolvedTint,
            alpha = rearAlpha,
        )
        CompletedMarkLayer(
            iconRes = R.drawable.ic_lucide_check,
            size = size,
            scale = 1f,
            tint = resolvedTint,
            alpha = 1f,
        )
    }
}

@Composable
private fun CompletedMarkLayer(
    @DrawableRes iconRes: Int,
    size: Dp,
    scale: Float,
    tint: Color,
    alpha: Float,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(size * scale)
            // Displacement from the box's centre as a fraction of the box, so the
            // drawing is the same proportion at every size the mark is drawn.
            .offset(x = size * offsetX, y = size * offsetY)
            .graphicsLayer { this.alpha = alpha },
    )
}

/**
 * The pair behind the check on the hero mark, matching
 * `TdayHeroTitleMetrics`'s own echo alpha — the back plate and the bleed out of
 * the disc's bottom-right are one depth plane, not two.
 */
private const val COMPLETED_MARK_REAR_ALPHA = 0.17f

/**
 * The same pair on the page watermark. Stronger, because nothing in that drawing
 * is strong: the whole mark sits under the watermark's own 0.10 fade, and at the
 * hero's ratio the back plate would not survive it — the watermark would show the
 * check alone, and the page would be drawing two different marks.
 */
private const val COMPLETED_MARK_WATERMARK_REAR_ALPHA = 0.45f

/**
 * The same pair on the empty state's badge, which is a white glyph on the accent
 * disc and is drawn at 32dp rather than the hero's 44 — a 0.17 ghost goes missing
 * at that size and contrast.
 */
private const val COMPLETED_MARK_BADGE_REAR_ALPHA = 0.25f

/**
 * The badge's glyph box. The badge's circle is 52dp and every other screen draws
 * its single glyph at 24dp inside it; three glyphs stacked need the room, and at
 * 24 the calendar's inner tick lands at ~2pt where the three cannot be told
 * apart. Raised here rather than in `TdayEmptyState`, so the eight other screens
 * that draw a badge keep the drawing they have.
 */
private val COMPLETED_MARK_BADGE_SIZE = 32.dp

/**
 * How much of [CompletedMark]'s box each glyph behind the check is drawn in, and
 * where the leaf sits inside it.
 *
 * Two things had to be true of the back plate at once: the two rear glyphs have to
 * read as two rather than fuse into one fringe, and each has to be nameable at the
 * size the mark is actually drawn. Drawn concentric at one size the three fused;
 * graduated by scale alone — leaf 0.62, calendar 0.88, the first arrangement — the
 * leaf still did not name, because at 0.62 its contour runs through the calendar's
 * header rule and *within* both frame walls, so the calendar's own straight lines
 * cut its silhouette at every crossing. Rasterised, that leaf kept 59.3% of its
 * ink, in six disconnected pieces: the "scratch" the mark was reported as, and the
 * one glyph of the three that was present, paid for and not nameable.
 *
 * So the leaf is drawn small enough to sit *inside* the calendar's body — under
 * the header rule, above the frame's foot, and inside both walls — and shifted
 * right, out from under the front check's own lower arm. At 0.335 of the box its
 * outline clears the calendar's frame by 0.88 of a unit on every side, against the
 * 0.84 the first arrangement recorded: 1.61pt of the hero's 44dp, 1.17 at the
 * badge's 32, 7.77dp at [WatermarkGlyphSize]. Rasterised, the same leaf now keeps
 * 88.6% of its ink, in a single piece.
 *
 * The one contour it cannot avoid is the calendar's own inner tick, which sits in
 * the middle of the body the leaf now occupies: the leaf is drawn *over* it, so
 * the tick is covered rather than cut. That tick was already unreadable behind the
 * front check — its arms pass within the strokes' half-widths of the check's arms
 * at every pair of scales these two glyphs allow — so nothing legible is lost, and
 * the leaf's silhouette survives whole.
 *
 * The binding pair is now the leaf's topmost point against the header rule and its
 * foot against the frame's, both 0.88 of a unit. A scaled glyph scales its stroke
 * with it, so the leaf carries 0.67 of a unit of stroke against the calendar's
 * 1.76: the pair behind reads as *behind* partly by being drawn in a finer line
 * than the check's 2.
 */
private const val COMPLETED_MARK_LEAF_SCALE = 0.335f
private const val COMPLETED_MARK_CALENDAR_SCALE = 0.88f

/**
 * Where the leaf sits inside [CompletedMark]'s box, as a fraction of it — lucide
 * draws in a 24-unit box, so these are 2.5 and 3.69 of those units. Down and to
 * the right: down is what puts the leaf under the calendar's header rule, and
 * right is what takes it out from under the front check's lower arm. The
 * rightward half is worth a third of the leaf's ink — at the box's centre, at this
 * scale, the same leaf keeps 60.1% where it keeps 88.6% here.
 */
private const val COMPLETED_MARK_LEAF_OFFSET_X = 2.5f / 24f
private const val COMPLETED_MARK_LEAF_OFFSET_Y = 3.69f / 24f

private val COMPLETED_SECTION_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
private val COMPLETED_ROW_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
