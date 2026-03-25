package com.ohmz.tday.services

import com.ohmz.tday.db.tables.Notes
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.security.FieldEncryption
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

object NoteService {
    fun getAll(userId: String): List<Map<String, Any?>> = transaction {
        Notes.selectAll().where { Notes.userID eq userId }
            .orderBy(Notes.createdAt, SortOrder.DESC)
            .map { it.toNoteMap() }
    }

    fun getById(userId: String, noteId: String): Map<String, Any?>? = transaction {
        Notes.selectAll().where { (Notes.id eq noteId) and (Notes.userID eq userId) }
            .firstOrNull()?.toNoteMap()
    }

    fun create(userId: String, name: String, content: String?): Map<String, Any?> {
        val id = CuidGenerator.newCuid()
        transaction {
            Notes.insert {
                it[Notes.id] = id
                it[Notes.name] = name
                it[Notes.content] = FieldEncryption.encryptIfSensitive("content", content)
                it[Notes.userID] = userId
                it[Notes.createdAt] = LocalDateTime.now()
            }
        }
        return mapOf("id" to id, "name" to name)
    }

    fun update(userId: String, noteId: String, name: String?, content: String?) {
        transaction {
            Notes.update({ (Notes.id eq noteId) and (Notes.userID eq userId) }) {
                name?.let { n -> it[Notes.name] = n }
                content?.let { c -> it[Notes.content] = FieldEncryption.encryptIfSensitive("content", c) }
            }
        }
    }

    fun delete(userId: String, noteId: String): Int = transaction {
        Notes.deleteWhere { (Notes.id eq noteId) and (Notes.userID eq userId) }
    }

    private fun ResultRow.toNoteMap(): Map<String, Any?> = mapOf(
        "id" to this[Notes.id],
        "name" to this[Notes.name],
        "content" to FieldEncryption.decryptIfEncrypted(this[Notes.content]),
        "createdAt" to this[Notes.createdAt].toString(),
    )
}
