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
data class PreferencesPatchRequest(
    val sortBy: String? = null,
    val groupBy: String? = null,
    val direction: String? = null,
)

@Serializable
data class AdminSettingsPatchRequest(val aiSummaryEnabled: Boolean)
