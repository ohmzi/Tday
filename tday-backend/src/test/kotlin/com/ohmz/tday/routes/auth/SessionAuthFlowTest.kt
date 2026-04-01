package com.ohmz.tday.routes.auth

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.models.response.UserProfileResponse
import com.ohmz.tday.models.response.UserResponse
import com.ohmz.tday.plugins.AuthUserKey
import com.ohmz.tday.plugins.configureSecurity
import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.routes.userRoutes
import com.ohmz.tday.security.AuthCachedUser
import com.ohmz.tday.security.AuthThrottle
import com.ohmz.tday.security.AuthUserCache
import com.ohmz.tday.security.CaptchaResult
import com.ohmz.tday.security.CaptchaService
import com.ohmz.tday.security.CredentialEnvelope
import com.ohmz.tday.security.CredentialEnvelopeInput
import com.ohmz.tday.security.CredentialPublicKeyDescriptor
import com.ohmz.tday.security.DecryptedCredentials
import com.ohmz.tday.security.JwtService
import com.ohmz.tday.security.JwtServiceImpl
import com.ohmz.tday.security.JwtUserClaims
import com.ohmz.tday.security.PasswordHashMetadata
import com.ohmz.tday.security.PasswordProof
import com.ohmz.tday.security.PasswordProofChallengePayload
import com.ohmz.tday.security.PasswordService
import com.ohmz.tday.security.PasswordVerification
import com.ohmz.tday.security.SecurityEventLogger
import com.ohmz.tday.security.SessionControl
import com.ohmz.tday.security.ThrottleAction
import com.ohmz.tday.security.ThrottleResult
import com.ohmz.tday.security.sessionCookieName
import com.ohmz.tday.security.testAppConfig
import com.ohmz.tday.services.RegisterResult
import com.ohmz.tday.services.UserService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.*
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TEST_USER_ID = "user_123"
private const val TEST_USER_EMAIL = "user@example.com"
private const val TEST_ROLE = "USER"
private const val TEST_APPROVAL_STATUS = "APPROVED"
private const val TEST_TIME_ZONE = "America/Toronto"
private const val API_ROUTE_PATH = "/api"
private const val AUTH_ROUTE_PATH = "/auth"
private const val AUTH_CALLBACK_PATH = "/api/auth/callback/credentials"
private const val AUTH_SESSION_PATH = "/api/auth/session"
private const val AUTH_LOGOUT_PATH = "/api/auth/logout"
private const val CHANGE_PASSWORD_PATH = "/api/user/change-password"
private const val UNUSED_ERROR_MESSAGE = "unused"
private const val SESSION_TEST_ISSUED_AT = "2026-04-01T00:00:00Z"

class SessionAuthFlowTest {
    @Test
    fun `credentials callback sets session cookie with configured max age`() = testApplication {
        val config = testAppConfig(sessionMaxAgeSec = 2_592_000)
        val jwtService = JwtServiceImpl(config)
        val userService = FakeUserService(
            loginUser = mapOf(
                "id" to TEST_USER_ID,
                "email" to TEST_USER_EMAIL,
                "password" to "stored-hash",
                "name" to "Test User",
                "role" to TEST_ROLE,
                "approvalStatus" to TEST_APPROVAL_STATUS,
                "tokenVersion" to 4,
                "timeZone" to TEST_TIME_ZONE,
            ),
        )

        application {
            configureCredentialsCallbackTestApp(
                config = config,
                jwtService = jwtService,
                userService = userService,
            )
        }

        val response = client.post(AUTH_CALLBACK_PATH) {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$TEST_USER_EMAIL","password":"Password123!"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val cookieHeader = response.requireCookieHeader(sessionCookieName(config.isProduction))
        assertTrue(cookieHeader.contains("Max-Age=2592000"))
    }

    @Test
    fun `near expiry authenticated request renews cookie and preserves session start`() = testApplication {
        val config = testAppConfig(
            sessionMaxAgeSec = 3_600,
            sessionAbsoluteMaxAgeSec = 5_400,
            sessionRenewThresholdSec = 600,
        )
        val issuedAt = Instant.parse(SESSION_TEST_ISSUED_AT)
        val initialJwtService = JwtServiceImpl(config, fixedClock(issuedAt))
        val initialToken = initialJwtService.encode(
            JwtUserClaims(
                id = TEST_USER_ID,
                email = TEST_USER_EMAIL,
                tokenVersion = 1,
            ),
        )

        val authUserCache = AuthUserCache().apply {
            put(
                TEST_USER_ID,
                AuthCachedUser(
                    role = TEST_ROLE,
                    approvalStatus = TEST_APPROVAL_STATUS,
                    tokenVersion = 1,
                    timeZone = TEST_TIME_ZONE,
                ),
            )
        }
        val renewalJwtService = JwtServiceImpl(
            config,
            fixedClock(issuedAt.plusSeconds(3_301)),
        )

        application {
            configureProtectedSessionTestApp(
                config = config,
                jwtService = renewalJwtService,
                authUserCache = authUserCache,
            )
        }

        val response = client.get(AUTH_SESSION_PATH) {
            header(HttpHeaders.Cookie, "${sessionCookieName(config.isProduction)}=$initialToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val cookieHeader = response.requireCookieHeader(sessionCookieName(config.isProduction))
        val renewedToken = cookieHeader.cookieValue()
        val renewedClaims = renewalJwtService.decode(renewedToken)
        assertNotNull(renewedClaims)
        assertEquals(issuedAt.epochSecond, renewedClaims.sessionStartedAtEpochSec)
    }

    @Test
    fun `far from expiry authenticated request does not rewrite cookie`() = testApplication {
        val config = testAppConfig(
            sessionMaxAgeSec = 3_600,
            sessionAbsoluteMaxAgeSec = 5_400,
            sessionRenewThresholdSec = 600,
        )
        val issuedAt = Instant.parse(SESSION_TEST_ISSUED_AT)
        val initialJwtService = JwtServiceImpl(config, fixedClock(issuedAt))
        val initialToken = initialJwtService.encode(
            JwtUserClaims(
                id = TEST_USER_ID,
                email = TEST_USER_EMAIL,
                tokenVersion = 1,
            ),
        )

        val authUserCache = AuthUserCache().apply {
            put(
                TEST_USER_ID,
                AuthCachedUser(
                    role = TEST_ROLE,
                    approvalStatus = TEST_APPROVAL_STATUS,
                    tokenVersion = 1,
                    timeZone = TEST_TIME_ZONE,
                ),
            )
        }
        val requestJwtService = JwtServiceImpl(
            config,
            fixedClock(issuedAt.plusSeconds(300)),
        )

        application {
            configureProtectedSessionTestApp(
                config = config,
                jwtService = requestJwtService,
                authUserCache = authUserCache,
            )
        }

        val response = client.get(AUTH_SESSION_PATH) {
            header(HttpHeaders.Cookie, "${sessionCookieName(config.isProduction)}=$initialToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(response.hasCookieHeader(sessionCookieName(config.isProduction)))
    }

    @Test
    fun `absolute expired session is rejected and cookie is cleared`() = testApplication {
        val config = testAppConfig(
            sessionMaxAgeSec = 3_600,
            sessionAbsoluteMaxAgeSec = 5_400,
            sessionRenewThresholdSec = 600,
        )
        val issuedAt = Instant.parse(SESSION_TEST_ISSUED_AT)
        val initialJwtService = JwtServiceImpl(config, fixedClock(issuedAt))
        val initialToken = initialJwtService.encode(
            JwtUserClaims(
                id = TEST_USER_ID,
                email = TEST_USER_EMAIL,
                tokenVersion = 1,
            ),
        )

        val renewalJwtService = JwtServiceImpl(
            config,
            fixedClock(issuedAt.plusSeconds(2_000)),
        )
        val initialClaims = assertNotNull(initialJwtService.decode(initialToken))
        val renewedToken = renewalJwtService.encode(initialClaims)

        val authUserCache = AuthUserCache().apply {
            put(
                TEST_USER_ID,
                AuthCachedUser(
                    role = TEST_ROLE,
                    approvalStatus = TEST_APPROVAL_STATUS,
                    tokenVersion = 1,
                    timeZone = TEST_TIME_ZONE,
                ),
            )
        }
        val expiredJwtService = JwtServiceImpl(
            config,
            fixedClock(issuedAt.plusSeconds(5_500)),
        )

        application {
            configureProtectedSessionTestApp(
                config = config,
                jwtService = expiredJwtService,
                authUserCache = authUserCache,
            )
        }

        val response = client.get(AUTH_SESSION_PATH) {
            header(HttpHeaders.Cookie, "${sessionCookieName(config.isProduction)}=$renewedToken")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val cookieHeader = response.requireCookieHeader(sessionCookieName(config.isProduction))
        assertTrue(cookieHeader.contains("Max-Age=0"))
    }

    @Test
    fun `logout clears cookie and revokes user sessions`() = testApplication {
        val config = testAppConfig()
        val sessionControl = RecordingSessionControl()
        val authUser = JwtUserClaims(
            id = TEST_USER_ID,
            tokenVersion = 2,
            role = TEST_ROLE,
            approvalStatus = TEST_APPROVAL_STATUS,
        )

        application {
            configureLogoutTestApp(
                config = config,
                sessionControl = sessionControl,
                authUser = authUser,
            )
        }

        val response = client.post(AUTH_LOGOUT_PATH)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf(TEST_USER_ID), sessionControl.revokedUserIds)
        val cookieHeader = response.requireCookieHeader(sessionCookieName(config.isProduction))
        assertTrue(cookieHeader.contains("Max-Age=0"))
    }

    @Test
    fun `password change revokes prior sessions and issues a refreshed cookie`() = testApplication {
        val config = testAppConfig()
        val jwtService = JwtServiceImpl(config, fixedClock(Instant.parse("2026-04-01T09:30:00Z")))
        val sessionControl = RecordingSessionControl()
        val authUser = JwtUserClaims(
            id = TEST_USER_ID,
            email = TEST_USER_EMAIL,
            role = TEST_ROLE,
            approvalStatus = TEST_APPROVAL_STATUS,
            tokenVersion = 4,
            sessionStartedAtEpochSec = Instant.parse("2026-03-20T09:30:00Z").epochSecond,
        )
        val userService = FakeUserService(changePasswordResult = true)

        application {
            configurePasswordChangeTestApp(
                config = config,
                jwtService = jwtService,
                sessionControl = sessionControl,
                userService = userService,
                authUser = authUser,
            )
        }

        val response = client.post(CHANGE_PASSWORD_PATH) {
            contentType(ContentType.Application.Json)
            setBody("""{"currentPassword":"Current123!","newPassword":"Updated123!"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf(TEST_USER_ID), sessionControl.revokedUserIds)
        val cookieHeader = response.requireCookieHeader(sessionCookieName(config.isProduction))
        val refreshedClaims = jwtService.decode(cookieHeader.cookieValue())
        assertNotNull(refreshedClaims)
        assertEquals(5, refreshedClaims.tokenVersion)
        assertEquals(authUser.sessionStartedAtEpochSec, refreshedClaims.sessionStartedAtEpochSec)
    }

    private fun Application.configureCredentialsCallbackTestApp(
        config: AppConfig,
        jwtService: JwtService,
        userService: UserService,
    ) {
        install(Koin) {
            modules(
                module {
                    single { config }
                    single<JwtService> { jwtService }
                    single<UserService> { userService }
                    single<AuthThrottle> { AllowAllAuthThrottle() }
                    single<CaptchaService> { AllowAllCaptchaService() }
                    single<SecurityEventLogger> { NoOpSecurityEventLogger() }
                    single<CredentialEnvelope> { FakeCredentialEnvelope() }
                    single<PasswordProof> { FakePasswordProof() }
                    single<PasswordService> { ValidPasswordService() }
                },
            )
        }
        configureSerialization()
        routing {
            route(API_ROUTE_PATH) {
                route(AUTH_ROUTE_PATH) {
                    credentialsCallbackRoutes()
                }
            }
        }
    }

    private fun Application.configureProtectedSessionTestApp(
        config: AppConfig,
        jwtService: JwtService,
        authUserCache: AuthUserCache,
    ) {
        install(Koin) {
            modules(
                module {
                    single { config }
                    single<JwtService> { jwtService }
                    single { authUserCache }
                    single<AuthThrottle> { AllowAllAuthThrottle() }
                    single<SecurityEventLogger> { NoOpSecurityEventLogger() }
                },
            )
        }
        configureSerialization()
        configureSecurity()
        routing {
            route(API_ROUTE_PATH) {
                route(AUTH_ROUTE_PATH) {
                    sessionRoutes()
                }
            }
        }
    }

    private fun Application.configureLogoutTestApp(
        config: AppConfig,
        sessionControl: RecordingSessionControl,
        authUser: JwtUserClaims,
    ) {
        install(Koin) {
            modules(
                module {
                    single { config }
                    single<SessionControl> { sessionControl }
                },
            )
        }
        configureSerialization()
        intercept(ApplicationCallPipeline.Plugins) {
            if (call.attributes.getOrNull(AuthUserKey) == null) {
                call.attributes.put(AuthUserKey, authUser)
            }
        }
        routing {
            route(API_ROUTE_PATH) {
                route(AUTH_ROUTE_PATH) {
                    logoutRoutes()
                }
            }
        }
    }

    private fun Application.configurePasswordChangeTestApp(
        config: AppConfig,
        jwtService: JwtService,
        sessionControl: RecordingSessionControl,
        userService: UserService,
        authUser: JwtUserClaims,
    ) {
        install(Koin) {
            modules(
                module {
                    single { config }
                    single<JwtService> { jwtService }
                    single<SessionControl> { sessionControl }
                    single<UserService> { userService }
                },
            )
        }
        configureSerialization()
        intercept(ApplicationCallPipeline.Plugins) {
            if (call.attributes.getOrNull(AuthUserKey) == null) {
                call.attributes.put(AuthUserKey, authUser)
            }
        }
        routing {
            route(API_ROUTE_PATH) {
                userRoutes()
            }
        }
    }

    private fun fixedClock(instant: Instant): Clock =
        Clock.fixed(instant, ZoneOffset.UTC)

    private fun io.ktor.client.statement.HttpResponse.requireCookieHeader(cookieName: String): String {
        return headers.getAll(HttpHeaders.SetCookie)
            ?.firstOrNull { it.startsWith("$cookieName=") }
            ?: error("Expected Set-Cookie header for $cookieName")
    }

    private fun io.ktor.client.statement.HttpResponse.hasCookieHeader(cookieName: String): Boolean {
        return headers.getAll(HttpHeaders.SetCookie)
            ?.any { it.startsWith("$cookieName=") }
            ?: false
    }

    private fun String.cookieValue(): String =
        substringAfter('=').substringBefore(';')

    private class AllowAllAuthThrottle : AuthThrottle {
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
        ): Boolean = false

        override suspend fun recordSuccessSignal(
            request: io.ktor.server.request.ApplicationRequest,
            identifier: String?,
        ) = Unit

        override fun formatRetryWait(seconds: Int): String = "${seconds}s"
    }

    private class AllowAllCaptchaService : CaptchaService {
        override fun isConfigured(): Boolean = false

        override suspend fun verify(
            token: String?,
            request: io.ktor.server.request.ApplicationRequest,
            action: String,
        ): CaptchaResult = CaptchaResult(ok = true)

        override fun extractTokenFromJson(body: kotlinx.serialization.json.JsonObject?): String? = null
    }

    private class NoOpSecurityEventLogger : SecurityEventLogger {
        override suspend fun log(reasonCode: String, details: Map<String, Any?>) = Unit
    }

    private class FakeCredentialEnvelope : CredentialEnvelope {
        override fun getPublicKeyDescriptor(): CredentialPublicKeyDescriptor {
            return CredentialPublicKeyDescriptor(
                version = "1",
                algorithm = "RSA-OAEP-256+A256GCM",
                keyId = "test-key",
                publicKey = "test-public-key",
            )
        }

        override fun decrypt(envelope: CredentialEnvelopeInput): DecryptedCredentials {
            error("credential envelope decryption is not used in this test")
        }
    }

    private class FakePasswordProof : PasswordProof {
        override fun normalizeEmail(value: String?): String? = value?.trim()?.lowercase()

        override fun issueChallenge(email: String, storedPasswordHash: String?): PasswordProofChallengePayload {
            error("password proof challenge is not used in this test")
        }

        override fun verify(
            email: String,
            challengeId: String,
            proofHex: String,
            proofVersion: String?,
            storedPasswordHash: String?,
        ): Boolean = false

        override fun consume(challengeId: String) = Unit
    }

    private class ValidPasswordService : PasswordService {
        override fun hashPassword(plainTextPassword: String): String = "rehash:$plainTextPassword"

        override fun verifyPassword(plainTextPassword: String, storedHash: String): PasswordVerification =
            PasswordVerification(valid = true, needsRehash = false)

        override fun parsePasswordHash(storedHash: String): PasswordHashMetadata? = null
    }

    private class RecordingSessionControl : SessionControl {
        val revokedUserIds = mutableListOf<String>()

        override suspend fun revokeUserSessions(userId: String) {
            revokedUserIds += userId
        }
    }

    private class FakeUserService(
        private val loginUser: Map<String, Any?>? = null,
        private val changePasswordResult: Boolean = true,
    ) : UserService {
        override suspend fun getUser(userId: String): Either<AppError, UserResponse> =
            AppError.NotFound(UNUSED_ERROR_MESSAGE).left()

        override suspend fun updateEncryption(userId: String, enable: Boolean): Either<AppError, Unit> =
            Unit.right()

        override suspend fun updateSymmetricKey(userId: String, key: String): Either<AppError, Unit> =
            Unit.right()

        override suspend fun getProfile(userId: String): Either<AppError, UserProfileResponse> =
            AppError.NotFound(UNUSED_ERROR_MESSAGE).left()

        override suspend fun updateProfile(userId: String, name: String?, image: String?): Either<AppError, Unit> =
            Unit.right()

        override suspend fun changePassword(
            userId: String,
            currentPassword: String,
            newPassword: String,
        ): Either<AppError, Boolean> = changePasswordResult.right()

        override suspend fun register(
            fname: String,
            lname: String?,
            email: String,
            password: String,
        ): Either<AppError, RegisterResult> = AppError.BadRequest(UNUSED_ERROR_MESSAGE).left()

        override suspend fun findByEmail(email: String): Map<String, Any?>? = loginUser

        override suspend fun isAdmin(userId: String): Boolean = false

        override suspend fun emailExists(email: String): Boolean = false

        override suspend fun updatePasswordHash(userId: String, newHash: String) = Unit
    }
}
