package com.ohmz.tday.compose.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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

        Log.d(LOG_TAG, "Received $action — rescheduling task reminders")

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ReminderReceiverEntryPoint::class.java,
                )
                entryPoint.taskReminderScheduler().rescheduleAll()
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Failed to reschedule reminders on boot", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val LOG_TAG = "BootRescheduleReceiver"
    }
}
