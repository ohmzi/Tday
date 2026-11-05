package com.ohmz.tday.compose.feature.todos

import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.shared.floater.FloaterMainScreen

private fun hiddenListIds(lists: List<ListSummary>): Set<String> =
    lists.filterNot { it.showOnMainScreen }.mapTo(mutableSetOf()) { it.id }

/**
 * The rows the Floater main feed draws. Only the rows: search, the list tile counts and the empty
 * answer keep reading every task, so a hidden list's open tasks hold the "all done" scene back.
 */
internal fun floaterTaskHomeFeedItems(items: List<TodoItem>, lists: List<ListSummary>): List<TodoItem> {
    val hidden = hiddenListIds(lists)
    if (hidden.isEmpty()) return items
    return items.filter { FloaterMainScreen.isShown(it.listId, hidden) }
}

/** A search result the feed has no row for: its hidden list is the only place to take the reader. */
internal fun hiddenListForSearchResult(todo: TodoItem, lists: List<ListSummary>): ListSummary? {
    val listId = todo.listId ?: return null
    return lists.firstOrNull { it.id == listId && !it.showOnMainScreen }
}
