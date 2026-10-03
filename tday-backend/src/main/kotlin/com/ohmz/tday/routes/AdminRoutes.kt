package com.ohmz.tday.routes

import arrow.core.raise.either
import com.ohmz.tday.di.inject
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.requireAdminAccess
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.models.request.ServerTelemetryPatchRequest
import com.ohmz.tday.observability.TdayObservability
import com.ohmz.tday.security.AbuseGuard
import com.ohmz.tday.services.AdminService
import com.ohmz.tday.services.InstanceSettingsService
import com.ohmz.tday.services.SecurityAlertService
import com.ohmz.tday.testcrash.BackendTestCrash
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.adminRoutes() {
    val adminService by inject<AdminService>()
    val abuseGuard by inject<AbuseGuard>()
    val securityAlertService by inject<SecurityAlertService>()
    val instanceSettingsService by inject<InstanceSettingsService>()

    route("/admin") {
        route("/security") {
            route("/alerts") {
                get {
                    call.withAuth { user ->
                        securityAlertService.listRecent(user).map { mapOf("alerts" to it) }
                    }
                }
            }

            route("/blocks") {
                get {
                    call.withAuth { user ->
                        abuseGuard.listActiveBlocks(user).map { mapOf("blocks" to it) }
                    }
                }

                // The manual escape hatch: whatever the automation decided, an admin can undo it.
                route("/{id}/clear") {
                    post {
                        call.withAuth { user ->
                            val blockId = call.parameters["id"]
                                ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest("block id is required"))
                            abuseGuard.clearBlock(blockId, user).map { mapOf("message" to it) }
                        }
                    }
                }
            }
        }

        // Whether this server may send its own error reports to Sentry. Web-only: it concerns
        // the operator's server, not any one device, so the mobile apps have no counterpart.
        route("/telemetry") {
            get {
                call.withAuth { user ->
                    instanceSettingsService.serverTelemetry(user)
                }
            }

            patch {
                call.withAuth { user ->
                    either {
                        val body = call.receive<ServerTelemetryPatchRequest>()
                        instanceSettingsService.setServerTelemetry(body.enabled, user).bind()
                    }
                }
            }

            // TEST-CRASH: lets an admin make this server fail on purpose, so a report travels every
            // step it really takes — the handler, StatusPages, the SDK, the scrubber, the operator's
            // own gate — and lands in the backend's Sentry project. The message is the shape the
            // mobile and web triggers use, so one search finds every platform:
            // `IllegalStateException: TEST-CRASH TC-BACKEND-CRASH: unhandled admin error`.
            post("/test-crash") {
                call.withAuth<Unit> { user ->
                    either {
                        user.requireAdminAccess().bind()
                        throw IllegalStateException(BackendTestCrash.CRASH.message)
                    }
                }
            }

            // The same trigger down the other path this server reports by: the handler catches the
            // failure and reports it itself, the way a caught failure anywhere else is reported.
            post("/test-error") {
                call.withAuth<Map<String, Boolean>>(status = HttpStatusCode.InternalServerError) { user ->
                    either {
                        user.requireAdminAccess().bind()
                        TdayObservability.captureException(
                            IllegalStateException(BackendTestCrash.HANDLED.message),
                            operation = BackendTestCrash.OPERATION,
                        )
                        mapOf("reported" to true)
                    }
                }
            }
        }

        route("/users") {
            get {
                call.withAuth { user ->
                    adminService.listUsers(user).map { mapOf("users" to it) }
                }
            }

            route("/{id}") {
                patch {
                    call.withAuth { user ->
                        val targetId = call.parameters["id"]
                            ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest("user id is required"))
                        adminService.approveUser(targetId, user).map { mapOf("message" to it) }
                    }
                }

                delete {
                    call.withAuth { user ->
                        val targetId = call.parameters["id"]
                            ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest("user id is required"))
                        adminService.deleteUser(targetId, user).map { mapOf("message" to it) }
                    }
                }

                route("/reject") {
                    post {
                        call.withAuth { user ->
                            val targetId = call.parameters["id"]
                                ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest("user id is required"))
                            adminService.rejectUser(targetId, user).map { mapOf("message" to it) }
                        }
                    }
                }

                route("/reset-password") {
                    post {
                        call.withAuth { user ->
                            val targetId = call.parameters["id"]
                                ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest("user id is required"))
                            adminService.resetPassword(targetId, user).map { password ->
                                mapOf("password" to password, "message" to "password reset")
                            }
                        }
                    }
                }

                // Dismiss a pending reset request and clear the self-service lockout without
                // issuing a new password. Works on admin targets, which /reset-password refuses.
                route("/clear-reset-request") {
                    post {
                        call.withAuth { user ->
                            val targetId = call.parameters["id"]
                                ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest("user id is required"))
                            adminService.clearResetRequest(targetId, user).map { mapOf("message" to it) }
                        }
                    }
                }
            }
        }
    }
}
