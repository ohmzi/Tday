package com.ohmz.tday.compose.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
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
)

internal data class TaskWidgetRow(
    val key: Long,
    /** The cached-record id a tap on the row's check ring completes. */
    val id: String,
    val title: String,
    val priority: String,
    val trailingText: String? = null,
    val description: String? = null,
    /** Tints [trailingText] as overdue: per-list rows past due, and Today's Overdue section. */
    val overdueTrailing: Boolean = false,
    /** Mid check-off (see [WidgetCheckOff]): ring filled and ticked, text struck through. */
    val checking: Boolean = false,
    /** A later day's task previewed under an empty Today: muted, and a ring tap opens the app. */
    val preview: Boolean = false,
)

/** One entry of a widget's task list: a task, or a label introducing what follows it. */
internal sealed interface TaskWidgetListItem {
    val key: Long

    data class Task(val row: TaskWidgetRow) : TaskWidgetListItem {
        override val key: Long get() = row.key
    }

    /**
     * [MESSAGE] is the empty-day line that heads a preview; [SECTION] names the rows below it.
     * Keys come from [labelKey], so they stay apart from task keys (`id.hashCode()`).
     */
    data class Label(override val key: Long, val text: String, val style: Style) : TaskWidgetListItem {
        enum class Style { MESSAGE, SECTION }
    }

    companion object {
        fun labelKey(slot: Int): Long = Long.MIN_VALUE + slot
    }
}

/**
 * Today's header date ("Mon" over "28"), formatted for the day the widget is showing, and the
 * shorter [title] ("Today") that stands beside it — the date already says which day.
 */
internal data class TaskWidgetDateBlock(val weekday: String, val day: String, val title: String)

/** Today's header ring: tasks done today out of done plus still due today. */
internal data class TaskWidgetProgress(val done: Int, val total: Int)

/**
 * Everything one widget instance shows, resolved by its kind ([TodayTasksWidget],
 * [FloaterTasksWidget], [ListTasksWidget]) and drawn by [TaskWidgetRemoteViews]. Taps are plain
 * intents here and become `PendingIntent`s only at render time, keyed to the instance.
 */
internal data class TaskWidgetModel(
    val title: String,
    val state: TaskWidgetContentState,
    /** The header count beside the title; null hides it. */
    val countLabel: String?,
    /** The compact (2x2) header's count, which has no room for a progress phrase. */
    val compactCountLabel: String? = countLabel,
    val setupTitle: String,
    val setupMessage: String,
    val emptyTitle: String,
    val lockedTitle: String,
    val lockedMessage: String,
    val loadingTitle: String,
    /** Spoken label for the "+" button. */
    val addLabel: String,
    /** The list drawn in the TASKS state. */
    val items: List<TaskWidgetListItem>,
    /** Drawn instead of the centred empty message wherever there is room for it (not compact). */
    val emptyPreview: List<TaskWidgetListItem> = emptyList(),
    /** Today only: the date the header leads with. */
    val dateBlock: TaskWidgetDateBlock? = null,
    /** Today only: the header's progress ring, drawn while there is anything to count. */
    val progress: TaskWidgetProgress? = null,
    /** A picture drawn above the SETUP message — the List widget's "choose a list" art. */
    val setupArt: Int? = null,
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
     * shape (layout bucket and width class). A single shape (the common case) is a plain
     * RemoteViews; more than one becomes a size map on API 31+, or the landscape/portrait pair
     * below it.
     */
    fun build(
        context: Context,
        appWidgetId: Int,
        model: TaskWidgetModel,
        sizes: List<DpSize>,
    ): RemoteViews {
        val byShape = sizes.ifEmpty { listOf(FallbackWidgetSize) }
            .distinct()
            .groupBy(::taskWidgetShapeFor)
        if (byShape.size == 1) return buildForShape(context, appWidgetId, model, byShape.keys.single())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // One entry per shape, never one RemoteViews under two keys: the size map stamps each
            // value's ideal size onto the instance itself. The key is the smallest width and height
            // the shape was reported at, so it fits every host size that maps to it.
            val sized = byShape.map { (shape, shapeSizes) ->
                SizeF(shapeSizes.minOf { it.width.value }, shapeSizes.minOf { it.height.value }) to
                    buildForShape(context, appWidgetId, model, shape)
            }.toMap()
            return RemoteViews(sized)
        }
        // Below API 31 the only multi-layout form is the orientation pair. `sizes` came from
        // taskWidgetSizes, which lists landscape first and portrait second in that case.
        return RemoteViews(
            buildForShape(context, appWidgetId, model, taskWidgetShapeFor(sizes.first())),
            buildForShape(context, appWidgetId, model, taskWidgetShapeFor(sizes.last())),
        )
    }

    private fun buildForShape(
        context: Context,
        appWidgetId: Int,
        model: TaskWidgetModel,
        shape: TaskWidgetShape,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_task).apply {
        val state = model.state
        val layout = shape.layout
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
        // Every bucket's watermark is switched, not just this one's: after a resize the host still
        // shows the previous bucket's watermark unless this render hides it (see setVisible).
        val watermarkId = taskWidgetWatermarkViewId(layout)
        for (bucket in TaskWidgetLayout.entries) {
            val bucketWatermarkId = taskWidgetWatermarkViewId(bucket)
            setVisible(bucketWatermarkId, watermark != null && bucketWatermarkId == watermarkId)
        }
        if (watermark != null) setImageViewResource(watermarkId, watermark)

        applyHeader(context, appWidgetId, model, compact, shape.narrow)

        // Where there is room, an empty Today previews its next day with tasks rather than
        // centring one line over blank space. Compact keeps the plain message.
        val listItems = when {
            state == TaskWidgetContentState.TASKS -> model.items
            state == TaskWidgetContentState.EMPTY && !compact && model.emptyPreview.isNotEmpty() -> model.emptyPreview
            else -> null
        }
        setVisible(R.id.widget_list, listItems != null)
        setVisible(R.id.widget_message, listItems == null)
        if (listItems != null) {
            applyItems(context, appWidgetId, listItems, shape)
        } else {
            applyMessage(model, compact)
        }
    }

    private fun RemoteViews.applyHeader(
        context: Context,
        appWidgetId: Int,
        model: TaskWidgetModel,
        compact: Boolean,
        narrow: Boolean,
    ) {
        val loading = model.state == TaskWidgetContentState.LOADING
        // Today leads with its date and stacks the title over the count, where there is the width
        // for it. The loading layouts a first render hands over from have neither, so LOADING
        // keeps the plain header they match.
        val dateBlock = model.dateBlock?.takeUnless { compact || narrow || loading }
        applyHeaderText(model, compact, dateBlock)
        applyProgress(model.progress?.takeIf { dateBlock != null && it.total > 0 })

        setInt(R.id.widget_add, "setBackgroundResource", model.visuals.addButtonBackground)
        setImageViewResource(R.id.widget_add_icon, model.visuals.addIcon)
        setContentDescription(R.id.widget_add, model.addLabel)
        setOnClickPendingIntent(R.id.widget_add, activityIntent(context, appWidgetId, model.addIntent))
    }

    /** The header's title and count: stacked under [dateBlock] when there is one, else in a row. */
    private fun RemoteViews.applyHeaderText(model: TaskWidgetModel, compact: Boolean, dateBlock: TaskWidgetDateBlock?) {
        val stacked = dateBlock != null
        val count = model.countLabel
        val compactCount = model.compactCountLabel
        // Compact shows no title — "Floater Tasks" + count + button can't fit a 2x2 width — so the
        // count leads, and only once there is something to count.
        setVisible(R.id.widget_date, stacked)
        setVisible(R.id.widget_header_wide, !compact && !stacked)
        setVisible(R.id.widget_header_stacked, stacked)
        setVisible(R.id.widget_compact_count, compact && compactCount != null)
        setVisible(R.id.widget_count, !compact && !stacked && count != null)
        setVisible(R.id.widget_count_stacked, stacked && count != null)
        when {
            compact -> compactCount?.let { setTextViewText(R.id.widget_compact_count, it) }
            dateBlock != null -> {
                setTextViewText(R.id.widget_date_weekday, dateBlock.weekday)
                setTextViewText(R.id.widget_date_day, dateBlock.day)
                setTextViewText(R.id.widget_title_stacked, dateBlock.title)
                count?.let { setTextViewText(R.id.widget_count_stacked, it) }
            }
            else -> {
                setTextViewText(R.id.widget_title, model.title)
                count?.let { setTextViewText(R.id.widget_count, it) }
            }
        }
    }

    private fun RemoteViews.applyProgress(progress: TaskWidgetProgress?) {
        setVisible(R.id.widget_progress, progress != null)
        if (progress != null) setProgressBar(R.id.widget_progress, progress.total, progress.done, false)
    }

    private fun RemoteViews.applyMessage(model: TaskWidgetModel, compact: Boolean) {
        val (title, message) = when (model.state) {
            TaskWidgetContentState.SETUP -> model.setupTitle to model.setupMessage
            TaskWidgetContentState.LOCKED -> model.lockedTitle to model.lockedMessage
            TaskWidgetContentState.LOADING -> model.loadingTitle to ""
            else -> model.emptyTitle to ""
        }
        applySetupArt(model.setupArt?.takeIf { model.state == TaskWidgetContentState.SETUP }, compact)
        applyLockIcon(model.state == TaskWidgetContentState.LOCKED, compact)
        applyMessageText(title, message, compact)
    }

    /**
     * The List widget's setup picture, in its full or compact slot, or in neither. Both slots are
     * set every time: a host re-applying a render keeps whatever the last one left visible.
     */
    private fun RemoteViews.applySetupArt(art: Int?, compact: Boolean) {
        setVisible(R.id.widget_message_art, art != null && !compact)
        setVisible(R.id.widget_message_art_compact, art != null && compact)
        if (art != null) {
            setImageViewResource(if (compact) R.id.widget_message_art_compact else R.id.widget_message_art, art)
        }
    }

    private fun RemoteViews.applyLockIcon(locked: Boolean, compact: Boolean) {
        setVisible(R.id.widget_message_icon, locked && !compact)
        setVisible(R.id.widget_message_icon_compact, locked && compact)
        if (locked) {
            val iconId = if (compact) R.id.widget_message_icon_compact else R.id.widget_message_icon
            setImageViewResource(iconId, R.drawable.widget_lock_icon)
        }
    }

    private fun RemoteViews.applyMessageText(title: String, message: String, compact: Boolean) {
        setTextViewText(R.id.widget_message_title, title)
        setTextViewTextSize(R.id.widget_message_title, TypedValue.COMPLEX_UNIT_SP, if (compact) 13f else 15f)
        setInt(R.id.widget_message_title, "setMaxLines", if (compact) 1 else 2)
        val showBody = !compact && message.isNotEmpty()
        setVisible(R.id.widget_message_body, showBody)
        if (showBody) setTextViewText(R.id.widget_message_body, message)
    }

    private fun RemoteViews.applyItems(
        context: Context,
        appWidgetId: Int,
        listItems: List<TaskWidgetListItem>,
        shape: TaskWidgetShape,
    ) {
        val showTrailing = taskWidgetShowsTrailingText(shape)
        val collection = RemoteViewsCompat.RemoteCollectionItems.Builder()
            .setHasStableIds(true)
            // A task row and a label row: the two layouts the list recycles between.
            .setViewTypeCount(2)
        listItems.forEachIndexed { index, item ->
            val views = when (item) {
                // A row's gap spaces it from the next TASK; a label brings its own top space.
                is TaskWidgetListItem.Task -> rowViews(
                    context,
                    item.row,
                    showTrailing,
                    isLast = listItems.getOrNull(index + 1) !is TaskWidgetListItem.Task,
                )
                is TaskWidgetListItem.Label -> labelViews(context, item)
            }
            collection.addItem(item.key, views)
        }
        RemoteViewsCompat.setRemoteAdapter(context, this, appWidgetId, R.id.widget_list, collection.build())
        // One template for the whole list; each row fills in what its tap means (see
        // WidgetTaskActions). A list item cannot carry a PendingIntent of its own.
        setPendingIntentTemplate(R.id.widget_list, WidgetTaskActions.rowTemplate(context, appWidgetId))
    }

    private fun rowViews(
        context: Context,
        row: TaskWidgetRow,
        showTrailing: Boolean,
        isLast: Boolean,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_task_list_row).apply {
        val ring = when {
            // Floater rows included: a floater has a priority too, and its ring shows it.
            row.checking -> taskWidgetCheckedRingResource(row.priority)
            // A preview is not today's to complete; the grey ring says so.
            row.preview -> R.drawable.widget_priority_ring_lowest
            else -> taskWidgetPriorityRingResource(row.priority)
        }
        setImageViewResource(R.id.widget_row_ring, ring)

        // Rows are recycled by re-applying onto a previous row's views, so every text, color,
        // visibility and background below is set on every row, never left to the XML defaults —
        // a struck, dimmed row must not hand its look to the next task recycled into it.
        val muted = row.checking || row.preview
        setTextViewText(R.id.widget_row_title, row.title.struckThroughIf(row.checking))
        setDayNightTextColor(
            context,
            R.id.widget_row_title,
            if (muted) R.color.tday_widget_on_surface_variant else R.color.tday_widget_on_surface,
        )

        val trailing = row.trailingText?.takeIf { showTrailing }
        setVisible(R.id.widget_row_time, trailing != null)
        if (trailing != null) {
            setTextViewText(R.id.widget_row_time, trailing)
            // Reuses the existing high-priority ring color as the overdue tint rather than
            // adding a new color resource — same red, already themed for day/night.
            setDayNightTextColor(
                context,
                R.id.widget_row_time,
                if (row.overdueTrailing) R.color.tday_widget_priority_high else R.color.tday_widget_on_surface_variant,
            )
            setInt(
                R.id.widget_row_time,
                "setBackgroundResource",
                if (row.overdueTrailing) R.drawable.widget_due_chip_overdue else R.drawable.widget_due_chip,
            )
        }

        val notes = flattenNotesToPlainText(row.description).takeIf { it.isNotBlank() }
        setVisible(R.id.widget_row_notes, notes != null)
        if (notes != null) setTextViewText(R.id.widget_row_notes, notes.struckThroughIf(row.checking))
        // Plain whitespace between rows, no separator line — matches the widget-picker previews.
        setVisible(R.id.widget_row_gap, !isLast)

        setOnClickFillInIntent(R.id.widget_row, WidgetTaskActions.openFillIn())
        setOnClickFillInIntent(
            R.id.widget_row_check,
            if (row.preview) WidgetTaskActions.openFillIn() else WidgetTaskActions.completeFillIn(row.id),
        )
    }

    private fun labelViews(context: Context, label: TaskWidgetListItem.Label): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_task_list_label).apply {
            // Two styled TextViews, one shown, rather than one restyled per label: RemoteViews
            // cannot swap a font, and the message and the section name are set in different ones.
            val message = label.style == TaskWidgetListItem.Label.Style.MESSAGE
            setVisible(R.id.widget_label_message, message)
            setVisible(R.id.widget_label_section, !message)
            setTextViewText(if (message) R.id.widget_label_message else R.id.widget_label_section, label.text)
            setOnClickFillInIntent(R.id.widget_label, WidgetTaskActions.openFillIn())
        }

    private fun CharSequence.struckThroughIf(struck: Boolean): CharSequence {
        if (!struck) return this
        return SpannableString(this).apply {
            setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
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

/**
 * The only way this file changes a view's visibility: always explicitly shown or hidden, never
 * "show if needed" on top of the XML default. A host that already displays `widget_task` does not
 * inflate a new RemoteViews — it re-applies the actions onto the live view tree (AppWidgetHostView
 * on every update and resize, RemoteCollectionItemsAdapter on every recycled row). So a view an
 * earlier render made VISIBLE stays VISIBLE until a later render hides it: resizing from MEDIUM to
 * TALL drew both buckets' watermarks on top of each other. `WidgetReapplyVisibilityTest` pins it.
 */
private fun RemoteViews.setVisible(@IdRes viewId: Int, visible: Boolean) =
    setViewVisibility(viewId, if (visible) View.VISIBLE else View.GONE)

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

/** The filled, ticked ring a row shows mid check-off, in the colour of its [taskWidgetPriorityRingResource]. */
internal fun taskWidgetCheckedRingResource(priority: String): Int {
    return when {
        isUrgentPriority(priority) -> R.drawable.widget_check_ring_high
        isImportantPriority(priority) -> R.drawable.widget_check_ring_medium
        isLowestPriority(priority) -> R.drawable.widget_check_ring_lowest
        else -> R.drawable.widget_check_ring_low
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

/**
 * A layout bucket plus whether it is [narrow]: a two-column widget tall enough for MEDIUM or TALL
 * but too thin for a due-time pill beside its titles, or for the date and ring beside its header —
 * the same content iOS gives its small family.
 */
internal data class TaskWidgetShape(val layout: TaskWidgetLayout, val narrow: Boolean)

/** The WIDE bucket's own breakpoint: below it, a row has no room to spare for a pill. */
private val NarrowWidgetWidth = 220.dp

internal fun taskWidgetShapeFor(size: DpSize): TaskWidgetShape =
    TaskWidgetShape(taskWidgetLayoutFor(size), narrow = size.width < NarrowWidgetWidth)

internal fun taskWidgetShowsTrailingText(shape: TaskWidgetShape): Boolean =
    !shape.narrow && taskWidgetShowsTrailingText(shape.layout)
