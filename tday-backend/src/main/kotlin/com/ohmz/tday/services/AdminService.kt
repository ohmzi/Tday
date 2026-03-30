package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import com.ohmz.tday.db.enums.ApprovalStatus
import com.ohmz.tday.db.enums.UserRole
import com.ohmz.tday.db.tables.*
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.AuthenticatedUser
import com.ohmz.tday.models.response.AdminUserResponse
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime

interface AdminService {
    suspend fun listUsers(admin: AuthenticatedUser): Either<AppError, List<AdminUserResponse>>
    suspend fun approveUser(targetId: String, admin: AuthenticatedUser): Either<AppError, String>
    suspend fun deleteUser(targetId: String, admin: AuthenticatedUser): Either<AppError, String>
}

class AdminServiceImpl : AdminService {

    override suspend fun listUsers(admin: AuthenticatedUser): Either<AppError, List<AdminUserResponse>> = either {
        requireAdmin(admin).bind()
        newSuspendedTransaction(Dispatchers.IO) {
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
    }

    override suspend fun approveUser(targetId: String, admin: AuthenticatedUser): Either<AppError, String> = either {
        requireAdmin(admin).bind()

        val target = newSuspendedTransaction(Dispatchers.IO) {
            Users.selectAll().where { Users.id eq targetId }.firstOrNull()
        } ?: raise(AppError.NotFound("user not found"))

        if (target[Users.approvalStatus] == ApprovalStatus.APPROVED) {
            return@either "user is already approved"
        }

        newSuspendedTransaction(Dispatchers.IO) {
            Users.update({ Users.id eq targetId }) {
                it[Users.approvalStatus] = ApprovalStatus.APPROVED
                it[Users.approvedAt] = LocalDateTime.now()
                it[Users.approvedById] = admin.id
                it[Users.updatedAt] = LocalDateTime.now()
            }
        }
        "user approved"
    }

    override suspend fun deleteUser(targetId: String, admin: AuthenticatedUser): Either<AppError, String> = either {
        requireAdmin(admin).bind()

        if (targetId == admin.id) raise(AppError.BadRequest("you cannot delete your own account"))

        newSuspendedTransaction(Dispatchers.IO) {
            Users.selectAll().where { Users.id eq targetId }.firstOrNull()
        } ?: raise(AppError.NotFound("user not found"))

        val target = newSuspendedTransaction(Dispatchers.IO) {
            Users.selectAll().where { Users.id eq targetId }.firstOrNull()
        } ?: raise(AppError.NotFound("user not found"))

        if (target[Users.role] == UserRole.ADMIN) {
            val otherAdmins = newSuspendedTransaction(Dispatchers.IO) {
                Users.selectAll().where {
                    (Users.role eq UserRole.ADMIN) and (Users.id neq targetId)
                }.count()
            }
            if (otherAdmins == 0L) raise(AppError.Forbidden("you cannot delete the last admin account"))
        }

        newSuspendedTransaction(Dispatchers.IO) {
            CompletedTodos.deleteWhere { CompletedTodos.userID eq targetId }
            Files.deleteWhere { Files.userID eq targetId }
            Todos.deleteWhere { Todos.userID eq targetId }
            Lists.deleteWhere { Lists.userID eq targetId }
            UserPreferences.deleteWhere { UserPreferences.userID eq targetId }
            Users.deleteWhere { Users.id eq targetId }
        }
        "user deleted"
    }

    private fun requireAdmin(user: AuthenticatedUser): Either<AppError, AuthenticatedUser> {
        if (user.approvalStatus != "APPROVED") return Either.Left(AppError.Forbidden("your account is awaiting admin approval"))
        if (user.role != "ADMIN") return Either.Left(AppError.Forbidden("admin access required"))
        return user.right()
    }
}
