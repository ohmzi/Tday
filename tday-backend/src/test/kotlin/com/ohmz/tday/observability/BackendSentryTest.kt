package com.ohmz.tday.observability

import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.security.testAppConfig
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.ITransportFactory
import io.sentry.KeyValueCollectionBehavior
import io.sentry.SamplingContext
import io.sentry.Sentry
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.TransactionContext
import io.sentry.protocol.SentryTransaction
import io.sentry.protocol.TransactionInfo
import io.sentry.protocol.User
import io.sentry.transport.ITransport
import io.sentry.transport.RateLimiter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("DEPRECATION")
class BackendSentryTest {
    private val gate = TelemetryGate()
    private val config = testAppConfig().copy(sentryDsn = DSN, sentryTracesSampleRate = 0.25)
    private val recording = RecordingTransport()

    @AfterEach
    fun closeSdk() {
        Sentry.close()
    }

    // --- Options ---------------------------------------------------------------------------

    @Test
    fun `identifies the release without naming the host`() {
        val options = options()

        assertEquals(DSN, options.dsn)
        assertEquals("development", options.environment)
        assertEquals("tday-backend@0.0.0", options.release)
        assertEquals("tday-backend", options.serverName)
        assertEquals(listOf("com.ohmz.tday"), options.inAppIncludes)
    }

    @Test
    fun `reports production as the environment when configured so`() {
        val options = options(config.copy(isProduction = true))

        assertEquals("production", options.environment)
    }

    @Test
    fun `collects no personal data`() {
        val options = options()
        val collection = options.dataCollection

        assertFalse(options.isSendDefaultPii)
        assertEquals(false, collection.userInfo)
        assertEquals(KeyValueCollectionBehavior.Mode.OFF, collection.cookies?.mode)
        assertEquals(KeyValueCollectionBehavior.Mode.OFF, collection.urlQueryParams?.mode)
        assertEquals(KeyValueCollectionBehavior.Mode.OFF, collection.httpHeaders.request?.mode)
        assertEquals(KeyValueCollectionBehavior.Mode.OFF, collection.httpHeaders.response?.mode)
        assertEquals(emptySet(), collection.httpBodies)
        assertEquals(false, collection.databaseQueryData)
        assertEquals(false, collection.filePaths)
        assertEquals(false, collection.graphql.document)
        assertEquals(false, collection.graphql.variables)
    }

    @Test
    fun `sends no client reports`() {
        assertFalse(options().isSendClientReports)
    }

    @Test
    fun `sends through the gated transport`() {
        assertIs<GatedTransportFactory>(options().transportFactory)
    }

    // --- Hooks follow the gate -------------------------------------------------------------

    @Test
    fun `beforeSend drops every event while the gate is closed`() {
        val beforeSend = assertNotNull(options().beforeSend)

        assertNull(beforeSend.execute(SentryEvent(), Hint()))
    }

    @Test
    fun `beforeSend scrubs events while the gate is open`() {
        gate.set(true)
        val beforeSend = assertNotNull(options().beforeSend)
        val event = SentryEvent().apply { user = User().apply { ipAddress = "203.0.113.7" } }

        val sent = assertNotNull(beforeSend.execute(event, Hint()))

        assertNull(sent.user)
    }

    @Test
    fun `beforeSend drops client aborts even when the gate is open`() {
        gate.set(true)
        val beforeSend = assertNotNull(options().beforeSend)

        assertNull(beforeSend.execute(SentryEvent(IOException("Broken pipe")), Hint()))
        assertNotNull(beforeSend.execute(SentryEvent(IllegalStateException("boom")), Hint()))
    }

    @Test
    fun `beforeSendTransaction drops transactions while the gate is closed`() {
        val beforeSendTransaction = assertNotNull(options().beforeSendTransaction)
        val transaction = SentryTransaction("GET /api/todo", 0.0, 1.0, emptyList(), emptyMap(), TransactionInfo("route"))

        assertNull(beforeSendTransaction.execute(transaction, Hint()))

        gate.set(true)
        assertNotNull(beforeSendTransaction.execute(transaction, Hint()))
    }

    @Test
    fun `beforeBreadcrumb drops everything while the gate is closed and log lines when it is open`() {
        val beforeBreadcrumb = assertNotNull(options().beforeBreadcrumb)
        val own = Breadcrumb().apply { category = "tday" }
        val logLine = Breadcrumb().apply { category = "com.ohmz.tday.plugins.Security" }

        assertNull(beforeBreadcrumb.execute(own, Hint()))

        gate.set(true)
        assertNotNull(beforeBreadcrumb.execute(own, Hint()))
        assertNull(beforeBreadcrumb.execute(logLine, Hint()))
    }

    // --- Tracing ---------------------------------------------------------------------------

    @Test
    fun `tracesSampler is zero while the gate is closed and the configured rate when open`() {
        val sampler = assertNotNull(options().tracesSampler)

        assertEquals(0.0, sampler.sample(samplingContext("GET /api/todo")))

        gate.set(true)
        assertEquals(0.25, sampler.sample(samplingContext("GET /api/todo")))
        assertEquals(0.0, sampler.sample(samplingContext("GET /health")))
    }

    @Test
    fun `health probe websocket and calendar feed requests are never traced`() {
        assertEquals(0.5, traceSampleRate("GET /api/todo/:id", gateOpen = true, configuredRate = 0.5))
        assertEquals(0.0, traceSampleRate("GET /health", gateOpen = true, configuredRate = 0.5))
        assertEquals(0.0, traceSampleRate("POST /api/mobile/probe", gateOpen = true, configuredRate = 0.5))
        assertEquals(0.0, traceSampleRate("GET /ws", gateOpen = true, configuredRate = 0.5))
        assertEquals(0.0, traceSampleRate("GET /calendar/:id", gateOpen = true, configuredRate = 0.5))
        assertEquals(0.0, traceSampleRate("GET /api/todo/:id", gateOpen = false, configuredRate = 0.5))
    }

    @Test
    fun `the names the request plugin really produces hit the exemptions`() {
        // The sampler sees the sanitised template, not the raw path: a calendar feed's token is
        // an identifier and must not be what decides whether it is traced.
        for (rawPath in listOf("/health", "/api/mobile/probe", "/ws", "/calendar/Zk3mQ9vT1xW8pL2nB7cD4fG6hJ")) {
            val name = TdayObservability.routeTemplate("GET", rawPath)
            assertEquals(0.0, traceSampleRate(name, gateOpen = true, configuredRate = 0.5), name)
        }
        assertEquals(0.5, traceSampleRate(TdayObservability.routeTemplate("GET", "/api/todo/summary"), true, 0.5))
    }

    @Test
    fun `a route that merely starts like an exempt one is still traced`() {
        assertEquals(0.5, traceSampleRate("GET /healthz", gateOpen = true, configuredRate = 0.5))
        assertEquals(0.5, traceSampleRate("GET /api/mobile/probe/:id", gateOpen = true, configuredRate = 0.5))
        assertEquals(0.5, traceSampleRate("GET /calendar", gateOpen = true, configuredRate = 0.5))
    }

    // --- End to end through the real SDK ---------------------------------------------------

    @Test
    fun `an absent dsn leaves the sdk disabled`() {
        BackendSentry.init(config.copy(sentryDsn = null), gate)

        assertFalse(Sentry.isEnabled())
    }

    @Test
    fun `nothing leaves the process while the gate is closed and nothing is replayed after it opens`() {
        initSdk()

        Sentry.captureException(RuntimeException("while off"))
        TdayObservability.captureException(RuntimeException("while off, too"), operation = "api.unhandled")
        Sentry.captureMessage("also off")
        assertTrue(recording.sent.isEmpty())

        gate.set(true)
        assertTrue(recording.sent.isEmpty(), "opening the gate must not release anything that was dropped")

        Sentry.captureException(RuntimeException("while on"))
        assertEquals(1, recording.sent.size)
    }

    @Test
    fun `an event stops flowing the moment the gate closes again`() {
        initSdk()
        gate.set(true)
        Sentry.captureException(RuntimeException("on"))
        gate.set(false)
        Sentry.captureException(RuntimeException("off again"))

        assertEquals(1, recording.sent.size)
    }

    @Test
    fun `a sent event carries no user ip host or identifier`() {
        initSdk()
        gate.set(true)
        TdayObservability.addBreadcrumb(
            operation = "api.request",
            category = "http",
            data = mapOf("method" to "PATCH", "route" to "/api/todo/cjld2cjxh0000qzrmn831i7rn?token=secret"),
        )
        Sentry.addBreadcrumb(
            Breadcrumb().apply {
                category = "com.ohmz.tday.plugins.Security"
                message = "[security] userId=cjld2cjxh0000qzrmn831i7rn path=/calendar/9f8e7d6c5b4a3210"
                level = SentryLevel.WARNING
            },
        )

        TdayObservability.captureException(
            RuntimeException(
                "duplicate key value violates unique constraint \"User_username_key\" " +
                    "Detail: Key (username)=(alexsmith) already exists. " +
                    "for alex@example.com from 203.0.113.7 " +
                    "list 3f2504e0-4f89-41d3-9a0c-0305e82c3301 todo cjld2cjxh0000qzrmn831i7rn " +
                    "at https://tday.example.com/api/todo/42?token=hunter2 " +
                    "on jdbc:postgresql:" + "//db.internal:5432/tday?password=hunter2",
            ),
            operation = "api.unhandled",
            data = mapOf("route" to "https://tday.example.com/api/todo/cjld2cjxh0000qzrmn831i7rn?token=hunter2"),
        )

        val payload = recording.eventPayloads().single()
        for (secret in listOf(
            "alexsmith", "alex@example.com", "203.0.113.7", "3f2504e0", "cjld2cjxh0000qzrmn831i7rn",
            "tday.example.com", "hunter2", "db.internal", "9f8e7d6c5b4a3210", "userId=",
        )) {
            assertFalse(secret in payload, "the event payload contains \"$secret\":\n$payload")
        }

        val event = Json.parseToJsonElement(payload).jsonObject
        assertFalse("user" in event, "no user object may be sent")
        assertEquals("tday-backend", event.getValue("server_name").jsonPrimitive.content)
        val crumbs = event.getValue("breadcrumbs").jsonArray
            .map { it.jsonObject.getValue("category").jsonPrimitive.content }
        assertEquals(listOf("http"), crumbs)
        // The constraint name still says what failed.
        assertTrue("User_username_key" in payload, payload)
    }

    private fun options(appConfig: AppConfig = config): SentryOptions =
        SentryOptions().also { BackendSentry.configure(it, appConfig, gate) }

    private fun initSdk() {
        Sentry.init { options ->
            BackendSentry.configure(options, config, gate, ITransportFactory { _, _ -> recording })
            options.isEnableUncaughtExceptionHandler = false
            options.isEnableShutdownHook = false
        }
    }

    private fun samplingContext(transactionName: String) =
        SamplingContext(TransactionContext(transactionName, "http.server"), null)

    private class RecordingTransport : ITransport {
        private val envelopes = mutableListOf<SentryEnvelope>()
        val sent: List<SentryEnvelope> get() = envelopes
        private val limiter = RateLimiter(SentryOptions())

        override fun send(envelope: SentryEnvelope, hint: Hint) {
            envelopes += envelope
        }

        fun eventPayloads(): List<String> =
            envelopes.flatMap { it.items }.map { String(it.data, Charsets.UTF_8) }

        override fun flush(timeoutMillis: Long) = Unit

        override fun getRateLimiter(): RateLimiter = limiter

        override fun close(isRestarting: Boolean) = Unit

        override fun close() = Unit
    }

    private companion object {
        const val DSN = "https://publickey@o1.ingest.sentry.io/1"
    }
}
