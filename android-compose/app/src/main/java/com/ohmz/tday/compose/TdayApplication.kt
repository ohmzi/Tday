package com.ohmz.tday.compose

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ohmz.tday.compose.core.notification.BootRescheduleReceiver
import com.ohmz.tday.compose.core.notification.ReminderRescheduleWorker
import com.ohmz.tday.compose.core.notification.TaskReminderReceiver
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class TdayApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        enqueuePeriodicRescheduleWorker()
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
