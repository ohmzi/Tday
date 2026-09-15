package com.ohmz.tday.compose.feature.todos

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.RestingFloatersPreferenceStore
import com.ohmz.tday.compose.core.data.list.ShareListKind
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TaskRescheduleScope
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.core.model.TodoTitleNlpResponse
import com.ohmz.tday.compose.core.model.capitalizeFirstListLetter
import com.ohmz.tday.compose.core.model.supportsTaskReschedule
import com.ohmz.tday.compose.core.model.timelineRescheduleTargetDate
import com.ohmz.tday.compose.core.sound.rememberTaskCompletionSound
import com.ohmz.tday.compose.core.text.flattenNotesToPlainText
import com.ohmz.tday.compose.core.ui.CategoryCard
import com.ohmz.tday.compose.core.ui.EmptyTaskWatermark
import com.ohmz.tday.compose.core.ui.LazyListHeroTitleSettle
import com.ohmz.tday.compose.core.ui.LocalSnackbarManager
import com.ohmz.tday.compose.core.ui.RootFeedHeroHeader
import com.ohmz.tday.compose.core.ui.RootFeedHeroHeaderMetrics
import com.ohmz.tday.compose.core.ui.RootFeedHeroMark
import com.ohmz.tday.compose.core.ui.TaskSwipeActionButton
import com.ohmz.tday.compose.core.ui.TaskSwipeSlot
import com.ohmz.tday.compose.core.ui.TaskSwipeSlotBackHandler
import com.ohmz.tday.compose.core.ui.TdayDragLift
import com.ohmz.tday.compose.core.ui.TdayEmptyState
import com.ohmz.tday.compose.core.ui.TdayFeedItemMotion
import com.ohmz.tday.compose.core.ui.TdayHaptics
import com.ohmz.tday.compose.core.ui.TdayHeroToolbar
import com.ohmz.tday.compose.core.ui.TdayMotionTokens
import com.ohmz.tday.compose.core.ui.TdayPress
import com.ohmz.tday.compose.core.ui.TdaySearchCapsule
import com.ohmz.tday.compose.core.ui.TdayTaskRowMetrics
import com.ohmz.tday.compose.core.ui.TdayTaskRowSkeleton
import com.ohmz.tday.compose.core.ui.TdayTaskRowSkeletonGroup
import com.ohmz.tday.compose.core.ui.animateTaskSwipeOffsetAsState
import com.ohmz.tday.compose.core.ui.rememberLazyListHeroTitleCollapse
import com.ohmz.tday.compose.core.ui.rememberSystemMotionScale
import com.ohmz.tday.compose.core.ui.rememberTaskStrikeProgress
import com.ohmz.tday.compose.core.ui.rememberTaskSwipeRevealState
import com.ohmz.tday.compose.core.ui.rememberTdayMotionEnabled
import com.ohmz.tday.compose.core.ui.rememberTdayMotionScale
import com.ohmz.tday.compose.core.ui.rememberTdayTaskRowSkeletonMounted
import com.ohmz.tday.compose.core.ui.scaledDelay
import com.ohmz.tday.compose.core.ui.shareList
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
import com.ohmz.tday.compose.ui.component.RootFeedDock
import com.ohmz.tday.compose.ui.component.RootFeedDockCollapse
import com.ohmz.tday.compose.ui.component.RootFeedTab
import com.ohmz.tday.compose.ui.component.TdayCenteredSelectorDialog
import com.ohmz.tday.compose.ui.component.TdayModalBottomSheet
import com.ohmz.tday.compose.ui.component.TdayPullToRefreshBox
import com.ohmz.tday.compose.ui.component.TdaySheetCard
import com.ohmz.tday.compose.ui.component.TdaySheetDefaults
import com.ohmz.tday.compose.ui.component.TdaySheetFullBleedWindow
import com.ohmz.tday.compose.ui.component.TdaySheetHeader
import com.ohmz.tday.compose.ui.component.TdaySheetSectionTitle
import com.ohmz.tday.compose.ui.component.ThemedDatePickerDialog
import com.ohmz.tday.compose.ui.priority.PRIORITY_OPTIONS_LOW_TO_HIGH
import com.ohmz.tday.compose.ui.priority.canonicalPriorityValue
import com.ohmz.tday.compose.ui.priority.isImportantPriority
import com.ohmz.tday.compose.ui.priority.isLowestPriority
import com.ohmz.tday.compose.ui.priority.isUrgentPriority
import com.ohmz.tday.compose.ui.priority.priorityDisplayLabelRes
import com.ohmz.tday.compose.ui.theme.TDAY_DEFAULT_LIST_COLOR_KEY
import com.ohmz.tday.compose.ui.theme.TDAY_DEFAULT_LIST_ICON_KEY
import com.ohmz.tday.compose.ui.theme.TdayCompletedTileAccent
import com.ohmz.tday.compose.ui.theme.TdayDimens
import com.ohmz.tday.compose.ui.theme.TdayFloaterAccent
import com.ohmz.tday.compose.ui.theme.TdayListColorOptions
import com.ohmz.tday.compose.ui.theme.TdayListIconOptions
import com.ohmz.tday.compose.ui.theme.TdaySwipeCopyBackground
import com.ohmz.tday.compose.ui.theme.TdaySwipeDeleteBackground
import com.ohmz.tday.compose.ui.theme.TdaySwipeEditBackground
import com.ohmz.tday.compose.ui.theme.TdaySwipeFloatBackground
import com.ohmz.tday.compose.ui.theme.TdaySwipeScheduleBackground
import com.ohmz.tday.compose.ui.theme.TdayTaskCompleteAccent
import com.ohmz.tday.compose.ui.theme.TdayTitleIconDayAccent
import com.ohmz.tday.compose.ui.theme.TdayTitleIconNightAccent
import com.ohmz.tday.compose.ui.theme.TdayTodoModeAllAccent
import com.ohmz.tday.compose.ui.theme.TdayTodoModeOverdueAccent
import com.ohmz.tday.compose.ui.theme.TdayTodoModePriorityAccent
import com.ohmz.tday.compose.ui.theme.TdayTodoModeScheduledAccent
import com.ohmz.tday.compose.ui.theme.TdayTodoModeTodayAccent
import com.ohmz.tday.compose.ui.theme.isTdayListIconKeySupported
import com.ohmz.tday.compose.ui.theme.normalizeTdayListColorKey
import com.ohmz.tday.compose.ui.theme.tdayListAccentColor
import com.ohmz.tday.compose.ui.theme.tdayListIconForKey
import com.ohmz.tday.compose.ui.theme.tdayListIconResForKey
import com.ohmz.tday.compose.ui.theme.tdayPriorityColor
import com.ohmz.tday.shared.bulk.BulkAction
import com.ohmz.tday.shared.bulk.BulkSelectionPolicy
import com.ohmz.tday.shared.floater.FloaterResting
import com.ohmz.tday.shared.floater.FloaterRestingTier
import com.ohmz.tday.shared.sort.TaskSortEngine
import com.ohmz.tday.shared.sort.TaskSortKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.lerp as lerpColor

private val TimelineSameDateTaskSpacing = 2.dp
private val TimelineDateGroupSpacing = 6.dp
private val TimelineSectionTopSpacing = 6.dp
private val TimelineHeaderBodySpacing = 2.dp
private val TimelineCollapsedSectionSpacing = 4.dp

// What this screen draws that the scale has no rung for. Named here rather than snapped
// onto a neighbouring step, because the differences are what they say: 21 dp against
// 23 dp is the ripple of a colour swatch against the ripple of an icon one, and a rung
// minted for one call site is a rung nobody can reason about.

/** How flat a pressed card presses, against the row's own resting elevation below. */
private val PressedCardElevation = 2.dp

/** The target a finger gets where the control drawn inside it is smaller than a finger. */
private val MinTouchTargetSize = 48.dp

/** List mode insets 16 where the Today and root-feed styles inset
 *  `ContentPaddingHorizontal`'s 18; pulling it across would move a page margin. */
private val ListModeContentHorizontalPadding = 16.dp

/** The gap under every card in the floater feed — search results, tile, list rows. */
private val FloaterFeedRowSpacing = 10.dp

// The header's circular buttons, and the bar that replaces the FAB while selecting.
private val HeaderButtonIconSize = 22.dp
private val BulkSelectionBarElevation = 14.dp
private val BulkSelectionBarVerticalPadding = 10.dp
private val BulkSelectionCountHorizontalPadding = 10.dp
private val BulkSelectionActionIconSize = 22.dp

// The full-screen confirmation dialog: its scrim's inset, the card floating in it, and
// the gap between Cancel and the button that destroys.
private val OverlayDialogScrimInset = 34.dp
private val OverlayDialogMaxWidth = 420.dp
private val OverlayDialogElevation = 18.dp
private val OverlayDialogBottomPadding = 20.dp
private val OverlayDialogSectionSpacing = 22.dp
private val OverlayDialogButtonGap = 10.dp

// The search overlay: a card hung under the field, and the result rows inside it.
private val SearchResultsOverlayElevation = 8.dp
private val SearchResultsMaxHeight = 320.dp
private val SearchResultRowVerticalPadding = 9.dp
private val SearchResultRowSpacing = 10.dp
private val SearchResultIconSize = 17.dp

// A list row in the floater feed, and the oversized glyph hung off its right edge and
// half out of frame.
private val ListRowHeight = 70.dp
private val ListRowElevation = 8.dp
private val ListRowIconSize = 24.dp
private val FeedCardHorizontalPadding = 16.dp
private val ListRowWatermarkOffsetX = 14.dp
private val ListRowWatermarkOffsetY = 8.dp
private val ListRowWatermarkSize = 82.dp

// The summary sheet's spinner. This app's progress indicators run 18 to 32 dp with no
// agreement between them, so this one says what it is instead of claiming an icon size.
private val SummarySpinnerSize = 20.dp
private val SummarySpinnerStroke = 2.dp

// The list-settings sheet: the icon preview, then the colour and icon pickers under it.
private val ListCardSpacing = 16.dp
private val ListIconPreviewSize = 86.dp
private val ListIconPreviewGlyphSize = 42.dp
private val ListColorSwatchSize = 42.dp
private val ListColorSwatchRippleRadius = 21.dp
private val ListIconSwatchSize = 46.dp
private val ListIconSwatchRippleRadius = 23.dp
private val ListIconOptionSpacing = 10.dp

/** The colour swatch rings heavier because the ring is all it has: a selected icon
 *  option also tints its fill and its glyph. */
private val ListColorSwatchSelectedOutline = 3.dp
private val ListIconSwatchSelectedOutline = 2.dp

// That sheet's Sharing row: two tiles side by side, then one tile's icon and label, then
// the Delete button under both, which is wider inside than they are.
private val ListSettingsActionTileSpacing = 10.dp
private val ListSettingsActionContentSpacing = 10.dp
private val ListSettingsDeleteHorizontalPadding = 16.dp

// A timeline section header and the placeholder a dragged task opens under it. Each
// draws at two heights and the pairs are the point — the minimal one is what Today
// wears — so neither half is rounded onto the other.
private val TimelineSectionHeaderMinHeight = 44.dp
private val TimelineSectionHeaderMinHeightMinimal = 32.dp
private val TimelineSectionChevronSize = 18.dp
private val TimelineDropPlaceholderActiveHeight = 72.dp
private val TimelineDropPlaceholderActiveHeightMinimal = 66.dp
private val TimelineDropPlaceholderHeight = 52.dp
private val TimelineDropPlaceholderHeightMinimal = 46.dp

/** A header can collapse to nothing but still has to catch a task dropped on it, so it
 *  keeps a hairline of height for the drop target to live in. */
private val TimelineSectionHeaderDropTargetMinHeight = 1.dp

// The drag preview rides under the finger, not beside it: the pointer is offset into the
// card so the task being carried is the thing the hand is over.
private val TimelineDragPreviewAnchorX = 130.dp
private val TimelineDragPreviewAnchorY = 34.dp
private val TimelineDragPreviewMinWidth = 220.dp
private val TimelineDragPreviewMaxWidth = 280.dp
private val TimelineDragPreviewContentSpacing = 10.dp
private val TimelineDragPreviewIconSize = 22.dp

// A task row: the swipe pills behind it, the rise it leaves on, and the badges it
// carries — in the preview above as well as in the row itself.
private val SwipeRevealWidth = 256.dp
private val SwipeRevealWidthWithExtraAction = 336.dp
private val SwipeActionSpacing = 16.dp
private val TaskCompletionRiseOffsetY = (-10).dp
private val TaskRowTitleStartPadding = 10.dp
private val RowTrailingIconSize = 18.dp
private val CompletionToggleRippleRadius = 24.dp

private fun timelineTaskBottomSpacing(
    itemIndex: Int,
    lastIndex: Int,
    showDateDivider: Boolean,
): Dp {
    return if (showDateDivider || itemIndex == lastIndex) {
        TimelineDateGroupSpacing
    } else {
        TimelineSameDateTaskSpacing
    }
}

/**
 * [TdayFeedItemMotion] on one item of this screen's feed.
 *
 * Every item that a completion can move goes through here rather than declaring
 * its own specs, so the row that leaves, the empty scene that takes its slot and
 * the tiles that scene pushes down are all on one clock. A caller passes `null`
 * for a fade it owns itself, or has no business running.
 *
 * [placementSpec] defaults to [TdayFeedItemMotion.Placement] — the shared clock
 * every ordinary displacement (a row added or removed elsewhere) travels on —
 * and stays nullable with no caller currently taking that door. The one that
 * did was [EARLIER_SECTION_KEY]'s header, which dropped placement while the
 * empty-state scene resized itself directly above it; the scene is emitted
 * below Earlier's rows now, so the header has no moving target to chase and
 * takes the shared clock like every other header. The parameter is kept, rather
 * than narrowed to non-null, because the hazard that justified it is a property
 * of the feed and not of that one header: see [TdayFeedItemMotion]'s first rule
 * for the shape to watch for — an item placed directly after an
 * `AnimatedVisibility` running `expandVertically`/`shrinkVertically`, whose
 * bounds move every frame for longer than [TdayFeedItemMotion.PlacementMillis]
 * takes to chase them. Reorder first; reach for `null` only when the resizing
 * neighbour genuinely cannot be moved out from above.
 */
private fun LazyItemScope.feedItemMotion(
    enabled: Boolean,
    fadeInSpec: FiniteAnimationSpec<Float>? = TdayFeedItemMotion.FadeIn,
    fadeOutSpec: FiniteAnimationSpec<Float>? = TdayFeedItemMotion.FadeOut,
    placementSpec: FiniteAnimationSpec<IntOffset>? = TdayFeedItemMotion.Placement,
): Modifier = if (enabled) {
    Modifier.animateItem(
        fadeInSpec = fadeInSpec,
        placementSpec = placementSpec,
        fadeOutSpec = fadeOutSpec,
    )
} else {
    Modifier
}

/**
 * [feedItemMotion] for an item a completion *moves* but never adds or removes.
 *
 * Placement only, deliberately. These items are already on screen before the
 * last row is ticked and still on screen after, so a fade has nothing to
 * describe — and it would fire on a path this fix has no business touching:
 * a live search query replaces the whole feed body in one go, and an item with
 * a fade spec fades out and back in on every open and close of the field.
 *
 * [placementSpec] defaults to the shared [TdayFeedItemMotion.Placement] clock,
 * same as [feedItemMotion] — and, same as there, nothing overrides it to
 * `null` any more. See that default's own doc for what would justify doing so
 * again.
 */
private fun LazyItemScope.displacedFeedItemMotion(
    enabled: Boolean,
    placementSpec: FiniteAnimationSpec<IntOffset>? = TdayFeedItemMotion.Placement,
): Modifier =
    feedItemMotion(
        enabled = enabled,
        fadeInSpec = null,
        fadeOutSpec = null,
        placementSpec = placementSpec,
    )

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
/**
 * How long after a tick the empty state still counts as "you just finished it".
 * Wider than the confetti's own flight, so a recomposition mid-burst cannot cut
 * the paper off in mid-air.
 *
 * The inline empty state holds its celebration back by
 * [TdayFeedItemMotion.CelebrationStartDelayMillis] first, so what has to fit
 * inside this window is that hold plus the flight — 2.3s of the 4s, today. Widen
 * the hold a long way and this has to follow it.
 */
private const val CompletionCelebrationWindowMs = 4_000L

/**
 * The pure boolean [TodoListScreen] wires into `celebrate`, pulled out of the
 * composable so requirement 1 (confetti on completing the last pending-today
 * task) has a real unit test rather than only a device/emulator check.
 *
 * Deliberately takes no opinion on Earlier/overdue tasks itself: `itemsEmpty`
 * means whatever the caller's own mode considers "the screen's own scope,
 * Earlier excluded". For Today that has always been plain
 * `uiState.items.isEmpty()` — [TodoListUiState.items] never included overdue
 * tasks for that mode (see `TodoRepository.buildTodosForMode`'s `isTodayTodo`
 * filter) — but Scheduled/Priority/All/List mix overdue straight into
 * `items`, so those callers pass [nonEarlierSectionsEmpty] instead. Either
 * way this function needs no Earlier-aware parameter of its own.
 *
 * [cancelledAtMs] is the ending this gate did not have. A celebration is OPENED
 * by a transition -- a completion -- and was only ever CLOSED by re-reading a
 * static predicate plus a timer, so nothing in it observed the opposite
 * transition: a task coming back. Undo restores the row through the repository
 * and, on the reported path, does not move `itemsEmpty` at all, because the row
 * that came back was OVERDUE and this predicate excludes Earlier by design. The
 * paper then flew over a visible row until its own flight clock ran out, which
 * is the "goes away after a few seconds" in the report -- this window has no
 * clock of its own on Android, `nowMs` being read during composition.
 *
 * So the cancel is an ARRIVAL, counted across every bucket by
 * [TodoListViewModel]'s `pendingRowArrived`, and never a re-read of the
 * emptiness above. Compared rather than cleared: a completion landing after a
 * cancel re-opens the window by being the newer stamp, with no mutation from an
 * effect to order against a second completion arriving inside the same window.
 * `>=` and not `>` because an undo always follows its own completion and a
 * same-tick stamp must lose to nothing.
 */
internal fun shouldCelebrateEmptyState(
    itemsEmpty: Boolean,
    lastCompletionAtMs: Long,
    remoteEmptiedAtMs: Long,
    cancelledAtMs: Long,
    screenResumed: Boolean,
    nowMs: Long,
    windowMs: Long = CompletionCelebrationWindowMs,
): Boolean {
    if (!itemsEmpty) return false
    if (cancelledAtMs != 0L && cancelledAtMs >= maxOf(lastCompletionAtMs, remoteEmptiedAtMs)) {
        return false
    }
    val ownTapCelebrates = lastCompletionAtMs != 0L && nowMs - lastCompletionAtMs < windowMs
    val remoteCompletionCelebrates = remoteEmptiedAtMs != 0L &&
            screenResumed &&
            nowMs - remoteEmptiedAtMs < windowMs
    return ownTapCelebrates || remoteCompletionCelebrates
}

/**
 * The mirror of [TodoListScreen]'s `showTodayEarlierIllustration`, for the case
 * where Earlier is expanded rather than collapsed.
 *
 * `showTodayEarlierIllustration` deliberately stays false once Earlier is
 * expanded -- requirement 3 hands that slot to Earlier's own rows, not the
 * scene -- but that left completing the very last pending-today task while
 * Earlier already happened to be expanded with nothing on screen at all: not
 * the celebratory scene, not the plain one, no confetti. This is true for
 * exactly [shouldCelebrateEmptyState]'s own window -- it never contests
 * requirement 3's "expanded Earlier owns this slot" call outside that window,
 * it only fills the gap requirement 3 left inside it.
 *
 * What happens when the window closes changed once the scene moved below
 * Earlier's rows. It used to hand the slot straight back to Earlier, the same
 * as if no completion had happened. Now [shouldFoldEarlierForCelebration] folds
 * Earlier shut on the way in -- otherwise the scene this turns on is composed
 * past the fold and the burst never runs at all -- so what the window's expiry
 * hands back is the ordinary collapsed presentation: header, then the plain
 * scene, which is where this screen settles for "scope empty, overdue waiting"
 * anyway. The slot is still never taken from rows the user is looking at
 * outside the window.
 *
 * Pulled out as a pure function, like [shouldCelebrateEmptyState] above, so
 * this interaction -- the one the earlier review found neither the overlay
 * nor the inline scene covered -- has a unit test rather than only a
 * device/emulator check.
 *
 * Despite the name, nothing inside is actually Today-specific -- every
 * parameter is a plain boolean the caller derives however its own mode
 * needs to. [TodoListScreen] now also calls this for Scheduled/Priority/All/
 * List, passing their own `scopeHasEarlierItems`/`scopeItemsEmpty` (see
 * [nonEarlierSectionsEmpty]) in for `todayHasEarlierItems`/`itemsEmpty`.
 */
internal fun shouldShowTodayEarlierExpandedCelebration(
    todayHasEarlierItems: Boolean,
    itemsEmpty: Boolean,
    isLoading: Boolean,
    suppressInitialTodayTimeline: Boolean,
    scopedSearchActive: Boolean,
    earlierCollapsed: Boolean,
    celebrateEmptyState: Boolean,
): Boolean {
    return todayHasEarlierItems &&
            itemsEmpty &&
            !isLoading &&
            !suppressInitialTodayTimeline &&
            !scopedSearchActive &&
            !earlierCollapsed &&
            celebrateEmptyState
}

/**
 * Whether this frame is the one that folds Earlier shut so a celebration has
 * somewhere on screen to land.
 *
 * [shouldShowTodayEarlierExpandedCelebration] answers "should the scene be
 * visible"; this answers the question that only became a question once the
 * scene moved below Earlier's rows -- "will anyone see it". They are not the
 * same question and collapsing them into one boolean is what produced the bug
 * this exists for. The scene is emitted after Earlier's own rows (see
 * [earlierSceneFollowsSection]), so a completion that empties the scope while
 * Earlier is ALREADY expanded puts it under N overdue rows. Past six or so of
 * them the scene's box is half below the fold; past eleven its top edge is,
 * and a `LazyColumn` does not compose an item it has not reached. `TdayConfetti`
 * is `matchParentSize()` inside `TdayEmptyState`, and its flight is started by
 * a `LaunchedEffect` -- an effect in an uncomposed item never runs, so the
 * burst does not merely land off screen, it never happens, and the window has
 * closed and taken the item away by the time a scroll could reach it. That flag
 * exists for nothing else.
 *
 * The fix keeps the scene's single mount point and moves the ROWS instead:
 * fold Earlier for the duration, and the scene rises into the slot directly
 * under a header that has not moved -- which is the collapsed presentation this
 * screen already settles into for "scope empty, overdue waiting", reached a few
 * seconds early. The trade, stated because it is a real one: this closes a list
 * the user opened. It is still the cheaper of the two, because the alternatives
 * are worse in kind rather than in degree. A second mount point above the header
 * reintroduces the screen-height header jump this whole change exists to remove.
 * An `animateScrollToItem` onto the scene drags the viewport past every overdue
 * row to show a celebration, leaves the feed parked at the bottom, and jumps it
 * again when the window closes and the scene goes away.
 *
 * [celebrationStampMs] is the completion's own timestamp -- the later of the
 * local tap and `remoteEmptiedAtMs`, both on `SystemClock.uptimeMillis` -- and
 * [foldedForStampMs] is the last stamp already folded for. Keyed on the stamp
 * rather than on the flag because the flag goes false the instant the fold takes
 * effect and true again the instant the user re-expands: keyed on the flag, a
 * user who taps Earlier open during the celebration gets it folded shut under
 * their finger, over and over, for four seconds. One fold per completion. After
 * that the user's tap wins and the scene goes back below the rows, unseen, which
 * is their own deliberate choice and not ours.
 *
 * WHAT A CANCELLED CELEBRATION DOES TO THE FOLD: nothing, deliberately, and the
 * decision is written here rather than left to be rediscovered. Undo now ends the
 * burst the moment the row comes back (see [shouldCelebrateEmptyState]), and the
 * obvious follow-on is that it should put Earlier back the way the user had it.
 * It should not, for two reasons that are the same reason twice. The first is
 * that the fold is a write into `collapsedSectionKeys`, which is ALSO the user's
 * own control: restoring it means remembering a pre-celebration state and
 * replaying it over whatever the user has done to that header since, and a
 * header that re-opens under the finger that just shut it is this function's own
 * `foldedForStampMs` bug pointing the other way. The second is that the state a
 * cancel leaves behind -- scope empty, Earlier collapsed over the restored
 * overdue row, its header and its count directly above it and one tap from open
 * -- is EXACTLY the state the window expiring four seconds later would have left
 * anyway. Undo is not owed a better outcome than waiting; it is owed the same
 * one, sooner, and that is what it gets. What was wrong was never the fold. It
 * was the paper still flying over a row that had come back.
 */
internal fun shouldFoldEarlierForCelebration(
    showEarlierExpandedCelebration: Boolean,
    celebrationStampMs: Long,
    foldedForStampMs: Long,
): Boolean = showEarlierExpandedCelebration &&
        celebrationStampMs != 0L &&
        celebrationStampMs != foldedForStampMs

/**
 * The [TodoSection.key] every mode uses for the overdue/"Earlier" bucket.
 * Pulled out once the Today-mode Earlier section started repeating this literal
 * enough in one file to trip DeepSource's duplicate-string-literal check.
 * `internal` rather than `private` so [earlierSceneFollowsSection]'s own tests
 * can assert against the real key instead of a second, test-local copy of it --
 * which matters more now that the key is what decides where in the feed the
 * empty-state scene is emitted, not merely which header collapses.
 */
internal const val EARLIER_SECTION_KEY = "earlier"

/**
 * "Zero active/pending items for this screen's own scope" -- generalizes
 * Today's `uiState.items.isEmpty()` (already exactly this, since
 * [TodoListUiState.earlierItems] keeps overdue tasks out of `items`
 * entirely for that mode) to Scheduled/Priority/All/List, where Earlier is
 * a display-time bucket of the SAME items array instead of a separate
 * field: [TodoRepository.buildTodosForMode] mixes their overdue tasks
 * straight into `items`, so `items.isEmpty()` alone would stay false for as
 * long as Earlier held anything, even with nothing else left pending.
 *
 * Takes the sections [buildTimelineSections] already built rather than
 * re-deriving "which tasks are overdue" itself, so this can never disagree
 * with what a viewer sees sitting under the Earlier header. Every mode
 * without an Earlier bucket at all (Scheduled, Overdue, Floater) has no
 * section keyed [EARLIER_SECTION_KEY] to exclude, so this reduces to plain
 * "no sections" for them -- the same thing `items.isEmpty()` already meant.
 */
internal fun nonEarlierSectionsEmpty(sections: List<TodoSection>): Boolean =
    sections.none { section -> section.key != EARLIER_SECTION_KEY && section.items.isNotEmpty() }

/**
 * The task a live reschedule drag is actually carrying, or null when the id in
 * hand no longer names a row on screen.
 *
 * Searches [earlierItems] as well as [items] because in Today mode those are
 * two disjoint arrays, not one: [TodoListUiState.earlierItems] deliberately
 * keeps overdue tasks OUT of `items` so the empty-state gate can stay a plain
 * "pending today" count (see [nonEarlierSectionsEmpty]). Every Earlier row is
 * still a real, long-pressable, draggable row, so an `items`-only lookup
 * answered null for exactly the rows this screen most needs to move -- the
 * overdue ones -- and a drag that starts on Earlier read as no drag at all:
 * no dragged task to test drop-eligibility against, therefore no registered
 * drop targets, therefore a gesture that went nowhere and died on release.
 * Every other mode leaves `earlierItems` empty, so the extra scan costs them
 * nothing and changes nothing.
 *
 * One function for both readers (the liveness flag that restores empty drop
 * buckets, and the dragged-task lookup the drop-eligibility test runs on) so
 * the two can never disagree about whether a drag is live -- they used to
 * match on different fields, `id` alone against `id`-or-`canonicalId`, which
 * is a disagreement waiting to happen for a recurring occurrence.
 */
internal fun draggedTimelineTodo(
    draggedTodoId: String?,
    items: List<TodoItem>,
    earlierItems: List<TodoItem>,
): TodoItem? {
    val targetId = draggedTodoId ?: return null
    return (items.asSequence() + earlierItems.asSequence())
        .firstOrNull { todo -> todo.id == targetId || todo.canonicalId == targetId }
}

/**
 * Which section the inline "today-earlier-empty-scene" item is emitted AFTER --
 * the whole of this screen's new ordering claim, written as a decision rather
 * than as a line number, because a line number is the one thing a module with no
 * Compose UI test and no device cannot check.
 *
 * True for Earlier's own section and for nothing else, which is to say: hero,
 * then the real Earlier header, then Earlier's rows, then the scene. The scene
 * used to be emitted above [sectionedTimelineContent] entirely, between the hero
 * and that header. That order was argued in place and the argument was real --
 * it kept the header out from under a full-screen overlay -- but it cost the
 * header its anchor. Expanding Earlier shrank an item ABOVE the header, so the
 * header the user had just tapped, and the hero-relative position they had
 * tapped it at, travelled roughly a third of a screen upward every time the
 * overdue list opened, and back down every time it closed. Header-first removes
 * that by construction: nothing above the header changes height any more, so the
 * header does not move at all. The rows grow downward out of it and push the
 * scene ahead of them while it fades.
 *
 * Keyed on the section rather than emitted after the whole of
 * [sectionedTimelineContent], because "after everything" and "after Earlier" are
 * only the same place while the scope is empty. A live reschedule drag restores
 * the empty time-of-day buckets for Today and the empty day buckets for
 * All/Priority/List (see [buildTimelineSections]), so a scene emitted after the
 * loop would drop below a week of empty headers the moment a drag started inside
 * the celebration window -- a screen-height teleport mid-gesture. Emitted from
 * inside the loop it is directly under Earlier in every mode, drag or no drag,
 * which is the only form of "directly under Earlier" that is true all the time.
 */
internal fun earlierSceneFollowsSection(sectionKey: String): Boolean =
    sectionKey == EARLIER_SECTION_KEY

/**
 * The inline scene's own visibility -- [TodoListScreen]'s
 * `showEarlierIllustration` -- pulled out for the reason
 * [shouldCelebrateEmptyState] and [nonEarlierSectionsEmpty] were: on this screen
 * a decision that is not a function is a decision nothing checks.
 *
 * Read what is NOT a parameter. There is no `earlierExpandPending` and no motion
 * flag, and both used to be here. `earlierExpandPending` was the
 * exit-before-expand beat: a tap on Earlier's collapsed header set it, held the
 * section shut for one [TdayFeedItemMotion.FadeOutMillis] while the scene played
 * its exit, and only then released the rows. It existed because the scene and
 * the rows wanted one slot -- the scene shrinking pulled the header UP on the
 * same frame the rows pushed it DOWN, and something had to go first. With the
 * scene below the rows (see [earlierSceneFollowsSection]) they contest nothing.
 * The rows insert between the header and the scene; `displacedFeedItemMotion`
 * carries the scene down on [TdayFeedItemMotion.Placement] while its own
 * `fadeOut` takes the paint away. Two motions in the same direction, begun on
 * the same frame from the same boolean -- which is what was actually asked for:
 * the image goes down and fades away AS the overdue list expands, not after it.
 *
 * So the beat is retired rather than left lying around, and its absence from
 * this signature is the guarantee that it cannot come back by accident. A
 * serialised wait in front of motion that no longer needs serialising is the
 * fifth idiom rule's dead gap exactly, and this one was worse than most: it ran
 * on `scaledDelay(..., motionScale)` against the DEVICE animator scale, so a
 * user with the app's own motion preference off and device animations on tapped
 * a header and got 150 ms of nothing in front of a hand-off that had already
 * happened.
 *
 * [earlierCollapsed] is therefore the whole of the sequencing. The frame that
 * releases Earlier's rows is the frame that turns this false: the scene's exit
 * and the rows' entrance are two readings of one boolean rather than two steps
 * of a schedule, and collapsing runs the same pair backwards for free -- rows
 * leave, scene comes back up behind them.
 *
 * Deliberately narrower than the item's mount guard (`earlierScenePresent` in
 * [TodoListScreen], which drops [earlierCollapsed] and the celebration term): an
 * item its guard has already taken out of the list has no exit left to play, so
 * the mount has to outlive the visibility.
 */
internal fun shouldShowEarlierScene(
    scopeHasEarlierItems: Boolean,
    scopeItemsEmpty: Boolean,
    isLoading: Boolean,
    suppressInitialTimeline: Boolean,
    scopedSearchActive: Boolean,
    earlierCollapsed: Boolean,
): Boolean = scopeHasEarlierItems &&
        scopeItemsEmpty &&
        !isLoading &&
        !suppressInitialTimeline &&
        !scopedSearchActive &&
        earlierCollapsed

/**
 * Whether the scene animates its hand-off at all, or is simply drawn -- and
 * removed -- in its finished state.
 *
 * Two gates, and they are different kinds of thing. [timelineAnimationsEnabled]
 * is the feed's first-frame guard ("this list has settled enough to animate at
 * all") and says nothing about what the user asked for. [motionEnabled] is the
 * preference, read through Phase 8's `rememberTdayMotionEnabled()`. The scene
 * consulted only the first, which left a user who had turned motion off watching
 * a third of a screen fade and expand over 190 ms anyway; the task-feed skeleton
 * directly above it in the same `LazyColumn` already asks the preference itself,
 * and this is that same call made from the one place in the hand-off that was
 * still missing it.
 *
 * With motion off the answer is the finished state on the first frame and no
 * wait left anywhere: `EnterTransition.None`/`ExitTransition.None` on the scene,
 * `displacedFeedItemMotion` already off behind the same flag, and no expand beat
 * in front of the rows -- not because the beat is skipped at scale 0, but
 * because there is no longer a beat to skip (see [shouldShowEarlierScene]).
 * That is the fifth idiom rule satisfied by having nothing to shorten, which is
 * the only way it is ever satisfied for good.
 */
internal fun earlierSceneAnimatesHandoff(
    timelineAnimationsEnabled: Boolean,
    motionEnabled: Boolean,
): Boolean = timelineAnimationsEnabled && motionEnabled

/**
 * The Anytime home's inline scene -- whether it is VISIBLE.
 *
 * The plain `if` this replaces read `isFloaterTaskHomeScreen && items.isEmpty()
 * && !isLoading` inline in [floaterTaskHomeRootFeedContent], which is the shape
 * [shouldShowEarlierScene] above was pulled out of and is pulled out for the
 * same reason: on this screen a decision that is not a function is a decision
 * nothing checks, and there is no device here to check it on.
 *
 * Raw [itemsEmpty] rather than `scopeItemsEmpty`, deliberately, and this is the
 * one place on this screen where the raw count is the right one. The scoped
 * screens subtract Earlier out because an overdue task waiting does not stop
 * today's work being finished; an Anytime task has no date, so this feed has no
 * Earlier bucket to subtract and nothing for the distinction to mean.
 */
internal fun shouldShowFloaterEmptyScene(
    isFloaterTaskHomeScreen: Boolean,
    itemsEmpty: Boolean,
    isLoading: Boolean,
): Boolean = isFloaterTaskHomeScreen && itemsEmpty && !isLoading

/**
 * ...and whether it is MOUNTED, which is not the same question and is the half
 * that was wrong.
 *
 * The scene's mount guard was [shouldShowFloaterEmptyScene] itself, so an undo
 * on this feed took `items` 0 -> 1 and the lazy item -- with `TdayEmptyState`
 * and the `TdayConfetti` inside it -- was dropped on that frame. A cancelled
 * burst fades its paper out over `Quick` instead of cutting it (`TdayConfetti`'s
 * mount latch), and a fade cut by the unmount above it is the same complaint one
 * layer up: the very thing the envelope was added to stop. An item its guard has
 * already removed has no exit left.
 *
 * So the mount outlives the visibility, exactly as [TodoListScreen]'s
 * `earlierScenePresent` outlives `showEarlierIllustration` one branch over. The
 * difference is where the extra life comes from. Earlier's scene can drop two
 * NARROWER terms (the collapse state and the celebration) and still have a true
 * guard left around them; this scene's guard is the emptiness itself, and
 * emptiness is the thing the undo moves -- there is no wider standing condition
 * to fall back on. What holds it instead is the exit's own clock:
 * [sceneStillDrawn] is the `MutableTransitionState`'s `currentState`, which stays
 * true until `AnimatedVisibility` has finished playing the exit and then falls on
 * its own. With motion off there is no exit to play, so it falls in the same
 * frame and no wait survives in front of the restored row.
 */
internal fun shouldMountFloaterEmptyScene(
    sceneVisible: Boolean,
    sceneStillDrawn: Boolean,
): Boolean = sceneVisible || sceneStillDrawn

// KT-R1006 (cyclomatic complexity) is suppressed on this declaration rather
// than fixed further here. Two separate facts, both worth writing down:
//
//   * The decomposition already happened, and it helped. DeepSource measured
//     this function at 310 before this PR. Pulling the sectioned-timeline
//     body, the flat items path, and the root floater feed body out of the
//     LazyColumn content below — see [sectionedTimelineContent],
//     [flatTodoRowsContent], and [floaterTaskHomeRootFeedContent] — brought
//     it to 272. DeepSource fingerprints an occurrence by its line and by the
//     number in its message, so that improvement still reads as "1
//     introduced, 0 resolved" and turns the check red on its own.
//   * The remaining 272 is not the LazyColumn body's anymore — those three
//     extractions already own it. It is this function's own state
//     derivation, effects, and sheet/dialog wiring, and splitting that
//     further means state-holder classes, not LazyListScope extensions: a
//     riskier shape of change, on a screen with no Compose UI tests and no
//     device on this box to catch a state-vs-value mistake at a new function
//     boundary. That follow-up is deliberately out of scope here rather than
//     rushed into this PR.
//
// One declaration, one issue code — the narrowest form the tool has, and the
// style the repo already uses for KT-W1042, KT-C1001, and
// sectionedTimelineContent's own KT-R1006 below. Never file-wide.
@Composable
fun TodoListScreen( // skipcq: KT-R1006
    uiState: TodoListUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    highlightedTodoId: String? = null,
    onSummarize: () -> Unit,
    onDismissSummaryConnectivityError: () -> Unit,
    onAddTask: (payload: CreateTaskPayload) -> Unit,
    onParseTaskTitleNlp: suspend (title: String, referenceDueEpochMs: Long) -> TodoTitleNlpResponse?,
    onUpdateTask: (todo: TodoItem, payload: CreateTaskPayload) -> Unit,
    onMoveTask: (todo: TodoItem, targetDate: LocalDate, scope: TaskRescheduleScope) -> Unit,
    onMoveTaskToTimeOfDay: (todo: TodoItem, hour: Int, scope: TaskRescheduleScope) -> Unit,
    onComplete: (todo: TodoItem) -> Unit,
    onDelete: (todo: TodoItem) -> Unit,
    onPromoteFloater: (todo: TodoItem, dueEpochMs: Long) -> Unit = { _, _ -> },
    onDemoteTodo: (todo: TodoItem) -> Unit = {},
    onDeferTask: (todo: TodoItem, dueEpochMs: Long) -> Unit = { _, _ -> },
    // Bulk actions. Each takes the whole selection, already reduced to the rows
    // the action may touch — see docs/design/bulk-selection.md.
    onBulkComplete: (todos: List<TodoItem>) -> Unit = {},
    onBulkDelete: (todos: List<TodoItem>) -> Unit = {},
    onBulkSetPriority: (todos: List<TodoItem>, priority: String) -> Unit = { _, _ -> },
    onBulkMoveToList: (todos: List<TodoItem>, listId: String?) -> Unit = { _, _ -> },
    onOpenMorningSweep: () -> Unit = {},
    onUpdateListSettings: (listId: String, name: String, color: String?, iconKey: String?) -> Unit,
    onDeleteList: (listId: String) -> Unit,
    onOpenFloaterList: (listId: String, listName: String) -> Unit = { _, _ -> },
    onOpenCompleted: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onCreateList: (name: String, color: String?, iconKey: String?) -> Unit = { _, _, _ -> },
    rootFeedTab: RootFeedTab? = null,
    onRootFeedTabSelected: ((RootFeedTab) -> Unit)? = null,
    showRootFeedDock: Boolean = true,
    showCreateTaskButton: Boolean = true,
    /**
     * The swipe slot to use instead of one of this screen's own, for a host that
     * draws chrome outside this composable. Non-null exactly where
     * `showRootFeedDock`/`showCreateTaskButton` are false and for the same
     * reason — see the slot's own comment below, and `RootFeedContent`.
     */
    hostSwipeSlot: TaskSwipeSlot? = null,
    openCreateTaskOnStart: Boolean = false,
    exitToLauncherOnBack: Boolean = false,
    exitOnCreateTaskSheetDismiss: Boolean = false,
    onCreateTaskFlowFinished: () -> Unit = {},
    pullRefreshEnabled: Boolean = true,
    summaryAvailable: Boolean = true,
    usesRootFeedHeader: Boolean = false,
    createTaskRequestKey: Int = 0,
    onCreateTaskRequestHandled: (Int) -> Unit = {},
    scrollToTopRequestKey: Int = 0,
    onRootDockCollapsedChange: (Boolean) -> Unit = {},
    onRootControlsVisibleChange: (Boolean) -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val context = LocalContext.current
    val restingFloatersEnabled = remember { RestingFloatersPreferenceStore(context).isEnabled() }
    // Finishing a list is a payoff, not an absence. The empty state that follows
    // the last row leaving gets confetti — but only when the row left because it
    // was ticked off: deleting the last task, or opening a list that was already
    // empty, gets the plain arrival. Whether that tick happened here, on another
    // device, or from a collaborator on a shared list.
    var lastCompletionAtMs by remember { mutableLongStateOf(0L) }
    val completeAndCelebrate: (TodoItem) -> Unit = { todo ->
        lastCompletionAtMs = SystemClock.uptimeMillis()
        onComplete(todo)
    }
    // `uiState.remoteEmptiedAtMs` is the remote sibling of the tap-time set
    // above — see `TodoListViewModel.hydrateFromExternalCacheChange` for how
    // it is set and why it can't be as precise (it can't tell a remote
    // completion from a remote delete of the last task the way this composable
    // tells complete from delete for its own taps). The ViewModel is scoped to
    // this route's `NavBackStackEntry`, not to this composable being on
    // screen: a forward push to another destination (or the app going to the
    // background) leaves the entry — and its `cacheDataVersion` collector —
    // alive and still updating `remoteEmptiedAtMs` underneath, so "the route
    // got disposed" is not a real guarantee here the way it is for a popped
    // entry. `screenLifecycleState` below is the on-screen/foreground gate
    // that closes that gap: `LocalLifecycleOwner` inside a `NavHost`
    // destination resolves to the entry's own lifecycle, which only reaches
    // `RESUMED` while this destination is both the top of the back stack and
    // the app is foregrounded — the same two conditions iOS's
    // `isScreenVisible`/`scenePhase == .active` check for `remoteEmptiedAt`.
    val lifecycleOwner = LocalLifecycleOwner.current
    val screenLifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    // Built off `uiState.items` directly -- never the search-filtered
    // `timelineItems`/`timelineSections` further down -- and with no drag in
    // progress, so a live query or an in-flight reschedule drag can never
    // make the scope read as emptier (or as having more/less Earlier) than
    // it actually does. For Today this reproduces exactly what
    // `buildTimelineSections` already builds for rendering, since Today's
    // `items`/`earlierItems` split is unaffected by search or drag state
    // either; for Scheduled/Priority/All/List it is what lets
    // [nonEarlierSectionsEmpty] answer for their scope without inventing a
    // second "is this task overdue" check next to the one already inside
    // `buildScheduledSections`.
    val scopeSections = remember(uiState.mode, uiState.items, uiState.earlierItems) {
        buildTimelineSections(
            mode = uiState.mode,
            items = uiState.items,
            isDragActive = false,
            earlierItems = uiState.earlierItems,
        )
    }
    // Requirement 2's "does an Earlier section apply here, and does it hold
    // anything": true for Today whenever `earlierItems` does (as before),
    // and now also for Scheduled/Priority/All/List whenever their own
    // Earlier bucket -- carved out of the same `items` array at
    // display time -- is non-empty. Every other mode has no section keyed
    // `EARLIER_SECTION_KEY` at all, so this is false for them, unchanged.
    val scopeHasEarlierItems = scopeSections.any { section ->
        section.key == EARLIER_SECTION_KEY && section.items.isNotEmpty()
    }
    // Requirement 1, generalized: "zero active/pending items for this
    // screen's own scope, Earlier excluded". Today's `items` already
    // excludes Earlier by construction, so this is still plain
    // `items.isEmpty()` for it; see [nonEarlierSectionsEmpty] for why the
    // other modes need more than that.
    val scopeItemsEmpty = nonEarlierSectionsEmpty(scopeSections)
    // `celebrationCancelledAtMs` is the ViewModel's, and it has to be: undo lives
    // in `UndoableDeleteCoordinator`, a @Singleton on its own MainScope with no
    // per-screen identity and no way to reach back into this composition. The
    // signal comes home through `uiState` or it does not come home at all.
    val celebrateEmptyState = shouldCelebrateEmptyState(
        itemsEmpty = scopeItemsEmpty,
        lastCompletionAtMs = lastCompletionAtMs,
        remoteEmptiedAtMs = uiState.remoteEmptiedAtMs,
        cancelledAtMs = uiState.celebrationCancelledAtMs,
        screenResumed = screenLifecycleState == Lifecycle.State.RESUMED,
        nowMs = SystemClock.uptimeMillis(),
    )
    val zoneId = remember { ZoneId.systemDefault() }
    val selectedList = uiState.lists.firstOrNull { it.id == uiState.listId }
    val selectedListColorKey = selectedList?.color
    val isTodayDaytime = rememberTodoRootIsDaytime()
    if (isTodayDaytime) ImageVector.vectorResource(R.drawable.ic_lucide_sun) else ImageVector.vectorResource(
        R.drawable.ic_lucide_moon
    )
    if (isTodayDaytime) TdayTitleIconDayAccent else TdayTitleIconNightAccent
    val usesTodayStyle =
        uiState.mode == TodoListMode.TODAY || uiState.mode == TodoListMode.OVERDUE || uiState.mode == TodoListMode.SCHEDULED || uiState.mode == TodoListMode.ALL || uiState.mode == TodoListMode.PRIORITY || uiState.mode == TodoListMode.FLOATER || uiState.mode == TodoListMode.LIST
    val isFloaterTaskHomeScreen =
        uiState.mode == TodoListMode.FLOATER && uiState.listId.isNullOrBlank()
    val isListDetailScreen =
        uiState.mode == TodoListMode.LIST ||
                (uiState.mode == TodoListMode.FLOATER && !uiState.listId.isNullOrBlank())
    // VIEWER members of a shared list get a read-only screen: no create FAB,
    // no swipe edit/delete, no complete taps, no drag.
    val isViewerList = isListDetailScreen && selectedList?.isViewer == true
    val usesRootFeedChrome =
        usesRootFeedHeader || isFloaterTaskHomeScreen
    val titleColor = modeAccentColor(
        mode = uiState.mode,
        listColorKey = selectedListColorKey,
    )
    val fabColor = todoFabColorForMode(
        mode = uiState.mode,
        listColorKey = selectedListColorKey,
    )
    val emptyWatermarkIcon = emptyStateIconForMode(
        mode = uiState.mode,
        listIconKey = selectedList?.iconKey,
        isTodayDaytime = isTodayDaytime,
    )
    val emptyWatermarkDrawable = emptyStateDrawableForMode(uiState.mode)
    val emptySceneIcon = emptyStateSceneIconForMode(
        mode = uiState.mode,
        listIconKey = selectedList?.iconKey,
        isTodayDaytime = isTodayDaytime,
    )
    // The floater leaf watermark is mirrored so it points the same way as the iOS "leaf" symbol
    // and the Tday widgets.
    val flipWatermark =
        uiState.mode == TodoListMode.FLOATER && selectedList?.iconKey.isNullOrBlank()
    val showSectionedTimeline =
        uiState.mode == TodoListMode.TODAY || uiState.mode == TodoListMode.OVERDUE || uiState.mode == TodoListMode.SCHEDULED || uiState.mode == TodoListMode.ALL || uiState.mode == TodoListMode.PRIORITY || uiState.mode == TodoListMode.FLOATER || uiState.mode == TodoListMode.LIST
    val suppressInitialTodayTimeline =
        uiState.mode == TodoListMode.TODAY &&
                !uiState.hasHydratedSnapshot &&
                uiState.items.isEmpty()
    var draggedScheduledTodoId by rememberSaveable(uiState.mode) { mutableStateOf<String?>(null) }
    val canRescheduleTasks = uiState.mode.supportsTaskReschedule()
    // Search over one screen's own tasks and nothing else. The root feeds carry
    // their own field in RootFeedHeroHeader, so this one belongs to every screen
    // that draws the hero toolbar instead: the five timeline scopes as well as
    // the list-detail screens — the custom lists and the floater lists.
    val supportsScopedSearch = usesTodayStyle && !usesRootFeedChrome
    // Whether the bar offers the magnifier at all. A scope or a list with no
    // tasks in it has no set for a query to narrow, so the button would only
    // raise a keyboard over an empty screen — and over the empty-state scene,
    // which is the whole of what that screen has to say. Gates the button, not
    // the field: deleting the last task while a search is open should retire
    // the affordance, not slam the field shut under the user's hands.
    val canOpenScopedSearch = supportsScopedSearch && uiState.items.isNotEmpty()
    var scopedSearchExpanded by rememberSaveable(uiState.mode, uiState.listId) {
        mutableStateOf(false)
    }
    var scopedSearchQuery by rememberSaveable(uiState.mode, uiState.listId) { mutableStateOf("") }
    var scopedSearchNeedsFocus by remember(uiState.mode, uiState.listId) { mutableStateOf(false) }
    val normalizedScopedSearchQuery = remember(scopedSearchQuery) {
        scopedSearchQuery.trim().lowercase(Locale.getDefault())
    }
    val showScopedSearchField = supportsScopedSearch && scopedSearchExpanded
    val scopedSearchActive = showScopedSearchField && normalizedScopedSearchQuery.isNotBlank()
    // Day Done: "finished everything" earns its own calm state instead of
    // the generic no-tasks scene. Hoisted (rather than computed once per
    // call site, as it used to be inline in the overlay below) because the
    // empty-state scene now has two possible homes -- the full-screen
    // overlay for a plain empty Today, and the inline scene Today shows in
    // Earlier's place when Earlier is still holding overdue tasks
    // (requirement 2/3) -- and both need to agree on the same
    // glyph/title/description and fire the same one-shot haptic exactly
    // once between them, not once each.
    val isDayDone = uiState.mode == TodoListMode.TODAY &&
            uiState.items.isEmpty() &&
            !uiState.isLoading &&
            !suppressInitialTodayTimeline &&
            !scopedSearchActive &&
            uiState.completedTodayCount > 0
    LaunchedEffect(isDayDone) {
        if (isDayDone) {
            TdayHaptics.completion(view)
        }
    }
    val emptyStateSceneIconRes = if (isDayDone) {
        R.drawable.ic_lucide_check_check
    } else {
        emptySceneIcon
    }
    val emptyStateSceneTitle = if (isDayDone) {
        stringResource(R.string.todos_all_done_today)
    } else {
        emptyStateMessageForMode(mode = uiState.mode, isFloaterList = isListDetailScreen)
    }
    val emptyStateSceneDescription = if (isDayDone) {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
    } else {
        emptyStateDescriptionForMode(mode = uiState.mode, isFloaterList = isListDetailScreen)
    }
    val closeScopedSearch = {
        scopedSearchExpanded = false
        scopedSearchQuery = ""
        scopedSearchNeedsFocus = false
    }
    val timelineItems = remember(uiState.items, scopedSearchActive, normalizedScopedSearchQuery) {
        if (!scopedSearchActive) {
            uiState.items
        } else {
            // The same two fields the web screens match on: the title and the
            // notes flattened out of their rich-text form. `uiState.items` is
            // already only this scope's tasks, so the search cannot reach past
            // the screen it was opened on.
            uiState.items.filter { todo ->
                todo.title.lowercase(Locale.getDefault())
                    .contains(normalizedScopedSearchQuery) ||
                        flattenNotesToPlainText(todo.description)
                            .lowercase(Locale.getDefault())
                            .contains(normalizedScopedSearchQuery)
            }
        }
    }
    val scopedSearchHasNoResults = scopedSearchActive && timelineItems.isEmpty()
    // An empty date bucket exists only to be dropped on, so it is on screen only
    // while something is being dragged. Keyed on the same id the drop-eligibility
    // check reads, so the buckets that appear are exactly the ones that can catch
    // the task in hand — and a list of three overdue tasks stops drawing a dozen
    // empty headers down to December.
    // Guarded on the id still resolving to a real task. `draggedScheduledTodoId`
    // is rememberSaveable, so a rotation mid-drag persists it while the gesture
    // that would clear it is gone — and the scaffold would then be stuck on
    // screen at rest, which is the very thing this is meant to remove.
    // Resolved against the search-filtered `timelineItems` rather than raw
    // `uiState.items` -- a row the query has hidden cannot be under a thumb --
    // but against `uiState.earlierItems` unfiltered, the same way the Earlier
    // section itself is built below: Earlier's rows are on screen, and
    // draggable, whether or not a scoped search is narrowing the day.
    val timelineDragActive = canRescheduleTasks &&
            draggedTimelineTodo(
                draggedTodoId = draggedScheduledTodoId,
                items = timelineItems,
                earlierItems = uiState.earlierItems,
            ) != null
    val timelineSections = remember(
        uiState.mode,
        timelineItems,
        timelineDragActive,
        uiState.earlierItems,
    ) {
        buildTimelineSections(
            mode = uiState.mode,
            items = timelineItems,
            isDragActive = timelineDragActive,
            earlierItems = uiState.earlierItems,
        )
    }
    val floaterTaskHomeListRows = remember(uiState.mode, uiState.listId, uiState.items, uiState.lists) {
        if (uiState.mode == TodoListMode.FLOATER && uiState.listId.isNullOrBlank()) {
            val floaterTaskHomeCountsByList = uiState.items
                .asSequence()
                .mapNotNull { it.listId }
                .groupingBy { it }
                .eachCount()
            // Show every list, including ones with no tasks yet, so a newly
            // created (still-empty) list is always reachable here.
            uiState.lists.map { list ->
                list to (floaterTaskHomeCountsByList[list.id] ?: 0)
            }
        } else {
            emptyList()
        }
    }
    val floaterTaskHomeListById = remember(uiState.lists) { uiState.lists.associateBy { it.id } }
    var timelineAnimationsReady by remember(uiState.mode, uiState.listId) {
        mutableStateOf(uiState.mode != TodoListMode.TODAY)
    }
    LaunchedEffect(uiState.mode, uiState.listId, uiState.hasHydratedSnapshot) {
        if (uiState.mode != TodoListMode.TODAY) {
            timelineAnimationsReady = true
            return@LaunchedEffect
        }
        if (!uiState.hasHydratedSnapshot) {
            timelineAnimationsReady = false
            return@LaunchedEffect
        }
        if (!timelineAnimationsReady) {
            withFrameNanos { }
            timelineAnimationsReady = true
        }
    }
    val timelineAnimationsEnabled =
        uiState.mode != TodoListMode.TODAY || timelineAnimationsReady
    val listState = rememberLazyListState()
    val screenScope = rememberCoroutineScope()
    val hasScrollableContent =
        listState.canScrollForward || listState.canScrollBackward
    val dockCollapsePx = with(LocalDensity.current) {
        RootFeedDockCollapse.CollapseThreshold.roundToPx()
    }
    val dockExpandPx = with(LocalDensity.current) {
        RootFeedDockCollapse.ExpandThreshold.roundToPx()
    }
    // Held rather than derived: which edge applies depends on the answer before it. The
    // position is sampled in a snapshotFlow instead of in composition because the offset
    // moves every frame of a fling, and this screen has no business recomposing at that
    // rate to settle one boolean.
    var scrolledPastDockFold by remember { mutableStateOf(false) }
    LaunchedEffect(listState, dockCollapsePx, dockExpandPx) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offsetPx) ->
            scrolledPastDockFold = RootFeedDockCollapse.next(
                previous = scrolledPastDockFold,
                firstVisibleItemIndex = index,
                scrollOffsetPx = offsetPx,
                collapsePx = dockCollapsePx,
                expandPx = dockExpandPx,
            )
        }
    }
    // A feed too short to scroll never folds the dock, whatever the fold point says.
    val dockCollapsed = hasScrollableContent && scrolledPastDockFold
    LaunchedEffect(dockCollapsed) {
        onRootDockCollapsedChange(dockCollapsed)
    }
    LaunchedEffect(Unit) {
        onRootControlsVisibleChange(true)
    }
    DisposableEffect(Unit) {
        onDispose { onRootControlsVisibleChange(true) }
    }
    val density = LocalDensity.current
    // The settle before a search result *navigated to* is scrolled to, on the app's
    // scale: what it waits out is `navigationEnterTransition` in `TdayApp`, and that
    // handover answers the in-app switch now as well as the animator scale. Left on
    // the device's it would be the 380 ms of a tapped result doing nothing that
    // [SEARCH_RESULT_NAV_SETTLE_DELAY_MS] already names — over a destination drawn
    // whole on the first frame, for the users who asked for less motion.
    val navSettleMotionScale = rememberTdayMotionScale()
    // Every other wait on this screen reads the SYSTEM scale, because each covers an
    // animation the in-app switch does not reach: the floater settle waits out the
    // feed re-laying itself under `animateItem` once the results card is dropped from
    // it (no route changes there — `closeFloaterTaskHomeSearch` is a state flip), the
    // and the two holds wait out `SwipeTaskRow`'s ungated highlight pulses. The only
    // gate on either is `timelineAnimationsEnabled`, a first-frame guard rather than
    // a preference. A wait zeroed while the motion it covers plays on is the fifth
    // idiom rule broken the other way about — see [effectiveMotionScale]. Each moves
    // to `rememberTdayMotionScale` as its animation is gated.
    //
    // The Earlier hand-off used to be the third of these, waiting out
    // `TdayFeedItemMotion.FadeOut` before releasing Earlier's rows. It is not listed
    // because it no longer exists: with the empty-state scene below those rows
    // instead of above them the exit and the entrance stopped competing for a slot,
    // and the wait went out rather than being re-scaled. See
    // [shouldShowEarlierScene].
    val motionScale = rememberSystemMotionScale()
    val heroCollapse = rememberLazyListHeroTitleCollapse(
        listState = listState,
        enabled = usesTodayStyle && !usesRootFeedChrome,
    )
    val heroIcon = emptyStateIconForMode(
        mode = uiState.mode,
        listIconKey = selectedList?.iconKey,
        isTodayDaytime = isTodayDaytime,
    )
    val isCollapsibleTimelineMode =
        uiState.mode == TodoListMode.ALL ||
                uiState.mode == TodoListMode.PRIORITY ||
                uiState.mode == TodoListMode.LIST ||
                // Today's own "Earlier" bucket (the overdue tasks tucked
                // under Today, see `buildTodaySections`) -- starts
                // collapsed exactly like the other three.
                uiState.mode == TodoListMode.TODAY
    var showCreateTaskSheet by rememberSaveable {
        mutableStateOf(openCreateTaskOnStart)
    }
    // The screen's one swipe slot, unless something above it owns a bigger
    // screen than this composable is. Screen level is the right altitude for
    // every pushed destination — each is its own NavHost entry with its own
    // chrome, so there is nothing above it worth hoisting to and nothing beside
    // it that could be open at the same time.
    //
    // The Anytime tab is the exception, and it is a real one rather than a
    // tidiness argument. There this composable is drawn INSIDE a `Crossfade`,
    // and the dock and the create button are siblings of that crossfade one
    // level up — so they are outside this Scaffold entirely, an interceptor here
    // never sees a touch on either, and "tapping the dock closes the row" was
    // false on the one screen the user spends the most time on. `RootFeedContent`
    // therefore owns the slot for both root tabs and installs the one interceptor
    // at the box that actually contains everything; whoever creates the slot
    // installs the interceptor, which is the rule that keeps the count at one.
    //
    // Keyed on mode + scoped list the way the saveable it replaced was, so
    // changing what the screen is a list *of* hands the slot back. `remember`
    // rather than `rememberSaveable` is deliberate and is argued at
    // [TaskSwipeSlot]: the rows' own reveal states are plain `remember`, so a
    // restored id named a row that had rebuilt closed. A host slot needs no key:
    // the two root tabs are one mode each and never change what they are a list
    // of.
    val swipeSlot = hostSwipeSlot ?: remember(uiState.mode, uiState.listId) { TaskSwipeSlot() }
    // --- Bulk selection ---------------------------------------------------
    // Screen-local, hoisted exactly the way `swipeSlot` above is, and
    // keyed on mode + scoped list so leaving the screen drops it for free. It
    // deliberately does not live in the ViewModel: that re-hydrates `items` on
    // every cache-version bump and a selection held there would fight it.
    var selectionActive by rememberSaveable(uiState.mode, uiState.listId) {
        mutableStateOf(false)
    }
    var selectedTodoIds by rememberSaveable(uiState.mode, uiState.listId) {
        mutableStateOf(emptySet<String>())
    }
    // Offered exactly where the hero toolbar draws its action cluster — every
    // list mode except the root floater feed, which is a feed of lists rather
    // than a task list. VIEWER members of a shared list get no affordance at
    // all, as they already get no swipe, complete or drag.
    val canSelectTasks = supportsScopedSearch && !isViewerList && uiState.items.isNotEmpty()
    // What "select all" means: every task the screen would render if you
    // scrolled to the end and expanded every section, in display order.
    val selectableTodos = remember(timelineSections) {
        timelineSections.flatMap { section -> section.items }
    }
    val selectedTodos = remember(selectableTodos, selectedTodoIds) {
        selectableTodos.filter { it.id in selectedTodoIds }
    }
    // The recurring rule, in one place: an occurrence may be bulk-completed as
    // the occurrence it represents, but delete, priority and move have no
    // per-occurrence route and would silently rewrite the whole series, so they
    // never see one. Complete also needs the occurrence to exist — a recurring
    // row with no instanceDate queues COMPLETE_TODO, which the backend turns
    // into a phantom completed-history row that marks nothing complete.
    val bulkCompleteTargets = remember(selectedTodos) {
        BulkSelectionPolicy.effectiveSelection(
            action = BulkAction.COMPLETE,
            selection = selectedTodos,
            hasInstanceDate = { it.instanceDate != null },
            isRecurring = { it.isRecurring },
        )
    }
    val bulkNonRecurringTargets = remember(selectedTodos) {
        BulkSelectionPolicy.effectiveSelection(
            action = BulkAction.DELETE,
            selection = selectedTodos,
            isRecurring = { it.isRecurring },
        )
    }
    val bulkSkipsRecurring = bulkNonRecurringTargets.size < selectedTodos.size
    var showBulkDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var showBulkPriorityPicker by rememberSaveable { mutableStateOf(false) }
    var showBulkListPicker by rememberSaveable { mutableStateOf(false) }
    var showBulkMoveConfirmation by rememberSaveable { mutableStateOf(false) }
    var pendingBulkMoveListId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectionCapReached = BulkSelectionPolicy.isAtCap(selectedTodoIds.size)
    val allSelectableSelected = selectableTodos.isNotEmpty() &&
            selectedTodoIds.size >= minOf(selectableTodos.size, BulkSelectionPolicy.MAX_SELECTION)
    val exitSelection = {
        selectionActive = false
        selectedTodoIds = emptySet()
        showBulkDeleteConfirmation = false
        showBulkPriorityPicker = false
        showBulkListPicker = false
        showBulkMoveConfirmation = false
        pendingBulkMoveListId = null
    }
    val toggleTodoSelected: (TodoItem) -> Unit = { todo ->
        selectedTodoIds = when {
            todo.id in selectedTodoIds -> selectedTodoIds - todo.id
            // At the cap a further tap is simply refused — no toast, no error.
            // The bar already says why, in place of the plain count.
            BulkSelectionPolicy.isAtCap(selectedTodoIds.size) -> selectedTodoIds
            else -> selectedTodoIds + todo.id
        }
    }
    // Reconciliation: a task that synced away, was completed on another device
    // or fell out of a live search drops out of the selection silently, and the
    // mode closes behind the last one instead of leaving an action bar hanging
    // over nothing.
    LaunchedEffect(selectionActive, selectableTodos, canSelectTasks) {
        if (!selectionActive) return@LaunchedEffect
        if (!canSelectTasks || selectableTodos.isEmpty()) {
            exitSelection()
            return@LaunchedEffect
        }
        val visibleIds = selectableTodos.mapTo(mutableSetOf()) { it.id }
        val reconciled = selectedTodoIds.filterTo(mutableSetOf()) { it in visibleIds }
        if (reconciled.size == selectedTodoIds.size) return@LaunchedEffect
        if (reconciled.isEmpty()) exitSelection() else selectedTodoIds = reconciled
    }
    var floaterTaskHomeSearchExpanded by rememberSaveable { mutableStateOf(false) }
    var floaterTaskHomeSearchQuery by rememberSaveable { mutableStateOf("") }
    val normalizedFloaterTaskHomeSearchQuery = remember(floaterTaskHomeSearchQuery) {
        floaterTaskHomeSearchQuery.trim().lowercase(Locale.getDefault())
    }
    val floaterTaskHomeSearchResults = remember(
        isFloaterTaskHomeScreen,
        normalizedFloaterTaskHomeSearchQuery,
        uiState.items,
        floaterTaskHomeListById,
    ) {
        if (!isFloaterTaskHomeScreen || normalizedFloaterTaskHomeSearchQuery.isBlank()) {
            emptyList()
        } else {
            uiState.items
                .asSequence()
                .filter { todo ->
                    todo.title.lowercase(Locale.getDefault())
                        .contains(normalizedFloaterTaskHomeSearchQuery) ||
                            flattenNotesToPlainText(todo.description).lowercase(Locale.getDefault())
                                .contains(normalizedFloaterTaskHomeSearchQuery) ||
                            (todo.listId?.let { floaterTaskHomeListById[it]?.name }
                                ?.lowercase(Locale.getDefault())
                                ?.contains(normalizedFloaterTaskHomeSearchQuery) == true)
                }
                .sortedWith(
                    compareByDescending<TodoItem> { it.pinned }
                        .thenBy { floaterPriorityRank(it.priority) }
                        .thenBy { it.title.lowercase(Locale.getDefault()) },
                )
                .take(20)
                .toList()
        }
    }
    val showFloaterTaskHomeSearchResults =
        isFloaterTaskHomeScreen && floaterTaskHomeSearchExpanded && floaterTaskHomeSearchQuery.isNotBlank()
    val headerCollapsePx = with(LocalDensity.current) {
        RootFeedHeroHeaderMetrics.CollapseDistance.toPx()
    }
    // The header draws the refresh pill itself, so it can fly in from the top
    // and hover in front of the title instead of being painted underneath the
    // pinned toolbar.
    var refreshIsRefreshing by remember { mutableStateOf(false) }
    var refreshPullFraction by remember { mutableFloatStateOf(0f) }
    var refreshWaveFrozen by remember { mutableStateOf(false) }
    var floaterSearchResultsBounds by remember { mutableStateOf<Rect?>(null) }
    val headerBarHeightPx = with(LocalDensity.current) {
        RootFeedHeroHeaderMetrics.BarHeight.toPx()
    }
    // The other bar: TdayHeroToolbar's row, on the screens that pin one instead
    // of carrying the root feeds' hero header.
    val pinnedToolbarHeightPx = with(LocalDensity.current) {
        TdayHeroTitleMetrics.ToolbarHeight.toPx()
    }
    // Read lazily inside the header so a scroll frame recomposes the header
    // alone rather than this whole screen — the list body is very large.
    val headerCollapseProgress: () -> Float = {
        if (listState.firstVisibleItemIndex > 0) {
            1f
        } else {
            (listState.firstVisibleItemScrollOffset / headerCollapsePx).coerceIn(0f, 1f)
        }
    }
    // The same settle every other screen uses, rather than the copy this feed
    // used to keep: keyed on `isScrollInProgress`, it restarted in the gap
    // between the drag's scroll session and the fling's, took the nearest half
    // with no velocity so a short upward flick fell back, and eased in-out so it
    // held still before it moved. That is what read as "not elastic" here.
    LazyListHeroTitleSettle(
        listState = listState,
        collapsePx = headerCollapsePx,
        enabled = usesRootFeedChrome && !floaterTaskHomeSearchExpanded,
    )
    val closeFloaterTaskHomeSearch = {
        floaterTaskHomeSearchExpanded = false
        floaterTaskHomeSearchQuery = ""
    }
    LaunchedEffect(scrollToTopRequestKey) {
        if (scrollToTopRequestKey <= 0 || !isFloaterTaskHomeScreen) return@LaunchedEffect
        closeFloaterTaskHomeSearch()
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            listState.animateScrollToItem(index = 0, scrollOffset = 0)
        }
    }
    // The open row's task leaving the feed hands the slot back -- completed,
    // deleted, filtered out, search re-scoped.
    //
    // Read through `snapshotFlow` rather than as an effect key, and that is the
    // point of the holder rather than an incidental style: `LaunchedEffect(…,
    // swipeSlot.openId)` would be a `MutableState` read inside this composable's
    // body, which invalidates it. This screen is ~6000 lines including the whole
    // LazyColumn content lambda, so every open and every close used to recompose
    // all of it. A read inside a coroutine registers no such dependency.
    LaunchedEffect(uiState.items, swipeSlot) {
        snapshotFlow { swipeSlot.openId }.collect { openId ->
            if (openId != null && uiState.items.none { it.id == openId }) {
                swipeSlot.openId = null
            }
        }
    }
    // A scroll closes the row, at the moment the list starts moving.
    //
    // A deliberate divergence from `tdayClosesSearchOnOutsideTap`, which ignores
    // scrolls on purpose -- and the difference is what each thing *is*. The
    // search field is chrome: pinned to the viewport, staying put while the page
    // moves under it, so a flick that leaves it alone is right. An open row is
    // content. It travels with the list, so a row that stayed open through a
    // scroll would put an armed Delete pill under a thumb now aimed at a
    // different task, while the surface is still moving. That is a mis-tap
    // generator, and the one case where persisting is worse than going away.
    //
    // Scroll *start* rather than scroll end, for the same reason: the decision
    // belongs to the moment the list begins to move, not to wherever a fling
    // happens to stop.
    //
    // It also covers the case an outside-tap test cannot see at all -- a
    // vertical drag that begins on the open row itself, which is the likeliest
    // scroll of the lot because the hand is already there.
    //
    // The `!= null` guard is not decoration: without it this writes to the slot
    // on every scroll of every feed, whether or not anything is open.
    LaunchedEffect(listState, swipeSlot) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && swipeSlot.openId != null) swipeSlot.openId = null
        }
    }
    var lastHandledCreateTaskRequestKey by rememberSaveable { mutableStateOf(0) }
    var collapsedSectionKeys by rememberSaveable(uiState.mode, uiState.listId, highlightedTodoId) {
        mutableStateOf(
            if (isCollapsibleTimelineMode && highlightedTodoId.isNullOrBlank()) {
                setOf(EARLIER_SECTION_KEY)
            } else {
                emptySet()
            },
        )
    }
    // Requirement 2 + 3, generalized: this screen's own scope has nothing
    // pending, but Earlier is still holding overdue tasks, collapsed --
    // Today originally, now also Scheduled/Priority/All/List via
    // `scopeHasEarlierItems`/`scopeItemsEmpty` above. Independent of
    // `celebrateEmptyState` — this only decides whether the scene is shown
    // inline (so Earlier's header stays reachable) versus not at all; it
    // never gates the burst.
    //
    // A function rather than an expression, and a shorter one than it was:
    // `earlierExpandPending` used to be the last term here, held true for one
    // exit beat by `onTimelineSectionHeaderToggle` below. That beat is gone
    // with the slot contention that produced it — see [shouldShowEarlierScene]
    // for the whole argument, and for why the term's absence from the
    // signature is the part that matters.
    val showEarlierIllustration = shouldShowEarlierScene(
        scopeHasEarlierItems = scopeHasEarlierItems,
        scopeItemsEmpty = scopeItemsEmpty,
        isLoading = uiState.isLoading,
        suppressInitialTimeline = suppressInitialTodayTimeline,
        scopedSearchActive = scopedSearchActive,
        earlierCollapsed = collapsedSectionKeys.contains(EARLIER_SECTION_KEY),
    )
    // Requirement 1's gap for the case above's mirror: Earlier is already
    // expanded (not collapsed) at the moment the user's own tap -- or a
    // remote completion -- empties this scope. `showEarlierIllustration`
    // stays false on purpose whenever Earlier is expanded (requirement 3:
    // Earlier's own rows own this slot then, not the scene), so without
    // this, a completion landing in that state drew neither the scene nor
    // the full-screen overlay below (which also defers to
    // `scopeHasEarlierItems`) -- no illustration at all, and no confetti.
    // Scoped to `celebrateEmptyState`'s own window rather than shown for as
    // long as Earlier stays expanded and empty: once the celebration times
    // out nothing here contests Earlier's slot again. What the expiry leaves
    // behind is the ordinary collapsed presentation rather than the open list,
    // because the fold below had to shut Earlier for the burst to run at all --
    // argued at [shouldFoldEarlierForCelebration], not hidden here.
    //
    // This is the one case the new order charges for. The scene it mounts is
    // emitted at the FOOT of Earlier's rows, so with Earlier already open the
    // charge is not a worse position but no burst at all -- see
    // [shouldFoldEarlierForCelebration] immediately below, which is what pays
    // it, and the scene's own build site for the cost of each alternative.
    val showEarlierExpandedCelebration = shouldShowTodayEarlierExpandedCelebration(
        todayHasEarlierItems = scopeHasEarlierItems,
        itemsEmpty = scopeItemsEmpty,
        isLoading = uiState.isLoading,
        suppressInitialTodayTimeline = suppressInitialTodayTimeline,
        scopedSearchActive = scopedSearchActive,
        earlierCollapsed = collapsedSectionKeys.contains(EARLIER_SECTION_KEY),
        celebrateEmptyState = celebrateEmptyState,
    )
    // ...and the half of that charge this screen refuses to pay. The scene
    // being at the foot of the open list is a position; the burst never
    // running is a missing feature, and the two were the same sentence until
    // someone counted the rows. A `LazyColumn` composes what it reaches, and
    // `TdayConfetti` starts its flight from a `LaunchedEffect` inside the
    // scene -- so an expanded Earlier tall enough to push the scene past the
    // fold does not delay the burst, it deletes it, and the four-second window
    // expires and unmounts the item before any scroll could bring it back.
    // [shouldFoldEarlierForCelebration] carries the whole argument, including
    // what folding the list costs and why the two alternatives cost more.
    //
    // The stamp, not the flag, is the key: the completion this fold belongs to,
    // taken as the later of the local tap and the remote emptying because
    // `shouldCelebrateEmptyState` opens its window for either. Folding is a
    // state write that makes its own trigger false, so one fold per completion
    // is the only shape that does not fight a user who taps Earlier back open.
    val celebrationStampMs = maxOf(lastCompletionAtMs, uiState.remoteEmptiedAtMs)
    var earlierFoldedForCelebrationMs by remember { mutableLongStateOf(0L) }
    val foldEarlierForCelebration = shouldFoldEarlierForCelebration(
        showEarlierExpandedCelebration = showEarlierExpandedCelebration,
        celebrationStampMs = celebrationStampMs,
        foldedForStampMs = earlierFoldedForCelebrationMs,
    )
    // No delay inside, deliberately: this is a state assignment, not a beat.
    // The rows leave on `feedItemMotion` and the scene arrives on its own enter,
    // both begun by this one write, and with motion off both are simply drawn
    // finished on the next frame -- nothing here to skip, and nothing to
    // survive as a dead wait.
    LaunchedEffect(foldEarlierForCelebration, celebrationStampMs) {
        if (!foldEarlierForCelebration) return@LaunchedEffect
        earlierFoldedForCelebrationMs = celebrationStampMs
        collapsedSectionKeys = collapsedSectionKeys + EARLIER_SECTION_KEY
    }
    // The flat feed's placeholder, and how long its lazy item outlives it. Both
    // hoisted because `LazyListScope` is not a composition — by the time the list
    // builds itself its guard has to already be a plain Boolean.
    val taskFeedSkeletonVisible = !showSectionedTimeline &&
            uiState.items.isEmpty() &&
            uiState.isLoading
    val taskFeedSkeletonMounted = rememberTdayTaskRowSkeletonMounted(taskFeedSkeletonVisible)
    // The Anytime home's inline scene, hoisted for the reason the placeholder
    // above it is: `LazyListScope` is not a composition, so both its visibility
    // and the transition that plays it have to be settled before the list builds
    // itself. See [shouldMountFloaterEmptyScene] for why those are two values.
    val floaterEmptySceneVisible = shouldShowFloaterEmptyScene(
        isFloaterTaskHomeScreen = isFloaterTaskHomeScreen,
        itemsEmpty = uiState.items.isEmpty(),
        isLoading = uiState.isLoading,
    )
    // Seeded from the live value rather than from `false`, and keyed by scope,
    // for `earlierSceneTransition`'s reasons exactly: arriving at a feed that is
    // already empty is a cold entry and not a transition, and a state carried
    // across a scope change would hold an item alive on a feed it does not
    // belong to.
    val floaterEmptySceneTransition = remember(uiState.mode, uiState.listId) {
        MutableTransitionState(floaterEmptySceneVisible)
    }
    floaterEmptySceneTransition.targetState = floaterEmptySceneVisible
    // `currentState` is read HERE, in the composition that owns the list, and
    // that is what makes the item's removal a recomposition rather than a thing
    // nobody observes: the exit finishing flips this, this rebuilds the
    // `LazyColumn` content lambda, and the item goes.
    val floaterEmptySceneMounted = shouldMountFloaterEmptyScene(
        sceneVisible = floaterEmptySceneVisible,
        sceneStillDrawn = floaterEmptySceneTransition.currentState,
    )
    // The scene itself, built here and handed to [floaterTaskHomeRootFeedContent]
    // to emit — the same shape `earlierSceneContent` below takes, and taken for
    // the same reason plus one of its own. `LazyListScope` is not a composition,
    // so the transition state above cannot be remembered down there; and a guard
    // whose state is remembered inside it is re-seeded on every mount, which is
    // a transition with nothing to animate from.
    //
    // Nullable, and the `if` is the item's mount guard. Deliberately wider than
    // the scene's own visibility: see [shouldMountFloaterEmptyScene] for what
    // holds it open and why an item removed by its guard has no exit left.
    val floaterEmptySceneContent: (LazyListScope.() -> Unit)? = if (floaterEmptySceneMounted) {
        {
            // Mirrors the web layout: the scene sits in a gap in
            // the middle of the screen with the list names below
            // it, rather than in a full-screen watermark overlay.
            item(
                key = "floater-empty-message",
                contentType = "floater-empty-message",
            ) {
                // The preference, not the feed's first-frame guard:
                // the exit below is paint the user can ask not to
                // see, and Phase 8's plumbing is where that answer
                // comes from rather than a second read of the OS.
                val sceneAnimates = rememberTdayMotionEnabled()
                AnimatedVisibility(
                    // `visibleState` and not a plain `visible =`,
                    // though not for the enter's sake the way
                    // Earlier's scene needs it. This state is
                    // remembered ABOVE the item's guard so that the
                    // guard can read its `currentState` and keep
                    // the item alive until the exit has finished
                    // with it; a boolean here would live and die
                    // with the item it sits inside.
                    visibleState = floaterEmptySceneTransition,
                    // No enter, which is the one thing about this
                    // scene that does not change. The scene inside
                    // runs its own entrance on the confetti's
                    // clock, and a host fade layered over it would
                    // also dim the burst during the frames it is
                    // meant to lead at full opacity.
                    enter = EnterTransition.None,
                    // The exit is new, and it is the reported bug's
                    // second half. The comment that stood here said
                    // this scene is only ever removed outright, and
                    // argued it: a 42%-tall illustration fading out
                    // over a task row arriving in the same slot
                    // paints the empty state on top of the thing
                    // that disproves it. That was right while the
                    // burst inside cut on the same frame. It is not
                    // right now the burst FADES — the paper keeps
                    // flying while its own envelope takes it away
                    // over `Quick`, and dropping the item drops the
                    // canvas that envelope is painting into, which
                    // is the same complaint one layer up. So the
                    // scene leaves on the envelope's own rung and
                    // the two go together, exactly as the
                    // full-screen overlay one branch over now does.
                    //
                    // Fade AND shrink, where that overlay fades
                    // alone, and the difference is layout: the
                    // overlay is drawn over a page and owes the
                    // feed nothing, while this holds ~42% of the
                    // screen that the tile and the list rows below
                    // are waiting to have back. Fading its ink
                    // while holding its track and then dropping the
                    // track in the frame the node goes is the
                    // larger of the two movements and the jump the
                    // whole hand-off exists to remove. One duration
                    // and one curve across both — web's
                    // `.tday-empty-cancel-exit` closes its grid
                    // track on the same `Quick`/`Exit` pair, for
                    // the same reason. `shrinkTowards` is named for
                    // the reason Earlier's scene names it: the
                    // default `Bottom` offsets the content by
                    // `animatedHeight - fullHeight`, so the picture
                    // would slide UP by its own full height while
                    // the slot it lives in travels down.
                    //
                    // `ExitTransition.None` with motion off, where
                    // the burst is unmounting on the same frame for
                    // the same reason and there is nothing left to
                    // keep alive for — no trip, and no wait left
                    // standing in front of the restored row.
                    exit = if (sceneAnimates) {
                        fadeOut(
                            animationSpec = tween(
                                durationMillis = TdayMotionTokens.Durations.Quick,
                                easing = TdayMotionTokens.Easings.Exit,
                            ),
                        ) + shrinkVertically(
                            animationSpec = tween(
                                durationMillis = TdayMotionTokens.Durations.Quick,
                                easing = TdayMotionTokens.Easings.Exit,
                            ),
                            shrinkTowards = Alignment.Top,
                        )
                    } else {
                        ExitTransition.None
                    },
                    // Moved up off the `Box` along with the
                    // wrapper: a displaced item takes its placement
                    // spec on the item's own root, and the root is
                    // this now.
                    modifier = displacedFeedItemMotion(timelineAnimationsEnabled),
                ) {
                    val gapHeight = (LocalConfiguration.current.screenHeightDp * 0.42f).dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = gapHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        TdayEmptyState(
                            icon = emptySceneIcon,
                            accentColor = titleColor,
                            title = emptyStateMessageForMode(
                                mode = uiState.mode,
                                isFloaterList = isListDetailScreen,
                            ),
                            description = emptyStateDescriptionForMode(
                                mode = uiState.mode,
                                isFloaterList = isListDetailScreen,
                            ),
                            celebrate = celebrateEmptyState,
                            // The overlay callers below pass nothing:
                            // they draw over a page where nothing is
                            // moving, so the burst can own the frame
                            // the feed empties on. Here the scene is
                            // inline, and the tile and list rows under
                            // it are still gliding down into the space
                            // it just claimed. Hold the celebration for
                            // exactly that glide, so the paper flies
                            // over a settled screen — which is the
                            // whole of what makes the list screen's
                            // version read as smooth.
                            celebrationStartDelayMillis =
                                TdayFeedItemMotion.CelebrationStartDelayMillis,
                        )
                    }
                }
            }
        }
    } else {
        null
    }
    // The scene item's own mount guard, deliberately wider than either
    // visibility flag above: it drops Earlier's collapse state and the
    // celebration term, so the item outlives the moment its content stops
    // being visible and the `AnimatedVisibility` inside it has somewhere to
    // play its exit from. An item its guard has already removed has no exit
    // left.
    val earlierScenePresent = scopeHasEarlierItems &&
            scopeItemsEmpty &&
            !uiState.isLoading &&
            !suppressInitialTodayTimeline &&
            !scopedSearchActive
    // The scene's own visibility, and the transition that plays it, hoisted out of the
    // `item {}` that draws it.
    //
    // It has to live up here because `earlierScenePresent` above is implied by
    // `showEarlierIllustration`: completing the last task in scope turns both true on the
    // same frame, so the item is created at the exact moment the scene should be appearing.
    // An `AnimatedVisibility(visible = …)` inside it would therefore enter composition with
    // initialState == targetState and skip its enter outright — the 190 ms fade + expand
    // below was never once seen, and 34 % of the screen claimed its slot in one jump,
    // shoving everything under it down with it. A transition state remembered out here
    // outlives the item's mount, so it still holds the "not visible yet" the enter needs
    // to animate from.
    //
    // Seeded from the live value rather than from `false`, deliberately: seeding false
    // would also animate the scene in on every cold entry into an already-finished Today,
    // which is motion nobody asked for. Keyed by scope for the same reason — arriving at a
    // mode or list that is already empty is a cold entry too, not a transition.
    val earlierSceneVisible = showEarlierIllustration || showEarlierExpandedCelebration
    val earlierSceneTransition = remember(uiState.mode, uiState.listId) {
        MutableTransitionState(earlierSceneVisible)
    }
    earlierSceneTransition.targetState = earlierSceneVisible
    // The scene itself, built here and handed to [sectionedTimelineContent] to
    // emit — see [earlierSceneFollowsSection] for where it lands and why:
    // immediately after Earlier's own rows, from inside that loop, rather than
    // above the whole call the way it used to be.
    //
    // A `LazyListScope` lambda rather than eight more parameters on a function
    // that already takes thirty. Everything the scene needs — the scope's icon
    // and copy, the accent, the celebration flag, the hoisted transition state —
    // is derived here and is nothing `sectionedTimelineContent` otherwise knows
    // or should learn. What that function gets instead is one question it can
    // answer on its own: does the scene follow THIS section?
    //
    // Nullable, and the `if` is the item's mount guard. Mounted for as long as
    // the scope reads empty-with-overdue regardless of Earlier's collapse state,
    // so the scene can still play its exit on the frame the user expands
    // Earlier; `AnimatedVisibility` inside is what actually shows and hides it.
    //
    // WHAT THE NEW ORDER BUYS, because it inverts an order this file argued for.
    // The comment that stood here said the scene was "placed right above
    // `sectionedTimelineContent` below so it occupies the slot the scope's own
    // sections would otherwise fill ... and the (always-present, real) Earlier
    // header sits right under it — reachable the whole time, never covered by an
    // overlay the way the plain-empty scene is". Half of that survives and gets
    // stronger. The header is still never under the full-screen overlay (that
    // deferral is untouched), and it is now the first thing under the hero
    // instead of the thing a third of a screen below it — the strongest form of
    // the reachability claim this file has been able to make. The half that does
    // not survive is the slot argument: the scene no longer occupies the slot the
    // scope's sections would have filled, it occupies the slot Earlier's rows
    // grow INTO, and what earns it that slot is being the thing they push down. A
    // header someone has just tapped should still be under their finger once the
    // list it opens has opened, and with the scene above it that was never true.
    //
    // WHAT IT COSTS, stated here rather than left to be discovered.
    // `showEarlierExpandedCelebration` — a completion that empties the scope
    // while Earlier is ALREADY expanded — wants this scene, and `TdayConfetti`
    // with it (the burst is `matchParentSize()` inside `TdayEmptyState`, so it
    // goes wherever the scene goes). Under the new order the scene's one home is
    // at the foot of Earlier's rows, and with those rows on screen that is a few
    // hundred dp down: half clipped past six or so of them, past the fold
    // entirely past eleven. Off screen would have been survivable. It is worse
    // than off screen — a `LazyColumn` never composes an item it has not
    // reached, and the burst is started by a `LaunchedEffect` inside the item,
    // so the celebration does not land late, it does not happen, and the
    // four-second window closes and unmounts the scene before any scroll could
    // fix it.
    //
    // So the rows move for the celebration, not the scene:
    // [shouldFoldEarlierForCelebration] above folds Earlier shut for that one
    // completion and the scene rises into the slot directly under a header that
    // still has not moved. One home for the scene, always — the celebration
    // follows the scene, and where the scene cannot be seen it is the feed
    // around it that gives way. The two ways of chasing the celebration instead
    // are both rejected, and the reasons are written out at that function: a
    // second mount point above the header reintroduces the screen-height header
    // jump this change exists to remove and makes the scene hop across the
    // header for anyone who expands mid-celebration; an `animateScrollToItem`
    // onto the scene drags the viewport past every overdue row and leaves it
    // parked there.
    val earlierSceneContent: (LazyListScope.() -> Unit)? = if (earlierScenePresent) {
        {
            item(
                key = "today-earlier-empty-scene",
                contentType = "today-earlier-empty-scene",
            ) {
                // `timelineAnimationsEnabled` is the feed's first-frame guard,
                // not the preference, so the scene asks the preference itself —
                // the same call the task-feed skeleton above it already makes.
                // See [earlierSceneAnimatesHandoff] for what motion-off draws.
                val sceneAnimates = earlierSceneAnimatesHandoff(
                    timelineAnimationsEnabled = timelineAnimationsEnabled,
                    motionEnabled = rememberTdayMotionEnabled(),
                )
                AnimatedVisibility(
                    // `earlierSceneTransition`, not a plain `visible =`: this
                    // item is mounted by a guard that the visibility implies,
                    // so a boolean here would arrive already true and the
                    // enter below would never play. See where the state is
                    // remembered, above the guard, for the whole story.
                    visibleState = earlierSceneTransition,
                    // Fade AND expand: the mirror of exit's fade + shrink
                    // below, so the scene's arrival reads as the same one
                    // motion running backwards instead of an alpha fade over a
                    // size that has already snapped to full height. Before
                    // this, `enter` was fade-only -- the Box's
                    // `heightIn(min = gapHeight)` claimed its ~34%-of-screen
                    // slot on the very first frame, shoving everything under it
                    // down in one jump while only the alpha eased in on top of
                    // that jump.
                    //
                    // `expandFrom` is named rather than left to the default,
                    // because the default is the wrong one here and it is the
                    // kind of wrong that only shows up on a device. Both
                    // `expandVertically` and `shrinkVertically` default to
                    // `Alignment.Bottom`, which pins the CONTENT to the bottom
                    // edge of the animating box and offsets it by
                    // `animatedHeight - fullHeight` -- which over an expand
                    // runs -H -> 0. The box grows downward while the picture
                    // inside it starts a full height ABOVE the box's top edge
                    // and slides down into it, so what the first frames show is
                    // the bottom of the copy and the illustration at the top of
                    // the Box is the last thing to arrive. Anchored to `Top`
                    // that offset is a flat zero for the whole run: the scene is
                    // painted at the item's top edge from the first frame and
                    // the box simply reveals more of it downward. See `exit` for
                    // why the same alignment matters more on the way out.
                    enter = if (sceneAnimates) {
                        fadeIn(
                            animationSpec = tween(
                                durationMillis = TdayFeedItemMotion.FadeInMillis,
                                easing = FastOutSlowInEasing,
                            ),
                        ) + expandVertically(
                            animationSpec = tween(
                                durationMillis = TdayFeedItemMotion.FadeInMillis,
                                easing = FastOutSlowInEasing,
                            ),
                            expandFrom = Alignment.Top,
                        )
                    } else {
                        EnterTransition.None
                    },
                    // Fade AND shrink. The floater home's inline scene now
                    // leaves over the same pair (see
                    // [shouldMountFloaterEmptyScene]), but for the other of the
                    // two reasons a scene leaves: that one goes because the
                    // feed REFILLED under it and the burst it holds is fading,
                    // this one because a user tap asked for the slot while the
                    // scope is still empty -- so that one rides `Quick`, the
                    // envelope's own rung, and this one rides the hand-off it
                    // leads. It is not racing anything while it does: the rows
                    // arriving above it are what carry it down, and this fade
                    // is the paint half of the same one motion.
                    //
                    // `shrinkTowards = Alignment.Top` for the reason `enter`
                    // names `expandFrom`, and on this leg it decides whether
                    // the change does what was asked for at all. The default
                    // `Alignment.Bottom` offsets the content by
                    // `animatedHeight - fullHeight`, which over the shrink runs
                    // 0 -> -H: the picture slides UP by its own full height
                    // while the slot it lives in travels down. H here is the
                    // Box's `gapHeight`, ~34% of the screen -- ~272dp on an
                    // 800dp device -- and it is spent inside `FadeOutMillis`,
                    // 150ms, while `Placement` has only eased about 0.73 of the
                    // rows' total height in the same 150ms of its own 320. Three
                    // overdue rows is ~190dp inserted against ~272dp of content
                    // sliding the other way; the upward term wins outright until
                    // Earlier is holding six or so rows. So the pixels the user
                    // watches would go UP for the whole time the scene is still
                    // painted, on a screen whose entire point is that the image
                    // goes DOWN and fades as the list expands. Anchored to `Top`
                    // there is no cancelling term left at all: the content sits
                    // at the item's top edge, the box clips it from the bottom,
                    // and the only translation on it is the `Placement` below
                    // carrying it down behind the arriving rows.
                    exit = if (sceneAnimates) {
                        fadeOut(
                            animationSpec = tween(
                                durationMillis = TdayFeedItemMotion.FadeOutMillis,
                                easing = FastOutSlowInEasing,
                            ),
                        ) + shrinkVertically(
                            animationSpec = tween(
                                durationMillis = TdayFeedItemMotion.FadeOutMillis,
                                easing = FastOutSlowInEasing,
                            ),
                            shrinkTowards = Alignment.Top,
                        )
                    } else {
                        ExitTransition.None
                    },
                    // The geometry half, and the reason the scene rather than
                    // the header is the thing that travels now. Earlier's rows
                    // inserting above this item move it, and a moved item takes
                    // [TdayFeedItemMotion.Placement] — Emphasis, because this is
                    // position changing — while the fade above stays on the
                    // shorter paint rungs. Under the old order this modifier
                    // only ever carried the scene into the slot the vanishing
                    // time-of-day sections left; it now also carries it down as
                    // the overdue list opens, which is the motion that was
                    // actually asked for.
                    modifier = displacedFeedItemMotion(sceneAnimates),
                ) {
                    val gapHeight =
                        (LocalConfiguration.current.screenHeightDp * 0.34f).dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = gapHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        TdayEmptyState(
                            icon = emptyStateSceneIconRes,
                            accentColor = titleColor,
                            title = emptyStateSceneTitle,
                            description = emptyStateSceneDescription,
                            celebrate = celebrateEmptyState,
                            // Mirrors the floater home: hold the burst back for
                            // exactly as long as this item's own placement spec
                            // takes, so it lands once the feed has finished
                            // settling underneath it. What that settling IS
                            // changed with the order. It used to be the
                            // now-shorter Morning/Afternoon/Tonight sections
                            // easing out of the way and the Earlier header BELOW
                            // finishing its slide up into place. The header does
                            // not move at all any more; what the hold covers now
                            // is this item's own travel, on the very
                            // `TdayFeedItemMotion.Placement` clock the delay is
                            // measured from. Same number, different journey —
                            // and it is still the travel that precedes the
                            // burst, which is the only thing the number was ever
                            // timed against.
                            celebrationStartDelayMillis =
                                TdayFeedItemMotion.CelebrationStartDelayMillis,
                            // This scene already sits inside the
                            // `AnimatedVisibility` above, which owns its fade +
                            // size now. Running `TdayEmptyState`'s own 520ms
                            // rise on top of that too is a second,
                            // uncoordinated animation racing the first one --
                            // the double-animation stutter this hand-off cannot
                            // have. The celebrating case is the one exception:
                            // `celebrationStartDelayMillis` (plus
                            // `TdayEmptyState`'s own lead) holds this rise back
                            // well past the 190ms the wrapper above takes to
                            // finish its own fade, so the two never actually
                            // overlap there and the confetti-leads-the-scene
                            // choreography is worth keeping.
                            animateAppearance = celebrateEmptyState,
                        )
                    }
                }
            }
        }
    } else {
        null
    }
    var flashTodoId by remember(uiState.mode) { mutableStateOf<String?>(null) }
    var quickAddDueEpochMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var editTargetTodoId by rememberSaveable { mutableStateOf<String?>(null) }
    var promoteTargetTodoId by rememberSaveable { mutableStateOf<String?>(null) }
    var deferTargetTodoId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeDropSectionKey by remember(uiState.mode) { mutableStateOf<String?>(null) }
    var activeTimelineDrag by remember(uiState.mode) { mutableStateOf<TimelineInAppDrag?>(null) }
    var timelineDragContainerOrigin by remember(uiState.mode) { mutableStateOf(Offset.Zero) }
    val timelineDropTargetBounds =
        remember(uiState.mode) { mutableStateMapOf<String, TimelineDropTargetBounds>() }
    var pendingRescheduleDrop by remember(uiState.mode) { mutableStateOf<TaskRescheduleDrop?>(null) }
    LaunchedEffect(createTaskRequestKey) {
        if (createTaskRequestKey > 0 && createTaskRequestKey != lastHandledCreateTaskRequestKey) {
            lastHandledCreateTaskRequestKey = createTaskRequestKey
            onCreateTaskRequestHandled(createTaskRequestKey)
            closeFloaterTaskHomeSearch()
            quickAddDueEpochMs = null
            showCreateTaskSheet = true
        }
    }
    LaunchedEffect(openCreateTaskOnStart) {
        if (openCreateTaskOnStart) {
            closeFloaterTaskHomeSearch()
            quickAddDueEpochMs = null
            showCreateTaskSheet = true
        }
    }
    BackHandler(enabled = floaterTaskHomeSearchExpanded) {
        closeFloaterTaskHomeSearch()
    }
    BackHandler(enabled = exitToLauncherOnBack && !showCreateTaskSheet && !floaterTaskHomeSearchExpanded) {
        onBack()
    }
    // Registered last so back dismisses the field before it leaves the list.
    BackHandler(enabled = showScopedSearchField) {
        closeScopedSearch()
    }
    // Later registration wins in the back dispatcher, so this sits after the
    // exit-to-launcher handler: back cancels the selection before it can leave
    // the screen (or the app).
    BackHandler(enabled = selectionActive) {
        exitSelection()
    }
    // Last of the five, and that placement is the behaviour: later registration
    // wins, so a revealed row is the innermost state back can be in and the
    // first one it undoes. `!selectionActive` keeps it from outranking the
    // handler directly above -- see `TaskSwipeSlotBackHandler`.
    TaskSwipeSlotBackHandler(slot = swipeSlot, enabled = !selectionActive)
    LaunchedEffect(isFloaterTaskHomeScreen, floaterTaskHomeSearchExpanded) {
        if (!isFloaterTaskHomeScreen) {
            closeFloaterTaskHomeSearch()
            onRootControlsVisibleChange(true)
        } else {
            onRootControlsVisibleChange(!floaterTaskHomeSearchExpanded)
        }
    }
    var showListSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showMembersSheet by rememberSaveable { mutableStateOf(false) }
    var showCreateListSheet by rememberSaveable { mutableStateOf(false) }
    var showDeleteListConfirmation by rememberSaveable { mutableStateOf(false) }
    var showSummarySheet by rememberSaveable(uiState.mode) { mutableStateOf(false) }
    var listSettingsTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var listSettingsName by rememberSaveable { mutableStateOf("") }
    var listSettingsColor by rememberSaveable { mutableStateOf(TDAY_DEFAULT_LIST_COLOR_KEY) }
    var listSettingsIconKey by rememberSaveable { mutableStateOf(TDAY_DEFAULT_LIST_ICON_KEY) }
    var listSettingsColorTouched by rememberSaveable { mutableStateOf(false) }
    var listSettingsIconTouched by rememberSaveable { mutableStateOf(false) }
    var createListName by rememberSaveable { mutableStateOf("") }
    var createListColor by rememberSaveable { mutableStateOf(TDAY_DEFAULT_LIST_COLOR_KEY) }
    var createListIconKey by rememberSaveable { mutableStateOf(TDAY_DEFAULT_LIST_ICON_KEY) }
    val fabInteractionSource = remember { MutableInteractionSource() }
    val editTargetTodo = rememberEditSheetTarget(
        id = editTargetTodoId,
        current = remember(editTargetTodoId, uiState.items) {
            editTargetTodoId?.let { targetId -> uiState.items.firstOrNull { it.id == targetId } }
        },
    )
    val draggedScheduledTodo =
        remember(draggedScheduledTodoId, uiState.items, uiState.earlierItems) {
            draggedTimelineTodo(
                draggedTodoId = draggedScheduledTodoId,
                items = uiState.items,
                earlierItems = uiState.earlierItems,
            )
        }
    val requestTaskReschedule: (TodoItem, LocalDate) -> Unit =
        requestTaskReschedule@{ todo, targetDate ->
        draggedScheduledTodoId = null
        activeDropSectionKey = null
        activeTimelineDrag = null
        timelineDropTargetBounds.clear()
            val currentDue = todo.due ?: return@requestTaskReschedule
            val currentDate = LocalDate.ofInstant(currentDue, zoneId)
        if (currentDate != targetDate) {
            TdayHaptics.dragDrop(view)
            if (todo.isRecurring) {
                pendingRescheduleDrop = TaskRescheduleDrop(todo = todo, targetDate = targetDate)
            } else {
                onMoveTask(todo, targetDate, TaskRescheduleScope.OCCURRENCE)
            }
        }
    }
    // Today screen: drop onto a Morning / Afternoon / Tonight bucket sets the time
    // of day (date unchanged). Sibling of requestTaskReschedule.
    val requestTaskRescheduleTime: (TodoItem, Int) -> Unit =
        requestTaskRescheduleTime@{ todo, hour ->
            draggedScheduledTodoId = null
            activeDropSectionKey = null
            activeTimelineDrag = null
            timelineDropTargetBounds.clear()
            val currentDue = todo.due ?: return@requestTaskRescheduleTime
            val movedDue = ZonedDateTime.of(
                LocalDate.ofInstant(currentDue, zoneId),
                LocalTime.of(hour, 0),
                zoneId,
            ).toInstant()
            if (movedDue != currentDue) {
                TdayHaptics.dragDrop(view)
                if (todo.isRecurring) {
                    pendingRescheduleDrop = TaskRescheduleDrop(todo = todo, targetHour = hour)
                } else {
                    onMoveTaskToTimeOfDay(todo, hour, TaskRescheduleScope.OCCURRENCE)
                }
            }
        }
    val canSummarizeCurrentMode =
        summaryAvailable &&
                uiState.aiSummaryEnabled &&
                uiState.items.isNotEmpty() &&
                // Hidden on the root floater screen; kept on the per-mode screens
                // (today/all/scheduled/etc.) and list detail.
                !isFloaterTaskHomeScreen
    // Morning Sweep: guided triage entry, only where there is something to
    // triage (recurring occurrences reschedule via the edit flow instead).
    val hasSweepableTasks = uiState.mode == TodoListMode.OVERDUE &&
        uiState.items.any { it.rrule.isNullOrBlank() && it.instanceDate == null }
    val topBarActions = listOfNotNull(
        if (canOpenScopedSearch) {
            TodoTopBarAction(
                icon = ImageVector.vectorResource(R.drawable.ic_lucide_search),
                contentDescription = stringResource(R.string.action_search),
                // Only opens: the bar hands its row over to the field, so this
                // button is not on screen to be tapped again. The field's own
                // close button is what comes back from there.
                onClick = {
                    scopedSearchExpanded = true
                    scopedSearchNeedsFocus = true
                },
            )
        } else {
            null
        },
        if (hasSweepableTasks) {
            TodoTopBarAction(
                icon = ImageVector.vectorResource(R.drawable.ic_lucide_sun),
                contentDescription = stringResource(R.string.sweep_title),
                onClick = onOpenMorningSweep,
            )
        } else {
            null
        },
        if (canSummarizeCurrentMode) {
            TodoTopBarAction(
                icon = ImageVector.vectorResource(R.drawable.ic_lucide_sparkles),
                contentDescription = stringResource(R.string.todos_summarize),
                onClick = { showSummarySheet = true },
            )
        } else {
            null
        },
        if (canSelectTasks) {
            // An explicit button, never long-press: long-press already starts
            // drag-to-reschedule on five of the seven list modes, and taking it
            // would be a gesture-arbitration bug nobody could try before it
            // shipped.
            TodoTopBarAction(
                icon = ImageVector.vectorResource(R.drawable.ic_lucide_circle_check_big),
                contentDescription = stringResource(R.string.bulk_select),
                onClick = {
                    closeScopedSearch()
                    swipeSlot.openId = null
                    selectedTodoIds = emptySet()
                    selectionActive = true
                },
            )
        } else {
            null
        },
        if (isListDetailScreen && selectedList != null) {
            // One entry point per role: owners get list settings (which hosts
            // the Sharing section); members go straight to the members sheet.
            TodoTopBarAction(
                icon = ImageVector.vectorResource(R.drawable.ic_lucide_ellipsis),
                contentDescription = stringResource(R.string.action_more_options),
                onClick = {
                    if (!selectedList.isOwner) {
                        showMembersSheet = true
                    } else {
                        listSettingsTargetId = selectedList.id
                        listSettingsName = selectedList.name
                        listSettingsColor = normalizeTdayListColorKey(selectedList.color)
                        listSettingsIconKey = selectedList.iconKey
                            ?.takeIf { isTdayListIconKeySupported(it) }
                            ?: TDAY_DEFAULT_LIST_ICON_KEY
                        listSettingsColorTouched = false
                        listSettingsIconTouched = false
                        showListSettingsSheet = true
                    }
                },
            )
        } else {
            null
        },
    )
    val timelineItemSpacing = TimelineDateGroupSpacing
    fun highlightedTodoListTarget(todoId: String): Pair<Int, String>? {
        // Starts at 1: the hero block holds index 0 on this path, so every row
        // below it is one further down than the sections alone would say.
        var itemIndex = if (usesTodayStyle && !usesRootFeedChrome) 1 else 0
        timelineSections.forEach { section ->
            itemIndex += 1
            val todoIndex = section.items.indexOfFirst { item ->
                item.id == todoId || item.canonicalId == todoId
            }
            if (todoIndex >= 0) {
                val todo = section.items[todoIndex]
                return itemIndex + todoIndex to "timeline-todo-${section.key}-${todo.id}"
            }
            itemIndex += section.items.size
        }
        return null
    }
    fun floaterTaskHomeTodoListTarget(todoId: String): Pair<Int, String>? {
        var itemIndex = 1 // Root Floater header row.
        timelineSections.forEach { section ->
            val todoIndex = section.items.indexOfFirst { item ->
                item.id == todoId || item.canonicalId == todoId
            }
            if (todoIndex >= 0) {
                val todo = section.items[todoIndex]
                return itemIndex + todoIndex to "timeline-todo-${section.key}-${todo.id}"
            }
            itemIndex += section.items.size
        }
        return null
    }
    LaunchedEffect(showSummarySheet, canSummarizeCurrentMode) {
        if (showSummarySheet && canSummarizeCurrentMode) {
            onSummarize()
        }
    }
    LaunchedEffect(highlightedTodoId, uiState.mode, timelineSections) {
        if (uiState.mode != TodoListMode.ALL || highlightedTodoId.isNullOrBlank()) return@LaunchedEffect
        val target = highlightedTodoListTarget(highlightedTodoId)
        if (target != null) {
            // The one settle that is genuinely waiting on a route change: this effect
            // runs because the screen was navigated to with a row to highlight.
            scaledDelay(SEARCH_RESULT_NAV_SETTLE_DELAY_MS, navSettleMotionScale)
            val viewportHeight =
                listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
            val estimatedRowHeight =
                with(density) { SEARCH_RESULT_ESTIMATED_ROW_HEIGHT_DP.dp.toPx().toInt() }
            val centeredScrollOffset =
                -((viewportHeight - estimatedRowHeight).coerceAtLeast(0) / 2)
            listState.animateSearchResultScrollToItem(
                targetIndex = target.first,
                targetKey = target.second,
                centeredScrollOffset = centeredScrollOffset,
                estimatedItemSizePx = estimatedRowHeight,
            )
            flashTodoId = highlightedTodoId
            scaledDelay(SEARCH_RESULT_FLASH_HOLD_MS, motionScale)
            if (flashTodoId == highlightedTodoId) {
                flashTodoId = null
            }
        }
    }
    fun openFloaterTaskHomeSearchResult(todo: TodoItem) {
        closeFloaterTaskHomeSearch()
        val target = floaterTaskHomeTodoListTarget(todo.id) ?: return
        screenScope.launch {
            // Same constant, the other scale: nothing navigates here. What settles is
            // the feed closing over the results card, so the clock is the feed's.
            scaledDelay(SEARCH_RESULT_NAV_SETTLE_DELAY_MS, motionScale)
            val viewportHeight =
                listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
            val estimatedRowHeight =
                with(density) { SEARCH_RESULT_ESTIMATED_ROW_HEIGHT_DP.dp.toPx().toInt() }
            val centeredScrollOffset =
                -((viewportHeight - estimatedRowHeight).coerceAtLeast(0) / 2)
            listState.animateSearchResultScrollToItem(
                targetIndex = target.first,
                targetKey = target.second,
                centeredScrollOffset = centeredScrollOffset,
                estimatedItemSizePx = estimatedRowHeight,
            )
            flashTodoId = todo.id
            scaledDelay(SEARCH_RESULT_FLASH_HOLD_MS, motionScale)
            if (flashTodoId == todo.id || flashTodoId == todo.canonicalId) {
                flashTodoId = null
            }
        }
    }
    LaunchedEffect(uiState.mode) {
        if (uiState.mode == TodoListMode.PRIORITY ||
            uiState.mode == TodoListMode.LIST ||
            uiState.mode == TodoListMode.TODAY
        ) {
            collapsedSectionKeys = collapsedSectionKeys + EARLIER_SECTION_KEY
        }
    }
    LaunchedEffect(draggedScheduledTodoId) {
        if (draggedScheduledTodoId == null) {
            timelineDropTargetBounds.clear()
        }
    }

    // Read through a State rather than off the captured `val`. These helpers are
    // reached from the drag gesture's pointerInput lambda, which is keyed on
    // (todo.id, dragEnabled) — neither changes when a drag begins, so the running
    // gesture keeps invoking the closures built BEFORE `timelineDragActive`
    // flipped. That was harmless while the section list never moved during a
    // drag; now that empty buckets appear at drag start, a stale list has none of
    // them and a drop onto one resolved to null and silently did nothing.
    val latestTimelineSections by rememberUpdatedState(timelineSections)

    fun timelineSectionForKey(key: String): TodoSection? =
        latestTimelineSections.firstOrNull { section -> section.key == key }

    fun originSectionKeyFor(todo: TodoItem): String? {
        latestTimelineSections.firstOrNull { section ->
            section.items.any { item -> item.id == todo.id }
        }?.let { section ->
            return section.key
        }
        return timelineSections.firstOrNull { section ->
            section.items.any { item -> item.canonicalId == todo.canonicalId }
        }?.key
    }

    fun canDropTodoInTimelineSection(todo: TodoItem, section: TodoSection): Boolean {
        val targetDate = section.targetDate ?: return false
        if (originSectionKeyFor(todo) == section.key) return false
        val due = todo.due ?: return false
        // Today time-buckets: a same-day move between Morning / Afternoon /
        // Tonight (the same-section check above already blocks dropping in place).
        if (section.targetHour != null) return true
        return LocalDate.ofInstant(due, zoneId) != targetDate
    }

    fun timelineDropSectionKeyAt(position: Offset, todo: TodoItem): String? {
        return timelineDropTargetBounds.values
            .asSequence()
            .filter { target -> target.bounds.contains(position) }
            .mapNotNull { target ->
                val section = timelineSectionForKey(target.sectionKey) ?: return@mapNotNull null
                if (canDropTodoInTimelineSection(todo, section)) target else null
            }
            .minByOrNull { target -> target.bounds.height }
            ?.sectionKey
    }

    fun updateActiveTimelineDropTarget(position: Offset) {
        val todo = activeTimelineDrag?.todo ?: draggedScheduledTodo
        val nextSectionKey = todo?.let { timelineDropSectionKeyAt(position, it) }
        if (activeDropSectionKey != nextSectionKey) {
            activeDropSectionKey = nextSectionKey
        }
    }

    fun finishTimelineDrag(position: Offset?) {
        val drag = activeTimelineDrag
        val targetKey = position
            ?.let { dropPosition -> drag?.let { timelineDropSectionKeyAt(dropPosition, it.todo) } }
            ?: activeDropSectionKey
        val targetSection = targetKey
            ?.let(::timelineSectionForKey)
            ?.takeIf { section -> drag?.let { canDropTodoInTimelineSection(it.todo, section) } == true }
        activeTimelineDrag = null
        draggedScheduledTodoId = null
        activeDropSectionKey = null
        timelineDropTargetBounds.clear()
        if (drag != null && targetSection != null) {
            val hour = targetSection.targetHour
            if (hour != null) {
                requestTaskRescheduleTime(drag.todo, hour)
            } else if (targetSection.targetDate != null) {
                requestTaskReschedule(drag.todo, targetSection.targetDate)
            }
        }
    }

    // --- Sectioned timeline callbacks --------------------------------------
    // Named here, in TodoListScreen's own scope, rather than written inline
    // where they are used inside sectionedTimelineContent below: each one
    // closes over a `var ... by remember`/`by rememberSaveable` property
    // declared above, and a property delegate's get/set resolve against the
    // live backing state on every call regardless of where the closure that
    // holds it is invoked from. Moving the *mutation* itself into
    // sectionedTimelineContent — instead of moving only these already-bound
    // closures — would have been the TdayApp bug again: the write would
    // still compile, but it would land on a copy taken at the extracted
    // function's call site instead of the live state.
    val onTimelineSectionHeaderToggle: (key: String, wasCollapsed: Boolean) -> Unit =
        { key, wasCollapsed ->
            // One statement, and that is the change. Earlier used to be routed
            // through `decideSectionHeaderToggleAction`, which held the expand
            // back for one `TdayFeedItemMotion.FadeOutMillis` — and ignored any
            // second tap landing inside that window — so the empty-state scene
            // could finish leaving before Earlier's rows were released into the
            // slot it was giving up. Both went out with the slot contention that
            // produced them. The scene sits BELOW Earlier's rows now (see
            // [earlierSceneFollowsSection]), so the rows arrive BETWEEN the
            // header and the scene instead of into the scene's place, and the
            // two motions run together, in the same direction, off this one
            // assignment: the rows expand downward out of a header that does not
            // move while the scene is carried down on `Placement` and fades. The
            // beat has nothing left to cover, and a beat that covers nothing is
            // 150 ms of a header answering a tap by doing nothing — the fifth
            // idiom rule, and worse than usual here because the old
            // `scaledDelay` read the DEVICE animator scale, so the wait survived
            // in full for a user who had turned the app's own motion off.
            // Nothing needs to be skipped at motion-off now; there is nothing
            // left to skip.
            //
            // Web and iOS both still sequence this, deliberately, and the
            // divergence is written here so the next reader finds it rather than
            // discovering it: their scene is drawn OVER the list rather than in
            // it — web's `useEarlierExpandHandoff`, iOS's
            // `toggleEarlierSectionWithIllustrationHandoff` behind the
            // `EmptyStateReservedTopHeightPreferenceKey` overlay — so rows
            // expanding in would arrive underneath a scene still painting over
            // them. Android's is a lazy item, and once it is below the rows it
            // shares no pixels with them at all. Same requirement, different
            // geometry, and only the geometry decided the beat.
            collapsedSectionKeys = if (wasCollapsed) {
                collapsedSectionKeys - key
            } else {
                collapsedSectionKeys + key
            }
        }
    val onTimelineQuickAdd: (dueEpochMs: Long) -> Unit = { dueEpochMs ->
        quickAddDueEpochMs = dueEpochMs
        showCreateTaskSheet = true
    }
    val onTimelineEditRequested: (todoId: String) -> Unit = { todoId ->
        editTargetTodoId = todoId
    }
    val onTimelinePromoteRequested: (todoId: String) -> Unit = { todoId ->
        promoteTargetTodoId = todoId
    }
    val onTimelineDeferRequested: (todoId: String) -> Unit = { todoId ->
        deferTargetTodoId = todoId
    }
    val onTimelineDragStart: (todo: TodoItem, position: Offset) -> Unit = { todo, position ->
        activeDropSectionKey = null
        timelineDropTargetBounds.clear()
        draggedScheduledTodoId = todo.id
        TdayHaptics.dragPickUp(view)
        activeTimelineDrag = TimelineInAppDrag(todo, position)
    }
    val onTimelineDragMove: (todo: TodoItem, position: Offset) -> Unit = { todo, position ->
        activeTimelineDrag = activeTimelineDrag?.copy(position = position)
            ?: TimelineInAppDrag(todo, position)
        updateActiveTimelineDropTarget(position)
    }
    val onTimelineDragCancel: () -> Unit = {
        activeTimelineDrag = null
        draggedScheduledTodoId = null
        activeDropSectionKey = null
        timelineDropTargetBounds.clear()
    }

    Scaffold(
        // One interceptor per screen, at the outermost composable rather than on
        // the feed container, so the header, the search capsule, the FAB and the
        // gaps between rows are all inside it and none of them has to know this
        // feature exists. It observes and never consumes -- see
        // `tdayClosesSwipeRowOnOutsideTap`.
        //
        // Skipped when a host handed the slot down, because then this Scaffold is
        // NOT the outermost composable -- on the Anytime tab the dock and the
        // create button are drawn above it, outside this subtree, and Compose
        // routes a pointer down into the hit child's path only. The host installs
        // one at the box that does contain them. Installing both would be
        // harmless and still wrong: two observers whose agreement nobody checks,
        // where the rule is one per screen and the screen is whatever contains
        // the chrome.
        modifier = if (hostSwipeSlot == null) {
            Modifier.tdayClosesSwipeRowOnOutsideTap(
                slot = swipeSlot,
                close = { swipeSlot.openId = null },
            )
        } else {
            Modifier
        },
        containerColor = colorScheme.background,
        floatingActionButton = {
            // The selection action bar takes the bottom of the screen while
            // selecting; the two must never share it.
            if (showCreateTaskButton && !isViewerList && !selectionActive) {
                CreateTaskButton(
                    interactionSource = fabInteractionSource,
                    backgroundColor = fabColor,
                    onClick = {
                        quickAddDueEpochMs = null
                        showCreateTaskSheet = true
                    },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    timelineDragContainerOrigin = coordinates.positionInRoot()
                },
        ) {
            TdayPullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = onRefresh,
                enabled = pullRefreshEnabled,
                showsIndicator = false,
                onIndicatorStateChange = { refreshing, fraction, frozen ->
                    refreshIsRefreshing = refreshing
                    refreshPullFraction = fraction
                    refreshWaveFrozen = frozen
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .then(
                        // A tap on the blank space the feed gave up dismisses the
                        // field. Guarded on the toolbar row's fixed geometry, not
                        // a reported rect, so the tap that opened the field can
                        // never be read as an outside tap.
                        if (floaterTaskHomeSearchExpanded) {
                            Modifier.pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(pass = PointerEventPass.Final)
                                    val point = down.position
                                    val onResults =
                                        floaterSearchResultsBounds?.contains(point) == true
                                    val up = waitForUpOrCancellation(pass = PointerEventPass.Final)
                                    if (up != null && !onResults && point.y > headerBarHeightPx) {
                                        closeFloaterTaskHomeSearch()
                                    }
                                }
                            }
                        } else {
                            Modifier
                        },
                    )
                    // The pinned-toolbar screens get the same courtesy the root
                    // feeds have had: tap the feed and the field goes away.
                    .tdayClosesSearchOnOutsideTap(
                        isSearchOpen = showScopedSearchField,
                        barHeightPx = pinnedToolbarHeightPx,
                        close = closeScopedSearch,
                    ),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    // Freeze list scrolling while a task is being dragged so the
                    // drop target stays put under the finger (matches Calendar).
                    userScrollEnabled = activeTimelineDrag == null,
                    contentPadding = when {
                        usesRootFeedChrome -> PaddingValues(
                            start = TdayDimens.ContentPaddingHorizontal,
                            end = TdayDimens.ContentPaddingHorizontal,
                            bottom = TdayDimens.SpacingXxl,
                        )
                        // No top padding: the hero item reserves the bar's
                        // height itself, so the scroll offset is a clean count
                        // from the top.
                        usesTodayStyle -> PaddingValues(
                            start = TdayDimens.ContentPaddingHorizontal,
                            end = TdayDimens.ContentPaddingHorizontal,
                            bottom = TdayDimens.SpacingXxs,
                        )
                        else -> PaddingValues(
                            horizontal = ListModeContentHorizontalPadding,
                            vertical = TdayDimens.SpacingLg,
                        )
                    },
                    verticalArrangement = Arrangement.spacedBy(
                        if (showSectionedTimeline) TdayDimens.SpacingNone else timelineItemSpacing,
                    ),
                ) {
                    // One branch, so index 0 is always well defined — the
                    // hero's progress is read off the first item's offset.
                    if (usesRootFeedChrome) {
                        item(
                            key = "root-feed-header-spacer",
                            contentType = "root-feed-header-spacer",
                        ) {
                            // Reserves the pinned header's space. The feed
                            // scrolls behind the header, folding it down into
                            // its always-visible toolbar strip.
                            Spacer(
                                modifier = Modifier
                                    .height(RootFeedHeroHeaderMetrics.ExpandedHeight),
                            )
                        }
                    } else if (usesTodayStyle) {
                        tdayHeroTitleItem(
                            title = uiState.title,
                            icon = heroIcon,
                            accentColor = titleColor,
                            collapseProgress = heroCollapse.progress,
                        )
                    }

                    if (showFloaterTaskHomeSearchResults) {
                        item(
                            key = "root-floater-search-results",
                            contentType = "root-floater-search-results",
                        ) {
                            FloaterTaskHomeSearchResultsCard(
                                results = floaterTaskHomeSearchResults,
                                listsById = floaterTaskHomeListById,
                                onOpenTodo = ::openFloaterTaskHomeSearchResult,
                                modifier = Modifier
                                    .padding(bottom = FloaterFeedRowSpacing)
                                    .onGloballyPositioned { coordinates ->
                                        floaterSearchResultsBounds = coordinates.boundsInRoot()
                                    },
                            )
                        }
                    }

                    // While a query is live only the results remain; the rest
                    // of the feed gives way to blank space, and a tap there
                    // dismisses the field.
                    if (!showFloaterTaskHomeSearchResults) {

                    // The flat feed's first paint. This was a card with the word
                    // "Loading" in it, and the rows then appeared underneath in
                    // one frame; the skeleton draws the rows' own geometry
                    // instead, so the frame the data lands on changes colour and
                    // nothing else.
                    //
                    // Mounted for a window rather than removed by its guard: an
                    // item that its guard has already taken out of the list has no
                    // exit left to play, and the exit is the whole mechanism here.
                    // The window closes one hand-over later, because this
                    // `LazyColumn` spaces its items and a mounted item that draws
                    // nothing is still charged `timelineItemSpacing` — see
                    // [rememberTdayTaskRowSkeletonMounted].
                    // Two lazy items cannot cross-fade over each other — they are
                    // stacked, not layered — so the hand-over is the placeholder
                    // fading AND retracting on one spec while the feed takes the
                    // space it gives up. Fading alone would hold the skeleton's
                    // full height for the length of the fade and then drop three
                    // rows' worth of feed upward in a single frame, which is the
                    // pop this is here to remove, moved later and made larger.
                    // `AnimatedVisibility` with a paired fade and shrink is the
                    // same answer the Earlier scene below already gives.
                    if (taskFeedSkeletonMounted) {
                        item(
                            key = "task-feed-skeleton",
                            contentType = "task-feed-skeleton",
                        ) {
                            AnimatedVisibility(
                                visible = taskFeedSkeletonVisible,
                                // No enter. A placeholder that fades itself in is
                                // a wait in front of the notice that there is a
                                // wait; the fifth idiom rule cuts that way round
                                // as well.
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

                    // A search that matched nothing replaces the timeline rather
                    // than leaving its empty day headers standing, as on web.
                    if (scopedSearchHasNoResults) {
                        item(
                            key = "scoped-search-no-results",
                            contentType = "scoped-search-no-results",
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(
                                        min = (LocalConfiguration.current.screenHeightDp * 0.32f).dp,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                TdayEmptyState(
                                    // The magnifier, not the screen's own glyph:
                                    // this is the query coming up short, not the
                                    // screen being empty.
                                    icon = R.drawable.ic_lucide_search,
                                    accentColor = titleColor,
                                    title = stringResource(R.string.scheduled_task_home_search_no_results),
                                    description = stringResource(R.string.search_no_results_body),
                                    // Clears the query without leaving the
                                    // search, so the next word can be typed
                                    // straight into the field that is still open.
                                    action = {
                                        OutlinedButton(onClick = { scopedSearchQuery = "" }) {
                                            Text(text = stringResource(R.string.action_clear_search))
                                        }
                                    },
                                )
                            }
                        }
                    }

                    if (showSectionedTimeline && !suppressInitialTodayTimeline && !scopedSearchHasNoResults) {
                        sectionedTimelineContent(
                            uiState = uiState,
                            timelineSections = timelineSections,
                            usesRootFeedChrome = usesRootFeedChrome,
                            usesTodayStyle = usesTodayStyle,
                            timelineAnimationsEnabled = timelineAnimationsEnabled,
                            earlierSceneContent = earlierSceneContent,
                            scopedSearchActive = scopedSearchActive,
                            canRescheduleTasks = canRescheduleTasks,
                            isViewerList = isViewerList,
                            selectionActive = selectionActive,
                            selectedTodoIds = selectedTodoIds,
                            flashTodoId = flashTodoId,
                            swipeSlot = swipeSlot,
                            collapsedSectionKeys = collapsedSectionKeys,
                            activeDropSectionKey = activeDropSectionKey,
                            draggedScheduledTodo = draggedScheduledTodo,
                            restingFloatersEnabled = restingFloatersEnabled,
                            timelineDropTargetBounds = timelineDropTargetBounds,
                            canDropTodoInTimelineSection = ::canDropTodoInTimelineSection,
                            onSectionHeaderToggle = onTimelineSectionHeaderToggle,
                            onQuickAdd = onTimelineQuickAdd,
                            onToggleTodoSelected = toggleTodoSelected,
                            onComplete = completeAndCelebrate,
                            onDelete = onDelete,
                            onEditRequested = onTimelineEditRequested,
                            onPromoteRequested = onTimelinePromoteRequested,
                            onDemoteTodo = onDemoteTodo,
                            onDeferRequested = onTimelineDeferRequested,
                            onDragStart = onTimelineDragStart,
                            onDragMove = onTimelineDragMove,
                            onDragEnd = ::finishTimelineDrag,
                            onDragCancel = onTimelineDragCancel,
                        )
                    } else if (!showSectionedTimeline) {
                        flatTodoRowsContent(
                            todos = uiState.items,
                            usesTodayStyle = usesTodayStyle,
                            onComplete = completeAndCelebrate,
                            onDelete = onDelete,
                        )
                    }

                    floaterTaskHomeRootFeedContent(
                        isFloaterTaskHomeScreen = isFloaterTaskHomeScreen,
                        timelineAnimationsEnabled = timelineAnimationsEnabled,
                        emptyScene = floaterEmptySceneContent,
                        onOpenCompleted = onOpenCompleted,
                        floaterTaskHomeListRows = floaterTaskHomeListRows,
                        onOpenFloaterList = onOpenFloaterList,
                    )

                    // Keyed, because a load failure genuinely adds and removes a
                    // row here and `animateItem` cannot animate either on an item
                    // whose identity is its index. Unlike the blocks above it this
                    // one takes all three specs: it is added and removed rather
                    // than merely displaced, which is the one case on this feed a
                    // fade describes.
                    //
                    // Nothing below it travels — this is the last content item and
                    // only a keyless spacer follows — so the inherited
                    // [TdayFeedItemMotion.Placement] earns its place in the other
                    // direction: the skeleton leaving and the empty scene arriving
                    // change the row count ABOVE the card while it is already up,
                    // and it should glide into the slot they leave it in rather
                    // than be re-laid-out into it.
                    //
                    // It sits inside the `!showFloaterTaskHomeSearchResults`
                    // branch, so it does take the fades [displacedFeedItemMotion]
                    // argues itself out of: opening a live query takes this whole
                    // body away in one frame and leaves the card fading alone over
                    // the blank. Accepted, not gated — an error banner and a live
                    // query rarely coexist, and a gate read inside this lambda
                    // could never fire, because the item is only ever composed
                    // while the flag is false.
                    uiState.errorMessage?.let { message ->
                        item(key = "error-retry", contentType = "error_retry") {
                            // `timelineAnimationsEnabled` is a first-frame gate,
                            // not the preference one — it only says the feed has
                            // settled enough to animate at all — so the card asks
                            // the preference itself, and motion off draws the card
                            // where it belongs with no wait in front of it.
                            val errorCardMotionEnabled = rememberTdayMotionEnabled()
                            com.ohmz.tday.compose.core.ui.ErrorRetryCard(
                                message = message,
                                onRetry = onRefresh,
                                modifier = feedItemMotion(
                                    timelineAnimationsEnabled && errorCardMotionEnabled,
                                ),
                            )
                        }
                    }

                    item { Spacer(Modifier.height(TdayDimens.BottomScrollSpacer)) }
                    }
                }
            }

            if (emptyWatermarkDrawable != null) {
                EmptyTaskWatermark(
                    iconRes = emptyWatermarkDrawable,
                    accentColor = titleColor,
                    flipHorizontal = flipWatermark,
                )
            } else {
                EmptyTaskWatermark(
                    imageVector = emptyWatermarkIcon,
                    accentColor = titleColor,
                    flipHorizontal = flipWatermark,
                )
            }
            // Root floater shows its empty message inline (in the list, above
            // the list names) so the overlay version would double up. Any
            // mode with an Earlier concept (Today, and now
            // Scheduled/Priority/All/List — see `scopeHasEarlierItems`)
            // defers the same way whenever Earlier still holds overdue
            // tasks: that case renders its own inline scene in Earlier's
            // place instead (above, inside the LazyColumn), so the collapsed
            // header stays reachable under it rather than sitting beneath
            // this full-screen overlay -- see requirement 2/3. `scopeItemsEmpty`
            // rather than raw `uiState.items.isEmpty()` for the same reason:
            // Scheduled/Priority/All/List mix Earlier straight into `items`,
            // so the raw count would stay non-zero for as long as Earlier
            // held anything, even with nothing else left pending.
            // `scopedSearchActive` and not `scopedSearchHasNoResults`: a scope
            // with no tasks at all still has none once a query is typed, so both
            // states were true at once and the screen drew two empty scenes on
            // top of each other. While a query stands the in-list no-results
            // scene owns it — it is the one that can say what was searched.
            // THE ONE PRESENTATION CHANGE IN THIS FIX, and it is here rather than
            // in the burst because of what the burst now needs from its host. A
            // cancelled celebration fades its paper out over `Quick` instead of
            // cutting it (see `TdayConfetti`'s mount latch), and on the plain path
            // -- no overdue tasks -- the very undo that cancels the burst also
            // makes this scene's condition false. This branch had no exit at all,
            // so the scene and the paper on it were removed on the same frame and
            // the envelope never got to run: a fade cut by the unmount above it is
            // the same complaint one layer up.
            //
            // So: the scene leaves on the envelope's own rung, and scene and paper
            // go together. `EnterTransition.None` because nothing about the
            // ARRIVAL is in dispute -- this scene has always appeared on the frame
            // the scope emptied, the celebration's 320 ms lead is timed against
            // that, and giving it an enter here would put a fade in front of the
            // payoff. `ExitTransition.None` with motion off, where the burst is
            // unmounting on the same frame for the same reason and there is
            // nothing left to keep alive for.
            //
            // What else now leaves on this exit, stated rather than discovered:
            // every other way this condition goes false. A pull-to-refresh over an
            // already-empty scope sets `isLoading` and used to cut the scene; it
            // fades it now. Same destination, 150 ms of paint, and the shortest
            // rung on the ladder -- an exit is never longer than the enter it
            // undoes, and this one has no enter at all.
            //
            // The overdue path does NOT come through here (`!scopeHasEarlierItems`
            // defers it to the inline scene under Earlier's header), and must not:
            // a restored overdue row leaves that scene exactly where it is, which
            // is the v0.7.25 presentation and was never the thing that was wrong.
            // Only the confetti over it was.
            AnimatedVisibility(
                visible = scopeItemsEmpty && !uiState.isLoading && !suppressInitialTodayTimeline &&
                    !isFloaterTaskHomeScreen && !scopedSearchActive && !scopeHasEarlierItems,
                enter = EnterTransition.None,
                exit = if (rememberTdayMotionEnabled()) {
                    fadeOut(
                        animationSpec = tween(
                            durationMillis = TdayMotionTokens.Durations.Quick,
                            easing = TdayMotionTokens.Easings.Exit,
                        ),
                    )
                } else {
                    ExitTransition.None
                },
            ) {
                Box(
                    // The Scaffold's insets, so the scene centres in the content
                    // area rather than in the window: without them a notch pushes
                    // it half the inset off centre.
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    TdayEmptyState(
                        // Day Done keeps its own glyph and its date line: it is
                        // a payoff, not an absence, and the scope's own icon
                        // would undersell it.
                        icon = emptyStateSceneIconRes,
                        accentColor = titleColor,
                        title = emptyStateSceneTitle,
                        description = emptyStateSceneDescription,
                        celebrate = celebrateEmptyState,
                    )
                }
            }

            if (usesRootFeedChrome) {
                RootFeedHeroHeader(
                    title = uiState.title,
                    mark = if (isFloaterTaskHomeScreen) {
                        RootFeedHeroMark.FloaterLeaf
                    } else {
                        RootFeedHeroMark.TimeOfDay
                    },
                    collapseProgress = headerCollapseProgress,
                    searchExpanded = floaterTaskHomeSearchExpanded,
                    searchQuery = floaterTaskHomeSearchQuery,
                    searchPlaceholder = stringResource(R.string.root_feed_search_floater),
                    searchPlaceholderShort = stringResource(R.string.action_search),
                    onSearchQueryChange = { floaterTaskHomeSearchQuery = it },
                    onSearchExpandedChange = { floaterTaskHomeSearchExpanded = it },
                    onSearchClose = closeFloaterTaskHomeSearch,
                    onCreateList = {
                        closeFloaterTaskHomeSearch()
                        showCreateListSheet = true
                    },
                    onOpenSettings = {
                        closeFloaterTaskHomeSearch()
                        onOpenSettings()
                    },
                    onScrollToTop = {
                        screenScope.launch {
                            closeFloaterTaskHomeSearch()
                            listState.animateScrollToItem(index = 0, scrollOffset = 0)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(padding)
                        .zIndex(6f),
                    refreshIsRefreshing = refreshIsRefreshing,
                    refreshPullFraction = { refreshPullFraction },
                    refreshWaveFrozen = refreshWaveFrozen,
                )
            } else if (usesTodayStyle) {
                // The pinned half of the header. An overlay rather than a
                // Scaffold `topBar`, so the hero block below can scroll behind
                // it instead of starting under it.
                TdayHeroToolbar(
                    title = uiState.title,
                    titleColor = titleColor,
                    collapseProgress = heroCollapse.progress,
                    // Gone while the field is up: a back chevron beside an
                    // open search is a second way out that leaves the screen
                    // rather than the query, and it costs the field the width
                    // that makes a placeholder readable.
                    onBack = if (showScopedSearchField || selectionActive) null else onBack,
                    backContentDescription = stringResource(R.string.action_back),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(padding)
                        .zIndex(6f),
                    titleSuppressed = showScopedSearchField || selectionActive,
                    actions = {
                        if (selectionActive) {
                            // The same full-row takeover the search field uses:
                            // the bar's own title and back chevron give way, and
                            // the count plus Select all / Deselect all take the
                            // whole row.
                            TodayHeaderButton(
                                onClick = exitSelection,
                                icon = ImageVector.vectorResource(R.drawable.ic_lucide_x),
                                contentDescription = stringResource(R.string.action_cancel),
                                iconSize = HeaderButtonIconSize,
                            )
                            Text(
                                text = if (selectionCapReached) {
                                    stringResource(
                                        R.string.bulk_selected_capped,
                                        selectedTodoIds.size,
                                    )
                                } else {
                                    stringResource(R.string.bulk_selected, selectedTodoIds.size)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = BulkSelectionCountHorizontalPadding),
                            )
                            TextButton(
                                onClick = {
                                    if (allSelectableSelected) {
                                        selectedTodoIds = emptySet()
                                    } else {
                                        selectedTodoIds = selectableTodos
                                            .take(BulkSelectionPolicy.MAX_SELECTION)
                                            .mapTo(mutableSetOf()) { it.id }
                                    }
                                },
                            ) {
                                Text(
                                    text = if (allSelectableSelected) {
                                        stringResource(R.string.bulk_deselect_all)
                                    } else {
                                        stringResource(R.string.bulk_select_all)
                                    },
                                    color = colorScheme.primary,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                )
                            }
                        } else if (showScopedSearchField) {
                            // The field takes the WHOLE bar — back chevron,
                            // title and action cluster all give way to it, as
                            // they do on the root feeds and on iOS's
                            // TimelineTopBar. In ordinary flow under the hero
                            // block the field scrolled away from under a live
                            // query, leaving the keyboard up with nothing to
                            // type in.
                            val focusRequester = remember { FocusRequester() }
                            LaunchedEffect(scopedSearchNeedsFocus) {
                                if (!scopedSearchNeedsFocus) return@LaunchedEffect
                                // Consumed on the way in, so returning to a
                                // screen that still has the field open does not
                                // re-open the keyboard with it.
                                scopedSearchNeedsFocus = false
                                focusRequester.requestFocus()
                            }
                            TdaySearchCapsule(
                                value = scopedSearchQuery,
                                onValueChange = { scopedSearchQuery = it },
                                // Names the screen, as the other three scoped
                                // searches do and as web does — "Search" alone
                                // is the one thing a scoped field must not
                                // imply it is.
                                placeholder = stringResource(
                                    R.string.action_search_in,
                                    uiState.title,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                                // The one control in the row, so its X leaves
                                // the search — and leaving clears the query on
                                // the way out.
                                onClose = closeScopedSearch,
                                trailingContentDescription = stringResource(R.string.action_close_search),
                            )
                        } else {
                            topBarActions.forEach { action ->
                                TodayHeaderButton(
                                    onClick = action.onClick,
                                    icon = action.icon,
                                    contentDescription = action.contentDescription,
                                    iconSize = HeaderButtonIconSize,
                                )
                            }
                        }
                    },
                )
            }

            if (showRootFeedDock && rootFeedTab != null && onRootFeedTabSelected != null &&
                !selectionActive
            ) {
                RootFeedDock(
                    activeTab = rootFeedTab,
                    collapsed = dockCollapsed,
                    onTabSelected = { tab ->
                        if (tab == rootFeedTab && isFloaterTaskHomeScreen) {
                            screenScope.launch {
                                closeFloaterTaskHomeSearch()
                                listState.animateScrollToItem(index = 0, scrollOffset = 0)
                            }
                        } else {
                            onRootFeedTabSelected(tab)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .zIndex(8f),
                )
            }

            if (selectionActive) {
                BulkSelectionActionBar(
                    // Delete / Priority / Move go dark when the selection is
                    // nothing but repeating occurrences, which those three
                    // never touch.
                    completeEnabled = bulkCompleteTargets.isNotEmpty(),
                    editEnabled = bulkNonRecurringTargets.isNotEmpty(),
                    onComplete = {
                        lastCompletionAtMs = SystemClock.uptimeMillis()
                        onBulkComplete(bulkCompleteTargets)
                        exitSelection()
                    },
                    onPriority = { showBulkPriorityPicker = true },
                    onMove = { showBulkListPicker = true },
                    onDelete = { showBulkDeleteConfirmation = true },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .zIndex(8f),
                )
            }

            activeTimelineDrag?.let { drag ->
                TimelineTaskDragPreview(
                    modifier = Modifier
                        .offset {
                            val localPosition = drag.position - timelineDragContainerOrigin
                            IntOffset(
                                x = (localPosition.x - with(density) { TimelineDragPreviewAnchorX.toPx() }).roundToInt(),
                                y = (localPosition.y - with(density) { TimelineDragPreviewAnchorY.toPx() }).roundToInt(),
                            )
                        }
                        .zIndex(20f),
                    todo = drag.todo,
                    lists = uiState.lists,
                    mode = uiState.mode,
                )
            }
        }
    }

    if (showCreateTaskSheet) {
        CreateTaskBottomSheet(
            lists = uiState.lists,
            defaultListId = if (uiState.mode == TodoListMode.LIST || uiState.mode == TodoListMode.FLOATER) uiState.listId else null,
            defaultPriority = if (uiState.mode == TodoListMode.PRIORITY) "Medium" else null,
            defaultScheduled = uiState.mode != TodoListMode.FLOATER,
            showScheduleControls = uiState.mode != TodoListMode.FLOATER,
            initialDueEpochMs = quickAddDueEpochMs,
            presentImmediately = openCreateTaskOnStart,
            onParseTaskTitleNlp = if (uiState.mode == TodoListMode.FLOATER) null else onParseTaskTitleNlp,
            onDismiss = {
                if (exitOnCreateTaskSheetDismiss) {
                    onBack()
                } else {
                    showCreateTaskSheet = false
                    quickAddDueEpochMs = null
                    onCreateTaskFlowFinished()
                }
            },
            onCreateTask = onAddTask,
        )
    }

    LaunchedEffect(uiState.summaryConnectivityError) {
        if (uiState.summaryConnectivityError) showSummarySheet = false
    }

    if (showSummarySheet) {
        SummaryBottomSheet(
            isLoading = uiState.isSummarizing,
            summaryText = uiState.summaryText,
            summarySource = uiState.summarySource,
            aiSummaryConfigured = uiState.aiSummaryConfigured,
            errorMessage = uiState.summaryError,
            onDismiss = { showSummarySheet = false },
        )
    }

    if (uiState.summaryConnectivityError) {
        AlertDialog(
            onDismissRequest = onDismissSummaryConnectivityError,
            title = {
                Text(
                    text = stringResource(R.string.error_connectivity_title),
                    fontWeight = FontWeight.ExtraBold,
                )
            },
            text = {
                Text(text = stringResource(R.string.error_connectivity))
            },
            dismissButton = {
                TextButton(onClick = onDismissSummaryConnectivityError) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onDismissSummaryConnectivityError()
                    showSummarySheet = true
                }) {
                    Text(stringResource(R.string.action_retry))
                }
            },
        )
    }

    pendingRescheduleDrop?.let { drop ->
        AlertDialog(
            onDismissRequest = { pendingRescheduleDrop = null },
            title = {
                Text(
                    text = stringResource(R.string.todos_reschedule_recurring_title),
                    fontWeight = FontWeight.ExtraBold,
                )
            },
            text = {
                Text(text = stringResource(R.string.todos_reschedule_recurring_message))
            },
            dismissButton = {
                TextButton(onClick = { pendingRescheduleDrop = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        pendingRescheduleDrop = null
                        val hour = drop.targetHour
                        if (hour != null) {
                            onMoveTaskToTimeOfDay(drop.todo, hour, TaskRescheduleScope.OCCURRENCE)
                        } else if (drop.targetDate != null) {
                            onMoveTask(drop.todo, drop.targetDate, TaskRescheduleScope.OCCURRENCE)
                        }
                    }) {
                        Text(stringResource(R.string.todos_reschedule_this_occurrence))
                    }
                    TextButton(onClick = {
                        pendingRescheduleDrop = null
                        val hour = drop.targetHour
                        if (hour != null) {
                            onMoveTaskToTimeOfDay(drop.todo, hour, TaskRescheduleScope.SERIES)
                        } else if (drop.targetDate != null) {
                            onMoveTask(drop.todo, drop.targetDate, TaskRescheduleScope.SERIES)
                        }
                    }) {
                        Text(stringResource(R.string.todos_reschedule_entire_series))
                    }
                }
            },
        )
    }

    editTargetTodo?.let { todo ->
        CreateTaskBottomSheet(
            lists = uiState.lists,
            editingTask = todo,
            showScheduleControls = uiState.mode != TodoListMode.FLOATER,
            onParseTaskTitleNlp = if (uiState.mode == TodoListMode.FLOATER) null else onParseTaskTitleNlp,
            onDismiss = { editTargetTodoId = null },
            onCreateTask = { _ -> },
            onUpdateTask = onUpdateTask,
        )
    }

    // "Schedule" a floater: pick a day, promote it as a real todo at 09:00
    // local that day (a sane default; the time is one edit away afterwards).
    val promoteTargetTodo = remember(promoteTargetTodoId, uiState.items) {
        promoteTargetTodoId?.let { targetId -> uiState.items.firstOrNull { it.id == targetId } }
    }
    promoteTargetTodo?.let { floater ->
        ThemedDatePickerDialog(
            initialEpochMs = System.currentTimeMillis(),
            onDismiss = { promoteTargetTodoId = null },
            onConfirm = { pickedDayUtcMidnightMs ->
                promoteTargetTodoId = null
                // The picker returns UTC-midnight of the picked calendar day.
                val pickedDay = Instant.ofEpochMilli(pickedDayUtcMidnightMs)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                val dueEpochMs = pickedDay
                    .atTime(9, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                onPromoteFloater(floater, dueEpochMs)
            },
        )
    }

    // Quick Defer: one tap moves the task to a locally computed instant.
    val deferTargetTodo = remember(deferTargetTodoId, uiState.items) {
        deferTargetTodoId?.let { targetId -> uiState.items.firstOrNull { it.id == targetId } }
    }
    deferTargetTodo?.let { todo ->
        val deferOptions = remember(deferTargetTodoId) { quickDeferOptions() }
        val deferContext = LocalContext.current
        TdayCenteredSelectorDialog(
            title = stringResource(R.string.action_defer),
            options = deferOptions,
            optionLabel = { option -> deferContext.getString(option.choice.labelRes) },
            optionSwatchColor = { TdaySwipeScheduleBackground },
            isSelected = { false },
            onDismiss = { deferTargetTodoId = null },
            onOptionSelected = { option ->
                deferTargetTodoId = null
                onDeferTask(todo, option.dueEpochMs)
            },
        )
    }

    if (showCreateListSheet && isFloaterTaskHomeScreen) {
        ListSettingsBottomSheet(
            title = stringResource(R.string.scheduled_task_home_new_list),
            listName = createListName,
            onListNameChange = { createListName = capitalizeFirstListLetter(it) },
            listColor = createListColor,
            onListColorChange = { createListColor = it },
            listIconKey = createListIconKey,
            onListIconChange = { createListIconKey = it },
            showDelete = false,
            onDismiss = { showCreateListSheet = false },
            onSave = {
                val normalizedName = capitalizeFirstListLetter(createListName).trim()
                if (normalizedName.isNotBlank()) {
                    onCreateList(normalizedName, createListColor, createListIconKey)
                    createListName = ""
                    createListColor = TDAY_DEFAULT_LIST_COLOR_KEY
                    createListIconKey = TDAY_DEFAULT_LIST_ICON_KEY
                    showCreateListSheet = false
                }
            },
            onDelete = {},
        )
    }

    val selectedListId = listSettingsTargetId ?: uiState.listId
    if (
        showListSettingsSheet &&
        isListDetailScreen &&
        !selectedListId.isNullOrBlank()
    ) {
        ListSettingsBottomSheet(
            title = stringResource(R.string.todos_list_settings),
            listName = listSettingsName,
            onListNameChange = { listSettingsName = capitalizeFirstListLetter(it) },
            listColor = listSettingsColor,
            onListColorChange = {
                listSettingsColor = it
                listSettingsColorTouched = true
            },
            listIconKey = listSettingsIconKey,
            onListIconChange = {
                listSettingsIconKey = it
                listSettingsIconTouched = true
            },
            onShare = {
                showListSettingsSheet = false
                listSettingsTargetId = null
                selectedList?.let { list ->
                    shareList(context, list.name, uiState.items)
                }
            },
            onMembers = {
                showListSettingsSheet = false
                listSettingsTargetId = null
                showMembersSheet = true
            },
            onDismiss = {
                showListSettingsSheet = false
                listSettingsTargetId = null
            },
            onSave = {
                onUpdateListSettings(
                    selectedListId,
                    listSettingsName,
                    if (listSettingsColorTouched) listSettingsColor else null,
                    if (listSettingsIconTouched) listSettingsIconKey else null,
                )
                showListSettingsSheet = false
                listSettingsTargetId = null
            },
            onDelete = {
                showListSettingsSheet = false
                showDeleteListConfirmation = true
            },
        )
    }

    if (showMembersSheet && isListDetailScreen && selectedList != null) {
        ManageMembersSheet(
            listId = selectedList.id,
            listName = selectedList.name,
            kind = if (uiState.mode == TodoListMode.FLOATER) {
                ShareListKind.FLOATER
            } else {
                ShareListKind.SCHEDULED
            },
            myRole = selectedList.myRole,
            onShareAsText = { shareList(context, selectedList.name, uiState.items) },
            onDismiss = { showMembersSheet = false },
            onLeftList = {
                showMembersSheet = false
                onRefresh()
                onBack()
            },
        )
    }

    val deleteConfirmationListId = selectedListId
    if (
        showDeleteListConfirmation &&
        isListDetailScreen &&
        !deleteConfirmationListId.isNullOrBlank()
    ) {
        ListDeleteConfirmationDialog(
            onDismissRequest = { showDeleteListConfirmation = false },
            onConfirm = {
                showDeleteListConfirmation = false
                onDeleteList(deleteConfirmationListId)
                listSettingsTargetId = null
            },
        )
    }

    // Bulk delete, layer one. The undo toast the ViewModel raises afterwards is
    // layer two, and neither stands in for the other: a batch delete is the one
    // action here that is worth being asked about twice.
    if (showBulkDeleteConfirmation && bulkNonRecurringTargets.isNotEmpty()) {
        val deleteCount = bulkNonRecurringTargets.size
        TdayConfirmationDialog(
            title = pluralStringResource(R.plurals.bulk_delete_title, deleteCount, deleteCount),
            message = stringResource(R.string.bulk_delete_body),
            skippedMessage = if (bulkSkipsRecurring) {
                stringResource(R.string.bulk_applies_to, deleteCount, selectedTodos.size)
            } else {
                null
            },
            confirmLabel = pluralStringResource(
                R.plurals.bulk_delete_confirm,
                deleteCount,
                deleteCount,
            ),
            confirmColor = colorScheme.error,
            confirmIsDestructive = true,
            onDismissRequest = { showBulkDeleteConfirmation = false },
            onConfirm = {
                showBulkDeleteConfirmation = false
                onBulkDelete(bulkNonRecurringTargets)
                exitSelection()
            },
        )
    }

    if (showBulkPriorityPicker && bulkNonRecurringTargets.isNotEmpty()) {
        val priorityTargets = bulkNonRecurringTargets
        val priorityOptions = remember { PRIORITY_OPTIONS_LOW_TO_HIGH }
        TdayCenteredSelectorDialog(
            title = stringResource(R.string.bulk_action_priority),
            options = priorityOptions,
            optionLabel = { option -> context.getString(priorityDisplayLabelRes(option)) },
            optionSwatchColor = { option -> tdayPriorityColor(option) },
            isSelected = { option ->
                priorityTargets.all { canonicalPriorityValue(it.priority) == option }
            },
            onDismiss = { showBulkPriorityPicker = false },
            onOptionSelected = { option ->
                showBulkPriorityPicker = false
                onBulkSetPriority(priorityTargets, option)
                exitSelection()
            },
        )
    }

    if (showBulkListPicker && bulkNonRecurringTargets.isNotEmpty()) {
        val moveTargets = bulkNonRecurringTargets
        // Same-silo only: scheduled tasks move between scheduled lists and
        // floaters between floater lists, and `uiState.lists` is already the
        // right silo for the mode. Viewer lists are not offered, exactly as the
        // create/edit sheet excludes them.
        val moveOptions = remember(uiState.lists) {
            listOf<ListSummary?>(null) + uiState.lists.filter { !it.isViewer }
        }
        val noListLabel = stringResource(R.string.create_task_no_list)
        TdayCenteredSelectorDialog(
            title = stringResource(R.string.bulk_action_move),
            options = moveOptions,
            optionLabel = { option -> option?.name ?: noListLabel },
            optionSwatchColor = { option ->
                option?.let { tdayListAccentColor(it.color) }
                    ?: colorScheme.outlineVariant.copy(alpha = 0.95f)
            },
            isSelected = { option -> moveTargets.all { it.listId == option?.id } },
            onDismiss = { showBulkListPicker = false },
            onOptionSelected = { option ->
                showBulkListPicker = false
                val distinctSourceLists = moveTargets.map { it.listId }.distinct().size
                if (
                    BulkSelectionPolicy.requiresConfirmation(
                        action = BulkAction.MOVE_TO_LIST,
                        distinctSourceLists = distinctSourceLists,
                    )
                ) {
                    // Only when the sources differ: that is the case the user
                    // cannot put back, because an edit has no undo and the
                    // original assignments are gone.
                    pendingBulkMoveListId = option?.id
                    showBulkMoveConfirmation = true
                } else {
                    onBulkMoveToList(moveTargets, option?.id)
                    exitSelection()
                }
            },
        )
    }

    if (showBulkMoveConfirmation && bulkNonRecurringTargets.isNotEmpty()) {
        val moveCount = bulkNonRecurringTargets.size
        TdayConfirmationDialog(
            title = pluralStringResource(R.plurals.bulk_move_title, moveCount, moveCount),
            message = stringResource(R.string.bulk_move_body),
            skippedMessage = if (bulkSkipsRecurring) {
                stringResource(R.string.bulk_applies_to, moveCount, selectedTodos.size)
            } else {
                null
            },
            confirmLabel = pluralStringResource(R.plurals.bulk_move_confirm, moveCount, moveCount),
            confirmColor = colorScheme.primary,
            onDismissRequest = {
                showBulkMoveConfirmation = false
                pendingBulkMoveListId = null
            },
            onConfirm = {
                val targetListId = pendingBulkMoveListId
                val targets = bulkNonRecurringTargets
                showBulkMoveConfirmation = false
                pendingBulkMoveListId = null
                onBulkMoveToList(targets, targetListId)
                exitSelection()
            },
        )
    }
}

/**
 * The flat, unsectioned task list body — the non-timeline sibling of
 * [sectionedTimelineContent], used wherever [TodoListScreen] shows its items
 * in one plain run rather than under day/priority headers.
 *
 * A `LazyListScope` receiver extension, not a plain `@Composable`, so the
 * `items(...)` call below registers directly against the caller's
 * [LazyColumn] — its keys, content types and placement are exactly what
 * they were when this body lived inline in [TodoListScreen].
 */
private fun LazyListScope.flatTodoRowsContent(
    todos: List<TodoItem>,
    usesTodayStyle: Boolean,
    onComplete: (TodoItem) -> Unit,
    onDelete: (TodoItem) -> Unit,
) {
    items(
        items = todos,
        key = { it.id },
        contentType = { "todo-row" },
    ) { todo ->
        if (usesTodayStyle) {
            TodayTodoRow(
                todo = todo,
                onComplete = { onComplete(todo) },
                onDelete = { onDelete(todo) },
            )
        } else {
            TodoRow(
                todo = todo,
                onComplete = { onComplete(todo) },
                onDelete = { onDelete(todo) },
            )
        }
    }
}

/**
 * The root floater feed's own content, below the shared timeline/flat item
 * paths: the inline empty scene, the Completed tile, and the "My Lists"
 * header with its rows. Lives only on [TodoListMode.FLOATER] with no
 * `listId` — every branch below re-checks [isFloaterTaskHomeScreen] (or
 * [floaterTaskHomeListRows], which is empty whenever that flag is false)
 * exactly as it did inline, so calling this unconditionally changes nothing.
 *
 * A `LazyListScope` receiver extension for the same reason as
 * [flatTodoRowsContent]: every `item`/`items` call below needs the caller's
 * scope to register its key, content type and placement correctly.
 *
 * [timelineAnimationsEnabled] feeds [displacedFeedItemMotion] exactly as it
 * did inline — this is the celebration choreography from PR #122, so nothing
 * here changes the item order, keys or placement specs those depend on. The
 * empty scene itself arrives as [emptyScene], already built and already
 * guarded; it used to be an `if` and five more parameters here.
 */
private fun LazyListScope.floaterTaskHomeRootFeedContent(
    isFloaterTaskHomeScreen: Boolean,
    timelineAnimationsEnabled: Boolean,
    emptyScene: (LazyListScope.() -> Unit)?,
    onOpenCompleted: () -> Unit,
    floaterTaskHomeListRows: List<Pair<ListSummary, Int>>,
    onOpenFloaterList: (listId: String, listName: String) -> Unit,
) {
    // The Anytime home's empty scene, built by [TodoListScreen] and merely
    // emitted here, so that it stays the FIRST item in this feed.
    //
    // Handed over as a lambda rather than as the six values it needs, for the
    // reason `earlierSceneContent` is handed over the same way and for one
    // more of its own: the `MutableTransitionState` that plays its exit has to
    // be remembered ABOVE the guard that mounts it, and `LazyListScope` is not
    // a composition to remember anything in. Null when the scene is neither
    // visible nor still leaving — the guard itself, made up there; see
    // [shouldMountFloaterEmptyScene].
    emptyScene?.invoke(this)

    // Floater tab's nav entry to the browsable Completed screen — the
    // todo side's own root feed reaches it through an identical
    // CategoryCard tile (ScheduledTaskHomeScreen's CategoryGrid); this
    // is the same shared component and the same destination (one
    // Completed screen renders both item types, distinguished there
    // by CompletedItem.isFloater), just placed to fit this screen's
    // single-column layout instead of a 2-up grid.
    if (isFloaterTaskHomeScreen) {
        item(
            key = "floater-completed-entry",
            contentType = "floater-completed-entry",
        ) {
            // The empty scene above opens a near-half-screen gap
            // in the slot the last row leaves, and this tile and
            // the lists under it are what that gap displaces.
            // Without the shared placement they cover that
            // distance in one frame — the snap the celebration
            // was landing in the middle of.
            CategoryCard(
                modifier = displacedFeedItemMotion(timelineAnimationsEnabled)
                    .fillMaxWidth()
                    .padding(bottom = FloaterFeedRowSpacing),
                color = TdayCompletedTileAccent,
                iconRes = R.drawable.ic_lucide_circle_check_big,
                watermarkRes = R.drawable.ic_lucide_circle_check_big,
                title = stringResource(R.string.scheduled_task_home_category_completed),
                onClick = onOpenCompleted,
            )
        }
    }

    if (floaterTaskHomeListRows.isNotEmpty()) {
        item(
            key = "floater-my-lists-header",
            contentType = "floater-list-header",
        ) {
            FloaterTaskHomeMyListsHeader(
                modifier = displacedFeedItemMotion(timelineAnimationsEnabled)
                    .padding(top = TdayDimens.SpacingXs, bottom = FloaterFeedRowSpacing),
            )
        }
        items(
            items = floaterTaskHomeListRows,
            key = { (list, _) -> "floater-list-${list.id}" },
            contentType = { "floater-list-row" },
        ) { (list, count) ->
            FloaterTaskHomeListRow(
                modifier = displacedFeedItemMotion(timelineAnimationsEnabled)
                    .padding(bottom = FloaterFeedRowSpacing),
                name = list.name,
                colorKey = list.color,
                iconKey = list.iconKey,
                count = count,
                onClick = {
                    onOpenFloaterList(
                        list.id,
                        capitalizeFirstListLetter(list.name),
                    )
                },
            )
        }
    }
}

/**
 * The sectioned timeline body: one [TodoSection] at a time, each its own
 * header, an optional drop placeholder, then its task rows. The largest and
 * highest-risk of the three LazyColumn seams pulled out of [TodoListScreen].
 *
 * Every mutation below — collapsing a header, starting/updating/ending a
 * drag, opening the create sheet from a quick-add tap, opening a swipe row,
 * requesting edit/promote/defer — is routed through a callback parameter
 * instead of assigning a `var ... by remember` property directly inside this
 * function. Each callback is bound in [TodoListScreen]'s own scope (see the
 * block directly above its `Scaffold` call), so every read/write it performs
 * resolves against the live backing state no matter when this function
 * invokes it — the same live-state guarantee those properties had before
 * this code was inline here. Moving the mutation *itself* into this
 * function, instead of only the already-bound callback, is exactly the
 * TdayApp regression this extraction is written to avoid: the write would
 * still compile, but it would land on a value copied at this function's own
 * call site rather than on the live state.
 *
 * [feedItemMotion]/[displacedFeedItemMotion], the drop placeholder's
 * `animateItem` spec, and the item keys/content types below are the PR #122
 * celebration/drag choreography and are unchanged by this extraction — same
 * calls, same order, same keys as when this loop lived inline.
 *
 * [earlierSceneContent] is the inline empty-state scene, built by
 * [TodoListScreen] and emitted from inside the loop below, immediately after
 * Earlier's own rows. It arrives as a `LazyListScope` lambda rather than as the
 * eight values the scene is composed from because none of those values is
 * anything this function knows or should learn; what it decides is only WHERE,
 * and it decides that with [earlierSceneFollowsSection] — one question, one
 * section at a time. Emitting it here rather than after the whole of this call
 * is what makes "directly under Earlier" true during a live reschedule drag as
 * well as at rest, since a drag restores the empty buckets this function would
 * otherwise put between the two.
 */
// KT-R1006 (cyclomatic complexity, reported at 33) is suppressed on this
// declaration rather than split further. Two separate facts, both worth
// writing down:
//
//   * It is unavoidable at this size, not just high. DeepSource fingerprints
//     an issue by (file, line, message), so a function that never existed on
//     `develop` reads as "introduced" the moment it appears, regardless of
//     its complexity value — pulling this loop out of [TodoListScreen] (see
//     that function's own KT-R1006 note above) could only ever relocate this
//     finding, never make it disappear on arrival.
//   * Fragmenting it further is the wrong trade here, not just an unwanted
//     one. The header item, the drop-placeholder item and the per-todo item
//     below carry the PR #122 celebration/drag choreography this function's
//     own doc comment above describes — splitting each into its own
//     LazyItemScope helper to shave a few complexity points would multiply
//     the function boundaries that section/drag state and `animateItem`
//     specs have to keep crossing correctly, on a screen with no Compose UI
//     test coverage to catch a mistake. The 33 is real (five collapsible-
//     section rules, a drag/drop eligibility check, and three item kinds
//     sharing one loop), but it is complexity this loop always had —
//     extraction only gave it its own line number.
//
// One declaration, one issue code — the narrowest form the tool has, and the
// style the repo already uses for KT-W1042, KT-C1001, and TodoListScreen's
// own KT-R1006 above. Never file-wide.
private fun LazyListScope.sectionedTimelineContent( // skipcq: KT-R1006
    uiState: TodoListUiState,
    timelineSections: List<TodoSection>,
    usesRootFeedChrome: Boolean,
    usesTodayStyle: Boolean,
    timelineAnimationsEnabled: Boolean,
    earlierSceneContent: (LazyListScope.() -> Unit)?,
    scopedSearchActive: Boolean,
    canRescheduleTasks: Boolean,
    isViewerList: Boolean,
    selectionActive: Boolean,
    selectedTodoIds: Set<String>,
    flashTodoId: String?,
    swipeSlot: TaskSwipeSlot,
    collapsedSectionKeys: Set<String>,
    activeDropSectionKey: String?,
    draggedScheduledTodo: TodoItem?,
    restingFloatersEnabled: Boolean,
    timelineDropTargetBounds: MutableMap<String, TimelineDropTargetBounds>,
    canDropTodoInTimelineSection: (TodoItem, TodoSection) -> Boolean,
    onSectionHeaderToggle: (key: String, wasCollapsed: Boolean) -> Unit,
    onQuickAdd: (dueEpochMs: Long) -> Unit,
    onToggleTodoSelected: (TodoItem) -> Unit,
    onComplete: (TodoItem) -> Unit,
    onDelete: (TodoItem) -> Unit,
    onEditRequested: (todoId: String) -> Unit,
    onPromoteRequested: (todoId: String) -> Unit,
    onDemoteTodo: (TodoItem) -> Unit,
    onDeferRequested: (todoId: String) -> Unit,
    onDragStart: (todo: TodoItem, position: Offset) -> Unit,
    onDragMove: (todo: TodoItem, position: Offset) -> Unit,
    onDragEnd: (position: Offset?) -> Unit,
    onDragCancel: () -> Unit,
) {
    timelineSections.forEachIndexed { sectionIndex, section ->
        val sectionHasTasks = section.items.isNotEmpty()
        val sectionModeCanCollapse = when (uiState.mode) {
            TodoListMode.ALL -> true
            TodoListMode.OVERDUE -> true
            TodoListMode.SCHEDULED -> true
            TodoListMode.PRIORITY -> section.key == EARLIER_SECTION_KEY
            TodoListMode.LIST -> section.key == EARLIER_SECTION_KEY
            TodoListMode.TODAY -> section.key == EARLIER_SECTION_KEY
            else -> false
        }
        val sectionCanCollapse = sectionModeCanCollapse && sectionHasTasks
        // A list opens with Earlier collapsed, so a live query
        // would otherwise hide the matches it just found.
        val isCollapsed = sectionCanCollapse &&
                !scopedSearchActive &&
                collapsedSectionKeys.contains(section.key)
        val isActiveDropSection = activeDropSectionKey == section.key
        val sectionDraggedTodo = if (canRescheduleTasks) {
            draggedScheduledTodo
        } else {
            null
        }
        val isDropEligibleSection = sectionDraggedTodo?.let { todo ->
            canDropTodoInTimelineSection(todo, section)
        } == true

        if (!usesRootFeedChrome) {
            item(
                key = "timeline-header-${section.key}",
                contentType = "timeline-header",
            ) {
                // A header slides with its section but never
                // fades: it is a label on content that is doing
                // its own arriving and leaving. Earlier's header
                // used to be the one exception here — it dropped
                // `placementSpec` entirely while the empty-state
                // scene sat above it, because a tween that chases
                // a target the scene was still resizing every
                // frame lags its real, already-smooth bounds for
                // the whole transition and ends up drawn over
                // them. The scene is below this header now, so
                // nothing above it resizes and there is no moving
                // target to chase. The one displacement Earlier's
                // header has left is the completion frame, where
                // the scope's own sections vanish and it
                // genuinely travels — and that one should slide,
                // like every other header in this loop.
                // `earlierHeaderSkipsPlacementSpec` went out with
                // the order that produced it rather than being
                // left green over a premise that had stopped
                // being true.
                val headerModifier =
                    displacedFeedItemMotion(enabled = timelineAnimationsEnabled)
                TimelineSectionHeader(
                    modifier = headerModifier
                        .fillMaxWidth()
                        .heightIn(min = TimelineSectionHeaderDropTargetMinHeight)
                        .timelineInAppDropTarget(
                            targetId = "header-${section.key}",
                            section = section,
                            enabled = isDropEligibleSection,
                            dropTargets = timelineDropTargetBounds,
                        )
                        .padding(top = if (sectionIndex == 0) TdayDimens.SpacingNone else TimelineSectionTopSpacing),
                    section = section,
                    useMinimalStyle = usesTodayStyle,
                    isCollapsed = isCollapsed,
                    isDropTarget = isActiveDropSection && isDropEligibleSection,
                    bottomSpacing = if (isCollapsed) {
                        TimelineCollapsedSectionSpacing
                    } else {
                        TimelineHeaderBodySpacing
                    },
                    onHeaderClick = if (sectionCanCollapse) {
                        {
                            onSectionHeaderToggle(section.key, isCollapsed)
                        }
                    } else {
                        null
                    },
                    onTapForQuickAdd = section.quickAddDefaults
                        ?.takeUnless { sectionModeCanCollapse }
                        ?.let { dueEpochMs ->
                            {
                                onQuickAdd(dueEpochMs)
                            }
                        },
                )
            }
        }

        if (canRescheduleTasks && isActiveDropSection && isDropEligibleSection && section.targetDate != null) {
            item(
                key = "timeline-drop-placeholder-${section.key}",
                contentType = "timeline-drop-placeholder",
            ) {
                var placeholderModifier: Modifier = Modifier
                if (timelineAnimationsEnabled) {
                    // The placeholder is an item in this feed like any other: it arrives,
                    // the rows under it move down, and it goes. Three numbers of its own
                    // bought it nothing except a gap that faded in and out at a different
                    // speed from everything moving around it. The placement leg is the one
                    // that never plays here — nothing displaces the gap while it is up —
                    // and it is taken whole anyway, because a site that adopts two legs of
                    // three is a site that drifts back off the third.
                    placeholderModifier = placeholderModifier.animateItem(
                        fadeInSpec = TdayFeedItemMotion.FadeIn,
                        placementSpec = TdayFeedItemMotion.Placement,
                        fadeOutSpec = TdayFeedItemMotion.FadeOut,
                    )
                }
                TimelineDropPlaceholder(
                    modifier = placeholderModifier
                        .timelineInAppDropTarget(
                            targetId = "placeholder-${section.key}",
                            section = section,
                            enabled = isDropEligibleSection,
                            dropTargets = timelineDropTargetBounds,
                        )
                        .padding(
                            bottom = TimelineDateGroupSpacing,
                        ),
                    active = true,
                    useMinimalStyle = usesTodayStyle,
                )
            }
        }

        if (!isCollapsed && section.items.isNotEmpty()) {
            val showEarlierDateTimeSubtitle =
                section.key == EARLIER_SECTION_KEY &&
                        (
                                uiState.mode == TodoListMode.ALL ||
                                        uiState.mode == TodoListMode.PRIORITY ||
                                        uiState.mode == TodoListMode.LIST ||
                                        uiState.mode == TodoListMode.TODAY
                                )
            section.items.forEachIndexed { itemIndex, todo ->
                val showTimelineDateDivider = shouldShowDateDivider(
                    afterItemIndex = itemIndex,
                    inSectionIndex = sectionIndex,
                    sections = timelineSections,
                    collapsedSectionKeys = collapsedSectionKeys,
                )
                item(
                    key = "timeline-todo-${section.key}-${todo.id}",
                    contentType = "timeline-todo",
                ) {
                    val rowModifier =
                        feedItemMotion(timelineAnimationsEnabled)
                    TimelineTaskRow(
                        modifier = rowModifier
                            .alpha(
                                restingAlphaFor(
                                    uiState.mode,
                                    todo,
                                    restingFloatersEnabled
                                )
                            )
                            .timelineInAppDropTarget(
                                targetId = "row-${section.key}-${todo.id}",
                                section = section,
                                enabled = isDropEligibleSection,
                                dropTargets = timelineDropTargetBounds,
                            )
                            .padding(
                                bottom = timelineTaskBottomSpacing(
                                    itemIndex = itemIndex,
                                    lastIndex = section.items.lastIndex,
                                    showDateDivider = showTimelineDateDivider,
                                ),
                            ),
                        todo = todo,
                        mode = uiState.mode,
                        lists = uiState.lists,
                        useMinimalStyle = usesTodayStyle,
                        flashHighlight = flashTodoId == todo.id || flashTodoId == todo.canonicalId,
                        showEarlierDateTimeSubtitle = showEarlierDateTimeSubtitle,
                        showDateDivider = showTimelineDateDivider,
                        readOnly = isViewerList,
                        selectionActive = selectionActive,
                        selected = todo.id in selectedTodoIds,
                        onToggleSelected = { onToggleTodoSelected(todo) },
                        onComplete = { onComplete(todo) },
                        onDelete = { onDelete(todo) },
                        onInfo = {
                            onEditRequested(todo.id)
                        },
                        onPromote = if (uiState.mode == TodoListMode.FLOATER) {
                            { onPromoteRequested(todo.id) }
                        } else {
                            null
                        },
                        onDemote = if (uiState.mode == TodoListMode.OVERDUE ||
                            (uiState.mode == TodoListMode.TODAY && section.key == EARLIER_SECTION_KEY)
                        ) {
                            { onDemoteTodo(todo) }
                        } else {
                            null
                        },
                        onDefer = { onDeferRequested(todo.id) },
                        draggedTodo = sectionDraggedTodo,
                        swipeSlot = swipeSlot,
                        // Long-press drag-to-reschedule stands
                        // down while selecting: a null start
                        // handler is what turns `dragEnabled`
                        // off inside the row.
                        onDragTodoStart = if (canRescheduleTasks && !selectionActive) {
                            { position -> onDragStart(todo, position) }
                        } else {
                            null
                        },
                        onDragTodoMove = { position -> onDragMove(todo, position) },
                        onDragTodoEnd = { position -> onDragEnd(position) },
                        onDragTodoCancel = onDragCancel,
                    )
                }
            }
        }

        // The inline empty-state scene, emitted after THIS section's rows when
        // this section is Earlier's own — hero, header, rows, scene. This `if`
        // is the entirety of the screen's ordering claim, and the only part of
        // it a JVM test can see; [earlierSceneFollowsSection] carries the
        // argument for why the scene sits here and not above the header it used
        // to sit above. Inside the loop rather than after it on purpose: a live
        // reschedule drag restores every empty bucket this function skips, and
        // an emission after the loop would drop the scene below a week of empty
        // headers mid-gesture.
        if (earlierSceneContent != null && earlierSceneFollowsSection(section.key)) {
            earlierSceneContent.invoke(this)
        }
    }
}

/**
 * The bar that replaces the create FAB while selecting: the four actions a bulk
 * selection can apply, and nothing else.
 */
@Composable
private fun BulkSelectionActionBar(
    completeEnabled: Boolean,
    editEnabled: Boolean,
    onComplete: () -> Unit,
    onPriority: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier
            .navigationBarsPadding()
            .fillMaxWidth()
            .padding(
                horizontal = TdayDimens.ContentPaddingHorizontal,
                vertical = TdayDimens.ContentPaddingVertical,
            ),
        shape = RoundedCornerShape(TdayDimens.RadiusField),
        border = BorderStroke(TdayDimens.BorderWidth, TdaySheetDefaults.cardStrokeColor()),
        colors = CardDefaults.cardColors(containerColor = TdaySheetDefaults.surfaceColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = BulkSelectionBarElevation),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = BulkSelectionBarVerticalPadding),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BulkSelectionAction(
                icon = R.drawable.ic_lucide_check_check,
                label = stringResource(R.string.bulk_action_complete),
                tint = TdayTaskCompleteAccent,
                enabled = completeEnabled,
                onClick = onComplete,
            )
            BulkSelectionAction(
                icon = R.drawable.ic_lucide_flag_filled,
                label = stringResource(R.string.bulk_action_priority),
                tint = colorScheme.onSurface,
                enabled = editEnabled,
                onClick = onPriority,
            )
            BulkSelectionAction(
                icon = R.drawable.ic_lucide_list,
                label = stringResource(R.string.bulk_action_move),
                tint = colorScheme.onSurface,
                enabled = editEnabled,
                onClick = onMove,
            )
            BulkSelectionAction(
                icon = R.drawable.ic_lucide_trash,
                label = stringResource(R.string.bulk_action_delete),
                tint = colorScheme.error,
                enabled = editEnabled,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun BulkSelectionAction(
    @DrawableRes icon: Int,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val contentAlpha = if (enabled) 1f else 0.34f

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(TdayDimens.RadiusRow))
            .clickable(enabled = enabled) {
                TdayHaptics.buttonPress(view)
                onClick()
            }
            .padding(horizontal = TdayDimens.SpacingXl, vertical = TdayDimens.SpacingSm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingXs),
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(icon),
            contentDescription = null,
            tint = tint.copy(alpha = contentAlpha),
            modifier = Modifier.size(BulkSelectionActionIconSize),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            maxLines = 1,
        )
    }
}

@Composable
private fun ListDeleteConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    TdayConfirmationDialog(
        title = stringResource(R.string.todos_delete_list_title),
        message = stringResource(R.string.todos_delete_list_message),
        confirmLabel = stringResource(R.string.action_delete),
        confirmColor = MaterialTheme.colorScheme.error,
        confirmIsDestructive = true,
        onDismissRequest = onDismissRequest,
        onConfirm = onConfirm,
    )
}

/**
 * The house confirmation card — full-bleed scrim, a headline, a body, Cancel on
 * the left in the primary colour and the confirm on the right in [confirmColor].
 * Shared by the list delete and by the two bulk actions that ask first, so a
 * batch delete looks and reads like every other destructive prompt in the app.
 *
 * [skippedMessage] carries the "applies to N of M" line when a bulk selection
 * held repeating occurrences the action cannot touch.
 *
 * [confirmIsDestructive] says whether this dialog's confirm button is the moment
 * something is destroyed rather than one more control on the way there. It has to
 * be told, because the button itself cannot know: the same composable commits a
 * list delete, a delete of N tasks and a move of N tasks, and only the first two
 * earn the heavy thud.
 */
@Composable
private fun TdayConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    confirmColor: Color,
    confirmIsDestructive: Boolean = false,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    skippedMessage: String? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val dialogContainerColor = TdaySheetDefaults.surfaceColor()
    val scrimColor = TdaySheetDefaults.scrimColor()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        TdaySheetFullBleedWindow()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                )
                .padding(horizontal = OverlayDialogScrimInset),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(maxWidth = OverlayDialogMaxWidth)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = TdaySheetDefaults.OverlayShape,
                border = BorderStroke(TdayDimens.BorderWidth, TdaySheetDefaults.cardStrokeColor()),
                colors = CardDefaults.cardColors(containerColor = dialogContainerColor),
                elevation = CardDefaults.cardElevation(defaultElevation = OverlayDialogElevation),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = TdayDimens.Spacing3xl,
                            top = TdayDimens.Spacing3xl,
                            end = TdayDimens.Spacing3xl,
                            bottom = OverlayDialogBottomPadding,
                        ),
                    verticalArrangement = Arrangement.spacedBy(OverlayDialogSectionSpacing),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingXl)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.16f,
                        )
                        if (skippedMessage != null) {
                            Text(
                                text = skippedMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismissRequest) {
                            Text(
                                text = stringResource(R.string.action_cancel),
                                color = colorScheme.primary,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                        Spacer(Modifier.size(OverlayDialogButtonGap))
                        TextButton(
                            onClick = {
                                // This is the tap that destroys; the one that
                                // opened this dialog only asked. The heavy thud
                                // belongs here, where Cancel has stopped being an
                                // option.
                                if (confirmIsDestructive) {
                                    TdayHaptics.destructive(view)
                                } else {
                                    TdayHaptics.buttonPress(view)
                                }
                                onConfirm()
                            },
                        ) {
                            Text(
                                text = confirmLabel,
                                color = confirmColor,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloaterTaskHomeSearchResultsCard(
    results: List<TodoItem>,
    listsById: Map<String, ListSummary>,
    onOpenTodo: (TodoItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(TdayDimens.RadiusField),
        border = BorderStroke(TdayDimens.BorderWidth, colorScheme.onSurface.copy(alpha = 0.2f)),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = SearchResultsOverlayElevation),
    ) {
        if (results.isEmpty()) {
            Text(
                text = stringResource(R.string.scheduled_task_home_search_no_results),
                modifier = Modifier.padding(
                    horizontal = TdayDimens.SpacingXl,
                    vertical = TdayDimens.SpacingLg,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = SearchResultsMaxHeight),
                contentPadding = PaddingValues(vertical = TdayDimens.SpacingXs),
            ) {
                items(
                    items = results,
                    key = { todo -> todo.id },
                ) { todo ->
                    val listMeta = todo.listId?.let { listsById[it] }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {}
                            .heightIn(min = MinTouchTargetSize)
                            .clickable { onOpenTodo(todo) }
                            .padding(
                                horizontal = TdayDimens.SpacingLg,
                                vertical = SearchResultRowVerticalPadding,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(SearchResultRowSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = tdayListIconForKey(listMeta?.iconKey),
                            contentDescription = null,
                            tint = tdayListAccentColor(listMeta?.color).copy(alpha = 0.92f),
                            modifier = Modifier.size(SearchResultIconSize),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = todo.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                text = listMeta?.name
                                    ?: stringResource(priorityDisplayLabelRes(todo.priority)),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloaterTaskHomeMyListsHeader(
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.scheduled_task_home_my_lists),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.ExtraBold,
        modifier = modifier,
    )
}

@Composable
private fun FloaterTaskHomeListRow(
    modifier: Modifier = Modifier,
    name: String,
    colorKey: String?,
    iconKey: String?,
    count: Int,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val accent = tdayListAccentColor(colorKey)
    val icon = tdayListIconForKey(iconKey)
    val containerColor =
        lerpColor(colorScheme.surfaceVariant, accent, FLOATER_TASK_HOME_LIST_CONTAINER_COLOR_WEIGHT)
    val displayName = capitalizeFirstListLetter(name)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(ListRowHeight)
            .semantics(mergeDescendants = true) {}
            .tdayPressable(interactionSource, scale = TdayMotionTokens.PressScales.Row),
        onClick = {
            TdayHaptics.buttonPress(view)
            onClick()
        },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(TdayDimens.RadiusCard),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        // Was a third `animateDpAsState` off the same press fed into both slots.
        // `cardElevation` holds exactly this pair and animates between them.
        elevation = CardDefaults.cardElevation(
            defaultElevation = ListRowElevation,
            pressedElevation = PressedCardElevation,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = lerpColor(containerColor, Color.White, 0.34f).copy(alpha = 0.42f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = ListRowWatermarkOffsetX, y = ListRowWatermarkOffsetY)
                    .size(ListRowWatermarkSize),
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = FeedCardHorizontalPadding, vertical = TdayDimens.SpacingLg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(ListRowIconSize),
                    )
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = TdayDimens.SpacingMd),
                    )
                }
                Text(
                    text = count.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = TdayDimens.SpacingLg),
                )
            }
        }
    }
}

@Composable
private fun rememberTodoRootIsDaytime(): Boolean {
    var hour by remember { mutableStateOf(LocalTime.now().hour) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalTime.now()
            val millisToNextMinute = ((60 - now.second) * 1000L) - (now.nano / 1_000_000L)
            delay(millisToNextMinute.coerceAtLeast(500L))
            hour = LocalTime.now().hour
        }
    }

    return hour in 6 until 18
}

private data class TodoTopBarAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
)

@Composable
private fun TodayTitleLabel(
    text: String,
    color: Color,
    icon: ImageVector?,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(TdayDimens.SpacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(TdayDimens.IconMd),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TodayHeaderButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    iconSize: Dp = TdayDimens.IconXl,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    // The same fill the back button beside it carries. These were painted with
    // `background` and a hairline instead, which on a bar whose own strip is
    // that colour left them as outlines next to a solid white circle.
    val containerColor = tdayBarButtonContainerColor()
    val iconTint = MaterialTheme.colorScheme.onSurface
    val buttonSize = TdayDimens.FabSize

    Card(
        modifier = Modifier
            .tdayPressable(interactionSource, scale = TdayMotionTokens.PressScales.Bar),
        onClick = {
            TdayHaptics.buttonPress(view)
            onClick()
        },
        interactionSource = interactionSource,
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        // Every circle in a bar carries the same lift, back button or not —
        // half of them having no shadow at all was the giveaway that these were
        // three separate implementations.
        elevation = CardDefaults.cardElevation(
            defaultElevation = TdayDimens.BarButtonElevation,
            pressedElevation = TdayDimens.CardElevationDefault,
        ),
    ) {
        Box(
            modifier = Modifier.size(buttonSize),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryBottomSheet(
    isLoading: Boolean,
    summaryText: String?,
    summarySource: String?,
    aiSummaryConfigured: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme

    TdayModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    horizontal = TdayDimens.ContentPaddingHorizontal,
                    vertical = TdayDimens.ContentPaddingVertical,
                ),
            verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingXl),
        ) {
            TdaySheetHeader(
                title = stringResource(R.string.todos_summary_title),
                leftIcon = ImageVector.vectorResource(R.drawable.ic_lucide_x),
                leftContentDescription = stringResource(R.string.todos_summary_close),
                onLeftClick = onDismiss,
                showConfirmAction = false,
            )

            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TdayDimens.SpacingLg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(SummarySpinnerSize),
                        strokeWidth = SummarySpinnerStroke,
                    )
                    Text(
                        text = stringResource(R.string.todos_summary_loading),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!summaryText.isNullOrBlank()) {
                TdaySheetCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = TdayDimens.SpacingXl, vertical = TdayDimens.SpacingXl),
                        verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingMd),
                    ) {
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colorScheme.onSurface,
                        )
                        if (aiSummaryConfigured) {
                            Text(
                                text = if (summarySource == "ai") {
                                    stringResource(R.string.summary_source_server)
                                } else {
                                    stringResource(R.string.summary_source_local)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurface.copy(alpha = 0.58f),
                            )
                        }
                    }
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CreateTaskButton(
    interactionSource: MutableInteractionSource,
    backgroundColor: Color,
    onClick: () -> Unit,
) {
    val view = LocalView.current

    Card(
        // The press sits with the Card that owns the source rather than being
        // handed in from the Scaffold slot, which is where `RootCreateTaskButton`
        // keeps its own. `FabScale`, not `PressScales.Bar`: this is the other of
        // the two Android FABs that were each spelling 0.93 out.
        modifier = Modifier.tdayPressable(interactionSource, scale = TdayPress.FabScale),
        onClick = {
            TdayHaptics.buttonPress(view)
            onClick()
        },
        interactionSource = interactionSource,
        shape = CircleShape,
        border = BorderStroke(TdayDimens.BorderWidth, backgroundColor.copy(alpha = 0.72f)),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = TdayDimens.FabElevation,
            pressedElevation = TdayDimens.FabPressedElevation,
        ),
    ) {
        Box(
            modifier = Modifier.size(TdayDimens.FabSize),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_plus),
                contentDescription = stringResource(R.string.action_create_task),
                tint = Color.White,
                modifier = Modifier.size(TdayDimens.FabIconSize),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListSettingsBottomSheet(
    title: String,
    listName: String,
    onListNameChange: (String) -> Unit,
    listColor: String,
    onListColorChange: (String) -> Unit,
    listIconKey: String,
    onListIconChange: (String) -> Unit,
    showDelete: Boolean = true,
    onShare: (() -> Unit)? = null,
    onMembers: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val colorScheme = MaterialTheme.colorScheme
    val selectedAccent = tdayListAccentColor(listColor)
    val selectedIcon = tdayListIconForKey(listIconKey)
    val canSave = listName.isNotBlank()

    TdayModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(
                        horizontal = TdayDimens.ContentPaddingHorizontal,
                        vertical = TdayDimens.ContentPaddingVertical,
                    ),
                verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingXl),
            ) {
                TdaySheetHeader(
                    title = title,
                    leftIcon = ImageVector.vectorResource(R.drawable.ic_lucide_x),
                    leftContentDescription = stringResource(R.string.action_close),
                    onLeftClick = {
                        focusManager.clearFocus(force = true)
                        onDismiss()
                    },
                    confirmContentDescription = stringResource(R.string.todos_save_list_settings),
                    onConfirm = {
                        focusManager.clearFocus(force = true)
                        if (canSave) onSave()
                    },
                    confirmEnabled = canSave,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingXl),
                ) {
                    TdaySheetSectionTitle(
                        text = stringResource(R.string.scheduled_task_home_section_list),
                    )
                    TdaySheetCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = TdayDimens.SpacingXxl, vertical = TdayDimens.SpacingXxl),
                            verticalArrangement = Arrangement.spacedBy(ListCardSpacing),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(ListIconPreviewSize)
                                    .background(selectedAccent, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = selectedIcon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(ListIconPreviewGlyphSize),
                                )
                            }

                            BasicTextField(
                                value = listName,
                                onValueChange = onListNameChange,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus(force = true)
                                        if (canSave) onSave()
                                    },
                                ),
                                textStyle = MaterialTheme.typography.headlineSmall.copy(
                                    color = selectedAccent,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onPreviewKeyEvent { event ->
                                        if (
                                            event.type == KeyEventType.KeyUp &&
                                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                                        ) {
                                            keyboardController?.hide()
                                            focusManager.clearFocus(force = true)
                                            if (canSave) onSave()
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                decorationBox = { innerTextField ->
                                    Box(
                                        // RadiusRow and not RadiusField, which is the rung a
                                        // text field would otherwise take: this one has always
                                        // been drawn at 16 dp, and rounding it up to 22 would
                                        // be a redraw rather than a name. Same call and same
                                        // reason as the create-list field in the root feed.
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                TdaySheetDefaults.controlSurfaceColor(),
                                                RoundedCornerShape(TdayDimens.RadiusRow)
                                            )
                                            .padding(horizontal = TdayDimens.SpacingXl, vertical = TdayDimens.SpacingLg),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (listName.isBlank()) {
                                            Text(
                                                text = stringResource(R.string.scheduled_task_home_list_name_placeholder),
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                                fontWeight = FontWeight.ExtraBold,
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            innerTextField()
                                        }
                                    }
                                },
                            )
                        }
                    }

                    TdaySheetSectionTitle(
                        text = stringResource(R.string.scheduled_task_home_section_color),
                    )
                    TdaySheetCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = TdayDimens.SpacingXl, vertical = TdayDimens.SpacingXl),
                            horizontalArrangement = Arrangement.spacedBy(TdayDimens.SpacingLg),
                        ) {
                            TdayListColorOptions.forEach { option ->
                                val colorKey = option.key
                                val selected = listColor == colorKey
                                val swatchColor = option.color
                                val interactionSource = remember { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .sizeIn(minWidth = MinTouchTargetSize, minHeight = MinTouchTargetSize)
                                        .wrapContentSize(Alignment.Center)
                                        .size(ListColorSwatchSize)
                                        .clip(CircleShape)
                                        .background(swatchColor, CircleShape)
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = ripple(
                                                bounded = true,
                                                radius = ListColorSwatchRippleRadius,
                                            ),
                                        ) { onListColorChange(colorKey) }
                                        .then(
                                            if (selected) {
                                                Modifier.border(
                                                    width = ListColorSwatchSelectedOutline,
                                                    color = colorScheme.onBackground.copy(alpha = 0.32f),
                                                    shape = CircleShape,
                                                )
                                            } else {
                                                Modifier
                                            }
                                        ),
                                )
                            }
                        }
                    }

                    TdaySheetSectionTitle(
                        text = stringResource(R.string.scheduled_task_home_section_icon),
                    )
                    TdaySheetCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = TdayDimens.SpacingXl, vertical = TdayDimens.SpacingXl),
                            horizontalArrangement = Arrangement.spacedBy(ListIconOptionSpacing),
                        ) {
                            TdayListIconOptions.forEach { option ->
                                val selected = listIconKey == option.key
                                val interactionSource = remember { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .size(ListIconSwatchSize)
                                        .clip(CircleShape)
                                        .background(
                                            color = if (selected) {
                                                selectedAccent.copy(alpha = 0.2f)
                                            } else {
                                                TdaySheetDefaults.controlSurfaceColor()
                                            },
                                            shape = CircleShape,
                                        )
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = ripple(
                                                bounded = true,
                                                radius = ListIconSwatchRippleRadius,
                                            ),
                                        ) { onListIconChange(option.key) }
                                        .then(
                                            if (selected) {
                                                Modifier.border(
                                                    width = ListIconSwatchSelectedOutline,
                                                    color = selectedAccent.copy(alpha = 0.55f),
                                                    shape = CircleShape,
                                                )
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(option.iconRes),
                                        contentDescription = stringResource(R.string.scheduled_task_home_section_icon),
                                        tint = if (selected) selectedAccent else colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    if (onShare != null || onMembers != null) {
                        TdaySheetSectionTitle(
                            text = stringResource(R.string.share_section_title),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(ListSettingsActionTileSpacing)) {
                            if (onMembers != null) {
                                ListSettingsActionTile(
                                    icon = ImageVector.vectorResource(R.drawable.ic_lucide_users_round),
                                    label = stringResource(R.string.members_title),
                                    onClick = onMembers,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (onShare != null) {
                                ListSettingsActionTile(
                                    icon = ImageVector.vectorResource(R.drawable.ic_lucide_share_2),
                                    label = stringResource(R.string.action_share),
                                    onClick = onShare,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(TdayDimens.SpacingXxs))
                    if (showDelete) {
                        ListSettingsDeleteButton(onClick = onDelete)
                    }
                }
            }
        }
    }
}

/**
 * Half-width tile used by the Sharing section of the list settings sheet —
 * "Members" (collaboration) and "Share" (external share sheet) side by side.
 */
@Composable
private fun ListSettingsActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        // `offsetY = TdayDimens.SpacingNone`: these two tiles sit side by side inside the
        // sheet and never had a sink. A tile dropping while the one beside it holds
        // still reads as the pair misaligning, not as a tile going down.
        modifier = modifier
            .tdayPressable(
                interactionSource,
                scale = TdayMotionTokens.PressScales.Card,
                offsetY = TdayDimens.SpacingNone,
            ),
        onClick = {
            TdayHaptics.buttonPress(view)
            onClick()
        },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(TdayDimens.RadiusXl),
        border = BorderStroke(TdayDimens.BorderWidthThick, colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(
            containerColor = TdaySheetDefaults.controlSurfaceColor(),
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = TdayDimens.CardElevationDefault,
            pressedElevation = TdayDimens.CardElevationDefault,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TdayDimens.SpacingXl, vertical = TdayDimens.SpacingXl),
            horizontalArrangement = Arrangement.spacedBy(ListSettingsActionContentSpacing, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ListSettingsDeleteButton(
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        // Flat, full width, and the last thing in the sheet: `offsetY =
        // TdayDimens.SpacingNone` for the same reason the tiles above it take it.
        modifier = Modifier
            .fillMaxWidth()
            .tdayPressable(
                interactionSource,
                scale = TdayMotionTokens.PressScales.Card,
                offsetY = TdayDimens.SpacingNone,
            ),
        onClick = {
            // Opens the confirmation, destroys nothing — Cancel is still there.
            // The thud is fired by the dialog's confirm button instead.
            TdayHaptics.buttonPress(view)
            onClick()
        },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(TdayDimens.RadiusXl),
        border = BorderStroke(TdayDimens.BorderWidthThick, colorScheme.error.copy(alpha = 0.45f)),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.error.copy(
                alpha = if (TdaySheetDefaults.isDarkTheme()) 0.14f else 0.04f,
            ),
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = TdayDimens.CardElevationDefault,
            pressedElevation = TdayDimens.CardElevationDefault,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ListSettingsDeleteHorizontalPadding, vertical = TdayDimens.SpacingXl),
            horizontalArrangement = Arrangement.spacedBy(TdayDimens.SpacingLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_trash_2),
                contentDescription = null,
                tint = colorScheme.error,
            )
            Text(
                text = stringResource(R.string.action_delete_list),
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.error,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun TimelineSectionHeader(
    modifier: Modifier = Modifier,
    section: TodoSection,
    useMinimalStyle: Boolean,
    isCollapsed: Boolean = false,
    isDropTarget: Boolean,
    bottomSpacing: Dp,
    onHeaderClick: (() -> Unit)? = null,
    onTapForQuickAdd: (() -> Unit)?,
) {
    if (section.title.isEmpty()) return

    val colorScheme = MaterialTheme.colorScheme
    val headerInteractionSource = remember { MutableInteractionSource() }
    val isHeaderPressed by headerInteractionSource.collectIsPressedAsState()
    val collapseChevronRotation by animateFloatAsState(
        targetValue = if (isCollapsed) -90f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "sectionChevronRotation",
    )
    val animatedBottomSpacing by animateDpAsState(
        targetValue = bottomSpacing,
        // A spacing is a size, and the rung for a size is Emphasis. The 240 it was
        // written on named nothing and was shared with nothing.
        animationSpec = tween(
            durationMillis = TdayMotionTokens.Durations.Emphasis,
            easing = FastOutSlowInEasing,
        ),
        label = "sectionBottomSpacing",
    )
    val baseHeaderColor = if (useMinimalStyle) {
        colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
    } else {
        colorScheme.onSurfaceVariant
    }
    val headerTextColor = if (isHeaderPressed) {
        lerpColor(baseHeaderColor, colorScheme.onSurface, 0.16f)
    } else if (isDropTarget) {
        colorScheme.error
    } else {
        baseHeaderColor
    }
    val baseChevronColor =
        colorScheme.onSurfaceVariant.copy(alpha = if (useMinimalStyle) 0.72f else 1f)
    val chevronColor = if (isHeaderPressed) {
        lerpColor(baseChevronColor, colorScheme.onSurface, 0.16f)
    } else {
        baseChevronColor
    }
    val minimumHeaderHeight = if (useMinimalStyle) {
        TimelineSectionHeaderMinHeightMinimal
    } else {
        TimelineSectionHeaderMinHeight
    }
    val headerClickModifier = when {
        onHeaderClick != null -> Modifier.clickable(
            interactionSource = headerInteractionSource,
            indication = null,
            onClick = onHeaderClick,
        )

        onTapForQuickAdd != null -> Modifier.clickable(
            interactionSource = headerInteractionSource,
            indication = null,
            onClick = onTapForQuickAdd,
        )

        else -> Modifier
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colorScheme.background)
            .padding(bottom = animatedBottomSpacing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(TdayDimens.RadiusLg))
                .background(Color.Transparent)
                .padding(horizontal = TdayDimens.SpacingXs)
                .heightIn(min = minimumHeaderHeight)
                .then(headerClickModifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = localizedSectionTitle(section),
                color = headerTextColor,
                style = if (useMinimalStyle) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = FontWeight.ExtraBold,
            )
            if (onHeaderClick != null) {
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
                        .size(TimelineSectionChevronSize)
                        .graphicsLayer {
                            rotationZ = collapseChevronRotation
                        },
                )
            }
        }
    }
}

@Composable
private fun TimelineDropPlaceholder(
    modifier: Modifier = Modifier,
    active: Boolean,
    useMinimalStyle: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    val placeholderHeight by animateDpAsState(
        targetValue = if (active) {
            if (useMinimalStyle) {
                TimelineDropPlaceholderActiveHeightMinimal
            } else {
                TimelineDropPlaceholderActiveHeight
            }
        } else {
            if (useMinimalStyle) {
                TimelineDropPlaceholderHeightMinimal
            } else {
                TimelineDropPlaceholderHeight
            }
        },
        // The box's own size, so the same rung as the section spacing above. It plays
        // seldom — the one caller always passes `active = true`, which leaves the style
        // flag as the only thing that can move the target — but 180 named no rung, and
        // when it does play it must not undercut the placement the rows around it take.
        animationSpec = tween(
            durationMillis = TdayMotionTokens.Durations.Emphasis,
            easing = FastOutSlowInEasing,
        ),
        label = "timelineDropPlaceholderHeight",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(placeholderHeight)
            .clip(RoundedCornerShape(TdayDimens.RadiusLg))
            .background(
                if (active) {
                    colorScheme.error.copy(alpha = 0.10f)
                } else {
                    colorScheme.surfaceVariant.copy(alpha = 0.16f)
                },
            )
            .border(
                BorderStroke(
                    width = if (active) TdayDimens.BorderWidthThick else TdayDimens.BorderWidth,
                    color = if (active) {
                        colorScheme.error.copy(alpha = 0.64f)
                    } else {
                        colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
                    },
                ),
                RoundedCornerShape(TdayDimens.RadiusLg),
            ),
    )
}

@Composable
private fun TimelineTaskDragPreview(
    modifier: Modifier = Modifier,
    todo: TodoItem,
    lists: List<ListSummary>,
    mode: TodoListMode,
) {
    val colorScheme = MaterialTheme.colorScheme
    val listMeta = todo.listId?.let { listId -> lists.firstOrNull { it.id == listId } }
    val showListIndicator = listMeta != null && mode != TodoListMode.LIST
    val previewShape = RoundedCornerShape(TdayDimens.RadiusLg)
    // The pick-up itself. This card used to be composed straight into its final
    // size and elevation, so the one frame that says "the app has your task"
    // never existed; [TdayDragLift] argues the rise and its two ends.
    val lift by TdayDragLift.rememberProgress(rememberTdayMotionEnabled())
    Card(
        modifier = modifier
            .sizeIn(minWidth = TimelineDragPreviewMinWidth, maxWidth = TimelineDragPreviewMaxWidth)
            .graphicsLayer {
                val scale = TdayDragLift.scaleAt(lift)
                scaleX = scale
                scaleY = scale
            },
        shape = previewShape,
        // Opaque. A card the finger is holding is not a card the user may not
        // have, and partial alpha is what this app says everywhere else.
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(TdayDimens.BorderWidth, colorScheme.outlineVariant.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = TdayDragLift.elevationAt(lift)),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = TdayDimens.SpacingXl,
                vertical = TimelineDragPreviewContentSpacing,
            ),
            horizontalArrangement = Arrangement.spacedBy(TimelineDragPreviewContentSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_circle),
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                modifier = Modifier.size(TimelineDragPreviewIconSize),
            )
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingXxs),
            ) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                )
                todo.due?.let(TODO_DUE_TIME_FORMATTER::format)?.let { dueText ->
                    Text(
                        text = dueText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            if (showListIndicator) {
                Icon(
                    imageVector = tdayListIconForKey(listMeta?.iconKey),
                    contentDescription = null,
                    tint = tdayListAccentColor(listMeta?.color),
                    modifier = Modifier.size(RowTrailingIconSize),
                )
            }
            priorityIconFor(todo.priority)?.let { priorityIcon ->
                Icon(
                    imageVector = priorityIcon,
                    contentDescription = null,
                    tint = tdayPriorityColor(todo.priority),
                    modifier = Modifier.size(RowTrailingIconSize),
                )
            }
        }
    }
}

@Composable
private fun TimelineTaskRow(
    modifier: Modifier = Modifier,
    todo: TodoItem,
    mode: TodoListMode,
    lists: List<ListSummary>,
    useMinimalStyle: Boolean,
    flashHighlight: Boolean,
    showEarlierDateTimeSubtitle: Boolean,
    showDateDivider: Boolean,
    readOnly: Boolean = false,
    selectionActive: Boolean = false,
    selected: Boolean = false,
    onToggleSelected: () -> Unit = {},
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    onPromote: (() -> Unit)? = null,
    onDemote: (() -> Unit)? = null,
    onDefer: (() -> Unit)? = null,
    draggedTodo: TodoItem? = null,
    swipeSlot: TaskSwipeSlot,
    onDragTodoStart: ((Offset) -> Unit)? = null,
    onDragTodoMove: (Offset) -> Unit = {},
    onDragTodoEnd: (Offset?) -> Unit = {},
    onDragTodoCancel: () -> Unit = {},
) {
    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        if (mode == TodoListMode.ALL) {
            AllTaskSwipeRow(
                todo = todo,
                lists = lists,
                flashHighlight = flashHighlight,
                selectionActive = selectionActive,
                selected = selected,
                onToggleSelected = onToggleSelected,
                onComplete = onComplete,
                onDelete = onDelete,
                onInfo = onInfo,
                showDuePrefix = true,
                showDueDateInSubtitle = showEarlierDateTimeSubtitle,
                showDateDivider = showDateDivider,
                dragEnabled = onDragTodoStart != null,
                dragging = draggedTodo?.id == todo.id,
                onDragStart = { position -> onDragTodoStart?.invoke(position) },
                onDragMove = onDragTodoMove,
                onDragEnd = onDragTodoEnd,
                onDragCancel = onDragTodoCancel,
                swipeSlot = swipeSlot,
            )
        } else if (
            useMinimalStyle &&
            (
                    mode == TodoListMode.TODAY ||
                            mode == TodoListMode.OVERDUE ||
                            mode == TodoListMode.SCHEDULED ||
                            mode == TodoListMode.PRIORITY ||
                            mode == TodoListMode.FLOATER ||
                            mode == TodoListMode.LIST
                    )
        ) {
            TodayTaskSwipeRow(
                todo = todo,
                mode = mode,
                lists = lists,
                flashHighlight = flashHighlight,
                readOnly = readOnly,
                selectionActive = selectionActive,
                selected = selected,
                onToggleSelected = onToggleSelected,
                onComplete = onComplete,
                onDelete = onDelete,
                onInfo = onInfo,
                onPromote = onPromote,
                onDemote = onDemote,
                onDefer = onDefer,
                showDuePrefix = true,
                showDueDateInSubtitle = showEarlierDateTimeSubtitle,
                showDateDivider = showDateDivider,
                dragEnabled = onDragTodoStart != null,
                dragging = draggedTodo?.id == todo.id,
                onDragStart = { position -> onDragTodoStart?.invoke(position) },
                onDragMove = onDragTodoMove,
                onDragEnd = onDragTodoEnd,
                onDragCancel = onDragTodoCancel,
                swipeSlot = swipeSlot,
            )
        } else if (useMinimalStyle) {
            TodayTodoRow(
                todo = todo,
                onComplete = onComplete,
                onDelete = onDelete,
            )
        } else {
            TodoRow(
                todo = todo,
                onComplete = onComplete,
                onDelete = onDelete,
            )
        }
    }
}

private fun Modifier.timelineInAppDropTarget(
    targetId: String,
    section: TodoSection,
    enabled: Boolean,
    dropTargets: MutableMap<String, TimelineDropTargetBounds>,
): Modifier {
    if (!enabled || section.targetDate == null) {
        return this
    }

    return composed {
        DisposableEffect(targetId) {
            onDispose {
                dropTargets.remove(targetId)
            }
        }
        onGloballyPositioned { coordinates ->
            val position = coordinates.positionInRoot()
            val size = coordinates.size
            dropTargets[targetId] = TimelineDropTargetBounds(
                sectionKey = section.key,
                bounds = Rect(
                    left = position.x,
                    top = position.y,
                    right = position.x + size.width,
                    bottom = position.y + size.height,
                ),
            )
        }
    }
}

private data class TimelineDropTargetBounds(
    val sectionKey: String,
    val bounds: Rect,
)

private data class TimelineInAppDrag(
    val todo: TodoItem,
    val position: Offset,
)

// internal, not private: exercised directly by TodoTimelineSectionsTest so the
// "zero pending today" section-building rules (requirement 2's core logic)
// have real unit coverage instead of only a device/emulator check.
internal data class TodoSection(
    val key: String,
    val title: String,
    val items: List<TodoItem>,
    val quickAddDefaults: Long? = null,
    val targetDate: LocalDate? = null,
    // Today buckets only: the hour a task is set to when dropped here (Morning 9 /
    // Afternoon 15 / Tonight 20). null for normal date sections.
    val targetHour: Int? = null,
)

private data class TaskRescheduleDrop(
    val todo: TodoItem,
    val targetDate: LocalDate? = null,
    val targetHour: Int? = null,
)

private fun shouldShowDateDivider(
    afterItemIndex: Int,
    inSectionIndex: Int,
    sections: List<TodoSection>,
    collapsedSectionKeys: Set<String>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean {
    val section = sections.getOrNull(inSectionIndex) ?: return false
    val currentTodo = section.items.getOrNull(afterItemIndex) ?: return false
    val nextTodoInSection = section.items.getOrNull(afterItemIndex + 1)
    if (nextTodoInSection != null) {
        val currentDue = currentTodo.due ?: return false
        val nextDue = nextTodoInSection.due ?: return false
        return !currentDue.isSameLocalDayAs(nextDue, zoneId)
    }

    val nextVisibleTodo = sections
        .asSequence()
        .drop(inSectionIndex + 1)
        .filter { it.key !in collapsedSectionKeys }
        .flatMap { it.items.asSequence() }
        .firstOrNull()
        ?: return false

    val currentDue = currentTodo.due ?: return false
    val nextDue = nextVisibleTodo.due ?: return false
    return !currentDue.isSameLocalDayAs(nextDue, zoneId)
}

private fun Instant.isSameLocalDayAs(other: Instant, zoneId: ZoneId): Boolean =
    LocalDate.ofInstant(this, zoneId) == LocalDate.ofInstant(other, zoneId)

private enum class TodaySectionSlot {
    MORNING, AFTERNOON, TONIGHT,
}

internal fun buildTimelineSections(
    mode: TodoListMode,
    items: List<TodoItem>,
    isDragActive: Boolean,
    earlierItems: List<TodoItem> = emptyList(),
): List<TodoSection> {
    val zoneId = ZoneId.systemDefault()
    val sections = when (mode) {
        TodoListMode.TODAY -> buildTodaySections(items, earlierItems, zoneId)
        TodoListMode.OVERDUE -> buildOverdueSections(items, zoneId)
        TodoListMode.SCHEDULED -> buildScheduledSections(
            items = items,
            zoneId = zoneId,
            futureOnly = true,
        )

        TodoListMode.ALL -> buildScheduledSections(
            items = items,
            zoneId = zoneId,
            futureOnly = false,
            placesEarlierBeforeToday = true,
        )

        TodoListMode.PRIORITY -> buildScheduledSections(
            items = items,
            zoneId = zoneId,
            futureOnly = false,
            placesEarlierBeforeToday = true,
        )

        TodoListMode.FLOATER -> buildFloaterSections(items)

        TodoListMode.LIST -> buildScheduledSections(
            items = items,
            zoneId = zoneId,
            futureOnly = false,
            placesEarlierBeforeToday = true,
        )
    }

    // Morning / Afternoon / Tonight are the shape of the day itself rather than a
    // scaffold of dates, and each one is where a quick-add lands, so Today keeps
    // all three whether or not they hold anything -- but only once the day has
    // something in it somewhere. With the whole day empty there is nothing left
    // to shape, and the three headers would float above the empty-state scene
    // with nothing under any of them; that case defers to the same "no sections
    // at all" rule every other scope already gets below. Checked on the built
    // sections rather than the raw `items` so this stays correct regardless of
    // whatever `buildTodaySections` itself filters out before bucketing.
    //
    // "earlier" answers to a different rule than the other three: it is not
    // part of the day's own shape, so it is kept whenever it holds tasks —
    // Morning/Afternoon/Tonight being empty says nothing about whether
    // there is still a backlog collapsed underneath. That is requirement 2:
    // the "day is empty" illustration cares about pending-today only, and
    // Earlier staying reachable while it shows is requirement 3.
    //
    // The "nothing in the day, so no headers" rule above describes the screen
    // at REST. A drag is not rest: the task in hand has to have somewhere to
    // land, and for Today the only somewheres are exactly these three buckets.
    // Hiding them because they are empty is self-defeating -- an empty bucket
    // is precisely the one a task is being dragged INTO -- and it is what left
    // an Earlier row with no drop target at all on a day whose pending list had
    // just been emptied: pick the row up, and there was nothing on screen that
    // could catch it. So a live drag restores all three for its duration, the
    // same exception, for the same reason, that the general rule below already
    // makes for every other scope's empty date buckets. Both restores are keyed
    // on the one `isDragActive` flag, so the buckets appear and leave together
    // with the gesture rather than on any state of their own.
    if (mode == TodoListMode.TODAY) {
        val timeOfDaySections = sections.filterNot { section -> section.key == EARLIER_SECTION_KEY }
        val earlierSection = sections.firstOrNull { section -> section.key == EARLIER_SECTION_KEY }
        val visibleTimeOfDay = if (isDragActive || timeOfDaySections.any { it.items.isNotEmpty() }) {
            timeOfDaySections
        } else {
            emptyList()
        }
        return if (earlierSection != null && earlierSection.items.isNotEmpty()) {
            visibleTimeOfDay + earlierSection
        } else {
            visibleTimeOfDay
        }
    }

    // The one rule for every other scope, Earlier and "Rest of <month>" included:
    // a section earns its header by holding tasks, or by being somewhere the task
    // in hand can be dropped. `targetDate` is what makes a bucket droppable, so a
    // section without one (an overdue day, a floater group) is only ever kept by
    // its tasks.
    // "earlier" is excluded from the drag-time restore: its target date is
    // yesterday, and web never builds an empty Earlier at all, so keeping it
    // would make rescheduling INTO the past possible here and not there.
    return sections.filter { section ->
        section.items.isNotEmpty() ||
                (isDragActive && section.targetDate != null && section.key != EARLIER_SECTION_KEY)
    }
}

// Maps a domain TodoItem onto the shared, platform-neutral sort key so every
// feed and widget orders tasks through the one TaskSortEngine.
private fun TodoItem.toTaskSortKey(): TaskSortKey = TaskSortKey(
    id = id,
    pinned = pinned,
    dueEpochMs = due?.toEpochMilli(),
    priorityRank = TaskSortEngine.priorityRank(priority),
    updatedAtEpochMs = updatedAt?.toEpochMilli(),
)

private fun buildOverdueSections(
    items: List<TodoItem>,
    zoneId: ZoneId,
): List<TodoSection> {
    val now = Instant.now()
    val today = LocalDate.now(zoneId)
    val overdueByDate = items.asSequence()
        .mapNotNull { todo -> todo.due?.let { due -> due to todo } }
        .filter { (due, _) -> due.isBefore(now) }
        .groupBy({ (due, _) -> LocalDate.ofInstant(due, zoneId) }, { (_, todo) -> todo })

    val sections = mutableListOf<TodoSection>()

    overdueByDate[today]
        ?.let { dayItems -> TaskSortEngine.sortedTodos(dayItems) { it.toTaskSortKey() } }
        ?.takeIf { it.isNotEmpty() }
        ?.let { todaysItems ->
            sections += TodoSection(
                key = "day-$today",
                title = "Today",
                items = todaysItems,
                quickAddDefaults = quickAddDefaultsForDate(
                    date = today,
                    zoneId = zoneId,
                ),
            )
        }

    overdueByDate.keys
        .asSequence()
        .filter { date -> date < today }
        .sortedDescending()
        .forEach { date ->
            sections += TodoSection(
                key = "day-$date",
                title = date.format(SCHEDULED_DAY_FORMATTER),
                items = TaskSortEngine.sortedTodos(overdueByDate[date].orEmpty()) { it.toTaskSortKey() },
                quickAddDefaults = null,
            )
        }

    return sections
}

private fun buildTodaySections(
    items: List<TodoItem>,
    earlierItems: List<TodoItem>,
    zoneId: ZoneId,
): List<TodoSection> {
    val sorted = TaskSortEngine.sortedTodos(items.filter { it.due != null }) { it.toTaskSortKey() }
    val today = LocalDate.now(zoneId)
    val noon = LocalTime.NOON
    val eveningStartBoundary = LocalTime.of(18, 0)

    fun sectionOf(todo: TodoItem): TodaySectionSlot {
        val dueTime = todo.due?.atZone(zoneId)?.toLocalTime() ?: LocalTime.NOON
        return when {
            // Requested boundaries:
            // Morning: 12:01 AM -> 12:00 PM (inclusive of 12:00 PM)
            // Afternoon: 12:01 PM -> 6:00 PM
            // Tonight: 6:01 PM -> end of day
            dueTime <= noon -> TodaySectionSlot.MORNING
            dueTime <= eveningStartBoundary -> TodaySectionSlot.AFTERNOON
            else -> TodaySectionSlot.TONIGHT
        }
    }

    // Today's own "Earlier": the overdue tasks fetched separately from
    // `items` (see `TodoListUiState.earlierItems`) so they never count
    // toward "pending today" -- that separation is what keeps requirement 1
    // (confetti on the last pending-today completion) correct without any
    // special-casing here. Sorted and shaped exactly like every other
    // scope's own Earlier bucket (`buildScheduledSections`), just carrying
    // this mode's separately-fetched items instead of a slice of `items`.
    val sortedEarlier = TaskSortEngine.sortedTodos(earlierItems) { it.toTaskSortKey() }
    val earlierSection = TodoSection(
        key = EARLIER_SECTION_KEY,
        title = "Earlier",
        items = sortedEarlier,
        quickAddDefaults = quickAddDefaultsForDate(
            date = today.minusDays(1),
            zoneId = zoneId,
        ),
        targetDate = timelineRescheduleTargetDate(EARLIER_SECTION_KEY, today),
    )

    return listOf(
        TodoSection(
            key = "today-morning",
            title = "Morning",
            items = sorted.filter { sectionOf(it) == TodaySectionSlot.MORNING },
            quickAddDefaults = quickAddDefaultsForTodaySection(
                slot = TodaySectionSlot.MORNING,
                zoneId = zoneId,
            ),
            targetDate = today,
            targetHour = 9,
        ),
        TodoSection(
            key = "today-afternoon",
            title = "Afternoon",
            items = sorted.filter { sectionOf(it) == TodaySectionSlot.AFTERNOON },
            quickAddDefaults = quickAddDefaultsForTodaySection(
                slot = TodaySectionSlot.AFTERNOON,
                zoneId = zoneId,
            ),
            targetDate = today,
            targetHour = 15,
        ),
        TodoSection(
            key = "today-tonight",
            title = "Tonight",
            items = sorted.filter { sectionOf(it) == TodaySectionSlot.TONIGHT },
            quickAddDefaults = quickAddDefaultsForTodaySection(
                slot = TodaySectionSlot.TONIGHT,
                zoneId = zoneId,
            ),
            targetDate = today,
            targetHour = 20,
        ),
        earlierSection,
    )
}

private fun buildFloaterSections(items: List<TodoItem>): List<TodoSection> {
    val floaterItems = TaskSortEngine.sortedFloaters(items) { it.toTaskSortKey() }

    return listOf(
        TodoSection(
            key = "floater-all",
            title = "",
            items = floaterItems,
        ),
    )
}

private fun floaterPriorityRank(priority: String): Int {
    return when {
        isUrgentPriority(priority) -> 0
        isImportantPriority(priority) -> 1
        isLowestPriority(priority) -> 3
        else -> 2
    }
}

private fun buildScheduledSections(
    items: List<TodoItem>,
    zoneId: ZoneId,
    futureOnly: Boolean,
    placesEarlierBeforeToday: Boolean = true,
): List<TodoSection> {
    val now = Instant.now()
    val dueItems = items.filter { todo ->
        val due = todo.due ?: return@filter false
        if (futureOnly) !due.isBefore(now) else true
    }
    val sorted = TaskSortEngine.sortedTodos(dueItems) { it.toTaskSortKey() }
    val groupedByDate = sorted.groupBy { todo ->
        LocalDate.ofInstant(todo.due ?: Instant.MAX, zoneId)
    }
    val today = LocalDate.now(zoneId)
    val horizonStart = today.plusDays(7)
    val currentMonth = YearMonth.from(today)

    val sections = mutableListOf<TodoSection>()
    fun daySection(date: LocalDate, title: String): TodoSection {
        return TodoSection(
            key = "day-$date",
            title = title,
            items = groupedByDate[date].orEmpty(),
            quickAddDefaults = quickAddDefaultsForDate(
                date = date,
                zoneId = zoneId,
            ),
            targetDate = timelineRescheduleTargetDate("day-$date", today),
        )
    }

    if (futureOnly) {
        return groupedByDate.keys
            .asSequence()
            .filter { date -> date >= today }
            .sorted()
            .map { date ->
                daySection(
                    date = date,
                    title = date.format(SCHEDULED_DAY_FORMATTER),
                )
            }
            .toList()
    }

    val earlierSection = if (!futureOnly) {
        val earlierItems = TaskSortEngine.sortedTodos(
            groupedByDate.asSequence().filter { (date, _) -> date < today }
                .flatMap { (_, dayItems) -> dayItems.asSequence() }.toList(),
        ) { it.toTaskSortKey() }
        // Handed over whole, empty or not: buildTimelineSections is the single
        // place that decides whether an empty bucket is worth a header.
        TodoSection(
            key = EARLIER_SECTION_KEY,
            title = "Earlier",
            items = earlierItems,
            quickAddDefaults = quickAddDefaultsForDate(
                date = today.minusDays(1),
                zoneId = zoneId,
            ),
            targetDate = timelineRescheduleTargetDate(EARLIER_SECTION_KEY, today),
        )
    } else {
        null
    }

    if (placesEarlierBeforeToday) {
        earlierSection?.let { sections += it }
    }

    sections += daySection(today, "Today")
    if (!placesEarlierBeforeToday) {
        earlierSection?.let { sections += it }
    }
    sections += daySection(today.plusDays(1), "Tomorrow")
    for (offset in 2..6) {
        val date = today.plusDays(offset.toLong())
        sections += daySection(
            date = date,
            title = date.format(SCHEDULED_DAY_FORMATTER),
        )
    }

    val restOfCurrentMonthItems = TaskSortEngine.sortedTodos(
        groupedByDate.asSequence().filter { (date, _) ->
            date >= horizonStart && YearMonth.from(date) == currentMonth
        }.flatMap { (_, dayItems) -> dayItems.asSequence() }.toList(),
    ) { it.toTaskSortKey() }
    val monthName = currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    sections += TodoSection(
        key = "rest-$currentMonth",
        title = "Rest of $monthName",
        items = restOfCurrentMonthItems,
        quickAddDefaults = quickAddDefaultsForDate(
            date = currentMonth.atEndOfMonth(),
            zoneId = zoneId,
        ),
        targetDate = timelineRescheduleTargetDate("rest-$currentMonth", today),
    )

    val futureMonthsWithData =
        groupedByDate.keys.asSequence().filter { it >= horizonStart }.map { YearMonth.from(it) }
            .toSet()
    val minimumFinalMonth = YearMonth.of(currentMonth.year, 12)
    val finalMonth = maxOf(
        minimumFinalMonth,
        futureMonthsWithData.maxOrNull() ?: minimumFinalMonth,
    )

    var targetMonth = currentMonth.plusMonths(1)
    while (targetMonth <= finalMonth) {
        val monthItems = TaskSortEngine.sortedTodos(
            groupedByDate.asSequence().filter { (date, _) ->
                date >= horizonStart && YearMonth.from(date) == targetMonth
            }.flatMap { (_, dayItems) -> dayItems.asSequence() }.toList(),
        ) { it.toTaskSortKey() }
        sections += TodoSection(
            key = "month-$targetMonth",
            title = monthTitle(targetMonth, currentMonth.year),
            items = monthItems,
            quickAddDefaults = quickAddDefaultsForDate(
                date = targetMonth.atDay(1),
                zoneId = zoneId,
            ),
            targetDate = timelineRescheduleTargetDate("month-$targetMonth", today),
        )
        targetMonth = targetMonth.plusMonths(1)
    }

    return sections
}

private fun monthTitle(
    month: YearMonth,
    currentYear: Int,
): String {
    val monthName = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return if (month.year == currentYear) {
        monthName
    } else {
        "$monthName ${month.year}"
    }
}

@Composable
private fun localizedSectionTitle(section: TodoSection): String {
    return when {
        section.key == "today-morning" -> stringResource(R.string.todos_section_morning)
        section.key == "today-afternoon" -> stringResource(R.string.todos_section_afternoon)
        section.key == "today-tonight" -> stringResource(R.string.todos_section_tonight)
        section.key == EARLIER_SECTION_KEY -> stringResource(R.string.todos_section_earlier)
        section.key.startsWith("day-") -> {
            val zoneId = ZoneId.systemDefault()
            val date = runCatching { LocalDate.parse(section.key.removePrefix("day-")) }.getOrNull()
            val today = LocalDate.now(zoneId)
            when (date) {
                today -> stringResource(R.string.todos_section_today)
                today.plusDays(1) -> stringResource(R.string.todos_section_tomorrow)
                else -> section.title
            }
        }
        section.key.startsWith("rest-") -> {
            val ymPart = section.key.removePrefix("rest-")
            val monthName = runCatching {
                val ym = YearMonth.parse(ymPart)
                ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
            }.getOrNull()

            if (monthName != null) {
                stringResource(R.string.todos_section_rest_of, monthName)
            } else {
                section.title
            }
        }
        else -> section.title
    }
}

/**
 * @param isFloaterList a floater screen opened on one list rather than the root
 *   feed. Both are [TodoListMode.FLOATER], but "No floater tasks" reads as if
 *   there were none anywhere when it is one list that is empty.
 */
@Composable
private fun emptyStateMessageForMode(mode: TodoListMode, isFloaterList: Boolean): String {
    return when (mode) {
        TodoListMode.TODAY -> stringResource(R.string.todos_empty_today)
        TodoListMode.OVERDUE -> stringResource(R.string.todos_empty_overdue)
        TodoListMode.PRIORITY -> stringResource(R.string.todos_empty_priority)
        TodoListMode.FLOATER -> if (isFloaterList) {
            stringResource(R.string.todos_empty_floater_list)
        } else {
            stringResource(R.string.todos_empty_floater)
        }

        TodoListMode.SCHEDULED -> stringResource(R.string.todos_empty_scheduled)
        TodoListMode.ALL -> stringResource(R.string.todos_empty_all)
        TodoListMode.LIST -> stringResource(R.string.todos_empty_list)
    }
}

/** The line under [emptyStateMessageForMode]: what puts something on the screen. */
@Composable
private fun emptyStateDescriptionForMode(mode: TodoListMode, isFloaterList: Boolean): String {
    return when (mode) {
        TodoListMode.TODAY -> stringResource(R.string.todos_empty_today_body)
        TodoListMode.OVERDUE -> stringResource(R.string.todos_empty_overdue_body)
        TodoListMode.PRIORITY -> stringResource(R.string.todos_empty_priority_body)
        TodoListMode.FLOATER -> if (isFloaterList) {
            stringResource(R.string.todos_empty_floater_list_body)
        } else {
            stringResource(R.string.todos_empty_floater_body)
        }

        TodoListMode.SCHEDULED -> stringResource(R.string.todos_empty_scheduled_body)
        TodoListMode.ALL -> stringResource(R.string.todos_empty_all_body)
        TodoListMode.LIST -> stringResource(R.string.todos_empty_list_body)
    }
}

/**
 * The glyph in the empty scene's accent circle: the tile glyph the watermark
 * behind it draws where there is one, and the screen's own icon otherwise.
 */
@DrawableRes
private fun emptyStateSceneIconForMode(
    mode: TodoListMode,
    listIconKey: String?,
    isTodayDaytime: Boolean,
): Int {
    return when (mode) {
        TodoListMode.TODAY ->
            if (isTodayDaytime) R.drawable.ic_lucide_sun else R.drawable.ic_lucide_moon

        TodoListMode.OVERDUE -> R.drawable.ic_lucide_clock_3
        TodoListMode.PRIORITY -> R.drawable.ic_lucide_flag
        TodoListMode.SCHEDULED -> R.drawable.ic_lucide_calendar_clock
        TodoListMode.ALL -> R.drawable.ic_lucide_layers
        TodoListMode.FLOATER ->
            if (listIconKey.isNullOrBlank()) {
                R.drawable.ic_lucide_leaf
            } else {
                tdayListIconResForKey(listIconKey)
            }

        TodoListMode.LIST -> tdayListIconResForKey(listIconKey)
    }
}

/**
 * Lucide drawable watermark for the scheduled task home category modes, mirroring the web app.
 * Returns null for modes that keep their Material vector watermark (today/floater/list).
 */
@DrawableRes
private fun emptyStateDrawableForMode(mode: TodoListMode): Int? {
    return when (mode) {
        TodoListMode.OVERDUE -> R.drawable.ic_lucide_clock_3
        TodoListMode.PRIORITY -> R.drawable.ic_lucide_flag
        TodoListMode.SCHEDULED -> R.drawable.ic_lucide_calendar_clock
        TodoListMode.ALL -> R.drawable.ic_lucide_layers
        else -> null
    }
}

@Composable
private fun emptyStateIconForMode(
    mode: TodoListMode,
    listIconKey: String?,
    isTodayDaytime: Boolean,
): ImageVector {
    return when (mode) {
        TodoListMode.TODAY -> if (isTodayDaytime) ImageVector.vectorResource(R.drawable.ic_lucide_sun) else ImageVector.vectorResource(
            R.drawable.ic_lucide_moon
        )

        TodoListMode.OVERDUE -> ImageVector.vectorResource(R.drawable.ic_lucide_circle_alert)
        TodoListMode.PRIORITY -> ImageVector.vectorResource(R.drawable.ic_lucide_flag)
        TodoListMode.FLOATER ->
            if (listIconKey.isNullOrBlank()) ImageVector.vectorResource(R.drawable.ic_lucide_leaf) else tdayListIconForKey(
                listIconKey
            )

        TodoListMode.SCHEDULED -> ImageVector.vectorResource(R.drawable.ic_lucide_clock)
        TodoListMode.ALL -> ImageVector.vectorResource(R.drawable.ic_lucide_inbox)
        TodoListMode.LIST -> tdayListIconForKey(listIconKey)
    }
}

private val SCHEDULED_DAY_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE MMM d", Locale.getDefault())

private fun quickAddDefaultsForDate(
    date: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long {
    val dueTime = LocalTime.of(23, 59)
    return ZonedDateTime.of(date, dueTime, zoneId).toInstant().toEpochMilli()
}

private fun quickAddDefaultsForTodaySection(
    slot: TodaySectionSlot,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long {
    val today = LocalDate.now(zoneId)
    val time = when (slot) {
        TodaySectionSlot.MORNING -> LocalTime.NOON
        TodaySectionSlot.AFTERNOON -> LocalTime.of(18, 0)
        TodaySectionSlot.TONIGHT -> LocalTime.of(22, 0)
    }
    return ZonedDateTime.of(today, time, zoneId).toInstant().toEpochMilli()
}

private suspend fun LazyListState.animateSearchResultScrollToItem(
    targetIndex: Int,
    targetKey: String,
    centeredScrollOffset: Int,
    estimatedItemSizePx: Int,
) {
    repeat(SEARCH_RESULT_SCROLL_CORRECTION_PASSES) {
        val visibleTarget =
            layoutInfo.visibleItemsInfo.firstOrNull { item -> item.key == targetKey }
        if (visibleTarget != null) {
            animateVisibleSearchResultToCenter(
                itemOffset = visibleTarget.offset,
                itemSize = visibleTarget.size,
            )
            return
        }

        val visibleItems = layoutInfo.visibleItemsInfo
        val averageItemSizePx = visibleItems
            .takeIf { it.isNotEmpty() }
            ?.map { item -> item.size }
            ?.average()
            ?.toFloat()
            ?.takeIf { it > 0f }
            ?: estimatedItemSizePx.toFloat()
        val estimatedDistance =
            ((targetIndex - firstVisibleItemIndex) * averageItemSizePx) +
                    centeredScrollOffset -
                    firstVisibleItemScrollOffset
        if (abs(estimatedDistance) < SEARCH_RESULT_SCROLL_MIN_DISTANCE_PX) return
        animateScrollBy(
            value = estimatedDistance,
            animationSpec = tween(
                durationMillis = searchResultScrollDurationMillis(estimatedDistance),
                easing = LinearOutSlowInEasing,
            ),
        )
    }

    val visibleTarget = layoutInfo.visibleItemsInfo.firstOrNull { item -> item.key == targetKey }
    if (visibleTarget != null) {
        animateVisibleSearchResultToCenter(
            itemOffset = visibleTarget.offset,
            itemSize = visibleTarget.size,
        )
    } else {
        scrollToItem(targetIndex, centeredScrollOffset)
    }
}

private suspend fun LazyListState.animateVisibleSearchResultToCenter(
    itemOffset: Int,
    itemSize: Int,
) {
    val viewportCenter =
        (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    val itemCenter = itemOffset + (itemSize / 2)
    val centerDelta = (itemCenter - viewportCenter).toFloat()
    if (abs(centerDelta) < SEARCH_RESULT_SCROLL_MIN_DISTANCE_PX) return
    animateScrollBy(
        value = centerDelta,
        animationSpec = tween(
            durationMillis = SEARCH_RESULT_CENTER_SCROLL_DURATION_MS,
            easing = FastOutSlowInEasing,
        ),
    )
}

private fun searchResultScrollDurationMillis(distancePx: Float): Int =
    (abs(distancePx) / SEARCH_RESULT_SCROLL_PX_PER_MS)
        .roundToInt()
        .coerceIn(
            SEARCH_RESULT_SCROLL_MIN_DURATION_MS,
            SEARCH_RESULT_SCROLL_MAX_DURATION_MS,
        )

/**
 * The wait before a search result is scrolled to — not a token — see docs/motion.md.
 *
 * What it is waiting for is the feed stopping: scrolling a list whose rows are still
 * moving aims at final positions that are not final yet, and the correction passes
 * below spend themselves chasing the movement rather than the target. Its two call
 * sites are waiting on different movement — one on the route handover into this
 * screen, one on the floater feed re-laying itself out once the search card leaves it
 * — which is why they are handed different scales; [effectiveMotionScale] has the
 * argument. [scaledDelay] and not `delay` at both, because both are covering Compose
 * animations: with those refused the screen is whole on the first frame and this
 * would be 380 ms of a tapped result doing nothing.
 */
private const val SEARCH_RESULT_NAV_SETTLE_DELAY_MS = 380L
private const val SEARCH_RESULT_SCROLL_CORRECTION_PASSES = 2
private const val SEARCH_RESULT_SCROLL_MIN_DISTANCE_PX = 2f
private const val SEARCH_RESULT_SCROLL_PX_PER_MS = 1.15f
private const val SEARCH_RESULT_SCROLL_MIN_DURATION_MS = 720
private const val SEARCH_RESULT_SCROLL_MAX_DURATION_MS = 2400
private const val SEARCH_RESULT_CENTER_SCROLL_DURATION_MS = 520
private const val SEARCH_RESULT_ESTIMATED_ROW_HEIGHT_DP = 72f

/**
 * How long a navigated-to row stays flagged as the flashing one — not a token — see
 * docs/motion.md.
 *
 * The flash is two pulses of 420 up and 620 down with
 * [SEARCH_RESULT_FLASH_PULSE_GAP_MS] between them, in [SwipeTaskRow]: 2230 ms, which
 * this outlasts by seventy. Pinned rather than summed from those four numbers on
 * purpose, the same call `EarlierIllustrationMotionTest` argues for — a hold defined
 * as its own contents can never be caught disagreeing with them.
 *
 * [scaledDelay] and not `delay`, because every millisecond of it is spent waiting on
 * pulses Compose already scales: at 0x they land instantly and this would keep a row
 * flagged for two and a third seconds with nothing drawn on it, and at 2x it would
 * drop the flag with the second pulse still climbing.
 */
private const val SEARCH_RESULT_FLASH_HOLD_MS = 2300L

/**
 * The dark between the two pulses — not a token — see docs/motion.md.
 *
 * A gap and not a motion: nobody watches it, they watch the pulses either side. So it
 * stays a plain integer rather than taking the Quick rung it happens to equal, the
 * same way `taskCompletionTiming.ts` keeps its own offsets plain.
 */
private const val SEARCH_RESULT_FLASH_PULSE_GAP_MS = 150L

/**
 * The tick landing, then the strike beginning.
 *
 * Every leg of this sequence is handed to [scaledDelay] rather than to `delay`, and
 * that is the whole of what the row has to get right under the animator scale. The
 * three beats the gaps separate are each gated on the same answer the gaps are — the
 * tint crossfade is a `snap()` when motion is off, `rememberTaskStrikeProgress` hands
 * back its finished progress, and `completionAlpha` snaps rather than fading — so the
 * row is drawn ticked, struck and gone on the first frame and the only thing left to
 * remove is the 780 ms the user would otherwise spend in front of it. That is
 * `docs/motion.md`'s fifth idiom rule read from the other side, and it lands on the
 * app's most-performed interaction.
 *
 * All three being gated is also what lets the gaps take the app's scale rather than
 * the device's. The fade was a bare `tween` until the in-app switch existed, and the
 * two would have come apart the moment it did: the row pulled out of the list at full
 * opacity, by a wait of nothing, while the fade meant to carry it off ran on.
 * [effectiveMotionScale] has the general shape of that trap.
 *
 * Android cuts deeper here than web does — `taskCompletionStaging.ts` keeps these
 * first two legs and drops only the last — because the two preferences are not the
 * same question. `prefers-reduced-motion: reduce` asks for less movement; an
 * animator scale of 0 is the platform stating that every animation lands in one
 * frame, and a scale is also a *number*: the same call has to be right at 2x, where
 * holding these two at 160 and 360 would start the strike over a tint still
 * crossfading and the fade over a rule barely half swept.
 */
private const val TASK_COMPLETION_CHECK_TO_STRIKE_MS = 160L

/** The strike holding — title and notes both — before the row starts fading. */
private const val TASK_COMPLETION_STRIKE_TO_FADE_MS = 360L

/**
 * The ink leaving, and the wait before the row is handed to the list.
 *
 * The two are one number because they are one motion — the coroutine that ticks
 * the task off waits exactly as long as the fade it started — so it is read from
 * the vocabulary once rather than typed on both sides. Change is the rung: the
 * row's content goes where it stands and nothing moves, which is the geometry
 * test `docs/motion.md`'s second idiom rule decides this by. The two offsets
 * above are gaps rather than motions and stay plain, the same way web's
 * `taskCompletionTiming.ts` keeps them.
 */
private val TASK_COMPLETION_FADE_MS = TdayMotionTokens.Durations.Change.toLong()
private val SWIPE_ROW_CONTENT_VERTICAL_PADDING = 2.dp
private val SWIPE_ROW_HEIGHT = 56.dp
private val TASK_CHECKMARK_GREEN = TdayTaskCompleteAccent
private val TODO_DUE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).withZone(ZoneId.systemDefault())
private val TODO_DUE_DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())
        .withZone(ZoneId.systemDefault())

@Composable
private fun AllTaskSwipeRow(
    todo: TodoItem,
    lists: List<ListSummary>,
    flashHighlight: Boolean,
    selectionActive: Boolean = false,
    selected: Boolean = false,
    onToggleSelected: () -> Unit = {},
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    showDuePrefix: Boolean,
    showDueDateInSubtitle: Boolean = false,
    showDateDivider: Boolean,
    dragEnabled: Boolean = false,
    dragging: Boolean = false,
    onDragStart: ((Offset) -> Unit)? = null,
    onDragMove: (Offset) -> Unit = {},
    onDragEnd: (Offset?) -> Unit = {},
    onDragCancel: () -> Unit = {},
    swipeSlot: TaskSwipeSlot,
) {
    SwipeTaskRow(
        todo = todo,
        onComplete = onComplete,
        onDelete = onDelete,
        onInfo = onInfo,
        keepCompletedInline = false,
        mode = TodoListMode.ALL,
        lists = lists,
        flashHighlight = flashHighlight,
        selectionActive = selectionActive,
        selected = selected,
        onToggleSelected = onToggleSelected,
        showDueText = true,
        showDuePrefix = showDuePrefix,
        showDueDateInSubtitle = showDueDateInSubtitle,
        showDateDivider = showDateDivider,
        useDelayedFadeCompletion = false,
        dragEnabled = dragEnabled,
        dragging = dragging,
        onDragStart = onDragStart,
        onDragMove = onDragMove,
        onDragEnd = onDragEnd,
        onDragCancel = onDragCancel,
        swipeSlot = swipeSlot,
    )
}

@Composable
private fun TodayTaskSwipeRow(
    todo: TodoItem,
    mode: TodoListMode,
    lists: List<ListSummary>,
    flashHighlight: Boolean = false,
    readOnly: Boolean = false,
    selectionActive: Boolean = false,
    selected: Boolean = false,
    onToggleSelected: () -> Unit = {},
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    onPromote: (() -> Unit)? = null,
    onDemote: (() -> Unit)? = null,
    onDefer: (() -> Unit)? = null,
    showDuePrefix: Boolean,
    showDueDateInSubtitle: Boolean = false,
    showDateDivider: Boolean,
    dragEnabled: Boolean = false,
    dragging: Boolean = false,
    onDragStart: ((Offset) -> Unit)? = null,
    onDragMove: (Offset) -> Unit = {},
    onDragEnd: (Offset?) -> Unit = {},
    onDragCancel: () -> Unit = {},
    swipeSlot: TaskSwipeSlot,
) {
    SwipeTaskRow(
        todo = todo,
        onComplete = onComplete,
        onDelete = onDelete,
        onInfo = onInfo,
        onPromote = onPromote,
        onDemote = onDemote,
        onDefer = onDefer,
        keepCompletedInline = false,
        mode = mode,
        lists = lists,
        flashHighlight = flashHighlight,
        readOnly = readOnly,
        selectionActive = selectionActive,
        selected = selected,
        onToggleSelected = onToggleSelected,
        showDueText = true,
        showDuePrefix = showDuePrefix,
        showDueDateInSubtitle = showDueDateInSubtitle,
        showDateDivider = showDateDivider,
        useDelayedFadeCompletion = mode != TodoListMode.TODAY,
        dragEnabled = dragEnabled && !readOnly,
        dragging = dragging,
        onDragStart = onDragStart,
        onDragMove = onDragMove,
        onDragEnd = onDragEnd,
        onDragCancel = onDragCancel,
        swipeSlot = swipeSlot,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeTaskRow(
    todo: TodoItem,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    keepCompletedInline: Boolean,
    onPromote: (() -> Unit)? = null,
    onDemote: (() -> Unit)? = null,
    onDefer: (() -> Unit)? = null,
    mode: TodoListMode = TodoListMode.ALL,
    lists: List<ListSummary> = emptyList(),
    flashHighlight: Boolean = false,
    readOnly: Boolean = false,
    selectionActive: Boolean = false,
    selected: Boolean = false,
    onToggleSelected: () -> Unit = {},
    showDueText: Boolean,
    showDuePrefix: Boolean,
    showDueDateInSubtitle: Boolean = false,
    showDateDivider: Boolean = false,
    useDelayedFadeCompletion: Boolean = false,
    useFadeOnCompletion: Boolean = false,
    dragEnabled: Boolean = false,
    dragging: Boolean = false,
    onDragStart: ((Offset) -> Unit)? = null,
    onDragMove: (Offset) -> Unit = {},
    onDragEnd: (Offset?) -> Unit = {},
    onDragCancel: () -> Unit = {},
    swipeSlot: TaskSwipeSlot,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val taskCompletionSound = rememberTaskCompletionSound()
    val coroutineScope = rememberCoroutineScope()
    // Floater rows reveal a third "Schedule" action, overdue rows a "Float"
    // action, dated rows a "Defer" action (never for recurring todos — their
    // series can't float, and occurrences defer via the edit sheet instead).
    val promoteAction = onPromote.takeIf { mode == TodoListMode.FLOATER }
    val demoteAction = onDemote.takeIf {
        mode == TodoListMode.OVERDUE && todo.rrule.isNullOrBlank()
    }
    val deferAction = onDefer.takeIf {
        todo.rrule.isNullOrBlank() && mode in setOf(
            TodoListMode.TODAY,
            TodoListMode.SCHEDULED,
            TodoListMode.PRIORITY,
            TodoListMode.LIST,
        )
    }
    val hasExtraSwipeAction = promoteAction != null || demoteAction != null || deferAction != null
    // Edit + Copy + Delete always show; the optional mode-specific extra
    // action (Schedule/Float/Defer) adds a 4th pill. Matches the +80dp-per-pill
    // step already established between the 2- and 3-pill widths below.
    val swipeRevealState = rememberTaskSwipeRevealState(
        todo.id,
        revealWidth = if (hasExtraSwipeAction) SwipeRevealWidthWithExtraAction else SwipeRevealWidth,
    )
    val clipboardManager = LocalClipboardManager.current
    val snackbarManager = LocalSnackbarManager.current
    val copyContext = LocalContext.current
    val copiedMessage = stringResource(R.string.task_copied_toast)
    val copyFailedMessage = stringResource(R.string.task_copy_failed_toast)
    var localChecked by remember(todo.id) { mutableStateOf(false) }
    var localStruck by remember(todo.id) { mutableStateOf(false) }
    var pendingCompletion by remember(todo.id) { mutableStateOf(false) }
    var completionFading by remember(todo.id) { mutableStateOf(false) }
    var rowOriginInRoot by remember(todo.id) { mutableStateOf(Offset.Zero) }
    var dragPointerPosition by remember(todo.id) { mutableStateOf<Offset?>(null) }
    fun claimSwipeSlot() {
        if (swipeSlot.openId != todo.id) {
            swipeSlot.openId = todo.id
        }
    }

    // This row handing back the slot it holds, and only that -- see
    // [swipeSlotAfterRowDisclaim] for why it is guarded and for the revoke
    // that deliberately is not.
    fun closeSwipeSlot() {
        swipeRevealState.close()
        swipeSlot.openId = swipeSlotAfterRowDisclaim(swipeSlot.openId, todo.id)
    }
    val highlightAnim = remember(todo.id) { Animatable(0f) }
    val visuallyChecked = localChecked || (keepCompletedInline && todo.completed)
    val visuallyStruck = localStruck || (keepCompletedInline && todo.completed)
    // Hoisted above the reveal's own animation because that is now one of its
    // callers: with the app's Reduce Motion switch on, a close draws its
    // finished state instead of springing to it. One read, two uses -- the
    // switch and the row can never disagree about the same device.
    val rowMotionScale = rememberTdayMotionScale()
    val animatedOffsetX by animateTaskSwipeOffsetAsState(
        state = swipeRevealState,
        label = "swipeTaskOffset",
        scale = rowMotionScale,
    )
    val actionRevealProgress = swipeRevealState.revealProgress(animatedOffsetX)
    // The beats the row used to cut straight to. Tint and title colour are one
    // event each and travel with the glyph and the rule they belong to; the rule
    // itself is `taskStrikethrough`, which argues the mechanism where it lives;
    // the fade below is the last of them. Under reduced motion every one of these
    // is handed its finished value rather than the first frame of a trip nobody is
    // taking.
    val motionEnabled = rememberTdayMotionEnabled()
    // Gated like the two beats in front of it, and read alongside them rather than
    // left to Compose. The three legs of the check-off are timed against these
    // specs, so a fade that kept running while its own wait was zeroed would pull
    // the row out of the list at full opacity — the pop the last leg exists to
    // prevent. See [TASK_COMPLETION_CHECK_TO_STRIKE_MS].
    val completionAlpha by animateFloatAsState(
        targetValue = if (completionFading) 0f else 1f,
        animationSpec = if (motionEnabled) {
            tween(
                durationMillis = TASK_COMPLETION_FADE_MS.toInt(),
                easing = TdayMotionTokens.Easings.Standard,
            )
        } else {
            snap()
        },
        label = "swipeTaskCompletionAlpha",
    )
    val completionOffsetY by animateDpAsState(
        targetValue = if (completionFading) TaskCompletionRiseOffsetY else TdayDimens.SpacingNone,
        animationSpec = if (motionEnabled) {
            tween(
                durationMillis = TASK_COMPLETION_FADE_MS.toInt(),
                easing = TdayMotionTokens.Easings.Standard,
            )
        } else {
            snap()
        },
        label = "swipeTaskCompletionOffsetY",
    )
    // The scale behind that switch, for this row's waits: the hint's hold and the
    // three legs of the check-off. Every one of them is a gap between animations
    // rather than a spec handed to one, which is the line [scaledDelay] draws — and
    // every one of them sits between beats this row gates on [motionEnabled], which
    // is what makes the app's scale the right clock for them.
    // The flash is the exception, so it gets its own number. Its two pulses are
    // ungated `tween`s below, which means they keep playing at the device's scale
    // with the in-app switch on; a gap between them timed on the app's scale would
    // close while the first pulse was still climbing. Free per row — both scales come
    // down a composition local. See [effectiveMotionScale].
    val rowFlashMotionScale = rememberSystemMotionScale()
    val toggleTint by animateColorAsState(
        targetValue = if (selectionActive) {
            if (selected) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
        } else if (visuallyChecked) {
            TASK_CHECKMARK_GREEN
        } else {
            colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
        },
        animationSpec = if (motionEnabled) {
            tween(
                durationMillis = TdayMotionTokens.Durations.Quick,
                easing = TdayMotionTokens.Easings.Standard,
            )
        } else {
            snap()
        },
        label = "swipeTaskToggleTint",
    )
    val titleColor by animateColorAsState(
        targetValue = if (visuallyStruck) {
            colorScheme.onSurface.copy(alpha = 0.78f)
        } else {
            colorScheme.onSurface
        },
        animationSpec = if (motionEnabled) {
            tween(
                durationMillis = TdayMotionTokens.Durations.Emphasis,
                easing = TdayMotionTokens.Easings.Standard,
            )
        } else {
            snap()
        },
        label = "swipeTaskTitleColor",
    )
    var titleLayoutResult by remember(todo.id) { mutableStateOf<TextLayoutResult?>(null) }
    var noteLayoutResult by remember(todo.id) { mutableStateOf<TextLayoutResult?>(null) }
    val titleStrikeProgress = rememberTaskStrikeProgress(visuallyStruck, "swipeTaskTitleStrike")
    val isOverdue = !todo.completed && todo.due?.isBefore(Instant.now()) == true
    val dueBodyText = todo.due?.let {
        if (showDueDateInSubtitle) {
            TODO_DUE_DATE_TIME_FORMATTER.format(it)
        } else {
            TODO_DUE_TIME_FORMATTER.format(it)
        }
    }
    val dueSubtitleText = dueBodyText?.let { text ->
        if (isOverdue) {
            stringResource(R.string.todos_due_overdue_text, text)
        } else if (showDuePrefix) {
            stringResource(R.string.todos_due_text, text)
        } else {
            text
        }
    }
    val rowShape = RoundedCornerShape(TdayDimens.RadiusRow)
    val foregroundColor = colorScheme.background
    val highlightStrength = highlightAnim.value.coerceIn(0f, 1f)
    val contentGlowBrush = Brush.horizontalGradient(
        colors = listOf(
            colorScheme.primary.copy(
                alpha = if (colorScheme.background.luminance() < 0.5f) {
                    0.50f * highlightStrength
                } else {
                    0.40f * highlightStrength
                },
            ),
            colorScheme.primary.copy(
                alpha = if (colorScheme.background.luminance() < 0.5f) {
                    0.30f * highlightStrength
                } else {
                    0.20f * highlightStrength
                },
            ),
            Color.Transparent,
        ),
    )
    val listMeta = todo.listId?.let { listId -> lists.firstOrNull { it.id == listId } }
    val showListIndicator = when (mode) {
        TodoListMode.TODAY,
        TodoListMode.OVERDUE,
        TodoListMode.SCHEDULED,
        TodoListMode.PRIORITY,
        TodoListMode.FLOATER,
        TodoListMode.ALL,
            -> listMeta != null

        TodoListMode.LIST,
            -> false
    }
    val priorityIcon = priorityIconFor(todo.priority)
    val showPriorityIcon = priorityIcon != null
    val listIndicatorColor = tdayListAccentColor(listMeta?.color)
    // The other half of the pick-up: the slot this card came out of. It used to
    // cut to 70 % on the frame the long press fired, alongside a preview that
    // cut to full size, which is two events for one gesture. Same rung as the
    // rise, so the row empties exactly as the card leaves it.
    val vacatedAlpha by animateFloatAsState(
        targetValue = if (dragging) TdayDragLift.VacatedAlpha else 1f,
        animationSpec = TdayDragLift.spec(motionEnabled),
        label = "timelineTaskDragVacated",
    )
    // The row's whole subscription to the screen's slot, and the only place it
    // reads it. Outside composition, so a row recomposes for nothing when
    // another row opens or closes -- which is what makes this affordable on a
    // feed of hundreds.
    //
    // The predicate is [shouldCloseSwipeRow] and the clause it no longer carries
    // is the point: `openSwipeTaskId != null` used to guard this, which meant
    // the slot could be handed from row to row but never revoked. Every
    // dismissal added here -- outside tap, scroll, back, bulk select -- is a
    // write of `null`, so under the old guard every one of them would have been
    // a silent no-op.
    //
    // The close is `TaskSwipeRevealState.close()`, which is the same
    // `TaskSwipeMotion.Release` rung the open already uses and is deliberately
    // silent. `settle`'s own doc argues exactly this case: "a close is
    // frequently not even something the user did to this row -- one row open at
    // a time means the previous row is shut from under a finger that is nowhere
    // near it."
    //
    // Entering selection mode no longer needs an effect of its own. It writes
    // `null` to the slot like everything else now, and this closes the row; the
    // second `LaunchedEffect(selectionActive)` that used to do the closing was
    // only ever there because the write did not work.
    LaunchedEffect(swipeSlot, todo.id) {
        snapshotFlow { swipeSlot.openId }.collect { openId ->
            if (shouldCloseSwipeRow(openId, todo.id, swipeRevealState.isOpenOrDragging)) {
                swipeRevealState.close()
            }
        }
    }
    LaunchedEffect(flashHighlight) {
        if (!flashHighlight) return@LaunchedEffect
        closeSwipeSlot()
        highlightAnim.stop()
        highlightAnim.snapTo(0f)
        // not a token — see docs/motion.md. Neither leg is on the ladder and neither is
        // meant to be: the two are timed against each other and against the dark between
        // them, so that the pulse reads as a heartbeat rather than as two arrivals. The
        // 620 is longer than Scene, the app's longest motion, which makes it a wait.
        repeat(2) { pulseIndex ->
            highlightAnim.animateTo(
                targetValue = 0.46f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            )
            highlightAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing),
            )
            if (pulseIndex < 1) {
                scaledDelay(SEARCH_RESULT_FLASH_PULSE_GAP_MS, rowFlashMotionScale)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = completionAlpha * vacatedAlpha
                translationY = completionOffsetY.toPx()
            },
        verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingXs),
    ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = SWIPE_ROW_HEIGHT)
                    .height(IntrinsicSize.Min),
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = TdayDimens.SpacingXxs),
                    horizontalArrangement = Arrangement.spacedBy(SwipeActionSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (promoteAction != null) {
                        TaskSwipeActionButton(
                            icon = R.drawable.ic_lucide_calendar_clock,
                            contentDescription = stringResource(R.string.action_schedule_task),
                            label = stringResource(R.string.action_schedule),
                            tint = Color.White,
                            background = TdaySwipeScheduleBackground,
                            revealProgress = actionRevealProgress,
                            revealDelay = 0.74f,
                            onClick = {
                                TdayHaptics.buttonPress(view)
                                closeSwipeSlot()
                                promoteAction()
                            },
                        )
                    }
                    if (demoteAction != null) {
                        TaskSwipeActionButton(
                            icon = R.drawable.ic_lucide_waves,
                            contentDescription = stringResource(R.string.action_float_task),
                            label = stringResource(R.string.action_float),
                            tint = Color.White,
                            background = TdaySwipeFloatBackground,
                            revealProgress = actionRevealProgress,
                            revealDelay = 0.74f,
                            onClick = {
                                TdayHaptics.buttonPress(view)
                                closeSwipeSlot()
                                demoteAction()
                            },
                        )
                    }
                    if (deferAction != null) {
                        TaskSwipeActionButton(
                            icon = R.drawable.ic_lucide_alarm_clock,
                            contentDescription = stringResource(R.string.action_defer_task),
                            label = stringResource(R.string.action_defer),
                            tint = Color.White,
                            background = TdaySwipeScheduleBackground,
                            revealProgress = actionRevealProgress,
                            revealDelay = 0.74f,
                            onClick = {
                                TdayHaptics.buttonPress(view)
                                closeSwipeSlot()
                                deferAction()
                            },
                        )
                    }
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
                                clipboardManager.setText(AnnotatedString(taskCopyText(copyContext, todo)))
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
                        .onGloballyPositioned { coordinates ->
                            rowOriginInRoot = coordinates.positionInRoot()
                        }
                        .graphicsLayer { translationX = animatedOffsetX }
                        .then(
                            if (dragEnabled) {
                                Modifier.pointerInput(todo.id, dragEnabled) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { localOffset ->
                                            // Unconditional, unlike `closeSwipeSlot`, and that is
                                            // the whole of the difference. A long-press drag is a
                                            // gesture over the FEED, not over this row: the row
                                            // whose actions are out is almost always some other
                                            // one, so a disclaim would test `== todo.id`, find
                                            // false, and leave an armed Delete pill sitting under
                                            // the task the user is about to drop. Nothing else
                                            // catches it either -- these feeds have no drag
                                            // autoscroll, so the `isScrollInProgress` collector
                                            // never fires, and the outside-tap modifier only gets
                                            // to act on release, by which time the pill has been
                                            // armed under a moving thumb for the whole drag. iOS
                                            // writes the same `nil` at the top of `beginInAppDrag`.
                                            swipeRevealState.close()
                                            swipeSlot.openId = null
                                            val startPosition = rowOriginInRoot + localOffset
                                            dragPointerPosition = startPosition
                                            onDragStart?.invoke(startPosition)
                                            onDragMove(startPosition)
                                            TdayHaptics.dragPickUp(view)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val nextPosition = (dragPointerPosition
                                                ?: rowOriginInRoot) + dragAmount
                                            dragPointerPosition = nextPosition
                                            onDragMove(nextPosition)
                                        },
                                        onDragEnd = {
                                            onDragEnd(dragPointerPosition)
                                            dragPointerPosition = null
                                        },
                                        onDragCancel = {
                                            dragPointerPosition = null
                                            onDragCancel()
                                        },
                                    )
                                }
                            } else {
                                Modifier
                            },
                        )
                        .then(
                            if (readOnly || selectionActive) {
                                // Viewer role, or selection mode: no
                                // swipe-to-reveal edit/delete.
                                Modifier
                            } else {
                                Modifier.draggable(
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
                                        if (!swipeRevealState.isOpenOrDragging && swipeSlot.openId == todo.id) {
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
                                        } else if (swipeSlot.openId == todo.id) {
                                            swipeSlot.openId = null
                                        }
                                    },
                                )
                            },
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (selectionActive) {
                                onToggleSelected()
                                return@clickable
                            }
                            if (readOnly) return@clickable
                            if (swipeRevealState.isOpenOrDragging) {
                                closeSwipeSlot()
                            } else if (!swipeRevealState.isHinting && !pendingCompletion && !dragging) {
                                claimSwipeSlot()
                                coroutineScope.launch {
                                    swipeRevealState.playHint(rowMotionScale)
                                    if (swipeSlot.openId == todo.id && !swipeRevealState.isOpenOrDragging) {
                                        swipeSlot.openId = null
                                    }
                                }
                            }
                        },
                    shape = rowShape,
                    colors = CardDefaults.cardColors(containerColor = foregroundColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = TdayDimens.CardElevationDefault),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                horizontal = TdayDimens.SpacingXs,
                                vertical = SWIPE_ROW_CONTENT_VERTICAL_PADDING
                            )
                            .semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(vertical = TdayDimens.SpacingXxs)
                                .clip(RoundedCornerShape(TdayDimens.RadiusLg))
                                .background(foregroundColor, RoundedCornerShape(TdayDimens.RadiusLg))
                                .background(contentGlowBrush, RoundedCornerShape(TdayDimens.RadiusLg)),
                            // Top-align so the toggle sits on the first line of a
                            // multi-line title rather than centring across all lines.
                            verticalAlignment = Alignment.Top,
                        ) {
                            // While selecting, the same circle becomes the
                            // selection checkbox — the row's own glyphs, so the
                            // list does not visibly change shape — and a tap can
                            // never complete a task by accident.
                            CircularCheckToggleIcon(
                                imageVector = if (selectionActive) {
                                    if (selected) {
                                        ImageVector.vectorResource(R.drawable.ic_lucide_circle_check_big)
                                    } else {
                                        ImageVector.vectorResource(R.drawable.ic_lucide_circle)
                                    }
                                } else if (!visuallyChecked) {
                                    ImageVector.vectorResource(R.drawable.ic_lucide_circle)
                                } else {
                                    ImageVector.vectorResource(R.drawable.ic_lucide_circle_check_big)
                                },
                                contentDescription = if (selectionActive) {
                                    if (selected) {
                                        stringResource(R.string.bulk_deselect_task)
                                    } else {
                                        stringResource(R.string.bulk_select_task)
                                    }
                                } else if (visuallyChecked) {
                                    stringResource(R.string.label_completed)
                                } else {
                                    stringResource(R.string.label_mark_complete)
                                },
                                tint = toggleTint,
                                enabled = if (selectionActive) {
                                    true
                                } else {
                                    !visuallyChecked && !pendingCompletion && !readOnly
                                },
                                onClick = {
                                    // One control, two events: in bulk-select mode this
                                    // circle moves the selection, everywhere else it
                                    // finishes the task. They must not feel the same.
                                    if (selectionActive) {
                                        TdayHaptics.selection(view)
                                        onToggleSelected()
                                    } else {
                                        TdayHaptics.completion(view)
                                        taskCompletionSound.play()
                                        closeSwipeSlot()
                                        localChecked = true
                                        pendingCompletion = true
                                        coroutineScope.launch {
                                            // Three gaps, three animations they
                                            // are covering, so all three are on
                                            // the animator's clock. See
                                            // [TASK_COMPLETION_CHECK_TO_STRIKE_MS].
                                            scaledDelay(
                                                TASK_COMPLETION_CHECK_TO_STRIKE_MS,
                                                rowMotionScale,
                                            )
                                            localStruck = true
                                            scaledDelay(
                                                TASK_COMPLETION_STRIKE_TO_FADE_MS,
                                                rowMotionScale,
                                            )
                                            completionFading = true
                                            scaledDelay(
                                                TASK_COMPLETION_FADE_MS,
                                                rowMotionScale,
                                            )
                                            onComplete()
                                        }
                                    }
                                },
                            )

                            Column(
                                // top pad centres the first title line against the
                                // (taller) toggle so the toggle lands on line one.
                                modifier = Modifier
                                    .padding(
                                        start = TaskRowTitleStartPadding,
                                        top = TdayDimens.SpacingLg,
                                        end = TdayDimens.SpacingMd,
                                    )
                                    .weight(1f),
                            ) {
                                Text(
                                    text = todo.title,
                                    // Drawn rather than declared. `TextDecoration.LineThrough`
                                    // is a boolean and this is a beat the user is meant to
                                    // watch land; `taskStrikethrough` sweeps one rule per line,
                                    // which is what the decoration would have drawn on a
                                    // wrapped title had it been animatable.
                                    modifier = Modifier.taskStrikethrough(
                                        progress = titleStrikeProgress,
                                        layout = titleLayoutResult,
                                        color = titleColor,
                                        thickness = TdayDimens.BorderWidthThick,
                                    ),
                                    color = titleColor,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    // No line cap in-app: show the whole title.
                                    maxLines = Int.MAX_VALUE,
                                    onTextLayout = { titleLayoutResult = it },
                                )
                                if (showDueText && dueSubtitleText != null) {
                                    Text(
                                        text = dueSubtitleText,
                                        color = if (isOverdue) colorScheme.error else colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                flattenNotesToPlainText(todo.description).takeIf { it.isNotBlank() }?.let { note ->
                                    val noteColor = colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    Text(
                                        text = note,
                                        // Struck alongside the title, on the title's own sweep,
                                        // so the whole task reads as one edit rather than as a
                                        // rule that grows and a rule that appears.
                                        modifier = Modifier.taskStrikethrough(
                                            progress = titleStrikeProgress,
                                            layout = noteLayoutResult,
                                            color = noteColor,
                                            thickness = TdayDimens.BorderWidthThick,
                                        ),
                                        color = noteColor,
                                        style = MaterialTheme.typography.bodySmall,
                                        onTextLayout = { noteLayoutResult = it },
                                    )
                                }
                            }
                        }
                        if (showListIndicator || showPriorityIcon) {
                            Row(
                                modifier = Modifier.padding(start = TdayDimens.SpacingMd, end = TdayDimens.Spacing3xl),
                                horizontalArrangement = Arrangement.spacedBy(TdayDimens.SpacingMd),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (showListIndicator) {
                                    Icon(
                                        imageVector = tdayListIconForKey(listMeta?.iconKey),
                                        contentDescription = stringResource(R.string.label_task_list),
                                        tint = listIndicatorColor,
                                        modifier = Modifier.size(RowTrailingIconSize),
                                    )
                                }
                                if (priorityIcon != null) {
                                    Icon(
                                        imageVector = priorityIcon,
                                        contentDescription = stringResource(R.string.label_priority_task),
                                        tint = tdayPriorityColor(todo.priority),
                                        modifier = Modifier.size(RowTrailingIconSize),
                                    )
                                }
                            }
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
private fun TodayTodoRow(
    todo: TodoItem,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDetailOverdue = !todo.completed && todo.due?.isBefore(Instant.now()) == true
    val detailDueText = todo.due?.let { due ->
        val dueText = TODO_DUE_TIME_FORMATTER.format(due)
        if (isDetailOverdue) {
            stringResource(R.string.todos_due_overdue_text, dueText)
        } else {
            dueText
        }
    }

    // The geometry below is `TdayTaskRowMetrics`, not literals:
    // `TdayTaskRowSkeleton` draws this same shape with the ink taken out, and a
    // placeholder that merely happens to match the row stops matching the first
    // time the row is re-spaced.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TdayTaskRowMetrics.RowSpacing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = TdayTaskRowMetrics.RowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularCheckToggleIcon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_circle_check_big),
                    contentDescription = stringResource(R.string.action_complete),
                    tint = TASK_CHECKMARK_GREEN,
                    onClick = onComplete,
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = TdayTaskRowMetrics.TextColumnStartPadding),
                ) {
                    Text(
                        text = todo.title,
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    detailDueText?.let { text ->
                        Text(
                            text = text,
                            color = if (isDetailOverdue) colorScheme.error else colorScheme.onSurfaceVariant.copy(
                                alpha = 0.8f
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    flattenNotesToPlainText(todo.description).takeIf { it.isNotBlank() }?.let { note ->
                        Text(
                            text = note,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_trash_2),
                    contentDescription = stringResource(R.string.action_delete),
                    tint = colorScheme.error,
                )
            }
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(TdayTaskRowMetrics.DividerThickness)
                .background(
                    colorScheme.outlineVariant.copy(alpha = TdayTaskRowMetrics.DividerAlpha),
                ),
        )
    }
}

@Composable
private fun TodoRow(
    todo: TodoItem,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val due = todo.due?.let(TODO_DUE_DATE_TIME_FORMATTER::format)

    Card(
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        shape = RoundedCornerShape(TdayDimens.RadiusRow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(TdayDimens.SpacingXl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularCheckToggleIcon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_circle_check_big),
                    contentDescription = stringResource(R.string.action_complete),
                    tint = TASK_CHECKMARK_GREEN,
                    onClick = onComplete,
                )

                Column(modifier = Modifier.padding(start = TdayDimens.SpacingLg)) {
                    Text(
                        text = todo.title,
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    due?.let { text ->
                        Text(
                            text = text,
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    flattenNotesToPlainText(todo.description).takeIf { it.isNotBlank() }?.let { note ->
                        Text(
                            text = note,
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_trash_2),
                    contentDescription = stringResource(R.string.action_delete),
                    tint = colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CircularCheckToggleIcon(
    imageVector: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .sizeIn(
                minWidth = TdayTaskRowMetrics.CheckTargetMinSize,
                minHeight = TdayTaskRowMetrics.CheckTargetMinSize,
            )
            .wrapContentSize(Alignment.Center)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    radius = CompletionToggleRippleRadius,
                ),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // The glyph crosses over rather than swapping. On web the check mark
        // appears inside a disc whose fill is already transitioning, so the
        // control reads as changing even though the mark itself does not; here
        // the glyph IS the whole control, and swapping it in one frame is the
        // toggle hard-cutting. Same behaviour, different mechanism — which is
        // what copying behaviour rather than implementation means.
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
            label = "circularCheckToggleGlyph",
        ) { glyph ->
            Icon(
                imageVector = glyph,
                // Only the glyph being crossed TO carries the description: for
                // the frames both exist, two identical labels in the tree would
                // have TalkBack announce the control twice.
                contentDescription = contentDescription.takeIf { glyph == imageVector },
                tint = tint,
                modifier = Modifier.size(TdayTaskRowMetrics.CheckGlyphSize),
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

private fun todoFabColorForMode(
    mode: TodoListMode,
    listColorKey: String?,
): Color {
    return modeAccentColor(
        mode = mode,
        listColorKey = listColorKey,
    )
}

private fun modeAccentColor(
    mode: TodoListMode,
    listColorKey: String?,
): Color {
    return when (mode) {
        TodoListMode.TODAY -> TdayTodoModeTodayAccent
        TodoListMode.OVERDUE -> TdayTodoModeOverdueAccent
        TodoListMode.SCHEDULED -> TdayTodoModeScheduledAccent
        TodoListMode.ALL -> TdayTodoModeAllAccent
        TodoListMode.PRIORITY -> TdayTodoModePriorityAccent
        TodoListMode.FLOATER -> listColorKey
            ?.takeIf { it.isNotBlank() }
            ?.let(::tdayListAccentColor)
            ?: TdayFloaterAccent
        TodoListMode.LIST -> tdayListAccentColor(listColorKey)
    }
}

private const val FLOATER_TASK_HOME_LIST_CONTAINER_COLOR_WEIGHT = 0.66f

/** Dim factor for a "resting" floater row: 1f = normal, lower = faded/dormant. */
private fun restingAlphaFor(
    mode: TodoListMode,
    todo: TodoItem,
    enabled: Boolean,
): Float {
    if (!enabled || mode != TodoListMode.FLOATER || todo.completed) return 1f
    return when (FloaterResting.tierFor(todo.updatedAt?.toEpochMilli(), System.currentTimeMillis())) {
        FloaterRestingTier.RESTING -> 0.45f
        FloaterRestingTier.FADING -> 0.6f
        else -> 1f
    }
}
