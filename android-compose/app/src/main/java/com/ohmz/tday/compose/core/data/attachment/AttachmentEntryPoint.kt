package com.ohmz.tday.compose.core.data.attachment

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Lets the attachments section reach its repository without threading it through the task sheet's
 * already-long parameter list. The section owns its own loading and error state, the same way the
 * device-calendar settings rows do.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AttachmentEntryPoint {
    fun attachmentRepository(): AttachmentRepository
}
