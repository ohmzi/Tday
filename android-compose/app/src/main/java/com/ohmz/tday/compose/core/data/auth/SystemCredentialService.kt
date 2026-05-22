package com.ohmz.tday.compose.core.data.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class SystemCredential(
    val email: String,
    val password: String,
)

enum class SystemCredentialSaveResult {
    SAVED,
    SKIPPED,
    CANCELLED,
    FAILED,
}

enum class LoginCredentialSource {
    MANUAL,
    SYSTEM_PASSWORD_MANAGER,
}

interface SystemCredentialServicing {
    suspend fun requestSavedCredential(
        context: Context,
        preferredEmail: String? = null,
    ): SystemCredential?

    suspend fun offerSaveOrUpdateCredential(
        context: Context,
        credential: SystemCredential,
    ): SystemCredentialSaveResult

    suspend fun requestSavedServerUrl(context: Context): String?
    suspend fun offerSaveOrUpdateServerUrl(
        context: Context,
        serverUrl: String,
    ): SystemCredentialSaveResult

    suspend fun clearCredentialState()
}

@Singleton
class SystemCredentialService @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : SystemCredentialServicing {
    override suspend fun requestSavedCredential(
        context: Context,
        preferredEmail: String?,
    ): SystemCredential? {
        val activity = context.findActivity() ?: return null
        val credentialManager = CredentialManager.create(activity)
        val allowedUserIds = preferredEmail
            ?.trim()
            ?.lowercase(Locale.US)
            ?.takeIf { it.isNotBlank() }
            ?.let { setOf(it) }
            ?: emptySet()
        val request = GetCredentialRequest(
            credentialOptions = listOf(
                GetPasswordOption(
                    allowedUserIds = allowedUserIds,
                    isAutoSelectAllowed = true,
                ),
            ),
        )

        return try {
            val credential = credentialManager.getCredential(
                context = activity,
                request = request,
            ).credential
            when (credential) {
                is PasswordCredential -> SystemCredentialRecords.loginCredential(
                    id = credential.id,
                    password = credential.password,
                )

                else -> null
            }
        } catch (_: GetCredentialException) {
            null
        }
    }

    override suspend fun offerSaveOrUpdateCredential(
        context: Context,
        credential: SystemCredential,
    ): SystemCredentialSaveResult {
        val normalizedEmail = credential.email.trim().lowercase(Locale.US)
        if (normalizedEmail.isBlank() || credential.password.isBlank()) {
            return SystemCredentialSaveResult.SKIPPED
        }

        val activity = context.findActivity() ?: return SystemCredentialSaveResult.FAILED
        val credentialManager = CredentialManager.create(activity)
        val request = CreatePasswordRequest(
            id = normalizedEmail,
            password = credential.password,
        )

        return try {
            credentialManager.createCredential(
                context = activity,
                request = request,
            )
            SystemCredentialSaveResult.SAVED
        } catch (_: CreateCredentialCancellationException) {
            SystemCredentialSaveResult.CANCELLED
        } catch (error: CreateCredentialException) {
            Log.w(
                LOG_TAG,
                "Android Password Manager could not save credential: ${error.type}",
                error
            )
            SystemCredentialSaveResult.FAILED
        }
    }

    override suspend fun requestSavedServerUrl(context: Context): String? {
        val activity = context.findActivity() ?: return null
        val credentialManager = CredentialManager.create(activity)
        val request = GetCredentialRequest(
            credentialOptions = listOf(
                GetPasswordOption(
                    allowedUserIds = setOf(SystemCredentialRecords.SERVER_URL_CREDENTIAL_ID),
                    isAutoSelectAllowed = true,
                ),
            ),
        )

        return try {
            val credential = credentialManager.getCredential(
                context = activity,
                request = request,
            ).credential
            when (credential) {
                is PasswordCredential -> SystemCredentialRecords.serverUrl(
                    id = credential.id,
                    password = credential.password,
                )

                else -> null
            }
        } catch (_: GetCredentialException) {
            null
        }
    }

    override suspend fun offerSaveOrUpdateServerUrl(
        context: Context,
        serverUrl: String,
    ): SystemCredentialSaveResult {
        val normalizedServerUrl = serverUrl.trim()
        if (normalizedServerUrl.isBlank()) {
            return SystemCredentialSaveResult.SKIPPED
        }

        val activity = context.findActivity() ?: return SystemCredentialSaveResult.FAILED
        val credentialManager = CredentialManager.create(activity)
        val request = CreatePasswordRequest(
            id = SystemCredentialRecords.SERVER_URL_CREDENTIAL_ID,
            password = normalizedServerUrl,
        )

        return try {
            credentialManager.createCredential(
                context = activity,
                request = request,
            )
            SystemCredentialSaveResult.SAVED
        } catch (_: CreateCredentialCancellationException) {
            SystemCredentialSaveResult.CANCELLED
        } catch (error: CreateCredentialException) {
            Log.w(
                LOG_TAG,
                "Android Password Manager could not save server URL: ${error.type}",
                error
            )
            SystemCredentialSaveResult.FAILED
        }
    }

    override suspend fun clearCredentialState() {
        try {
            val credentialManager = CredentialManager.create(appContext)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: ClearCredentialException) {
            // The local app session is already cleared; credential providers are best-effort here.
        }
    }

    private companion object {
        const val LOG_TAG = "TdayCredentials"
    }
}

internal object SystemCredentialRecords {
    const val SERVER_URL_CREDENTIAL_ID = "T'Day Server URL"

    fun loginCredential(id: String, password: String): SystemCredential? {
        val normalizedId = id.trim()
        // Older builds briefly saved server URLs as password records; never treat those as logins.
        if (normalizedId == SERVER_URL_CREDENTIAL_ID) return null
        if (normalizedId.isBlank() || password.isBlank()) return null
        return SystemCredential(
            email = normalizedId,
            password = password,
        )
    }

    fun serverUrl(id: String, password: String): String? {
        if (id.trim() != SERVER_URL_CREDENTIAL_ID) return null
        return password.trim().takeIf { it.isNotBlank() }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
