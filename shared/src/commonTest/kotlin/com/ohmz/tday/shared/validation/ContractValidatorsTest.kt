package com.ohmz.tday.shared.validation

import com.ohmz.tday.shared.model.CreateListRequest
import com.ohmz.tday.shared.model.CreateTodoRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContractValidatorsTest {
    @Test
    fun `validateTodoCreate returns all required field errors`() {
        val errors = ContractValidators.validateTodoCreate(
            CreateTodoRequest(
                title = "",
                due = "",
            ),
        )

        assertEquals(
            listOf(
                "title cannot be blank",
                "due is required",
            ),
            errors,
        )
    }

    @Test
    fun `validateTodoCreate returns no errors for valid todo`() {
        val errors = ContractValidators.validateTodoCreate(
            CreateTodoRequest(
                title = "Ship tests",
                description = "Add shared validator coverage",
                due = "2026-03-27T10:00:00Z",
                listID = "list_123",
            ),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `validateListCreate rejects blank list names`() {
        val errors = ContractValidators.validateListCreate(
            CreateListRequest(
                name = "",
                color = "BLUE",
                iconKey = "list",
            ),
        )

        assertEquals(listOf("name cannot be blank"), errors)
    }

    @Test
    fun `validateListCreate accepts non blank list names`() {
        val errors = ContractValidators.validateListCreate(
            CreateListRequest(
                name = "Inbox",
                color = "BLUE",
                iconKey = "inbox",
            ),
        )

        assertTrue(errors.isEmpty())
    }
}
