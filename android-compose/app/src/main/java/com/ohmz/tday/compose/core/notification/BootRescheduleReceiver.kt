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
import com.ohmz.tday.compose.feature.widget.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        TdayTelemetry.addBreadcrumb("reminder.reschedule", data = mapOf("source" to "boot_receiver"))
        Log.d(LOG_TAG, "Received boot/update action for reminder reschedule")

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ReminderReceiverEntryPoint::class.java,
                )
                entryPoint.taskReminderScheduler().rescheduleAll()
                // Paint the widgets from cache right away. A reboot leaves them showing their
                // initialLayout ("Loading tasks…") until something starts this process, and
                // updatePeriodMillis only promises a render every 30 minutes — so without this
                // the home screen can sit on the placeholder for a long while after a restart.
                // runCatching so a widget failure can never cost the reminder reschedule above;
                // refreshNow (not requestRefresh) because goAsync's window closes on return.
                runCatching {
                    val widgetEntryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        WidgetEntryPoint::class.java,
                    )
                    widgetEntryPoint.todayTasksWidgetRefresher().refreshNow()
                    widgetEntryPoint.floaterTasksWidgetRefresher().refreshNow()
                }.onFailure { Log.w(LOG_TAG, "Widget refresh after boot/update failed", it) }
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
    }
}
