package com.ohmz.tday.models.request

import kotlinx.serialization.Serializable

@Serializable
data class PushSubscribeRequest(
    val endpoint: String,
    val p256dh: String,
    val auth: String,
)

@Serializable
data class PushUnsubscribeRequest(
    val endpoint: String,
)
