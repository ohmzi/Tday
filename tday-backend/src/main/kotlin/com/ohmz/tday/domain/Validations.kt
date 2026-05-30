package com.ohmz.tday.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ohmz.tday.models.request.*
import io.konform.validation.Validation
import io.konform.validation.constraints.maxLength
import io.konform.validation.constraints.minLength
import io.konform.validation.constraints.pattern

fun <T> Validation<T>.validateOrFail(value: T): Either<AppError, T> {
    val result = this(value)
    return if (result.isValid) value.right()
    else Either.Left(AppError.BadRequest(
        result.errors.joinToString("; ") { it.message }
    ))
}

inline fun <reified T : Enum<T>> validateRequiredEnumValue(value: String, field: String): Either<AppError, String> {
    val normalized = value.trim()
    return if (enumValues<T>().any { it.name == normalized }) {
        normalized.right()
    } else {
        AppError.BadRequest("$field is invalid", field).left()
    }
}

inline fun <reified T : Enum<T>> validateOptionalEnumValue(value: String?, field: String): Either<AppError, String?> {
    val normalized = value?.trim() ?: return null.right()
    return if (enumValues<T>().any { it.name == normalized }) {
        normalized.right()
    } else {
        AppError.BadRequest("$field is invalid", field).left()
    }
}

fun validateOptionalValue(value: String?, field: String, allowedValues: Set<String>): Either<AppError, String?> {
    val normalized = value?.trim() ?: return null.right()
    return if (normalized in allowedValues) {
        normalized.right()
    } else {
        AppError.BadRequest("$field is invalid", field).left()
    }
}

val validateCreateTodo = Validation<TodoCreateRequest> {
    TodoCreateRequest::title {
        minLength(1) hint "Title is required"
        maxLength(500) hint "Title too long"
    }
}

val validatePatchTodo = Validation<TodoPatchRequest> {
    TodoPatchRequest::id {
        minLength(1) hint "Todo id is required"
    }
}

val validateCreateFloater = Validation<FloaterCreateRequest> {
    FloaterCreateRequest::title {
        minLength(1) hint "Title is required"
        maxLength(500) hint "Title too long"
    }
}

val validateCreateList = Validation<ListCreateRequest> {
    ListCreateRequest::name {
        minLength(1) hint "Name is required"
        maxLength(255) hint "Name too long"
    }
}

val validatePatchList = Validation<ListPatchRequest> {
    ListPatchRequest::id {
        minLength(1) hint "List id is required"
    }
}

val validateCreateFloaterList = Validation<FloaterListCreateRequest> {
    FloaterListCreateRequest::name {
        minLength(1) hint "Name is required"
        maxLength(255) hint "Name too long"
    }
}

val validatePatchFloaterList = Validation<FloaterListPatchRequest> {
    FloaterListPatchRequest::id {
        minLength(1) hint "List id is required"
    }
}

val validateRegister = Validation<RegisterRequest> {
    RegisterRequest::fname {
        minLength(2) hint "First name must be at least two characters"
    }
    RegisterRequest::email {
        pattern(".+@.+") hint "Email is incorrect"
    }
    RegisterRequest::password {
        minLength(8) hint "Password must be at least 8 characters"
    }
}
