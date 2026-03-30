package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.db.enums.Direction
import com.ohmz.tday.db.enums.GroupBy
import com.ohmz.tday.db.enums.SortBy
import com.ohmz.tday.db.tables.UserPreferences
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.models.response.PreferencesResponse
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update

interface PreferencesService {
    suspend fun get(userId: String): Either<AppError, PreferencesResponse>
    suspend fun update(userId: String, sortBy: String?, groupBy: String?, direction: String?): Either<AppError, Unit>
}

class PreferencesServiceImpl : PreferencesService {
    override suspend fun get(userId: String): Either<AppError, PreferencesResponse> {
        val prefs = newSuspendedTransaction(Dispatchers.IO) {
            val row = UserPreferences.selectAll().where { UserPreferences.userID eq userId }.firstOrNull()
            PreferencesResponse(
                sortBy = row?.get(UserPreferences.sortBy)?.name,
                groupBy = row?.get(UserPreferences.groupBy)?.let { GroupBy.toApi(it) },
                direction = row?.get(UserPreferences.direction)?.name,
            )
        }
        return prefs.right()
    }

    override suspend fun update(userId: String, sortBy: String?, groupBy: String?, direction: String?): Either<AppError, Unit> {
        newSuspendedTransaction(Dispatchers.IO) {
            val existing = UserPreferences.selectAll().where { UserPreferences.userID eq userId }.firstOrNull()
            if (existing != null) {
                UserPreferences.update({ UserPreferences.userID eq userId }) {
                    sortBy?.let { s -> it[UserPreferences.sortBy] = SortBy.valueOf(s) }
                    groupBy?.let { g -> it[UserPreferences.groupBy] = GroupBy.fromApi(g) }
                    direction?.let { d -> it[UserPreferences.direction] = Direction.valueOf(d) }
                }
            } else {
                UserPreferences.insert {
                    it[UserPreferences.id] = CuidGenerator.newCuid()
                    it[UserPreferences.userID] = userId
                    sortBy?.let { s -> it[UserPreferences.sortBy] = SortBy.valueOf(s) }
                    groupBy?.let { g -> it[UserPreferences.groupBy] = GroupBy.fromApi(g) }
                    direction?.let { d -> it[UserPreferences.direction] = Direction.valueOf(d) }
                }
            }
        }
        return Unit.right()
    }
}
