package com.ohmz.tday.routes

import arrow.core.Either
import arrow.core.raise.either
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.models.request.*
import com.ohmz.tday.services.UserService
import io.ktor.server.request.*
import io.ktor.server.routing.*
import com.ohmz.tday.di.inject

private val BASE64_REGEX = Regex("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$")

fun Route.userRoutes() {
    val userService by inject<UserService>()

    route("/user") {
        get {
            call.withAuth { user ->
                userService.getUser(user.id)
                    .map { mapOf("message" to "user found", "queriedUser" to it) }
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
                        mapOf("message" to "password changed")
                    }
                }
            }
        }
    }
}
