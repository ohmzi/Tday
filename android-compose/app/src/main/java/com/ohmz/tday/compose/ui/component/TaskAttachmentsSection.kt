package com.ohmz.tday.compose.ui.component

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.attachment.AttachmentEntryPoint
import com.ohmz.tday.compose.core.data.attachment.AttachmentUploadError
import com.ohmz.tday.compose.core.data.attachment.AttachmentUploadException
import com.ohmz.tday.compose.core.model.AttachmentDto
import com.ohmz.tday.compose.core.model.AttachmentTaskType
import com.ohmz.tday.shared.model.AttachmentLimits
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

/**
 * Pictures attached to a task, for both task types — pass [taskType] to say which feed the task
 * lives in.
 *
 * Only shown for a saved task, because an attachment needs a task id to hang off. Hidden entirely
 * in Local Mode: there is no server to hold the bytes, and offering an upload that could never
 * complete is worse than not offering one.
 *
 * Thumbnails are fetched through the app's API client rather than a general-purpose image loader,
 * since the images sit behind the session cookie and pinned server trust that client already owns.
 */
@Composable
fun TaskAttachmentsSection(
    taskType: AttachmentTaskType,
    taskId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val repository = remember {
        EntryPointAccessors
            .fromApplication(context.applicationContext, AttachmentEntryPoint::class.java)
            .attachmentRepository()
    }

    if (!repository.isAvailable()) return

    var attachments by remember(taskId) { mutableStateOf<List<AttachmentDto>>(emptyList()) }
    var isBusy by remember(taskId) { mutableStateOf(false) }
    var errorMessage by remember(taskId) { mutableStateOf<String?>(null) }
    var preview by remember(taskId) { mutableStateOf<AttachmentDto?>(null) }
    val thumbnails = remember(taskId) { SnapshotStateMap<String, ImageBitmap>() }

    suspend fun reload() {
        attachments = repository.list(taskType, taskId)
    }

    LaunchedEffect(taskType, taskId) { reload() }

    // Decode each thumbnail once and keep it for as long as the sheet is open. Keyed by id, so a
    // reload after an upload only fetches the picture that is actually new.
    LaunchedEffect(attachments) {
        attachments.forEach { attachment ->
            if (thumbnails.containsKey(attachment.id)) return@forEach
            val bytes = repository.bytes(attachment.id, thumbnail = true) ?: return@forEach
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@forEach
            thumbnails[attachment.id] = bitmap.asImageBitmap()
        }
    }

    val unsupportedMessage = stringResource(R.string.attachments_unsupported_type)
    val tooLargeMessage = stringResource(R.string.attachments_too_large)
    val uploadFailedMessage = stringResource(R.string.attachments_upload_failed)
    val deleteFailedMessage = stringResource(R.string.attachments_delete_failed)

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isBusy = true
            errorMessage = null
            repository.upload(taskType, taskId, uri)
                .onSuccess { reload() }
                .onFailure { failure ->
                    errorMessage = when ((failure as? AttachmentUploadException)?.error) {
                        AttachmentUploadError.UNSUPPORTED_TYPE -> unsupportedMessage
                        AttachmentUploadError.TOO_LARGE -> tooLargeMessage
                        else -> uploadFailedMessage
                    }
                }
            isBusy = false
        }
    }

    val isFull = attachments.size >= AttachmentLimits.MAX_PER_TASK

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (attachments.isEmpty()) {
                stringResource(R.string.attachments)
            } else {
                stringResource(
                    R.string.attachments_with_count,
                    attachments.size,
                    AttachmentLimits.MAX_PER_TASK,
                )
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            color = colorScheme.onSurface,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            attachments.forEach { attachment ->
                Box {
                    Box(
                        modifier = Modifier
                            .size(THUMBNAIL_SIZE)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colorScheme.surfaceVariant)
                            .clickable { preview = attachment },
                        contentAlignment = Alignment.Center,
                    ) {
                        thumbnails[attachment.id]?.let { bitmap ->
                            Image(
                                bitmap = bitmap,
                                contentDescription = attachment.fileName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-6).dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(colorScheme.surface)
                            .border(1.dp, colorScheme.outlineVariant, CircleShape)
                            .clickable {
                                scope.launch {
                                    errorMessage = null
                                    repository.delete(attachment.id)
                                        .onSuccess {
                                            thumbnails.remove(attachment.id)
                                            reload()
                                        }
                                        .onFailure { errorMessage = deleteFailedMessage }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_x),
                            contentDescription = stringResource(
                                R.string.attachments_remove,
                                attachment.fileName,
                            ),
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }

            if (!isFull) {
                Box(
                    modifier = Modifier
                        .size(THUMBNAIL_SIZE)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .clickable(enabled = !isBusy) {
                            pickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_plus),
                            contentDescription = stringResource(R.string.attachments_add),
                            tint = colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.error,
            )
        }
    }

    preview?.let { attachment ->
        AttachmentPreviewDialog(
            attachment = attachment,
            loadBytes = { repository.bytes(attachment.id, thumbnail = false) },
            onDismiss = { preview = null },
        )
    }
}

/** Full-screen look at one picture. Loads the full-size bytes lazily, not with the thumbnails. */
@Composable
private fun AttachmentPreviewDialog(
    attachment: AttachmentDto,
    loadBytes: suspend () -> ByteArray?,
    onDismiss: () -> Unit,
) {
    var full by remember(attachment.id) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(attachment.id) {
        val bytes = loadBytes() ?: return@LaunchedEffect
        full = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.92f), RoundedCornerShape(16.dp))
                .clickable(onClick = onDismiss)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            full?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = attachment.fileName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            } ?: CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        }
    }
}

private val THUMBNAIL_SIZE = 72.dp
