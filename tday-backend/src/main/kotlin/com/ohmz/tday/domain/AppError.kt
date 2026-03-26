package com.ohmz.tday.domain

sealed interface AppError {
    val message: String

    data class NotFound(override val message: String) : AppError
    data class BadRequest(override val message: String, val field: String? = null) : AppError
    data class Unauthorized(override val message: String = "Authentication required") : AppError
    data class Forbidden(override val message: String = "Access denied") : AppError
    data class Conflict(override val message: String) : AppError
    data class Internal(override val message: String, val cause: Throwable? = null) : AppError
}
