package com.ohmz.tday.shared.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TodoModelsTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun `todo create request serializes with stable wire shape`() {
        val payload = CreateTodoRequest(
            title = "Review sprint",
            description = "Focus on Kotlin migration",
            priority = "High",
            dtstart = "2026-03-26T10:00:00Z",
            due = "2026-03-26T11:00:00Z",
            rrule = null,
            listID = "list_1",
        )

        val decoded = json.decodeFromString<CreateTodoRequest>(
            json.encodeToString(CreateTodoRequest.serializer(), payload),
        )

        assertEquals(payload, decoded)
    }

    @Test
    fun `todo dto accepts backend and mobile fields`() {
        val raw = """
            {
              "id": "todo_1",
              "title": "Ship shared models",
              "priority": "Medium",
              "dtstart": "2026-03-26T10:00:00Z",
              "due": "2026-03-26T11:00:00Z",
              "durationMinutes": 60,
              "timeZone": "UTC",
              "completed": false,
              "pinned": true,
              "order": 4,
              "listID": "list_1",
              "userID": "user_1"
            }
        """.trimIndent()

        val decoded = json.decodeFromString<TodoDto>(raw)

        assertEquals("todo_1", decoded.id)
        assertEquals(60, decoded.durationMinutes)
        assertEquals("UTC", decoded.timeZone)
        assertNotNull(decoded.order)
    }
}
