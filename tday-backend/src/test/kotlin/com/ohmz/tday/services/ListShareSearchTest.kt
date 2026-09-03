package com.ohmz.tday.services

import com.ohmz.tday.db.TestDatabase
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.LikePattern
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The share-member typeahead. Usernames in this app are email-shaped, and members are as likely
 * to be looked up by the name they display under as by the address they signed up with.
 *
 * The `skipcq: KT-W1042` marker below is deliberate. `"ann_lee"` is a per-test fixture identity,
 * not a shared value: the underscore test creates that account and then types it in full, so
 * `assertEquals(listOf("ann_lee"), search("ann_lee"))` reads as an input/output pair and naming
 * it would hide the very thing the test asserts — that what you type comes back. The fourth
 * occurrence is in the pure `userSearchPattern` test, which touches no database at all and only
 * happens to reuse the string as a convenient input with an underscore in it; binding it to the
 * fixture's constant would imply a coupling that does not exist. Same call as the
 * `// skipcq: KT-W1042` markers in the Android `BulkTaskCacheTest`.
 */
class ListShareSearchTest {
    // JUnit builds a new instance per test method, so this is one empty database per test.
    private val db: Database = TestDatabase.fresh()
    private val service = ListShareServiceImpl(CacheServiceImpl(), RealtimeServiceImpl(), NoOpPushNotificationService())

    @BeforeEach
    fun setUp() {
        TestDatabase.insertUser(REQUESTER_ID, username = "me@tday.test", name = "Me Myself")
        TestDatabase.insertUser("user_zed", username = EMAIL_SHAPED_USERNAME, name = "Zed Adams")
        TestDatabase.insertUser("user_karen", username = "kb1972@mail.test", name = "Karen Blake")
        TestDatabase.insertUser("user_nameless", username = "quiet@mail.test", name = null)
    }

    @AfterEach
    fun tearDown() {
        TestDatabase.close(db)
    }

    private suspend fun search(query: String): List<String> =
        service.searchUsers(REQUESTER_ID, query).getOrNull().orEmpty().map { it.username }

    @Test
    fun `an email shaped username is found by typing it in full`() = runBlocking {
        assertEquals(listOf(EMAIL_SHAPED_USERNAME), search(EMAIL_SHAPED_USERNAME))
    }

    @Test
    fun `a member is found by display name when the username does not match`() = runBlocking {
        assertEquals(listOf("kb1972@mail.test"), search("Karen"))
    }

    @Test
    fun `a member without a display name is still found by username`() = runBlocking {
        assertEquals(listOf("quiet@mail.test"), search("quiet"))
    }

    @Test
    fun `a member matching on both name and username is returned once`() = runBlocking {
        TestDatabase.insertUser("user_both", username = "blake@mail.test", name = "Blake Reed")
        assertEquals(1, search("blake").count { it == "blake@mail.test" })
    }

    @Test
    fun `the requester and unapproved accounts stay out of the results`() = runBlocking {
        TestDatabase.insertUser("user_pending", username = "pending@mail.test", approvalStatus = "PENDING")

        assertTrue(search("mail.test").isNotEmpty())
        assertTrue(search("me@tday.test").isEmpty(), "the requester should not find themselves")
        assertTrue(search("pending").isEmpty(), "unapproved accounts are not shareable")
    }

    @Test
    fun `an underscore in a username is matched as itself`() = runBlocking {
        TestDatabase.insertUser("user_score", username = "ann_lee", name = "Ann Lee")  // skipcq: KT-W1042
        TestDatabase.insertUser("user_decoy", username = "annXlee", name = "Decoy")

        // Stripping "_" looked for "annlee" and found neither; leaving it unescaped would have
        // matched the decoy too, because "_" is LIKE's single-character wildcard.
        assertEquals(listOf("ann_lee"), search("ann_lee"))
    }

    @Test
    fun `wildcards typed into the query cannot widen the match`() = runBlocking {
        // Read literally these match nobody; treated as LIKE wildcards both would find
        // kb1972@mail.test.
        assertTrue(search("k%1972").isEmpty())
        assertTrue(search("k_1972").isEmpty())
        assertTrue(search("%").isEmpty(), "a lone wildcard is below the minimum length")
        assertTrue(search("%%").isEmpty(), "a wildcard-only query matches those characters literally")
    }

    @Test
    fun `a query shorter than two characters returns nothing`() = runBlocking {
        assertTrue(search("z").isEmpty())
        assertTrue(search(" ").isEmpty())
    }

    @Test
    fun `the search pattern escapes LIKE syntax instead of dropping it`() {
        assertEquals(LikePattern("""%z@a.com%""", '\\'), userSearchPattern("  Z@A.com "))
        assertEquals(LikePattern("""%karen blake%""", '\\'), userSearchPattern("Karen Blake"))
        assertEquals(LikePattern("""%a\%b%""", '\\'), userSearchPattern("a%b"))
        assertEquals(LikePattern("""%ann\_lee%""", '\\'), userSearchPattern("ann_lee"))
        assertEquals(LikePattern("""%a\\b%""", '\\'), userSearchPattern("""a\b"""))
        assertEquals(null, userSearchPattern("_"), "one character is still too short to search")
    }

    private companion object {
        const val REQUESTER_ID = "user_me"
        const val EMAIL_SHAPED_USERNAME = "z@a.com"
    }
}
