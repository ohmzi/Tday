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
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ReminderReceiverEntryPoint::class.java,
                )
                entryPoint.taskReminderScheduler().rescheduleAll()
                // Two-phase widget refresh after a reboot, the way the stock system widgets
                // behave: show the PREVIOUSLY LOADED data immediately, then catch up to the
                // server.
                //
                // Phase one — paint from the offline cache (no network), so the home screen is
                // useful straight away. The static initialLayout ("Loading tasks…") is
                // unavoidable as the very first frame, because Android does not persist a
                // widget's RemoteViews across a reboot and the launcher inflates that XML until
                // some provider renders. This turns it into a blink rather than a wait for the
                // next updatePeriodMillis tick, which the system schedules a full period out
                // from boot.
                //
                // runCatching so a widget failure can never cost the reminder reschedule above;
                // refreshNow (not requestRefresh) because goAsync's window closes on return.
                runCatching {
                    val widgetEntryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        WidgetEntryPoint::class.java,
                    )
                    Log.i(WIDGET_LOG_TAG, "boot: cache render starting (action=$action)")
                    val startedAtMs = System.currentTimeMillis()
                    // Bounded because this runs inside goAsync's window, which the system
                    // closes if we take too long — a slow render must not hold the broadcast.
                    withTimeout(WIDGET_CACHE_RENDER_TIMEOUT_MS) {
                        widgetEntryPoint.todayTasksWidgetRefresher().refreshNow()
                        widgetEntryPoint.floaterTasksWidgetRefresher().refreshNow()
                    }
                    Log.i(
                        WIDGET_LOG_TAG,
                        "boot: cache render finished in ${System.currentTimeMillis() - startedAtMs}ms",
                    )
                }.onFailure { Log.w(WIDGET_LOG_TAG, "boot: cache render FAILED", it) }

                // Phase two — fresh data from the server. Handed to WorkManager rather than run
                // here: it owns the retry/backoff, survives this receiver, and can wait for
                // connectivity that may not exist yet seconds after a boot. Its cache
                // write-through is what repaints the widgets with whatever the server returned.
                runCatching {
                    WidgetSyncWorker.runOnce(context.applicationContext)
                    Log.i(WIDGET_LOG_TAG, "boot: server sync enqueued")
                }.onFailure { Log.w(WIDGET_LOG_TAG, "boot: server sync FAILED to enqueue", it) }
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
         * Ceiling for the cache-only widget render. Comfortably longer than a Room read plus a
         * Glance composition, while staying well inside the window the system allows a
         * goAsync() broadcast — boot is congested, so this is a backstop, not a budget.
         */
        private const val WIDGET_CACHE_RENDER_TIMEOUT_MS = 10_000L
    }
}
