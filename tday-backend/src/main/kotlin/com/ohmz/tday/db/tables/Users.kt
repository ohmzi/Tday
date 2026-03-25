package com.ohmz.tday.db.tables

import com.ohmz.tday.db.enums.*
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.math.BigDecimal

object Users : Table("User") {
    val id = varchar("id", 30)
    val name = varchar("name", 255).nullable()
    val email = varchar("email", 255).uniqueIndex()
    val emailVerified = datetime("emailVerified").nullable()
    val password = text("password").nullable()
    val image = text("image").nullable()
    val createdAt = datetime("createdAt")
    val updatedAt = datetime("updatedAt")
    val maxStorage = decimal("maxStorage", 20, 1).default(BigDecimal("1000000.0"))
    val usedStoraged = decimal("usedStoraged", 20, 1).default(BigDecimal("0.0"))
    val protectedSymmetricKey = text("protectedSymmetricKey").nullable()
    val enableEncryption = bool("enableEncryption").default(true)
    val timeZone = varchar("timeZone", 64).nullable()
    val role = pgEnum<UserRole>("role", "\"UserRole\"")
    val approvalStatus = pgEnum<ApprovalStatus>("approvalStatus", "\"ApprovalStatus\"")
    val tokenVersion = integer("tokenVersion").default(0)
    val approvedAt = datetime("approvedAt").nullable()
    val approvedById = varchar("approvedById", 30).references(id).nullable()

    override val primaryKey = PrimaryKey(id)
}
