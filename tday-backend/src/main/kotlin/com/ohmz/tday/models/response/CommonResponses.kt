package com.ohmz.tday.models.response

import kotlinx.serialization.Serializable

@Serializable
data class MessageResponse(val message: String)

@Serializable
data class ThrottleResponse(
    val message: String,
    val reason: String,
    val retryAfterSeconds: Int,
    val retryAt: String,
)
