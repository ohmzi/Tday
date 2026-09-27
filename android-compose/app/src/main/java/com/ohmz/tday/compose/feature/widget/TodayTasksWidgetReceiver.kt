package com.ohmz.tday.compose.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotKind
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotSignal
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
        recomposeIfFromEarlierDay(context)
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    /**
     * `updatePeriodMillis` delivers this broadcast every 30 minutes whether or not the device is
     * online, which makes it the one clock the widget gets for free. After midnight the snapshot on
     * disk already carries today (see `WidgetSnapshot.todayAt`), but a live Glance session only
     * re-reads the day when something recomposes it, and `update()` alone is not guaranteed to. So
     * when the snapshot was written on an earlier day, bump the repaint signal the composition
     * collects: it recomposes and picks today's day. A stat and a counter — no decrypt, no cache
     * open, no WorkManager. A fresh session composes from scratch and needs none of this.
     */
    private fun recomposeIfFromEarlierDay(context: Context) {
        runCatching {
            val appContext = context.applicationContext
            val lastWritten = WidgetSnapshotStore(appContext).lastWrittenEpochMs(WidgetSnapshotKind.TODAY)
                ?: return
            if (wasWrittenBeforeLocalDay(lastWritten, System.currentTimeMillis(), ZoneId.systemDefault())) {
                WidgetSnapshotSignal.bump()
            }
        }.onFailure {
            Log.w(WIDGET_LOG_TAG, "today: day-rollover recompose skipped", it)
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
