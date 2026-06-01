package com.ohmz.tday.compose.feature.car

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.ui.theme.TdayFloaterAccent
import com.ohmz.tday.compose.ui.theme.TdayTodayBlue

enum class CarTaskMode {
    TODAY,
    FLOATER,
}

val CarTaskMode.todoListMode: TodoListMode
    get() = when (this) {
        CarTaskMode.TODAY -> TodoListMode.TODAY
        CarTaskMode.FLOATER -> TodoListMode.FLOATER
    }

val CarTaskMode.telemetryName: String
    get() = when (this) {
        CarTaskMode.TODAY -> "today"
        CarTaskMode.FLOATER -> "floater"
    }

val CarTaskMode.plusColor: Color
    get() = when (this) {
        CarTaskMode.TODAY -> TdayTodayBlue
        CarTaskMode.FLOATER -> TdayFloaterAccent
    }

@get:StringRes
val CarTaskMode.titleRes: Int
    get() = when (this) {
        CarTaskMode.TODAY -> R.string.car_surface_title_today
        CarTaskMode.FLOATER -> R.string.car_surface_title_floater
    }

@get:StringRes
val CarTaskMode.emptyTitleRes: Int
    get() = when (this) {
        CarTaskMode.TODAY -> R.string.widget_today_tasks_empty
        CarTaskMode.FLOATER -> R.string.widget_floater_tasks_empty
    }

@get:StringRes
val CarTaskMode.voicePromptRes: Int
    get() = when (this) {
        CarTaskMode.TODAY -> R.string.car_voice_prompt_today
        CarTaskMode.FLOATER -> R.string.car_voice_prompt_floater
    }

data class CarTaskItem(
    val id: String,
    val title: String,
    val priority: String,
    val dueLabel: String?,
    val mode: CarTaskMode,
    val source: TodoItem,
)

data class CarTaskSurfaceState(
    val mode: CarTaskMode = CarTaskMode.TODAY,
    val items: List<CarTaskItem> = emptyList(),
    val loading: Boolean = false,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean
        get() = !loading && items.isEmpty()
}

fun buildCarTaskSurfaceState(
    mode: CarTaskMode,
    todos: List<TodoItem>,
    dueLabelFor: (TodoItem) -> String? = { null },
    loading: Boolean = false,
    errorMessage: String? = null,
): CarTaskSurfaceState {
    return CarTaskSurfaceState(
        mode = mode,
        items = todos.filterNot { it.completed }
            .map { todo ->
                CarTaskItem(
                    id = "${mode.telemetryName}:${todo.id}:${todo.instanceDateEpochMillis ?: "series"}",
                    title = todo.title,
                    priority = todo.priority,
                    dueLabel = dueLabelFor(todo),
                    mode = mode,
                    source = todo,
                )
            },
        loading = loading,
        errorMessage = errorMessage,
    )
}
