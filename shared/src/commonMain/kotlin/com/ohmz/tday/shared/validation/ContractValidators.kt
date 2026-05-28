package com.ohmz.tday.shared.validation

import com.ohmz.tday.shared.model.CreateListRequest
import com.ohmz.tday.shared.model.CreateTodoRequest

object ContractValidators {
    fun validateTodoCreate(request: CreateTodoRequest): List<String> {
        val errors = mutableListOf<String>()
        if (request.title.isBlank()) {
            errors += "title cannot be blank"
        }
        if (!request.rrule.isNullOrBlank() && request.due.isNullOrBlank()) {
            errors += "due is required for recurring tasks"
        }
        return errors
    }

    fun validateListCreate(request: CreateListRequest): List<String> {
        return if (request.name.isBlank()) {
            listOf("name cannot be blank")
        } else {
            emptyList()
        }
    }
}
