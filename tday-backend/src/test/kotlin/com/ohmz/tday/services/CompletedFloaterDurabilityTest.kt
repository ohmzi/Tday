package com.ohmz.tday.services

import arrow.core.Either
import com.ohmz.tday.db.TestDatabase
import com.ohmz.tday.db.tables.CompletedFloaters
import com.ohmz.tday.db.tables.FloaterLists
import com.ohmz.tday.db.tables.Floaters
import com.ohmz.tday.security.FieldEncryption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the real service implementations against a database built from the
 * production Exposed table declarations (see [TestDatabase]), so the
 * `ON DELETE SET NULL` FK realignment in V27 and the find-or-create logic in
 * [FloaterServiceImpl.uncompleteFloater] both run the same path production
 * does -- a hand-written fake could not catch either one drifting.
 */
class CompletedFloaterDurabilityTest {
    private val db: Database = TestDatabase.fresh()
    private val push = NoOpPushNotificationService()
    private val cache = CacheServiceImpl()
    private val realtime = RealtimeServiceImpl()
    private val shareService = ListShareServiceImpl(cache, realtime, push)
    private val publisher = RealtimePublisher(realtime, shareService, cache, NoOpWebhookDispatchService, push)
    private val floaterService: FloaterService = FloaterServiceImpl(PassthroughFieldEncryption, cache, shareService, publisher)
    private val floaterListService: FloaterListService = FloaterListServiceImpl(PassthroughFieldEncryption, cache, shareService, publisher)
    private val completedFloaterService: CompletedFloaterService = CompletedFloaterServiceImpl(PassthroughFieldEncryption, cache)

    @BeforeEach
    fun setUp() {
        TestDatabase.insertUser(USER_ID, username = "owner@tday.test")
    }

    @AfterEach
    fun tearDown() {
        TestDatabase.close(db)
    }

    @Test
    fun `uncomplete restores the floater in place when the list was never deleted`() = runBlocking {
        val list = createList("Errands", "BLUE")
        val floaterId = createFloater("Buy milk", list.id)
        complete(floaterId)

        val listing = (completedFloaterService.getAll(USER_ID) as Either.Right).value
        assertFalse(listing.single().listDeleted, "the list is still live -- listDeleted must stay false")

        val result = floaterService.uncompleteFloater(USER_ID, floaterId)

        assertTrue(result.isRight())
        val response = (result as Either.Right).value
        assertFalse(response.listRecreated, "the list was never deleted -- nothing should be recreated")
        assertEquals(list.id, response.listID)
        assertEquals(list.id, response.floater?.listID)
        assertEquals(false, response.floater?.completed)

        assertLiveFloaterCount(1)
        assertCompletedFloaterCount(0)
        assertFloaterListCount(1)
    }

    @Test
    fun `list deletion, then uncomplete recreates the list with the original name and color`() = runBlocking {
        val list = createList("Weekend chores", "TEAL")
        val floaterId = createFloater("Mow the lawn", list.id)
        complete(floaterId)

        val deleted = floaterListService.deleteMany(USER_ID, listOf(list.id))
        assertTrue(deleted.isRight())
        assertEquals(listOf(list.id), (deleted as Either.Right).value)

        // The list is gone, but the completion record survives it, detached.
        assertFloaterListCount(0)
        assertCompletedFloaterCount(1)
        val survivor = singleCompletedFloaterRow()
        assertNull(survivor[CompletedFloaters.listID], "the FK should have been nulled by ON DELETE SET NULL")
        assertEquals(list.id, survivor[CompletedFloaters.originalListID], "the unconstrained copy must survive the detach")

        // GET /api/completedFloater must be able to tell the client the list is gone.
        val listing = (completedFloaterService.getAll(USER_ID) as Either.Right).value
        assertTrue(listing.single().listDeleted, "listDeleted should flip once the list is gone")

        val result = floaterService.uncompleteFloater(USER_ID, floaterId)

        assertTrue(result.isRight())
        val response = (result as Either.Right).value
        assertTrue(response.listRecreated, "the original list is gone -- this must recreate it")
        assertNotNull(response.listID)
        assertNotEquals(list.id, response.listID, "a recreated list must not reuse the deleted list's id")
        assertEquals("Weekend chores", response.listName)
        assertEquals("TEAL", response.listColor)
        assertEquals(response.listID, response.floater?.listID)
        assertEquals(false, response.floater?.completed)

        assertFloaterListCount(1)
        assertCompletedFloaterCount(0)
        assertLiveFloaterCount(1)

        val recreatedList = newSuspendedTransaction(Dispatchers.IO) {
            FloaterLists.selectAll().where { FloaterLists.id eq response.listID!! }.first()
        }
        assertEquals(list.id, recreatedList[FloaterLists.recreatedFromListID])
        assertEquals("Weekend chores", recreatedList[FloaterLists.name])
        assertEquals("TEAL", recreatedList[FloaterLists.color]?.name)
    }

    @Test
    fun `a second undo from the same deleted list converges onto the list the first undo recreated`() = runBlocking {
        val list = createList("Groceries", "ORANGE")
        val floaterA = createFloater("Buy milk", list.id)
        val floaterB = createFloater("Buy eggs", list.id)
        complete(floaterA)
        complete(floaterB)

        floaterListService.deleteMany(USER_ID, listOf(list.id))
        assertFloaterListCount(0)
        assertCompletedFloaterCount(2)

        val firstUndo = (floaterService.uncompleteFloater(USER_ID, floaterA) as Either.Right).value
        assertTrue(firstUndo.listRecreated)
        assertFloaterListCount(1, "the first undo should have created exactly one list")

        val secondUndo = (floaterService.uncompleteFloater(USER_ID, floaterB) as Either.Right).value
        assertTrue(secondUndo.listRecreated, "the SECOND item also lands in a recreated list, not its original")
        assertEquals(firstUndo.listID, secondUndo.listID, "both items must converge onto the SAME recreated list")

        assertFloaterListCount(1, "converging must not create a second list")
        assertLiveFloaterCount(2)
        assertCompletedFloaterCount(0)
    }

    @Test
    fun `deleteById on a completed floater also removes the orphaned Floaters row`() = runBlocking {
        val list = createList("Someday", null)
        val floaterId = createFloater("Read a book", list.id)
        complete(floaterId)

        val completedRow = singleCompletedFloaterRow()
        val completedId = completedRow[CompletedFloaters.id]

        // Still there, completed = true, invisible to getAll() but not yet cleaned up.
        assertLiveFloaterCount(1)

        val result = completedFloaterService.deleteById(USER_ID, completedId)

        assertTrue(result.isRight())
        assertEquals(1, (result as Either.Right).value)
        assertCompletedFloaterCount(0)
        assertLiveFloaterCount(0, "the permanent delete must take the orphaned Floaters row with it")
    }

    // -- helpers ------------------------------------------------------------

    private data class TestList(val id: String, val name: String, val color: String?)

    private suspend fun createList(name: String, color: String?): TestList {
        val result = floaterListService.create(USER_ID, name, color, iconKey = null)
        check(result.isRight()) { "failed to create list: $result" }
        val list = (result as Either.Right).value
        return TestList(list.id, list.name, list.color)
    }

    private suspend fun createFloater(title: String, listId: String?): String {
        val result = floaterService.create(USER_ID, title, description = null, priority = "Medium", listID = listId)
        check(result.isRight()) { "failed to create floater: $result" }
        return (result as Either.Right).value.id
    }

    private suspend fun complete(floaterId: String) {
        val result = floaterService.completeFloater(USER_ID, floaterId)
        check(result.isRight()) { "failed to complete floater: $result" }
    }

    private fun assertLiveFloaterCount(expected: Int, message: String? = null) = runBlocking {
        val count = newSuspendedTransaction(Dispatchers.IO) { Floaters.selectAll().count() }
        assertEquals(expected.toLong(), count, message ?: "unexpected live Floaters row count")
    }

    private fun assertCompletedFloaterCount(expected: Int, message: String? = null) = runBlocking {
        val count = newSuspendedTransaction(Dispatchers.IO) { CompletedFloaters.selectAll().count() }
        assertEquals(expected.toLong(), count, message ?: "unexpected CompletedFloaters row count")
    }

    private fun assertFloaterListCount(expected: Int, message: String? = null) = runBlocking {
        val count = newSuspendedTransaction(Dispatchers.IO) { FloaterLists.selectAll().count() }
        assertEquals(expected.toLong(), count, message ?: "unexpected FloaterLists row count")
    }

    private fun singleCompletedFloaterRow() = runBlocking {
        newSuspendedTransaction(Dispatchers.IO) { CompletedFloaters.selectAll().single() }
    }

    private companion object {
        const val USER_ID = "user_owner"
    }
}

/** No encryption key configured -- every call is a pass-through, same as an unconfigured server. */
private object PassthroughFieldEncryption : FieldEncryption {
    override fun isConfigured(): Boolean = false
    override fun encrypt(plaintext: String): String = plaintext
    override fun decrypt(raw: String): String = raw
    override fun isSensitiveField(fieldName: String): Boolean = false
    override fun isEncrypted(value: String): Boolean = false
    override fun encryptIfSensitive(fieldName: String, value: String?): String? = value
    override fun decryptIfEncrypted(value: String?): String? = value
}

/** Nothing is registered to dispatch in these tests; skip the real HTTP-backed implementation. */
private object NoOpWebhookDispatchService : WebhookDispatchService {
    override fun dispatch(recipientUserIds: Collection<String>, event: com.ohmz.tday.domain.DomainEvent) = Unit
}
