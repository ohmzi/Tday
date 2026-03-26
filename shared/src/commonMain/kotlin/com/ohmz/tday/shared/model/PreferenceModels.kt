package com.ohmz.tday.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class PreferencesDto(
    val sortBy: String? = null,
    val groupBy: String? = null,
    val direction: String? = null,
)

@Serializable
data class PreferencesResponse(
    val sortBy: String? = null,
    val groupBy: String? = null,
    val direction: String? = null,
    val userPreferences: PreferencesDto? = null,
)
