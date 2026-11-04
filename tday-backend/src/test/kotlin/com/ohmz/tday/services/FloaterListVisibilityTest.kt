package com.ohmz.tday.services

import arrow.core.Either
import com.ohmz.tday.db.TestDatabase
import com.ohmz.tday.db.tables.FloaterListShares
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.shared.model.ImportRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Real services over the production table declarations: the setting belongs to the list. */
class FloaterListVisibilityTest {
    private val db: Database = TestDatabase.fresh()
    private val push = NoOpPushNotificationService()
    private val cache = CacheServiceImpl()
    private val realtime = RealtimeServiceImpl()
    private val shareService = ListShareServiceImpl(cache, realtime, push)
    private val publisher = RealtimePublisher(realtime, shareService, cache, push)
    private val exportService: ExportService = ExportServiceImpl(PassthroughFieldEncryption, cache, publisher)
    private val service: FloaterListService = FloaterListServiceImpl(PassthroughFieldEncryption, shareService, publisher)

    @BeforeEach
    fun setUp() {
        TestDatabase.insertUser(OWNER, username = "owner@tday.test")
        TestDatabase.insertUser(MEMBER, username = "member@tday.test")
    }

    @AfterEach
    fun tearDown() {
        TestDatabase.close(db)
    }

    @Test
    fun `a new list shows on the main screen by default`() = runBlocking {
        val id = create()
        assertTrue(listFor(OWNER, id).showOnMainScreen)
    }

    @Test
    fun `create can start a list hidden`() = runBlocking {
        val id = create(showOnMainScreen = false)
        assertFalse(listFor(OWNER, id).showOnMainScreen)
    }

    @Test
    fun `update persists hiding the list`() = runBlocking {
        val id = create()
        service.update(OWNER, id, null, null, null, showOnMainScreen = false)
        assertFalse(listFor(OWNER, id).showOnMainScreen)
    }

    @Test
    fun `an update that omits the field leaves it alone`() = runBlocking {
        val id = create(showOnMainScreen = false)
        service.update(OWNER, id, "Renamed", null, null)
        assertFalse(listFor(OWNER, id).showOnMainScreen)
    }

    @Test
    fun `a member cannot change the setting`() = runBlocking {
        val id = create()
        share(id)
        val result = service.update(MEMBER, id, null, null, null, showOnMainScreen = false)
        assertIs<AppError.Forbidden>((result as Either.Left).value)
        assertTrue(listFor(OWNER, id).showOnMainScreen)
    }

    @Test
    fun `a member reads the owner's setting`() = runBlocking {
        val id = create(showOnMainScreen = false)
        share(id)
        assertFalse(listFor(MEMBER, id).showOnMainScreen)
    }

    @Test
    fun `export and import keep a hidden list hidden`() = runBlocking {
        val id = create(showOnMainScreen = false)
        val exported = (exportService.exportAll(OWNER) as Either.Right).value
        assertFalse(exported.floaterLists.single { it.id == id }.showOnMainScreen)

        service.deleteMany(OWNER, listOf(id))
        exportService.import(OWNER, ImportRequest(export = exported))

        assertFalse(listFor(OWNER, id).showOnMainScreen)
    }

    private suspend fun create(showOnMainScreen: Boolean = true): String =
        (service.create(OWNER, "Someday", null, null, showOnMainScreen = showOnMainScreen) as Either.Right).value.id

    private suspend fun listFor(userId: String, id: String) =
        (service.getAll(userId) as Either.Right).value.single { it.id == id }

    private suspend fun share(listId: String) {
        val now = LocalDateTime.now()
        newSuspendedTransaction(Dispatchers.IO) {
            FloaterListShares.insert {
                it[FloaterListShares.id] = "share_1"
                it[FloaterListShares.listID] = listId
                it[FloaterListShares.userID] = MEMBER
                it[FloaterListShares.role] = "EDITOR"
                it[FloaterListShares.createdAt] = now
                it[FloaterListShares.updatedAt] = now
            }
        }
    }

    private companion object {
        const val OWNER = "owner_1"
        const val MEMBER = "member_1"
    }
}
