package com.ohmz.tday.shared.validation

import com.ohmz.tday.shared.model.CreateFloaterListRequest
import com.ohmz.tday.shared.model.CreateFloaterRequest
import com.ohmz.tday.shared.model.CreateListRequest
import com.ohmz.tday.shared.model.CreateTodoRequest

/**
 * Client-side mirror of the backend's create-request rules
 * ([com.ohmz.tday.domain] Validations). Keep the limits below in sync with the
 * server so a request that passes here isn't rejected there.
 */
object ContractValidators {
    private const val MAX_TITLE_LENGTH = 500
    private const val MAX_NAME_LENGTH = 255

    fun validateTodoCreate(request: CreateTodoRequest): List<String> {
        val errors = mutableListOf<String>()
        if (request.title.isBlank()) {
            errors += "title cannot be blank"
        } else if (request.title.length > MAX_TITLE_LENGTH) {
            errors += "title too long"
        }
        // Scheduled todos always need a due (floaters use validateFloaterCreate).
        if (request.due.isBlank()) {
            errors += "due is required"
        }
        return errors
    }

    fun validateFloaterCreate(request: CreateFloaterRequest): List<String> {
        val errors = mutableListOf<String>()
        if (request.title.isBlank()) {
            errors += "title cannot be blank"
        } else if (request.title.length > MAX_TITLE_LENGTH) {
            errors += "title too long"
        }
        return errors
    }

    fun validateListCreate(request: CreateListRequest): List<String> = validateName(request.name)

    fun validateFloaterListCreate(request: CreateFloaterListRequest): List<String> =
        validateName(request.name)

    private fun validateName(name: String): List<String> {
        val errors = mutableListOf<String>()
        if (name.isBlank()) {
            errors += "name cannot be blank"
        } else if (name.length > MAX_NAME_LENGTH) {
            errors += "name too long"
        }
        return errors
    }
}
