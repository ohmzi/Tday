package com.ohmz.tday.compose.core.data.auth

import android.util.Base64
import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.extractApiErrorMessage
import com.ohmz.tday.compose.core.data.requireApiBody
import com.ohmz.tday.compose.core.model.AuthResult
import com.ohmz.tday.compose.core.model.AuthSession
import com.ohmz.tday.compose.core.model.CredentialsCallbackRequest
import com.ohmz.tday.compose.core.model.RegisterOutcome
import com.ohmz.tday.compose.core.model.RegisterRequest
import com.ohmz.tday.compose.core.model.SessionUser
import com.ohmz.tday.compose.core.network.TdayApiService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLDecoder
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: TdayApiService,
    private val json: Json,
    private val secureConfigStore: SecureConfigStore,
    private val cacheManager: OfflineCacheManager,
) {
    private val secureRandom = SecureRandom()

    suspend fun restoreSession(): SessionUser? {
        val response = api.getSession()
        if (!response.isSuccessful) return null

        val payload = response.body() ?: return null
        if (payload is JsonNull) return null

        return runCatching {
            json.decodeFromJsonElement<AuthSession>(payload).user
        }.getOrNull()
    }

    suspend fun login(email: String, password: String): AuthResult {
        if (!secureConfigStore.hasServerUrl()) {
            return AuthResult.Error("Server URL is not configured")
        }

        val credentialEnvelope = runCatching {
            createCredentialEnvelope(email, password)
        }.getOrElse {
            return AuthResult.Error(it.message ?: "Could not prepare secure sign-in flow")
        }

        val csrf = runCatching {
            requireApiBody(api.getCsrfToken(), "Could not start sign-in flow").csrfToken
        }.getOrElse { return AuthResult.Error(it.message ?: "Could not start sign-in flow") }

        val requestCallbackUrl = secureConfigStore.buildAbsoluteAppUrl("/app/tday")
            ?: return AuthResult.Error("Server URL is not configured")

        val callback = runCatching {
            api.signInWithCredentials(
                payload = CredentialsCallbackRequest(
                    csrfToken = csrf,
                    encryptedPayload = credentialEnvelope.encryptedPayload,
                    encryptedKey = credentialEnvelope.encryptedKey,
                    encryptedIv = credentialEnvelope.encryptedIv,
                    credentialKeyId = credentialEnvelope.keyId,
                    credentialEnvelopeVersion = credentialEnvelope.version,
                    redirect = "false",
                    callbackUrl = requestCallbackUrl,
                ),
            )
        }.getOrElse {
            return AuthResult.Error(it.message ?: "Unable to reach server during sign in")
        }

        val callbackUrlFromBody = callback.body()
            ?.jsonObject
            ?.get("url")
            ?.jsonPrimitive
            ?.contentOrNull
            .orEmpty()
        val callbackUrlFromHeader = callback.headers()["location"].orEmpty()
        val callbackUrlParam = callbackUrlFromBody.ifBlank { callbackUrlFromHeader }
        val params = parseQueryParams(callbackUrlParam)
        val error = params["error"]
        val code = params["code"]

        if (code == "pending_approval") {
            return AuthResult.PendingApproval
        }

        if (!error.isNullOrBlank()) {
            return AuthResult.Error(mapAuthError(error))
        }

        if (!callback.isSuccessful && callback.code() !in 300..399) {
            return AuthResult.Error(extractApiErrorMessage(callback, "Unable to sign in"))
        }

        val user = runCatching { restoreSession() }.getOrNull()
        return if (user?.id != null) {
            syncTimezone()
            runCatching { secureConfigStore.persistRuntimeServerUrl() }
            secureConfigStore.saveLastEmail(email)
            AuthResult.Success
        } else {
            AuthResult.Error("Sign in failed. Please check backend URL and credentials.")
        }
    }

    suspend fun register(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
    ): RegisterOutcome {
        val response = runCatching {
            api.register(
                RegisterRequest(
                    fname = firstName,
                    lname = lastName.ifBlank { null },
                    email = email,
                    password = password,
                ),
            )
        }.getOrElse { error ->
            return RegisterOutcome(
                success = false,
                requiresApproval = false,
                message = error.message ?: "Unable to reach server",
            )
        }

        if (!response.isSuccessful) {
            val message = extractApiErrorMessage(response, "Unable to create account")
            return RegisterOutcome(
                success = false,
                requiresApproval = false,
                message = message,
            )
        }

        val body = response.body()
        return RegisterOutcome(
            success = true,
            requiresApproval = body?.requiresApproval ?: false,
            message = body?.message ?: "Account created",
        )
    }

    suspend fun logout() {
        try {
            runCatching { api.signOut() }
        } finally {
            cacheManager.clearAllLocalData()
        }
    }

    suspend fun syncTimezone() {
        runCatching {
            api.syncTimezone(TimeZone.getDefault().id)
        }
    }

    fun clearSessionOnly() {
        cacheManager.clearSessionOnly()
    }

    fun clearAllLocalUserDataForUnauthenticatedState() {
        cacheManager.clearAllLocalData()
    }

    fun getLastEmail(): String? = secureConfigStore.getLastEmail()

    private suspend fun createCredentialEnvelope(
        email: String,
        password: String,
    ): CredentialEnvelope {
        val credentialKey = requireApiBody(
            api.getCredentialKey(),
            "Could not initialize secure sign-in",
        )
        if (credentialKey.version != CREDENTIAL_ENVELOPE_VERSION) {
            throw IllegalStateException("Unsupported secure sign-in version")
        }

        val publicKeyBytes = decodeBase64Url(credentialKey.publicKey)
        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(publicKeyBytes))

        val aesKey = ByteArray(AES_KEY_BYTES).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(AES_GCM_IV_BYTES).also { secureRandom.nextBytes(it) }

        val credentialPayload = json.encodeToString(
            kotlinx.serialization.serializer<CredentialEnvelopePayload>(),
            CredentialEnvelopePayload(
                email = email.trim().lowercase(Locale.US),
                password = password,
            ),
        ).toByteArray(Charsets.UTF_8)

        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(aesKey, "AES"),
            GCMParameterSpec(AES_GCM_TAG_BITS, iv),
        )
        val encryptedPayload = aesCipher.doFinal(credentialPayload)

        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        rsaCipher.init(
            Cipher.ENCRYPT_MODE,
            publicKey,
            OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT,
            ),
        )
        val encryptedKey = rsaCipher.doFinal(aesKey)

        return CredentialEnvelope(
            encryptedPayload = encodeBase64Url(encryptedPayload),
            encryptedKey = encodeBase64Url(encryptedKey),
            encryptedIv = encodeBase64Url(iv),
            keyId = credentialKey.keyId,
            version = credentialKey.version,
        )
    }

    private fun encodeBase64Url(value: ByteArray): String {
        return Base64.encodeToString(
            value,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
    }

    private fun decodeBase64Url(value: String): ByteArray {
        return Base64.decode(
            value,
            Base64.URL_SAFE or Base64.NO_WRAP,
        )
    }

    private fun mapAuthError(errorCode: String): String {
        return when (errorCode.lowercase()) {
            "credentialssignin" -> "Invalid email or password"
            "configuration" -> "Sign in failed on server. Check credentials or reset password."
            "accessdenied" -> "Access denied"
            else -> "Sign in failed: $errorCode"
        }
    }

    private fun parseQueryParams(url: String): Map<String, String> {
        if (url.isBlank()) return emptyMap()
        return runCatching {
            val query = runCatching { URI(url).query }.getOrNull()
                ?: runCatching { URI("http://placeholder$url").query }.getOrNull()
                ?: return emptyMap()
            query.split('&')
                .mapNotNull { pair ->
                    val key = pair.substringBefore('=', missingDelimiterValue = "")
                    if (key.isBlank()) return@mapNotNull null
                    val value = pair.substringAfter('=', missingDelimiterValue = "")
                    key to URLDecoder.decode(value, Charsets.UTF_8.name())
                }
                .toMap()
        }.getOrElse { emptyMap() }
    }

    private companion object {
        const val CREDENTIAL_ENVELOPE_VERSION = "1"
        const val AES_KEY_BYTES = 32
        const val AES_GCM_IV_BYTES = 12
        const val AES_GCM_TAG_BITS = 128
    }

    @kotlinx.serialization.Serializable
    private data class CredentialEnvelopePayload(
        val email: String,
        val password: String,
    )

    private data class CredentialEnvelope(
        val encryptedPayload: String,
        val encryptedKey: String,
        val encryptedIv: String,
        val keyId: String,
        val version: String,
    )
}
