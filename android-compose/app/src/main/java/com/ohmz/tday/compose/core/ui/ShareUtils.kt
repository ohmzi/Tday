package com.ohmz.tday.compose.core.ui

import android.content.Context
import android.content.Intent
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.text.flattenNotesToPlainText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val SHARE_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())

// Title + flattened notes + due + priority as plain text — the single source
// of truth for "what a task looks like as text", shared by the share sheet
// and the swipe-to-copy clipboard action so both read the same on every
// platform (see iOS's ShareSheet.taskShareText, web's buildTaskShareText).
private fun taskCopyText(
    context: Context,
    title: String,
    description: String?,
    priority: String,
    due: Instant?,
): String {
    val parts = buildList {
        add(title)
        flattenNotesToPlainText(description).takeIf { it.isNotBlank() }?.let { add(it) }
        due?.let {
            add(context.getString(R.string.share_due_label, SHARE_DATE_FORMATTER.format(it)))
        }
        priority.takeIf { it != "Low" }?.let {
            add(context.getString(R.string.share_priority_label, it))
        }
    }
    return parts.joinToString("\n")
}

fun taskCopyText(context: Context, todo: TodoItem): String =
    taskCopyText(context, todo.title, todo.description, todo.priority, todo.due)

fun taskCopyText(context: Context, item: CompletedItem): String =
    taskCopyText(context, item.title, item.description, item.priority, item.due)

fun shareTask(context: Context, todo: TodoItem) {
    val text = taskCopyText(context, todo)
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
            flattenNotesToPlainText(todo.description).takeIf { it.isNotBlank() }?.let { note ->
                note.split("\n").forEach { line -> add("   $line") }
            }
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
