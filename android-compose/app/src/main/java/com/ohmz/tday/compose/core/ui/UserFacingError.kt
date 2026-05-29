package com.ohmz.tday.compose.core.ui

import android.content.Context
import androidx.annotation.StringRes
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.ApiCallException
import kotlinx.serialization.SerializationException

fun Throwable.userFacingMessage(
    context: Context,
    @StringRes fallbackRes: Int = R.string.error_generic,
): String {
    if (this is SerializationException || this.cause is SerializationException) {
        return context.getString(R.string.error_app_outdated)
    }

    if (this is ApiCallException) {
        return when (statusCode) {
            401 -> context.getString(R.string.error_auth_expired)
            403 -> context.getString(R.string.error_permission_denied)
            404 -> context.getString(R.string.error_not_found)
            in 500..599 -> context.getString(R.string.error_server)
            else -> context.getString(fallbackRes)
        }
    }

    val msg = message.orEmpty().lowercase()
    if (msg.contains("failed to connect") ||
        msg.contains("unable to resolve host") ||
        msg.contains("network is unreachable") ||
        msg.contains("econnrefused") ||
        msg.contains("timed out")
    ) {
        return context.getString(R.string.error_connection)
    }

    if (msg.contains("serial name") || msg.contains("required for type")) {
        return context.getString(R.string.error_app_outdated)
    }

    return context.getString(fallbackRes)
}
