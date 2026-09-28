package com.ohmz.tday.compose.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.annotation.IdRes
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.widget.RemoteViewsCompat
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.text.flattenNotesToPlainText
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetPriorityRing
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotStatus
import com.ohmz.tday.compose.ui.priority.PRIORITY_IMPORTANT_VALUE
import com.ohmz.tday.compose.ui.priority.PRIORITY_LOWEST_VALUE
import com.ohmz.tday.compose.ui.priority.PRIORITY_NORMAL_VALUE
import com.ohmz.tday.compose.ui.priority.PRIORITY_URGENT_VALUE
import com.ohmz.tday.compose.ui.priority.isImportantPriority
import com.ohmz.tday.compose.ui.priority.isLowestPriority
import com.ohmz.tday.compose.ui.priority.isUrgentPriority

internal enum class TaskWidgetContentState {
    SETUP,
    EMPTY,
    TASKS,
    LOCKED,

    /**
     * No snapshot on disk yet — a fresh install or an upgrade rebooted before the app was ever
     * opened (see `WidgetHydrateWorker`). Pixel-matched to the XML `initialLayout` placeholders
     * (`widget_loading.xml`, `widget_today_tasks_loading.xml`, `widget_floater_tasks_loading.xml`)
     * so the handoff from that static layout to the first real render is invisible: same header, a
     * centred "Loading tasks…" line, no watermark. Never used once a snapshot decodes to
     * SETUP/EMPTY/TASKS, even an empty one — this is specifically "haven't read anything yet".
     */
    LOADING,
}

internal fun WidgetSnapshotStatus.toContentState(): TaskWidgetContentState = when (this) {
    WidgetSnapshotStatus.SETUP -> TaskWidgetContentState.SETUP
    WidgetSnapshotStatus.EMPTY -> TaskWidgetContentState.EMPTY
    WidgetSnapshotStatus.TASKS -> TaskWidgetContentState.TASKS
}

/** The ring was already bucketed at write time; map it back to a canonical priority value for
 *  [taskWidgetPriorityRingResource], which re-buckets it (a cheap, idempotent string compare). */
internal fun WidgetPriorityRing.toPriorityValue(): String = when (this) {
    WidgetPriorityRing.HIGH -> PRIORITY_URGENT_VALUE
    WidgetPriorityRing.MEDIUM -> PRIORITY_IMPORTANT_VALUE
    WidgetPriorityRing.LOW -> PRIORITY_NORMAL_VALUE
    WidgetPriorityRing.LOWEST -> PRIORITY_LOWEST_VALUE
}

internal enum class TaskWidgetLayout {
    COMPACT,
    WIDE,
    MEDIUM,
    TALL,
}

internal data class TaskWidgetVisuals(
    val addButtonBackground: Int,
    val addIcon: Int,
    // Nullable so a widget that does not yet KNOW which kind it is can decline to draw one. Every
    // watermark this app ships is a kind-specific glyph in that kind's accent (the Today sun, the
    // Floater leaf) filling most of the widget, so picking one is an assertion about the
    // instance's identity — see ListTasksWidget's UnconfiguredListWidgetVisuals. A null renders
    // the same way LOADING already does: no watermark, just the header and the message.
    val emptyWatermark: Int?,
    val setupWatermark: Int?,
    // When set, every task row uses this check ring instead of a priority-coloured one
    // (floater tasks have no priority, so they use the widget's green accent).
    val priorityRingOverride: Int? = null,
)

internal data class TaskWidgetRow(
    val key: Long,
    /** The cached-record id a tap on the row's check ring completes. */
    val id: String,
    val title: String,
    val priority: String,
    val trailingText: String? = null,
    val description: String? = null,
    /** List widget only (widgets v3): tints [trailingText] as overdue. Today/Floater never set
     *  this, so their trailing time keeps its normal secondary color unchanged. */
    val overdueTrailing: Boolean = false,
)

/**
 * Everything one widget instance shows, resolved by its kind ([TodayTasksWidget],
 * [FloaterTasksWidget], [ListTasksWidget]) and drawn by [TaskWidgetRemoteViews]. Taps are plain
 * intents here and become `PendingIntent`s only at render time, keyed to the instance.
 */
internal data class TaskWidgetModel(
    val title: String,
    val state: TaskWidgetContentState,
    val countLabel: String,
    val setupTitle: String,
    val setupMessage: String,
    val emptyTitle: String,
    val lockedTitle: String,
    val lockedMessage: String,
    val loadingTitle: String,
    /** Spoken label for the "+" button. */
    val addLabel: String,
    val rows: List<TaskWidgetRow>,
    val visuals: TaskWidgetVisuals,
    val openIntent: Intent,
    val addIntent: Intent,
)

/**
 * Builds a widget's RemoteViews directly from `layout/widget_task.xml` and
 * `layout/widget_task_list_row.xml`.
 *
 * This replaced a Glance renderer. On API 33+ Glance inserts every element through
 * `RemoteViews(packageName, layoutId, viewId)` + `addStableView` and then targets that generated
 * `viewId` for text, clicks and the list adapter. HyperOS 4's launcher re-implements RemoteViews
 * and ignores that constructor's `viewId` — the inflated view keeps its XML id (`@id/glanceView`)
 * — so on that host every tap silently missed and the task list stayed blank. Verified on-device
 * with hand-built RemoteViews: XML ids, `addView`, `RemoteCollectionItems` and size maps all
 * work there, the generated-id child does not. So every view this class touches is declared, with
 * its id, in XML, and nothing here depends on an id assigned at runtime.
 */
internal object TaskWidgetRemoteViews {

    /**
     * One RemoteViews for every size the host reported for [appWidgetId], built once per distinct
     * layout bucket. A single bucket (the common case) is a plain RemoteViews; more than one
     * becomes a size map on API 31+, or the landscape/portrait pair below it.
     */
    fun build(
        context: Context,
        appWidgetId: Int,
        model: TaskWidgetModel,
        sizes: List<DpSize>,
    ): RemoteViews {
        val byLayout = sizes.ifEmpty { listOf(FallbackWidgetSize) }
            .distinct()
            .groupBy(::taskWidgetLayoutFor)
        if (byLayout.size == 1) return buildForLayout(context, appWidgetId, model, byLayout.keys.single())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // One entry per bucket, never one RemoteViews under two keys: the size map stamps each
            // value's ideal size onto the instance itself. The key is the smallest width and height
            // the bucket was reported at, so it fits every host size that maps to it.
            val sized = byLayout.map { (layout, layoutSizes) ->
                SizeF(layoutSizes.minOf { it.width.value }, layoutSizes.minOf { it.height.value }) to
                    buildForLayout(context, appWidgetId, model, layout)
            }.toMap()
            return RemoteViews(sized)
        }
        // Below API 31 the only multi-layout form is the orientation pair. `sizes` came from
        // taskWidgetSizes, which lists landscape first and portrait second in that case.
        return RemoteViews(
            buildForLayout(context, appWidgetId, model, taskWidgetLayoutFor(sizes.first())),
            buildForLayout(context, appWidgetId, model, taskWidgetLayoutFor(sizes.last())),
        )
    }

    private fun buildForLayout(
        context: Context,
        appWidgetId: Int,
        model: TaskWidgetModel,
        layout: TaskWidgetLayout,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_task).apply {
        val state = model.state
        val compact = layout == TaskWidgetLayout.COMPACT

        setOnClickPendingIntent(android.R.id.background, activityIntent(context, appWidgetId, model.openIntent))

        val watermark = when (state) {
            TaskWidgetContentState.SETUP -> model.visuals.setupWatermark
            TaskWidgetContentState.EMPTY,
            TaskWidgetContentState.TASKS,
            TaskWidgetContentState.LOCKED -> model.visuals.emptyWatermark
            // Matches the loading XML layouts: no watermark image, just the title and a centred
            // loading line. Showing one here would flash in on the first render, then vanish the
            // moment a real snapshot lands — the opposite of the invisible handoff this is for.
            TaskWidgetContentState.LOADING -> null
        }
        if (watermark != null) {
            val watermarkId = taskWidgetWatermarkViewId(layout)
            setImageViewResource(watermarkId, watermark)
            setViewVisibility(watermarkId, View.VISIBLE)
        }

        applyHeader(context, appWidgetId, model, compact)

        if (state == TaskWidgetContentState.TASKS) {
            setViewVisibility(R.id.widget_list, View.VISIBLE)
            applyRows(context, appWidgetId, model, layout)
        } else {
            applyMessage(model, compact)
        }
    }

    private fun RemoteViews.applyHeader(
        context: Context,
        appWidgetId: Int,
        model: TaskWidgetModel,
        compact: Boolean,
    ) {
        val showCount = model.state == TaskWidgetContentState.TASKS
        if (compact) {
            // Compact shows no title — "Floater Tasks" + count + button can't fit a 2x2 width —
            // so the count leads, and only once there is something to count.
            setViewVisibility(R.id.widget_header_wide, View.GONE)
            if (showCount) {
                setTextViewText(R.id.widget_compact_count, model.countLabel)
                setViewVisibility(R.id.widget_compact_count, View.VISIBLE)
            }
        } else {
            setTextViewText(R.id.widget_title, model.title)
            if (showCount) {
                setTextViewText(R.id.widget_count, model.countLabel)
                setViewVisibility(R.id.widget_count, View.VISIBLE)
            }
        }

        setInt(R.id.widget_add, "setBackgroundResource", model.visuals.addButtonBackground)
        setImageViewResource(R.id.widget_add_icon, model.visuals.addIcon)
        setContentDescription(R.id.widget_add, model.addLabel)
        setOnClickPendingIntent(R.id.widget_add, activityIntent(context, appWidgetId, model.addIntent))
    }

    private fun RemoteViews.applyMessage(model: TaskWidgetModel, compact: Boolean) {
        val (title, message) = when (model.state) {
            TaskWidgetContentState.SETUP -> model.setupTitle to model.setupMessage
            TaskWidgetContentState.LOCKED -> model.lockedTitle to model.lockedMessage
            TaskWidgetContentState.LOADING -> model.loadingTitle to ""
            else -> model.emptyTitle to ""
        }
        setViewVisibility(R.id.widget_message, View.VISIBLE)
        if (model.state == TaskWidgetContentState.LOCKED) {
            val iconId = if (compact) R.id.widget_message_icon_compact else R.id.widget_message_icon
            setImageViewResource(iconId, R.drawable.widget_lock_icon)
            setViewVisibility(iconId, View.VISIBLE)
        }
        setTextViewText(R.id.widget_message_title, title)
        setTextViewTextSize(R.id.widget_message_title, TypedValue.COMPLEX_UNIT_SP, if (compact) 13f else 15f)
        setInt(R.id.widget_message_title, "setMaxLines", if (compact) 1 else 2)
        if (!compact && message.isNotEmpty()) {
            setTextViewText(R.id.widget_message_body, message)
            setViewVisibility(R.id.widget_message_body, View.VISIBLE)
        }
    }

    private fun RemoteViews.applyRows(
        context: Context,
        appWidgetId: Int,
        model: TaskWidgetModel,
        layout: TaskWidgetLayout,
    ) {
        val showTrailing = taskWidgetShowsTrailingText(layout)
        val items = RemoteViewsCompat.RemoteCollectionItems.Builder()
            .setHasStableIds(true)
            .setViewTypeCount(1)
        model.rows.forEachIndexed { index, row ->
            items.addItem(
                row.key,
                rowViews(context, row, model.visuals, showTrailing, isLast = index == model.rows.lastIndex),
            )
        }
        RemoteViewsCompat.setRemoteAdapter(context, this, appWidgetId, R.id.widget_list, items.build())
        // One template for the whole list; each row fills in what its tap means (see
        // WidgetTaskActions). A list item cannot carry a PendingIntent of its own.
        setPendingIntentTemplate(R.id.widget_list, WidgetTaskActions.rowTemplate(context, appWidgetId))
    }

    private fun rowViews(
        context: Context,
        row: TaskWidgetRow,
        visuals: TaskWidgetVisuals,
        showTrailing: Boolean,
        isLast: Boolean,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_task_list_row).apply {
        setImageViewResource(
            R.id.widget_row_ring,
            visuals.priorityRingOverride ?: taskWidgetPriorityRingResource(row.priority),
        )
        setTextViewText(R.id.widget_row_title, row.title)

        val trailing = row.trailingText?.takeIf { showTrailing }
        if (trailing != null) {
            setTextViewText(R.id.widget_row_time, trailing)
            setViewVisibility(R.id.widget_row_time, View.VISIBLE)
            if (row.overdueTrailing) {
                // Reuses the existing high-priority ring color as the overdue tint rather than
                // adding a new color resource — same red, already themed for day/night.
                setDayNightTextColor(context, R.id.widget_row_time, R.color.tday_widget_priority_high)
            }
        }

        val notes = flattenNotesToPlainText(row.description).takeIf { it.isNotBlank() }
        if (notes != null) {
            setTextViewText(R.id.widget_row_notes, notes)
            setViewVisibility(R.id.widget_row_notes, View.VISIBLE)
        }
        // Plain whitespace between rows, no separator line — matches the widget-picker previews.
        if (isLast) setViewVisibility(R.id.widget_row_gap, View.GONE)

        setOnClickFillInIntent(R.id.widget_row, WidgetTaskActions.openFillIn())
        setOnClickFillInIntent(R.id.widget_row_check, WidgetTaskActions.completeFillIn(row.id))
    }

    /**
     * Request code = the instance id, so two instances never share a PendingIntent even where
     * their intents compare equal (a reconfigure intent differs only by an extra).
     */
    private fun activityIntent(context: Context, appWidgetId: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

// On API 31+, RemoteViews.setColorStateList stores a color-resource REFERENCE, not a resolved
// literal, so whichever process applies this RemoteViews (the launcher/widget host, the OS, or our
// own app) re-resolves it against ITS OWN current configuration — self-correcting on a theme
// change even if this app's process was never alive to catch it. That overload does not exist
// below API 31, so a literal is baked there — minSdk is 26, so that branch is load-bearing.
private fun RemoteViews.setDayNightTextColor(
    context: Context,
    viewId: Int,
    @ColorRes colorResId: Int,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setColorStateList(viewId, "setTextColor", colorResId)
    } else {
        setTextColor(viewId, ContextCompat.getColor(context, colorResId))
    }
}

/**
 * Every size the host has told us [appWidgetId] is drawn at, in dp.
 *
 * API 31+ hosts list them in `OPTION_APPWIDGET_SIZES`. Otherwise (older platforms, and launchers
 * that never fill that list) the min/max bounds give the classic pair — landscape first, portrait
 * second, the order [TaskWidgetRemoteViews.build] relies on. A widget the host has not sized yet
 * falls back to its provider's declared minimum; `onAppWidgetOptionsChanged` re-renders once the
 * real size arrives.
 */
internal fun taskWidgetSizes(context: Context, appWidgetId: Int): List<DpSize> {
    val manager = AppWidgetManager.getInstance(context)
    val options = runCatching { manager.getAppWidgetOptions(appWidgetId) }.getOrNull() ?: Bundle.EMPTY

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val reported = reportedSizes(options)
        if (reported.isNotEmpty()) return reported
    }

    val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
    val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
    val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
    val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
    if (minWidth > 0 && minHeight > 0) {
        return listOf(
            DpSize(maxWidth.coerceAtLeast(minWidth).dp, minHeight.dp),
            DpSize(minWidth.dp, maxHeight.coerceAtLeast(minHeight).dp),
        )
    }

    val info = runCatching { manager.getAppWidgetInfo(appWidgetId) }.getOrNull() ?: return emptyList()
    val density = context.resources.displayMetrics.density
    return listOf(DpSize((info.minWidth / density).dp, (info.minHeight / density).dp))
}

@Suppress("DEPRECATION") // The typed overload is API 33; this runs from 31.
private fun reportedSizes(options: Bundle): List<DpSize> =
    options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
        .orEmpty()
        .filter { it.width > 0f && it.height > 0f }
        .map { DpSize(it.width.dp, it.height.dp) }

/** A size in the MEDIUM bucket, for the one case where no size is known at all. */
private val FallbackWidgetSize = DpSize(250.dp, 140.dp)

@IdRes
internal fun taskWidgetWatermarkViewId(layout: TaskWidgetLayout): Int = when (layout) {
    TaskWidgetLayout.COMPACT -> R.id.widget_watermark_compact
    TaskWidgetLayout.WIDE -> R.id.widget_watermark_wide
    TaskWidgetLayout.MEDIUM -> R.id.widget_watermark_medium
    TaskWidgetLayout.TALL -> R.id.widget_watermark_tall
}

internal fun taskWidgetPriorityRingResource(priority: String): Int {
    return when {
        isUrgentPriority(priority) -> R.drawable.widget_priority_ring_high
        isImportantPriority(priority) -> R.drawable.widget_priority_ring_medium
        isLowestPriority(priority) -> R.drawable.widget_priority_ring_lowest
        else -> R.drawable.widget_priority_ring_low
    }
}

internal fun taskWidgetIsDaytime(hour: Int): Boolean = hour in 6 until 18

/**
 * The layout bucket for a host size. The breakpoints are the launcher's cell grid, not our spacing
 * scale; the insets, header and row metrics each bucket draws with live in `widget_task.xml` and
 * mirror iOS's `WidgetLayoutMetrics`.
 */
internal fun taskWidgetLayoutFor(size: DpSize): TaskWidgetLayout {
    return when {
        size.height >= 208.dp -> TaskWidgetLayout.TALL
        size.height >= 140.dp -> TaskWidgetLayout.MEDIUM
        size.width >= 220.dp -> TaskWidgetLayout.WIDE
        else -> TaskWidgetLayout.COMPACT
    }
}

internal fun taskWidgetShowsTrailingText(layout: TaskWidgetLayout): Boolean =
    layout == TaskWidgetLayout.MEDIUM || layout == TaskWidgetLayout.TALL
