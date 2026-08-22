package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ohmz.tday.db.tables.Floaters
import com.ohmz.tday.db.tables.TaskAttachments
import com.ohmz.tday.db.tables.Todos
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.DomainEvent
import com.ohmz.tday.security.FieldEncryption
import com.ohmz.tday.security.decryptRequired
import com.ohmz.tday.security.encryptRequired
import com.ohmz.tday.shared.model.AttachmentDto
import com.ohmz.tday.shared.model.AttachmentLimits
import com.ohmz.tday.shared.model.AttachmentTaskType
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Bytes plus the content type to serve them with. */
data class AttachmentContent(
    val bytes: ByteArray,
    val contentType: String,
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * Removes a task's attachment rows and reports the storage keys that are now unreachable.
 *
 * Called from inside the deleting service's own transaction, which is why it is a plain object
 * rather than a method on [AttachmentService]: nesting a second suspended transaction inside a
 * task delete would be a different transaction, and the rows have to go with the task or not at all.
 *
 * A database cascade cannot replace this. It would drop the rows and leave their image files on
 * disk forever, because Postgres knows nothing about the attachment directory — so the caller
 * deletes the returned keys once its transaction commits.
 */
object TaskAttachmentCleanup {

    /** Purges attachments for the given todos. Returns every storage key to delete from disk. */
    fun purgeRowsForTodos(todoIds: List<String>): List<String> {
        if (todoIds.isEmpty()) return emptyList()
        val keys = TaskAttachments.selectAll()
            .where { TaskAttachments.todoID inList todoIds }
            .flatMap { it.storageKeys() }
        TaskAttachments.deleteWhere { TaskAttachments.todoID inList todoIds }
        return keys
    }

    /** Purges attachments for one floater. Returns every storage key to delete from disk. */
    fun purgeRowsForFloater(floaterId: String): List<String> {
        val keys = TaskAttachments.selectAll()
            .where { TaskAttachments.floaterID eq floaterId }
            .flatMap { it.storageKeys() }
        TaskAttachments.deleteWhere { TaskAttachments.floaterID eq floaterId }
        return keys
    }

    private fun ResultRow.storageKeys(): List<String> =
        listOfNotNull(this[TaskAttachments.storageKey], this[TaskAttachments.thumbnailKey])
}

/**
 * Pictures attached to scheduled tasks and to Anytime tasks.
 *
 * Ownership is enforced through the parent task on every path, including the byte-serving ones:
 * an attachment id is not a capability, so a valid id belonging to another user reads as
 * not-found rather than serving their photo.
 */
interface AttachmentService {
    suspend fun listForTask(
        userId: String,
        taskType: AttachmentTaskType,
        taskId: String,
    ): Either<AppError, List<AttachmentDto>>

    suspend fun create(
        userId: String,
        taskType: AttachmentTaskType,
        taskId: String,
        fileName: String,
        bytes: ByteArray,
    ): Either<AppError, AttachmentDto>

    suspend fun content(
        userId: String,
        attachmentId: String,
        thumbnail: Boolean,
    ): Either<AppError, AttachmentContent>

    suspend fun delete(userId: String, attachmentId: String): Either<AppError, Unit>
}

class AttachmentServiceImpl(
    private val storage: AttachmentStorage,
    private val fieldEncryption: FieldEncryption,
    private val cache: CacheService,
    private val publisher: RealtimePublisher,
) : AttachmentService {

    override suspend fun listForTask(
        userId: String,
        taskType: AttachmentTaskType,
        taskId: String,
    ): Either<AppError, List<AttachmentDto>> {
        val rows = newSuspendedTransaction(Dispatchers.IO) {
            if (!ownsTask(userId, taskType, taskId)) return@newSuspendedTransaction null
            TaskAttachments.selectAll()
                .where { ownerColumnMatches(taskType, taskId) }
                .orderBy(TaskAttachments.createdAt to SortOrder.ASC)
                .map { it.toDto() }
        } ?: return AppError.NotFound("task not found").left()
        return rows.right()
    }

    override suspend fun create(
        userId: String,
        taskType: AttachmentTaskType,
        taskId: String,
        fileName: String,
        bytes: ByteArray,
    ): Either<AppError, AttachmentDto> {
        if (!storage.isWritable) {
            return AppError.Internal("attachment storage is not writable").left()
        }

        val sanitized = storage.sanitize(bytes, AttachmentLimits.MAX_BYTES).getOrElse { error ->
            return when ((error as? ImageRejectedException)?.rejection) {
                ImageRejection.TooLarge -> AppError.BadRequest("image is too large")
                ImageRejection.UnsupportedType -> AppError.BadRequest("only JPEG and PNG images are supported")
                ImageRejection.TooManyPixels -> AppError.BadRequest("image resolution is too large")
                else -> AppError.BadRequest("image could not be read")
            }.left()
        }

        val format = ImageFormat.fromContentType(sanitized.contentType)
            ?: return AppError.BadRequest("only JPEG and PNG images are supported").left()

        // Ownership and the per-task cap are checked before anything touches the disk, so a
        // rejected upload cannot leave a stray file behind.
        val precheck = newSuspendedTransaction(Dispatchers.IO) {
            if (!ownsTask(userId, taskType, taskId)) return@newSuspendedTransaction Precheck.MISSING_TASK
            val count = TaskAttachments.selectAll()
                .where { ownerColumnMatches(taskType, taskId) }
                .count()
            if (count >= AttachmentLimits.MAX_PER_TASK) Precheck.FULL else Precheck.OK
        }
        when (precheck) {
            Precheck.MISSING_TASK -> return AppError.NotFound("task not found").left()
            Precheck.FULL -> return AppError.Conflict(
                "a task can hold at most ${AttachmentLimits.MAX_PER_TASK} pictures",
            ).left()
            Precheck.OK -> Unit
        }

        val id = CuidGenerator.newCuid()
        val storageKey = runCatching {
            storage.write(id, "", sanitized.bytes, format.fileSuffix)
        }.getOrElse { return AppError.Internal("could not store image").left() }

        val thumbnailKey = runCatching {
            storage.write(id, AttachmentStorage.THUMBNAIL_SUFFIX, sanitized.thumbnailBytes, format.fileSuffix)
        }.getOrNull()

        val safeName = fileName.trim().take(AttachmentLimits.MAX_FILE_NAME_LENGTH).ifBlank { "image.${format.fileSuffix}" }
        val now = LocalDateTime.now(ZoneOffset.UTC)

        val dto = runCatching {
            newSuspendedTransaction(Dispatchers.IO) {
                TaskAttachments.insert {
                    it[TaskAttachments.id] = id
                    it[TaskAttachments.userID] = userId
                    it[TaskAttachments.todoID] = taskId.takeIf { taskType == AttachmentTaskType.TODO }
                    it[TaskAttachments.floaterID] = taskId.takeIf { taskType == AttachmentTaskType.FLOATER }
                    it[TaskAttachments.fileName] = fieldEncryption.encryptRequired("fileName", safeName)
                    it[TaskAttachments.contentType] = sanitized.contentType
                    it[TaskAttachments.sizeBytes] = sanitized.bytes.size.toLong()
                    it[TaskAttachments.width] = sanitized.width
                    it[TaskAttachments.height] = sanitized.height
                    it[TaskAttachments.storageKey] = storageKey
                    it[TaskAttachments.thumbnailKey] = thumbnailKey
                    it[TaskAttachments.createdAt] = now
                }
                AttachmentDto(
                    id = id,
                    taskId = taskId,
                    taskType = taskType.wireValue,
                    fileName = safeName,
                    contentType = sanitized.contentType,
                    sizeBytes = sanitized.bytes.size.toLong(),
                    width = sanitized.width,
                    height = sanitized.height,
                    createdAt = now.toString(),
                )
            }
        }.getOrElse { error ->
            // The row is the record of truth. If it never landed, the files it would have pointed
            // at are unreachable, so they are removed instead of being left to accumulate.
            storage.delete(storageKey)
            storage.delete(thumbnailKey)
            return AppError.Internal("could not save attachment: ${error.message}").left()
        }

        notifyChange(userId, taskType)
        return dto.right()
    }

    override suspend fun content(
        userId: String,
        attachmentId: String,
        thumbnail: Boolean,
    ): Either<AppError, AttachmentContent> {
        val row = newSuspendedTransaction(Dispatchers.IO) {
            TaskAttachments.selectAll()
                .where { (TaskAttachments.id eq attachmentId) and (TaskAttachments.userID eq userId) }
                .limit(1)
                .firstOrNull()
        } ?: return AppError.NotFound("attachment not found").left()

        // Falls back to the full image when no thumbnail was generated, so a picture is never
        // missing from a list just because thumbnailing failed at upload time.
        val key = row[TaskAttachments.thumbnailKey]?.takeIf { thumbnail } ?: row[TaskAttachments.storageKey]
        val bytes = storage.read(key)
            ?: return AppError.NotFound("attachment file is missing").left()

        return AttachmentContent(bytes = bytes, contentType = row[TaskAttachments.contentType]).right()
    }

    override suspend fun delete(userId: String, attachmentId: String): Either<AppError, Unit> {
        val removed = newSuspendedTransaction(Dispatchers.IO) {
            val row = TaskAttachments.selectAll()
                .where { (TaskAttachments.id eq attachmentId) and (TaskAttachments.userID eq userId) }
                .limit(1)
                .firstOrNull() ?: return@newSuspendedTransaction null

            TaskAttachments.deleteWhere { TaskAttachments.id eq attachmentId }
            Triple(
                row[TaskAttachments.storageKey],
                row[TaskAttachments.thumbnailKey],
                if (row[TaskAttachments.todoID] != null) AttachmentTaskType.TODO else AttachmentTaskType.FLOATER,
            )
        } ?: return AppError.NotFound("attachment not found").left()

        storage.delete(removed.first)
        storage.delete(removed.second)
        notifyChange(userId, removed.third)
        return Unit.right()
    }

    private fun ownerColumnMatches(taskType: AttachmentTaskType, taskId: String) =
        when (taskType) {
            AttachmentTaskType.TODO -> TaskAttachments.todoID eq taskId
            AttachmentTaskType.FLOATER -> TaskAttachments.floaterID eq taskId
        }

    /** An attachment is reachable only when its parent task belongs to [userId]. */
    private fun ownsTask(userId: String, taskType: AttachmentTaskType, taskId: String): Boolean =
        when (taskType) {
            AttachmentTaskType.TODO ->
                Todos.selectAll()
                    .where { (Todos.id eq taskId) and (Todos.userID eq userId) }
                    .limit(1).any()

            AttachmentTaskType.FLOATER ->
                Floaters.selectAll()
                    .where { (Floaters.id eq taskId) and (Floaters.userID eq userId) }
                    .limit(1).any()
        }

    private suspend fun notifyChange(userId: String, taskType: AttachmentTaskType) {
        when (taskType) {
            AttachmentTaskType.TODO -> {
                cache.invalidateTodoCaches(userId)
                publisher.publishToCollaborators(userId, DomainEvent.TodoChanged())
            }
            AttachmentTaskType.FLOATER -> {
                cache.invalidateFloaterCaches(userId)
                publisher.publishToCollaborators(userId, DomainEvent.FloaterChanged())
            }
        }
    }

    private fun ResultRow.toDto(): AttachmentDto {
        val todoId = this[TaskAttachments.todoID]
        return AttachmentDto(
            id = this[TaskAttachments.id],
            taskId = todoId ?: this[TaskAttachments.floaterID].orEmpty(),
            taskType = if (todoId != null) {
                AttachmentTaskType.TODO.wireValue
            } else {
                AttachmentTaskType.FLOATER.wireValue
            },
            fileName = fieldEncryption.decryptRequired(this[TaskAttachments.fileName]),
            contentType = this[TaskAttachments.contentType],
            sizeBytes = this[TaskAttachments.sizeBytes],
            width = this[TaskAttachments.width],
            height = this[TaskAttachments.height],
            createdAt = this[TaskAttachments.createdAt].toString(),
        )
    }

    private enum class Precheck { OK, MISSING_TASK, FULL }
}
