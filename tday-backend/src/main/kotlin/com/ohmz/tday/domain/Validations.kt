package com.ohmz.tday.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ohmz.tday.models.request.*
import io.konform.validation.Validation
import io.konform.validation.constraints.maxLength
import io.konform.validation.constraints.minLength
import io.konform.validation.constraints.pattern

fun <T> Validation<T>.validateOrFail(value: T): Either<AppError, T> {
    val result = this(value)
    return if (result.isValid) value.right()
    else Either.Left(AppError.BadRequest(
        result.errors.joinToString("; ") { it.message }
    ))
}

inline fun <reified T : Enum<T>> validateRequiredEnumValue(value: String, field: String): Either<AppError, String> {
    val normalized = value.trim()
    return if (enumValues<T>().any { it.name == normalized }) {
        normalized.right()
    } else {
        AppError.BadRequest("$field is invalid", field).left()
    }
}

inline fun <reified T : Enum<T>> validateOptionalEnumValue(value: String?, field: String): Either<AppError, String?> {
    val normalized = value?.trim() ?: return null.right()
    return if (enumValues<T>().any { it.name == normalized }) {
        normalized.right()
    } else {
        AppError.BadRequest("$field is invalid", field).left()
    }
}

fun validateOptionalValue(value: String?, field: String, allowedValues: Set<String>): Either<AppError, String?> {
    val normalized = value?.trim() ?: return null.right()
    return if (normalized in allowedValues) {
        normalized.right()
    } else {
        AppError.BadRequest("$field is invalid", field).left()
    }
}

/** Longest outbound URL we will store (webhook + push endpoints). */
const val MAX_OUTBOUND_URL_LENGTH = 2048

/**
 * True when [host] is a literal IP address that must never be a webhook/push destination.
 *
 * The backend runs inside a Docker network on a home LAN, so an unvalidated outbound URL turns it
 * into a request forger with a trusted position: `http://database:5432`, `http://ollama:11434`,
 * `http://127.0.0.1:8080` (itself), the cloud metadata address, or any host on the owner's LAN.
 *
 * Hostnames are *not* resolved here — a DNS lookup at validation time can disagree with the lookup
 * the HTTP client makes moments later (DNS rebinding), so this deliberately only rejects what it
 * can decide from the string itself. Redirect following is disabled at the dispatch site, which is
 * the other half of the defense.
 */
fun isBlockedIpLiteral(host: String): Boolean {
    val cleaned = host.trim().trim('[', ']').substringBefore('%').lowercase()
    if (cleaned.isEmpty()) return true

    val v4 = cleaned.split('.')
    if (v4.size == 4 && v4.all { it.isNotEmpty() && it.all(Char::isDigit) }) {
        val octets = v4.mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return true
        val (a, b) = octets
        return when {
            a == 0 -> true                       // 0.0.0.0/8 "this network"
            a == 10 -> true                      // RFC1918
            a == 127 -> true                     // loopback
            a == 169 && b == 254 -> true         // link-local, incl. 169.254.169.254 metadata
            a == 172 && b in 16..31 -> true      // RFC1918
            a == 192 && b == 168 -> true         // RFC1918
            a == 100 && b in 64..127 -> true     // CGNAT (RFC6598)
            a >= 224 -> true                     // multicast + reserved
            else -> false
        }
    }

    // IPv6 (and IPv4-mapped forms like ::ffff:127.0.0.1).
    if (cleaned.contains(':')) {
        if (cleaned == "::" || cleaned == "::1") return true
        val mapped = cleaned.substringAfterLast(':')
        if (mapped.count { it == '.' } == 3) return isBlockedIpLiteral(mapped)
        return when {
            cleaned.startsWith("fe8") || cleaned.startsWith("fe9") ||
                cleaned.startsWith("fea") || cleaned.startsWith("feb") -> true  // fe80::/10
            cleaned.startsWith("fc") || cleaned.startsWith("fd") -> true        // fc00::/7 ULA
            cleaned.startsWith("ff") -> true                                    // multicast
            else -> false
        }
    }

    // Bare names that resolve to the loopback or to a sibling compose service.
    return cleaned == "localhost" || cleaned.endsWith(".localhost")
}

/**
 * Validates a user-supplied URL the *server* will later call. Rejects non-http(s) schemes,
 * embedded credentials, and destinations inside the host's own network.
 */
fun validateOutboundUrl(raw: String, field: String): Either<AppError, java.net.URI> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return AppError.BadRequest("$field is required", field).left()
    if (trimmed.length > MAX_OUTBOUND_URL_LENGTH) {
        return AppError.BadRequest("$field is too long", field).left()
    }

    val uri = try {
        java.net.URI(trimmed)
    } catch (_: Exception) {
        return AppError.BadRequest("$field must be a valid URL", field).left()
    }

    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") {
        return AppError.BadRequest("$field must be an http(s) URL", field).left()
    }
    if (uri.userInfo != null) {
        return AppError.BadRequest("$field must not contain credentials", field).left()
    }

    val host = uri.host ?: return AppError.BadRequest("$field must include a host", field).left()
    if (isBlockedIpLiteral(host)) {
        return AppError.BadRequest("$field must not point at a private or local address", field).left()
    }
    // A single-label host ("database", "ollama") can only resolve inside the container network.
    if (!host.contains('.')) {
        return AppError.BadRequest("$field must use a fully qualified public hostname", field).left()
    }

    return uri.right()
}

val validateCreateTodo = Validation<TodoCreateRequest> {
    TodoCreateRequest::title {
        minLength(1) hint "Title is required"
        maxLength(500) hint "Title too long"
    }
}

val validatePatchTodo = Validation<TodoPatchRequest> {
    TodoPatchRequest::id {
        minLength(1) hint "Todo id is required"
    }
}

val validateCreateFloater = Validation<FloaterCreateRequest> {
    FloaterCreateRequest::title {
        minLength(1) hint "Title is required"
        maxLength(500) hint "Title too long"
    }
}

val validateCreateList = Validation<ListCreateRequest> {
    ListCreateRequest::name {
        minLength(1) hint "Name is required"
        maxLength(255) hint "Name too long"
    }
}

val validatePatchList = Validation<ListPatchRequest> {
    ListPatchRequest::id {
        minLength(1) hint "List id is required"
    }
}

val validateCreateFloaterList = Validation<FloaterListCreateRequest> {
    FloaterListCreateRequest::name {
        minLength(1) hint "Name is required"
        maxLength(255) hint "Name too long"
    }
}

val validatePatchFloaterList = Validation<FloaterListPatchRequest> {
    FloaterListPatchRequest::id {
        minLength(1) hint "List id is required"
    }
}

val validateRegister = Validation<RegisterRequest> {
    RegisterRequest::fname {
        minLength(2) hint "First name must be at least two characters"
    }
    RegisterRequest::username {
        pattern("^[a-z0-9](?:[a-z0-9._-]{1,28}[a-z0-9])$") hint
            "Username must be 3-30 characters using letters, numbers, . _ - and start/end alphanumeric"
    }
    RegisterRequest::password {
        minLength(8) hint "Password must be at least 8 characters"
    }
}
