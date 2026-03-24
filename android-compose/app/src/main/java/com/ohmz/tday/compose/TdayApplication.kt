package com.ohmz.tday.compose

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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
        createNotificationChannel()
        enqueuePeriodicRescheduleWorker()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            TaskReminderReceiver.CHANNEL_ID,
            "Task Reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Reminders for upcoming task due dates"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
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
