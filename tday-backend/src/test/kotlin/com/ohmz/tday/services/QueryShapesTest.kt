package com.ohmz.tday.services

import arrow.core.Either
import com.ohmz.tday.db.TestDatabase
import com.ohmz.tday.db.enums.Priority
import com.ohmz.tday.db.tables.CompletedTodos
import com.ohmz.tday.db.tables.FloaterListShares
import com.ohmz.tday.db.tables.FloaterLists
import com.ohmz.tday.db.tables.Floaters
import com.ohmz.tday.db.tables.ListShares
import com.ohmz.tday.db.tables.Lists
import com.ohmz.tday.db.tables.TodoInstances
import com.ohmz.tday.shared.model.ImportRequest
import com.ohmz.tday.shared.model.ShareRole
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pins what the read paths return for data the aggregate / subquery rewrites touch, against a
 * database built from the production tables: per-list counts computed in SQL, role filtering done
 * in SQL, and todo-instance lookups that go through a subquery on the caller's own todos instead
 * of binding one parameter per todo. Every assertion is something the previous Kotlin-side
 * implementation also produced, so a drift here is a behaviour change, not a new rule.
 */
class QueryShapesTest {
    private val db: Database = TestDatabase.fresh()
    private val push = NoOpPushNotificationService()
    private val cache = CacheServiceImpl()
    private val realtime = RealtimeServiceImpl()
    private val shareService = ListShareServiceImpl(cache, realtime, push)
    private val publisher = RealtimePublisher(realtime, shareService, cache, push)
    private val listService: ListService = ListServiceImpl(PassthroughFieldEncryption, shareService, publisher)
    private val floaterListService: FloaterListService =
        FloaterListServiceImpl(PassthroughFieldEncryption, shareService, publisher)
    private val exportService: ExportService = ExportServiceImpl(PassthroughFieldEncryption, cache, publisher)
    private val calendarFeed: CalendarFeedService = CalendarFeedServiceImpl(PassthroughFieldEncryption)

    @BeforeEach
    fun setUp() {
        TestDatabase.insertUser(OWNER, username = "owner@tday.test")
        TestDatabase.insertUser(EDITOR, username = "editor@tday.test")
        TestDatabase.insertUser(VIEWER, username = "viewer@tday.test")
        TestDatabase.insertUser(STRANGER, username = "stranger@tday.test")
    }

    @AfterEach
    fun tearDown() {
        TestDatabase.close(db)
    }

    @Test
    fun `shared list ids can be narrowed to editor memberships for both list kinds`() = runBlocking {
        transaction(db) {
            insertList("list_a", OWNER)
            insertList("list_b", OWNER)
            share("share_1", "list_a", EDITOR, ShareRole.EDITOR)
            share("share_2", "list_b", EDITOR, ShareRole.VIEWER)
            share("share_3", "list_a", VIEWER, ShareRole.VIEWER)

            insertFloaterList("flist_a", OWNER)
            insertFloaterList("flist_b", OWNER)
            floaterShare("fshare_1", "flist_a", EDITOR, ShareRole.VIEWER)
            floaterShare("fshare_2", "flist_b", EDITOR, ShareRole.EDITOR)
        }

        val scheduledAll = shareService.sharedListIdsFor(EDITOR, ListType.SCHEDULED, editorOnly = false)
        val scheduledEditable = shareService.sharedListIdsFor(EDITOR, ListType.SCHEDULED, editorOnly = true)
        val floaterAll = shareService.sharedListIdsFor(EDITOR, ListType.FLOATER, editorOnly = false)
        val floaterEditable = shareService.sharedListIdsFor(EDITOR, ListType.FLOATER, editorOnly = true)

        assertEquals(setOf("list_a", "list_b"), scheduledAll.toSet())
        assertEquals(listOf("list_a"), scheduledEditable)
        assertEquals(setOf("flist_a", "flist_b"), floaterAll.toSet())
        assertEquals(listOf("flist_b"), floaterEditable)
        // Another member's rows never leak into this user's answer.
        assertEquals(listOf("list_a"), shareService.sharedListIdsFor(VIEWER, ListType.SCHEDULED, editorOnly = false))
        assertTrue(shareService.sharedListIdsFor(VIEWER, ListType.SCHEDULED, editorOnly = true).isEmpty())
        assertTrue(shareService.sharedListIdsFor(STRANGER, ListType.FLOATER, editorOnly = false).isEmpty())
    }

    @Test
    fun `list overview counts each lists members from the share rows`() = runBlocking {
        transaction(db) {
            insertList("list_shared", OWNER)
            insertList("list_private", OWNER)
            share("share_1", "list_shared", EDITOR, ShareRole.EDITOR)
            share("share_2", "list_shared", VIEWER, ShareRole.VIEWER)
        }

        val lists = (listService.getAll(OWNER) as Either.Right).value.associateBy { it.id }

        assertEquals(2, lists.getValue("list_shared").memberCount)
        assertTrue(lists.getValue("list_shared").isShared)
        assertEquals(0, lists.getValue("list_private").memberCount)
        assertEquals(false, lists.getValue("list_private").isShared)
    }

    @Test
    fun `floater list overview counts pending floaters and members per list`() = runBlocking {
        transaction(db) {
            insertFloaterList("flist_busy", OWNER)
            insertFloaterList("flist_done", OWNER)
            insertFloaterList("flist_empty", OWNER)
            insertFloater("f1", OWNER, "flist_busy", completed = false)
            insertFloater("f2", OWNER, "flist_busy", completed = false)
            insertFloater("f3", OWNER, "flist_busy", completed = true)
            insertFloater("f4", OWNER, "flist_done", completed = true)
            insertFloater("f5", OWNER, null, completed = false)
            floaterShare("fshare_1", "flist_busy", EDITOR, ShareRole.EDITOR)
        }

        val lists = (floaterListService.getAll(OWNER) as Either.Right).value.associateBy { it.id }

        assertEquals(2, lists.getValue("flist_busy").todoCount)
        assertEquals(0, lists.getValue("flist_done").todoCount)
        assertEquals(0, lists.getValue("flist_empty").todoCount)
        assertEquals(1, lists.getValue("flist_busy").memberCount)
        assertEquals(0, lists.getValue("flist_empty").memberCount)
    }

    @Test
    fun `export carries only the callers todos and their instances`() = runBlocking {
        transaction(db) {
            insertTodo("todo_mine", OWNER)
            insertTodo("todo_theirs", STRANGER)
            insertInstance("inst_mine", "todo_mine")
            insertInstance("inst_theirs", "todo_theirs")
        }

        val bundle = (exportService.exportAll(OWNER) as Either.Right).value

        assertEquals(listOf("todo_mine"), bundle.todos.map { it.todo.id })
        assertEquals(listOf("inst_mine"), bundle.todos.single().instances.map { it.id })

        val empty = (exportService.exportAll(VIEWER) as Either.Right).value
        assertTrue(empty.todos.isEmpty())
    }

    @Test
    fun `import sees every id kind the caller already owns and none from other users`() = runBlocking {
        transaction(db) {
            insertList("list_1", OWNER)
            insertFloaterList("flist_1", OWNER)
            insertTodo("todo_1", OWNER)
            insertInstance("inst_1", "todo_1")
            insertFloater("floater_1", OWNER, "flist_1", completed = false)
            insertCompletedTodo("done_1", "todo_1", OWNER)
            // Same-shaped rows owned by somebody else must not count as the caller's.
            insertTodo("todo_other", STRANGER)
            insertInstance("inst_other", "todo_other")
        }

        val bundle = (exportService.exportAll(OWNER) as Either.Right).value

        // Re-importing the caller's own backup into the same account: each id it holds
        // (list, floater list, todo, instance, floater, completed todo) collides and is re-minted.
        val ownResponse = exportService.import(OWNER, ImportRequest(export = bundle, dryRun = true))
        val own = assertNotNull((ownResponse as Either.Right).value.imported)
        assertEquals(6, own.remappedIds)

        // The same bundle imported by an account that owns none of it collides with nothing.
        val strangerResponse = exportService.import(VIEWER, ImportRequest(export = bundle, dryRun = true))
        assertEquals(0, (strangerResponse as Either.Right).value.imported.remappedIds)
    }

    @Test
    fun `calendar feed renders the owners overrides and nobody elses`() = runBlocking {
        transaction(db) {
            insertTodo("todo_mine", OWNER, title = "Mine")
            insertTodo("todo_theirs", STRANGER, title = "Theirs")
            insertInstance("inst_mine", "todo_mine", overriddenTitle = "Mine, moved")
            insertInstance("inst_theirs", "todo_theirs", overriddenTitle = "Theirs, moved")
        }
        val token = (calendarFeed.generate(OWNER) as Either.Right).value.token

        val ics = assertNotNull(calendarFeed.renderIcs(token))

        assertTrue("SUMMARY:Mine" in ics, ics)
        assertTrue("Mine\\, moved" in ics || "SUMMARY:Mine, moved" in ics, ics)
        assertTrue("Theirs" !in ics, "another user's task leaked into the feed")
    }

    // -- fixtures ----------------------------------------------------------------------------

    private fun Transaction.insertList(id: String, owner: String) {
        Lists.insert {
            it[Lists.id] = id
            it[Lists.name] = id
            it[Lists.userID] = owner
            it[Lists.createdAt] = NOW
            it[Lists.updatedAt] = NOW
        }
    }

    private fun Transaction.insertFloaterList(id: String, owner: String) {
        FloaterLists.insert {
            it[FloaterLists.id] = id
            it[FloaterLists.name] = id
            it[FloaterLists.userID] = owner
            it[FloaterLists.createdAt] = NOW
            it[FloaterLists.updatedAt] = NOW
        }
    }

    private fun Transaction.share(id: String, listId: String, user: String, role: ShareRole) {
        ListShares.insert {
            it[ListShares.id] = id
            it[ListShares.listID] = listId
            it[ListShares.userID] = user
            it[ListShares.role] = role.name
            it[ListShares.createdAt] = NOW
            it[ListShares.updatedAt] = NOW
        }
    }

    private fun Transaction.floaterShare(id: String, listId: String, user: String, role: ShareRole) {
        FloaterListShares.insert {
            it[FloaterListShares.id] = id
            it[FloaterListShares.listID] = listId
            it[FloaterListShares.userID] = user
            it[FloaterListShares.role] = role.name
            it[FloaterListShares.createdAt] = NOW
            it[FloaterListShares.updatedAt] = NOW
        }
    }

    private fun Transaction.insertFloater(id: String, owner: String, listId: String?, completed: Boolean) {
        Floaters.insert {
            it[Floaters.id] = id
            it[Floaters.title] = id
            it[Floaters.createdAt] = NOW
            it[Floaters.updatedAt] = NOW
            it[Floaters.userID] = owner
            it[Floaters.priority] = Priority.Low
            it[Floaters.completed] = completed
            it[Floaters.listID] = listId
        }
    }

    /** Raw SQL: `exdates` is a Postgres array and `priority` a Postgres enum, neither of which H2 binds. */
    private fun Transaction.insertTodo(id: String, owner: String, title: String = id) {
        exec(
            """
            INSERT INTO todos (id, title, "createdAt", "updatedAt", "userID", priority, due, exdates)
            VALUES ('$id', '$title', now(), now(), '$owner', 'Medium', now(), ARRAY[])
            """.trimIndent(),
        )
    }

    private fun Transaction.insertInstance(id: String, todoId: String, overriddenTitle: String? = null) {
        TodoInstances.insert {
            it[TodoInstances.id] = id
            it[TodoInstances.todoId] = todoId
            it[TodoInstances.recurId] = id
            it[TodoInstances.instanceDate] = NOW
            it[TodoInstances.overriddenTitle] = overriddenTitle
        }
    }

    private fun Transaction.insertCompletedTodo(id: String, originalTodoId: String, owner: String) {
        CompletedTodos.insert {
            it[CompletedTodos.id] = id
            it[CompletedTodos.originalTodoID] = originalTodoId
            it[CompletedTodos.title] = id
            it[CompletedTodos.priority] = Priority.Low
            it[CompletedTodos.completedAt] = NOW
            it[CompletedTodos.due] = NOW
            it[CompletedTodos.completedOnTime] = true
            it[CompletedTodos.daysToComplete] = BigDecimal.ONE
            it[CompletedTodos.userID] = owner
        }
    }

    private companion object {
        const val OWNER = "user_owner"
        const val EDITOR = "user_editor"
        const val VIEWER = "user_viewer"
        const val STRANGER = "user_stranger"
        val NOW: LocalDateTime = LocalDateTime.of(2026, 6, 1, 9, 0)
    }
}
