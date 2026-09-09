package com.ohmz.tday.compose.feature.todos

import com.ohmz.tday.compose.core.model.TodoItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [nonEarlierSectionsEmpty] generalizes Today's `uiState.items.isEmpty()`
 * empty-state gate to Scheduled/Priority/All/List. `TodoRepository
 * .buildTodosForMode` mixes those four modes' overdue tasks straight into
 * `items` -- unlike Today, which fetches them separately into
 * `TodoListUiState.earlierItems` -- so the raw item count alone cannot
 * answer "is there anything left besides Earlier" for them the way it
 * always could for Today. This function answers that instead, off the
 * sections [buildTimelineSections] already built, so it can never disagree
 * with what a viewer sees sitting under the Earlier header.
 *
 * Exercised here against bare [TodoSection] lists rather than through
 * `buildTimelineSections` itself -- [TodoTimelineSectionsTest] already pins
 * that each mode's sections come out shaped correctly; these tests pin only
 * the one thing this function decides on top of that: which section keys
 * count toward "empty".
 */
class NonEarlierSectionsEmptyTest {

    private fun fakeTodo(id: String = "t1"): TodoItem = TodoItem(
        id = id,
        canonicalId = id,
        title = "task-$id",
        description = null,
        priority = "Low",
        due = null,
        rrule = null,
        instanceDate = null,
        pinned = false,
        completed = false,
        listId = null,
        updatedAt = null,
    )

    private fun section(key: String, hasItems: Boolean): TodoSection = TodoSection(
        key = key,
        title = key,
        items = if (hasItems) listOf(fakeTodo()) else emptyList(),
    )

    @Test
    fun `no sections at all counts as empty`() {
        assertTrue(nonEarlierSectionsEmpty(emptyList()))
    }

    @Test
    fun `an Earlier section holding tasks does not count on its own`() {
        assertTrue(
            nonEarlierSectionsEmpty(listOf(section(EARLIER_SECTION_KEY, hasItems = true))),
        )
    }

    @Test
    fun `a non-Earlier section holding tasks means the scope is not empty`() {
        assertFalse(
            nonEarlierSectionsEmpty(
                listOf(
                    section(EARLIER_SECTION_KEY, hasItems = true),
                    section("day-2026-09-10", hasItems = true),
                ),
            ),
        )
    }

    @Test
    fun `an empty non-Earlier section -- a drag drop target -- does not break the empty read`() {
        // buildTimelineSections keeps an otherwise-empty date bucket on
        // screen while a task is being dragged, purely so there is
        // somewhere to drop it -- that placeholder must never itself count
        // as "an active task", or an in-flight drag would flip the
        // illustration off underneath the user's thumb.
        assertTrue(
            nonEarlierSectionsEmpty(
                listOf(
                    section(EARLIER_SECTION_KEY, hasItems = true),
                    section("day-2026-09-10", hasItems = false),
                ),
            ),
        )
    }

    @Test
    fun `multiple non-Earlier sections all empty still counts as empty`() {
        assertTrue(
            nonEarlierSectionsEmpty(
                listOf(
                    section(EARLIER_SECTION_KEY, hasItems = true),
                    section("day-2026-09-10", hasItems = false),
                    section("rest-2026-09", hasItems = false),
                ),
            ),
        )
    }
}
