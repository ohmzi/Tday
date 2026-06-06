package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.raise.either
import com.ohmz.tday.db.enums.ApprovalStatus
import com.ohmz.tday.db.enums.UserRole
import com.ohmz.tday.db.tables.*
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.AuthenticatedUser
import com.ohmz.tday.domain.requireAdminAccess
import com.ohmz.tday.models.response.AdminUserResponse
import com.ohmz.tday.security.PasswordService
import com.ohmz.tday.security.SessionControl
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.security.SecureRandom
import java.time.LocalDateTime
import kotlin.random.asKotlinRandom

interface AdminService {
    suspend fun listUsers(admin: AuthenticatedUser): Either<AppError, List<AdminUserResponse>>
    suspend fun approveUser(targetId: String, admin: AuthenticatedUser): Either<AppError, String>
    suspend fun deleteUser(targetId: String, admin: AuthenticatedUser): Either<AppError, String>
    suspend fun rejectUser(targetId: String, admin: AuthenticatedUser): Either<AppError, String>
    suspend fun resetPassword(targetId: String, admin: AuthenticatedUser): Either<AppError, String>
}

class AdminServiceImpl(
    private val passwordService: PasswordService,
    private val sessionControl: SessionControl,
) : AdminService {

    override suspend fun listUsers(admin: AuthenticatedUser): Either<AppError, List<AdminUserResponse>> = either {
        admin.requireAdminAccess().bind()
        newSuspendedTransaction(Dispatchers.IO) {
            Users.selectAll()
                .orderBy(Users.approvalStatus to SortOrder.DESC, Users.createdAt to SortOrder.DESC)
                .map { row ->
                    AdminUserResponse(
                        id = row[Users.id],
                        name = row[Users.name],
                        username = row[Users.username],
                        role = row[Users.role].name,
                        approvalStatus = row[Users.approvalStatus].name,
                        createdAt = row[Users.createdAt].toString(),
                        approvedAt = row[Users.approvedAt]?.toString(),
                    )
                }
        }
    }

    override suspend fun approveUser(targetId: String, admin: AuthenticatedUser): Either<AppError, String> = either {
        admin.requireAdminAccess().bind()

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
        admin.requireAdminAccess().bind()

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

        purgeUser(targetId)
        "user deleted"
    }

    override suspend fun rejectUser(
        targetId: String,
        admin: AuthenticatedUser
    ): Either<AppError, String> = either {
        admin.requireAdminAccess().bind()

        val target = newSuspendedTransaction(Dispatchers.IO) {
            Users.selectAll().where { Users.id eq targetId }.firstOrNull()
        } ?: raise(AppError.NotFound("user not found"))

        if (target[Users.approvalStatus] != ApprovalStatus.PENDING) {
            raise(AppError.BadRequest("only pending registrations can be rejected"))
        }

        purgeUser(targetId)
        "registration rejected"
    }

    /** Deletes a user and every record they own. */
    private suspend fun purgeUser(targetId: String) {
        newSuspendedTransaction(Dispatchers.IO) {
            CompletedTodos.deleteWhere { CompletedTodos.userID eq targetId }
            CompletedFloaters.deleteWhere { CompletedFloaters.userID eq targetId }
            Files.deleteWhere { Files.userID eq targetId }
            Todos.deleteWhere { Todos.userID eq targetId }
            Floaters.deleteWhere { Floaters.userID eq targetId }
            Lists.deleteWhere { Lists.userID eq targetId }
            UserPreferences.deleteWhere { UserPreferences.userID eq targetId }
            Users.deleteWhere { Users.id eq targetId }
        }
    }

    override suspend fun resetPassword(
        targetId: String,
        admin: AuthenticatedUser
    ): Either<AppError, String> = either {
        admin.requireAdminAccess().bind()

        val target = newSuspendedTransaction(Dispatchers.IO) {
            Users.selectAll().where { Users.id eq targetId }.firstOrNull()
        } ?: raise(AppError.NotFound("user not found"))

        // Admin accounts manage their own password under Settings; they can't be
        // reset from the admin panel.
        if (target[Users.role] == UserRole.ADMIN) {
            raise(AppError.Forbidden("admin account passwords can't be reset here"))
        }

        val generatedPassword = generatePassword()
        val newHash = passwordService.hashPassword(generatedPassword)

        newSuspendedTransaction(Dispatchers.IO) {
            Users.update({ Users.id eq target[Users.id] }) {
                it[Users.password] = newHash
                it[Users.requirePasswordChange] = true
                it[Users.updatedAt] = LocalDateTime.now()
            }
        }

        // Force every existing session for this user to re-authenticate with the
        // new temporary password.
        sessionControl.revokeUserSessions(targetId)

        generatedPassword
    }

    /**
     * Builds a random temporary password that always satisfies the registration
     * policy (>= 8 chars, at least one uppercase letter and one special char).
     */
    private fun generatePassword(): String {
        val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val lower = "abcdefghijkmnpqrstuvwxyz"
        val digits = "23456789"
        val special = "!@#\$%^&*-_=+?"
        val all = upper + lower + digits + special

        val required = listOf(
            upper.random(secureRandom),
            lower.random(secureRandom),
            digits.random(secureRandom),
            special.random(secureRandom),
        )
        val filler = (1..12).map { all[secureRandom.nextInt(all.length)] }
        return (required + filler).shuffled(secureRandom.asKotlinRandom()).joinToString("")
    }

    private fun String.random(random: SecureRandom): Char = this[random.nextInt(length)]

    companion object {
        private val secureRandom = SecureRandom()
    }
}
