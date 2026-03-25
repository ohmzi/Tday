package com.ohmz.tday.services

import com.ohmz.tday.db.enums.ListColor
import com.ohmz.tday.db.tables.Lists
import com.ohmz.tday.db.tables.Todos
import com.ohmz.tday.db.util.CuidGenerator
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

object ListService {
    fun getAll(userId: String): List<Map<String, Any?>> = transaction {
        Lists.selectAll().where { Lists.userID eq userId }
            .orderBy(Lists.createdAt, SortOrder.DESC)
            .map { it.toListMap() }
    }

    fun getById(userId: String, listId: String): Map<String, Any?>? = transaction {
        Lists.selectAll().where { (Lists.id eq listId) and (Lists.userID eq userId) }
            .firstOrNull()?.toListMap()
    }

    fun getTodosForList(userId: String, listId: String): List<Map<String, Any?>> = transaction {
        Todos.selectAll().where {
            (Todos.userID eq userId) and (Todos.listID eq listId) and (Todos.completed eq false)
        }.orderBy(Todos.order, SortOrder.ASC).map { row ->
            mapOf(
                "id" to row[Todos.id],
                "title" to row[Todos.title],
                "priority" to row[Todos.priority].name,
                "dtstart" to row[Todos.dtstart].toString(),
                "due" to row[Todos.due].toString(),
                "completed" to row[Todos.completed],
                "order" to row[Todos.order],
            )
        }
    }

    fun create(userId: String, name: String, color: String?, iconKey: String?): Map<String, Any?> {
        val id = CuidGenerator.newCuid()
        val now = LocalDateTime.now()
        transaction {
            Lists.insert {
                it[Lists.id] = id
                it[Lists.name] = name
                it[Lists.color] = color?.let { c -> ListColor.valueOf(c) }
                it[Lists.iconKey] = iconKey
                it[Lists.userID] = userId
                it[Lists.createdAt] = now
                it[Lists.updatedAt] = now
            }
        }
        MemoryCache.invalidateListCaches(userId)
        return mapOf("id" to id, "name" to name)
    }

    fun update(userId: String, id: String, name: String?, color: String?, iconKey: String?) {
        transaction {
            Lists.update({ (Lists.id eq id) and (Lists.userID eq userId) }) {
                name?.let { n -> it[Lists.name] = n }
                color?.let { c -> it[Lists.color] = ListColor.valueOf(c) }
                iconKey?.let { k -> it[Lists.iconKey] = k }
                it[Lists.updatedAt] = LocalDateTime.now()
            }
        }
        MemoryCache.invalidateListCaches(userId)
    }

    fun delete(userId: String, id: String): Int {
        val count = transaction {
            Todos.update({ (Todos.listID eq id) and (Todos.userID eq userId) }) {
                it[Todos.listID] = null
            }
            Lists.deleteWhere { (Lists.id eq id) and (Lists.userID eq userId) }
        }
        MemoryCache.invalidateListCaches(userId)
        return count
    }

    private fun ResultRow.toListMap(): Map<String, Any?> = mapOf(
        "id" to this[Lists.id],
        "name" to this[Lists.name],
        "color" to this[Lists.color]?.name,
        "iconKey" to this[Lists.iconKey],
        "userID" to this[Lists.userID],
        "createdAt" to this[Lists.createdAt].toString(),
        "updatedAt" to this[Lists.updatedAt].toString(),
    )
}
