package com.ohmz.tday.routes

import com.ohmz.tday.db.enums.ApprovalStatus
import com.ohmz.tday.db.enums.UserRole
import com.ohmz.tday.db.tables.*
import com.ohmz.tday.models.request.AdminSettingsPatchRequest
import com.ohmz.tday.plugins.*
import com.ohmz.tday.security.JwtUserClaims
import com.ohmz.tday.services.AppConfigService
import com.ohmz.tday.services.MemoryCache
import com.ohmz.tday.services.TodoSummaryService
import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

fun Route.adminRoutes() {
    route("/admin") {
        route("/settings") {
            get {
                requireAdmin(call)
                val config = AppConfigService.getGlobalConfig()
                val ollamaHealthy = TodoSummaryService.isHealthy()
                call.respond(HttpStatusCode.OK, mapOf(
                    "aiSummaryEnabled" to config.aiSummaryEnabled,
                    "updatedAt" to config.updatedAt.toString(),
                    "ollamaStatus" to if (ollamaHealthy) "healthy" else "unreachable",
                ))
            }

            patch {
                val admin = requireAdmin(call)
                val body = call.receive<AdminSettingsPatchRequest>()
                val config = AppConfigService.setAiSummaryEnabled(body.aiSummaryEnabled, admin.id)
                MemoryCache.clear()
                call.respond(HttpStatusCode.OK, mapOf(
                    "aiSummaryEnabled" to config.aiSummaryEnabled,
                    "updatedAt" to config.updatedAt.toString(),
                ))
            }
        }

        route("/users") {
            get {
                requireAdmin(call)
                val users = transaction {
                    Users.selectAll()
                        .orderBy(Users.approvalStatus to SortOrder.DESC, Users.createdAt to SortOrder.DESC)
                        .map {
                            mapOf(
                                "id" to it[Users.id],
                                "name" to it[Users.name],
                                "email" to it[Users.email],
                                "role" to it[Users.role].name,
                                "approvalStatus" to it[Users.approvalStatus].name,
                                "createdAt" to it[Users.createdAt].toString(),
                                "approvedAt" to it[Users.approvedAt]?.toString(),
                            )
                        }
                }
                call.respond(HttpStatusCode.OK, mapOf("users" to users))
            }

            route("/{id}") {
                patch {
                    val admin = requireAdmin(call)
                    val targetId = call.parameters["id"] ?: throw BadRequestException("user id is required")

                    val target = transaction {
                        Users.selectAll().where { Users.id eq targetId }.firstOrNull()
                    } ?: throw NotFoundException("user not found")

                    if (target[Users.approvalStatus] == ApprovalStatus.APPROVED) {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "user is already approved"))
                        return@patch
                    }

                    transaction {
                        Users.update({ Users.id eq targetId }) {
                            it[Users.approvalStatus] = ApprovalStatus.APPROVED
                            it[Users.approvedAt] = LocalDateTime.now()
                            it[Users.approvedById] = admin.id
                            it[Users.updatedAt] = LocalDateTime.now()
                        }
                    }
                    call.respond(HttpStatusCode.OK, mapOf("message" to "user approved"))
                }

                delete {
                    val admin = requireAdmin(call)
                    val targetId = call.parameters["id"] ?: throw BadRequestException("user id is required")
                    if (targetId == admin.id) throw BadRequestException("you cannot delete your own account")

                    val target = transaction {
                        Users.selectAll().where { Users.id eq targetId }.firstOrNull()
                    } ?: throw NotFoundException("user not found")

                    if (target[Users.role] == UserRole.ADMIN) {
                        val otherAdmins = transaction {
                            Users.selectAll().where {
                                (Users.role eq UserRole.ADMIN) and (Users.id neq targetId)
                            }.count()
                        }
                        if (otherAdmins == 0L) throw ForbiddenException("you cannot delete the last admin account")
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
                    call.respond(HttpStatusCode.OK, mapOf("message" to "user deleted"))
                }
            }
        }
    }
}

private suspend fun requireAdmin(call: ApplicationCall): JwtUserClaims {
    val user = call.authUser() ?: throw UnauthorizedException("you must be logged in to do this")
    if (user.approvalStatus != "APPROVED") throw ForbiddenException("your account is awaiting admin approval")
    if (user.role != "ADMIN") throw ForbiddenException("admin access required")
    return user
}
