package com.ohmz.tday.compose.feature.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

abstract class BaseFloaterTasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FloaterTasksWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        TodayTasksWidgetPreviewPublisher.publish(context)
    }
}

class FloaterTasksWidgetSmallReceiver : BaseFloaterTasksWidgetReceiver()

class FloaterTasksWidgetReceiver : BaseFloaterTasksWidgetReceiver()

class FloaterTasksWidgetLargeReceiver : BaseFloaterTasksWidgetReceiver()
