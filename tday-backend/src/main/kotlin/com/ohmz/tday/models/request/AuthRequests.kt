package com.ohmz.tday.models.request

import kotlinx.serialization.Serializable

@Serializable
data class SecurityAnswerInput(
    val questionId: Int,
    val answer: String,
)

@Serializable
data class RegisterRequest(
    val fname: String,
    val lname: String? = null,
    val username: String,
    val password: String,
    val securityAnswers: List<SecurityAnswerInput>? = null,
    val captchaToken: String? = null,
)

@Serializable
data class SelfServiceResetRequest(
    val username: String,
    val answers: List<SecurityAnswerInput> = emptyList(),
    val newPassword: String,
    val captchaToken: String? = null,
)

@Serializable
data class VerifySecurityAnswersRequest(
    val username: String,
    val answers: List<SecurityAnswerInput> = emptyList(),
    val captchaToken: String? = null,
)

@Serializable
data class RequestAdminResetRequest(
    val username: String,
)

@Serializable
data class SetSecurityQuestionsRequest(
    val answers: List<SecurityAnswerInput> = emptyList(),
    // Required when changing already-configured questions from settings; the first-time
    // gate (requireSecurityQuestions = true) leaves this null.
    val currentPassword: String? = null,
)

@Serializable
data class LoginChallengeRequest(
    val username: String,
)

@Serializable
data class CredentialsCallbackRequest(
    val username: String? = null,
    val password: String? = null,
    val encryptedPayload: String? = null,
    val encryptedKey: String? = null,
    val encryptedIv: String? = null,
    val credentialKeyId: String? = null,
    val credentialEnvelopeVersion: String? = null,
    val passwordProof: String? = null,
    val passwordProofChallengeId: String? = null,
    val passwordProofVersion: String? = null,
    val captchaToken: String? = null,
)
