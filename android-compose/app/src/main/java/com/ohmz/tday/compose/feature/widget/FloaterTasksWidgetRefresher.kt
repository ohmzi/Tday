package com.ohmz.tday.compose.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FloaterTasksWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun requestRefresh() {
        scope.launch {
            updateWidgetInstances()
            requestSystemRefresh()
        }
    }

    suspend fun refreshNow() {
        updateWidgetInstances()
        requestSystemRefresh()
    }

    // Glance exposes no public per-id update path; AppWidgetId is the
    // documented interop for refreshing specific widget instances.
    @android.annotation.SuppressLint("RestrictedApi")
    private suspend fun updateWidgetInstances() {
        withContext(Dispatchers.Default) {
            val widget = FloaterTasksWidget()
            runCatching { widget.updateAll(context) }
            floaterWidgetReceiverClasses.forEach { receiverClass ->
                runCatching {
                    val manager = AppWidgetManager.getInstance(context)
                    val componentName = ComponentName(context, receiverClass)
                    manager.getAppWidgetIds(componentName).forEach { appWidgetId ->
                        widget.update(context, AppWidgetId(appWidgetId))
                    }
                }
            }
        }
    }

    private fun requestSystemRefresh() {
        val manager = AppWidgetManager.getInstance(context)
        floaterWidgetReceiverClasses.forEach { receiverClass ->
            val componentName = ComponentName(context, receiverClass)
            val appWidgetIds = manager.getAppWidgetIds(componentName)
            if (appWidgetIds.isEmpty()) return@forEach
            context.sendBroadcast(
                Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    component = componentName
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                },
            )
        }
    }

    private companion object {
        val floaterWidgetReceiverClasses = listOf(
            FloaterTasksWidgetSmallReceiver::class.java,
            FloaterTasksWidgetReceiver::class.java,
            FloaterTasksWidgetLargeReceiver::class.java,
        )
    }
}
