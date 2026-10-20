package com.ohmz.tday.shared.model

import kotlinx.serialization.Serializable

/**
 * One call that grounds an external integration — `GET /api/integration/context`.
 *
 * External clients (dashboards, AI assistants over MCP) otherwise have to stitch
 * this together from `/api/auth/session`, `/api/list` and `/api/floaterList`, and
 * still can't discover the scope of the key they are holding: the only signal today
 * is a 403 `api_key_read_only` after a write has already been attempted.
 */
@Serializable
data class IntegrationContextResponse(
    /** Null when the caller authenticated with a session rather than an API key. */
    val apiKey: IntegrationApiKeyDto? = null,
    val user: IntegrationUserDto,
    /** Server "now" in the API's wire format: a UTC wall clock with no offset. */
    val serverTime: String,
    val capabilities: IntegrationCapabilitiesDto,
    /** Lists that group scheduled tasks. */
    val lists: List<ListDto> = emptyList(),
    /** Lists that group undated Anytime tasks. A separate namespace from [lists]. */
    val anytimeLists: List<FloaterListDto> = emptyList(),
)

@Serializable
data class IntegrationApiKeyDto(
    /** `READ` or `FULL`. */
    val scope: String,
    val label: String? = null,
    val keyPreview: String? = null,
)

@Serializable
data class IntegrationUserDto(
    val id: String,
    val username: String? = null,
    val name: String? = null,
    val timeZone: String? = null,
)

@Serializable
data class IntegrationCapabilitiesDto(
    /** False only for a READ-scoped API key. Session callers always write. */
    val canWrite: Boolean,
)
