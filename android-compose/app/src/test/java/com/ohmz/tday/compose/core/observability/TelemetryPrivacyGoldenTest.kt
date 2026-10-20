package com.ohmz.tday.compose.core.observability

import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.JsonSerializer
import io.sentry.SentryEvent
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.protocol.App
import io.sentry.protocol.DebugImage
import io.sentry.protocol.DebugMeta
import io.sentry.protocol.Device
import io.sentry.protocol.Message
import io.sentry.protocol.SentryException
import io.sentry.protocol.SentryStackFrame
import io.sentry.protocol.SentryStackTrace
import io.sentry.protocol.SentryThread
import io.sentry.protocol.User
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import java.util.Date
import java.util.TimeZone

/**
 * The privacy promise, tested the way a reader of the report would check it: build the worst event
 * this app could plausibly produce, send it through the real callbacks the SDK is given, serialise
 * it exactly as the transport would, and look for the things the FAQ says are never sent.
 *
 * A failure here is not "a scrubber rule regressed", it is "this string would have reached Sentry".
 */
class TelemetryPrivacyGoldenTest {
    private val gate = TelemetryGate().apply { open() }
    private val options = SentryAndroidOptions().also {
        TelemetryOptions.configure(
            it,
            TelemetrySettings(
                dsn = "https://publickey@o123.ingest.sentry.io/456",
                environment = "production",
                release = "tday-android@0.8.0",
                dist = "80000",
                appVersion = "0.8.0",
                cacheDirPath = "/data/user/0/com.ohmz.tday.compose/cache/sentry",
            ),
            grantedAtMs = 1_000L,
            gate = gate,
            eventTags = {
                TelemetryEventTags.build(
                    appVersion = "0.8.0",
                    mode = TelemetryWorkspaceMode.SERVER,
                    timeZone = TimeZone.getTimeZone("America/Sao_Paulo"),
                    locale = java.util.Locale("pt", "BR"),
                    nowMs = 1_700_000_000_000L,
                )
            },
        )
    }

    private fun worstEvent(): SentryEvent = SentryEvent(Date(2_000L)).apply {
        user = User().apply {
            id = INSTALL_ID
            ipAddress = "{{auto}}"
            email = "alex@example.com"
            username = "alex"
        }
        serverName = "alex-pixel"
        contexts.setDevice(
            Device().apply {
                id = INSTALL_ID
                name = "Alex's Pixel"
                locale = "pt_BR"
                timezone = TimeZone.getTimeZone("America/Sao_Paulo")
                bootTime = Date(1_700_000_000_000L)
                model = "Pixel 8"
            },
        )
        contexts.setApp(
            App().apply {
                appVersion = "0.8.0"
                permissions = mapOf("android.permission.READ_CALENDAR" to "granted")
            },
        )
        exceptions = listOf(
            SentryException().apply {
                type = "java.net.UnknownHostException"
                value = "Unable to resolve host \"tasks.my-home.example\": No address associated with hostname"
            },
            SentryException().apply {
                type = "java.net.ConnectException"
                value = "Failed to connect to nas.local/192.168.1.20:8443 for alex@example.com"
            },
            SentryException().apply {
                type = "java.lang.IllegalStateException"
                value = "No route for tday://todos/list/9d2f4c1e-0a1b-4c2d-8e3f-5a6b7c8d9e0f/Groceries; " +
                    "peer 203.0.113.7 list 9d2f4c1e-0a1b-4c2d-8e3f-5a6b7c8d9e0f"
            },
        )
        message = Message().apply { message = "sync failed for alex@example.com" }
        // Free text in an extra is redacted by shape, like a message. What an extra says in words
        // comes from `TdayTelemetry.safeDataValue`, which only lets an enum-like label through.
        setExtra("note", "owner alex@example.com retried")
        breadcrumbs = listOf(
            Breadcrumb().apply {
                category = "navigation"
                setData("from", "todos/list/{listId}/{listName}")
                setData("to", "calendar")
                setData("from_arguments", "{listId=9d2f4c1e, listName=Groceries}")
            },
            Breadcrumb().apply {
                category = "http"
                setData("url", "https://tasks.my-home.example/api/todo/cjld2cjxh0000qzrmn831i7rn?q=Groceries")
                setData("method", "GET")
                setData("status_code", 503)
                setData("host", "tasks.my-home.example")
            },
            Breadcrumb().apply {
                category = "ui.click"
                message = "Groceries"
                setData("view.tag", "Groceries")
            },
            Breadcrumb().apply {
                category = "tday"
                message = "sync.replay"
                setData("pending", 3)
            },
        ).mapNotNull { options.beforeBreadcrumb!!.execute(it, Hint()) }
    }

    private fun serializedReport(): String {
        val sent = options.beforeSend!!.execute(worstEvent(), Hint())
        requireNotNull(sent) { "the event was dropped, so the test proves nothing" }
        return StringWriter().also { JsonSerializer(options).serialize(sent, it) }.toString()
    }

    @Test
    fun `nothing the FAQ lists as never sent survives into the serialised report`() {
        val json = serializedReport()

        for (secret in listOf(
            INSTALL_ID,
            "alex@example.com",
            "alex-pixel",
            "Alex's Pixel",
            "\"alex\"",
            "tasks.my-home.example",
            "my-home",
            "nas.local",
            "192.168.1.20",
            "203.0.113.7",
            "9d2f4c1e",
            "cjld2cjxh0000qzrmn831i7rn",
            "Groceries",
            "{{auto}}",
            "America/Sao_Paulo",
            "Sao_Paulo",
            "pt_BR",
            "READ_CALENDAR",
            "ip_address",
        )) {
            assertFalse("the report still contains $secret:\n$json", json.contains(secret))
        }
    }

    /** What `AnrV2Integration` builds from the system's thread dump: every thread, every loaded image. */
    private fun anrEvent(): SentryEvent = SentryEvent(Date(2_000L)).apply {
        threads = listOf(
            SentryThread().apply {
                id = 1L
                name = "main"
                isCrashed = true
                stacktrace = SentryStackTrace(
                    listOf(
                        SentryStackFrame().apply {
                            module = "com.ohmz.tday.compose.MainActivity"
                            function = "onResume"
                            filename = "MainActivity.kt"
                        },
                        SentryStackFrame().apply {
                            `package` = "/data/app/~~x==/com.ohmz.tday-y==/lib/arm64/libsentry.so"
                            absPath = "/data/app/~~x==/com.ohmz.tday-y==/base.apk"
                            filename = "/data/app/~~x==/com.ohmz.tday-y==/base.apk"
                        },
                    ),
                )
            },
            SentryThread().apply {
                id = 42L
                name = "OkHttp https://tday.alice-home.net/..."
            },
            SentryThread().apply {
                id = 43L
                name = "OkHttp tday.alice-home.net"
            },
            SentryThread().apply {
                id = 44L
                name = "RenderThread"
            },
        )
        debugMeta = DebugMeta().apply {
            images = listOf(
                DebugImage().apply {
                    type = "elf"
                    debugId = "0123456789abcdef"
                    codeFile = "/data/app/~~x==/com.ohmz.tday-y==/lib/arm64/libsentry.so"
                    debugFile = "/data/app/~~x==/com.ohmz.tday-y==/lib/arm64/libsentry.so"
                },
                DebugImage().apply {
                    type = "elf"
                    codeFile = "/system/lib64/libc.so"
                },
            )
        }
    }

    @Test
    fun `an ANR report carries neither the server host in a thread name nor the install path`() {
        val sent = options.beforeSend!!.execute(anrEvent(), Hint())!!
        val json = StringWriter().also { JsonSerializer(options).serialize(sent, it) }.toString()

        for (secret in listOf("alice-home", "alice", "tday.alice", "https://", "~~x==", "com.ohmz.tday-y==", "/data/app")) {
            assertFalse("the report still contains $secret:\n$json", json.contains(secret))
        }
        for (kept in listOf("\"main\"", "\"OkHttp\"", "RenderThread", "libsentry.so", "libc.so", "MainActivity.kt", "onResume")) {
            assertTrue("the report lost $kept:\n$json", json.contains(kept))
        }
    }

    @Test
    fun `what the developer needs to reproduce the failure is still there`() {
        val json = serializedReport()

        for (kept in listOf(
            "java.net.UnknownHostException",
            "java.net.ConnectException",
            "Unable to resolve host",
            "Pixel 8",
            "\"app_version\":\"0.8.0\"",
            "\"client\":\"android\"",
            "\"mode\":\"server\"",
            "\"tz_offset\":\"UTC-3\"",
            "\"locale_lang\":\"pt\"",
            "sync.replay",
            "/api/todo/:id",
            "\"status_code\":503",
        )) {
            assertTrue("the report lost $kept:\n$json", json.contains(kept))
        }
    }

    @Test
    fun `the breadcrumbs that record what the user touched never reach the report`() {
        val json = serializedReport()

        assertFalse(json.contains("ui.click"))
        assertFalse(json.contains("view.tag"))
        assertFalse(json.contains("from_arguments"))
    }

    private companion object {
        const val INSTALL_ID = "0f8fad5bd9cb469fa16570867728950e"
    }
}
