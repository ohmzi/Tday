package com.ohmz.tday.config

import com.ohmz.tday.db.tables.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.net.URI

data class ParsedDatabaseUrl(val jdbcUrl: String, val username: String?, val password: String?)

class DatabaseConfig(private val config: AppConfig) {
    private val logger = LoggerFactory.getLogger(DatabaseConfig::class.java)

    private fun parseDatabaseUrl(raw: String): ParsedDatabaseUrl {
        if (raw.startsWith("jdbc:")) {
            return ParsedDatabaseUrl(raw, null, null)
        }

        val normalized = if (raw.startsWith("postgres://")) {
            "postgresql://${raw.removePrefix("postgres://")}"
        } else raw

        val uri = URI(normalized)
        val userInfo = uri.userInfo
        val user = userInfo?.substringBefore(':')
        val pass = userInfo?.substringAfter(':', "")?.ifEmpty { null }
        val hostPort = buildString {
            append(uri.host)
            if (uri.port > 0) append(":${uri.port}")
        }
        val jdbcUrl = "jdbc:postgresql://$hostPort${uri.path}${uri.query?.let { "?$it" } ?: ""}"
        return ParsedDatabaseUrl(jdbcUrl, user, pass)
    }

    fun init() {
        val parsed = parseDatabaseUrl(config.databaseUrl)
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = parsed.jdbcUrl
            parsed.username?.let { username = it }
            parsed.password?.let { password = it }
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            minimumIdle = 2
            idleTimeout = 600_000
            connectionTimeout = 30_000
            maxLifetime = 1_800_000
            isAutoCommit = false
        }
        val dataSource = HikariDataSource(hikariConfig)

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion(MigrationVersion.fromVersion("2"))
            .load()
            .migrate()

        Database.connect(dataSource)

        transaction {
            val pgEnums = listOf(
                "\"UserRole\"" to listOf("ADMIN", "USER"),
                "\"ApprovalStatus\"" to listOf("APPROVED", "PENDING"),
                "\"SortBy\"" to listOf("due", "priority"),
                "\"GroupBy\"" to listOf("due", "priority", "rrule", "project"),
                "\"Direction\"" to listOf("Ascending", "Descending"),
                "\"Priority\"" to listOf("Low", "Medium", "High"),
                "\"ProjectColor\"" to listOf(
                    "RED", "ORANGE", "YELLOW", "LIME", "BLUE", "PURPLE", "PINK", "TEAL",
                    "CORAL", "GOLD", "DEEP_BLUE", "ROSE", "LIGHT_RED", "BRICK", "SLATE",
                ),
            )
            for ((name, values) in pgEnums) {
                val valList = values.joinToString(", ") { "'$it'" }
                exec("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = ${name.replace("\"", "'")}) THEN CREATE TYPE $name AS ENUM ($valList); END IF; END $$;")
            }

            SchemaUtils.createMissingTablesAndColumns(
                Users, Accounts, VerificationTokens, Lists, Todos, TodoInstances,
                CompletedTodos, Files, UserPreferences, AppConfigs,
                EventLogs, CronLogs, AuthThrottles, AuthSignals,
            )
        }

        logger.info("Database connected via HikariCP with Flyway migrations applied")
    }
}
