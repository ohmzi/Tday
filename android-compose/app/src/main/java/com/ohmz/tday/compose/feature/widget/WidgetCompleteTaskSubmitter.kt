package com.ohmz.tday.compose.feature.widget

import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.cache.floaterFromCache
import com.ohmz.tday.compose.core.data.cache.todoFromCache
import com.ohmz.tday.compose.core.data.todo.TodoRepository
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Completes a task straight from a widget tap (widgets v2), riding the exact
 * repository paths the in-app checkbox uses — optimistic cache write, queued
 * COMPLETE_TODO/COMPLETE_TODO_INSTANCE/COMPLETE_FLOATER mutation, sync in
 * Server Mode, widget refresh. Mirrors [WidgetCreateTaskSubmitter].
 */
@Singleton
class WidgetCompleteTaskSubmitter @Inject constructor(
    private val cacheManager: OfflineCacheManager,
    private val todoRepository: TodoRepository,
    private val todayTasksWidgetRefresher: TodayTasksWidgetRefresher,
    private val floaterTasksWidgetRefresher: FloaterTasksWidgetRefresher,
) {
    suspend fun completeTodayTask(taskId: String) = withContext(Dispatchers.Default) {
        runCatching {
            // Resolve the cache record by widget row id; the mapper carries
            // rrule/instanceDate so the repository picks the right mutation kind.
            val record = cacheManager.loadOfflineState().todos
                .firstOrNull { it.id == taskId && !it.completed }
                ?: return@withContext
            todoRepository.completeTodo(todoFromCache(record))
        }.onFailure { error ->
            TdayTelemetry.capture(error, operation = "widget_complete_task.submit")
            todayTasksWidgetRefresher.refreshNow()
        }
    }

    suspend fun completeFloaterTask(taskId: String) = withContext(Dispatchers.Default) {
        runCatching {
            val record = cacheManager.loadOfflineState().floaters
                .firstOrNull { it.id == taskId && !it.completed }
                ?: return@withContext
            todoRepository.completeFloater(floaterFromCache(record))
        }.onFailure { error ->
            TdayTelemetry.capture(error, operation = "widget_complete_floater.submit")
            floaterTasksWidgetRefresher.refreshNow()
        }
    }
}
