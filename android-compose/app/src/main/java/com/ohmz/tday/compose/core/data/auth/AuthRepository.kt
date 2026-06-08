package com.ohmz.tday.compose.core.data.auth

import android.util.Base64
import com.ohmz.tday.compose.core.data.ApiCallException
import com.ohmz.tday.compose.core.data.AuthErrorCode
import com.ohmz.tday.compose.core.data.ConnectionFailureKind
import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.classifyConnectionFailure
import com.ohmz.tday.compose.core.data.extractApiErrorDetails
import com.ohmz.tday.compose.core.data.extractApiErrorMessage
import com.ohmz.tday.compose.core.data.isLikelyConnectivityIssue
import com.ohmz.tday.compose.core.data.isLikelyServerUnavailableStatus
import com.ohmz.tday.compose.core.data.requireApiBody
import com.ohmz.tday.compose.core.data.versionMismatchAuthCode
import com.ohmz.tday.compose.core.model.AuthResult
import com.ohmz.tday.compose.core.model.AuthSession
import com.ohmz.tday.compose.core.model.ChangePasswordRequest
import com.ohmz.tday.compose.core.model.CredentialsCallbackRequest
import com.ohmz.tday.compose.core.model.PasswordResetOutcome
import com.ohmz.tday.compose.core.model.RegisterOutcome
import com.ohmz.tday.compose.core.model.RegisterRequest
import com.ohmz.tday.compose.core.model.RequestAdminResetRequest
import com.ohmz.tday.compose.core.model.SecurityAnswerInput
import com.ohmz.tday.compose.core.model.SecurityQuestion
import com.ohmz.tday.compose.core.model.SecurityQuestionStatusResponse
import com.ohmz.tday.compose.core.model.SelfServiceResetRequest
import com.ohmz.tday.compose.core.model.SessionUser
import com.ohmz.tday.compose.core.model.SetSecurityQuestionsRequest
import com.ohmz.tday.compose.core.model.UpdateProfileRequest
import com.ohmz.tday.compose.core.model.VerifyAnswersOutcome
import com.ohmz.tday.compose.core.model.VerifySecurityAnswersRequest
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

    data class RestoredSession(
        val user: SessionUser,
        val usedCachedSession: Boolean,
    )

    suspend fun restoreSession(): SessionUser? {
        return restoreSessionFromServer()?.also(::cacheSessionUser)
    }

    suspend fun restoreSessionForBootstrap(): RestoredSession? {
        return runCatching {
            restoreSessionFromServer()?.also(::cacheSessionUser)?.let { user ->
                RestoredSession(user = user, usedCachedSession = false)
            }
        }.getOrElse { error ->
            if (!isLikelyConnectivityIssue(error)) return@getOrElse null
            (loadCachedSessionUser() ?: loadLastKnownOfflineSessionUser())
                ?.takeIf { it.id != null }
                ?.let { user ->
                    RestoredSession(user = user, usedCachedSession = true)
                }
        }
    }

    private suspend fun restoreSessionFromServer(): SessionUser? {
        val response = api.getSession()
        if (!response.isSuccessful) {
            if (isLikelyServerUnavailableStatus(response.code())) {
                throw ApiCallException(
                    statusCode = response.code(),
                    message = extractApiErrorMessage(response, SERVER_UNREACHABLE_MESSAGE),
                )
            }
            secureConfigStore.clearCachedSessionUser()
            return null
        }

        val payload = response.body() ?: return null
        if (payload is JsonNull) {
            secureConfigStore.clearCachedSessionUser()
            return null
        }

        return runCatching {
            json.decodeFromJsonElement<AuthSession>(payload).user
        }.getOrNull().also { user ->
            if (user?.id == null) secureConfigStore.clearCachedSessionUser()
        }
    }

    private fun cacheSessionUser(user: SessionUser) {
        if (user.id == null) return
        runCatching {
            secureConfigStore.saveCachedSessionUserRaw(
                json.encodeToString(
                    SessionUser.serializer(),
                    user
                )
            )
        }
    }

    private fun loadCachedSessionUser(): SessionUser? {
        val raw = secureConfigStore.getCachedSessionUserRaw() ?: return null
        return runCatching {
            json.decodeFromString(SessionUser.serializer(), raw)
        }.getOrNull()
    }

    private fun loadLastKnownOfflineSessionUser(): SessionUser? {
        val username = secureConfigStore.getLastUsername() ?: return null
        if (!cacheManager.hasCachedData()) return null
        return SessionUser(id = username, username = username)
    }

    suspend fun login(username: String, password: String): AuthResult {
        if (!secureConfigStore.hasServerUrl()) {
            return AuthResult.Error("Server URL is not configured")
        }

        val credentialEnvelope = runCatching {
            createCredentialEnvelope(username, password)
        }.getOrElse {
            return AuthResult.Error(it.loginErrorMessage("Could not prepare secure sign-in flow"))
        }

        val csrf = runCatching {
            requireApiBody(api.getCsrfToken(), "Could not start sign-in flow").csrfToken
        }.getOrElse { return AuthResult.Error(it.loginErrorMessage("Could not start sign-in flow")) }

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
            return AuthResult.Error(it.loginErrorMessage("Unable to reach server during sign in"))
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
            if (isLikelyServerUnavailableStatus(callback.code())) {
                return AuthResult.Error(AuthErrorCode.SERVER_UNAVAILABLE)
            }
            return AuthResult.Error(extractApiErrorMessage(callback, "Unable to sign in"))
        }

        val sessionResult = runCatching { restoreSession() }
        val user = sessionResult.getOrNull()
        return if (user?.id != null) {
            syncTimezone()
            runCatching { secureConfigStore.persistRuntimeServerUrl() }
            secureConfigStore.saveLastUsername(username)
            AuthResult.Success
        } else {
            val sessionError = sessionResult.exceptionOrNull()
            if (sessionError != null && isLikelyConnectivityIssue(sessionError)) {
                return AuthResult.Error(sessionError.loginErrorMessage(SERVER_UNREACHABLE_MESSAGE))
            }
            AuthResult.Error("Sign in failed. Please check backend URL and credentials.")
        }
    }

    suspend fun register(
        firstName: String,
        lastName: String,
        username: String,
        password: String,
        securityAnswers: List<SecurityAnswerInput>,
    ): RegisterOutcome {
        val response = runCatching {
            api.register(
                RegisterRequest(
                    fname = firstName,
                    lname = lastName.ifBlank { null },
                    username = username,
                    password = password,
                    securityAnswers = securityAnswers.ifEmpty { null },
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

    suspend fun fetchAllSecurityQuestions(): List<SecurityQuestion> {
        val response = api.getAllSecurityQuestions()
        if (!response.isSuccessful) {
            throw ApiCallException(
                statusCode = response.code(),
                message = extractApiErrorMessage(response, "Unable to load security questions"),
            )
        }
        return response.body()?.questions.orEmpty()
    }

    suspend fun fetchQuestionsForUsername(username: String): List<SecurityQuestion> {
        val response = api.getSecurityQuestionsForUsername(username.trim().lowercase(Locale.US))
        if (!response.isSuccessful) {
            throw ApiCallException(
                statusCode = response.code(),
                message = extractApiErrorMessage(response, "Unable to load security questions"),
            )
        }
        return response.body()?.questions.orEmpty()
    }

    suspend fun verifyAnswers(
        username: String,
        answers: List<SecurityAnswerInput>,
    ): VerifyAnswersOutcome {
        val response = runCatching {
            api.verifySecurityAnswers(
                VerifySecurityAnswersRequest(
                    username = username.trim().lowercase(Locale.US),
                    answers = answers,
                ),
            )
        }.getOrElse { error ->
            return VerifyAnswersOutcome.Error(error.message ?: "Unable to verify your answers.")
        }

        if (response.isSuccessful) {
            val body = response.body()
            return if (body?.valid == true) {
                VerifyAnswersOutcome.Valid
            } else {
                VerifyAnswersOutcome.Invalid(body?.results.orEmpty())
            }
        }

        val details = extractApiErrorDetails(response, "Unable to verify your answers.")
        return if (response.code() == 403 && details.reason == "reset_locked") {
            VerifyAnswersOutcome.Locked
        } else {
            VerifyAnswersOutcome.Error(details.message)
        }
    }

    suspend fun resetPassword(
        username: String,
        answers: List<SecurityAnswerInput>,
        newPassword: String,
    ): PasswordResetOutcome {
        val response = runCatching {
            api.resetPassword(
                SelfServiceResetRequest(
                    username = username.trim().lowercase(Locale.US),
                    answers = answers,
                    newPassword = newPassword,
                ),
            )
        }.getOrElse { error ->
            return PasswordResetOutcome.Failed(
                error.message ?: "Unable to reset password",
            )
        }

        if (response.isSuccessful) {
            return PasswordResetOutcome.Success
        }

        val details =
            extractApiErrorDetails(response, "Unable to reset password. Check your answers.")
        return if (response.code() == 403 && details.reason == "reset_locked") {
            PasswordResetOutcome.Locked
        } else {
            PasswordResetOutcome.Failed(details.message)
        }
    }

    suspend fun requestAdminReset(username: String) {
        val response = api.requestAdminReset(
            RequestAdminResetRequest(username = username.trim().lowercase(Locale.US)),
        )
        if (!response.isSuccessful) {
            throw ApiCallException(
                statusCode = response.code(),
                message = extractApiErrorMessage(response, "Unable to send request"),
            )
        }
    }

    fun savePendingApproval(username: String, password: String) =
        secureConfigStore.savePendingApproval(username, password)

    fun loadPendingApproval(): Pair<String, String>? = secureConfigStore.getPendingApproval()

    fun clearPendingApproval() = secureConfigStore.clearPendingApproval()

    suspend fun updateProfileName(name: String) {
        val response = api.patchUserProfile(UpdateProfileRequest(name = name.trim()))
        if (!response.isSuccessful) {
            throw ApiCallException(
                statusCode = response.code(),
                message = extractApiErrorMessage(response, "Unable to update name"),
            )
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val response = api.changePassword(
            ChangePasswordRequest(currentPassword = currentPassword, newPassword = newPassword),
        )
        if (!response.isSuccessful) {
            throw ApiCallException(
                statusCode = response.code(),
                message = extractApiErrorMessage(response, "Unable to change password"),
            )
        }
    }

    suspend fun setSecurityQuestions(
        answers: List<SecurityAnswerInput>,
        currentPassword: String? = null,
    ) {
        val response = api.setUserSecurityQuestions(
            SetSecurityQuestionsRequest(answers = answers, currentPassword = currentPassword),
        )
        if (!response.isSuccessful) {
            throw ApiCallException(
                statusCode = response.code(),
                message = extractApiErrorMessage(response, "Failed to save security questions"),
            )
        }
    }

    suspend fun getUserSecurityQuestionStatus(): SecurityQuestionStatusResponse {
        val response = api.getUserSecurityQuestionStatus()
        if (!response.isSuccessful) {
            throw ApiCallException(
                statusCode = response.code(),
                message = extractApiErrorMessage(response, "Unable to load security questions"),
            )
        }
        return response.body() ?: SecurityQuestionStatusResponse()
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
        secureConfigStore.clearCachedSessionUser()
        cacheManager.clearSessionOnly()
    }

    fun clearAllLocalUserDataForUnauthenticatedState() {
        secureConfigStore.clearCachedSessionUser()
        cacheManager.clearAllLocalData()
    }

    fun getLastUsername(): String? = secureConfigStore.getLastUsername()

    private suspend fun createCredentialEnvelope(
        username: String,
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
                username = username.trim().lowercase(Locale.US),
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
            "credentialssignin" -> "Invalid username or password"
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

    private fun Throwable.loginErrorMessage(fallback: String): String {
        versionMismatchAuthCode(this)?.let { return it }
        return when (classifyConnectionFailure(this)) {
            ConnectionFailureKind.CANNOT_REACH -> AuthErrorCode.CANNOT_REACH
            ConnectionFailureKind.SERVER_UNAVAILABLE -> AuthErrorCode.SERVER_UNAVAILABLE
            ConnectionFailureKind.NONE -> message ?: fallback
        }
    }

    private companion object {
        const val SERVER_UNREACHABLE_MESSAGE =
            "Cannot reach server. Check your server URL and try again."
        const val CREDENTIAL_ENVELOPE_VERSION = "1"
        const val AES_KEY_BYTES = 32
        const val AES_GCM_IV_BYTES = 12
        const val AES_GCM_TAG_BITS = 128
    }

    @kotlinx.serialization.Serializable
    private data class CredentialEnvelopePayload(
        val username: String,
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
