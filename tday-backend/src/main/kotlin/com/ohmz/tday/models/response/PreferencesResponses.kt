package com.ohmz.tday.models.response

import kotlinx.serialization.Serializable

@Serializable
data class PreferencesResponse(
    val sortBy: String?,
    val groupBy: String?,
    val direction: String?,
)
