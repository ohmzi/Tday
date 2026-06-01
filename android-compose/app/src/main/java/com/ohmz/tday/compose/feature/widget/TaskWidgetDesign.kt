package com.ohmz.tday.compose.feature.widget

import android.annotation.SuppressLint
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
import androidx.glance.unit.ColorProvider
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

internal data class TaskWidgetVisuals(
    val accentColor: ColorProvider,
    val accentWash: ColorProvider,
    val countPillBackground: Int,
    val addButtonBackground: Int,
    val addIcon: Int,
    val featuredRowBackground: Int,
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
    countUnit: String,
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
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(3.dp)
                .background(visuals.accentWash),
        ) {}

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
                count = taskCount,
                countLabel = countLabel,
                countUnit = countUnit,
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
                    visuals = visuals,
                    openAction = openAction,
                )

                TaskWidgetContentState.EMPTY -> TaskWidgetMessage(
                    title = emptyTitle,
                    message = emptyMessage,
                    compact = layout == TaskWidgetLayout.COMPACT,
                    visuals = visuals,
                    openAction = openAction,
                )

                TaskWidgetContentState.TASKS -> TaskWidgetList(
                    rows = rows,
                    overflowCount = overflowCount,
                    overflowLabel = overflowLabel,
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
    count: Int,
    countLabel: String,
    countUnit: String,
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
                count = count,
                countUnit = countUnit,
                visuals = visuals,
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
                visuals = visuals,
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
    count: Int,
    countUnit: String,
    visuals: TaskWidgetVisuals,
    modifier: GlanceModifier = GlanceModifier,
) {
    Column(
        modifier = modifier,
    ) {
        Text(
            text = count.toString(),
            style = TextStyle(
                color = visuals.accentColor,
                fontFamily = TdayWidgetFontFamily,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Text(
            text = countUnit,
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
private fun CountPill(
    label: String,
    visuals: TaskWidgetVisuals,
) {
    Text(
        modifier = GlanceModifier
            .height(26.dp)
            .background(ImageProvider(visuals.countPillBackground))
            .padding(start = 10.dp, end = 10.dp),
        text = label,
        style = TextStyle(
            color = visuals.accentColor,
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
    visuals: TaskWidgetVisuals,
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
            Box(
                modifier = GlanceModifier
                    .width(if (compact) 28.dp else 34.dp)
                    .height(3.dp)
                    .background(visuals.accentWash),
            ) {}
            Spacer(modifier = GlanceModifier.height(if (compact) 6.dp else 8.dp))
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
    visuals: TaskWidgetVisuals,
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
                visuals = visuals,
                featured = layout == TaskWidgetLayout.TALL && index == 0,
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
                visuals = visuals,
                openAction = openAction,
            )
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
private fun TaskWidgetRow(
    row: TaskWidgetRow,
    layout: TaskWidgetLayout,
    metrics: TaskWidgetMetrics,
    visuals: TaskWidgetVisuals,
    featured: Boolean,
    openAction: Action,
) {
    var modifier = GlanceModifier
        .fillMaxWidth()
        .height(if (featured) metrics.featuredRowHeight else metrics.rowHeight)
        .clickable(openAction)

    modifier = if (featured) {
        modifier
            .background(ImageProvider(visuals.featuredRowBackground))
            .padding(start = 9.dp, end = 9.dp)
    } else {
        modifier.padding(start = 2.dp, end = 2.dp)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PriorityDot(priority = row.priority, size = if (featured) 8.dp else 7.dp)
        Spacer(modifier = GlanceModifier.width(if (featured) 8.dp else 7.dp))
        Text(
            modifier = GlanceModifier.defaultWeight(),
            text = row.title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontFamily = TdayWidgetFontFamily,
                fontSize = if (featured) 13.sp else metrics.rowFontSize,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        if (layout != TaskWidgetLayout.COMPACT && row.trailingText != null) {
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
            .background(ImageProvider(R.drawable.widget_due_time_chip_background))
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
    visuals: TaskWidgetVisuals,
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
                color = visuals.accentColor,
                fontFamily = TdayWidgetFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun PriorityDot(priority: String, size: Dp) {
    Image(
        provider = ImageProvider(priorityDotResource(priority)),
        contentDescription = null,
        modifier = GlanceModifier.size(size),
    )
}

private fun priorityDotResource(priority: String): Int {
    return when (priority.trim().lowercase(Locale.getDefault())) {
        "high", "urgent", "important" -> R.drawable.widget_priority_dot_high
        "medium" -> R.drawable.widget_priority_dot_medium
        else -> R.drawable.widget_priority_dot_low
    }
}

internal fun taskWidgetLayoutFor(size: DpSize): TaskWidgetLayout {
    return when {
        size.height >= 210.dp -> TaskWidgetLayout.TALL
        size.height >= 150.dp -> TaskWidgetLayout.MEDIUM
        size.width >= 220.dp -> TaskWidgetLayout.WIDE
        else -> TaskWidgetLayout.COMPACT
    }
}

private fun taskWidgetMetrics(layout: TaskWidgetLayout): TaskWidgetMetrics {
    return when (layout) {
        TaskWidgetLayout.COMPACT -> TaskWidgetMetrics(
            horizontalPadding = 13.dp,
            topPadding = 6.dp,
            bottomPadding = 7.dp,
            headerHeight = 40.dp,
            addButtonSize = 42.dp,
            contentSpacing = 4.dp,
            rowHeight = 22.dp,
            featuredRowHeight = 22.dp,
            rowSpacing = 2.dp,
            rowFontSize = 12.sp,
            visibleRowCapacity = 2,
        )

        TaskWidgetLayout.WIDE -> TaskWidgetMetrics(
            horizontalPadding = 14.dp,
            topPadding = 6.dp,
            bottomPadding = 7.dp,
            headerHeight = 42.dp,
            addButtonSize = 44.dp,
            contentSpacing = 5.dp,
            rowHeight = 23.dp,
            featuredRowHeight = 23.dp,
            rowSpacing = 2.dp,
            rowFontSize = 12.sp,
            visibleRowCapacity = 2,
        )

        TaskWidgetLayout.MEDIUM -> TaskWidgetMetrics(
            horizontalPadding = 14.dp,
            topPadding = 8.dp,
            bottomPadding = 8.dp,
            headerHeight = 44.dp,
            addButtonSize = 46.dp,
            contentSpacing = 6.dp,
            rowHeight = 25.dp,
            featuredRowHeight = 25.dp,
            rowSpacing = 3.dp,
            rowFontSize = 12.sp,
            visibleRowCapacity = 3,
        )

        TaskWidgetLayout.TALL -> TaskWidgetMetrics(
            horizontalPadding = 14.dp,
            topPadding = 9.dp,
            bottomPadding = 10.dp,
            headerHeight = 46.dp,
            addButtonSize = 48.dp,
            contentSpacing = 8.dp,
            rowHeight = 26.dp,
            featuredRowHeight = 34.dp,
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
    val featuredRowHeight: Dp,
    val rowSpacing: Dp,
    val rowFontSize: TextUnit,
    val visibleRowCapacity: Int,
)
