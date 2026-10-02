package com.ohmz.tday.plugins

import com.ohmz.tday.observability.BackendSentry
import com.ohmz.tday.observability.TdayObservability
import com.ohmz.tday.observability.TelemetryGate
import com.ohmz.tday.security.testAppConfig
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.sentry.Hint
import io.sentry.ITransportFactory
import io.sentry.Sentry
import io.sentry.SentryEnvelope
import io.sentry.SentryOptions
import io.sentry.transport.ITransport
import io.sentry.transport.RateLimiter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryRequestPluginTest {
    private val gate = TelemetryGate().apply { set(true) }
    private val recording = RecordingTransport()
    private val serverThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    @AfterEach
    fun closeSdk() {
        Sentry.close()
        serverThread.close()
    }

    @Test
    fun `a breadcrumb follows its request across a thread hop`() = testApplication(serverThread) {
        initSdk()
        application {
            install(SentryRequestPlugin)
            routing {
                get("/api/hop") {
                    TdayObservability.addBreadcrumb("before.hop")
                    // A database call leaves the thread the request started on; the scope has to
                    // go with it, or the error is reported without its trail.
                    withContext(Dispatchers.IO) { TdayObservability.captureException(RuntimeException("hop"), "test.hop") }
                    call.respondText("ok")
                }
            }
        }

        assertEquals(HttpStatusCode.OK, client.get("/api/hop").status)

        assertTrue("before.hop" in breadcrumbMessages(recording.events().single()))
    }

    @Test
    fun `a breadcrumb never appears on another request's event`() = testApplication(serverThread) {
        initSdk()
        application {
            install(SentryRequestPlugin)
            routing {
                get("/api/first") {
                    TdayObservability.addBreadcrumb("first.only")
                    TdayObservability.captureException(RuntimeException("first"), "test.first")
                    call.respondText("ok")
                }
                get("/api/second") {
                    TdayObservability.captureException(RuntimeException("second"), "test.second")
                    call.respondText("ok")
                }
            }
        }

        // One thread serves both requests, which is exactly when a thread-local scope would be
        // shared between them.
        client.get("/api/first")
        client.get("/api/second")

        val (first, second) = recording.events()
        assertTrue("first.only" in breadcrumbMessages(first))
        assertFalse("first.only" in breadcrumbMessages(second), "a breadcrumb bled across requests")
        assertEquals(listOf("api.request"), breadcrumbMessages(second))
    }

    @Test
    fun `tags the client platform and version for the request that sent them only`() = testApplication {
        initSdk()
        application {
            install(SentryRequestPlugin)
            routing {
                get("/api/capture") {
                    TdayObservability.captureException(RuntimeException("boom"), "test.capture")
                    call.respondText("ok")
                }
            }
        }

        client.get("/api/capture") {
            header("X-Tday-Client", "ios")
            header("X-Tday-App-Version", "0.8.0")
        }
        client.get("/api/capture")

        val (tagged, untagged) = recording.events()
        assertEquals("ios", tags(tagged)["client.platform"])
        assertEquals("0.8.0", tags(tagged)["client.version"])
        assertNull(tags(untagged)["client.platform"])
        assertNull(tags(untagged)["client.version"])
    }

    @Test
    fun `ignores client headers that are not a known platform or a version`() = testApplication {
        initSdk()
        application {
            install(SentryRequestPlugin)
            routing {
                get("/api/capture") {
                    TdayObservability.captureException(RuntimeException("boom"), "test.capture")
                    call.respondText("ok")
                }
            }
        }

        client.get("/api/capture") {
            header("X-Tday-Client", "alex@example.com")
            header("X-Tday-App-Version", "not-a-version")
        }

        val tags = tags(recording.events().single())
        assertNull(tags["client.platform"])
        assertNull(tags["client.version"])
    }

    @Test
    fun `serves requests normally when the sdk is not enabled`() = testApplication {
        application {
            install(SentryRequestPlugin)
            routing { get("/api/ping") { call.respondText("pong") } }
        }

        val response = client.get("/api/ping")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    private fun initSdk() {
        Sentry.init { options ->
            BackendSentry.configure(
                options,
                testAppConfig().copy(sentryDsn = DSN, sentryTracesSampleRate = 0.0),
                gate,
                ITransportFactory { _, _ -> recording },
            )
            options.isEnableUncaughtExceptionHandler = false
            options.isEnableShutdownHook = false
        }
    }

    private fun breadcrumbMessages(event: JsonObject): List<String> =
        event["breadcrumbs"]?.jsonArray
            ?.mapNotNull { it.jsonObject["message"]?.jsonPrimitive?.content }
            .orEmpty()

    private fun tags(event: JsonObject): Map<String, String> =
        event["tags"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty()

    private class RecordingTransport : ITransport {
        private val envelopes = mutableListOf<SentryEnvelope>()
        private val limiter = RateLimiter(SentryOptions())

        fun events(): List<JsonObject> = envelopes.flatMap { it.items }
            .map { Json.parseToJsonElement(String(it.data, Charsets.UTF_8)).jsonObject }

        override fun send(envelope: SentryEnvelope, hint: Hint) {
            envelopes += envelope
        }

        override fun flush(timeoutMillis: Long) = Unit

        override fun getRateLimiter(): RateLimiter = limiter

        override fun close(isRestarting: Boolean) = Unit

        override fun close() = Unit
    }

    private companion object {
        const val DSN = "https://publickey@o1.ingest.sentry.io/1"
    }
}
