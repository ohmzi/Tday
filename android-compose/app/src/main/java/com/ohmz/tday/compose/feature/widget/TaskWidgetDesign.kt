package com.ohmz.tday.compose.feature.widget

import android.util.TypedValue
import android.view.Gravity
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
import androidx.glance.text.TextAlign
import com.ohmz.tday.compose.R
import java.util.Locale

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
)

internal data class TaskWidgetRow(
    val key: Long,
    val title: String,
    val priority: String,
    val trailingText: String? = null,
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
    overflowCount: Int,
    overflowLabel: String,
    visuals: TaskWidgetVisuals,
    openAction: Action,
    addAction: Action,
) {
    val layout = taskWidgetLayoutFor(LocalSize.current)
    val metrics = taskWidgetMetrics(layout)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_preview_background))
            .clickable(openAction),
    ) {
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
                layout = layout,
                metrics = metrics,
                visuals = visuals,
                addAction = addAction,
            )

            Spacer(modifier = GlanceModifier.height(metrics.contentSpacing))

            when (state) {
                TaskWidgetContentState.SETUP -> TaskWidgetMessage(
                    title = setupTitle,
                    message = setupMessage,
                    compact = layout == TaskWidgetLayout.COMPACT,
                    openAction = openAction,
                )

                TaskWidgetContentState.EMPTY -> TaskWidgetMessage(
                    title = emptyTitle,
                    message = emptyMessage,
                    compact = layout == TaskWidgetLayout.COMPACT,
                    openAction = openAction,
                )

                TaskWidgetContentState.TASKS -> TaskWidgetList(
                    rows = rows,
                    overflowCount = overflowCount,
                    overflowLabel = overflowLabel,
                    layout = layout,
                    metrics = metrics,
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
    layout: TaskWidgetLayout,
    metrics: TaskWidgetMetrics,
    visuals: TaskWidgetVisuals,
    addAction: Action,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(metrics.headerHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (layout == TaskWidgetLayout.COMPACT) {
            CompactCountLockup(
                countLabel = countLabel,
                modifier = GlanceModifier.width(metrics.compactCountWidth),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
        } else {
            WidgetText(
                modifier = GlanceModifier.width(metrics.headerTitleWidth),
                text = title,
                color = TaskWidgetTextColor.PRIMARY,
                fontSize = if (layout == TaskWidgetLayout.TALL) 17.sp else 16.sp,
                maxLines = 1,
                fillBounds = true,
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            CountPill(
                label = countLabel,
            )
        }

        Spacer(modifier = GlanceModifier.width(if (layout == TaskWidgetLayout.COMPACT) 8.dp else 10.dp))
        AddButton(
            visuals = visuals,
            action = addAction,
            size = metrics.addButtonSize,
        )
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
        WidgetText(
            modifier = GlanceModifier.fillMaxSize(),
            text = countLabel,
            color = TaskWidgetTextColor.SECONDARY,
            fontSize = 18.sp,
            maxLines = 1,
            fillBounds = true,
        )
    }
}

@Composable
private fun CountPill(
    label: String,
) {
    WidgetText(
        modifier = GlanceModifier
            .height(26.dp)
            .padding(start = 10.dp, end = 10.dp),
        text = label,
        color = TaskWidgetTextColor.SECONDARY,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
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

@Composable
private fun WidgetText(
    text: String,
    color: TaskWidgetTextColor,
    fontSize: TextUnit,
    modifier: GlanceModifier = GlanceModifier,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
    fillBounds: Boolean = false,
) {
    require(fontSize.isSp) { "Widget font sizes must be expressed in sp." }

    val context = LocalContext.current
    val layoutId = if (fillBounds) {
        R.layout.widget_nunito_text_fill
    } else {
        R.layout.widget_nunito_text_wrap
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
            if (!compact) {
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
private fun TaskWidgetList(
    rows: List<TaskWidgetRow>,
    overflowCount: Int,
    overflowLabel: String,
    layout: TaskWidgetLayout,
    metrics: TaskWidgetMetrics,
    openAction: Action,
    modifier: GlanceModifier = GlanceModifier,
) {
    val totalCount = rows.size + overflowCount
    val rowCapacity = metrics.visibleRowCapacity
    val visibleTaskSlots =
        if (totalCount > rowCapacity && rowCapacity > 1) rowCapacity - 1 else rowCapacity
    val visibleRows = rows.take(visibleTaskSlots)
    val hiddenCount = (totalCount - visibleRows.size).coerceAtLeast(0)

    Column(modifier = modifier) {
        visibleRows.forEachIndexed { index, row ->
            TaskWidgetRow(
                row = row,
                layout = layout,
                metrics = metrics,
                openAction = openAction,
            )
            if (index < visibleRows.lastIndex || hiddenCount > 0) {
                Spacer(modifier = GlanceModifier.height(metrics.rowSpacing))
            }
        }
        if (hiddenCount > 0 && rowCapacity > 1) {
            OverflowRow(
                label = String.format(Locale.getDefault(), overflowLabel, hiddenCount),
                metrics = metrics,
                openAction = openAction,
            )
        }
    }
}

@Composable
private fun TaskWidgetRow(
    row: TaskWidgetRow,
    layout: TaskWidgetLayout,
    metrics: TaskWidgetMetrics,
    openAction: Action,
) {
    val modifier = GlanceModifier
        .fillMaxWidth()
        .height(metrics.rowHeight)
        .clickable(openAction)
        .padding(start = 2.dp, end = 2.dp)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val titleWidth = if (taskWidgetShowsTrailingText(layout) && row.trailingText != null) {
            metrics.rowTitleWidthWithTrailing
        } else {
            metrics.rowTitleWidth
        }

        PriorityDot(size = 7.dp)
        Spacer(modifier = GlanceModifier.width(7.dp))
        WidgetText(
            modifier = GlanceModifier.width(titleWidth),
            text = row.title,
            color = TaskWidgetTextColor.PRIMARY,
            fontSize = metrics.rowFontSize,
            maxLines = 1,
            fillBounds = true,
        )
        if (taskWidgetShowsTrailingText(layout) && row.trailingText != null) {
            Spacer(modifier = GlanceModifier.width(6.dp))
            TimeChip(text = row.trailingText)
        }
    }
}

@Composable
private fun TimeChip(text: String) {
    WidgetText(
        modifier = GlanceModifier
            .height(22.dp)
            .padding(start = 7.dp, end = 7.dp),
        text = text,
        color = TaskWidgetTextColor.SECONDARY,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
private fun OverflowRow(
    label: String,
    metrics: TaskWidgetMetrics,
    openAction: Action,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(metrics.rowHeight)
            .padding(start = 16.dp, end = 2.dp)
            .clickable(openAction),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetText(
            text = label,
            color = TaskWidgetTextColor.SECONDARY,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun PriorityDot(size: Dp) {
    Image(
        provider = ImageProvider(R.drawable.widget_priority_dot_neutral),
        contentDescription = null,
        modifier = GlanceModifier.size(size),
    )
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
            horizontalPadding = 13.dp,
            topPadding = 5.dp,
            bottomPadding = 6.dp,
            headerHeight = 44.dp,
            compactCountWidth = 74.dp,
            headerTitleWidth = 0.dp,
            addButtonSize = 42.dp,
            contentSpacing = 4.dp,
            rowHeight = 22.dp,
            rowSpacing = 2.dp,
            rowFontSize = 12.sp,
            rowTitleWidth = 122.dp,
            rowTitleWidthWithTrailing = 0.dp,
            visibleRowCapacity = 2,
        )

        TaskWidgetLayout.WIDE -> TaskWidgetMetrics(
            horizontalPadding = 14.dp,
            topPadding = 6.dp,
            bottomPadding = 7.dp,
            headerHeight = 44.dp,
            compactCountWidth = 74.dp,
            headerTitleWidth = 112.dp,
            addButtonSize = 44.dp,
            contentSpacing = 5.dp,
            rowHeight = 23.dp,
            rowSpacing = 2.dp,
            rowFontSize = 12.sp,
            rowTitleWidth = 184.dp,
            rowTitleWidthWithTrailing = 112.dp,
            visibleRowCapacity = 2,
        )

        TaskWidgetLayout.MEDIUM -> TaskWidgetMetrics(
            horizontalPadding = 14.dp,
            topPadding = 6.dp,
            bottomPadding = 7.dp,
            headerHeight = 44.dp,
            compactCountWidth = 74.dp,
            headerTitleWidth = 112.dp,
            addButtonSize = 44.dp,
            contentSpacing = 5.dp,
            rowHeight = 22.dp,
            rowSpacing = 2.dp,
            rowFontSize = 12.sp,
            rowTitleWidth = 184.dp,
            rowTitleWidthWithTrailing = 112.dp,
            visibleRowCapacity = 3,
        )

        TaskWidgetLayout.TALL -> TaskWidgetMetrics(
            horizontalPadding = 14.dp,
            topPadding = 8.dp,
            bottomPadding = 9.dp,
            headerHeight = 48.dp,
            compactCountWidth = 74.dp,
            headerTitleWidth = 124.dp,
            addButtonSize = 48.dp,
            contentSpacing = 7.dp,
            rowHeight = 25.dp,
            rowSpacing = 3.dp,
            rowFontSize = 13.sp,
            rowTitleWidth = 184.dp,
            rowTitleWidthWithTrailing = 112.dp,
            visibleRowCapacity = 5,
        )
    }
}

private data class TaskWidgetMetrics(
    val horizontalPadding: Dp,
    val topPadding: Dp,
    val bottomPadding: Dp,
    val headerHeight: Dp,
    val compactCountWidth: Dp,
    val headerTitleWidth: Dp,
    val addButtonSize: Dp,
    val contentSpacing: Dp,
    val rowHeight: Dp,
    val rowSpacing: Dp,
    val rowFontSize: TextUnit,
    val rowTitleWidth: Dp,
    val rowTitleWidthWithTrailing: Dp,
    val visibleRowCapacity: Int,
)
