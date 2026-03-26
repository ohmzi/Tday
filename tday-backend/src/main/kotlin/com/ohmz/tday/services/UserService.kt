package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ohmz.tday.db.enums.ApprovalStatus
import com.ohmz.tday.db.enums.UserRole
import com.ohmz.tday.db.tables.Users
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.models.response.UserProfileResponse
import com.ohmz.tday.models.response.UserResponse
import com.ohmz.tday.security.PasswordService
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

data class RegisterResult(val userId: String, val requiresApproval: Boolean, val isBootstrapAdmin: Boolean)

interface UserService {
    suspend fun getUser(userId: String): Either<AppError, UserResponse>
    suspend fun updateEncryption(userId: String, enable: Boolean): Either<AppError, Unit>
    suspend fun updateSymmetricKey(userId: String, key: String): Either<AppError, Unit>
    suspend fun getProfile(userId: String): Either<AppError, UserProfileResponse>
    suspend fun updateProfile(userId: String, name: String?, image: String?): Either<AppError, Unit>
    suspend fun changePassword(userId: String, currentPassword: String, newPassword: String): Either<AppError, Boolean>
    suspend fun register(fname: String, lname: String?, email: String, password: String): Either<AppError, RegisterResult>
    suspend fun findByEmail(email: String): Map<String, Any?>?
    suspend fun isAdmin(userId: String): Boolean
    suspend fun emailExists(email: String): Boolean
    suspend fun updatePasswordHash(userId: String, newHash: String)
}

class UserServiceImpl(private val passwordService: PasswordService) : UserService {
    override suspend fun getUser(userId: String): Either<AppError, UserResponse> {
        val user = transaction {
            Users.selectAll().where { Users.id eq userId }.firstOrNull()?.let {
                UserResponse(
                    maxStorage = it[Users.maxStorage].toDouble(),
                    usedStoraged = it[Users.usedStoraged].toDouble(),
                    enableEncryption = it[Users.enableEncryption],
                    protectedSymmetricKey = it[Users.protectedSymmetricKey],
                )
            }
        }
        return user?.right() ?: AppError.NotFound("user not found").left()
    }

    override suspend fun updateEncryption(userId: String, enable: Boolean): Either<AppError, Unit> {
        transaction {
            Users.update({ Users.id eq userId }) {
                it[Users.enableEncryption] = enable
                it[Users.updatedAt] = LocalDateTime.now()
            }
        }
        return Unit.right()
    }

    override suspend fun updateSymmetricKey(userId: String, key: String): Either<AppError, Unit> {
        transaction {
            Users.update({ Users.id eq userId }) {
                it[Users.protectedSymmetricKey] = key
                it[Users.updatedAt] = LocalDateTime.now()
            }
        }
        return Unit.right()
    }

    override suspend fun getProfile(userId: String): Either<AppError, UserProfileResponse> {
        val profile = transaction {
            Users.selectAll().where { Users.id eq userId }.firstOrNull()?.let {
                UserProfileResponse(
                    id = it[Users.id],
                    name = it[Users.name],
                    email = it[Users.email],
                    image = it[Users.image],
                    role = it[Users.role].name,
                    approvalStatus = it[Users.approvalStatus].name,
                    createdAt = it[Users.createdAt].toString(),
                )
            }
        }
        return profile?.right() ?: AppError.NotFound("user not found").left()
    }

    override suspend fun updateProfile(userId: String, name: String?, image: String?): Either<AppError, Unit> {
        transaction {
            Users.update({ Users.id eq userId }) {
                name?.let { n -> it[Users.name] = n }
                image?.let { i -> it[Users.image] = i }
                it[Users.updatedAt] = LocalDateTime.now()
            }
        }
        return Unit.right()
    }

    override suspend fun changePassword(userId: String, currentPassword: String, newPassword: String): Either<AppError, Boolean> {
        val result = transaction {
            val user = Users.selectAll().where { Users.id eq userId }.firstOrNull() ?: return@transaction false
            val storedHash = user[Users.password] ?: return@transaction false
            val verification = passwordService.verifyPassword(currentPassword, storedHash)
            if (!verification.valid) return@transaction false

            val newHash = passwordService.hashPassword(newPassword)
            Users.update({ Users.id eq userId }) {
                it[Users.password] = newHash
                it[Users.updatedAt] = LocalDateTime.now()
            }
            true
        }
        return result.right()
    }

    override suspend fun register(fname: String, lname: String?, email: String, password: String): Either<AppError, RegisterResult> {
        val hashedPassword = passwordService.hashPassword(password)
        val fullName = listOf(fname.trim(), lname?.trim() ?: "").filter { it.isNotEmpty() }.joinToString(" ")

        val result = transaction {
            val userCount = Users.selectAll().count()
            val isFirst = userCount == 0L

            val id = CuidGenerator.newCuid()
            val now = LocalDateTime.now()

            Users.insert {
                it[Users.id] = id
                it[Users.name] = fullName
                it[Users.email] = email
                it[Users.password] = hashedPassword
                it[Users.role] = if (isFirst) UserRole.ADMIN else UserRole.USER
                it[Users.approvalStatus] = if (isFirst) ApprovalStatus.APPROVED else ApprovalStatus.PENDING
                it[Users.approvedAt] = if (isFirst) now else null
                it[Users.createdAt] = now
                it[Users.updatedAt] = now
            }

            RegisterResult(
                userId = id,
                requiresApproval = !isFirst,
                isBootstrapAdmin = isFirst,
            )
        }
        return result.right()
    }

    override suspend fun findByEmail(email: String): Map<String, Any?>? = transaction {
        Users.selectAll().where { Users.email eq email }.firstOrNull()?.let {
            mapOf(
                "id" to it[Users.id],
                "email" to it[Users.email],
                "password" to it[Users.password],
                "name" to it[Users.name],
                "role" to it[Users.role].name,
                "approvalStatus" to it[Users.approvalStatus].name,
                "tokenVersion" to it[Users.tokenVersion],
                "timeZone" to it[Users.timeZone],
            )
        }
    }

    override suspend fun isAdmin(userId: String): Boolean = transaction {
        val user = Users.selectAll().where { Users.id eq userId }.firstOrNull() ?: return@transaction false
        user[Users.role] == UserRole.ADMIN && user[Users.approvalStatus] == ApprovalStatus.APPROVED
    }

    override suspend fun emailExists(email: String): Boolean = transaction {
        Users.selectAll().where { Users.email eq email }.count() > 0
    }

    override suspend fun updatePasswordHash(userId: String, newHash: String) {
        transaction {
            Users.update({ Users.id eq userId }) {
                it[Users.password] = newHash
                it[Users.updatedAt] = LocalDateTime.now()
            }
        }
    }
}
