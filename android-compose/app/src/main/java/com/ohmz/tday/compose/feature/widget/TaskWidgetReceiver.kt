package com.ohmz.tday.compose.feature.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/**
 * Base of every size receiver the manifest declares. Each one renders its own ids with its own
 * [kind] — the ids in an `APPWIDGET_UPDATE` broadcast are always bound to the receiver it was
 * delivered to, so no routing decision is made here.
 *
 * Every callback paints straight away through [WidgetRenderer]: `onUpdate` (placement, a reboot,
 * an app update and the 30-minute `updatePeriodMillis` tick — which is also what turns the Today
 * widget over at midnight, since each render re-reads the clock) and `onAppWidgetOptionsChanged`
 * (a resize, which can change the layout bucket).
 */
abstract class TaskWidgetReceiver : AppWidgetProvider() {
    internal abstract val kind: WidgetInstanceKind

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WidgetRenderer.publishFromBroadcast(this, context, kind, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        WidgetRenderer.publishFromBroadcast(this, context, kind, intArrayOf(appWidgetId))
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        TodayTasksWidgetPreviewPublisher.publish(context)
    }
}
