package com.ohmz.tday.compose.core.ui

import android.content.Context
import android.content.Intent
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.text.flattenNotesToPlainText
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val SHARE_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())

// Title + flattened notes as plain text: what the swipe-to-copy clipboard action
// puts on the clipboard, read the same on every platform (see iOS's
// ShareSheet.taskShareText, web's buildTaskShareText).
//
// The due date/time and the priority flag are deliberately left out. A copy is
// text to paste somewhere else, and those are scheduling details rather than
// what the task says.
internal fun taskCopyText(title: String, description: String?): String {
    val parts = buildList {
        add(title)
        flattenNotesToPlainText(description).takeIf { it.isNotBlank() }?.let { add(it) }
    }
    return parts.joinToString("\n")
}

fun taskCopyText(todo: TodoItem): String = taskCopyText(todo.title, todo.description)

fun taskCopyText(item: CompletedItem): String = taskCopyText(item.title, item.description)

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
