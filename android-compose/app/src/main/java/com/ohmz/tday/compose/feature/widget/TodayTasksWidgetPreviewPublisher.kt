package com.ohmz.tday.compose.feature.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import com.ohmz.tday.compose.R

object TodayTasksWidgetPreviewPublisher {
    fun publish(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return

        val appContext = context.applicationContext
        val packageName = appContext.packageName
        val manager = AppWidgetManager.getInstance(appContext)

        previewDefinitions.forEach { definition ->
            runCatching {
                manager.setWidgetPreview(
                    ComponentName(appContext, definition.receiver),
                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                    RemoteViews(packageName, definition.layout),
                )
            }
        }
    }

    private val previewDefinitions = listOf(
        WidgetPreviewDefinition(
            receiver = TodayTasksWidgetSmallReceiver::class.java,
            layout = R.layout.widget_today_tasks_preview_small,
        ),
        WidgetPreviewDefinition(
            receiver = TodayTasksWidgetReceiver::class.java,
            layout = R.layout.widget_today_tasks_preview,
        ),
        WidgetPreviewDefinition(
            receiver = TodayTasksWidgetLargeReceiver::class.java,
            layout = R.layout.widget_today_tasks_preview_large,
        ),
        WidgetPreviewDefinition(
            receiver = FloaterTasksWidgetSmallReceiver::class.java,
            layout = R.layout.widget_floater_tasks_preview_small,
        ),
        WidgetPreviewDefinition(
            receiver = FloaterTasksWidgetReceiver::class.java,
            layout = R.layout.widget_floater_tasks_preview,
        ),
        WidgetPreviewDefinition(
            receiver = FloaterTasksWidgetLargeReceiver::class.java,
            layout = R.layout.widget_floater_tasks_preview_large,
        ),
    )

    private data class WidgetPreviewDefinition(
        val receiver: Class<*>,
        @LayoutRes val layout: Int,
    )
}
