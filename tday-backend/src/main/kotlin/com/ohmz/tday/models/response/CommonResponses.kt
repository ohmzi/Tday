package com.ohmz.tday.models.response

import kotlinx.serialization.Serializable

typealias MessageResponse = com.ohmz.tday.shared.model.MessageResponse

@Serializable
data class ThrottleResponse(
    val message: String,
    val reason: String,
    val retryAfterSeconds: Int,
    val retryAt: String,
)
