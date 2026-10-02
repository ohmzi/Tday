package com.ohmz.tday.observability

import io.sentry.Breadcrumb
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Message
import io.sentry.protocol.Request
import io.sentry.protocol.SentryException
import io.sentry.protocol.SentryTransaction
import io.sentry.protocol.TransactionInfo
import io.sentry.protocol.User
import io.ktor.utils.io.ClosedWriteChannelException
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.function.ThrowingSupplier
import java.io.IOException
import java.time.Duration
import java.nio.channels.ClosedChannelException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TelemetryScrubberTest {
    @Test
    fun `redacts the offending key a Postgres unique violation quotes`() {
        val message = """
            ERROR: duplicate key value violates unique constraint "User_username_key"
              Detail: Key (username)=(alexsmith) already exists.
        """.trimIndent()

        val scrubbed = TelemetryScrubber.scrubText(message)

        assertFalse("alexsmith" in scrubbed, scrubbed)
        assertTrue("Key [redacted] already exists" in scrubbed, scrubbed)
        // The constraint name says which rule failed and carries no user data.
        assertTrue("User_username_key" in scrubbed, scrubbed)
    }

    @Test
    fun `redacts the row Postgres prints for a failed constraint`() {
        val scrubbed = TelemetryScrubber.scrubText(
            "null value in column \"title\" violates not-null constraint\n  Detail: Failing row contains (c1, Buy milk for Sam, 2026-10-01).",
        )

        assertFalse("Buy milk" in scrubbed, scrubbed)
        assertTrue("Failing row contains [redacted]" in scrubbed, scrubbed)
    }

    @Test
    fun `redacts a value quoted by an invalid input error`() {
        val scrubbed = TelemetryScrubber.scrubText("""invalid input syntax for type uuid: "alex smith"""")

        assertEquals("""invalid input syntax for type uuid: "[redacted]"""", scrubbed)
    }

    @Test
    fun `redacts connection strings and urls`() {
        val scrubbed = TelemetryScrubber.scrubText(
            "Cannot connect to jdbc:postgresql://db.internal:5432/tday?user=admin&password=hunter2 " +
                "after GET https://tday.example.com/api/todo/42?token=abc and postgresql://u:p@db/tday",
        )

        assertEquals("Cannot connect to [jdbc] after GET [url] and [url]", scrubbed)
    }

    @Test
    fun `redacts emails ip addresses and bare hosts`() {
        val scrubbed = TelemetryScrubber.scrubText(
            "login by alex@example.com from 203.0.113.7 and 2001:db8::ff00:42:8329 via ::1 " +
                "to tday.example.com:8443 or localhost:5432",
        )

        assertEquals("login by [email] from [ip] and [ip] via [ip] to [host] or [host]", scrubbed)
    }

    @Test
    fun `redacts uuids cuids tokens and long digit runs`() {
        val scrubbed = TelemetryScrubber.scrubText(
            "list 3f2504e0-4f89-41d3-9a0c-0305e82c3301 todo cjld2cjxh0000qzrmn831i7rn " +
                "key tday_8f3a9c1d2b7e4f60a1b2c3d4e5f60718 phone 4155550123",
        )

        assertEquals("list [uuid] todo [id] key [token] phone [number]", scrubbed)
    }

    @Test
    fun `keeps class names file positions and small numbers readable`() {
        val text = "IndexOutOfBoundsException: Index 5 out of bounds for length 3 at " +
            "com.ohmz.tday.services.TodoServiceImpl.create(TodoService.kt:42) on port 8080"

        assertEquals(text, TelemetryScrubber.scrubText(text))
    }

    @Test
    fun `truncates long text after redacting it`() {
        val scrubbed = TelemetryScrubber.scrubText("x".repeat(1000))

        assertEquals(TelemetryScrubber.MAX_TEXT_LENGTH + 1, scrubbed.length)
        assertTrue(scrubbed.endsWith("…"))
    }

    @Test
    fun `a pathological message is handled in bounded time`() {
        val hostile = "Key (a)=(b ".repeat(50_000) + "a.".repeat(100_000) + "1-".repeat(100_000)

        val scrubbed = assertTimeoutPreemptively(Duration.ofSeconds(5), ThrowingSupplier { TelemetryScrubber.scrubText(hostile) })

        assertTrue(scrubbed.length <= TelemetryScrubber.MAX_TEXT_LENGTH + 1)
    }

    @Test
    fun `strips the user the request headers and the query from an event`() {
        val event = SentryEvent().apply {
            user = User().apply {
                id = "user_1"
                email = "alex@example.com"
                ipAddress = "203.0.113.7"
            }
            request = Request().apply {
                method = "PATCH"
                url = "https://tday.example.com/api/todo/cjld2cjxh0000qzrmn831i7rn?token=secret"
                queryString = "token=secret"
                cookies = "session=abc"
                headers = mapOf("Cookie" to "session=abc", "Referer" to "https://tday.example.com/app/list/42")
                data = """{"title":"Buy milk"}"""
                setEnvs(mapOf("REMOTE_ADDR" to "203.0.113.7"))
            }
        }

        val scrubbed = TelemetryScrubber.scrubEvent(event)

        assertNull(scrubbed.user)
        val request = assertNotNull(scrubbed.request)
        assertEquals("PATCH", request.method)
        assertEquals("/api/todo/:id", request.url)
        assertNull(request.queryString)
        assertNull(request.cookies)
        assertNull(request.headers)
        assertNull(request.data)
        assertNull(request.envs)
    }

    @Test
    fun `redacts exception values and log messages but keeps the exception type`() {
        val event = SentryEvent().apply {
            message = Message().apply {
                message = "Could not reach %s for %s"
                formatted = "Could not reach tday.example.com:8443 for alex@example.com"
                params = listOf("tday.example.com:8443", "alex@example.com")
            }
            exceptions = listOf(
                SentryException().apply {
                    type = "PSQLException"
                    value = "Detail: Key (username)=(alexsmith) already exists."
                },
            )
        }

        val scrubbed = TelemetryScrubber.scrubEvent(event)

        val message = assertNotNull(scrubbed.message)
        assertEquals("Could not reach [host] for [email]", message.formatted)
        assertNull(message.params)
        assertFalse("example.com" in message.message.orEmpty())
        val exception = scrubbed.exceptions!!.single()
        assertEquals("PSQLException", exception.type)
        assertEquals("Detail: Key [redacted] already exists.", exception.value)
    }

    @Test
    fun `an event without a user request or exceptions passes through`() {
        val scrubbed = TelemetryScrubber.scrubEvent(SentryEvent())

        assertNull(scrubbed.user)
        assertNull(scrubbed.request)
        assertNull(scrubbed.message)
        assertNull(scrubbed.exceptions)
    }

    @Test
    fun `strips the user from a transaction`() {
        val transaction = SentryTransaction(
            "GET /api/todo/:id", 0.0, 1.0, emptyList(), emptyMap(), TransactionInfo("route"),
        )
        transaction.user = User().apply { ipAddress = "203.0.113.7" }

        assertNull(TelemetryScrubber.scrubTransaction(transaction).user)
    }

    @Test
    fun `keeps only breadcrumbs the app itself wrote`() {
        fun crumb(category: String?) = Breadcrumb().apply {
            this.category = category
            message = "something"
            level = SentryLevel.INFO
        }

        assertNotNull(TelemetryScrubber.scrubBreadcrumb(crumb("tday")))
        assertNotNull(TelemetryScrubber.scrubBreadcrumb(crumb("http")))
        assertNotNull(TelemetryScrubber.scrubBreadcrumb(crumb("security")))
        // A log line's category is its logger name, and its message is whatever was formatted
        // into it: user ids, paths, push endpoints.
        assertNull(TelemetryScrubber.scrubBreadcrumb(crumb("com.ohmz.tday.plugins.Security")))
        assertNull(TelemetryScrubber.scrubBreadcrumb(crumb("console")))
        assertNull(TelemetryScrubber.scrubBreadcrumb(crumb(null)))
    }

    @Test
    fun `recognises cancellation and clients that hung up as aborts`() {
        assertTrue(TelemetryScrubber.isClientAbort(CancellationException("cancelled")))
        assertTrue(TelemetryScrubber.isClientAbort(ClosedChannelException()))
        assertTrue(TelemetryScrubber.isClientAbort(ClosedWriteChannelException(null)))
        assertTrue(TelemetryScrubber.isClientAbort(IOException("Broken pipe")))
        assertTrue(TelemetryScrubber.isClientAbort(IOException("Connection reset by peer")))
        assertTrue(TelemetryScrubber.isClientAbort(RuntimeException("wrapped", IOException("Broken pipe"))))
    }

    @Test
    fun `real failures are not aborts`() {
        assertFalse(TelemetryScrubber.isClientAbort(null))
        assertFalse(TelemetryScrubber.isClientAbort(IllegalStateException("boom")))
        assertFalse(TelemetryScrubber.isClientAbort(IOException("No space left on device")))
    }

    @Test
    fun `a cause chain that loops does not hang the abort check`() {
        val first = RuntimeException("first")
        val second = RuntimeException("second", first)
        first.initCause(second)

        assertFalse(TelemetryScrubber.isClientAbort(first))
    }
}
