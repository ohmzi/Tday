package com.ohmz.tday.models.response

import kotlinx.serialization.Serializable

@Serializable
data class NoteResponse(
    val id: String,
    val name: String,
    val content: String?,
    val createdAt: String,
)
