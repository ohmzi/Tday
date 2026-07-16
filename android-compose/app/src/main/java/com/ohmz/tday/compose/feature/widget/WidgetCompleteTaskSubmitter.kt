package com.ohmz.tday.compose.feature.widget

import android.content.Context
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.cache.floaterFromCache
import com.ohmz.tday.compose.core.data.cache.todoFromCache
import com.ohmz.tday.compose.core.data.todo.TodoRepository
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Completes a task straight from a widget tap (widgets v2), riding the exact
 * repository paths the in-app checkbox uses — optimistic cache write, queued
 * COMPLETE_TODO/COMPLETE_TODO_INSTANCE/COMPLETE_FLOATER mutation, sync in
 * Server Mode, widget refresh. Mirrors [WidgetCreateTaskSubmitter].
 *
 * The repository call passes eagerSync=false so the Glance action isn't held
 * hostage by the network (the tap re-renders the widget the moment the optimistic
 * write lands). The queued mutation is then pushed to the backend IMMEDIATELY via
 * an expedited [WidgetSyncWorker] one-shot — without it, the completion sat in the
 * queue until the app was next opened (or the periodic sync fired), so the web app
 * and other devices didn't see widget check-offs for minutes/hours.
 */
@Singleton
class WidgetCompleteTaskSubmitter @Inject constructor(
    @ApplicationContext private val context: Context,
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
            // eagerSync=false: refresh the widget immediately, sync just below.
            todoRepository.completeTodo(todoFromCache(record), eagerSync = false)
            // Push the queued completion to the backend right now (expedited,
            // WorkManager-backed so it survives the widget action's short process
            // window). Offline: the worker retries/fails quietly and the mutation
            // replays on the next sync — same as before, minus the wait.
            WidgetSyncWorker.runOnce(context)
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
            todoRepository.completeFloater(floaterFromCache(record), eagerSync = false)
            WidgetSyncWorker.runOnce(context)
        }.onFailure { error ->
            TdayTelemetry.capture(error, operation = "widget_complete_floater.submit")
            floaterTasksWidgetRefresher.refreshNow()
        }
    }
}
