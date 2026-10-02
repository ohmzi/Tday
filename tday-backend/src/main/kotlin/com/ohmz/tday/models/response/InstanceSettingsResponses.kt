package com.ohmz.tday.models.response

import kotlinx.serialization.Serializable

/**
 * The admin's view of whether this server reports its own errors to Sentry.
 *
 * [dsnConfigured] says whether the operator set `SENTRY_DSN` at all; without it the switch has no
 * effect, so the web hides the row. [updatedAt] is ISO-8601 UTC, null until an admin first chooses.
 */
@Serializable
data class ServerTelemetryResponse(
    val dsnConfigured: Boolean,
    val enabled: Boolean,
    val updatedAt: String?,
)
