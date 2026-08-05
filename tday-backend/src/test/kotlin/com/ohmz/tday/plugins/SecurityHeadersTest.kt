package com.ohmz.tday.plugins

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecurityHeadersTest {

    private fun directive(policy: String, name: String): String? =
        policy.split(";").map(String::trim).firstOrNull { it == name || it.startsWith("$name ") }

    @Test
    fun `script-src does not allow inline`() {
        // The whole point of the policy: an XSS in the SPA must not be able to execute.
        // Allowing 'unsafe-inline' here would make the rest of it decorative.
        val scriptSrc = directive(buildCspHeader(emptyList()), "script-src")
        assertEquals("script-src 'self'", scriptSrc)
    }

    @Test
    fun `style-src allows inline because runtime style injection requires it`() {
        // sonner, vaul, react-style-singleton and next-themes all inject <style> at runtime.
        val styleSrc = directive(buildCspHeader(emptyList()), "style-src")
        assertTrue(styleSrc!!.contains("'unsafe-inline'"))
    }

    @Test
    fun `connect-src permits same-origin websockets and the update check`() {
        val connectSrc = directive(buildCspHeader(emptyList()), "connect-src")!!
        assertTrue(connectSrc.contains("'self'"))
        assertTrue(connectSrc.contains("ws:"))
        assertTrue(connectSrc.contains("wss:"))
        assertTrue(connectSrc.contains("https://raw.githubusercontent.com"))
        assertTrue(connectSrc.contains("https://api.github.com"))
    }

    @Test
    fun `connect-src includes configured extra origins`() {
        val connectSrc = directive(buildCspHeader(listOf("https://o1.ingest.sentry.io")), "connect-src")!!
        assertTrue(connectSrc.contains("https://o1.ingest.sentry.io"))
    }

    @Test
    fun `framing and object embedding are denied outright`() {
        val policy = buildCspHeader(emptyList())
        assertEquals("frame-ancestors 'none'", directive(policy, "frame-ancestors"))
        assertEquals("object-src 'none'", directive(policy, "object-src"))
        assertEquals("base-uri 'self'", directive(policy, "base-uri"))
        assertEquals("form-action 'self'", directive(policy, "form-action"))
    }

    @Test
    fun `assets the app actually loads are permitted`() {
        val policy = buildCspHeader(emptyList())
        // Self-hosted font, notification sounds, the service worker and the PWA manifest.
        assertEquals("font-src 'self'", directive(policy, "font-src"))
        assertEquals("media-src 'self'", directive(policy, "media-src"))
        assertEquals("worker-src 'self'", directive(policy, "worker-src"))
        assertEquals("manifest-src 'self'", directive(policy, "manifest-src"))
    }

    @Test
    fun `policy never contains unsafe-eval`() {
        assertFalse(buildCspHeader(emptyList()).contains("unsafe-eval"))
    }

    @Test
    fun `sentry ingest origin is parsed from a dsn`() {
        assertEquals(
            "https://o4507.ingest.us.sentry.io",
            parseSentryIngestOrigin("https://abc123@o4507.ingest.us.sentry.io/1234567"),
        )
    }

    @Test
    fun `sentry ingest origin keeps a non-default port`() {
        assertEquals(
            "https://sentry.example.com:9000",
            parseSentryIngestOrigin("https://key@sentry.example.com:9000/2"),
        )
    }

    @Test
    fun `blank or malformed dsn yields no origin`() {
        assertNull(parseSentryIngestOrigin(null))
        assertNull(parseSentryIngestOrigin(""))
        assertNull(parseSentryIngestOrigin("   "))
        assertNull(parseSentryIngestOrigin("not a dsn"))
        assertNull(parseSentryIngestOrigin("ftp://key@example.com/1"))
    }

    @Test
    fun `csp mode defaults to enforce and recognises the rollback values`() {
        assertEquals(CspMode.enforce, parseCspMode(null))
        assertEquals(CspMode.enforce, parseCspMode(""))
        assertEquals(CspMode.enforce, parseCspMode("enforce"))
        assertEquals(CspMode.reportOnly, parseCspMode("report-only"))
        assertEquals(CspMode.reportOnly, parseCspMode("REPORT_ONLY"))
        assertEquals(CspMode.off, parseCspMode("off"))
        // An unrecognised value must fail safe (enforcing), not silently disable the policy.
        assertEquals(CspMode.enforce, parseCspMode("nonsense"))
    }
}
