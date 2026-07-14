package com.ohmz.tday.routes

import arrow.core.Either
import arrow.core.raise.either
import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.di.inject
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.models.request.ChangePasswordRequest
import com.ohmz.tday.models.request.CreateApiKeyRequest
import com.ohmz.tday.models.request.SetSecurityQuestionsRequest
import com.ohmz.tday.models.request.UserPatchKeyRequest
import com.ohmz.tday.models.request.UserProfilePatchRequest
import com.ohmz.tday.models.response.SecurityQuestionStatusResponse
import com.ohmz.tday.plugins.authUser
import com.ohmz.tday.security.JwtService
import com.ohmz.tday.security.SecurityQuestions
import com.ohmz.tday.security.SessionControl
import com.ohmz.tday.security.issueSessionCookie
import com.ohmz.tday.services.ApiKeyScope
import com.ohmz.tday.services.CalendarFeedService
import com.ohmz.tday.services.CreateApiKeyResponse
import com.ohmz.tday.services.ListShareService
import com.ohmz.tday.services.SecurityQuestionService
import com.ohmz.tday.services.UserApiKeyService
import com.ohmz.tday.services.UserService
import com.ohmz.tday.shared.model.UserSearchResponse
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

private val BASE64_REGEX = Regex("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$")

fun Route.userRoutes() {
    val config by inject<AppConfig>()
    val jwtService by inject<JwtService>()
    val sessionControl by inject<SessionControl>()
    val userService by inject<UserService>()
    val userApiKeyService by inject<UserApiKeyService>()
    val calendarFeedService by inject<CalendarFeedService>()
    val securityQuestionService by inject<SecurityQuestionService>()
    val listShareService by inject<ListShareService>()

    route("/user") {
        get {
            call.withAuth { user ->
                userService.getUser(user.id)
                    .map { mapOf("message" to "user found", "queriedUser" to it) }
            }
        }

        // Username typeahead for the share-list member picker.
        get("/search") {
            call.withAuth { user ->
                val query = call.request.queryParameters["q"].orEmpty()
                listShareService.searchUsers(user.id, query)
                    .map { UserSearchResponse(users = it) }
            }
        }

        patch {
            call.withAuth { user ->
                val enableEncryption = call.request.queryParameters["enableEncryption"]
                if (enableEncryption != null) {
                    userService.updateEncryption(user.id, enableEncryption == "true")
                        .map { mapOf("message" to "enable encryption updated") }
                } else {
                    val body = call.receive<UserPatchKeyRequest>()
                    val key = body.protectedSymmetricKey.trim()
                    if (key.isEmpty() || key.length > 4096 || !BASE64_REGEX.matches(key)) {
                        return@withAuth Either.Left(AppError.BadRequest("protectedSymmetricKey is malformed"))
                    }
                    userService.updateSymmetricKey(user.id, key)
                        .map { mapOf("message" to "protected symmetric key updated") }
                }
            }
        }

        route("/profile") {
            patch {
                call.withAuth { user ->
                    val body = call.receive<UserProfilePatchRequest>()
                    userService.updateProfile(user.id, body.name, body.image)
                        .map { mapOf("message" to "profile updated") }
                }
            }
        }

        route("/change-password") {
            post {
                call.withAuth { user ->
                    either<AppError, Map<String, String>> {
                        val body = call.receive<ChangePasswordRequest>()
                        if (body.newPassword.length < 8) raise(AppError.BadRequest("password cannot be smaller than 8"))
                        if (!body.newPassword.any { it.isUpperCase() }) raise(AppError.BadRequest("password must have at least one uppercase letter"))
                        if (!body.newPassword.any { c -> !c.isLetterOrDigit() }) raise(AppError.BadRequest("password must have at least one special character"))

                        val success = userService.changePassword(user.id, body.currentPassword, body.newPassword).bind()
                        if (!success) raise(AppError.BadRequest("current password is incorrect"))

                        val currentClaims = call.authUser() ?: raise(AppError.Unauthorized())
                        // Password rotation invalidates the full-account API key too.
                        sessionControl.revokeUserSessions(user.id, revokeApiKeys = true)
                        val refreshedClaims = currentClaims.copy(
                            tokenVersion = (currentClaims.tokenVersion ?: 0) + 1,
                        )
                        call.issueSessionCookie(config, jwtService, refreshedClaims)
                        mapOf("message" to "password changed")
                    }
                }
            }
        }

        route("/security-questions") {
            get {
                call.withAuth { user ->
                    val status = securityQuestionService.statusFor(user.id)
                    Either.Right(
                        SecurityQuestionStatusResponse(
                            questionIds = status.questionIds,
                            requireSecurityQuestions = status.requireSecurityQuestions,
                        ),
                    )
                }
            }

            post {
                call.withAuth { user ->
                    either<AppError, Map<String, String>> {
                        val body = call.receive<SetSecurityQuestionsRequest>()
                        SecurityQuestions.validateSelection(body.answers, required = 3)?.let { raise(AppError.BadRequest(it)) }
                        // Changing already-configured questions (from settings) requires the
                        // current password; the first-time gate leaves them unset and skips this.
                        val status = securityQuestionService.statusFor(user.id)
                        if (!status.requireSecurityQuestions) {
                            val password = body.currentPassword?.takeIf { it.isNotBlank() }
                                ?: raise(AppError.BadRequest("current password is required"))
                            val valid = userService.verifyCurrentPassword(user.id, password).bind()
                            if (!valid) raise(AppError.BadRequest("current password is incorrect"))
                        }
                        securityQuestionService.setQuestions(user.id, body.answers)
                        mapOf("message" to "security questions updated")
                    }
                }
            }
        }

        route("/api-key") {
            // List every key the user owns (metadata only — secrets are never returned).
            get {
                call.withAuth { user ->
                    userApiKeyService.list(user.id)
                        .map { mapOf("keys" to it) }
                }
            }

            // Create a new scoped key. Additive — existing keys are left intact.
            post {
                call.withAuth { user ->
                    val body = runCatching { call.receive<CreateApiKeyRequest>() }
                        .getOrDefault(CreateApiKeyRequest())
                    val scope = ApiKeyScope.fromStorage(body.scope)
                    userApiKeyService.generate(
                        userId = user.id,
                        label = body.label,
                        scope = scope,
                        expiresInDays = body.expiresInDays,
                    ).map { CreateApiKeyResponse(message = "api key created", apiKey = it) }
                }
            }

            // Revoke a single key by id.
            delete("/{id}") {
                call.withAuth { user ->
                    val keyId = call.parameters["id"].orEmpty()
                    if (keyId.isBlank()) {
                        Either.Left(AppError.BadRequest("api key id is required"))
                    } else {
                        userApiKeyService.revokeKey(user.id, keyId)
                            .map { mapOf("message" to "api key revoked") }
                    }
                }
            }
        }

        route("/calendar-feed") {
            // Whether the user has an active feed token (secret never returned).
            get {
                call.withAuth { user ->
                    calendarFeedService.status(user.id)
                        .map { mapOf("status" to it) }
                }
            }

            // Generate (or rotate) the feed token. The plaintext token is returned once;
            // the client builds the subscribe URL as `<origin>/calendar/<token>.ics`.
            post {
                call.withAuth { user ->
                    calendarFeedService.generate(user.id)
                        .map { mapOf("message" to "calendar feed created", "feed" to it) }
                }
            }

            delete {
                call.withAuth { user ->
                    calendarFeedService.revoke(user.id)
                        .map { mapOf("message" to "calendar feed revoked") }
                }
            }
        }
    }
}
