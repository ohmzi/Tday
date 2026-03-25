package com.ohmz.tday.services

import com.ohmz.tday.db.enums.Direction
import com.ohmz.tday.db.enums.GroupBy
import com.ohmz.tday.db.enums.SortBy
import com.ohmz.tday.db.tables.UserPreferences
import com.ohmz.tday.db.util.CuidGenerator
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

object PreferencesService {
    fun get(userId: String): Map<String, Any?> = transaction {
        val prefs = UserPreferences.selectAll().where { UserPreferences.userID eq userId }.firstOrNull()
        mapOf(
            "sortBy" to prefs?.get(UserPreferences.sortBy)?.name,
            "groupBy" to prefs?.get(UserPreferences.groupBy)?.let { GroupBy.toPrisma(it) },
            "direction" to prefs?.get(UserPreferences.direction)?.name,
        )
    }

    fun update(userId: String, sortBy: String?, groupBy: String?, direction: String?) {
        transaction {
            val existing = UserPreferences.selectAll().where { UserPreferences.userID eq userId }.firstOrNull()
            if (existing != null) {
                UserPreferences.update({ UserPreferences.userID eq userId }) {
                    sortBy?.let { s -> it[UserPreferences.sortBy] = SortBy.valueOf(s) }
                    groupBy?.let { g -> it[UserPreferences.groupBy] = GroupBy.fromPrisma(g) }
                    direction?.let { d -> it[UserPreferences.direction] = Direction.valueOf(d) }
                }
            } else {
                UserPreferences.insert {
                    it[UserPreferences.id] = CuidGenerator.newCuid()
                    it[UserPreferences.userID] = userId
                    sortBy?.let { s -> it[UserPreferences.sortBy] = SortBy.valueOf(s) }
                    groupBy?.let { g -> it[UserPreferences.groupBy] = GroupBy.fromPrisma(g) }
                    direction?.let { d -> it[UserPreferences.direction] = Direction.valueOf(d) }
                }
            }
        }
    }
}
