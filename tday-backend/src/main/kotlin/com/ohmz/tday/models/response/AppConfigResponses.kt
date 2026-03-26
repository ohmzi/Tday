package com.ohmz.tday.models.response

import kotlinx.serialization.Serializable

@Serializable
data class AppConfigResponse(
    val aiSummaryEnabled: Boolean,
    val updatedAt: String,
)
