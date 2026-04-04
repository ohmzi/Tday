package com.ohmz.tday.compose.core.ui

import com.ohmz.tday.compose.core.data.ApiCallException
import kotlinx.serialization.SerializationException

fun Throwable.userFacingMessage(fallback: String = "Something went wrong. Please try again."): String {
    if (this is SerializationException || this.cause is SerializationException) {
        return "This version of the app is out of date. Please update to continue."
    }

    if (this is ApiCallException) {
        return when (statusCode) {
            401 -> "Session expired. Please sign in again."
            403 -> "You don't have permission to do that."
            404 -> "The requested item was not found."
            in 500..599 -> "Server error. Please try again later."
            else -> fallback
        }
    }

    val msg = message.orEmpty().lowercase()
    if (msg.contains("failed to connect") ||
        msg.contains("unable to resolve host") ||
        msg.contains("network is unreachable") ||
        msg.contains("econnrefused") ||
        msg.contains("timed out")
    ) {
        return "Connection error. Check your internet and try again."
    }

    if (msg.contains("serial name") || msg.contains("required for type")) {
        return "This version of the app is out of date. Please update to continue."
    }

    return fallback
}
