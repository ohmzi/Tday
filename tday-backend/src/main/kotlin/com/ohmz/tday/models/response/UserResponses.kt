package com.ohmz.tday.models.response

import kotlinx.serialization.Serializable

@Serializable
data class UserResponse(
    val maxStorage: Double,
    val usedStoraged: Double,
    val enableEncryption: Boolean,
    val protectedSymmetricKey: String?,
)

@Serializable
data class UserProfileResponse(
    val id: String,
    val name: String?,
    val email: String,
    val image: String?,
    val role: String,
    val approvalStatus: String,
    val createdAt: String,
)

@Serializable
data class AdminUserResponse(
    val id: String,
    val name: String?,
    val email: String,
    val role: String,
    val approvalStatus: String,
    val createdAt: String,
    val approvedAt: String?,
)
