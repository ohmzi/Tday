package com.ohmz.tday.compose.feature.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one place a widget instance is turned into RemoteViews and handed to the host.
 *
 * Every render reads the current snapshot (see the per-kind `model` functions) and publishes it
 * straight through [AppWidgetManager.updateAppWidget] — no Glance session, no WorkManager job.
 * The Glance pipeline this replaced published only from a WorkManager `SessionWorker`, which cost
 * seconds after a reboot and depended on the job actually being scheduled; this runs inside the
 * caller's own broadcast or coroutine.
 *
 * Renders are SERIALISED process-wide by [renderMutex]: the refresher, an `onUpdate` broadcast
 * and a resize can all ask at once, and two overlapping renders of one id could otherwise publish
 * out of order — the older snapshot landing last. One at a time, each reading the snapshot as it
 * is when its turn comes, means the last publish is always the newest data.
 */
internal object WidgetRenderer {
    private val renderMutex = Mutex()

    /** For broadcast-driven renders, which outlive `onReceive` through `goAsync`. */
    private val broadcastScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Renders and publishes each step in order. Returns how many were published. */
    suspend fun publish(context: Context, steps: List<WidgetRenderStep>): Int = renderMutex.withLock {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        var published = 0
        for ((appWidgetId, kind) in steps) {
            runCatching { manager.updateAppWidget(appWidgetId, render(appContext, kind, appWidgetId)) }
                .onSuccess { published++ }
                .onFailure {
                    Log.e(WIDGET_LOG_TAG, "${kind.name.lowercase()}: render($appWidgetId) failed", it)
                }
        }
        published
    }

    /**
     * Renders [appWidgetIds] — all bound to one receiver of [kind] — from inside an
     * `AppWidgetProvider` callback. Uses `goAsync` so the snapshot decrypt stays off the main
     * thread while the broadcast keeps the process alive until the publish lands.
     */
    fun publishFromBroadcast(
        receiver: BroadcastReceiver,
        context: Context,
        kind: WidgetInstanceKind,
        appWidgetIds: IntArray,
    ) {
        if (appWidgetIds.isEmpty()) return
        val pending = receiver.goAsync()
        val appContext = context.applicationContext
        broadcastScope.launch {
            try {
                publish(appContext, appWidgetIds.map { WidgetRenderStep(it, kind) })
            } finally {
                pending.finish()
            }
        }
    }

    private fun render(context: Context, kind: WidgetInstanceKind, appWidgetId: Int): RemoteViews {
        val model = when (kind) {
            WidgetInstanceKind.TODAY -> TodayTasksWidget.model(context, appWidgetId)
            WidgetInstanceKind.FLOATER -> FloaterTasksWidget.model(context, appWidgetId)
            WidgetInstanceKind.LIST -> ListTasksWidget.model(context, appWidgetId)
        }
        return TaskWidgetRemoteViews.build(context, appWidgetId, model, taskWidgetSizes(context, appWidgetId))
    }
}
