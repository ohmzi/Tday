package com.ohmz.tday.models.request

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val fname: String,
    val lname: String? = null,
    val email: String,
    val password: String,
    val captchaToken: String? = null,
)

@Serializable
data class LoginChallengeRequest(
    val email: String,
)

@Serializable
data class CredentialsCallbackRequest(
    val email: String? = null,
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
