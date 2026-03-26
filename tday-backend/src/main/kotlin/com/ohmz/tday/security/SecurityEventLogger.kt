package com.ohmz.tday.security

import com.ohmz.tday.db.tables.EventLogs
import com.ohmz.tday.db.util.CuidGenerator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

interface SecurityEventLogger {
    fun log(reasonCode: String, details: Map<String, Any?> = emptyMap())
}

class SecurityEventLoggerImpl : SecurityEventLogger {
    private val logger = LoggerFactory.getLogger("security")
    private val json = Json { encodeDefaults = true }

    override fun log(reasonCode: String, details: Map<String, Any?>) {
        val payload = buildJsonObject {
            put("reasonCode", JsonPrimitive(reasonCode))
            put("at", JsonPrimitive(java.time.Instant.now().toString()))
            for ((key, value) in details) {
                put(key, JsonPrimitive(value?.toString()))
            }
        }

        logger.warn("[security] {}", payload)

        try {
            val serialized = json.encodeToString(JsonElement.serializer(), payload).take(500)
            transaction {
                EventLogs.insert {
                    it[id] = CuidGenerator.newCuid()
                    it[capturedTime] = LocalDateTime.now()
                    it[eventName] = reasonCode
                    it[log] = serialized
                }
            }
        } catch (e: Exception) {
            logger.warn("[security:eventlog_failed] {}", e.message)
        }
    }
}
