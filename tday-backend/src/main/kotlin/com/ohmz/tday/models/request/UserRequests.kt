package com.ohmz.tday.models.request

import kotlinx.serialization.Serializable

@Serializable
data class UserPatchKeyRequest(val protectedSymmetricKey: String)

@Serializable
data class UserProfilePatchRequest(val name: String? = null, val image: String? = null)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

@Serializable
data class CreateApiKeyRequest(
    val label: String? = null,
    /** "READ" or "FULL"; defaults to FULL when absent or unrecognised. */
    val scope: String? = null,
    /** Optional lifetime in days; the key never expires when absent. */
    val expiresInDays: Long? = null,
)

@Serializable
data class PreferencesPatchRequest(
    val sortBy: String? = null,
    val groupBy: String? = null,
    val direction: String? = null,
    val aiSummaryEnabled: Boolean? = null,
)
