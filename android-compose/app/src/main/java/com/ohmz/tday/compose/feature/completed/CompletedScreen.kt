package com.ohmz.tday.compose.feature.completed

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.text.flattenNotesToPlainText
import com.ohmz.tday.compose.core.ui.EmptyTaskWatermark
import com.ohmz.tday.compose.core.ui.TaskSwipeActionButton
import com.ohmz.tday.compose.core.ui.TdayEmptyState
import com.ohmz.tday.compose.core.ui.TdayHeroToolbar
import com.ohmz.tday.compose.core.ui.TdaySearchCapsule
import com.ohmz.tday.compose.core.ui.animateTaskSwipeOffsetAsState
import com.ohmz.tday.compose.core.ui.rememberLazyListHeroTitleCollapse
import com.ohmz.tday.compose.core.ui.rememberTaskSwipeRevealState
import com.ohmz.tday.compose.core.ui.tdayBarButtonContainerColor
import com.ohmz.tday.compose.core.ui.tdayHeroTitleItem
import com.ohmz.tday.compose.core.ui.TdayHeroTitleMetrics
import com.ohmz.tday.compose.core.ui.tdayClosesSearchOnOutsideTap
import com.ohmz.tday.compose.ui.component.CreateTaskBottomSheet
import com.ohmz.tday.compose.ui.theme.TdayCompletedTitleAccent
import com.ohmz.tday.compose.ui.theme.TdayDimens
import com.ohmz.tday.compose.ui.theme.TdayFloaterAccent
import com.ohmz.tday.compose.ui.theme.TdaySwipeDeleteBackground
import com.ohmz.tday.compose.ui.theme.TdaySwipeEditBackground
import com.ohmz.tday.compose.ui.theme.TdayTaskCompleteAccent
import com.ohmz.tday.compose.ui.theme.tdayListAccentColor
import com.ohmz.tday.compose.ui.theme.tdayListIconForKey
import com.ohmz.tday.compose.ui.theme.tdayPriorityColor
import kotlinx.coroutines.delay
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
private const val COMPLETED_RESTORE_STEP_MS = 180L
private const val COMPLETED_RESTORE_FADE_MS = 260L

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
    val showEmptyState = visibleItems.isEmpty() && !uiState.isLoading
    val heroCollapse = rememberLazyListHeroTitleCollapse(listState = listState)
    val completedTitle = stringResource(R.string.completed_title)
    val completedIcon = ImageVector.vectorResource(R.drawable.ic_lucide_circle_check_big)
    var collapsedSectionKeys by rememberSaveable {
        mutableStateOf(emptySet<String>())
    }
    var editTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var openSwipeTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    val editTarget = remember(editTargetId, uiState.items) {
        editTargetId?.let { targetId -> uiState.items.firstOrNull { it.id == targetId } }
    }
    LaunchedEffect(uiState.items, openSwipeTaskId) {
        val openId = openSwipeTaskId ?: return@LaunchedEffect
        if (uiState.items.none { it.id == openId }) {
            openSwipeTaskId = null
        }
    }
    BackHandler(enabled = searchExpanded) {
        closeSearch()
    }

    Scaffold(containerColor = colorScheme.background) { padding ->
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
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
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
                                    .animateItem(
                                        fadeInSpec = null,
                                        placementSpec = tween(
                                            durationMillis = 320,
                                            easing = FastOutSlowInEasing,
                                        ),
                                        fadeOutSpec = null,
                                    )
                                    .padding(
                                        top = if (sectionIndex == 0) 0.dp else CompletedTimelineSectionTopSpacing,
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
                                                fadeInSpec = tween(
                                                    durationMillis = 190,
                                                    easing = FastOutSlowInEasing,
                                                ),
                                                placementSpec = tween(
                                                    durationMillis = 320,
                                                    easing = FastOutSlowInEasing,
                                                ),
                                                fadeOutSpec = tween(
                                                    durationMillis = 150,
                                                    easing = FastOutSlowInEasing,
                                                ),
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
                                        openSwipeTaskId = openSwipeTaskId,
                                        onOpenSwipeTaskIdChange = { openSwipeTaskId = it },
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.items.isEmpty() && uiState.isLoading) {
                        item {
                            EmptyCompletedState(
                                message = stringResource(R.string.label_loading),
                            )
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
                                    modifier = Modifier.padding(vertical = 24.dp),
                                )
                            } else {
                                TdayEmptyState(
                                    icon = R.drawable.ic_lucide_circle_check_big,
                                    accentColor = COMPLETED_TITLE_COLOR,
                                    title = stringResource(R.string.completed_empty),
                                    description = stringResource(R.string.completed_empty_body),
                                    modifier = Modifier.padding(vertical = 24.dp),
                                )
                            }
                        }
                    }

                    uiState.errorMessage?.let { message ->
                        item {
                            com.ohmz.tday.compose.core.ui.ErrorRetryCard(
                                message = message,
                                onRetry = onRefresh,
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(96.dp)) }
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
            onUpdateTask = { _, payload ->
                onUpdateTask(completed, payload)
                editTargetId = null
            },
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
                    .padding(start = 6.dp)
                    .size(18.dp)
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
    openSwipeTaskId: String?,
    onOpenSwipeTaskIdChange: (String?) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val swipeRevealState = rememberTaskSwipeRevealState(item.id)
    var restorePhase by remember(item.id) { mutableStateOf(CompletedRestorePhase.Completed) }
    val latestOpenSwipeTaskId = rememberUpdatedState(openSwipeTaskId)
    fun claimSwipeSlot() {
        if (latestOpenSwipeTaskId.value != item.id) {
            onOpenSwipeTaskIdChange(item.id)
        }
    }

    fun closeSwipeSlot() {
        swipeRevealState.close()
        if (latestOpenSwipeTaskId.value == item.id) {
            onOpenSwipeTaskIdChange(null)
        }
    }
    val animatedOffsetX by animateTaskSwipeOffsetAsState(
        state = swipeRevealState,
        label = "completedSwipeOffset",
    )
    val actionRevealProgress = swipeRevealState.revealProgress(animatedOffsetX)
    val showCompletedCheckmark = restorePhase == CompletedRestorePhase.Completed
    val showStrikethrough =
        restorePhase == CompletedRestorePhase.Completed || restorePhase == CompletedRestorePhase.Unchecked
    val isFading = restorePhase == CompletedRestorePhase.Fading
    val isRestoring = restorePhase != CompletedRestorePhase.Completed
    val rowAlpha by animateFloatAsState(
        targetValue = if (isFading) 0f else 1f,
        animationSpec = tween(
            durationMillis = COMPLETED_RESTORE_FADE_MS.toInt(),
            easing = FastOutSlowInEasing
        ),
        label = "completedRestoreRowAlpha",
    )
    val rowScale by animateFloatAsState(
        targetValue = if (isFading) 0.985f else 1f,
        animationSpec = tween(
            durationMillis = COMPLETED_RESTORE_FADE_MS.toInt(),
            easing = FastOutSlowInEasing
        ),
        label = "completedRestoreRowScale",
    )
    val rowOffsetY by animateDpAsState(
        targetValue = if (isFading) (-10).dp else 0.dp,
        animationSpec = tween(
            durationMillis = COMPLETED_RESTORE_FADE_MS.toInt(),
            easing = FastOutSlowInEasing
        ),
        label = "completedRestoreRowOffsetY",
    )
    val titleColor by animateColorAsState(
        targetValue = if (showStrikethrough) {
            colorScheme.onSurface.copy(alpha = 0.78f)
        } else {
            colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 160),
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
    val rowShape = RoundedCornerShape(16.dp)
    val foregroundColor = colorScheme.background
    LaunchedEffect(openSwipeTaskId, item.id) {
        if (openSwipeTaskId != null && openSwipeTaskId != item.id && swipeRevealState.isOpenOrDragging) {
            swipeRevealState.close()
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CompletedSwipeRowHeight),
        ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
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
                            ViewCompat.performHapticFeedback(
                                view,
                                HapticFeedbackConstantsCompat.CLOCK_TICK,
                            )
                            closeSwipeSlot()
                            onInfo()
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
                            ViewCompat.performHapticFeedback(
                                view,
                                HapticFeedbackConstantsCompat.CLOCK_TICK,
                            )
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
                                swipeRevealState.dragBy(delta)
                                if (!swipeRevealState.isOpenOrDragging && latestOpenSwipeTaskId.value == item.id) {
                                    onOpenSwipeTaskIdChange(null)
                                }
                            },
                            onDragStopped = { velocity ->
                                swipeRevealState.settle(velocity)
                                if (swipeRevealState.isOpenOrDragging) {
                                    claimSwipeSlot()
                                } else if (latestOpenSwipeTaskId.value == item.id) {
                                    onOpenSwipeTaskIdChange(null)
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
                                    swipeRevealState.playHint()
                                    if (latestOpenSwipeTaskId.value == item.id && !swipeRevealState.isOpenOrDragging) {
                                        onOpenSwipeTaskIdChange(null)
                                    }
                                }
                            }
                        },
                    shape = rowShape,
                    colors = CardDefaults.cardColors(containerColor = foregroundColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CompletedCircularToggleIcon(
                            imageVector = if (showCompletedCheckmark) {
                                ImageVector.vectorResource(R.drawable.ic_lucide_circle_check_big)
                            } else {
                                ImageVector.vectorResource(R.drawable.ic_lucide_circle)
                            },
                            contentDescription = stringResource(R.string.label_undo_complete),
                            tint = if (showCompletedCheckmark) {
                                TdayTaskCompleteAccent
                            } else {
                                colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                            },
                            enabled = !isRestoring,
                            onClick = {
                                ViewCompat.performHapticFeedback(
                                    view,
                                    HapticFeedbackConstantsCompat.CLOCK_TICK,
                                )
                                closeSwipeSlot()
                                coroutineScope.launch {
                                    restorePhase = CompletedRestorePhase.Unchecked
                                    delay(COMPLETED_RESTORE_STEP_MS)
                                    restorePhase = CompletedRestorePhase.Unstruck
                                    delay(COMPLETED_RESTORE_STEP_MS)
                                    restorePhase = CompletedRestorePhase.Fading
                                    delay(COMPLETED_RESTORE_FADE_MS)
                                    onUncomplete()
                                }
                            },
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp),
                        ) {
                            Text(
                                text = item.title,
                                color = titleColor,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                // Real per-line strikethrough crosses out every line of a
                                // wrapped title instead of one rule down the middle, the
                                // same as the task list's own row.
                                textDecoration = if (showStrikethrough) {
                                    TextDecoration.LineThrough
                                } else {
                                    TextDecoration.None
                                },
                                maxLines = 2,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
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
                                        modifier = Modifier.size(13.dp),
                                    )
                                }
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_clock),
                                    contentDescription = null,
                                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
                                    modifier = Modifier.size(13.dp),
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
                                modifier = Modifier.padding(end = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (showListIndicator) {
                                    Icon(
                                        imageVector = tdayListIconForKey(listMeta?.iconKey),
                                        contentDescription = stringResource(R.string.label_task_list),
                                        tint = listIndicatorColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Icon(
                                    imageVector = priorityIcon
                                        ?: ImageVector.vectorResource(R.drawable.ic_lucide_flag),
                                    contentDescription = stringResource(R.string.label_priority_task),
                                    tint = tdayPriorityColor(item.priority),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else if (showListIndicator) {
                            Icon(
                                imageVector = tdayListIconForKey(listMeta?.iconKey),
                                contentDescription = stringResource(R.string.label_task_list),
                                tint = listIndicatorColor,
                                modifier = Modifier
                                    .padding(end = 24.dp)
                                    .size(18.dp),
                            )
                        }
                    }
                }
            }
        if (showDateDivider) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
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
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    radius = 14.dp,
                ),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
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
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        label = "completedBarButtonScale",
    )
    val offsetY by animateDpAsState(
        targetValue = if (pressed) 2.dp else 0.dp,
        label = "completedBarButtonOffsetY",
    )

    Card(
        modifier = Modifier
            .offset(y = offsetY)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        onClick = {
            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
            onClick()
        },
        interactionSource = interactionSource,
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = tdayBarButtonContainerColor()),
        elevation = CardDefaults.cardElevation(
            defaultElevation = TdayDimens.BarButtonElevation,
            pressedElevation = 0.dp,
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
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun EmptyCompletedState(
    message: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 110.dp, bottom = 180.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
        )
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
