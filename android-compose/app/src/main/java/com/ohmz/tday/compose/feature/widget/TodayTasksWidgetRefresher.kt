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

/**
 * Re-renders the Today widget from the offline cache. This is UNCONDITIONAL on purpose:
 * `provideGlance` always reads the current cache, so a re-render with unchanged data is an
 * invisible no-op — but skipping renders (a content-signature guard) risks a stuck, stale
 * widget if a single render is dropped, which is worse. Reliability over micro-optimization.
 */
@Singleton
class TodayTasksWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun requestRefresh() {
        scope.launch { refreshNow() }
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
            val widget = TodayTasksWidget()
            runCatching { widget.updateAll(context) }
            todayWidgetReceiverClasses.forEach { receiverClass ->
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
        todayWidgetReceiverClasses.forEach { receiverClass ->
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
        val todayWidgetReceiverClasses = listOf(
            TodayTasksWidgetSmallReceiver::class.java,
            TodayTasksWidgetReceiver::class.java,
            TodayTasksWidgetLargeReceiver::class.java,
        )
    }
}
