package com.ohmz.tday.shared.validation

import com.ohmz.tday.shared.model.CreateFloaterListRequest
import com.ohmz.tday.shared.model.CreateFloaterRequest
import com.ohmz.tday.shared.model.CreateListRequest
import com.ohmz.tday.shared.model.CreateTodoRequest

object ContractValidators {
    fun validateTodoCreate(request: CreateTodoRequest): List<String> {
        val errors = mutableListOf<String>()
        if (request.title.isBlank()) {
            errors += "title cannot be blank"
        }
        if (request.due.isBlank()) {
            errors += "due is required"
        } else if (!request.rrule.isNullOrBlank() && request.due.isBlank()) {
            errors += "due is required for recurring tasks"
        }
        return errors
    }

    fun validateFloaterCreate(request: CreateFloaterRequest): List<String> {
        return if (request.title.isBlank()) {
            listOf("title cannot be blank")
        } else {
            emptyList()
        }
    }

    fun validateListCreate(request: CreateListRequest): List<String> {
        return if (request.name.isBlank()) {
            listOf("name cannot be blank")
        } else {
            emptyList()
        }
    }

    fun validateFloaterListCreate(request: CreateFloaterListRequest): List<String> {
        return if (request.name.isBlank()) {
            listOf("name cannot be blank")
        } else {
            emptyList()
        }
    }
}
