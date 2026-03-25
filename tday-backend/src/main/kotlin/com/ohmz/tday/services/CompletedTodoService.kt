package com.ohmz.tday.services

import com.ohmz.tday.db.tables.CompletedTodos
import com.ohmz.tday.security.FieldEncryption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object CompletedTodoService {
    fun getAll(userId: String): List<Map<String, Any?>> = transaction {
        CompletedTodos.selectAll().where { CompletedTodos.userID eq userId }
            .orderBy(CompletedTodos.completedAt, SortOrder.DESC)
            .map { it.toCompletedMap() }
    }

    fun deleteAll(userId: String): Int {
        val count = transaction {
            CompletedTodos.deleteWhere { CompletedTodos.userID eq userId }
        }
        MemoryCache.invalidateCompletedCaches(userId)
        return count
    }

    fun deleteById(userId: String, id: String): Int {
        val count = transaction {
            CompletedTodos.deleteWhere { (CompletedTodos.id eq id) and (CompletedTodos.userID eq userId) }
        }
        MemoryCache.invalidateCompletedCaches(userId)
        return count
    }

    private fun ResultRow.toCompletedMap(): Map<String, Any?> = mapOf(
        "id" to this[CompletedTodos.id],
        "originalTodoID" to this[CompletedTodos.originalTodoID],
        "title" to this[CompletedTodos.title],
        "description" to FieldEncryption.decryptIfEncrypted(this[CompletedTodos.description]),
        "priority" to this[CompletedTodos.priority].name,
        "completedAt" to this[CompletedTodos.completedAt].toString(),
        "dtstart" to this[CompletedTodos.dtstart].toString(),
        "due" to this[CompletedTodos.due].toString(),
        "completedOnTime" to this[CompletedTodos.completedOnTime],
        "daysToComplete" to this[CompletedTodos.daysToComplete].toDouble(),
        "rrule" to this[CompletedTodos.rrule],
        "userID" to this[CompletedTodos.userID],
        "instanceDate" to this[CompletedTodos.instanceDate]?.toString(),
        "listName" to this[CompletedTodos.listName],
        "listColor" to this[CompletedTodos.listColor],
    )
}
