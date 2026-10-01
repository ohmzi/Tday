package com.ohmz.tday.routes

import arrow.core.right
import com.ohmz.tday.db.tables.Users
import com.ohmz.tday.domain.withAuth
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZoneId

fun Route.timezoneRoutes() {
    route("/timezone") {
        get {
            call.withAuth { user ->
                val clientTz = resolveClientTimeZone(
                    queryTimeZone = call.request.queryParameters["timezone"],
                    xTimeZone = call.request.headers["x-timezone"],
                    xUserTimeZone = call.request.headers["x-user-timezone"],
                )

                val validClientTz = clientTz?.takeIf { it.isNotBlank() && isValidTimeZone(it) }

                // One transaction: read the stored zone, move it to the client's when it differs,
                // and answer with what is now stored (clientTz once the update landed).
                val timeZone = newSuspendedTransaction(Dispatchers.IO) {
                    val currentTz = Users.select(Users.timeZone).where { Users.id eq user.id }.firstOrNull()?.get(Users.timeZone)
                    if (validClientTz != null && currentTz != validClientTz) {
                        val updated = Users.update({ Users.id eq user.id }) {
                            it[Users.timeZone] = validClientTz
                            it[Users.updatedAt] = LocalDateTime.now(ZoneOffset.UTC)
                        }
                        if (updated > 0) validClientTz else currentTz
                    } else {
                        currentTz
                    }
                }
                mapOf("timeZone" to timeZone).right()
            }
        }
    }
}

internal fun resolveClientTimeZone(
    queryTimeZone: String?,
    xTimeZone: String?,
    xUserTimeZone: String?,
): String? = queryTimeZone ?: xTimeZone ?: xUserTimeZone

fun isValidTimeZone(tz: String): Boolean {
    return try {
        ZoneId.of(tz)
        true
    } catch (_: Exception) {
        false
    }
}
