package com.ohmz.tday.routes

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import com.ohmz.tday.db.enums.ApprovalStatus
import com.ohmz.tday.db.enums.UserRole
import com.ohmz.tday.db.tables.*
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.AuthenticatedUser
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.models.request.AdminSettingsPatchRequest
import com.ohmz.tday.models.response.AdminUserResponse
import com.ohmz.tday.services.AppConfigService
import com.ohmz.tday.services.CacheService
import com.ohmz.tday.services.TodoSummaryService
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import com.ohmz.tday.di.inject
import java.time.LocalDateTime

fun Route.adminRoutes() {
    val appConfigService by inject<AppConfigService>()
    val todoSummaryService by inject<TodoSummaryService>()
    val cacheService by inject<CacheService>()

    route("/admin") {
        route("/settings") {
            get {
                call.withAuth { user ->
                    either<AppError, Map<String, Any?>> {
                        requireAdminEither(user).bind()
                        val config = appConfigService.getGlobalConfig().bind()
                        val ollamaHealthy = todoSummaryService.isHealthy()
                        mapOf(
                            "aiSummaryEnabled" to config.aiSummaryEnabled,
                            "updatedAt" to config.updatedAt,
                            "ollamaStatus" to if (ollamaHealthy) "healthy" else "unreachable",
                        )
                    }
                }
            }

            patch {
                call.withAuth { user ->
                    either<AppError, Map<String, Any?>> {
                        val admin = requireAdminEither(user).bind()
                        val body = call.receive<AdminSettingsPatchRequest>()
                        val config = appConfigService.setAiSummaryEnabled(body.aiSummaryEnabled, admin.id).bind()
                        cacheService.clear()
                        mapOf("aiSummaryEnabled" to config.aiSummaryEnabled, "updatedAt" to config.updatedAt)
                    }
                }
            }
        }

        route("/users") {
            get {
                call.withAuth { user ->
                    either<AppError, Map<String, Any?>> {
                        requireAdminEither(user).bind()
                        val users = transaction {
                            Users.selectAll()
                                .orderBy(Users.approvalStatus to SortOrder.DESC, Users.createdAt to SortOrder.DESC)
                                .map { row ->
                                    AdminUserResponse(
                                        id = row[Users.id],
                                        name = row[Users.name],
                                        email = row[Users.email],
                                        role = row[Users.role].name,
                                        approvalStatus = row[Users.approvalStatus].name,
                                        createdAt = row[Users.createdAt].toString(),
                                        approvedAt = row[Users.approvedAt]?.toString(),
                                    )
                                }
                        }
                        mapOf("users" to users)
                    }
                }
            }

            route("/{id}") {
                patch {
                    call.withAuth { user ->
                        either<AppError, Map<String, String>> {
                            val admin = requireAdminEither(user).bind()
                            val targetId = call.parameters["id"]
                                ?: raise(AppError.BadRequest("user id is required"))

                            val target = transaction {
                                Users.selectAll().where { Users.id eq targetId }.firstOrNull()
                            } ?: raise(AppError.NotFound("user not found"))

                            if (target[Users.approvalStatus] == ApprovalStatus.APPROVED) {
                                return@either mapOf("message" to "user is already approved")
                            }

                            transaction {
                                Users.update({ Users.id eq targetId }) {
                                    it[Users.approvalStatus] = ApprovalStatus.APPROVED
                                    it[Users.approvedAt] = LocalDateTime.now()
                                    it[Users.approvedById] = admin.id
                                    it[Users.updatedAt] = LocalDateTime.now()
                                }
                            }
                            mapOf("message" to "user approved")
                        }
                    }
                }

                delete {
                    call.withAuth { user ->
                        either<AppError, Map<String, String>> {
                            val admin = requireAdminEither(user).bind()
                            val targetId = call.parameters["id"]
                                ?: raise(AppError.BadRequest("user id is required"))
                            if (targetId == admin.id) raise(AppError.BadRequest("you cannot delete your own account"))

                            val target = transaction {
                                Users.selectAll().where { Users.id eq targetId }.firstOrNull()
                            } ?: raise(AppError.NotFound("user not found"))

                            if (target[Users.role] == UserRole.ADMIN) {
                                val otherAdmins = transaction {
                                    Users.selectAll().where {
                                        (Users.role eq UserRole.ADMIN) and (Users.id neq targetId)
                                    }.count()
                                }
                                if (otherAdmins == 0L) raise(AppError.Forbidden("you cannot delete the last admin account"))
                            }

                            transaction {
                                CompletedTodos.deleteWhere { CompletedTodos.userID eq targetId }
                                Notes.deleteWhere { Notes.userID eq targetId }
                                Files.deleteWhere { Files.userID eq targetId }
                                Todos.deleteWhere { Todos.userID eq targetId }
                                Lists.deleteWhere { Lists.userID eq targetId }
                                UserPreferences.deleteWhere { UserPreferences.userID eq targetId }
                                Users.deleteWhere { Users.id eq targetId }
                            }
                            mapOf("message" to "user deleted")
                        }
                    }
                }
            }
        }
    }
}

private fun requireAdminEither(user: AuthenticatedUser): Either<AppError, AuthenticatedUser> {
    if (user.approvalStatus != "APPROVED") return Either.Left(AppError.Forbidden("your account is awaiting admin approval"))
    if (user.role != "ADMIN") return Either.Left(AppError.Forbidden("admin access required"))
    return user.right()
}
