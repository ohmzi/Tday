package com.ohmz.tday.compose.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.ohmz.tday.compose.core.data.AppSecurityPreferenceStore
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotKind
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotStore
import com.ohmz.tday.compose.feature.widget.snapshot.wasWrittenBeforeLocalDay
import java.time.ZoneId

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
        hydrateIfFromEarlierDay(context)
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    /**
     * `updatePeriodMillis` delivers this broadcast every 30 minutes whether or not the device is
     * online, which makes it the one clock the widget gets for free. Nothing else rebuilds the
     * Today snapshot at midnight — only a cache write with UI changes does, and offline there are
     * none — so check here, where it does not depend on Glance recomposing a live session. The
     * composition's own check (see `TodayTasksWidget`) covers a fresh session.
     */
    private fun hydrateIfFromEarlierDay(context: Context) {
        runCatching {
            val appContext = context.applicationContext
            // Same order as provideGlance: a locked device never reads or rebuilds task content.
            if (AppSecurityPreferenceStore(appContext).appLockEnabled.value) return
            val lastWritten = WidgetSnapshotStore(appContext).lastWrittenEpochMs(WidgetSnapshotKind.TODAY)
                ?: return
            if (wasWrittenBeforeLocalDay(lastWritten, System.currentTimeMillis(), ZoneId.systemDefault())) {
                WidgetHydrateWorker.runOnce(appContext)
            }
        }.onFailure {
            Log.w(WIDGET_LOG_TAG, "today: day-rollover check skipped", it)
            TdayTelemetry.capture(it, operation = "widget_today.day_rollover_check")
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        TodayTasksWidgetPreviewPublisher.publish(context)
    }
}

class TodayTasksWidgetSmallReceiver : BaseTodayTasksWidgetReceiver()

class TodayTasksWidgetReceiver : BaseTodayTasksWidgetReceiver()

class TodayTasksWidgetLargeReceiver : BaseTodayTasksWidgetReceiver()
