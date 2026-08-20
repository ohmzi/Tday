package com.ohmz.tday.compose.core.notification

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import com.ohmz.tday.compose.MainActivity
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import com.ohmz.tday.compose.feature.widget.WIDGET_LOG_TAG
import com.ohmz.tday.compose.feature.widget.WidgetEntryPoint
import com.ohmz.tday.compose.feature.widget.WidgetSyncWorker
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class BootRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        TdayTelemetry.addBreadcrumb("reminder.reschedule", data = mapOf("source" to "boot_receiver"))
        Log.d(LOG_TAG, "Received boot/update action for reminder reschedule")
        // Logged under the widget tag too: whether this receiver ran at all is the first fork in
        // diagnosing a widget that stays blank after a reboot.
        Log.i(WIDGET_LOG_TAG, "boot: receiver fired (action=$action)")

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                repaintWidgets(context)

                // KEEP, not UPDATE: a boot is not an app update, so re-enqueueing with UPDATE
                // here would reset the periodic window on every single reboot. UPDATE is still
                // used from TdayApplication.runDeferredStartup, which IS the right place to pick
                // up a changed worker definition after an app update.
                WidgetSyncWorker.schedule(context, ExistingPeriodicWorkPolicy.KEEP)
                WidgetSyncWorker.runOnce(context)

                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ReminderReceiverEntryPoint::class.java,
                )
                entryPoint.taskReminderScheduler().rescheduleAll()
                if (action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    showUpdateReadyNotification(context)
                }
            } catch (e: Exception) {
                TdayTelemetry.capture(e, "reminder.reschedule", data = mapOf("source" to "boot_receiver"))
                Log.e(LOG_TAG, "Failed to handle boot/update action", e)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Repaints both widgets after a boot or an app update.
     *
     * Also covers the `MY_PACKAGE_REPLACED` case: an install-over-install doesn't clear
     * `filesDir`, so the previous snapshot survives and this repaint renders it immediately; if it
     * doesn't survive, `provideGlance`'s own missing-snapshot check (see `TodayTasksWidget.kt`)
     * enqueues `WidgetHydrateWorker` the moment this repaint runs `provideGlance`. Nothing extra
     * is needed on this path.
     *
     * A reboot drops every widget's RemoteViews (AppWidgetService persists the provider/host
     * *bindings* but not the rendered views), so without this the widget sits on
     * `android:initialLayout` until something repaints it. Uses [TodayTasksWidgetRefresher.refreshNow]
     * rather than the fire-and-forget request so the render completes while [goAsync] still holds
     * the process alive.
     */
    private suspend fun repaintWidgets(context: Context) {
        val widgets = runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java,
            )
        }.getOrElse { e ->
            Log.e(LOG_TAG, "Unable to reach the widget refreshers after boot", e)
            return
        }

        Log.i(WIDGET_LOG_TAG, "boot: repaint starting")
        val startedAtMs = System.currentTimeMillis()
        runCatching {
            // Bounded because this runs inside goAsync's window, which the system closes if we
            // take too long — a slow render must not hold the broadcast.
            withTimeout(WIDGET_REPAINT_TIMEOUT_MS) {
                widgets.todayTasksWidgetRefresher().refreshNow()
                widgets.floaterTasksWidgetRefresher().refreshNow()
            }
        }.onFailure { Log.e(LOG_TAG, "Failed to repaint widgets after boot", it) }
        Log.i(WIDGET_LOG_TAG, "boot: repaint finished in ${System.currentTimeMillis() - startedAtMs}ms")
    }

    private fun showUpdateReadyNotification(context: Context) {
        if (!canPostNotifications(context)) return

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = Uri.parse("tday://home")
        }

        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            UPDATE_NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.update_notification_title))
            .setContentText(context.getString(R.string.update_notification_message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(UPDATE_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            TdayTelemetry.capture(
                e,
                "reminder.update_notification",
                data = mapOf("source" to "boot_receiver")
            )
            Log.w(LOG_TAG, "Unable to post update notification; permission was denied", e)
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val LOG_TAG = "BootRescheduleReceiver"
        const val UPDATE_CHANNEL_ID = "app_updates"
        const val UPDATE_NOTIFICATION_ID = 20_026

        /**
         * Ceiling for [repaintWidgets]. Comfortably longer than a Room read plus a Glance
         * composition, while staying well inside the window the system allows a goAsync()
         * broadcast — boot is congested, so this is a backstop, not a budget.
         */
        private const val WIDGET_REPAINT_TIMEOUT_MS = 10_000L
    }
}
