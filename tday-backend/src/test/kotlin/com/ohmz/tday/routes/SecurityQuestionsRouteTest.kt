package com.ohmz.tday.routes

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.models.request.SecurityAnswerInput
import com.ohmz.tday.models.response.UserProfileResponse
import com.ohmz.tday.models.response.UserResponse
import com.ohmz.tday.plugins.AuthUserKey
import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.security.JwtUserClaims
import com.ohmz.tday.security.SecurityQuestion
import com.ohmz.tday.services.RegisterResult
import com.ohmz.tday.services.ResetOutcome
import com.ohmz.tday.services.SecurityQuestionService
import com.ohmz.tday.services.SecurityQuestionStatus
import com.ohmz.tday.services.UserService
import com.ohmz.tday.services.VerifyAnswersResult
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.*
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.assertEquals

private const val TEST_USER_ID = "user_123"
private const val SECURITY_QUESTIONS_PATH = "/api/user/security-questions"

// Three distinct valid questions with answers — passes SecurityQuestions.validateSelection(required = 3).
private const val THREE_ANSWERS =
    """[{"questionId":1,"answer":"a"},{"questionId":2,"answer":"b"},{"questionId":3,"answer":"c"}]"""

class SecurityQuestionsRouteTest {
    @Test
    fun `update with correct current password succeeds and stores questions`() = testApplication {
        val securityQuestions = FakeSecurityQuestionService(requireSecurityQuestions = false)
        val userService = FakeUserService(verifyResult = true)
        application { configureApp(userService, securityQuestions) }

        val response = client.post(SECURITY_QUESTIONS_PATH) {
            contentType(ContentType.Application.Json)
            setBody("""{"currentPassword":"Current123!","answers":$THREE_ANSWERS}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, securityQuestions.setQuestionsCalls)
    }

    @Test
    fun `update without current password is rejected when already configured`() = testApplication {
        val securityQuestions = FakeSecurityQuestionService(requireSecurityQuestions = false)
        val userService = FakeUserService(verifyResult = true)
        application { configureApp(userService, securityQuestions) }

        val response = client.post(SECURITY_QUESTIONS_PATH) {
            contentType(ContentType.Application.Json)
            setBody("""{"answers":$THREE_ANSWERS}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(0, securityQuestions.setQuestionsCalls)
    }

    @Test
    fun `update with wrong current password is rejected`() = testApplication {
        val securityQuestions = FakeSecurityQuestionService(requireSecurityQuestions = false)
        val userService = FakeUserService(verifyResult = false)
        application { configureApp(userService, securityQuestions) }

        val response = client.post(SECURITY_QUESTIONS_PATH) {
            contentType(ContentType.Application.Json)
            setBody("""{"currentPassword":"WrongPass1!","answers":$THREE_ANSWERS}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(0, securityQuestions.setQuestionsCalls)
    }

    @Test
    fun `first-time gate stores questions without a password`() = testApplication {
        val securityQuestions = FakeSecurityQuestionService(requireSecurityQuestions = true)
        val userService = FakeUserService(verifyResult = false)
        application { configureApp(userService, securityQuestions) }

        val response = client.post(SECURITY_QUESTIONS_PATH) {
            contentType(ContentType.Application.Json)
            setBody("""{"answers":$THREE_ANSWERS}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, securityQuestions.setQuestionsCalls)
    }

    private fun Application.configureApp(
        userService: UserService,
        securityQuestionService: SecurityQuestionService,
    ) {
        install(Koin) {
            modules(
                module {
                    single { userService }
                    single { securityQuestionService }
                },
            )
        }
        configureSerialization()
        intercept(ApplicationCallPipeline.Plugins) {
            if (call.attributes.getOrNull(AuthUserKey) == null) {
                call.attributes.put(
                    AuthUserKey,
                    JwtUserClaims(id = TEST_USER_ID, role = "USER", approvalStatus = "APPROVED"),
                )
            }
        }
        routing {
            route("/api") {
                userRoutes()
            }
        }
    }

    private class FakeSecurityQuestionService(
        private val requireSecurityQuestions: Boolean,
    ) : SecurityQuestionService {
        var setQuestionsCalls = 0
            private set

        override suspend fun statusFor(userId: String): SecurityQuestionStatus =
            SecurityQuestionStatus(questionIds = emptyList(), requireSecurityQuestions = requireSecurityQuestions)

        override suspend fun setQuestions(userId: String, answers: List<SecurityAnswerInput>) {
            setQuestionsCalls += 1
        }

        override suspend fun questionsForUsername(rawUsername: String): List<SecurityQuestion> = error("unused")
        override suspend fun lookupQuestionsForUsername(rawUsername: String): List<SecurityQuestion>? = error("unused")
        override suspend fun verifyAnswers(rawUsername: String, answers: List<SecurityAnswerInput>): VerifyAnswersResult = error("unused")
        override suspend fun verifyAndReset(rawUsername: String, answers: List<SecurityAnswerInput>, newPassword: String): ResetOutcome = error("unused")
        override suspend fun requestAdminReset(rawUsername: String) = error("unused")
    }

    private class FakeUserService(private val verifyResult: Boolean) : UserService {
        override suspend fun verifyCurrentPassword(userId: String, password: String): Either<AppError, Boolean> =
            verifyResult.right()

        override suspend fun getUser(userId: String): Either<AppError, UserResponse> = unused()
        override suspend fun updateEncryption(userId: String, enable: Boolean): Either<AppError, Unit> = unused()
        override suspend fun updateSymmetricKey(userId: String, key: String): Either<AppError, Unit> = unused()
        override suspend fun getProfile(userId: String): Either<AppError, UserProfileResponse> = unused()
        override suspend fun updateProfile(userId: String, name: String?, image: String?): Either<AppError, Unit> = unused()
        override suspend fun changePassword(userId: String, currentPassword: String, newPassword: String): Either<AppError, Boolean> = unused()
        override suspend fun register(fname: String, lname: String?, username: String, password: String, securityAnswers: List<SecurityAnswerInput>): Either<AppError, RegisterResult> = unused()
        override suspend fun findByUsername(username: String): Map<String, Any?>? = null
        override suspend fun isAdmin(userId: String): Boolean = false
        override suspend fun usernameExists(username: String): Boolean = false
        override suspend fun updatePasswordHash(userId: String, newHash: String) = Unit
        override suspend fun requiresPasswordChange(userId: String): Boolean = false

        private fun <T> unused(): T = AppError.BadRequest("unused").left() as T
    }
}
