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
 * WARNING: the same three fields appear at the top level AND nested under
 * [userPreferences], with no enforced precedence. Today the backend
 * (PreferencesService) populates ONLY the top-level fields and leaves
 * [userPreferences] null, while the web client reads `data.userPreferences` —
 * so the two disagree. Pick one shape as canonical and make every client read it
 * (and the server emit it). Until then, prefer the top-level fields and treat
 * [userPreferences] as a deprecated mirror.
 */
@Serializable
data class PreferencesResponse(
    val sortBy: String? = null,
    val groupBy: String? = null,
    val direction: String? = null,
    val userPreferences: PreferencesDto? = null,
)
