package com.ohmz.tday.observability

import io.ktor.utils.io.ClosedByteChannelException
import io.sentry.Breadcrumb
import io.sentry.SentryBaseEvent
import io.sentry.SentryEvent
import io.sentry.protocol.Request
import io.sentry.protocol.SentryTransaction
import java.io.IOException
import java.nio.channels.ClosedChannelException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Pure rewrites applied to everything the backend hands to Sentry, so the rules can be tested
 * without the SDK running.
 *
 * Exception and log text is the weak point: the Postgres driver quotes the offending key and
 * the whole failing row into its messages, so a unique violation on `User.username` arrives with
 * the username in it. Free text is therefore redacted to a fixed set of placeholders, keeping the
 * exception type and the place in the code, which is what a bug report needs.
 */
object TelemetryScrubber {
    const val MAX_TEXT_LENGTH = 300

    // Several rules below scan to the end of the line from every candidate start, which is
    // quadratic on a pathological message. Nothing past this point could survive the final
    // truncation anyway, so the redaction rules never see more than this.
    private const val MAX_SCANNED_LENGTH = 4096

    // Categories the app itself writes through TdayObservability.addBreadcrumb. Anything else is a
    // log line (category = logger name, message = whatever was formatted into it) or an SDK
    // default, and those carry user ids, request paths and push endpoints.
    private val allowedBreadcrumbCategories = setOf("tday", "http", "security")

    private const val MAX_CAUSE_DEPTH = 10
    private val abortMessages = listOf("broken pipe", "connection reset", "connection was aborted")

    // Order matters: connection strings and URLs go first so their embedded hosts, keys and
    // credentials are consumed whole instead of being picked apart by the narrower rules below.
    private val textRules: List<Pair<Regex, String>> = listOf(
        Regex("""jdbc:[A-Za-z0-9]+:[^\s"'<>)\]]+""") to "[jdbc]",
        Regex("""\b[A-Za-z][A-Za-z0-9+.-]*://[^\s"'<>)\]]+""") to "[url]",
        // Parsers echo the offending input back: kotlinx.serialization appends the JSON it was
        // decoding (all of it, or a window around the offset) after "JSON input:", java.time and
        // the number parsers quote the text they could not read.
        Regex("""(JSON input:\s*)[\s\S]*""") to "$1[redacted]",
        Regex("""Text '.*' could not be parsed""") to "Text '[redacted]' could not be parsed",
        Regex("""(For input string:\s*).*""") to "$1[redacted]",
        Regex("""Key \(.*\)=\(.*\)""") to "Key [redacted]",
        Regex("""Failing row contains \(.*\)""") to "Failing row contains [redacted]",
        Regex("""(invalid input (?:syntax|value) for [^:\n]*:\s*)"[^"\n]*"""") to "$1\"[redacted]\"",
        Regex("""[\w.%+-]+@[\w-]+(?:\.[\w-]+)*\.[A-Za-z]{2,}""") to "[email]",
        Regex("""\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b""") to "[uuid]",
        // CuidGenerator: "c" + 24 lowercase base-36 characters, always with a digit in them.
        Regex("""\bc(?=[0-9a-z]*\d)[0-9a-z]{24}\b""") to "[id]",
        Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""") to "[ip]",
        Regex("""(?<![0-9A-Za-z:])(?:[0-9A-Fa-f]{0,4}:){2,7}[0-9A-Fa-f]{0,4}(?![0-9A-Za-z:])""") to "[ip]",
        // host:port. File names such as "TodoService.kt:42" are positions, not hosts.
        Regex(
            """\b(?:[A-Za-z0-9-]+\.)+(?!(?:kt|kts|java|class|ts|tsx|js|mjs)\b)[A-Za-z]{2,}:\d{2,5}\b|\blocalhost:\d{2,5}\b""",
        ) to "[host]",
        Regex("""\b(?=[A-Za-z0-9_-]*\d)[A-Za-z0-9_-]{32,}\b""") to "[token]",
        Regex("""\d{6,}""") to "[number]",
    )

    fun scrubText(raw: String): String {
        val redacted = textRules.fold(raw.take(MAX_SCANNED_LENGTH)) { text, (pattern, replacement) ->
            pattern.replace(text, replacement)
        }
        return if (redacted.length > MAX_TEXT_LENGTH) redacted.take(MAX_TEXT_LENGTH) + "…" else redacted
    }

    fun scrubEvent(event: SentryEvent): SentryEvent {
        scrubUserAndRequest(event)
        event.message?.let { message ->
            message.message = message.message?.let(::scrubText)
            message.formatted = message.formatted?.let(::scrubText)
            // Parameters are the unformatted values (ids, emails) the message was built from.
            message.params = null
        }
        event.exceptions?.forEach { exception -> exception.value = exception.value?.let(::scrubText) }
        return event
    }

    fun scrubTransaction(transaction: SentryTransaction): SentryTransaction {
        scrubUserAndRequest(transaction)
        return transaction
    }

    /** Null for a breadcrumb that must not be sent. */
    fun scrubBreadcrumb(breadcrumb: Breadcrumb): Breadcrumb? =
        breadcrumb.takeIf { it.category in allowedBreadcrumbCategories }

    /**
     * True for a failure that only means the caller went away: a cancelled coroutine, or a socket
     * the client closed under us. Neither is a server fault, and each would otherwise fire once
     * per dropped connection.
     */
    fun isClientAbort(throwable: Throwable?): Boolean =
        generateSequence(throwable) { it.cause }.take(MAX_CAUSE_DEPTH).any(::isAbort)

    private fun isAbort(throwable: Throwable): Boolean = when (throwable) {
        is CancellationException, is ClosedChannelException, is ClosedByteChannelException -> true
        is IOException -> throwable.message.orEmpty().lowercase().let { message -> abortMessages.any { it in message } }
        else -> false
    }

    private fun scrubUserAndRequest(event: SentryBaseEvent) {
        // The User can be the scope's own instance, so the address is cleared on it before the
        // event lets go of it, as well as dropping it from this event.
        event.user?.ipAddress = null
        event.user = null
        // A fresh Request, not a cleaned one: headers, cookies, env, body and query string are all
        // gone by construction, and a field added to Request later cannot leak through.
        event.request = event.request?.let { original ->
            Request().apply {
                method = original.method
                url = original.url?.let(TdayObservability::sanitizePath)
            }
        }
    }
}
