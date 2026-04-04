package com.ohmz.tday.routes.auth

import arrow.core.Either
import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.models.response.UserProfileResponse
import com.ohmz.tday.models.response.UserResponse
import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.security.AuthThrottle
import com.ohmz.tday.security.CaptchaResult
import com.ohmz.tday.security.CaptchaService
import com.ohmz.tday.security.CredentialEnvelope
import com.ohmz.tday.security.CredentialEnvelopeInput
import com.ohmz.tday.security.CredentialPublicKeyDescriptor
import com.ohmz.tday.security.DecryptedCredentials
import com.ohmz.tday.security.JwtService
import com.ohmz.tday.security.JwtServiceImpl
import com.ohmz.tday.security.PasswordProof
import com.ohmz.tday.security.PasswordProofChallengePayload
import com.ohmz.tday.security.PasswordService
import com.ohmz.tday.security.PasswordVerification
import com.ohmz.tday.security.SecurityEventLogger
import com.ohmz.tday.security.ThrottleAction
import com.ohmz.tday.security.ThrottleResult
import com.ohmz.tday.security.testAppConfig
import com.ohmz.tday.services.RegisterResult
import com.ohmz.tday.services.UserService
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.*
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.assertEquals

class CaptchaHardeningTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `credentials callback fails closed when captcha is required but secret is missing`() = testApplication {
        val userService = RecordingUserService()
        val eventLogger = RecordingSecurityEventLogger()

        application {
            configureCaptchaHardeningTestApp(
                config = testAppConfig(),
                userService = userService,
                authThrottle = RequireCaptchaAuthThrottle(),
                captchaService = MissingCaptchaService(),
                eventLogger = eventLogger,
            )
        }

        val response = client.post("/api/auth/callback/credentials") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"user@example.com","password":"Password123!"}""")
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("captcha_unavailable", payload.getValue("reason").jsonPrimitive.content)
        assertEquals(0, userService.findByEmailCalls)
        assertEquals(listOf("auth_captcha_misconfigured"), eventLogger.reasonCodes)
    }

    @Test
    fun `register fails closed when captcha is required but secret is missing`() = testApplication {
        val userService = RecordingUserService()
        val eventLogger = RecordingSecurityEventLogger()

        application {
            configureCaptchaHardeningTestApp(
                config = testAppConfig(),
                userService = userService,
                authThrottle = RequireCaptchaAuthThrottle(),
                captchaService = MissingCaptchaService(),
                eventLogger = eventLogger,
            )
        }

        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "fname":"Pending",
                  "lname":"User",
                  "email":"pending@example.com",
                  "password":"Password123!"
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("captcha_unavailable", payload.getValue("reason").jsonPrimitive.content)
        assertEquals(0, userService.emailExistsCalls)
        assertEquals(0, userService.registerCalls)
        assertEquals(listOf("auth_captcha_misconfigured"), eventLogger.reasonCodes)
    }

    private fun Application.configureCaptchaHardeningTestApp(
        config: AppConfig,
        userService: RecordingUserService,
        authThrottle: AuthThrottle,
        captchaService: CaptchaService,
        eventLogger: RecordingSecurityEventLogger,
    ) {
        install(Koin) {
            modules(
                module {
                    single { config }
                    single<UserService> { userService }
                    single<AuthThrottle> { authThrottle }
                    single<CaptchaService> { captchaService }
                    single<SecurityEventLogger> { eventLogger }
                    single<CredentialEnvelope> { NoOpCredentialEnvelope() }
                    single<PasswordProof> { NoOpPasswordProof() }
                    single<PasswordService> { NoOpPasswordService() }
                    single<JwtService> { JwtServiceImpl(config) }
                },
            )
        }
        configureSerialization()
        routing {
            route("/api") {
                route("/auth") {
                    registerRoutes()
                    credentialsCallbackRoutes()
                }
            }
        }
    }

    private class RequireCaptchaAuthThrottle : AuthThrottle {
        override suspend fun enforceRateLimit(
            action: ThrottleAction,
            request: io.ktor.server.request.ApplicationRequest,
            identifier: String?,
        ): ThrottleResult = ThrottleResult(allowed = true)

        override suspend fun recordFailure(
            request: io.ktor.server.request.ApplicationRequest,
            identifier: String?,
        ) = Unit

        override suspend fun clearFailures(
            request: io.ktor.server.request.ApplicationRequest,
            identifier: String?,
        ) = Unit

        override suspend fun requiresCaptcha(
            action: ThrottleAction,
            request: io.ktor.server.request.ApplicationRequest,
            identifier: String?,
        ): Boolean = true

        override suspend fun recordSuccessSignal(
            request: io.ktor.server.request.ApplicationRequest,
            identifier: String?,
        ) = Unit

        override fun formatRetryWait(seconds: Int): String = "${seconds}s"
    }

    private class MissingCaptchaService : CaptchaService {
        override fun isConfigured(): Boolean = false

        override suspend fun verify(
            token: String?,
            request: io.ktor.server.request.ApplicationRequest,
            action: String,
        ): CaptchaResult = error("verify should not be called when captcha secret is missing")

        override fun extractTokenFromJson(body: kotlinx.serialization.json.JsonObject?): String? = null
    }

    private class RecordingSecurityEventLogger : SecurityEventLogger {
        val reasonCodes = mutableListOf<String>()

        override suspend fun log(reasonCode: String, details: Map<String, Any?>) {
            reasonCodes += reasonCode
        }
    }

    private class RecordingUserService : UserService {
        var findByEmailCalls = 0
        var emailExistsCalls = 0
        var registerCalls = 0

        override suspend fun getUser(userId: String): Either<com.ohmz.tday.domain.AppError, UserResponse> = unsupported()

        override suspend fun updateEncryption(userId: String, enable: Boolean): Either<com.ohmz.tday.domain.AppError, Unit> = unsupported()

        override suspend fun updateSymmetricKey(userId: String, key: String): Either<com.ohmz.tday.domain.AppError, Unit> = unsupported()

        override suspend fun getProfile(userId: String): Either<com.ohmz.tday.domain.AppError, UserProfileResponse> = unsupported()

        override suspend fun updateProfile(
            userId: String,
            name: String?,
            image: String?,
        ): Either<com.ohmz.tday.domain.AppError, Unit> = unsupported()

        override suspend fun changePassword(
            userId: String,
            currentPassword: String,
            newPassword: String,
        ): Either<com.ohmz.tday.domain.AppError, Boolean> = unsupported()

        override suspend fun register(
            fname: String,
            lname: String?,
            email: String,
            password: String,
        ): Either<com.ohmz.tday.domain.AppError, RegisterResult> {
            registerCalls += 1
            return unsupported()
        }

        override suspend fun findByEmail(email: String): Map<String, Any?>? {
            findByEmailCalls += 1
            return null
        }

        override suspend fun isAdmin(userId: String): Boolean = false

        override suspend fun emailExists(email: String): Boolean {
            emailExistsCalls += 1
            return false
        }

        override suspend fun updatePasswordHash(userId: String, newHash: String) = Unit

        private fun <T> unsupported(): Either<com.ohmz.tday.domain.AppError, T> =
            Either.Left(com.ohmz.tday.domain.AppError.Internal("unsupported in captcha test"))
    }

    private class NoOpCredentialEnvelope : CredentialEnvelope {
        override fun getPublicKeyDescriptor(): CredentialPublicKeyDescriptor =
            CredentialPublicKeyDescriptor(
                version = "1",
                algorithm = "RSA-OAEP-256+A256GCM",
                keyId = "test-key",
                publicKey = "test-public-key",
            )

        override fun decrypt(envelope: CredentialEnvelopeInput): DecryptedCredentials =
            error("decrypt should not be called in captcha hardening tests")
    }

    private class NoOpPasswordProof : PasswordProof {
        override fun normalizeEmail(email: String?): String? = email?.trim()?.lowercase()

        override fun issueChallenge(email: String, storedPasswordHash: String?): PasswordProofChallengePayload =
            error("issueChallenge should not be called in captcha hardening tests")

        override fun verify(
            email: String,
            challengeId: String,
            proofHex: String,
            proofVersion: String?,
            storedPasswordHash: String?,
        ): Boolean = false

        override fun consume(challengeId: String) = Unit
    }

    private class NoOpPasswordService : PasswordService {
        override fun hashPassword(password: String): String =
            error("hashPassword should not be called in captcha hardening tests")

        override fun verifyPassword(password: String, storedHash: String): PasswordVerification =
            PasswordVerification(valid = false, needsRehash = false)

        override fun parsePasswordHash(storedHash: String) = null
    }
}
