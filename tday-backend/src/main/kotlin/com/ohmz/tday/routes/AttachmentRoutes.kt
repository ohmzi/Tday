package com.ohmz.tday.routes

import arrow.core.Either
import com.ohmz.tday.di.inject
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.requireApprovedAuthUser
import com.ohmz.tday.domain.respondError
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.services.AttachmentService
import com.ohmz.tday.shared.model.AttachmentLimits
import com.ohmz.tday.shared.model.AttachmentMutationResponse
import com.ohmz.tday.shared.model.AttachmentTaskType
import com.ohmz.tday.shared.model.AttachmentsResponse
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.cacheControl
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Pictures attached to a task, for both task types.
 *
 * Metadata routes hang off the owning task so a client never has to guess which feed a task lives
 * in. Byte routes are flat and id-addressed so an image can be cached and re-fetched on its own.
 *
 * The byte routes cannot use [withAuth]: it serializes its result as JSON, and these respond with
 * image bytes. They authenticate explicitly instead, and [AttachmentService] still re-checks
 * ownership on every read — an attachment id is not a capability.
 */
fun Route.attachmentRoutes() {
    val attachmentService by inject<AttachmentService>()

    route("/todo/{todoId}/attachments") {
        get {
            call.withAuth { user ->
                call.listFor(attachmentService, user.id, AttachmentTaskType.TODO, "todoId")
            }
        }
        post {
            call.withAuth(HttpStatusCode.Created) { user ->
                call.uploadTo(attachmentService, user.id, AttachmentTaskType.TODO, "todoId")
            }
        }
    }

    route("/floater/{floaterId}/attachments") {
        get {
            call.withAuth { user ->
                call.listFor(attachmentService, user.id, AttachmentTaskType.FLOATER, "floaterId")
            }
        }
        post {
            call.withAuth(HttpStatusCode.Created) { user ->
                call.uploadTo(attachmentService, user.id, AttachmentTaskType.FLOATER, "floaterId")
            }
        }
    }

    route("/attachment/{attachmentId}") {
        get { call.respondAttachmentBytes(attachmentService, thumbnail = false) }
        get("/thumbnail") { call.respondAttachmentBytes(attachmentService, thumbnail = true) }

        delete {
            call.withAuth { user ->
                val attachmentId = call.parameters["attachmentId"].orEmpty()
                if (attachmentId.isBlank()) {
                    Either.Left(AppError.BadRequest("attachment id is required"))
                } else {
                    attachmentService.delete(user.id, attachmentId)
                        .map { mapOf("message" to "attachment deleted") }
                }
            }
        }
    }
}

private suspend fun ApplicationCall.listFor(
    service: AttachmentService,
    userId: String,
    taskType: AttachmentTaskType,
    parameterName: String,
): Either<AppError, AttachmentsResponse> {
    val taskId = parameters[parameterName].orEmpty()
    if (taskId.isBlank()) return Either.Left(AppError.BadRequest("task id is required"))
    return service.listForTask(userId, taskType, taskId)
        .map { AttachmentsResponse(attachments = it) }
}

private suspend fun ApplicationCall.uploadTo(
    service: AttachmentService,
    userId: String,
    taskType: AttachmentTaskType,
    parameterName: String,
): Either<AppError, AttachmentMutationResponse> {
    val taskId = parameters[parameterName].orEmpty()
    if (taskId.isBlank()) return Either.Left(AppError.BadRequest("task id is required"))

    var fileName: String? = null
    var bytes: ByteArray? = null
    var exceededLimit = false

    receiveMultipart().forEachPart { part ->
        if (part is PartData.FileItem && bytes == null) {
            fileName = part.originalFileName
            val read = part.streamProvider().use { stream ->
                // Read one byte past the cap: enough to know the upload is oversized without
                // buffering an unbounded body into memory to find out.
                stream.readNBytes((AttachmentLimits.MAX_BYTES + 1).toInt())
            }
            if (read.size > AttachmentLimits.MAX_BYTES) exceededLimit = true else bytes = read
        }
        part.dispose()
    }

    if (exceededLimit) return Either.Left(AppError.BadRequest("image is too large"))
    val payload = bytes ?: return Either.Left(AppError.BadRequest("an image file is required"))

    return service.create(userId, taskType, taskId, fileName.orEmpty(), payload)
        .map { AttachmentMutationResponse(message = "attachment created", attachment = it) }
}

private suspend fun ApplicationCall.respondAttachmentBytes(
    service: AttachmentService,
    thumbnail: Boolean,
) {
    val user = when (val auth = requireApprovedAuthUser()) {
        is Either.Left -> {
            respondError(auth.value)
            return
        }
        is Either.Right -> auth.value
    }

    val attachmentId = parameters["attachmentId"].orEmpty()
    if (attachmentId.isBlank()) {
        respondError(AppError.BadRequest("attachment id is required"))
        return
    }

    when (val result = service.content(user.id, attachmentId, thumbnail)) {
        is Either.Left -> respondError(result.value)
        is Either.Right -> {
            // Private: these are the user's photos, and a shared cache must never hand one to a
            // different session. Immutable because an attachment's bytes never change — a new
            // picture is a new id.
            response.cacheControl(CacheControl.MaxAge(CACHE_SECONDS, visibility = CacheControl.Visibility.Private))
            response.headers.append(HttpHeaders.ContentDisposition, "inline")
            respondBytes(
                bytes = result.value.bytes,
                contentType = ContentType.parse(result.value.contentType),
            )
        }
    }
}

private const val CACHE_SECONDS = 60 * 60 * 24 * 30
