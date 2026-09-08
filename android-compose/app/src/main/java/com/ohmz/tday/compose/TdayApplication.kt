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
import com.ohmz.tday.compose.feature.widget.WidgetEntryPoint
import com.ohmz.tday.compose.feature.widget.WidgetSyncWorker
import com.ohmz.tday.compose.feature.widget.didNightModeFlip
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import android.content.res.Configuration as SystemConfiguration

@HiltAndroidApp
class TdayApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var dayAheadPreferenceStore: DayAheadPreferenceStore
    @Inject lateinit var calendarSyncManager: CalendarSyncManager
    private val deferredStartupRan = AtomicBoolean(false)

    // Resolved lazily through WidgetEntryPoint rather than an `@Inject lateinit` field, matching
    // every other widget call site (MainActivity, SettingsScreen, BootRescheduleReceiver,
    // CompleteTaskAction) — see WidgetEntryPoint's own KDoc for why this app keeps that one
    // pattern rather than injecting singletons ad hoc.
    private val widgetRefresher by lazy {
        EntryPointAccessors
            .fromApplication(applicationContext, WidgetEntryPoint::class.java)
            .widgetRefresher()
    }

    /**
     * The system `uiMode` this process last observed, seeded from [onCreate]. Compared on every
     * [onConfigurationChanged] so a widget repaint fires only for an actual day/night flip, not
     * for every orientation/keyboard/locale delta this callback also carries.
     */
    private var lastUiMode: Int = SystemConfiguration.UI_MODE_NIGHT_UNDEFINED

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        lastUiMode = resources.configuration.uiMode
    }

    /**
     * The only theme-change hook in this app (see docs/WIDGET_SYNC.md). `Application` is notified
     * of a configuration change in ANY live process — including a widget-only process that has
     * never opened `MainActivity`, which is the actual repro for "toggled dark mode from Quick
     * Settings while just looking at the home-screen widget". `MainActivity.onStart()`'s existing
     * belt-and-braces refresh only fires once the user opens the app, which never happens in that
     * scenario.
     *
     * This still cannot repaint a widget whose process the system has already killed in the
     * background — nothing can raise a dead process on a config change, which is exactly why
     * `ACTION_CONFIGURATION_CHANGED` is documented as undeliverable to a manifest-declared
     * receiver in the first place. That gap is covered the same way every other drift already is:
     * the 30-minute `WidgetSyncWorker` fallback and the next app open.
     *
     * Parameter is fully-qualified via the `SystemConfiguration` import alias: this file already
     * imports `androidx.work.Configuration` for `Configuration.Provider`, so an unqualified
     * `Configuration` here would resolve to the wrong class.
     */
    override fun onConfigurationChanged(newConfig: SystemConfiguration) {
        super.onConfigurationChanged(newConfig)
        val newUiMode = newConfig.uiMode
        if (didNightModeFlip(lastUiMode, newUiMode)) {
            widgetRefresher.requestRefresh()
        }
        lastUiMode = newUiMode
    }

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
