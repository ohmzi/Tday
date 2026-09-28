package com.ohmz.tday.compose.feature.widget

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Taps on a row of a widget's task list.
 *
 * A list item cannot own a PendingIntent: the list carries ONE mutable template
 * ([rowTemplate]) and each row fills in what its tap means ([openFillIn], [completeFillIn]). The
 * template has to be an activity, because the row body opens the app and only an activity start
 * is reliably allowed from a widget tap — so it points at the invisible [WidgetTaskActionActivity],
 * which either opens the app or hands a completion to [WidgetTaskActionReceiver]. That is the same
 * shape Glance used for its lazy lists, so a check-off behaves exactly as it did.
 *
 * The fill-in carries only an action and a task id. Where to open is re-derived from the
 * instance ([openIntentFor]) rather than carried: the template is mutable, so the host could
 * substitute any extra, and an intent taken from an extra and started with this app's identity
 * would be an intent-redirection hole.
 */
internal object WidgetTaskActions {
    private const val EXTRA_ROW_ACTION = "com.ohmz.tday.compose.widget.extra.ROW_ACTION"
    private const val EXTRA_TASK_ID = "com.ohmz.tday.compose.widget.extra.TASK_ID"
    private const val ROW_ACTION_OPEN = "open"
    private const val ROW_ACTION_COMPLETE = "complete"
    private const val ROW_URI_SCHEME = "tday-widget"
    private const val ROW_URI_HOST = "row"

    /** The list's shared template. Keyed to the instance by its data URI and request code. */
    fun rowTemplate(context: Context, appWidgetId: Int): PendingIntent = PendingIntent.getActivity(
        context,
        appWidgetId,
        Intent(context, WidgetTaskActionActivity::class.java).setData(rowUri(appWidgetId)),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    fun openFillIn(): Intent = Intent().putExtra(EXTRA_ROW_ACTION, ROW_ACTION_OPEN)

    fun completeFillIn(taskId: String): Intent = Intent()
        .putExtra(EXTRA_ROW_ACTION, ROW_ACTION_COMPLETE)
        .putExtra(EXTRA_TASK_ID, taskId)

    /** Runs in [WidgetTaskActionActivity] with the template intent the host filled in. */
    fun dispatch(activity: Activity, intent: Intent) {
        val appWidgetId = appWidgetIdOf(intent.data) ?: return
        when (intent.getStringExtra(EXTRA_ROW_ACTION)) {
            ROW_ACTION_OPEN -> openIntentFor(activity, appWidgetId)?.let(activity::startActivity)
            ROW_ACTION_COMPLETE -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                activity.sendBroadcast(
                    Intent(activity, WidgetTaskActionReceiver::class.java)
                        .setData(rowUri(appWidgetId))
                        .putExtra(EXTRA_TASK_ID, taskId),
                )
            }
        }
    }

    /**
     * Completes [taskId] through the same repository path the in-app checkbox uses, then repaints
     * the tapped instance first so the row disappears before the broadcast's process window ends.
     */
    suspend fun complete(context: Context, appWidgetId: Int, taskId: String) {
        val appContext = context.applicationContext
        val feed = WidgetInstanceResolver(appContext).feedOf(appWidgetId)
        if (feed == null) {
            // An instance we cannot place (removed mid-tap, or a per-list instance with no
            // readable selection): completing the id as either kind would be a guess.
            Log.w(WIDGET_LOG_TAG, "widget[$appWidgetId]: complete skipped, instance feed unknown")
            return
        }
        val entryPoint = EntryPointAccessors.fromApplication(appContext, WidgetEntryPoint::class.java)
        val submitter = entryPoint.widgetCompleteTaskSubmitter()
        when (feed) {
            WidgetFeed.SCHEDULED -> submitter.completeTodayTask(taskId)
            WidgetFeed.FLOATER -> submitter.completeFloaterTask(taskId)
        }
        entryPoint.widgetRefresher().refreshNow(firstAppWidgetId = appWidgetId)
    }

    fun taskIdOf(intent: Intent): String? = intent.getStringExtra(EXTRA_TASK_ID)

    fun appWidgetIdOf(uri: Uri?): Int? {
        if (uri == null || uri.scheme != ROW_URI_SCHEME || uri.host != ROW_URI_HOST) return null
        return uri.lastPathSegment?.toIntOrNull()
    }

    /** Where a tap on [appWidgetId]'s body goes, from the instance's own kind and selection. */
    private fun openIntentFor(context: Context, appWidgetId: Int): Intent? =
        when (WidgetInstanceResolver(context).kindOf(appWidgetId)) {
            WidgetInstanceKind.TODAY -> TodayTasksWidget.openIntent()
            WidgetInstanceKind.FLOATER -> FloaterTasksWidget.openIntent()
            WidgetInstanceKind.LIST -> ListTasksWidget.openIntent(context, appWidgetId)
            null -> null
        }

    private fun rowUri(appWidgetId: Int): Uri = Uri.Builder()
        .scheme(ROW_URI_SCHEME)
        .authority(ROW_URI_HOST)
        .appendPath(appWidgetId.toString())
        .build()
}

/**
 * Invisible trampoline for list-row taps (see [WidgetTaskActions]). Translucent, no animation, no
 * history and its own task affinity, so starting it neither flashes a window nor pulls an existing
 * T'Day task forward when the tap only completes a task.
 */
class WidgetTaskActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { WidgetTaskActions.dispatch(this, intent) }
            .onFailure { Log.w(WIDGET_LOG_TAG, "widget row action failed", it) }
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}

/**
 * Completes a task checked off on a widget row. `goAsync` keeps the process alive through the
 * optimistic cache write and the repaint, the way Glance's action receiver did.
 */
class WidgetTaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = WidgetTaskActions.appWidgetIdOf(intent.data) ?: return
        val taskId = WidgetTaskActions.taskIdOf(intent) ?: return
        val pending = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                WidgetTaskActions.complete(appContext, appWidgetId, taskId)
            } catch (error: Exception) {
                Log.w(WIDGET_LOG_TAG, "widget[$appWidgetId]: complete failed", error)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
