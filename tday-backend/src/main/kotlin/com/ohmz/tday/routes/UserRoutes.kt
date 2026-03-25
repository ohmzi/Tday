package com.ohmz.tday.routes

import com.ohmz.tday.models.request.*
import com.ohmz.tday.plugins.*
import com.ohmz.tday.services.UserService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val BASE64_REGEX = Regex("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$")

fun Route.userRoutes() {
    route("/user") {
        get {
            val user = call.requireUser()
            val queriedUser = UserService.getUser(user.id)
                ?: throw UnauthorizedException("user not found")
            call.respond(HttpStatusCode.OK, mapOf("message" to "user found", "queriedUser" to queriedUser))
        }

        patch {
            val user = call.requireUser()
            val enableEncryption = call.request.queryParameters["enableEncryption"]
            if (enableEncryption != null) {
                UserService.updateEncryption(user.id, enableEncryption == "true")
                call.respond(HttpStatusCode.OK, mapOf("message" to "enable encryption updated"))
                return@patch
            }

            val body = call.receive<UserPatchKeyRequest>()
            val key = body.protectedSymmetricKey.trim()
            if (key.isEmpty() || key.length > 4096 || !BASE64_REGEX.matches(key)) {
                throw BadRequestException("protectedSymmetricKey is malformed")
            }
            UserService.updateSymmetricKey(user.id, key)
            call.respond(HttpStatusCode.OK, mapOf("message" to "protected symmetric key updated"))
        }

        route("/profile") {
            patch {
                val user = call.requireUser()
                val body = call.receive<UserProfilePatchRequest>()
                UserService.updateProfile(user.id, body.name, body.image)
                call.respond(HttpStatusCode.OK, mapOf("message" to "profile updated"))
            }
        }

        route("/change-password") {
            post {
                val user = call.requireUser()
                val body = call.receive<ChangePasswordRequest>()
                if (body.newPassword.length < 8) throw BadRequestException("password cannot be smaller than 8")
                if (!body.newPassword.any { it.isUpperCase() }) throw BadRequestException("password must have at least one uppercase letter")
                if (!body.newPassword.any { !it.isLetterOrDigit() }) throw BadRequestException("password must have at least one special character")

                val success = UserService.changePassword(user.id, body.currentPassword, body.newPassword)
                if (!success) throw BadRequestException("current password is incorrect")
                call.respond(HttpStatusCode.OK, mapOf("message" to "password changed"))
            }
        }
    }
}
