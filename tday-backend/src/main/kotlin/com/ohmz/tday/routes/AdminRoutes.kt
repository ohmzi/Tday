package com.ohmz.tday.routes

import arrow.core.raise.either
import com.ohmz.tday.di.inject
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.requireAdminAccess
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.models.request.AdminSettingsPatchRequest
import com.ohmz.tday.services.AdminService
import com.ohmz.tday.services.AppConfigService
import com.ohmz.tday.services.CacheService
import com.ohmz.tday.shared.model.AdminSettingsResponse
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.adminRoutes() {
    val appConfigService by inject<AppConfigService>()
    val cacheService by inject<CacheService>()
    val adminService by inject<AdminService>()

    route("/admin") {
        route("/settings") {
            get {
                call.withAuth { user ->
                    either<AppError, AdminSettingsResponse> {
                        user.requireAdminAccess().bind()
                        val config = appConfigService.getGlobalConfig().bind()
                        AdminSettingsResponse(
                            aiSummaryEnabled = config.aiSummaryEnabled,
                            updatedAt = config.updatedAt,
                        )
                    }
                }
            }

            patch {
                call.withAuth { user ->
                    either<AppError, AdminSettingsResponse> {
                        user.requireAdminAccess().bind()
                        val body = call.receive<AdminSettingsPatchRequest>()
                        val config = appConfigService.setAiSummaryEnabled(body.aiSummaryEnabled, user.id).bind()
                        cacheService.clear()
                        AdminSettingsResponse(
                            aiSummaryEnabled = config.aiSummaryEnabled,
                            updatedAt = config.updatedAt,
                        )
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
            }
        }
    }
}
