package com.ohmz.tday.models.request

import kotlinx.serialization.Serializable

@Serializable
data class NoteCreateRequest(
    val name: String,
    val content: String? = null,
)

@Serializable
data class NotePatchRequest(
    val name: String? = null,
    val content: String? = null,
)
