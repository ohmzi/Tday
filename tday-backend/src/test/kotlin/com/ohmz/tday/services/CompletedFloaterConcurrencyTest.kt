package com.ohmz.tday.services

import arrow.core.Either
import com.ohmz.tday.db.TestDatabase
import com.ohmz.tday.db.tables.CompletedFloaters
import com.ohmz.tday.db.tables.FloaterListShares
import com.ohmz.tday.db.tables.FloaterLists
import com.ohmz.tday.db.tables.Floaters
import com.ohmz.tday.db.tables.ListShares
import com.ohmz.tday.db.tables.Lists
import com.ohmz.tday.db.tables.Users
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [CompletedFloaterDurabilityTest]'s "second undo converges" test calls
 * `uncompleteFloater()` twice in strict sequence, so it can never exercise
 * the one piece of [FloaterServiceImpl.recreateFromCompletedRow] that only a
 * real database enforces: the partial unique index on
 * `(userID, recreatedFromListID)` declared on [FloaterLists]. H2 -- what
 * every other backend test runs against, [TestDatabase] included -- silently
 * drops that index at boot (Exposed logs "Index creation with a filter
 * condition is not supported in H2" and moves on without it), so a
 * sequential-only regression suite stays green even if that index were
 * deleted from [FloaterLists] entirely: nothing enforces convergence, the
 * find-or-create in [FloaterServiceImpl] just happens to run one call at a
 * time in every other test.
 *
 * This test runs pairs of genuinely concurrent undos -- two floaters
 * completed from the same now-deleted list, uncompleted from two coroutines
 * at once -- against a disposable real Postgres container, the engine
 * production actually runs on. There the index exists and can fire: one of
 * the two racing find-or-create transactions gets a real
 * `duplicate key value violates unique constraint` from Postgres, and
 * Exposed's default transaction retry (`Transaction.maxAttempts`, 3 attempts
 * out of the box -- see `DatabaseConfig.defaultMaxAttempts` in
 * exposed-core, never overridden anywhere in this backend) re-runs that
 * transaction from the top, where its find-or-create now sees the winner's
 * row and converges instead of surfacing the error. This test is what pins
 * that retry default as something this suite actually depends on, instead of
 * an unstated property of whatever Exposed happens to default to.
 *
 * Requires Docker; skipped (not failed) wherever it isn't available.
 */
class CompletedFloaterConcurrencyTest {

    companion object {
        private const val USER_ID = "user_owner_concurrency"

        private lateinit var postgres: PostgreSQLContainer<Nothing>

        @JvmStatic
        @BeforeAll
        fun startContainer() {
            val dockerAvailable = runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)
            Assumptions.assumeTrue(
                dockerAvailable,
                "Docker is not available in this environment -- skipping the real-Postgres concurrency test",
            )
            postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:16-alpine"))
            postgres.start()
        }

        @JvmStatic
        @AfterAll
        fun stopContainer() {
            if (::postgres.isInitialized) postgres.stop()
        }
    }

    private lateinit var db: Database
    private val push = NoOpPushNotificationService()
    private val cache = CacheServiceImpl()
    private val realtime = RealtimeServiceImpl()
    private val shareService = ListShareServiceImpl(cache, realtime, push)
    private val publisher = RealtimePublisher(realtime, shareService, cache, NoOpWebhookDispatchService, push)
    private val floaterService: FloaterService = FloaterServiceImpl(PassthroughFieldEncryption, cache, shareService, publisher)
    private val floaterListService: FloaterListService = FloaterListServiceImpl(PassthroughFieldEncryption, cache, shareService, publisher)

    @BeforeEach
    fun setUp() {
        // Unpooled: Database.connect(url, driver, user, password) opens a fresh
        // JDBC connection per transaction via DriverManager, same as production
        // gets from HikariCP -- which is what lets the two coroutines below
        // actually hold independent, concurrent database transactions.
        db = Database.connect(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password,
        )
        TransactionManager.defaultDatabase = db
        transaction(db) {
            // Same enum bootstrap DatabaseConfig.init() runs in production --
            // trimmed to the types the tables below actually reference.
            listOf(
                "\"UserRole\"" to listOf("ADMIN", "USER"),
                "\"ApprovalStatus\"" to listOf("APPROVED", "PENDING"),
                "\"Priority\"" to listOf("Low", "Medium", "High"),
                "\"ProjectColor\"" to listOf(
                    "RED", "ORANGE", "YELLOW", "LIME", "BLUE", "PURPLE", "PINK", "TEAL",
                    "CORAL", "GOLD", "DEEP_BLUE", "ROSE", "LIGHT_RED", "BRICK", "SLATE",
                ),
            ).forEach { (name, values) ->
                val valList = values.joinToString(", ") { "'$it'" }
                exec(
                    "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = " +
                        "${name.replace("\"", "'")}) THEN CREATE TYPE $name AS ENUM ($valList); END IF; END $$;",
                )
            }
            // The same table set DatabaseConfig.init() bootstraps with -- this is
            // what actually creates the partial unique index in this test, since
            // Postgres (unlike H2) honours the filter condition. Lists/ListShares/
            // FloaterListShares are pulled in too: RealtimePublisher.publishToCollaborators()
            // -- which uncompleteFloater() calls on every successful undo -- queries
            // them unconditionally via ListShareService.collaboratorIdsFor().
            SchemaUtils.createMissingTablesAndColumns(
                Users, Lists, FloaterLists, Floaters, CompletedFloaters, ListShares, FloaterListShares,
            )
        }
        TestDatabase.insertUser(USER_ID, username = "owner-concurrency@tday.test")
    }

    @AfterEach
    fun tearDown() {
        transaction(db) {
            SchemaUtils.drop(ListShares, FloaterListShares, CompletedFloaters, Floaters, FloaterLists, Lists, Users)
        }
        TransactionManager.closeAndUnregister(db)
    }

    @Test
    fun `two truly concurrent undos of floaters from the same deleted list converge onto one recreated list`() = runBlocking {
        // Several independent list-floater pairs in one run so a scheduling
        // fluke on any single pair can't make this test flaky by accident --
        // every one of these races still has to converge.
        repeat(8) { iteration ->
            val list = createList("Race $iteration", "BLUE")
            val floaterA = createFloater("Item A $iteration", list.id)
            val floaterB = createFloater("Item B $iteration", list.id)
            complete(floaterA)
            complete(floaterB)

            val deleted = floaterListService.deleteMany(USER_ID, listOf(list.id))
            check(deleted.isRight()) { "iteration $iteration: failed to delete list: $deleted" }

            val (resultA, resultB) = listOf(
                async(Dispatchers.IO) { floaterService.uncompleteFloater(USER_ID, floaterA) },
                async(Dispatchers.IO) { floaterService.uncompleteFloater(USER_ID, floaterB) },
            ).awaitAll()

            assertTrue(resultA.isRight(), "iteration $iteration: undo A failed: $resultA")
            assertTrue(resultB.isRight(), "iteration $iteration: undo B failed: $resultB")
            val listIdA = (resultA as Either.Right).value.listID
            val listIdB = (resultB as Either.Right).value.listID
            assertEquals(
                listIdA,
                listIdB,
                "iteration $iteration: both undos of the same deleted list must land on the same recreated list",
            )

            val recreatedListCount = newSuspendedTransaction(Dispatchers.IO) {
                FloaterLists.selectAll().where { FloaterLists.recreatedFromListID eq list.id }.count()
            }
            assertEquals(
                1L,
                recreatedListCount,
                "iteration $iteration: exactly one list should have been recreated from ${list.id} -- " +
                    "the partial unique index should have forced the losing transaction to converge, not duplicate",
            )
        }
    }

    // -- helpers, mirroring CompletedFloaterDurabilityTest ---------------------

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
}
