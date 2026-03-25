package com.ohmz.tday.routes

import com.ohmz.tday.db.tables.Users
import com.ohmz.tday.plugins.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.time.ZoneId

fun Route.timezoneRoutes() {
    route("/timezone") {
        get {
            val user = call.requireUser()
            val clientTz = call.request.queryParameters["timezone"]
                ?: call.request.headers["x-timezone"]

            if (!clientTz.isNullOrBlank() && isValidTimeZone(clientTz)) {
                val dbUser = transaction {
                    Users.selectAll().where { Users.id eq user.id }.firstOrNull()
                }
                val currentTz = dbUser?.get(Users.timeZone)
                if (currentTz != clientTz) {
                    transaction {
                        Users.update({ Users.id eq user.id }) {
                            it[Users.timeZone] = clientTz
                            it[Users.updatedAt] = LocalDateTime.now()
                        }
                    }
                }
            }

            val dbUser = transaction {
                Users.selectAll().where { Users.id eq user.id }.firstOrNull()
            }
            call.respond(HttpStatusCode.OK, mapOf("timeZone" to dbUser?.get(Users.timeZone)))
        }
    }
}

fun isValidTimeZone(tz: String): Boolean {
    return try {
        ZoneId.of(tz)
        true
    } catch (_: Exception) {
        false
    }
}
