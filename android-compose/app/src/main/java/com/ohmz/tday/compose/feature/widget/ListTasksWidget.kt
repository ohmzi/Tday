package com.ohmz.tday.compose.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ohmz.tday.compose.BuildConfig
import com.ohmz.tday.compose.MainActivity
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.AppSecurityPreferenceStore
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetListType
import com.ohmz.tday.compose.feature.widget.snapshot.isPseudoList
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshot
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotStore
import com.ohmz.tday.compose.ui.theme.tdayListIconResForList
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * The List widget: one placed instance scoped to ONE list the user picks for it, rather than fixed
 * to "today's due tasks" or "the floater list" the way [TodayTasksWidget]/[FloaterTasksWidget] are.
 *
 * It is placed UNCONFIGURED: a picture and "Choose a list", and a tap (anywhere, "+" included)
 * opens [WidgetListPickerActivity] for this `appWidgetId`. The same activity is the launcher's
 * reconfigure target, so the list can be changed later from the widget's long-press menu. Once a
 * list is picked, content shape follows that list's TYPE (see [WidgetListType]'s KDoc) and "+"
 * creates straight into it — that list, that task type, and the list's own default priority.
 *
 * The selection, the lock flag and the snapshot are all read on every render, so a pick or a
 * reconfigure shows up on the next repaint without any per-instance state to invalidate.
 */
internal object ListTasksWidget {

    fun model(context: Context, appWidgetId: Int): TaskWidgetModel {
        val appContext = context.applicationContext
        val isAppLocked = AppSecurityPreferenceStore(appContext).appLockEnabled.value
        val selection = WidgetListSelectionStore(appContext).selectionFor(appWidgetId)
        val snapshot = if (isAppLocked || selection == null) {
            null
        } else {
            WidgetSnapshotStore(appContext).readList(appWidgetId)
        }
        // Same fallback Today/Floater use for "no snapshot yet" — a configured-but-unseeded
        // instance shouldn't sit in LOADING forever waiting for an unrelated cache write. The
        // normal case never reaches this: WidgetListPickerActivity writes the first snapshot
        // right after the user picks a list.
        if (!isAppLocked && selection != null && snapshot == null) WidgetHydrateWorker.runOnce(appContext)
        logWidgetComposition(
            composingAs = WidgetInstanceKind.LIST,
            appWidgetId = appWidgetId,
            providerKind = WidgetInstanceResolver(appContext).kindOf(appWidgetId),
            details = "locked=$isAppLocked listType=${selection?.listType?.name ?: "none"} " +
                "snapshotNull=${snapshot == null}",
        )

        // A list deleted since it was picked is the same state as no list at all — the widget asks
        // for another — with a message that says why.
        val listMissing = snapshot?.listMissing == true
        val liveSelection = selection?.takeUnless { listMissing }
        val visuals = if (isAppLocked) {
            // A chosen glyph hints at which of the user's lists this is, so the locked widget may
            // not draw it — the same rule the title follows below. It does not fall back to the
            // TYPE's mark either: that would put the Today sun on a widget that is not the Today
            // feed. Locked, there is nothing to assert, so nothing is drawn.
            listWidgetVisualsFor(liveSelection?.listType)
                .copy(emptyWatermark = null, setupWatermark = null)
        } else {
            listWidgetVisualsFor(
                listType = liveSelection?.listType,
                // The cache's current key first, so changing a list's icon reaches the watermark
                // on the next write; the pick-time key covers the window before that write lands,
                // and an upgrade from a build that stored neither falls back to the name (see
                // `listWidgetVisualsFor`).
                listIconKey = snapshot?.listIconKey ?: liveSelection?.listIconKey,
                listName = snapshot?.listName ?: liveSelection?.listName,
                // Same precedence as the icon: the cache's current colour wins, so recolouring a
                // list reaches its widget on the next write rather than waiting for a re-pick.
                listColorKey = snapshot?.listColorKey ?: liveSelection?.listColorKey,
            )
        }
        val title = listWidgetTitleFor(appContext, liveSelection, snapshot, isAppLocked)
        return if (selection == null || listMissing) {
            unconfiguredModel(appContext, appWidgetId, title, visuals, isAppLocked, listMissing)
        } else {
            configuredModel(appContext, appWidgetId, selection, title, visuals, isAppLocked, snapshot)
        }
    }

    /** Where a tap on this instance (or one of its rows) goes. */
    fun openIntent(context: Context, appWidgetId: Int): Intent {
        val selection = WidgetListSelectionStore(context.applicationContext).selectionFor(appWidgetId)
            ?: return pickListIntent(context, appWidgetId)
        // A pseudo instance opens the app view it stands for, not a list route.
        if (selection.listType.isPseudoList) return pseudoOpenIntent(selection.listType)
        return openListIntent(selection.listId, selection.listName, selection.listType)
    }
}

/**
 * The look of an instance whose selection could not be read — no watermark and a neutral "+",
 * rather than any kind's accent.
 *
 * This is the render half of the same rule [WidgetInstanceCatalog.feedFor] holds for routing: an
 * unresolved instance must not be presented as the scheduled widget. It used to fall in with
 * [WidgetListType.TODO] on the `null` branch below, which meant a per-list instance whose
 * selection was momentarily unreadable painted the Today sun watermark and the Today accent — the
 * scheduled widget's whole visual identity — on a widget that may well be on a floater list, then
 * changed back the moment the selection re-read. The state it is really in is "not configured
 * yet", and it now looks like that and nothing else.
 */
internal val UnconfiguredListWidgetVisuals = TaskWidgetVisuals(
    addButtonBackground = R.drawable.widget_add_button_background_neutral,
    addIcon = R.drawable.widget_add_icon_neutral,
    emptyWatermark = null,
    setupWatermark = null,
)

/**
 * A per-list instance's look: its list TYPE decides the "+" accent (what a tap creates — a
 * scheduled task or a floater), and the list ITSELF decides the watermark.
 *
 * The watermark used to come from the type alone, which meant a widget scoped to a custom
 * scheduled list painted the Today SUN — the global Today feed's mark — on a widget that is not
 * that feed, and a custom floater list painted the root Anytime leaf. The rule this now follows is
 * the one the in-app empty state already implements in `TodoListScreen.emptyStateSceneIconForMode`:
 * Today is the sun or the moon, the ROOT Anytime feed is the leaf, and a list the user named gets
 * the glyph that list shows everywhere else — resolved through the same
 * [tdayListIconResForList], so a list that was never given an `iconKey` still gets its
 * name-inferred glyph here rather than a generic fallback.
 *
 * With neither a key nor a name there is nothing to resolve a glyph FROM (a selection written
 * before the key was stored, whose snapshot has not been rebuilt yet), so the instance keeps its
 * type's mark for that render instead of asserting a default inbox glyph it was never given. The
 * next snapshot write supplies the name and it settles on the right one.
 */
internal fun listWidgetVisualsFor(
    listType: WidgetListType?,
    listIconKey: String? = null,
    listName: String? = null,
    listColorKey: String? = null,
): TaskWidgetVisuals {
    if (listType == null) return UnconfiguredListWidgetVisuals
    val isDaytime = taskWidgetIsDaytime(LocalTime.now().hour)
    val base = when (listType) {
        WidgetListType.FLOATER -> FloaterWidgetVisuals
        WidgetListType.TODO, WidgetListType.SCHEDULED, WidgetListType.OVERDUE -> todayWidgetVisuals(isDaytime)
    }
    if (listIconKey.isNullOrBlank() && listName.isNullOrBlank()) {
        // A pseudo list has no glyph key to resolve from — it wears the fixed mark for its view
        // instead, the same one its picker row shows.
        val pseudoGlyph = if (listType.isPseudoList) pseudoIconRes(listType) else null
        if (pseudoGlyph == null) return base
        val pseudoWatermark = TaskWidgetWatermark(
            drawable = pseudoGlyph,
            tint = todayWidgetAccentColor(isDaytime),
            tintArgb = null,
            alpha = TaskWidgetWatermark.WATERMARK_ALPHA,
        )
        return base.copy(emptyWatermark = pseudoWatermark, setupWatermark = pseudoWatermark)
    }
    // The list's glyph in the LIST's own colour, falling back to its type's accent only when the
    // list has no colour. The type used to own the accent outright, which is why a red floater
    // list wore the floater green: the glyph came from the list and the tint did not.
    val listAccent = widgetListAccentFor(listColorKey)
    val watermark = TaskWidgetWatermark(
        drawable = tdayListIconResForList(listIconKey, listName),
        tint = when (listType) {
            WidgetListType.FLOATER -> R.color.tday_widget_floater_accent
            WidgetListType.TODO, WidgetListType.SCHEDULED, WidgetListType.OVERDUE -> todayWidgetAccentColor(isDaytime)
        },
        tintArgb = listAccent?.light,
        // A shared Lucide glyph is white at full opacity, so the watermark weight the other
        // drawables bake has to be applied here instead.
        alpha = TaskWidgetWatermark.WATERMARK_ALPHA,
    )
    return base.copy(emptyWatermark = watermark, setupWatermark = watermark, accent = listAccent)
}

/**
 * A chosen list's NAME is more revealing than Today/Floater's fixed app-supplied title ("Job
 * search", "Therapy", …), so — unlike Today/Floater's title, which is shown regardless of state —
 * this falls back to the generic title while locked, consistent with the app-lock policy of never
 * surfacing user content on a locked device (see AppSecurityPreferenceStore's KDoc).
 */
private fun listWidgetTitleFor(
    appContext: Context,
    selection: WidgetListSelection?,
    snapshot: WidgetSnapshot?,
    isAppLocked: Boolean,
): String {
    if (isAppLocked || selection == null) return appContext.getString(R.string.widget_list_tasks_title)
    // A pseudo instance has no list name to show, and its title is a fixed app-supplied one like
    // Today's and Floater's — so it is read from resources now rather than stored at pick time,
    // and a language change reaches it without re-picking the widget.
    pseudoTitleRes(selection.listType)?.let { return appContext.getString(it) }
    // The cache's current name first, so a rename shows without picking the list again.
    return snapshot?.listName?.takeIf { it.isNotBlank() }
        ?: selection.listName.takeIf { it.isNotBlank() }
        ?: appContext.getString(R.string.widget_list_tasks_title)
}

/**
 * The app-supplied title a pseudo list's widget wears, or null for a real list (whose title is the
 * user's own name). Reuses the in-app screen titles so the widget and the screen it opens read the
 * same word.
 */
internal fun pseudoTitleRes(listType: WidgetListType): Int? = when (listType) {
    WidgetListType.SCHEDULED -> R.string.todos_title_scheduled
    WidgetListType.OVERDUE -> R.string.todos_title_overdue
    else -> null
}

/** The fixed Lucide glyph a pseudo list's picker row and widget watermark wear. */
internal fun pseudoIconRes(listType: WidgetListType): Int? = when (listType) {
    WidgetListType.SCHEDULED -> R.drawable.ic_lucide_calendar_clock
    WidgetListType.OVERDUE -> R.drawable.ic_lucide_alarm_clock
    else -> null
}

/**
 * No list yet — how every List widget is placed — or a list deleted since it was picked: the SETUP
 * content state, with the picture and kind-neutral visuals, and every tap (the "+" too, which has
 * no list to add to) opening the picker for this instance. Locked, it shows the lock and a tap
 * opens the app, which asks for the unlock, rather than listing list names.
 */
private fun unconfiguredModel(
    appContext: Context,
    appWidgetId: Int,
    title: String,
    visuals: TaskWidgetVisuals,
    isAppLocked: Boolean,
    listMissing: Boolean,
): TaskWidgetModel {
    // Locked: the app's front door and nothing more. Deep-linking to the chosen list would name
    // it to whoever picked the phone up, which is the same rule the locked title follows.
    val tapIntent = if (isAppLocked) TodayTasksWidget.launchIntent() else pickListIntent(appContext, appWidgetId)
    return TaskWidgetModel(
        title = title,
        state = if (isAppLocked) TaskWidgetContentState.LOCKED else TaskWidgetContentState.SETUP,
        countLabel = null,
        setupTitle = appContext.getString(R.string.widget_list_tasks_setup_title),
        setupMessage = appContext.getString(
            if (listMissing) R.string.widget_list_tasks_missing_message else R.string.widget_list_tasks_setup_message,
        ),
        emptyTitle = "",
        lockedTitle = appContext.getString(R.string.widget_locked_title),
        lockedMessage = appContext.getString(R.string.widget_locked_message),
        loadingTitle = appContext.getString(R.string.widget_loading),
        addLabel = appContext.getString(R.string.widget_list_tasks_setup_title),
        items = emptyList(),
        setupArt = R.drawable.widget_list_setup_art,
        visuals = visuals,
        openIntent = tapIntent,
        addIntent = tapIntent,
    )
}

private fun configuredModel(
    appContext: Context,
    appWidgetId: Int,
    selection: WidgetListSelection,
    title: String,
    visuals: TaskWidgetVisuals,
    isAppLocked: Boolean,
    snapshot: WidgetSnapshot?,
): TaskWidgetModel {
    // A list holds every open task in it, whatever day each is due, so it counts "open" and
    // empties to "nothing left in this list" for either type — not Today's "due" and "due today".
    val addRes = when (selection.listType) {
        WidgetListType.TODO, WidgetListType.SCHEDULED, WidgetListType.OVERDUE -> R.string.widget_today_tasks_add
        WidgetListType.FLOATER -> R.string.widget_floater_tasks_add
    }
    val state = listContentState(isAppLocked, snapshot)
    return TaskWidgetModel(
        title = title,
        state = state,
        countLabel = String.format(
            Locale.getDefault(),
            appContext.getString(R.string.widget_floater_tasks_count),
            snapshot?.taskCount ?: 0,
        ).takeIf { state == TaskWidgetContentState.TASKS },
        setupTitle = appContext.getString(R.string.widget_today_tasks_setup_title),
        setupMessage = appContext.getString(R.string.widget_today_tasks_setup_message),
        emptyTitle = appContext.getString(R.string.widget_list_tasks_empty),
        lockedTitle = appContext.getString(R.string.widget_locked_title),
        lockedMessage = appContext.getString(R.string.widget_locked_message),
        loadingTitle = appContext.getString(R.string.widget_loading),
        addLabel = appContext.getString(addRes),
        items = if (isAppLocked || snapshot == null) emptyList() else listRows(snapshot, selection.listType),
        visuals = visuals,
        // A pseudo list is not a list, so it opens the app screen it stands for and its "+"
        // creates a scheduled task with no list — the same task the app's own Scheduled view
        // would have you make.
        openIntent = if (selection.listType.isPseudoList) {
            pseudoOpenIntent(selection.listType)
        } else {
            openListIntent(selection.listId, selection.listName, selection.listType)
        },
        addIntent = if (selection.listType.isPseudoList) {
            createListTaskIntent(appWidgetId, listId = null, selection.listType)
        } else {
            createListTaskIntent(appWidgetId, selection.listId, selection.listType)
        },
    )
}

private fun listContentState(
    isAppLocked: Boolean,
    snapshot: WidgetSnapshot?,
): TaskWidgetContentState = when {
    isAppLocked -> TaskWidgetContentState.LOCKED
    snapshot == null -> TaskWidgetContentState.LOADING
    else -> snapshot.status.toContentState()
}

private fun listRows(snapshot: WidgetSnapshot, listType: WidgetListType): List<TaskWidgetListItem> {
    // Every type but Floater is due-date-shaped, pseudo lists included.
    val dueText = if (listType == WidgetListType.FLOATER) null else listDueText()
    val checkingIds = WidgetCheckOff.ids()
    return snapshot.rows.map { row ->
        TaskWidgetListItem.Task(
            TaskWidgetRow(
                key = row.key,
                id = row.id,
                title = row.title,
                priority = row.priorityRing.toPriorityValue(),
                trailingText = dueText?.let { format -> row.dueEpochMs?.let(format) },
                overdueTrailing = dueText != null && row.overdue,
                description = row.description,
                checking = row.id in checkingIds,
            ),
        )
    }
}

/**
 * A scheduled list runs across days, so a row due today shows its time and any other row its day
 * ("Sep 30") — a bare time on next week's task would read as today's. The formatters are built
 * once for the whole list and at read time, not baked into the snapshot: locale and the 12/24h
 * setting are read-time concerns (see TodayTasksWidget).
 */
private fun listDueText(): (Long) -> String {
    val locale = Locale.getDefault()
    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now(zoneId)
    val timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)
    val dayFormatter = SimpleDateFormat(
        android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMd"),
        locale,
    )
    return { epochMs ->
        val dueToday = Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate() == today
        dueTimeText(if (dueToday) timeFormatter else dayFormatter, epochMs)
    }
}

/**
 * Opens [WidgetListPickerActivity] for THIS instance — the same activity the launcher's own
 * reconfigure affordance opens. The data URI carries the id as well as the extra, so two placed
 * List widgets never share one `PendingIntent` (their intents would otherwise compare equal).
 */
internal fun pickListIntent(context: Context, appWidgetId: Int): Intent =
    Intent(context, WidgetListPickerActivity::class.java).apply {
        action = WidgetListPickerActivity.ACTION_PICK_LIST
        data = Uri.parse("tday-widget://pick-list/$appWidgetId")
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

/**
 * Carries this instance's own `appWidgetId`, so the create sheet resolves the feed from the
 * placement itself. The `target` parameter stays for the entry points that have no widget, but it
 * is no longer what decides this widget's behavior — a list instance whose stored selection cannot
 * be read now fails closed instead of quietly creating a scheduled task.
 */
private fun createListTaskIntent(
    appWidgetId: Int,
    listId: String?,
    listType: WidgetListType,
): Intent = Intent(
    Intent.ACTION_VIEW,
    Uri.parse(
        WidgetCreateRoute.deepLink(
            target = WidgetCreateRoute.targetFor(WidgetInstanceCatalog.feedForListType(listType)),
            appWidgetId = appWidgetId,
            listId = listId,
        ),
    ),
).apply {
    component = ComponentName(BuildConfig.APPLICATION_ID, WidgetCreateTaskActivity::class.java.name)
    flags = Intent.FLAG_ACTIVITY_NEW_TASK
}

private fun openListIntent(listId: String, listName: String, listType: WidgetListType): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(listDeepLink(listId, listName, listType))).apply {
        component = ComponentName(BuildConfig.APPLICATION_ID, MainActivity::class.java.name)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

/**
 * Opens the app screen a pseudo list stands for — `tday://todos/scheduled` or
 * `tday://todos/overdue`, both already registered on their routes. Same shape as
 * [openListIntent], minus the list coordinates there are none of.
 */
private fun pseudoOpenIntent(listType: WidgetListType): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(pseudoDeepLink(listType))).apply {
        component = ComponentName(BuildConfig.APPLICATION_ID, MainActivity::class.java.name)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

private fun pseudoDeepLink(listType: WidgetListType): String =
    if (listType == WidgetListType.OVERDUE) "tday://todos/overdue" else "tday://todos/scheduled"

private fun listDeepLink(listId: String, listName: String, listType: WidgetListType): String {
    val prefix = if (listType == WidgetListType.TODO) "tday://todos/list" else "tday://floater/list"
    return "$prefix/${Uri.encode(listId)}/${Uri.encode(listName)}"
}
