package com.ohmz.tday.models.request

import kotlinx.serialization.Serializable

@Serializable
data class PushSubscribeRequest(
    val endpoint: String,
    // Web Push carries encryption keys; UnifiedPush omits them (endpoint-only).
    val p256dh: String = "",
    val auth: String = "",
    val transport: String = "webpush",
)

@Serializable
data class PushUnsubscribeRequest(
    val endpoint: String,
)
