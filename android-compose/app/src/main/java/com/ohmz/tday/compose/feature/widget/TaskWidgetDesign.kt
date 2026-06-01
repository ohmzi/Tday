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
    val emptyWatermark: Int,
    val setupWatermark: Int,
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
    visuals: TaskWidgetVisuals,
    openAction: Action,
    addAction: Action,
) {
    val layout = taskWidgetLayoutFor(LocalSize.current)
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
                message = if (state == TaskWidgetContentState.SETUP) setupMessage else emptyMessage,
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
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(metrics.headerHeight),
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(end = metrics.addButtonSize + 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (layout == TaskWidgetLayout.COMPACT) {
                CompactCountLockup(
                    countLabel = countLabel,
                    modifier = GlanceModifier.defaultWeight(),
                )
            } else {
                HeaderText(
                    modifier = GlanceModifier
                        .width(metrics.headerTitleWidth)
                        .height(metrics.headerHeight),
                    text = title,
                    color = TaskWidgetTextColor.PRIMARY,
                    fontSize = if (layout == TaskWidgetLayout.TALL) 17.sp else 16.sp,
                    maxLines = 1,
                )
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
    openAction: Action,
    modifier: GlanceModifier = GlanceModifier,
) {
    LazyColumn(modifier = modifier) {
        itemsIndexed(
            items = rows,
            itemId = { _, row -> row.key },
        ) { index, row ->
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                TaskWidgetRow(
                    row = row,
                    layout = layout,
                    metrics = metrics,
                    openAction = openAction,
                )
                if (index < rows.lastIndex) {
                    Spacer(modifier = GlanceModifier.height(metrics.rowSpacing))
                }
            }
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
        PriorityDot(
            priority = row.priority,
            size = 7.dp,
        )
        Spacer(modifier = GlanceModifier.width(7.dp))
        WidgetText(
            modifier = GlanceModifier.defaultWeight(),
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
private fun PriorityDot(
    priority: String,
    size: Dp,
) {
    Image(
        provider = ImageProvider(taskWidgetPriorityDotResource(priority)),
        contentDescription = null,
        modifier = GlanceModifier.size(size),
    )
}

internal fun taskWidgetPriorityDotResource(priority: String): Int {
    return when (priority.trim().lowercase(Locale.getDefault())) {
        "high", "urgent", "important" -> R.drawable.widget_priority_dot_high
        "medium" -> R.drawable.widget_priority_dot_medium
        else -> R.drawable.widget_priority_dot_low
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
            horizontalPadding = 13.dp,
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
            horizontalPadding = 14.dp,
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
            horizontalPadding = 14.dp,
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
            horizontalPadding = 14.dp,
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
