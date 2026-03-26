package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.db.tables.Notes
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.models.response.NoteResponse
import com.ohmz.tday.security.FieldEncryption
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

interface NoteService {
    suspend fun getAll(userId: String): Either<AppError, List<NoteResponse>>
    suspend fun getById(userId: String, noteId: String): Either<AppError, NoteResponse>
    suspend fun create(userId: String, name: String, content: String?): Either<AppError, NoteResponse>
    suspend fun update(userId: String, noteId: String, name: String?, content: String?): Either<AppError, Unit>
    suspend fun delete(userId: String, noteId: String): Either<AppError, Int>
}

class NoteServiceImpl(private val fieldEncryption: FieldEncryption) : NoteService {
    override suspend fun getAll(userId: String): Either<AppError, List<NoteResponse>> {
        val notes = transaction {
            Notes.selectAll().where { Notes.userID eq userId }
                .orderBy(Notes.createdAt, SortOrder.DESC)
                .map { it.toNoteResponse() }
        }
        return notes.right()
    }

    override suspend fun getById(userId: String, noteId: String): Either<AppError, NoteResponse> {
        val note = transaction {
            Notes.selectAll().where { (Notes.id eq noteId) and (Notes.userID eq userId) }
                .firstOrNull()?.toNoteResponse()
        }
        return note?.right() ?: Either.Left(AppError.NotFound("note not found"))
    }

    override suspend fun create(userId: String, name: String, content: String?): Either<AppError, NoteResponse> {
        val id = CuidGenerator.newCuid()
        val now = LocalDateTime.now()
        transaction {
            Notes.insert {
                it[Notes.id] = id
                it[Notes.name] = name
                it[Notes.content] = fieldEncryption.encryptIfSensitive("content", content)
                it[Notes.userID] = userId
                it[Notes.createdAt] = now
            }
        }
        return NoteResponse(id = id, name = name, content = content, createdAt = now.toString()).right()
    }

    override suspend fun update(userId: String, noteId: String, name: String?, content: String?): Either<AppError, Unit> {
        transaction {
            Notes.update({ (Notes.id eq noteId) and (Notes.userID eq userId) }) {
                name?.let { n -> it[Notes.name] = n }
                content?.let { c -> it[Notes.content] = fieldEncryption.encryptIfSensitive("content", c) }
            }
        }
        return Unit.right()
    }

    override suspend fun delete(userId: String, noteId: String): Either<AppError, Int> {
        val count = transaction {
            Notes.deleteWhere { (Notes.id eq noteId) and (Notes.userID eq userId) }
        }
        return count.right()
    }

    private fun ResultRow.toNoteResponse(): NoteResponse = NoteResponse(
        id = this[Notes.id],
        name = this[Notes.name],
        content = fieldEncryption.decryptIfEncrypted(this[Notes.content]),
        createdAt = this[Notes.createdAt].toString(),
    )
}
