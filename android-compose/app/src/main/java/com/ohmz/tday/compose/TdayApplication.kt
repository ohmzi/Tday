package com.ohmz.tday.compose

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ohmz.tday.compose.core.calendar.CalendarSyncManager
import com.ohmz.tday.compose.core.notification.DayAheadPreferenceStore
import com.ohmz.tday.compose.core.notification.DayAheadScheduling
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ohmz.tday.compose.core.notification.BootRescheduleReceiver
import com.ohmz.tday.compose.core.notification.ReminderRescheduleWorker
import com.ohmz.tday.compose.core.notification.TaskReminderReceiver
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import com.ohmz.tday.compose.feature.widget.TodayTasksWidgetPreviewPublisher
import com.ohmz.tday.compose.feature.widget.WidgetSyncWorker
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@HiltAndroidApp
class TdayApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var dayAheadPreferenceStore: DayAheadPreferenceStore
    @Inject lateinit var calendarSyncManager: CalendarSyncManager
    private val deferredStartupRan = AtomicBoolean(false)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    fun runDeferredStartup() {
        if (!deferredStartupRan.compareAndSet(false, true)) return

        // Not on onCreate: onCreate also runs for a widget-only process start (the
        // APPWIDGET_UPDATE broadcast after a reboot), and neither of these is needed for that
        // path — the picker preview already refreshes from each provider's own onEnabled, and
        // the Day Ahead digest already re-arms itself after every run. Keeping them off onCreate
        // keeps a widget-only cold start from paying for a WorkManager bring-up and 6
        // setWidgetPreview binder calls it doesn't need.
        TodayTasksWidgetPreviewPublisher.publish(this)
        DayAheadScheduling.scheduleNext(this, dayAheadPreferenceStore.getOption())

        CoroutineScope(Dispatchers.Default).launch {
            SentryAndroid.init(this@TdayApplication) { options ->
                options.dsn = BuildConfig.SENTRY_DSN
                options.environment = if (BuildConfig.DEBUG) "development" else "production"
                options.release = "tday-android@${BuildConfig.VERSION_NAME}"
                options.dist = BuildConfig.VERSION_CODE.toString()
                options.isSendDefaultPii = false
                options.isEnableAutoSessionTracking = true
                options.tracesSampleRate = TdayTelemetry.traceSampleRate(
                    BuildConfig.SENTRY_TRACES_SAMPLE_RATE,
                    if (BuildConfig.DEBUG) 1.0 else 0.2,
                )
                options.setBeforeSend { event, _ ->
                    event.user?.ipAddress = null
                    event
                }
            }
        }

        createNotificationChannels()
        enqueuePeriodicRescheduleWorker()
        WidgetSyncWorker.schedule(this)
        // Opt-in and permission-gated internally, so this is a no-op until the user turns the
        // device-calendar mirror on.
        calendarSyncManager.start()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                TaskReminderReceiver.CHANNEL_ID,
                getString(R.string.notification_channel_task_reminders_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = getString(R.string.notification_channel_task_reminders_description)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                BootRescheduleReceiver.UPDATE_CHANNEL_ID,
                getString(R.string.notification_channel_app_updates_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.notification_channel_app_updates_description)
            },
        )
    }

    private fun enqueuePeriodicRescheduleWorker() {
        val request = PeriodicWorkRequestBuilder<ReminderRescheduleWorker>(
            6, TimeUnit.HOURS,
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ReminderRescheduleWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
