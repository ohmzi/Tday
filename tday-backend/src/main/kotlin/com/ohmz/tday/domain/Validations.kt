package com.ohmz.tday.domain

import com.ohmz.tday.models.request.*
import io.konform.validation.Validation
import io.konform.validation.constraints.maxLength
import io.konform.validation.constraints.minLength
import io.konform.validation.constraints.pattern

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

val validateCreateNote = Validation<NoteCreateRequest> {
    NoteCreateRequest::name {
        minLength(1) hint "Name is required"
        maxLength(500) hint "Name too long"
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
