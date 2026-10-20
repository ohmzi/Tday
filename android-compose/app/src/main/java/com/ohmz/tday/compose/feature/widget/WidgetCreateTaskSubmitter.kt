package com.ohmz.tday.compose.feature.widget

import com.ohmz.tday.compose.core.data.todo.TodoRepository
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.notification.TaskReminderScheduler
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates the task typed into the widget's own create sheet, then repaints.
 *
 * The repaint is deliberately NOT routed by which of the two methods below was called. It used to
 * be — `submitTodayTask` awaited the Today refresher and `submitFloaterTask` the Floater one — and
 * since the create target itself was guessed from a deep-link parameter that defaulted to "today",
 * an add could repaint a different widget than the one the user tapped, while the tapped instance
 * was left to a fire-and-forget request in a process that is backgrounded the moment the sheet
 * closes. [WidgetRefresher.refreshNow] repaints every placed instance with its own kind instead,
 * and [appWidgetId] only says which one to paint FIRST.
 */
@Singleton
class WidgetCreateTaskSubmitter @Inject constructor(
    private val todoRepository: TodoRepository,
    private val reminderScheduler: TaskReminderScheduler,
    private val widgetRefresher: WidgetRefresher,
) {
    /**
     * Lives as long as the process, the way [WidgetRefresher]'s does, and NOT as long as the sheet
     * the task was typed into.
     *
     * The create sheet's confirm plays an exit now, and the widget create surface is the window that
     * exit is drawn in. Run the submit in a composition-scoped coroutine and the window has to
     * outlive it or the write is cancelled — so the activity stood, fully transparent and swallowing
     * every touch, until this returned. Which is not the write: the local cache write and the widget
     * repaint are the first thing [TodoRepository.createTodo] does, and it ends in an AWAITED forced
     * sync that can sit on a connection probe. The user watched the card slide away and then found
     * their own home screen dead under their finger, with the new task already painted into the
     * widget behind the glass.
     *
     * Detached, the window leaves on the card and the sync finishes behind it. Nothing is lost by
     * that: the local write and its replayable CREATE_TODO mutation are committed within a frame or
     * two of the tap, long before the 260 ms exit has finished, and a sync cut short by the process
     * going away is the case pending-mutation replay exists for.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Submit without holding the caller's coroutine — see [scope].
     *
     * Fire-and-forget is safe to the last statement here: both submitters swallow and report their
     * own failures, so there is no outcome for a caller that has already gone to act on.
     */
    internal fun submitDetached(
        createTarget: WidgetCreateTarget,
        payload: CreateTaskPayload,
        appWidgetId: Int? = null,
    ) {
        scope.launch {
            when (createTarget) {
                WidgetCreateTarget.TODAY -> submitTodayTask(payload, appWidgetId)
                WidgetCreateTarget.FLOATER -> submitFloaterTask(payload, appWidgetId)
            }
        }
    }

    suspend fun submitTodayTask(
        payload: CreateTaskPayload,
        appWidgetId: Int? = null,
    ) = withContext(Dispatchers.Default) {
        if (payload.title.isBlank()) return@withContext

        runCatching {
            todoRepository.createTodo(payload)
        }.onSuccess {
            reminderScheduler.rescheduleAll()
            widgetRefresher.refreshNow(firstAppWidgetId = appWidgetId)
        }.onFailure { error ->
            TdayTelemetry.capture(
                error,
                operation = "widget_create_task.submit",
            )
            widgetRefresher.refreshNow(firstAppWidgetId = appWidgetId)
        }
    }

    suspend fun submitFloaterTask(
        payload: CreateTaskPayload,
        appWidgetId: Int? = null,
    ) = withContext(Dispatchers.Default) {
        if (payload.title.isBlank()) return@withContext

        runCatching {
            todoRepository.createFloater(payload)
        }.onSuccess {
            widgetRefresher.refreshNow(firstAppWidgetId = appWidgetId)
        }.onFailure { error ->
            TdayTelemetry.capture(
                error,
                operation = "widget_create_floater.submit",
            )
            widgetRefresher.refreshNow(firstAppWidgetId = appWidgetId)
        }
    }
}
