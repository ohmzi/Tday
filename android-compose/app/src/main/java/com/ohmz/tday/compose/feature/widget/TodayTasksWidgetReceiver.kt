package com.ohmz.tday.compose.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotKind

abstract class BaseTodayTasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTasksWidget()

    /**
     * Paints from the broadcast BEFORE handing over to Glance — see [WidgetFastPaint] for why this
     * is worth ~2.4-3.0s after a reboot. `super.onUpdate` is still called, unchanged, so the
     * managed session (and everything that depends on it) behaves exactly as before; this only
     * gets real content on screen sooner.
     *
     * The fast paint is deliberately synchronous and completes before `super.onUpdate`: this
     * receiver must not call `goAsync()` itself (the super implementation already does, and
     * `goAsync` cannot be called twice in one dispatch).
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WidgetFastPaint.publish(context, glanceAppWidget, WidgetSnapshotKind.TODAY, appWidgetIds)
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        TodayTasksWidgetPreviewPublisher.publish(context)
    }
}

class TodayTasksWidgetSmallReceiver : BaseTodayTasksWidgetReceiver()

class TodayTasksWidgetReceiver : BaseTodayTasksWidgetReceiver()

class TodayTasksWidgetLargeReceiver : BaseTodayTasksWidgetReceiver()
