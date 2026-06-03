package com.ohmz.tday.models.response

import kotlinx.serialization.Serializable

@Serializable
data class VapidPublicKeyResponse(
    val publicKey: String,
)
