package com.ohmz.tday.compose.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotKind

abstract class BaseFloaterTasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FloaterTasksWidget()

    /** See [BaseTodayTasksWidgetReceiver.onUpdate] and [WidgetFastPaint] for the full rationale. */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WidgetFastPaint.publish(context, glanceAppWidget, WidgetSnapshotKind.FLOATER, appWidgetIds)
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        TodayTasksWidgetPreviewPublisher.publish(context)
    }
}

class FloaterTasksWidgetSmallReceiver : BaseFloaterTasksWidgetReceiver()

class FloaterTasksWidgetReceiver : BaseFloaterTasksWidgetReceiver()

class FloaterTasksWidgetLargeReceiver : BaseFloaterTasksWidgetReceiver()
