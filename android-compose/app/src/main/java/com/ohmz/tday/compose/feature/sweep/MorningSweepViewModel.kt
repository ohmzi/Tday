package com.ohmz.tday.compose.feature.sweep

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.todo.TodoRepository
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.core.model.movedDuePreservingTime
import com.ohmz.tday.compose.core.notification.TaskReminderScheduler
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import com.ohmz.tday.compose.core.ui.UndoableDeleteCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class MorningSweepUiState(
    val cards: List<TodoItem> = emptyList(),
    val loaded: Boolean = false,
)

/**
 * Morning Sweep: guided one-card-at-a-time triage of carried-over tasks.
 * Every decision rides the existing repository paths (offline-queued, synced
 * in Server Mode); "Sweep all to today" batches the rest behind one undoable
 * toast. Recurring occurrences are excluded — they reschedule per-instance
 * from the edit flow.
 */
@HiltViewModel
class MorningSweepViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val reminderScheduler: TaskReminderScheduler,
    private val undoableDeleteCoordinator: UndoableDeleteCoordinator,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MorningSweepUiState())
    val uiState: StateFlow<MorningSweepUiState> = _uiState.asStateFlow()

    fun load() {
        TdayTelemetry.addBreadcrumb("sweep.open")
        val cards = runCatching { todoRepository.fetchTodosSnapshot(TodoListMode.OVERDUE) }
            .getOrElse { emptyList() }
            .filter { it.rrule.isNullOrBlank() && it.instanceDate == null }
        _uiState.value = MorningSweepUiState(cards = cards, loaded = true)
    }

    fun moveToToday(todo: TodoItem) = moveTo(todo, LocalDate.now())

    fun moveToTomorrow(todo: TodoItem) = moveTo(todo, LocalDate.now().plusDays(1))

    fun moveTo(todo: TodoItem, targetDate: LocalDate) {
        val due = todo.due ?: return
        advancePast(todo)
        viewModelScope.launch {
            runCatching { todoRepository.moveTodo(todo, movedDuePreservingTime(due, targetDate)) }
            runCatching { reminderScheduler.rescheduleAll() }
        }
    }

    fun makeFloater(todo: TodoItem) {
        advancePast(todo)
        viewModelScope.launch {
            runCatching { todoRepository.demoteTodo(todo) }
        }
    }

    fun letGo(todo: TodoItem) {
        advancePast(todo)
        viewModelScope.launch {
            runCatching { todoRepository.deleteTodo(todo) }
        }
    }

    fun skip(todo: TodoItem) = advancePast(todo)

    /** One undoable toast covers the batch; nothing commits until it closes. */
    fun sweepAllToToday() {
        val swept = _uiState.value.cards
        if (swept.isEmpty()) return
        TdayTelemetry.addBreadcrumb("sweep.all", data = mapOf("count" to swept.size))
        _uiState.update { it.copy(cards = emptyList()) }
        undoableDeleteCoordinator.showUndoableComplete(
            message = appContext.getString(R.string.sweep_swept_toast, swept.size),
            onCommit = {
                val today = LocalDate.now()
                for (todo in swept) {
                    val due = todo.due ?: continue
                    runCatching { todoRepository.moveTodo(todo, movedDuePreservingTime(due, today)) }
                }
                runCatching { reminderScheduler.rescheduleAll() }
            },
            onUndo = {
                _uiState.update { it.copy(cards = swept) }
            },
        )
    }

    private fun advancePast(todo: TodoItem) {
        _uiState.update { current ->
            current.copy(cards = current.cards.filterNot { it.id == todo.id })
        }
    }
}
