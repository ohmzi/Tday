package com.ohmz.tday.plugins

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.models.response.AppConfigResponse
import com.ohmz.tday.models.response.TodoResponse
import com.ohmz.tday.plugins.AuthUserKey
import com.ohmz.tday.routes.mobileProbeRoutes
import com.ohmz.tday.routes.todoRoutes
import com.ohmz.tday.routes.userRoutes
import com.ohmz.tday.security.JwtService
import com.ohmz.tday.security.JwtServiceImpl
import com.ohmz.tday.security.JwtUserClaims
import com.ohmz.tday.security.RequestRateLimitAssessment
import com.ohmz.tday.security.RequestRateLimitPolicy
import com.ohmz.tday.security.RequestRateLimitSubjectType
import com.ohmz.tday.security.RequestRateLimiter
import com.ohmz.tday.security.SessionControl
import com.ohmz.tday.security.testAppConfig
import com.ohmz.tday.services.AppConfigService
import com.ohmz.tday.services.NlpParseResult
import com.ohmz.tday.services.TodoNlpService
import com.ohmz.tday.services.TodoService
import com.ohmz.tday.services.TodoSummaryService
import com.ohmz.tday.services.UserService
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.*
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.time.LocalDateTime
import kotlin.test.assertEquals

class RateLimitingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `global API limiter blocks approved private route`() = testApplication {
        val limiter = RecordingRequestRateLimiter(blockedPolicies = setOf("api_global"))

        application {
            configureRateLimitHarness(
                requestRateLimiter = limiter,
                authUser = approvedUser(),
            )
        }

        val response = client.get("/api/private")

        assertRateLimited(response, expectedReason = "api_rate_limit", expectedRetryAfter = "11")
        assertEquals(listOf("api_global"), limiter.recordedPolicies)
    }

    @Test
    fun `infra limiter blocks health probe`() = testApplication {
        val limiter = RecordingRequestRateLimiter(blockedPolicies = setOf("infra"))

        application {
            configureRateLimitHarness(
                requestRateLimiter = limiter,
                authUser = null,
            )
        }

        val response = client.get("/health")

        assertRateLimited(response, expectedReason = "infra_rate_limit", expectedRetryAfter = "11")
        assertEquals(listOf("infra"), limiter.recordedPolicies)
    }

    @Test
    fun `mobile probe applies both global and infra policies`() = testApplication {
        val limiter = RecordingRequestRateLimiter(blockedPolicies = setOf("infra"))

        application {
            configureRateLimitHarness(
                requestRateLimiter = limiter,
                authUser = null,
            )
        }

        val response = client.get("/api/mobile/probe")

        assertRateLimited(response, expectedReason = "infra_rate_limit", expectedRetryAfter = "11")
        assertEquals(listOf("api_global", "infra"), limiter.recordedPolicies)
    }

    @Test
    fun `summary limiter short circuits before summary generation`() = testApplication {
        val limiter = RecordingRequestRateLimiter(blockedPolicies = setOf("todo_summary"))
        val todoService = RecordingTodoService()
        val summaryService = RecordingTodoSummaryService()

        application {
            configureRateLimitHarness(
                requestRateLimiter = limiter,
                authUser = approvedUser(),
                todoService = todoService,
                todoSummaryService = summaryService,
            )
        }

        val response = client.post("/api/todo/summary") {
            contentType(ContentType.Application.Json)
            setBody("""{"timeZone":"UTC"}""")
        }

        assertRateLimited(response, expectedReason = "summary_rate_limit", expectedRetryAfter = "11")
        assertEquals(0, todoService.timelineCalls)
        assertEquals(0, summaryService.generateCalls)
        assertEquals(listOf("api_global", "todo_summary"), limiter.recordedPolicies)
    }

    @Test
    fun `change password limiter blocks before handler executes`() = testApplication {
        val limiter = RecordingRequestRateLimiter(blockedPolicies = setOf("change_password"))
        val userService = RecordingUserService()

        application {
            configureRateLimitHarness(
                requestRateLimiter = limiter,
                authUser = approvedUser(),
                userService = userService,
            )
        }

        val response = client.post("/api/user/change-password") {
            contentType(ContentType.Application.Json)
            setBody("""{"currentPassword":"Password123!","newPassword":"Updated123!"}""")
        }

        assertRateLimited(response, expectedReason = "change_password_rate_limit", expectedRetryAfter = "11")
        assertEquals(0, userService.changePasswordCalls)
        assertEquals(listOf("api_global", "change_password"), limiter.recordedPolicies)
    }

    private fun Application.configureRateLimitHarness(
        requestRateLimiter: RequestRateLimiter,
        config: AppConfig = testAppConfig(),
        authUser: JwtUserClaims?,
        todoService: RecordingTodoService = RecordingTodoService(),
        todoSummaryService: RecordingTodoSummaryService = RecordingTodoSummaryService(),
        userService: RecordingUserService = RecordingUserService(),
    ) {
        install(Koin) {
            modules(
                module {
                    single { config }
                    single<RequestRateLimiter> { requestRateLimiter }
                    single<JwtService> { JwtServiceImpl(config) }
                    single<SessionControl> { NoOpSessionControl() }
                    single<UserService> { userService }
                    single<TodoService> { todoService }
                    single<TodoSummaryService> { todoSummaryService }
                    single<TodoNlpService> { NoOpTodoNlpService() }
                    single<AppConfigService> { EnabledAppConfigService() }
                },
            )
        }
        configureSerialization()
        if (authUser != null) {
            intercept(ApplicationCallPipeline.Plugins) {
                if (call.attributes.getOrNull(AuthUserKey) == null) {
                    call.attributes.put(AuthUserKey, authUser)
                }
            }
        }
        configureRateLimiting()
        routing {
            get("/health") {
                call.respond(mapOf("status" to "ok"))
            }
            route("/api") {
                get("/private") {
                    call.withAuth {
                        mapOf("ok" to true).right()
                    }
                }
                mobileProbeRoutes()
                todoRoutes()
                userRoutes()
            }
        }
    }

    private suspend fun assertRateLimited(
        response: io.ktor.client.statement.HttpResponse,
        expectedReason: String,
        expectedRetryAfter: String,
    ) {
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertEquals(expectedRetryAfter, response.headers["Retry-After"])
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(expectedReason, payload.getValue("reason").jsonPrimitive.content)
        assertEquals(expectedRetryAfter, payload.getValue("retryAfterSeconds").jsonPrimitive.content)
    }

    private fun approvedUser(): JwtUserClaims =
        JwtUserClaims(
            id = "user_123",
            email = "user@example.com",
            role = "USER",
            approvalStatus = "APPROVED",
            timeZone = "UTC",
        )

    private class RecordingRequestRateLimiter(
        private val blockedPolicies: Set<String> = emptySet(),
    ) : RequestRateLimiter {
        val recordedPolicies = mutableListOf<String>()

        override suspend fun assess(
            policy: RequestRateLimitPolicy,
            request: io.ktor.server.request.ApplicationRequest,
            authenticatedUserId: String?,
        ): RequestRateLimitAssessment {
            recordedPolicies += policy.name
            if (policy.name in blockedPolicies) {
                return RequestRateLimitAssessment(
                    allowed = false,
                    policyName = policy.name,
                    reasonCode = policy.reasonCode,
                    retryAfterSeconds = 11,
                    subjectType = if (authenticatedUserId.isNullOrBlank()) {
                        RequestRateLimitSubjectType.ip
                    } else {
                        RequestRateLimitSubjectType.user
                    },
                )
            }

            return RequestRateLimitAssessment(
                allowed = true,
                policyName = policy.name,
            )
        }
    }

    private class RecordingTodoService : TodoService {
        var timelineCalls = 0

        override suspend fun create(
            userId: String,
            title: String,
            description: String?,
            priority: String,
            due: LocalDateTime,
            rrule: String?,
            listID: String?,
        ): Either<AppError, TodoResponse> = unsupported()

        override suspend fun getByDateRange(
            userId: String,
            start: Long,
            end: Long,
            timeZone: String,
        ): Either<AppError, List<TodoResponse>> = emptyList<TodoResponse>().right()

        override suspend fun getTimeline(
            userId: String,
            timeZone: String,
            recurringFutureDays: Int,
        ): Either<AppError, List<TodoResponse>> {
            timelineCalls += 1
            return listOf(
                TodoResponse(
                    id = "todo_1",
                    title = "Task",
                    description = null,
                    priority = "Low",
                    due = "2026-04-02T10:00:00",
                    listID = null,
                    completed = false,
                    pinned = false,
                ),
            ).right()
        }

        override suspend fun update(userId: String, id: String, fields: Map<String, Any?>): Either<AppError, Unit> = Unit.right()

        override suspend fun delete(userId: String, id: String): Either<AppError, Int> = 1.right()

        override suspend fun completeTodo(
            userId: String,
            todoId: String,
            instanceDate: LocalDateTime?,
        ): Either<AppError, Unit> = Unit.right()

        override suspend fun uncompleteTodo(
            userId: String,
            todoId: String,
            instanceDate: LocalDateTime?,
        ): Either<AppError, Unit> = Unit.right()

        override suspend fun prioritize(userId: String, todoId: String, priority: String): Either<AppError, Unit> = Unit.right()

        override suspend fun reorder(userId: String, todoId: String, newOrder: Int): Either<AppError, Unit> = Unit.right()

        override suspend fun getOverdue(userId: String, timeZone: String): Either<AppError, List<TodoResponse>> = emptyList<TodoResponse>().right()

        override suspend fun patchInstance(
            userId: String,
            todoId: String,
            instanceDate: LocalDateTime,
            fields: Map<String, Any?>,
        ): Either<AppError, Unit> = Unit.right()

        override suspend fun deleteInstance(
            userId: String,
            todoId: String,
            instanceDate: LocalDateTime,
        ): Either<AppError, Unit> = Unit.right()

        private fun <T> unsupported(): Either<AppError, T> =
            Either.Left(AppError.Internal("unsupported in rate limit test"))
    }

    private class RecordingTodoSummaryService : TodoSummaryService {
        var generateCalls = 0

        override suspend fun generateSummary(prompt: String): String? {
            generateCalls += 1
            return "summary"
        }

        override suspend fun isHealthy(): Boolean = true
    }

    private class RecordingUserService : UserService {
        var changePasswordCalls = 0

        override suspend fun getUser(userId: String): Either<AppError, com.ohmz.tday.models.response.UserResponse> = unsupported()

        override suspend fun updateEncryption(userId: String, enable: Boolean): Either<AppError, Unit> = Unit.right()

        override suspend fun updateSymmetricKey(userId: String, key: String): Either<AppError, Unit> = Unit.right()

        override suspend fun getProfile(userId: String): Either<AppError, com.ohmz.tday.models.response.UserProfileResponse> = unsupported()

        override suspend fun updateProfile(userId: String, name: String?, image: String?): Either<AppError, Unit> = Unit.right()

        override suspend fun changePassword(userId: String, currentPassword: String, newPassword: String): Either<AppError, Boolean> {
            changePasswordCalls += 1
            return true.right()
        }

        override suspend fun register(
            fname: String,
            lname: String?,
            email: String,
            password: String,
        ): Either<AppError, com.ohmz.tday.services.RegisterResult> = unsupported()

        override suspend fun findByEmail(email: String): Map<String, Any?>? = null

        override suspend fun isAdmin(userId: String): Boolean = false

        override suspend fun emailExists(email: String): Boolean = false

        override suspend fun updatePasswordHash(userId: String, newHash: String) = Unit

        private fun <T> unsupported(): Either<AppError, T> =
            Either.Left(AppError.Internal("unsupported in rate limit test"))
    }

    private class EnabledAppConfigService : AppConfigService {
        override suspend fun getGlobalConfig(): Either<AppError, AppConfigResponse> =
            AppConfigResponse(aiSummaryEnabled = true, updatedAt = null).right()

        override suspend fun setAiSummaryEnabled(
            enabled: Boolean,
            updatedById: String?,
        ): Either<AppError, AppConfigResponse> =
            AppConfigResponse(aiSummaryEnabled = enabled, updatedAt = null).right()
    }

    private class NoOpTodoNlpService : TodoNlpService {
        override fun parse(
            text: String,
            locale: String?,
            referenceEpochMs: Long?,
            timezoneOffsetMinutes: Int?,
            defaultDurationMinutes: Int?,
        ): NlpParseResult = NlpParseResult(text, null, null, null)
    }

    private class NoOpSessionControl : SessionControl {
        override suspend fun revokeUserSessions(userId: String) = Unit
    }
}
