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
    val appVersion: String? = null,
    val encryptedCompatibility: String? = null,
)

/**
 * AI-summary capability advertised to every authenticated client (GET /app-settings).
 *
 * [aiSummaryConfigured] = the AI model was brought up via the `ai` docker-compose
 * profile (OLLAMA_URL is set). When false, clients always use the on-device/server
 * deterministic engine and hide the summary source label entirely.
 *
 * [aiSummaryHealthy] = the configured model is currently live. Advisory only — the
 * authoritative per-result engine is the `source` field on TodoSummaryResponse.
 */
@Serializable
data class AppSettingsResponse(
    val aiSummaryConfigured: Boolean = false,
    val aiSummaryHealthy: Boolean = false,
    val updatedAt: String? = null,
)
