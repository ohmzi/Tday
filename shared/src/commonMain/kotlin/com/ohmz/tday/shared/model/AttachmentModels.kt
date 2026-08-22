package com.ohmz.tday.shared.model

import kotlinx.serialization.Serializable

/**
 * Which task feed an attachment hangs off. Scheduled tasks and Anytime tasks are separate
 * entities with separate tables, so an attachment names one of them explicitly rather than
 * carrying an untyped task id.
 */
@Serializable
enum class AttachmentTaskType {
    TODO,
    FLOATER,
    ;

    /** Wire form used in URLs and JSON: lowercase, stable across platforms. */
    val wireValue: String get() = name.lowercase()

    companion object {
        fun fromWire(raw: String?): AttachmentTaskType? = when (raw?.lowercase()) {
            "todo" -> TODO
            "floater" -> FLOATER
            else -> null
        }
    }
}

/**
 * One picture attached to a task. Metadata only — bytes are fetched separately from the
 * content/thumbnail routes so a task list never carries image payloads.
 */
@Serializable
data class AttachmentDto(
    val id: String,
    val taskId: String,
    val taskType: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val width: Int? = null,
    val height: Int? = null,
    val createdAt: String? = null,
)

@Serializable
data class AttachmentsResponse(
    val attachments: List<AttachmentDto> = emptyList(),
)

@Serializable
data class AttachmentMutationResponse(
    val message: String? = null,
    val attachment: AttachmentDto? = null,
)

/** Limits shared by every client so an upload rejected by the server is caught before it starts. */
object AttachmentLimits {
    /** Per task, for both task types. */
    const val MAX_PER_TASK = 6

    const val MAX_BYTES = 10L * 1024 * 1024

    const val MAX_FILE_NAME_LENGTH = 255

    /**
     * Formats the server can decode and re-encode with a stock JDK.
     *
     * WebP and HEIC are deliberately absent: ImageIO ships no codec for either, and adding an
     * image-codec dependency to a self-hosted app that otherwise needs only Postgres is a worse
     * trade than having clients transcode. iOS in particular must convert HEIC camera output to
     * JPEG before upload.
     */
    val ALLOWED_CONTENT_TYPES = listOf("image/jpeg", "image/png")

    fun isAllowedContentType(contentType: String?): Boolean =
        contentType != null && ALLOWED_CONTENT_TYPES.any { it.equals(contentType, ignoreCase = true) }
}
