package com.ohmz.tday.compose.core.data.attachment

import android.content.Context
import android.net.Uri
import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.core.data.requireApiBody
import com.ohmz.tday.compose.core.model.AttachmentDto
import com.ohmz.tday.compose.core.model.AttachmentTaskType
import com.ohmz.tday.compose.core.network.TdayApiService
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import com.ohmz.tday.shared.model.AttachmentLimits
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** Why an attachment could not be added, in terms the UI can turn into a message. */
enum class AttachmentUploadError {
    UNSUPPORTED_TYPE,
    TOO_LARGE,
    UNAVAILABLE_OFFLINE,
    FAILED,
}

/**
 * Pictures attached to scheduled tasks and to Anytime tasks.
 *
 * Server Mode only, and deliberately not queued as a pending mutation: the offline queue persists
 * user intent as small JSON records, and parking multi-megabyte images in it would bloat the cache
 * the sync replay has to carry. In Local Mode there is no remote target at all, so the UI hides
 * the feature rather than accepting an upload it could never complete.
 */
@Singleton
class AttachmentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: TdayApiService,
    private val secureConfigStore: SecureConfigStore,
) {
    fun isAvailable(): Boolean = !secureConfigStore.isLocalMode()

    suspend fun list(taskType: AttachmentTaskType, taskId: String): List<AttachmentDto> {
        if (!isAvailable()) return emptyList()
        return runCatching {
            withContext(Dispatchers.IO) {
                val response = when (taskType) {
                    AttachmentTaskType.TODO -> api.getTodoAttachments(taskId)
                    AttachmentTaskType.FLOATER -> api.getFloaterAttachments(taskId)
                }
                requireApiBody(response, "Could not load pictures").attachments
            }
        }.getOrElse { emptyList() }
    }

    /**
     * Reads the picked image and uploads it.
     *
     * The content resolver is the only thing that can read a `content://` URI handed over by the
     * photo picker, and the read happens here rather than in the ViewModel so the bytes never
     * outlive the call.
     */
    suspend fun upload(
        taskType: AttachmentTaskType,
        taskId: String,
        uri: Uri,
    ): Result<AttachmentDto> {
        if (!isAvailable()) return Result.failure(AttachmentUploadException(AttachmentUploadError.UNAVAILABLE_OFFLINE))

        // Bound as non-null here so the media type is a plain String downstream rather than
        // something the upload has to re-assert with a force-unwrap.
        val contentType = context.contentResolver.getType(uri)
            ?.takeIf { AttachmentLimits.isAllowedContentType(it) }
            ?: return Result.failure(AttachmentUploadException(AttachmentUploadError.UNSUPPORTED_TYPE))

        val bytes = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
        } ?: return Result.failure(AttachmentUploadException(AttachmentUploadError.FAILED))

        if (bytes.size > AttachmentLimits.MAX_BYTES) {
            return Result.failure(AttachmentUploadException(AttachmentUploadError.TOO_LARGE))
        }

        return runCatching {
            withContext(Dispatchers.IO) {
                val part = MultipartBody.Part.createFormData(
                    name = "file",
                    filename = displayName(uri) ?: "image",
                    body = bytes.toRequestBody(contentType.toMediaType()),
                )
                val response = when (taskType) {
                    AttachmentTaskType.TODO -> api.uploadTodoAttachment(taskId, part)
                    AttachmentTaskType.FLOATER -> api.uploadFloaterAttachment(taskId, part)
                }
                requireApiBody(response, "Could not add that picture").attachment
                    ?: throw IllegalStateException("upload returned no attachment")
            }
        }.onFailure {
            TdayTelemetry.capture(it, "attachment.upload", mapOf("taskType" to taskType.wireValue))
        }.recoverCatching {
            throw AttachmentUploadException(AttachmentUploadError.FAILED)
        }
    }

    suspend fun delete(attachmentId: String): Result<Unit> {
        if (!isAvailable()) return Result.failure(AttachmentUploadException(AttachmentUploadError.UNAVAILABLE_OFFLINE))
        return runCatching {
            withContext(Dispatchers.IO) {
                requireApiBody(api.deleteAttachment(attachmentId), "Could not remove that picture")
                Unit
            }
        }.onFailure { TdayTelemetry.capture(it, "attachment.delete") }
    }

    /**
     * Fetches an attachment's bytes through the app's own API client.
     *
     * Deliberately not a URL handed to a general-purpose image loader: these images sit behind the
     * session cookie, and this client is the one thing that already carries the cookie jar, the
     * pinned server trust, and the resolved base URL.
     */
    suspend fun bytes(attachmentId: String, thumbnail: Boolean): ByteArray? {
        if (!isAvailable()) return null
        return runCatching {
            withContext(Dispatchers.IO) {
                val response = if (thumbnail) {
                    api.downloadAttachmentThumbnail(attachmentId)
                } else {
                    api.downloadAttachment(attachmentId)
                }
                if (!response.isSuccessful) null else response.body()?.bytes()
            }
        }.getOrNull()
    }

    private fun displayName(uri: Uri): String? =
        runCatching { uri.lastPathSegment?.substringAfterLast('/') }.getOrNull()
}

class AttachmentUploadException(val error: AttachmentUploadError) : Exception(error.name)
