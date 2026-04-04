package com.ohmz.tday.routes

import arrow.core.raise.either
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.requireAdminAccess
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.models.request.AdminSettingsPatchRequest
import com.ohmz.tday.shared.model.AdminSettingsResponse
import com.ohmz.tday.services.AdminService
import com.ohmz.tday.services.AppConfigService
import com.ohmz.tday.services.CacheService
import io.ktor.server.request.*
import io.ktor.server.routing.*
import com.ohmz.tday.di.inject

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
            }
        }
    }
}
