package com.ohmz.tday.compose

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ohmz.tday.compose.core.calendar.CalendarEntryPoint
import com.ohmz.tday.compose.core.notification.DayAheadPreferenceStore
import com.ohmz.tday.compose.core.notification.DayAheadScheduling
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ohmz.tday.compose.core.notification.BootRescheduleReceiver
import com.ohmz.tday.compose.core.notification.ReminderRescheduleWorker
import com.ohmz.tday.compose.core.notification.TaskReminderReceiver
import com.ohmz.tday.compose.core.observability.TelemetryBootstrap
import com.ohmz.tday.compose.feature.widget.TodayTasksWidgetPreviewPublisher
import com.ohmz.tday.compose.feature.widget.WidgetEntryPoint
import com.ohmz.tday.compose.feature.widget.WidgetSyncWorker
import com.ohmz.tday.compose.feature.widget.didNightModeFlip
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import android.content.res.Configuration as SystemConfiguration

@HiltAndroidApp
class TdayApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var dayAheadPreferenceStore: DayAheadPreferenceStore
    // Resolved lazily through CalendarEntryPoint, like widgetRefresher below, so Application.onCreate
    // does not construct the whole data layer (TodoRepository -> Retrofit/OkHttp/cookie + config
    // stores, the SQLCipher-backed cache, ...) in a widget-only or alarm/boot-receiver process that
    // never starts the calendar mirror. It is first used in runDeferredStartup, which only
    // MainActivity calls.
    private val calendarSyncManager by lazy {
        EntryPointAccessors
            .fromApplication(applicationContext, CalendarEntryPoint::class.java)
            .calendarSyncManager()
    }
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
        // First, and before Hilt builds the graph in super.onCreate(): this is the one place the
        // crash reporter starts, and it runs in every process of the app (a widget refresh, a boot
        // or alarm receiver, a worker) rather than only when MainActivity draws a frame. It is
        // cheap for everyone who has not opted in: one preferences read and, for them, a purge of
        // whatever an older build left on disk. Only a device that said yes pays for starting Sentry.
        TelemetryBootstrap.shared(this).start()
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

        createNotificationChannels()
        enqueuePeriodicRescheduleWorker()
        WidgetSyncWorker.schedule(this)
        // Widgets no longer render through Glance, but an install upgraded from a Glance build can
        // still hold its SessionWorker requests; that class is gone, so each would only fail when
        // it ran. WorkManager tags every request with its worker's class name.
        WorkManager.getInstance(this).cancelAllWorkByTag(LEGACY_GLANCE_SESSION_WORKER)
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

private const val LEGACY_GLANCE_SESSION_WORKER = "androidx.glance.session.SessionWorker"
