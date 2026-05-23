package com.ohmz.tday.compose.feature.auth

import android.content.Context
import com.ohmz.tday.compose.core.data.auth.SystemCredential

class LoginCredentialCoordinator {
    private var hasRequestedSystemCredential = false
    private var isRequestingSystemCredential = false

    suspend fun requestSavedCredentialIfAvailable(
        context: Context,
        currentEmail: String,
        currentPassword: String,
        isCreatingAccount: Boolean,
        isAuthLoading: Boolean,
        requestSavedCredential: suspend (Context, String?) -> SystemCredential?,
        login: suspend (SystemCredential) -> Boolean,
    ): Boolean {
        if (isCreatingAccount ||
            currentPassword.isNotEmpty() ||
            isAuthLoading ||
            isRequestingSystemCredential ||
            hasRequestedSystemCredential
        ) {
            return false
        }

        hasRequestedSystemCredential = true
        isRequestingSystemCredential = true
        try {
            val credential = requestSavedCredential(
                context,
                currentEmail.takeIf { it.isNotBlank() },
            ) ?: return false
            return login(credential)
        } finally {
            isRequestingSystemCredential = false
        }
    }
}
