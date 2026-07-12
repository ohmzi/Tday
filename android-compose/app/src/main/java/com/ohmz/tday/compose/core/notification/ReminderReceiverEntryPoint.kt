package com.ohmz.tday.compose.core.notification

import com.ohmz.tday.compose.core.data.todo.TodoRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderReceiverEntryPoint {
    fun reminderPreferenceStore(): ReminderPreferenceStore
    fun taskReminderScheduler(): TaskReminderScheduler
    fun todoRepository(): TodoRepository
}
