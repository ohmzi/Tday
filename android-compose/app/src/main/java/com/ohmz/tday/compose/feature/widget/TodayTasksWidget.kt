package com.ohmz.tday.compose.feature.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.ColorRes
import com.ohmz.tday.compose.BuildConfig
import com.ohmz.tday.compose.MainActivity
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.AppSecurityPreferenceStore
import com.ohmz.tday.compose.feature.widget.TaskWidgetListItem.Label.Style.MESSAGE
import com.ohmz.tday.compose.feature.widget.TaskWidgetListItem.Label.Style.SECTION
import com.ohmz.tday.compose.feature.widget.snapshot.TodayWidgetDay
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshot
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotRow
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotStatus
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotStore
import com.ohmz.tday.compose.feature.widget.snapshot.nextDayWithTasks
import com.ohmz.tday.compose.feature.widget.snapshot.todayAt
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * The Today accent for [isDaytime] — the ink its sun/moon watermark bakes, and the ink a todo-list
 * instance's own glyph borrows so the two watermark families read as the same mark.
 */
@ColorRes
internal fun todayWidgetAccentColor(isDaytime: Boolean): Int =
    if (isDaytime) R.color.tday_widget_today_accent else R.color.tday_widget_today_night_accent

/** Reused by `ListTasksWidget` for a todo-list instance — same due-date shape as Today. */
internal fun todayWidgetVisuals(isDaytime: Boolean): TaskWidgetVisuals {
    // Passing the accent the drawable already bakes keeps SRC_ATOP a no-op on these two: see
    // TaskWidgetWatermark for why the tint travels with the drawable at all.
    val watermark = TaskWidgetWatermark(
        drawable = if (isDaytime) {
            R.drawable.widget_empty_watermark_today
        } else {
            R.drawable.widget_empty_watermark_today_night
        },
        tint = todayWidgetAccentColor(isDaytime),
    )

    return TaskWidgetVisuals(
        addButtonBackground = R.drawable.widget_add_button_background,
        addIcon = R.drawable.widget_add_icon_today,
        emptyWatermark = watermark,
        setupWatermark = watermark,
    )
}

/**
 * What a Today widget instance shows right now. Called on every render (see [WidgetRenderer]), so
 * the lock flag, the snapshot and the clock are always read fresh — there is no long-lived session
 * holding an earlier answer.
 *
 * No Hilt, no encrypted cache on this path: both stores below are constructed directly from the
 * application context and read a small snapshot file (see `WidgetSnapshotStore`).
 */
internal object TodayTasksWidget {

    fun model(context: Context, appWidgetId: Int, nowEpochMs: Long = System.currentTimeMillis()): TaskWidgetModel {
        val appContext = context.applicationContext
        // App lock is checked FIRST, before anything reads a snapshot off disk — a locked device
        // never touches task content at all. Plain SharedPreferences, no Keystore.
        val isAppLocked = AppSecurityPreferenceStore(appContext).appLockEnabled.value
        val snapshot = if (isAppLocked) null else WidgetSnapshotStore(appContext).readToday()
        // The snapshot's day containing now — usually its own, or after midnight one of the
        // upcoming days it carries (see todayAt), so turning over needs no rebuild.
        val day = snapshot?.todayAt(nowEpochMs)
        val snapshotStale = snapshot != null && day == null
        // No snapshot (a fresh install, an upgrade that rebooted before the app was ever opened,
        // or a file that failed to decrypt), or one that has run out of days (a week offline with
        // the app never opened): rebuild it from the cache off the render path. The rewrite
        // repaints; until then this renders LOADING.
        if (!isAppLocked && (snapshot == null || snapshotStale)) {
            WidgetHydrateWorker.runOnce(appContext)
        }
        // Absence after a reboot means the widget was never rendered at all; presence with
        // snapshotNull=true past the first frame means something is stuck.
        logWidgetComposition(
            composingAs = WidgetInstanceKind.TODAY,
            appWidgetId = appWidgetId,
            providerKind = WidgetInstanceResolver(appContext).kindOf(appWidgetId),
            details = "locked=$isAppLocked snapshotNull=${snapshot == null} stale=$snapshotStale",
        )

        // The ONE day/night decision this widget makes, read once and handed to everything that
        // follows from it — the title, the empty line and the sun/moon watermark all flip together
        // at 18:00 because they share this flag rather than each re-asking the clock.
        val isDaytime = taskWidgetIsDaytime(LocalTime.now().hour)

        val content = if (isAppLocked || snapshot == null || day == null) {
            null
        } else {
            todayContent(appContext, snapshot, day, nowEpochMs, WidgetCheckOff.ids(), isDaytime)
        }

        return TaskWidgetModel(
            title = appContext.getString(
                if (isDaytime) R.string.widget_today_tasks_title else R.string.widget_tonight_tasks_title,
            ),
            state = todayContentState(isAppLocked, snapshot, nowEpochMs),
            countLabel = content?.countLabel,
            compactCountLabel = content?.compactCountLabel,
            setupTitle = appContext.getString(R.string.widget_today_tasks_setup_title),
            setupMessage = appContext.getString(R.string.widget_today_tasks_setup_message),
            emptyTitle = content?.emptyTitle ?: appContext.getString(
                if (isDaytime) R.string.widget_today_tasks_empty else R.string.widget_tonight_tasks_empty,
            ),
            lockedTitle = appContext.getString(R.string.widget_locked_title),
            lockedMessage = appContext.getString(R.string.widget_locked_message),
            loadingTitle = appContext.getString(R.string.widget_loading),
            addLabel = appContext.getString(R.string.widget_today_tasks_add),
            items = content?.items.orEmpty(),
            emptyPreview = content?.emptyPreview.orEmpty(),
            dateBlock = content?.dateBlock,
            progress = content?.progress,
            // Follows the clock, so the day/night artwork turns over with it.
            visuals = todayWidgetVisuals(isDaytime),
            openIntent = openIntent(),
            addIntent = createIntent(appWidgetId),
        )
    }

    fun openIntent(): Intent = Intent(Intent.ACTION_MAIN).apply {
        component = ComponentName(BuildConfig.APPLICATION_ID, MainActivity::class.java.name)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    /** Carries this instance's own id so the create sheet resolves THIS instance's feed. */
    private fun createIntent(appWidgetId: Int): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(WidgetCreateRoute.deepLink(WidgetCreateRoute.targetFor(WidgetFeed.SCHEDULED), appWidgetId)),
    ).apply {
        component = ComponentName(BuildConfig.APPLICATION_ID, WidgetCreateTaskActivity::class.java.name)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
}

/**
 * Renders the snapshot's day containing [nowEpochMs] (see [todayAt]). LOADING only when there is
 * no snapshot yet, or it has run out of days — never for one merely written on an earlier day,
 * which is the normal state of a widget after midnight whenever nothing has changed.
 */
internal fun todayContentState(
    isAppLocked: Boolean,
    snapshot: WidgetSnapshot?,
    nowEpochMs: Long,
): TaskWidgetContentState {
    if (isAppLocked) return TaskWidgetContentState.LOCKED
    val day = snapshot?.todayAt(nowEpochMs) ?: return TaskWidgetContentState.LOADING
    // Nothing due today but something overdue is still a list to show, not an empty day.
    if (day.status == WidgetSnapshotStatus.EMPTY && day.overdueCount > 0) return TaskWidgetContentState.TASKS
    return day.status.toContentState()
}

/** The parts of a Today model that come from its day's content rather than from its state. */
private data class TodayContent(
    val countLabel: String?,
    val compactCountLabel: String?,
    val emptyTitle: String,
    val items: List<TaskWidgetListItem>,
    val emptyPreview: List<TaskWidgetListItem>,
    val dateBlock: TaskWidgetDateBlock,
    val progress: TaskWidgetProgress?,
)

/**
 * [checkingIds] are rows mid check-off (see [WidgetCheckOff]). They still sit in the snapshot —
 * the completion is written when the beat ends — but the header already counts today's as done,
 * so the ring fills in step with the tick instead of a second later. An overdue row is not one of
 * today's tasks, so checking it off moves nothing in the header.
 */
private fun todayContent(
    context: Context,
    snapshot: WidgetSnapshot,
    day: TodayWidgetDay,
    nowEpochMs: Long,
    checkingIds: Set<String>,
    isDaytime: Boolean,
): TodayContent {
    val locale = Locale.getDefault()
    val zoneId = ZoneId.systemDefault()
    // One formatter for the whole list, not one per row: dueEpochMs is deliberately NOT
    // preformatted at write time (see WidgetSnapshot's KDoc — it depends on the read-time locale
    // and 12/24h setting), but constructing DateFormat.getTimeInstance is not free.
    val timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)
    // An overdue row's time of day says little; the day it was due says how late it is.
    val overdueDateFormatter = SimpleDateFormat(
        android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMd"),
        locale,
    )

    val checkingToday = day.rows.count { it.id in checkingIds }
    val dueToday = (day.taskCount - checkingToday).coerceAtLeast(0)
    val done = day.completedCount + checkingToday
    val total = done + dueToday
    val dueLabel = String.format(locale, context.getString(R.string.widget_today_tasks_count), dueToday)

    // Four lines, picked on the same 6..<18 predicate as the sun/moon watermark: the evening
    // should never read "today" while the moon is drawn behind it.
    val emptyTitle = context.getString(
        when {
            done > 0 && isDaytime -> R.string.todos_all_done_today
            done > 0 -> R.string.widget_tonight_all_done
            isDaytime -> R.string.widget_today_tasks_empty
            else -> R.string.widget_tonight_tasks_empty
        },
    )
    val dayStart = Instant.ofEpochMilli(day.dayStartEpochMs ?: nowEpochMs).atZone(zoneId).toLocalDate()
    val emptyPreview = if (day.taskCount == 0 && day.overdueCount == 0) {
        nextDayPreview(context, snapshot, nowEpochMs, dayStart, emptyTitle, timeFormatter)
    } else {
        emptyList()
    }

    return TodayContent(
        countLabel = when {
            done > 0 -> String.format(locale, context.getString(R.string.widget_today_progress), done, total)
            dueToday > 0 -> dueLabel
            else -> null
        },
        compactCountLabel = dueLabel.takeIf { dueToday > 0 },
        emptyTitle = emptyTitle,
        items = todayItems(context, day, checkingIds, timeFormatter, overdueDateFormatter),
        emptyPreview = emptyPreview,
        dateBlock = TaskWidgetDateBlock(
            weekday = DateTimeFormatter.ofPattern("EEE", locale).format(dayStart),
            day = DateTimeFormatter.ofPattern("d", locale).format(dayStart),
            // The in-app screen's own title strings, so the widget's stacked header and the screen
            // it opens never disagree about what the current block of the day is called.
            title = context.getString(
                if (isDaytime) R.string.todos_title_today else R.string.todos_section_tonight,
            ),
        ),
        progress = TaskWidgetProgress(done = done, total = total).takeIf { total > 0 },
    )
}

/** Today's rows, then an Overdue section when anything is overdue. */
private fun todayItems(
    context: Context,
    day: TodayWidgetDay,
    checkingIds: Set<String>,
    timeFormatter: DateFormat,
    overdueDateFormatter: DateFormat,
): List<TaskWidgetListItem> = buildList {
    day.rows.forEach { row ->
        add(
            TaskWidgetListItem.Task(
                row.toWidgetRow(
                    trailingText = row.dueEpochMs?.let { dueTimeText(timeFormatter, it) },
                    checking = row.id in checkingIds,
                ),
            ),
        )
    }
    if (day.overdueRows.isNotEmpty()) {
        add(sectionLabel(OVERDUE_LABEL_SLOT, context.getString(R.string.todos_title_overdue), day.overdueCount))
        day.overdueRows.forEach { row ->
            add(
                TaskWidgetListItem.Task(
                    row.toWidgetRow(
                        trailingText = row.dueEpochMs?.let { dueTimeText(overdueDateFormatter, it) },
                        overdue = true,
                        checking = row.id in checkingIds,
                    ),
                ),
            )
        }
    }
}

/**
 * An empty day's [emptyTitle] followed by the next day with tasks, or nothing when no later day
 * in the snapshot has any.
 */
private fun nextDayPreview(
    context: Context,
    snapshot: WidgetSnapshot,
    nowEpochMs: Long,
    dayStart: LocalDate,
    emptyTitle: String,
    timeFormatter: DateFormat,
): List<TaskWidgetListItem> {
    val next = snapshot.nextDayWithTasks(nowEpochMs) ?: return emptyList()
    val locale = Locale.getDefault()
    val nextDate = Instant.ofEpochMilli(next.dayStartEpochMs).atZone(ZoneId.systemDefault()).toLocalDate()
    val dayName = if (nextDate == dayStart.plusDays(1)) {
        context.getString(R.string.todos_section_tomorrow)
    } else {
        DateTimeFormatter.ofPattern(
            android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEMMMd"),
            locale,
        ).format(nextDate)
    }
    val nextDue = String.format(locale, context.getString(R.string.widget_today_tasks_count), next.taskCount)
    return buildList {
        add(TaskWidgetListItem.Label(TaskWidgetListItem.labelKey(EMPTY_MESSAGE_SLOT), emptyTitle, MESSAGE))
        add(TaskWidgetListItem.Label(TaskWidgetListItem.labelKey(PREVIEW_LABEL_SLOT), "$dayName · $nextDue", SECTION))
        next.rows.forEach { row ->
            add(
                TaskWidgetListItem.Task(
                    row.toWidgetRow(
                        trailingText = row.dueEpochMs?.let { dueTimeText(timeFormatter, it) },
                        preview = true,
                    ),
                ),
            )
        }
    }
}

private fun sectionLabel(slot: Int, name: String, count: Int) = TaskWidgetListItem.Label(
    key = TaskWidgetListItem.labelKey(slot),
    text = String.format(Locale.getDefault(), "%s · %d", name, count),
    style = SECTION,
)

private fun WidgetSnapshotRow.toWidgetRow(
    trailingText: String?,
    overdue: Boolean = false,
    checking: Boolean = false,
    preview: Boolean = false,
) = TaskWidgetRow(
    key = key,
    id = id,
    title = title,
    priority = priorityRing.toPriorityValue(),
    trailingText = trailingText,
    description = description,
    overdueTrailing = overdue,
    checking = checking,
    preview = preview,
)

private const val OVERDUE_LABEL_SLOT = 0
private const val EMPTY_MESSAGE_SLOT = 1
private const val PREVIEW_LABEL_SLOT = 2

internal fun dueTimeText(formatter: DateFormat, epochMs: Long): String =
    formatter.format(Date.from(Instant.ofEpochMilli(epochMs)))
