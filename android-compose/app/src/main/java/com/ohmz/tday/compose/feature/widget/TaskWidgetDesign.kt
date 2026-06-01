package com.ohmz.tday.compose.feature.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
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
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.ohmz.tday.compose.R
import java.util.Locale

internal val TdayWidgetFontFamily = FontFamily("Nunito")

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
                modifier = GlanceModifier.defaultWeight(),
            )
        } else {
            Text(
                modifier = GlanceModifier.defaultWeight(),
                text = title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontFamily = TdayWidgetFontFamily,
                    fontSize = if (layout == TaskWidgetLayout.TALL) 17.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
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
        Text(
            text = countLabel,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontFamily = TdayWidgetFontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun CountPill(
    label: String,
) {
    Text(
        modifier = GlanceModifier
            .height(26.dp)
            .padding(start = 10.dp, end = 10.dp),
        text = label,
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontFamily = TdayWidgetFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        ),
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
            Text(
                text = title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontFamily = TdayWidgetFontFamily,
                    fontSize = if (compact) 13.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                maxLines = if (compact) 1 else 2,
            )
            if (!compact) {
                Spacer(modifier = GlanceModifier.height(3.dp))
                Text(
                    text = message,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontFamily = TdayWidgetFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 2,
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
        PriorityDot(size = 7.dp)
        Spacer(modifier = GlanceModifier.width(7.dp))
        Text(
            modifier = GlanceModifier.defaultWeight(),
            text = row.title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontFamily = TdayWidgetFontFamily,
                fontSize = metrics.rowFontSize,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        if (taskWidgetShowsTrailingText(layout) && row.trailingText != null) {
            Spacer(modifier = GlanceModifier.width(6.dp))
            TimeChip(text = row.trailingText)
        }
    }
}

@Composable
private fun TimeChip(text: String) {
    Text(
        modifier = GlanceModifier
            .height(22.dp)
            .padding(start = 7.dp, end = 7.dp),
        text = text,
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontFamily = TdayWidgetFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        ),
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
        Text(
            text = label,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontFamily = TdayWidgetFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            ),
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
            addButtonSize = 42.dp,
            contentSpacing = 4.dp,
            rowHeight = 22.dp,
            rowSpacing = 2.dp,
            rowFontSize = 12.sp,
            visibleRowCapacity = 2,
        )

        TaskWidgetLayout.WIDE -> TaskWidgetMetrics(
            horizontalPadding = 14.dp,
            topPadding = 6.dp,
            bottomPadding = 7.dp,
            headerHeight = 44.dp,
            addButtonSize = 44.dp,
            contentSpacing = 5.dp,
            rowHeight = 23.dp,
            rowSpacing = 2.dp,
            rowFontSize = 12.sp,
            visibleRowCapacity = 2,
        )

        TaskWidgetLayout.MEDIUM -> TaskWidgetMetrics(
            horizontalPadding = 14.dp,
            topPadding = 6.dp,
            bottomPadding = 7.dp,
            headerHeight = 44.dp,
            addButtonSize = 44.dp,
            contentSpacing = 5.dp,
            rowHeight = 22.dp,
            rowSpacing = 2.dp,
            rowFontSize = 12.sp,
            visibleRowCapacity = 3,
        )

        TaskWidgetLayout.TALL -> TaskWidgetMetrics(
            horizontalPadding = 14.dp,
            topPadding = 8.dp,
            bottomPadding = 9.dp,
            headerHeight = 48.dp,
            addButtonSize = 48.dp,
            contentSpacing = 7.dp,
            rowHeight = 25.dp,
            rowSpacing = 3.dp,
            rowFontSize = 13.sp,
            visibleRowCapacity = 5,
        )
    }
}

private data class TaskWidgetMetrics(
    val horizontalPadding: Dp,
    val topPadding: Dp,
    val bottomPadding: Dp,
    val headerHeight: Dp,
    val addButtonSize: Dp,
    val contentSpacing: Dp,
    val rowHeight: Dp,
    val rowSpacing: Dp,
    val rowFontSize: TextUnit,
    val visibleRowCapacity: Int,
)
