package com.ohmz.tday.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class PreferencesDto(
    val sortBy: String? = null,
    val groupBy: String? = null,
    val direction: String? = null,
)

/**
 * Preferences payload returned by GET/PATCH `/api/preferences`.
 *
 * CANONICAL SHAPE: the top-level [sortBy]/[groupBy]/[direction] fields. The
 * backend (PreferencesService) emits only these, and every client reads them:
 * Android via this model, web via the top-level keys, iOS via its flat
 * PreferencesResponse.
 *
 * [userPreferences] is a deprecated nested mirror that the backend never
 * populates (always null). It is retained only so existing deserializers don't
 * trip on the field; do not read from or write to it. Remove it once no
 * persisted/cached payloads reference it.
 */
@Serializable
data class PreferencesResponse(
    val sortBy: String? = null,
    val groupBy: String? = null,
    val direction: String? = null,
    val userPreferences: PreferencesDto? = null,
)
