package com.ohmz.tday.models.response

import kotlinx.serialization.Serializable

/**
 * An active adaptive-abuse block, as shown on the admin panel.
 *
 * [subject] is a short prefix of the HMAC'd identity — enough to tell one source from another
 * across rows, useless for recovering the address it was derived from.
 */
@Serializable
data class AbuseBlockResponse(
    val id: String,
    val subject: String,
    val scope: String,
    val reason: String?,
    val strikes: Int,
    val blockedUntil: String?,
    val createdAt: String,
    val updatedAt: String,
)

/** One dispatched security alert. [suppressedCount] is how many events it folded in. */
@Serializable
data class SecurityAlertResponse(
    val id: String,
    val type: String,
    val detail: String,
    val suppressedCount: Int,
    val pushed: Boolean,
    val createdAt: String,
)
