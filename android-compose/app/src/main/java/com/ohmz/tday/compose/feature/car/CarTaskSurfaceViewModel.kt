package com.ohmz.tday.compose.feature.car

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.core.data.todo.TodoRepository
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class CarTaskSurfaceViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
) : ViewModel() {
    private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val zoneId: ZoneId
        get() = ZoneId.systemDefault()

    private val _uiState = MutableStateFlow(
        buildCarTaskSurfaceState(
            mode = CarTaskMode.TODAY,
            todos = emptyList(),
            loading = true,
        ),
    )
    val uiState: StateFlow<CarTaskSurfaceState> = _uiState.asStateFlow()

    init {
        TdayTelemetry.addBreadcrumb(
            operation = "car_surface.open",
            data = mapOf("platform" to "android", "mode" to CarTaskMode.TODAY.telemetryName),
        )
        refresh()
    }

    fun selectMode(mode: CarTaskMode) {
        if (_uiState.value.mode == mode) return
        TdayTelemetry.addBreadcrumb(
            operation = "car_surface.switch_mode",
            data = mapOf("platform" to "android", "mode" to mode.telemetryName),
        )
        _uiState.update {
            buildCarTaskSurfaceState(
                mode = mode,
                todos = emptyList(),
                loading = true,
            )
        }
        refresh()
    }

    fun refresh() {
        val mode = _uiState.value.mode
        viewModelScope.launch {
            val result = runCatching {
                val todos = todoRepository.fetchTodosSnapshot(mode.todoListMode)
                buildCarTaskSurfaceState(
                    mode = mode,
                    todos = todos,
                    dueLabelFor = ::dueLabelFor,
                )
            }
            if (_uiState.value.mode != mode) return@launch
            _uiState.value = result.getOrElse { error ->
                TdayTelemetry.capture(
                    error = error,
                    operation = "car_surface.load",
                    data = mapOf("platform" to "android", "mode" to mode.telemetryName),
                )
                buildCarTaskSurfaceState(
                    mode = mode,
                    todos = emptyList(),
                    errorMessage = error::class.java.simpleName,
                )
            }
        }
    }

    fun createFromVoice(title: String) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) {
            TdayTelemetry.addBreadcrumb(
                operation = "car_task.voice_create",
                data = mapOf(
                    "platform" to "android",
                    "mode" to _uiState.value.mode.telemetryName,
                    "result" to "blank",
                ),
            )
            return
        }

        val mode = _uiState.value.mode
        viewModelScope.launch {
            val result = runCatching {
                val payload = CreateTaskPayload(
                    title = trimmedTitle,
                    due = if (mode == CarTaskMode.TODAY) {
                        ZonedDateTime.now(zoneId).plusHours(1).toInstant()
                    } else {
                        null
                    },
                )
                if (mode == CarTaskMode.FLOATER) {
                    todoRepository.createFloater(payload)
                } else {
                    todoRepository.createTodo(payload)
                }
            }
            TdayTelemetry.addBreadcrumb(
                operation = "car_task.voice_create",
                data = mapOf(
                    "platform" to "android",
                    "mode" to mode.telemetryName,
                    "result" to if (result.isSuccess) "success" else "failure",
                ),
            )
            result.exceptionOrNull()?.let { error ->
                TdayTelemetry.capture(
                    error = error,
                    operation = "car_task.voice_create",
                    data = mapOf("platform" to "android", "mode" to mode.telemetryName),
                )
            }
            refresh()
        }
    }

    fun complete(item: CarTaskItem) {
        viewModelScope.launch {
            val result = runCatching {
                if (item.mode == CarTaskMode.FLOATER) {
                    todoRepository.completeFloater(item.source)
                } else {
                    todoRepository.completeTodo(item.source)
                }
            }
            TdayTelemetry.addBreadcrumb(
                operation = "car_task.complete",
                data = mapOf(
                    "platform" to "android",
                    "mode" to item.mode.telemetryName,
                    "result" to if (result.isSuccess) "success" else "failure",
                ),
            )
            result.exceptionOrNull()?.let { error ->
                TdayTelemetry.capture(
                    error = error,
                    operation = "car_task.complete",
                    data = mapOf("platform" to "android", "mode" to item.mode.telemetryName),
                )
            }
            refresh()
        }
    }

    private fun dueLabelFor(todo: com.ohmz.tday.compose.core.model.TodoItem): String? {
        val due = todo.due ?: return null
        return timeFormatter.format(due.atZone(zoneId))
    }
}
