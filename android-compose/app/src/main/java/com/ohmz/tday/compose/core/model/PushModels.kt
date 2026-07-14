package com.ohmz.tday.compose.core.model

import kotlinx.serialization.Serializable

/**
 * Registers a push endpoint with the backend. For UnifiedPush the encryption keys are
 * omitted (the server POSTs plaintext to the distributor endpoint).
 */
@Serializable
data class PushSubscribeRequest(
    val endpoint: String,
    val transport: String = "unifiedpush",
    val p256dh: String = "",
    val auth: String = "",
)

@Serializable
data class PushUnsubscribeRequest(
    val endpoint: String,
)
