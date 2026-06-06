package com.ohmz.tday.compose.core.ui

import android.content.Context
import android.content.Intent
import com.ohmz.tday.compose.core.model.TodoItem
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHARE_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a", Locale.getDefault())
        .withZone(ZoneId.systemDefault())

fun shareTask(context: Context, todo: TodoItem) {
    val parts = buildList {
        add(todo.title)
        todo.description?.takeIf { it.isNotBlank() }?.let { add(it) }
        todo.due?.let { add("Due: ${SHARE_DATE_FORMATTER.format(it)}") }
        todo.priority.takeIf { it != "Low" }?.let { add("Priority: $it") }
    }
    val text = parts.joinToString("\n")
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, todo.title)
    }
    context.startActivity(Intent.createChooser(intent, "Share Task"))
}

fun shareList(context: Context, listName: String, items: List<TodoItem>) {
    val parts = buildList {
        add(listName)
        add("—".repeat(listName.length.coerceAtMost(20)))
        items.forEach { todo ->
            val bullet = if (todo.completed) "✓" else "○"
            add("$bullet ${todo.title}")
        }
        add("")
        add("${items.size} task${if (items.size != 1) "s" else ""}")
    }
    val text = parts.joinToString("\n")
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, listName)
    }
    context.startActivity(Intent.createChooser(intent, "Share List"))
}
