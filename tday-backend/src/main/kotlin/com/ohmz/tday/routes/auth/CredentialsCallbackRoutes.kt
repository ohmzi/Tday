package com.ohmz.tday.routes.auth

import com.ohmz.tday.models.request.CredentialsCallbackRequest
import com.ohmz.tday.security.*
import com.ohmz.tday.services.UserService
import com.ohmz.tday.services.updatePasswordHash
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.credentialsCallbackRoutes() {
    route("/callback/credentials") {
        post {
            val body = call.receive<CredentialsCallbackRequest>()

            var email: String? = null
            var password: String? = null

            if (!body.encryptedPayload.isNullOrBlank() && !body.encryptedKey.isNullOrBlank() && !body.encryptedIv.isNullOrBlank()) {
                try {
                    val decrypted = CredentialEnvelope.decrypt(CredentialEnvelopeInput(
                        encryptedPayload = body.encryptedPayload,
                        encryptedKey = body.encryptedKey,
                        encryptedIv = body.encryptedIv,
                        keyId = body.credentialKeyId,
                        version = body.credentialEnvelopeVersion,
                    ))
                    email = decrypted.email
                    password = decrypted.password
                } catch (e: Exception) {
                    SecurityEventLogger.log("auth_credential_envelope_invalid", mapOf("error" to e.message))
                }
            }

            if (email == null) email = body.email?.trim()?.lowercase()
            if (password == null) password = body.password

            val identifier = email

            if (AuthThrottle.requiresCaptcha(ThrottleAction.credentials, call.request, identifier)) {
                val captchaResult = CaptchaService.verify(body.captchaToken, call.request, "credentials")
                if (!captchaResult.ok) {
                    SecurityEventLogger.log("auth_captcha_failed", mapOf("action" to "credentials", "reason" to captchaResult.reason))
                    call.respond(HttpStatusCode.Forbidden, mapOf(
                        "message" to "Additional verification required.",
                        "reason" to "captcha_required",
                    ))
                    return@post
                }
            }

            val throttle = AuthThrottle.enforceRateLimit(ThrottleAction.credentials, call.request, identifier)
            if (!throttle.allowed) {
                val wait = AuthThrottle.formatRetryWait(throttle.retryAfterSeconds)
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

            val user = UserService.findByEmail(email)
            if (user == null) {
                body.passwordProofChallengeId?.let { PasswordProof.consume(it) }
                AuthThrottle.recordFailure(call.request, identifier)
                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Invalid credentials"))
                return@post
            }

            val storedHash = user["password"] as? String
            if (storedHash.isNullOrBlank()) {
                AuthThrottle.recordFailure(call.request, identifier)
                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Invalid credentials"))
                return@post
            }

            val usingProof = password.isNullOrBlank() && !body.passwordProof.isNullOrBlank() && !body.passwordProofChallengeId.isNullOrBlank()

            val authenticated = if (usingProof) {
                PasswordProof.verify(
                    email = email,
                    challengeId = body.passwordProofChallengeId!!,
                    proofHex = body.passwordProof!!,
                    proofVersion = body.passwordProofVersion,
                    storedPasswordHash = storedHash,
                )
            } else {
                if (password.isNullOrBlank()) false
                else {
                    val verification = PasswordService.verifyPassword(password, storedHash)
                    if (verification.valid && verification.needsRehash) {
                        UserService.updatePasswordHash(user["id"] as String, PasswordService.hashPassword(password))
                    }
                    verification.valid
                }
            }

            if (!authenticated) {
                AuthThrottle.recordFailure(call.request, identifier)
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

            AuthThrottle.clearFailures(call.request, identifier)
            AuthThrottle.recordSuccessSignal(call.request, identifier)

            val token = JwtService.encode(JwtUserClaims(
                id = user["id"] as String,
                name = user["name"] as? String,
                email = email,
                role = user["role"] as? String,
                approvalStatus = approvalStatus,
                tokenVersion = user["tokenVersion"] as? Int,
                timeZone = user["timeZone"] as? String,
            ))

            val maxAge = com.ohmz.tday.config.AppConfig.sessionMaxAgeSec
            val secure = System.getenv("NODE_ENV") == "production"
            val cookieName = if (secure) "__Secure-authjs.session-token" else "authjs.session-token"

            call.response.cookies.append(Cookie(
                name = cookieName,
                value = token,
                maxAge = maxAge,
                path = "/",
                secure = secure,
                httpOnly = true,
                extensions = mapOf("SameSite" to "Lax"),
            ))

            call.respond(HttpStatusCode.OK, mapOf("message" to "authenticated"))
        }
    }
}
