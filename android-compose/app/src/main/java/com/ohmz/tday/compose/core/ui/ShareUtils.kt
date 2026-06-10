package com.ohmz.tday.compose.core.ui

import android.content.Context
import android.content.Intent
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.model.TodoItem
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val SHARE_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())

fun shareTask(context: Context, todo: TodoItem) {
    val parts = buildList {
        add(todo.title)
        todo.description?.takeIf { it.isNotBlank() }?.let { add(it) }
        todo.due?.let {
            add(context.getString(R.string.share_due_label, SHARE_DATE_FORMATTER.format(it)))
        }
        todo.priority.takeIf { it != "Low" }?.let {
            add(context.getString(R.string.share_priority_label, it))
        }
    }
    val text = parts.joinToString("\n")
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, todo.title)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.share_task_chooser_title)),
    )
}

fun buildListShareText(context: Context, listName: String, items: List<TodoItem>): String {
    val parts = buildList {
        add(listName)
        add("—".repeat(listName.length.coerceAtMost(20)))
        items.forEach { todo ->
            val bullet = if (todo.completed) "✓" else "○"
            add("$bullet ${todo.title}")
            todo.due?.let {
                add(
                    "   " + context.getString(
                        R.string.share_due_label,
                        SHARE_DATE_FORMATTER.format(it)
                    )
                )
            }
            todo.description?.takeIf { it.isNotBlank() }?.let { add("   $it") }
        }
        add("")
        add(context.resources.getQuantityString(R.plurals.share_task_count, items.size, items.size))
    }
    return parts.joinToString("\n")
}

fun shareList(context: Context, listName: String, items: List<TodoItem>) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, buildListShareText(context, listName, items))
        putExtra(Intent.EXTRA_SUBJECT, listName)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.share_list_action)),
    )
}
