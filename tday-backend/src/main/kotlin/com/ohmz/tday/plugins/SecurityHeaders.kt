package com.ohmz.tday.plugins

import com.ohmz.tday.config.AppConfig
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import org.koin.ktor.ext.inject

/** How the Content-Security-Policy is delivered. */
enum class CspMode { enforce, reportOnly, off }

fun parseCspMode(raw: String?): CspMode = when (raw?.trim()?.lowercase()) {
    null, "", "enforce" -> CspMode.enforce
    "report-only", "report_only", "reportonly" -> CspMode.reportOnly
    "off", "disabled", "none" -> CspMode.off
    else -> CspMode.enforce
}

/**
 * Extracts the ingest origin from a Sentry DSN (`https://<key>@<host>/<projectId>`).
 *
 * The browser SDK posts envelopes straight to that host, so it has to be allowed in `connect-src`.
 * The backend's own DSN is used as the default because in practice both SDKs report to the same
 * Sentry org; `CSP_CONNECT_EXTRA` overrides it when they differ.
 */
fun parseSentryIngestOrigin(dsn: String?): String? {
    val trimmed = dsn?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    return try {
        val uri = java.net.URI(trimmed)
        val host = uri.host ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.port > 0) "$scheme://$host:${uri.port}" else "$scheme://$host"
    } catch (_: Exception) {
        null
    }
}

/**
 * Builds the CSP for the SPA this backend serves.
 *
 * Two directives are weaker than they look, both forced by dependencies rather than chosen:
 *
 * - `style-src 'unsafe-inline'` — sonner, vaul, react-style-singleton (every Radix overlay) and
 *   next-themes all inject `<style>` elements with inline text at runtime. A nonce would *disable*
 *   `'unsafe-inline'` under CSP3 and break toasts, drawers, dialogs and popovers.
 * - `script-src 'self'` (no `'unsafe-inline'`) — this deliberately blocks next-themes' inline
 *   anti-FOUC script. Cost is a brief theme flash on first paint plus one console violation;
 *   theming still applies through its normal effects. A nonce cannot rescue it (next-themes blanks
 *   the nonce client-side) and a hash would silently break on any dependency bump.
 *
 * `ws:`/`wss:` are scheme sources because the SPA derives its socket URL from
 * `window.location.host`, and this header is built once while the origin varies (tunnel hostname
 * vs. a LAN address). Scheme sources permit WebSocket connections only, not HTTP exfiltration.
 */
fun buildCspHeader(connectExtra: List<String>): String {
    val connectSrc = buildList {
        add("'self'")
        add("ws:")
        add("wss:")
        // The in-app update check polls these on an interval; blocking them kills it silently.
        add("https://raw.githubusercontent.com")
        add("https://api.github.com")
        addAll(connectExtra)
    }.joinToString(" ")

    return listOf(
        "default-src 'self'",
        "base-uri 'self'",
        "object-src 'none'",
        "frame-src 'none'",
        "frame-ancestors 'none'",
        "form-action 'self'",
        "script-src 'self'",
        "style-src 'self' 'unsafe-inline'",
        "img-src 'self' data:",
        "font-src 'self'",
        "media-src 'self'",
        "manifest-src 'self'",
        "worker-src 'self'",
        "connect-src $connectSrc",
    ).joinToString("; ")
}

fun Application.configureSecurityHeaders() {
    val config by inject<AppConfig>()

    val connectExtra = config.cspConnectExtra.ifEmpty {
        listOfNotNull(parseSentryIngestOrigin(config.sentryDsn))
    }
    val cspMode = parseCspMode(config.cspMode)
    val cspHeaderName = when (cspMode) {
        CspMode.enforce -> "Content-Security-Policy"
        CspMode.reportOnly -> "Content-Security-Policy-Report-Only"
        CspMode.off -> null
    }
    val cspValue = buildCspHeader(connectExtra)

    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Referrer-Policy", "strict-origin-when-cross-origin")
        // Nothing here needs the browser's high-risk features; denying them costs nothing today
        // and means a future dependency cannot quietly start asking for them.
        header("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()")
        if (cspHeaderName != null) {
            header(cspHeaderName, cspValue)
        }
        if (config.isProduction) {
            header("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload")
        }
    }
}
