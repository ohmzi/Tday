package com.ohmz.tday.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

object DatabaseConfig {
    private val logger = LoggerFactory.getLogger(DatabaseConfig::class.java)

    fun init() {
        val config = HikariConfig().apply {
            jdbcUrl = AppConfig.databaseUrl
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            minimumIdle = 2
            idleTimeout = 600_000
            connectionTimeout = 30_000
            maxLifetime = 1_800_000
            isAutoCommit = false
        }
        val dataSource = HikariDataSource(config)
        Database.connect(dataSource)
        logger.info("Database connected via HikariCP")
    }
}
