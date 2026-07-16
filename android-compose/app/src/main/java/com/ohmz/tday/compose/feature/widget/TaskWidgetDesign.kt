package com.ohmz.tday.compose.feature.widget

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.ui.priority.isImportantPriority
import com.ohmz.tday.compose.ui.priority.isUrgentPriority

internal enum class TaskWidgetContentState {
    SETUP,
    EMPTY,
    TASKS,
}

internal enum class TaskWidgetLayout {
    COMPACT,
    WIDE,
    MEDIUM,
    TALL,
}

internal val TaskWidgetResponsiveSizes = setOf(
    DpSize(150.dp, 110.dp),
    DpSize(250.dp, 126.dp),
    DpSize(250.dp, 140.dp),
    DpSize(250.dp, 220.dp),
)

internal data class TaskWidgetVisuals(
    val addButtonBackground: Int,
    val addIcon: Int,
    val emptyWatermark: Int,
    val setupWatermark: Int,
    // When set, every task row uses this check ring instead of a priority-coloured one
    // (floater tasks have no priority, so they use the widget's green accent).
    val priorityRingOverride: Int? = null,
)

internal data class TaskWidgetRow(
    val key: Long,
    val title: String,
    val priority: String,
    val trailingText: String? = null,
    val description: String? = null,
    /** Tapping the leading dot completes the task inline (widgets v2). */
    val completeAction: Action? = null,
)

private enum class TaskWidgetTextColor(@ColorRes val resourceId: Int) {
    PRIMARY(R.color.tday_widget_on_surface),
    SECONDARY(R.color.tday_widget_on_surface_variant),
}

@Composable
internal fun TaskWidgetContent(
    title: String,
    state: TaskWidgetContentState,
    taskCount: Int,
    countLabel: String,
    setupTitle: String,
    setupMessage: String,
    emptyTitle: String,
    emptyMessage: String,
    rows: List<TaskWidgetRow>,
    visuals: TaskWidgetVisuals,
    openAction: Action,
    addAction: Action,
) {
    val widgetSize = LocalSize.current
    val layout = taskWidgetLayoutFor(widgetSize)
    val metrics = taskWidgetMetrics(layout)
    val watermark = when (state) {
        TaskWidgetContentState.SETUP -> visuals.setupWatermark
        TaskWidgetContentState.EMPTY,
        TaskWidgetContentState.TASKS -> visuals.emptyWatermark
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_preview_background))
            .clickable(openAction),
    ) {
        TaskWidgetMessageBackground(
            watermark = watermark,
            metrics = metrics,
        )

        if (state != TaskWidgetContentState.TASKS) {
            TaskWidgetMessage(
                title = if (state == TaskWidgetContentState.SETUP) setupTitle else emptyTitle,
                message = if (state == TaskWidgetContentState.SETUP) setupMessage else "",
                compact = layout == TaskWidgetLayout.COMPACT,
                openAction = openAction,
            )
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(
                    start = metrics.horizontalPadding,
                    top = metrics.topPadding,
                    end = metrics.horizontalPadding,
                    bottom = metrics.bottomPadding,
                ),
        ) {
            TaskWidgetHeader(
                title = title,
                countLabel = countLabel,
                showCount = state == TaskWidgetContentState.TASKS,
                layout = layout,
                metrics = metrics,
                visuals = visuals,
                addAction = addAction,
            )

            if (state == TaskWidgetContentState.TASKS) {
                Spacer(modifier = GlanceModifier.height(metrics.contentSpacing))

                TaskWidgetList(
                    rows = rows,
                    layout = layout,
                    metrics = metrics,
                    visuals = visuals,
                    openAction = openAction,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight(),
                )
            }
        }
    }
}

@Composable
private fun TaskWidgetHeader(
    title: String,
    countLabel: String,
    showCount: Boolean,
    layout: TaskWidgetLayout,
    metrics: TaskWidgetMetrics,
    visuals: TaskWidgetVisuals,
    addAction: Action,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(metrics.headerHeight),
    ) {
        if (layout == TaskWidgetLayout.COMPACT) {
            // Compact lays the add button out INLINE as the trailing Row child. The
            // overlay-Box approach the wider layouts use silently dropped the button here
            // (the weighted count lockup left it no room), so the small widget had no way
            // to add a task. A leading weighted cell pushes the button to the right edge.
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showCount) {
                    CompactCountLockup(
                        countLabel = countLabel,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                } else {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                }
                AddButton(
                    visuals = visuals,
                    action = addAction,
                    size = metrics.addButtonSize,
                )
            }
        } else {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(end = metrics.addButtonSize + 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderText(
                    modifier = GlanceModifier
                        .width(metrics.headerTitleWidth)
                        .height(metrics.headerHeight),
                    text = title,
                    color = TaskWidgetTextColor.PRIMARY,
                    fontSize = if (layout == TaskWidgetLayout.TALL) 17.sp else 16.sp,
                    maxLines = 1,
                )
                if (showCount) {
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    CountPill(
                        label = countLabel,
                        modifier = GlanceModifier.width(metrics.headerCountWidth),
                    )
                }
            }

            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                AddButton(
                    visuals = visuals,
                    action = addAction,
                    size = metrics.addButtonSize,
                )
            }
        }
    }
}

@Composable
private fun CompactCountLockup(
    countLabel: String,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        HeaderText(
            modifier = GlanceModifier.fillMaxSize(),
            text = countLabel,
            color = TaskWidgetTextColor.SECONDARY,
            fontSize = 18.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun CountPill(
    label: String,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        modifier = modifier.height(26.dp),
        contentAlignment = Alignment.Center,
    ) {
        HeaderText(
            modifier = GlanceModifier.fillMaxWidth(),
            text = label,
            color = TaskWidgetTextColor.SECONDARY,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun AddButton(
    visuals: TaskWidgetVisuals,
    action: Action,
    size: Dp,
) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(ImageProvider(visuals.addButtonBackground))
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(visuals.addIcon),
            contentDescription = null,
            modifier = GlanceModifier.size(if (size < 48.dp) 17.dp else 18.dp),
        )
    }
}

// ColorProvider(resourceId) is Glance's documented way to use a color
// resource; the overload is only lint-restricted, not actually internal.
@android.annotation.SuppressLint("RestrictedApi")
@Composable
private fun HeaderText(
    text: String,
    color: TaskWidgetTextColor,
    fontSize: TextUnit,
    modifier: GlanceModifier = GlanceModifier,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
) {
    require(fontSize.isSp) { "Widget font sizes must be expressed in sp." }

    Box(
        modifier = modifier,
        contentAlignment = when (textAlign) {
            TextAlign.Center -> Alignment.Center
            TextAlign.Right,
            TextAlign.End -> Alignment.CenterEnd

            else -> Alignment.CenterStart
        },
    ) {
        Text(
            text = text,
            modifier = GlanceModifier.fillMaxWidth(),
            style = TextStyle(
                color = ColorProvider(color.resourceId),
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                textAlign = textAlign,
            ),
            maxLines = maxLines,
        )
    }
}

@Composable
private fun WidgetText(
    text: String,
    color: TaskWidgetTextColor,
    fontSize: TextUnit,
    modifier: GlanceModifier = GlanceModifier,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
    fillBounds: Boolean = false,
    fillWidth: Boolean = false,
) {
    require(fontSize.isSp) { "Widget font sizes must be expressed in sp." }

    val context = LocalContext.current
    val layoutId = when {
        fillWidth -> R.layout.widget_nunito_text_fill_width
        fillBounds -> R.layout.widget_nunito_text_fill
        else -> R.layout.widget_nunito_text_wrap
    }
    val remoteViews = RemoteViews(context.packageName, layoutId).apply {
        setTextViewText(R.id.widget_nunito_text, text)
        setTextViewTextSize(R.id.widget_nunito_text, TypedValue.COMPLEX_UNIT_SP, fontSize.value)
        setTextColor(R.id.widget_nunito_text, ContextCompat.getColor(context, color.resourceId))
        setInt(R.id.widget_nunito_text, "setGravity", textAlign.toWidgetGravity())
        setInt(R.id.widget_nunito_text, "setMaxLines", maxLines)
    }

    AndroidRemoteViews(
        remoteViews = remoteViews,
        modifier = modifier,
    )
}

private fun TextAlign.toWidgetGravity(): Int {
    return when (this) {
        TextAlign.Center -> Gravity.CENTER
        TextAlign.Left -> Gravity.LEFT or Gravity.CENTER_VERTICAL
        TextAlign.Right -> Gravity.RIGHT or Gravity.CENTER_VERTICAL
        TextAlign.End -> Gravity.END or Gravity.CENTER_VERTICAL
        else -> Gravity.START or Gravity.CENTER_VERTICAL
    }
}

@Composable
private fun TaskWidgetMessage(
    title: String,
    message: String,
    compact: Boolean,
    openAction: Action,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(openAction),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WidgetText(
                modifier = GlanceModifier.fillMaxWidth(),
                text = title,
                color = TaskWidgetTextColor.PRIMARY,
                fontSize = if (compact) 13.sp else 15.sp,
                textAlign = TextAlign.Center,
                maxLines = if (compact) 1 else 2,
                fillBounds = true,
            )
            if (!compact && message.isNotEmpty()) {
                Spacer(modifier = GlanceModifier.height(3.dp))
                WidgetText(
                    modifier = GlanceModifier.fillMaxWidth(),
                    text = message,
                    color = TaskWidgetTextColor.SECONDARY,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    fillBounds = true,
                )
            }
        }
    }
}

@Composable
private fun TaskWidgetMessageBackground(
    watermark: Int,
    metrics: TaskWidgetMetrics,
) {
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Image(
            provider = ImageProvider(watermark),
            contentDescription = null,
            modifier = GlanceModifier.size(metrics.messageWatermarkSize),
        )
    }
}

@Composable
private fun TaskWidgetList(
    rows: List<TaskWidgetRow>,
    layout: TaskWidgetLayout,
    metrics: TaskWidgetMetrics,
    visuals: TaskWidgetVisuals,
    openAction: Action,
    modifier: GlanceModifier = GlanceModifier,
) {
    LazyColumn(modifier = modifier) {
        itemsIndexed(rows, itemId = { _, row -> row.key }) { index, row ->
            // Row + divider must live under ONE root per item: Glance wraps multiple
            // item children in a Box (which overlaps them), so the divider landed on top
            // of the row instead of below it. A Column stacks them vertically.
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                TaskWidgetRow(
                    row = row,
                    layout = layout,
                    metrics = metrics,
                    visuals = visuals,
                    openAction = openAction,
                )
                if (index < rows.lastIndex) {
                    TaskWidgetRowDivider(gap = metrics.rowSpacing)
                }
            }
        }
    }
}

// ColorProvider(resourceId) is Glance's documented day/night color API; only lint-restricted.
@android.annotation.SuppressLint("RestrictedApi")
@Composable
private fun TaskWidgetRowDivider(gap: Dp) {
    // Native Notes-widget-style separator: a full-width hairline centred in the SAME
    // inter-row gap that used to be an empty Spacer, so it adds no height and the visible
    // row count is unchanged (still ~3 medium / ~9 large). Each row's own 3dp vertical
    // padding gives the line its breathing room, so a tiny gap here is not cramped.
    Box(
        modifier = GlanceModifier.fillMaxWidth().height(gap),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ColorProvider(R.color.tday_widget_divider)),
        ) {}
    }
}

@Composable
private fun TaskWidgetRow(
    row: TaskWidgetRow,
    layout: TaskWidgetLayout,
    metrics: TaskWidgetMetrics,
    visuals: TaskWidgetVisuals,
    openAction: Action,
) {
    val description = row.description?.takeIf { it.isNotBlank() }

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(openAction),
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            // Top-align so the dot/time sit on the first line of a wrapped
            // two-line title rather than centring across both lines.
            verticalAlignment = Alignment.Top,
        ) {
            // Widgets v2: the leading check ring is an inline complete target — tap it
            // to check the task off in place (parity with the iOS widget). The clickable
            // box pads past the 14dp ring so the tap target stays usable at home-screen
            // sizes without moving the layout.
            if (row.completeAction != null) {
                Box(
                    modifier = GlanceModifier
                        .clickable(row.completeAction)
                        .padding(top = 1.dp, bottom = 3.dp, end = 4.dp),
                ) {
                    PriorityCheckRing(
                        priority = row.priority,
                        size = 14.dp,
                        ringResourceOverride = visuals.priorityRingOverride,
                    )
                }
                Spacer(modifier = GlanceModifier.width(4.dp))
            } else {
                PriorityCheckRing(
                    priority = row.priority,
                    size = 14.dp,
                    ringResourceOverride = visuals.priorityRingOverride,
                    // Nudge down slightly to sit on the first line of a wrapped title.
                    modifier = GlanceModifier.padding(top = 1.dp),
                )
                Spacer(modifier = GlanceModifier.width(7.dp))
            }
            // Title + trailing time are ONE AndroidRemoteViews. The title is a
            // RemoteViews TextView (Nunito font), and Glance collapses a weighted
            // AndroidRemoteViews to zero width when the Row has a *trailing* Glance
            // sibling — which is exactly why medium/large (with a time chip) rendered
            // blank titles while small (no time) was fine. Folding the time INTO the
            // same RemoteViews removes that sibling: the title's 0dp+weight=1 now lives
            // inside the RemoteViews' own LinearLayout, which the framework sizes
            // reliably (same structure as the static previews).
            val trailing = row.trailingText?.takeIf { taskWidgetShowsTrailingText(layout) }
            Box(modifier = GlanceModifier.defaultWeight()) {
                TaskTitleAndTime(
                    modifier = GlanceModifier.fillMaxWidth(),
                    title = row.title,
                    trailingText = trailing,
                    titleFontSize = metrics.rowFontSize,
                )
            }
        }
        if (description != null) {
            WidgetText(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(start = 14.dp),
                text = description,
                color = TaskWidgetTextColor.SECONDARY,
                fontSize = 11.sp,
                maxLines = 2,
            )
        }
    }
}

// Renders the task title and its optional trailing time as a SINGLE RemoteViews
// (see widget_task_row_line.xml). This is the fix for blank titles on any widget
// size that shows the time: the title's 0dp+weight=1 lives inside the RemoteViews,
// so there is no trailing Glance sibling to collapse the weighted wrapper.
@Composable
private fun TaskTitleAndTime(
    title: String,
    trailingText: String?,
    titleFontSize: TextUnit,
    modifier: GlanceModifier = GlanceModifier,
) {
    require(titleFontSize.isSp) { "Widget font sizes must be expressed in sp." }
    val context = LocalContext.current
    val remoteViews = RemoteViews(context.packageName, R.layout.widget_task_row_line).apply {
        setTextViewText(R.id.widget_row_title, title)
        setTextViewTextSize(R.id.widget_row_title, TypedValue.COMPLEX_UNIT_SP, titleFontSize.value)
        setTextColor(R.id.widget_row_title, ContextCompat.getColor(context, TaskWidgetTextColor.PRIMARY.resourceId))
        setInt(R.id.widget_row_title, "setMaxLines", 2)
        if (trailingText != null) {
            setViewVisibility(R.id.widget_row_time, View.VISIBLE)
            setTextViewText(R.id.widget_row_time, trailingText)
            setTextViewTextSize(R.id.widget_row_time, TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(R.id.widget_row_time, ContextCompat.getColor(context, TaskWidgetTextColor.SECONDARY.resourceId))
        } else {
            setViewVisibility(R.id.widget_row_time, View.GONE)
        }
    }
    AndroidRemoteViews(remoteViews = remoteViews, modifier = modifier)
}

// The leading bullet is a hollow priority-coloured CHECK RING (matching the iOS
// widget's tappable Circle().strokeBorder), not a solid dot — so it reads as a
// checkbox you tap to complete the task in place.
@Composable
private fun PriorityCheckRing(
    priority: String,
    size: Dp,
    modifier: GlanceModifier = GlanceModifier,
    ringResourceOverride: Int? = null,
) {
    Image(
        provider = ImageProvider(ringResourceOverride ?: taskWidgetPriorityRingResource(priority)),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

internal fun taskWidgetPriorityRingResource(priority: String): Int {
    return when {
        isUrgentPriority(priority) -> R.drawable.widget_priority_ring_high
        isImportantPriority(priority) -> R.drawable.widget_priority_ring_medium
        else -> R.drawable.widget_priority_ring_low
    }
}

internal fun taskWidgetIsDaytime(hour: Int): Boolean {
    return hour in 6 until 18
}

internal fun taskWidgetLayoutFor(size: DpSize): TaskWidgetLayout {
    return when {
        size.height >= 208.dp -> TaskWidgetLayout.TALL
        size.height >= 140.dp -> TaskWidgetLayout.MEDIUM
        size.width >= 220.dp -> TaskWidgetLayout.WIDE
        else -> TaskWidgetLayout.COMPACT
    }
}

internal fun taskWidgetShowsTrailingText(layout: TaskWidgetLayout): Boolean {
    return layout == TaskWidgetLayout.MEDIUM || layout == TaskWidgetLayout.TALL
}

private fun taskWidgetMetrics(layout: TaskWidgetLayout): TaskWidgetMetrics {
    return when (layout) {
        TaskWidgetLayout.COMPACT -> TaskWidgetMetrics(
            horizontalPadding = 12.dp,
            topPadding = 5.dp,
            bottomPadding = 6.dp,
            headerHeight = 38.dp,
            headerTitleWidth = 0.dp,
            headerCountWidth = 0.dp,
            addButtonSize = 38.dp,
            contentSpacing = 5.dp,
            rowHeight = 22.dp,
            rowSpacing = 2.dp,
            rowFontSize = 12.sp,
            messageWatermarkSize = 112.dp,
        )

        TaskWidgetLayout.WIDE -> TaskWidgetMetrics(
            horizontalPadding = 12.dp,
            topPadding = 6.dp,
            bottomPadding = 7.dp,
            headerHeight = 42.dp,
            headerTitleWidth = 108.dp,
            headerCountWidth = 50.dp,
            addButtonSize = 42.dp,
            contentSpacing = 7.dp,
            rowHeight = 23.dp,
            rowSpacing = 2.dp,
            rowFontSize = 12.sp,
            messageWatermarkSize = 128.dp,
        )

        TaskWidgetLayout.MEDIUM -> TaskWidgetMetrics(
            horizontalPadding = 12.dp,
            topPadding = 6.dp,
            bottomPadding = 7.dp,
            headerHeight = 42.dp,
            headerTitleWidth = 108.dp,
            headerCountWidth = 50.dp,
            addButtonSize = 42.dp,
            contentSpacing = 7.dp,
            rowHeight = 22.dp,
            rowSpacing = 2.dp,
            rowFontSize = 12.sp,
            messageWatermarkSize = 148.dp,
        )

        TaskWidgetLayout.TALL -> TaskWidgetMetrics(
            horizontalPadding = 12.dp,
            topPadding = 8.dp,
            bottomPadding = 9.dp,
            headerHeight = 45.dp,
            headerTitleWidth = 108.dp,
            headerCountWidth = 50.dp,
            addButtonSize = 46.dp,
            contentSpacing = 8.dp,
            rowHeight = 25.dp,
            rowSpacing = 3.dp,
            rowFontSize = 13.sp,
            messageWatermarkSize = 208.dp,
        )
    }
}

private fun taskWidgetVisibleRowCount(
    size: DpSize,
    metrics: TaskWidgetMetrics,
): Int {
    val availableHeight = size.height.value -
        metrics.topPadding.value -
        metrics.bottomPadding.value -
        metrics.headerHeight.value -
        metrics.contentSpacing.value
    val rowStep = metrics.rowHeight.value + metrics.rowSpacing.value
    return ((availableHeight + metrics.rowSpacing.value) / rowStep)
        .toInt()
        .coerceAtLeast(1)
}

private data class TaskWidgetMetrics(
    val horizontalPadding: Dp,
    val topPadding: Dp,
    val bottomPadding: Dp,
    val headerHeight: Dp,
    val headerTitleWidth: Dp,
    val headerCountWidth: Dp,
    val addButtonSize: Dp,
    val contentSpacing: Dp,
    val rowHeight: Dp,
    val rowSpacing: Dp,
    val rowFontSize: TextUnit,
    val messageWatermarkSize: Dp,
)
