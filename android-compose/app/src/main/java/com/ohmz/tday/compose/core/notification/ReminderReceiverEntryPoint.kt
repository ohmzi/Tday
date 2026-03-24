package com.ohmz.tday.compose.core.notification

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderReceiverEntryPoint {
    fun reminderPreferenceStore(): ReminderPreferenceStore
    fun taskReminderScheduler(): TaskReminderScheduler
}
