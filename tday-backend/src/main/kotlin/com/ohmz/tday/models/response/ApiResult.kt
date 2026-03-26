package com.ohmz.tday.models.response

import kotlinx.serialization.Serializable

@Serializable
data class ApiError(
    val code: Int,
    val message: String,
    val field: String? = null,
)
