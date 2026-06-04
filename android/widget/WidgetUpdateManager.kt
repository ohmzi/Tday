package com.ohmz.tday.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central manager responsible for triggering widget refreshes.
 *
 * Inject this wherever tasks are mutated (ViewModels, use-cases, repositories)
 * and call the appropriate method. All calls are fire-and-forget; failures are
 * swallowed so a widget hiccup never blocks the UI path.
 *
 * Usage:
 *   widgetUpdateManager.scheduleImmediateUpdate()   // after add/edit/delete
 *   widgetUpdateManager.scheduleImmediateUpdate()   // on app background
 */
@Singleton
class WidgetUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Dedicated scope so widget work outlives any individual ViewModel.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Push a full widget refresh right now. Safe to call from any thread. */
    fun scheduleImmediateUpdate() {
        scope.launch {
            runCatching { refreshAll() }
                .onFailure { /* never crash the caller */ }
        }
    }

    /** Refresh every placed instance of both Today and Floater widgets. */
    private suspend fun refreshAll() {
        val manager = GlanceAppWidgetManager(context)

        // Today (scheduled-task) widget
        manager.getGlanceIds(TodayGlanceWidget::class.java).forEach { id ->
            TodayGlanceWidget().update(context, id)
        }

        // Floater (anytime-task) widget
        manager.getGlanceIds(FloaterGlanceWidget::class.java).forEach { id ->
            FloaterGlanceWidget().update(context, id)
        }
    }

    /**
     * Stamp a "last updated" epoch millis into widget state so the widget can
     * show a freshness indicator (e.g. "Updated just now").
     */
    suspend fun stampLastUpdated(timestampMs: Long = System.currentTimeMillis()) {
        val manager = GlanceAppWidgetManager(context)
        val allIds =
            manager.getGlanceIds(TodayGlanceWidget::class.java) +
            manager.getGlanceIds(FloaterGlanceWidget::class.java)

        allIds.forEach { id ->
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[WidgetStateKeys.LAST_UPDATED_MS] = timestampMs
                }
            }
        }
    }
}
