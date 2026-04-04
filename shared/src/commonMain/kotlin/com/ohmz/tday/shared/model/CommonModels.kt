package com.ohmz.tday.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class MessageResponse(
    val message: String? = null,
)

@Serializable
data class MobileProbeResponse(
    val service: String,
    val probe: String? = null,
    val version: String,
    val serverTime: String,
    val encryptedCompatibility: String? = null,
)

@Serializable
data class AppSettingsResponse(
    val aiSummaryEnabled: Boolean = true,
    val updatedAt: String? = null,
)

@Serializable
data class AdminSettingsResponse(
    val aiSummaryEnabled: Boolean = true,
    val updatedAt: String? = null,
    val validationError: String? = null,
)

@Serializable
data class UpdateAdminSettingsRequest(
    val aiSummaryEnabled: Boolean,
)
