package com.ohmz.tday.models.request

import kotlinx.serialization.Serializable

@Serializable
data class ListCreateRequest(
    val name: String,
    val color: String? = null,
    val iconKey: String? = null,
)

@Serializable
data class ListPatchRequest(
    val id: String,
    val name: String? = null,
    val color: String? = null,
    val iconKey: String? = null,
)

@Serializable
data class ListDeleteRequest(val id: String)
