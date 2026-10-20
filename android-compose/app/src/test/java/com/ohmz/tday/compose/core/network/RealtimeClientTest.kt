package com.ohmz.tday.compose.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeClientTest {

    @Test
    fun `parses backend serialized todo domain events`() {
        val event = parseRealtimeEvent(
            """{"type":"com.ohmz.tday.domain.DomainEvent.TodoUpdated","todo":{"id":"todo-1"}}""",
        )

        assertEquals(RealtimeEvent.TodoChanged, event)
    }

    @Test
    fun `parses backend serialized list domain events`() {
        val event = parseRealtimeEvent(
            """{"type":"com.ohmz.tday.domain.DomainEvent.ListChanged","list":{"id":"list-1"}}""",
        )

        assertEquals(RealtimeEvent.ListChanged, event)
    }

    @Test
    fun `keeps old plain event names compatible`() {
        val event = parseRealtimeEvent("todo.created")

        assertEquals(RealtimeEvent.TodoChanged, event)
    }

    @Test
    fun `preserves unknown events for diagnostics`() {
        val event = parseRealtimeEvent("""{"type":"custom.event"}""")

        assertTrue(event is RealtimeEvent.Unknown)
    }
}
