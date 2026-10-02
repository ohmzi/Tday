package com.ohmz.tday.compose.core.observability

import io.sentry.Breadcrumb
import io.sentry.SentryEvent
import io.sentry.protocol.App
import io.sentry.protocol.Device
import io.sentry.protocol.Message
import io.sentry.protocol.SentryException
import io.sentry.protocol.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.TimeZone

class TelemetryScrubberTest {

    // ── Free text ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `redacts the host Android names when a lookup fails`() {
        val scrubbed = TelemetryScrubber.scrubText(
            "Unable to resolve host \"tasks.my-home.example\": No address associated with hostname",
        )

        assertFalse(scrubbed.contains("my-home"))
        assertTrue(scrubbed.contains("Unable to resolve host"))
    }

    @Test
    fun `redacts a single label host that no top level domain would give away`() {
        val scrubbed = TelemetryScrubber.scrubText("Unable to resolve host \"nas\": no address")

        assertFalse(scrubbed.contains("nas\""))
    }

    @Test
    fun `redacts the address in a connect failure, host and ip alike`() {
        val scrubbed = TelemetryScrubber.scrubText(
            "Failed to connect to nas.local/192.168.1.20:8443",
        )

        assertFalse(scrubbed.contains("nas.local"))
        assertFalse(scrubbed.contains("192.168.1.20"))
        assertTrue(scrubbed.startsWith("Failed to connect to"))
    }

    @Test
    fun `redacts the host an UnknownHostException carries`() {
        val scrubbed = TelemetryScrubber.scrubText("java.net.UnknownHostException: tday.mydomain.com")

        assertFalse(scrubbed.contains("mydomain"))
        assertTrue(scrubbed.contains("UnknownHostException"))
    }

    @Test
    fun `redacts the host a failed certificate check carries`() {
        val scrubbed = TelemetryScrubber.scrubText(
            "Hostname tday.home.arpa not verified:\n certificate: sha256/q3F0Zk1uVd8s9T0m\n " +
                "DN: CN=tday.home.arpa,O=Alex\n subjectAltNames: [tday.home.arpa]",
        )

        assertFalse(scrubbed.contains("home.arpa"))
        assertFalse(scrubbed.contains("q3F0Zk1uVd8s9T0m"))
        assertFalse(scrubbed.contains("Alex"))
        assertTrue(scrubbed.contains("not verified"))
    }

    @Test
    fun `redacts urls of every scheme including the app's own deep links`() {
        val scrubbed = TelemetryScrubber.scrubText(
            "GET https://tday.mydomain.com/api/todo/123?token=abc failed; " +
                "no route for tday://todos/list/9d2f/Groceries; ws://10.0.0.5:3000/ws",
        )

        assertFalse(scrubbed.contains("mydomain"))
        assertFalse(scrubbed.contains("token=abc"))
        assertFalse(scrubbed.contains("Groceries"))
        assertFalse(scrubbed.contains("10.0.0.5"))
    }

    @Test
    fun `redacts jdbc connection strings`() {
        val scrubbed = TelemetryScrubber.scrubText("Failed for jdbc:postgresql:" + "//db.internal:5432/tday")

        assertFalse(scrubbed.contains("db.internal"))
        assertFalse(scrubbed.contains("5432"))
    }

    @Test
    fun `redacts the values a unique violation quotes`() {
        val scrubbed = TelemetryScrubber.scrubText("Key (email)=(someone@private.example) already exists")

        assertFalse(scrubbed.contains("someone"))
        assertFalse(scrubbed.contains("private.example"))
        assertTrue(scrubbed.contains("already exists"))
    }

    @Test
    fun `redacts emails`() {
        assertEquals("sent to [email]", TelemetryScrubber.scrubText("sent to alex@example.com"))
    }

    @Test
    fun `redacts ipv4 with or without a port, and ipv6 in both spellings`() {
        for (address in listOf(
            "10.0.0.5",
            "192.168.1.20:8080",
            "fe80::1ff:fe23:4567:890a",
            "2001:db8:85a3:0:0:8a2e:370:7334",
            "::1",
        )) {
            val scrubbed = TelemetryScrubber.scrubText("peer $address closed")
            assertEquals("peer [ip] closed", scrubbed)
        }
    }

    @Test
    fun `redacts uuids in both spellings and cuids`() {
        for (id in listOf(
            "0f8fad5b-d9cb-469f-a165-70867728950e",
            "0f8fad5bd9cb469fa16570867728950e",
            "cjld2cjxh0000qzrmn831i7rn",
        )) {
            assertEquals("list [id] missing", TelemetryScrubber.scrubText("list $id missing"))
        }
    }

    @Test
    fun `redacts long digit runs but keeps status codes and ports`() {
        assertEquals("account [number]", TelemetryScrubber.scrubText("account 123456789012"))
        assertEquals("HTTP 503 on port 8080", TelemetryScrubber.scrubText("HTTP 503 on port 8080"))
    }

    @Test
    fun `leaves the technical text a developer needs untouched`() {
        for (text in listOf(
            "Cannot cast kotlin.String to java.lang.Integer",
            "NullPointerException at TodoService.kt:42",
            "dlopen failed: library libsentry.so not found",
            "see README.md for details",
            "Room cannot verify the data integrity",
            "Unresolved reference: Foo.size",
        )) {
            assertEquals(text, TelemetryScrubber.scrubText(text))
        }
    }

    @Test
    fun `truncates long messages`() {
        val scrubbed = TelemetryScrubber.scrubText("x ".repeat(500))

        assertTrue(scrubbed.length <= 301)
        assertTrue(scrubbed.endsWith("…"))
    }

    @Test
    fun `redacts the input a kotlinx serialization decoding error quotes`() {
        val short = TelemetryScrubber.scrubText(
            "Unexpected JSON token at offset 12: Expected '}'\nJSON input: {\"title\":\"Call Dr Patel about results\"}",
        )
        val windowed = TelemetryScrubber.scrubText(
            "Unexpected JSON token at offset 400: Expected '}'\nJSON input: .....\"title\":\"Buy milk for Bob\"...",
        )

        for (scrubbed in listOf(short, windowed)) {
            assertFalse(scrubbed, scrubbed.contains("Patel"))
            assertFalse(scrubbed, scrubbed.contains("Bob"))
            assertFalse(scrubbed, scrubbed.contains("milk"))
            assertTrue(scrubbed, scrubbed.contains("Unexpected JSON token at offset"))
            assertTrue(scrubbed, scrubbed.contains("JSON input: [redacted]"))
        }
    }

    @Test
    fun `redacts the text a date parse error and a number format error echo`() {
        val date = TelemetryScrubber.scrubText("Text 'Buy Bob's milk tomorrow' could not be parsed at index 0")
        val number = TelemetryScrubber.scrubText("java.lang.NumberFormatException: For input string: \"call mum\"")

        assertFalse(date, date.contains("Bob"))
        assertFalse(date, date.contains("milk"))
        assertTrue(date, date.endsWith("could not be parsed at index 0"))
        assertFalse(number, number.contains("mum"))
        assertTrue(number, number.contains("NumberFormatException: For input string:"))
    }

    @Test(timeout = 5_000)
    fun `a hostile message is capped before the rules run so a crash cannot stall the thread`() {
        val blob = "a".repeat(100_000)

        val scrubbed = TelemetryScrubber.scrubText(blob)

        assertTrue(scrubbed.length <= 301)
    }

    @Test
    fun `still redacts what sits inside the scanned window of an oversized message`() {
        val scrubbed = TelemetryScrubber.scrubText("owner alex@example.com " + "b".repeat(50_000))

        assertFalse(scrubbed.contains("alex"))
        assertTrue(scrubbed.startsWith("owner [email]"))
    }

    @Test
    fun `redacts a bare host under the less common suffixes a self hoster picks`() {
        for (host in listOf(
            "tday.alice.page", "tday.alice.it", "tday.alice.ai", "tday.my-home.me", "tday.alice.app",
            "tday.alice.dev", "tday.alice.io", "tday.alice.co", "tday.alice.co.uk", "tday.alice.de",
            "tday.alice.fr", "tday.alice.es", "tday.alice.nl", "tday.alice.se", "tday.alice.ch",
            "tday.alice.at", "tday.alice.ca", "tday.alice.com.au", "tday.alice.nz", "tday.alice.jp",
            "tday.alice.us", "tday.alice.eu", "tday.alice.xyz", "tday.alice.cloud", "tday.alice.tech",
            "tday.alice.online", "tday.alice.site", "tday.alice.link", "tday.alice.lan",
            "tday.alice.home", "tday.alice.internal", "tday.alice.local", "tday.alice.localdomain",
            "tday.tail1234.ts.net",
        )) {
            val scrubbed = TelemetryScrubber.scrubText("CN=$host closed")

            assertFalse("$host survived as: $scrubbed", scrubbed.contains("alice") || scrubbed.contains("tail1234"))
        }
    }

    @Test
    fun `keeps stack frame and file names that only look like hosts`() {
        for (text in listOf(
            "at com.ohmz.tday.compose.core.TdayApp.onCreate(TdayApp.kt:42)",
            "at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:100)",
            "dlopen failed: library libsentry-android-ndk.so not found",
            "see build.gradle.kts and libs.versions.toml and run.sh and script.py",
            "Foo.id Foo.is Foo.to Foo.md",
        )) {
            assertEquals(text, TelemetryScrubber.scrubText(text))
        }
    }

    // ── Events ──────────────────────────────────────────────────────────────────────────────

    private fun event(): SentryEvent = SentryEvent().apply {
        user = User().apply {
            id = INSTALL_ID
            ipAddress = "{{auto}}"
            email = "alex@example.com"
        }
        serverName = "alex-pixel"
        contexts.setDevice(
            Device().apply {
                id = INSTALL_ID
                name = "Alex's Pixel"
                locale = "pt_BR"
                timezone = TimeZone.getTimeZone("America/Sao_Paulo")
                bootTime = Date(1_700_000_000_000L)
                manufacturer = "Google"
                model = "Pixel 8"
                archs = arrayOf("arm64-v8a")
                memorySize = 8_000_000_000L
                batteryLevel = 71f
                isOnline = true
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
                type = "UnknownHostException"
                value = "Unable to resolve host \"tasks.my-home.example\""
            },
            SentryException().apply {
                type = "ConnectException"
                value = "Failed to connect to /192.168.1.20:8443"
            },
        )
        message = Message().apply {
            message = "sync failed for alex@example.com"
            formatted = "sync failed for alex@example.com"
        }
        setExtra("detail", "list 0f8fad5b-d9cb-469f-a165-70867728950e")
        setExtra("count", 3)
    }

    private val tags = mapOf(
        "client" to "android",
        "app_version" to "0.8.0",
        "mode" to "server",
        "tz_offset" to "UTC-3",
        "locale_lang" to "pt",
    )

    @Test
    fun `nulls the user, which takes the install id and the ip address with it`() {
        val scrubbed = TelemetryScrubber.scrub(event(), tags)

        assertNull(scrubbed.user)
    }

    @Test
    fun `strips the install id, name, locale, zone and boot time from the device and keeps the rest`() {
        val device = TelemetryScrubber.scrub(event(), tags).contexts.device!!

        assertNull(device.id)
        assertNull(device.name)
        assertNull(device.locale)
        assertNull(device.timezone)
        assertNull(device.bootTime)
        assertEquals("Pixel 8", device.model)
        assertEquals("Google", device.manufacturer)
        assertEquals(8_000_000_000L, device.memorySize)
        assertEquals(71f, device.batteryLevel)
        assertEquals(true, device.isOnline)
    }

    @Test
    fun `drops the permission list and keeps the app version`() {
        val app = TelemetryScrubber.scrub(event(), tags).contexts.app!!

        assertNull(app.permissions)
        assertEquals("0.8.0", app.appVersion)
    }

    @Test
    fun `drops the server name and any request context`() {
        val scrubbed = TelemetryScrubber.scrub(event(), tags)

        assertNull(scrubbed.serverName)
        assertNull(scrubbed.request)
    }

    @Test
    fun `scrubs every exception and the message but keeps the exception types`() {
        val scrubbed = TelemetryScrubber.scrub(event(), tags)

        assertEquals(listOf("UnknownHostException", "ConnectException"), scrubbed.exceptions!!.map { it.type })
        scrubbed.exceptions!!.forEach { exception ->
            assertFalse(exception.value!!.contains("my-home"))
            assertFalse(exception.value!!.contains("192.168.1.20"))
        }
        assertEquals("sync failed for [email]", scrubbed.message!!.message)
        assertEquals("sync failed for [email]", scrubbed.message!!.formatted)
    }

    @Test
    fun `scrubs text extras and leaves numbers alone`() {
        val scrubbed = TelemetryScrubber.scrub(event(), tags)

        assertEquals("list [id]", scrubbed.getExtra("detail"))
        assertEquals(3, scrubbed.getExtra("count"))
    }

    @Test
    fun `stamps the five tags every report carries`() {
        val scrubbed = TelemetryScrubber.scrub(event(), tags)

        assertEquals("android", scrubbed.getTag("client"))
        assertEquals("0.8.0", scrubbed.getTag("app_version"))
        assertEquals("server", scrubbed.getTag("mode"))
        assertEquals("UTC-3", scrubbed.getTag("tz_offset"))
        assertEquals("pt", scrubbed.getTag("locale_lang"))
    }

    @Test
    fun `does not overwrite a tag the report already carries`() {
        // A replayed ANR comes from an earlier process; what it recorded then beats what is true now.
        val replayed = event().apply { setTag("mode", "local") }

        assertEquals("local", TelemetryScrubber.scrub(replayed, tags).getTag("mode"))
    }

    @Test
    fun `leaves a tag out when its value is not known yet`() {
        val scrubbed = TelemetryScrubber.scrub(event(), tags - "mode")

        assertNull(scrubbed.getTag("mode"))
    }

    // ── Breadcrumbs ─────────────────────────────────────────────────────────────────────────

    private fun crumb(category: String, message: String? = null, vararg data: Pair<String, Any?>) =
        Breadcrumb().apply {
            this.category = category
            this.message = message
            data.forEach { (key, value) -> setData(key, value) }
        }

    @Test
    fun `drops every category that is not on the allow list`() {
        for (category in listOf(
            "ui.click", "ui.scroll", "console", "device.event", "system", "ui.lifecycle", "query",
            "Timber", "logcat", "something.new",
        )) {
            assertNull(category, TelemetryScrubber.scrubBreadcrumb(crumb(category, "x")))
        }
    }

    @Test
    fun `keeps lifecycle and connectivity breadcrumbs`() {
        for (category in listOf("app.lifecycle", "network.event")) {
            assertNotNull(category, TelemetryScrubber.scrubBreadcrumb(crumb(category, "ok")))
        }
    }

    @Test
    fun `reduces an http breadcrumb to method, status and a route template`() {
        val scrubbed = TelemetryScrubber.scrubBreadcrumb(
            crumb(
                "http",
                null,
                "url" to "https://tday.mydomain.com/api/todo/cjld2cjxh0000qzrmn831i7rn?search=milk",
                "method" to "PATCH",
                "status_code" to 200,
                "host" to "tday.mydomain.com",
                "http.query" to "search=milk",
                "http.fragment" to "top",
                "request_body_size" to 120,
            ),
        )!!

        assertEquals(setOf("method", "status_code", "route"), scrubbed.data.keys)
        assertEquals("/api/todo/:id", scrubbed.data["route"])
        assertEquals("PATCH", scrubbed.data["method"])
        assertEquals(200, scrubbed.data["status_code"])
    }

    @Test
    fun `reduces a navigation breadcrumb to its route templates and drops the arguments`() {
        val scrubbed = TelemetryScrubber.scrubBreadcrumb(
            crumb(
                "navigation",
                null,
                "from" to "todos/list/{listId}/{listName}",
                "to" to "calendar",
                "from_arguments" to "{listId=9d2f, listName=Groceries}",
                "to_arguments" to "{}",
                "arguments" to "{listName=Groceries}",
                "state" to "navigated",
            ),
        )!!

        assertEquals(setOf("from", "to"), scrubbed.data.keys)
        assertFalse(scrubbed.data.values.any { it.toString().contains("Groceries") })
    }

    @Test
    fun `scrubs the message of a breadcrumb it keeps`() {
        val scrubbed = TelemetryScrubber.scrubBreadcrumb(crumb("error", "failed for alex@example.com"))!!

        assertEquals("failed for [email]", scrubbed.message)
    }

    @Test
    fun `passes the app's own structural breadcrumbs through`() {
        val scrubbed = TelemetryScrubber.scrubBreadcrumb(
            crumb("tday", "sync.replay", "pending" to 3, "mode" to "server"),
        )!!

        assertEquals("sync.replay", scrubbed.message)
        assertEquals(3, scrubbed.data["pending"])
    }

    private companion object {
        const val INSTALL_ID = "0f8fad5bd9cb469fa16570867728950e"
    }
}
