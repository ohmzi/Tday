package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.db.tables.AppConfigs
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.models.response.AppConfigResponse
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

interface AppConfigService {
    suspend fun getGlobalConfig(): Either<AppError, AppConfigResponse>
    suspend fun setAiSummaryEnabled(enabled: Boolean, updatedById: String?): Either<AppError, AppConfigResponse>
}

class AppConfigServiceImpl : AppConfigService {
    private val globalId = 1

    override suspend fun getGlobalConfig(): Either<AppError, AppConfigResponse> {
        val config = newSuspendedTransaction(Dispatchers.IO) {
            val existing = AppConfigs.selectAll().where { AppConfigs.id eq globalId }.firstOrNull()
            if (existing != null) {
                AppConfigResponse(
                    aiSummaryEnabled = existing[AppConfigs.aiSummaryEnabled],
                    updatedAt = existing[AppConfigs.updatedAt].toString(),
                )
            } else {
                val now = LocalDateTime.now()
                AppConfigs.insert {
                    it[AppConfigs.id] = globalId
                    it[AppConfigs.aiSummaryEnabled] = true
                    it[AppConfigs.createdAt] = now
                    it[AppConfigs.updatedAt] = now
                }
                AppConfigResponse(aiSummaryEnabled = true, updatedAt = now.toString())
            }
        }
        return config.right()
    }

    override suspend fun setAiSummaryEnabled(enabled: Boolean, updatedById: String?): Either<AppError, AppConfigResponse> {
        val config = newSuspendedTransaction(Dispatchers.IO) {
            val now = LocalDateTime.now()
            val existing = AppConfigs.selectAll().where { AppConfigs.id eq globalId }.firstOrNull()
            if (existing != null) {
                AppConfigs.update({ AppConfigs.id eq globalId }) {
                    it[AppConfigs.aiSummaryEnabled] = enabled
                    it[AppConfigs.updatedById] = updatedById
                    it[AppConfigs.updatedAt] = now
                }
            } else {
                AppConfigs.insert {
                    it[AppConfigs.id] = globalId
                    it[AppConfigs.aiSummaryEnabled] = enabled
                    it[AppConfigs.updatedById] = updatedById
                    it[AppConfigs.createdAt] = now
                    it[AppConfigs.updatedAt] = now
                }
            }
            AppConfigResponse(aiSummaryEnabled = enabled, updatedAt = now.toString())
        }
        return config.right()
    }
}
