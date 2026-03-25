package com.ohmz.tday.services

import com.ohmz.tday.db.tables.AppConfigs
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

object AppConfigService {
    private const val GLOBAL_ID = 1

    fun getGlobalConfig(): AppConfigRow {
        return transaction {
            val existing = AppConfigs.selectAll().where { AppConfigs.id eq GLOBAL_ID }.firstOrNull()
            if (existing != null) {
                AppConfigRow(
                    aiSummaryEnabled = existing[AppConfigs.aiSummaryEnabled],
                    updatedAt = existing[AppConfigs.updatedAt],
                )
            } else {
                val now = LocalDateTime.now()
                AppConfigs.insert {
                    it[AppConfigs.id] = GLOBAL_ID
                    it[AppConfigs.aiSummaryEnabled] = true
                    it[AppConfigs.createdAt] = now
                    it[AppConfigs.updatedAt] = now
                }
                AppConfigRow(aiSummaryEnabled = true, updatedAt = now)
            }
        }
    }

    fun setAiSummaryEnabled(enabled: Boolean, updatedById: String?): AppConfigRow {
        return transaction {
            val now = LocalDateTime.now()
            val existing = AppConfigs.selectAll().where { AppConfigs.id eq GLOBAL_ID }.firstOrNull()
            if (existing != null) {
                AppConfigs.update({ AppConfigs.id eq GLOBAL_ID }) {
                    it[AppConfigs.aiSummaryEnabled] = enabled
                    it[AppConfigs.updatedById] = updatedById
                    it[AppConfigs.updatedAt] = now
                }
            } else {
                AppConfigs.insert {
                    it[AppConfigs.id] = GLOBAL_ID
                    it[AppConfigs.aiSummaryEnabled] = enabled
                    it[AppConfigs.updatedById] = updatedById
                    it[AppConfigs.createdAt] = now
                    it[AppConfigs.updatedAt] = now
                }
            }
            AppConfigRow(aiSummaryEnabled = enabled, updatedAt = now)
        }
    }

    data class AppConfigRow(val aiSummaryEnabled: Boolean, val updatedAt: LocalDateTime)
}
