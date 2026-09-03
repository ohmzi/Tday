package com.ohmz.tday.services

import arrow.core.Either
import com.ohmz.tday.db.TestDatabase
import com.ohmz.tday.db.tables.FloaterLists
import com.ohmz.tday.db.tables.Lists
import com.ohmz.tday.domain.AppError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The "you've been shared a list" push, exercised against a real database so the "is this
 * genuinely a new member" branch — the one thing worth getting right here — runs the same
 * insert-vs-update path production does.
 */
class ListShareNotificationTest {
    private val db: Database = TestDatabase.fresh()
    private val push = RecordingPushNotificationService()
    private val service = ListShareServiceImpl(CacheServiceImpl(), RealtimeServiceImpl(), push)

    @BeforeEach
    fun setUp() {
        TestDatabase.insertUser(OWNER_ID, username = "owner@tday.test", name = OWNER_NAME)
        TestDatabase.insertUser(MEMBER_ID, username = MEMBER_USERNAME, name = "Frieda Member")
        runBlocking { insertList(SCHEDULED_LIST_ID, SCHEDULED_LIST_NAME, OWNER_ID) }
        runBlocking { insertFloaterList(FLOATER_LIST_ID, FLOATER_LIST_NAME, OWNER_ID) }
    }

    @AfterEach
    fun tearDown() {
        TestDatabase.close(db)
    }

    @Test
    fun `sharing a scheduled list for the first time pushes the new member`() = runBlocking {
        val result = service.addMember(OWNER_ID, SCHEDULED_LIST_ID, ListType.SCHEDULED, MEMBER_USERNAME, "VIEWER")

        assertTrue(result.isRight())
        assertEquals(1, push.sent.size)
        val sent = push.sent.single()
        assertEquals(MEMBER_ID, sent.userId)
        assertEquals(SCHEDULED_LIST_ID, sent.listId)
        assertEquals("list", sent.listType)
        assertEquals(SCHEDULED_LIST_NAME, sent.listName)
        assertTrue(sent.body.contains(SCHEDULED_LIST_NAME), "body should name the list: ${sent.body}")
        assertTrue(sent.body.contains(OWNER_NAME), "body should name the sharer: ${sent.body}")
        assertEquals(null, sent.todoId)
    }

    @Test
    fun `sharing a floater list pushes with the floaterList wire type`() = runBlocking {
        service.addMember(OWNER_ID, FLOATER_LIST_ID, ListType.FLOATER, MEMBER_USERNAME, "EDITOR")

        assertEquals(1, push.sent.size)
        assertEquals("floaterList", push.sent.single().listType)
        assertEquals(FLOATER_LIST_ID, push.sent.single().listId)
    }

    @Test
    fun `re-adding an existing member to change their role does not push again`() = runBlocking {
        service.addMember(OWNER_ID, SCHEDULED_LIST_ID, ListType.SCHEDULED, MEMBER_USERNAME, "VIEWER")
        push.sent.clear()

        val result = service.addMember(OWNER_ID, SCHEDULED_LIST_ID, ListType.SCHEDULED, MEMBER_USERNAME, "EDITOR")

        assertTrue(result.isRight())
        assertTrue(push.sent.isEmpty(), "a role change on an existing member should not re-notify")
    }

    @Test
    fun `a rejected share never reaches the push service`() = runBlocking {
        // Not the owner: requester has no access to manage members on OWNER_ID's list.
        val result = service.addMember(MEMBER_ID, SCHEDULED_LIST_ID, ListType.SCHEDULED, MEMBER_USERNAME, "VIEWER")

        assertTrue(result.isLeft())
        assertTrue(push.sent.isEmpty())
    }

    @Test
    fun `a failed push does not fail the share itself`() = runBlocking {
        val failingPush = RecordingPushNotificationService(
            result = Either.Left(AppError.BadRequest("simulated push failure")),
        )
        val serviceWithFailingPush = ListShareServiceImpl(CacheServiceImpl(), RealtimeServiceImpl(), failingPush)

        val result = serviceWithFailingPush.addMember(OWNER_ID, SCHEDULED_LIST_ID, ListType.SCHEDULED, MEMBER_USERNAME, "VIEWER")

        assertTrue(result.isRight(), "the share must succeed even though push delivery failed")
        assertEquals(1, failingPush.sent.size)
    }

    private suspend fun insertList(id: String, name: String, ownerId: String) {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        newSuspendedTransaction(Dispatchers.IO) {
            Lists.insert {
                it[Lists.id] = id
                it[Lists.name] = name
                it[Lists.userID] = ownerId
                it[Lists.createdAt] = now
                it[Lists.updatedAt] = now
            }
        }
    }

    private suspend fun insertFloaterList(id: String, name: String, ownerId: String) {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        newSuspendedTransaction(Dispatchers.IO) {
            FloaterLists.insert {
                it[FloaterLists.id] = id
                it[FloaterLists.name] = name
                it[FloaterLists.userID] = ownerId
                it[FloaterLists.createdAt] = now
                it[FloaterLists.updatedAt] = now
            }
        }
    }

    private companion object {
        const val OWNER_ID = "user_owner"
        const val OWNER_NAME = "Olive Owner"
        const val MEMBER_ID = "user_member"
        const val MEMBER_USERNAME = "frieda"
        const val SCHEDULED_LIST_ID = "list_groceries"
        const val SCHEDULED_LIST_NAME = "Groceries"
        const val FLOATER_LIST_ID = "flist_someday"
        const val FLOATER_LIST_NAME = "Someday"
    }
}
