package com.ohmz.tday.compose.core.domain

import com.ohmz.tday.compose.core.data.todo.TodoRepository
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.notification.TaskReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateTodoUseCase @Inject constructor(
    private val todoRepository: TodoRepository,
    private val reminderScheduler: TaskReminderScheduler,
) {
    suspend operator fun invoke(payload: CreateTaskPayload) {
        todoRepository.createTodo(payload)
        withContext(Dispatchers.Default) {
            runCatching { reminderScheduler.rescheduleAll() }
        }
    }
}
