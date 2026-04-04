package com.ohmz.tday.routes.auth

import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.models.request.CredentialsCallbackRequest
import com.ohmz.tday.security.*
import com.ohmz.tday.services.UserService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.ohmz.tday.di.inject

fun Route.credentialsCallbackRoutes() {
    val userService by inject<UserService>()
    val authThrottle by inject<AuthThrottle>()
    val captchaService by inject<CaptchaService>()
    val eventLogger by inject<SecurityEventLogger>()
    val credentialEnvelope by inject<CredentialEnvelope>()
    val passwordProof by inject<PasswordProof>()
    val passwordService by inject<PasswordService>()
    val jwtService by inject<JwtService>()
    val config by inject<AppConfig>()

    route("/callback/credentials") {
        post {
            val body = call.receive<CredentialsCallbackRequest>()

            var email: String? = null
            var password: String? = null

            if (!body.encryptedPayload.isNullOrBlank() && !body.encryptedKey.isNullOrBlank() && !body.encryptedIv.isNullOrBlank()) {
                try {
                    val decrypted = credentialEnvelope.decrypt(CredentialEnvelopeInput(
                        encryptedPayload = body.encryptedPayload,
                        encryptedKey = body.encryptedKey,
                        encryptedIv = body.encryptedIv,
                        keyId = body.credentialKeyId,
                        version = body.credentialEnvelopeVersion,
                    ))
                    email = decrypted.email
                    password = decrypted.password
                } catch (e: Exception) {
                    eventLogger.log("auth_credential_envelope_invalid", mapOf("error" to e.message))
                }
            }

            if (email == null) email = body.email?.trim()?.lowercase()
            if (password == null) password = body.password

            val identifier = email

            if (authThrottle.requiresCaptcha(ThrottleAction.credentials, call.request, identifier)) {
                if (!captchaService.isConfigured()) {
                    eventLogger.log(
                        "auth_captcha_misconfigured",
                        mapOf("action" to "credentials", "reason" to "captcha_secret_missing"),
                    )
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf(
                            "message" to "Additional verification is temporarily unavailable.",
                            "reason" to "captcha_unavailable",
                        ),
                    )
                    return@post
                }
                val captchaResult = captchaService.verify(body.captchaToken, call.request, "credentials")
                if (!captchaResult.ok) {
                    eventLogger.log("auth_captcha_failed", mapOf("action" to "credentials", "reason" to captchaResult.reason))
                    call.respond(HttpStatusCode.Forbidden, mapOf(
                        "message" to "Additional verification required.",
                        "reason" to "captcha_required",
                    ))
                    return@post
                }
            }

            val throttle = authThrottle.enforceRateLimit(ThrottleAction.credentials, call.request, identifier)
            if (!throttle.allowed) {
                val wait = authThrottle.formatRetryWait(throttle.retryAfterSeconds)
                val msg = if (throttle.reasonCode == "auth_lockout")
                    "Too many failed sign-in attempts. Try again in $wait."
                else "Too many authentication requests. Try again in $wait."
                call.respond(HttpStatusCode.TooManyRequests, mapOf(
                    "message" to msg,
                    "reason" to (throttle.reasonCode ?: "auth_limit"),
                    "retryAfterSeconds" to throttle.retryAfterSeconds,
                ))
                return@post
            }

            if (email.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Invalid credentials"))
                return@post
            }

            val user = userService.findByEmail(email)
            if (user == null) {
                body.passwordProofChallengeId?.let { passwordProof.consume(it) }
                authThrottle.recordFailure(call.request, identifier)
                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Invalid credentials"))
                return@post
            }

            val storedHash = user["password"] as? String
            if (storedHash.isNullOrBlank()) {
                authThrottle.recordFailure(call.request, identifier)
                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Invalid credentials"))
                return@post
            }

            val usingProof = password.isNullOrBlank() && !body.passwordProof.isNullOrBlank() && !body.passwordProofChallengeId.isNullOrBlank()

            val authenticated = if (usingProof) {
                passwordProof.verify(
                    email = email,
                    challengeId = body.passwordProofChallengeId!!,
                    proofHex = body.passwordProof!!,
                    proofVersion = body.passwordProofVersion,
                    storedPasswordHash = storedHash,
                )
            } else {
                if (password.isNullOrBlank()) false
                else {
                    val verification = passwordService.verifyPassword(password, storedHash)
                    if (verification.valid && verification.needsRehash) {
                        userService.updatePasswordHash(user["id"] as String, passwordService.hashPassword(password))
                    }
                    verification.valid
                }
            }

            if (!authenticated) {
                authThrottle.recordFailure(call.request, identifier)
                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Invalid credentials"))
                return@post
            }

            val approvalStatus = user["approvalStatus"] as? String
            if (approvalStatus != "APPROVED") {
                call.respond(HttpStatusCode.Forbidden, mapOf(
                    "message" to "Account approval required",
                    "code" to "pending_approval",
                ))
                return@post
            }

            authThrottle.clearFailures(call.request, identifier)
            authThrottle.recordSuccessSignal(call.request, identifier)

            call.issueSessionCookie(config, jwtService, JwtUserClaims(
                id = user["id"] as String,
                name = user["name"] as? String,
                email = email,
                role = user["role"] as? String,
                approvalStatus = approvalStatus,
                tokenVersion = user["tokenVersion"] as? Int,
                timeZone = user["timeZone"] as? String,
            ))

            call.respond(HttpStatusCode.OK, mapOf("message" to "authenticated"))
        }
    }
}
