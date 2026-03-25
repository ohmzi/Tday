package com.ohmz.tday.services

import com.ohmz.tday.db.enums.ApprovalStatus
import com.ohmz.tday.db.enums.UserRole
import com.ohmz.tday.db.tables.Users
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.security.PasswordService
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

object UserService {
    fun getUser(userId: String): Map<String, Any?>? = transaction {
        Users.selectAll().where { Users.id eq userId }.firstOrNull()?.let {
            mapOf(
                "maxStorage" to it[Users.maxStorage].toDouble(),
                "usedStoraged" to it[Users.usedStoraged].toDouble(),
                "enableEncryption" to it[Users.enableEncryption],
                "protectedSymmetricKey" to it[Users.protectedSymmetricKey],
            )
        }
    }

    fun updateEncryption(userId: String, enable: Boolean) {
        transaction {
            Users.update({ Users.id eq userId }) {
                it[Users.enableEncryption] = enable
                it[Users.updatedAt] = LocalDateTime.now()
            }
        }
    }

    fun updateSymmetricKey(userId: String, key: String) {
        transaction {
            Users.update({ Users.id eq userId }) {
                it[Users.protectedSymmetricKey] = key
                it[Users.updatedAt] = LocalDateTime.now()
            }
        }
    }

    fun getProfile(userId: String): Map<String, Any?>? = transaction {
        Users.selectAll().where { Users.id eq userId }.firstOrNull()?.let {
            mapOf(
                "id" to it[Users.id],
                "name" to it[Users.name],
                "email" to it[Users.email],
                "image" to it[Users.image],
                "role" to it[Users.role].name,
                "approvalStatus" to it[Users.approvalStatus].name,
                "createdAt" to it[Users.createdAt].toString(),
            )
        }
    }

    fun updateProfile(userId: String, name: String?, image: String?) {
        transaction {
            Users.update({ Users.id eq userId }) {
                name?.let { n -> it[Users.name] = n }
                image?.let { i -> it[Users.image] = i }
                it[Users.updatedAt] = LocalDateTime.now()
            }
        }
    }

    fun changePassword(userId: String, currentPassword: String, newPassword: String): Boolean {
        return transaction {
            val user = Users.selectAll().where { Users.id eq userId }.firstOrNull() ?: return@transaction false
            val storedHash = user[Users.password] ?: return@transaction false
            val verification = PasswordService.verifyPassword(currentPassword, storedHash)
            if (!verification.valid) return@transaction false

            val newHash = PasswordService.hashPassword(newPassword)
            Users.update({ Users.id eq userId }) {
                it[Users.password] = newHash
                it[Users.updatedAt] = LocalDateTime.now()
            }
            true
        }
    }

    fun register(
        fname: String,
        lname: String?,
        email: String,
        password: String,
    ): RegisterResult {
        val hashedPassword = PasswordService.hashPassword(password)
        val fullName = listOf(fname.trim(), lname?.trim() ?: "").filter { it.isNotEmpty() }.joinToString(" ")

        return transaction {
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
    }

    data class RegisterResult(val userId: String, val requiresApproval: Boolean, val isBootstrapAdmin: Boolean)

    fun findByEmail(email: String): Map<String, Any?>? = transaction {
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

    fun isAdmin(userId: String): Boolean = transaction {
        val user = Users.selectAll().where { Users.id eq userId }.firstOrNull() ?: return@transaction false
        user[Users.role] == UserRole.ADMIN && user[Users.approvalStatus] == ApprovalStatus.APPROVED
    }

    fun emailExists(email: String): Boolean = transaction {
        Users.selectAll().where { Users.email eq email }.count() > 0
    }
}
