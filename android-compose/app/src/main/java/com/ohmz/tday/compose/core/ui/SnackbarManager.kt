package com.ohmz.tday.compose.core.ui

import android.content.Context
import com.ohmz.tday.compose.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class SnackbarKind { ERROR, SUCCESS, INFO }

data class SnackbarEvent(
    val message: String,
    val kind: SnackbarKind = SnackbarKind.ERROR,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

@Singleton
class SnackbarManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _events = MutableSharedFlow<SnackbarEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<SnackbarEvent> = _events.asSharedFlow()

    fun show(event: SnackbarEvent) {
        _events.tryEmit(event)
    }

    fun showError(message: String, retry: (() -> Unit)? = null) {
        _events.tryEmit(
            SnackbarEvent(
                message = message,
                kind = SnackbarKind.ERROR,
                actionLabel = if (retry != null) context.getString(R.string.action_retry) else null,
                onAction = retry,
            ),
        )
    }

    fun showSuccess(message: String) {
        _events.tryEmit(
            SnackbarEvent(message = message, kind = SnackbarKind.SUCCESS),
        )
    }
}
