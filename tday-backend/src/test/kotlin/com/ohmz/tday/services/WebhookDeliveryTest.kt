package com.ohmz.tday.services

import com.ohmz.tday.domain.DomainEvent
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebhookDeliveryTest {
    @Test
    fun `signature matches the known HMAC-SHA256 vector`() {
        // RFC-style test vector: HMAC-SHA256(key="key", msg="The quick brown fox…").
        val sig = webhookSignature("key", "The quick brown fox jumps over the lazy dog")
        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8", sig)
    }

    @Test
    fun `delivery succeeds on the first attempt without backoff`() = runBlocking {
        var calls = 0
        val result = deliverWithRetry(maxAttempts = 3, baseBackoffMs = 0) { calls++; 200 }
        assertTrue(result.delivered)
        assertEquals(200, result.lastStatus)
        assertEquals(1, calls)
    }

    @Test
    fun `delivery retries past a server error and then succeeds`() = runBlocking {
        val statuses = listOf(500, 502, 200)
        var index = 0
        val result = deliverWithRetry(maxAttempts = 3, baseBackoffMs = 0) { statuses[index++] }
        assertTrue(result.delivered)
        assertEquals(200, result.lastStatus)
        assertEquals(3, result.attempts)
    }

    @Test
    fun `delivery gives up after max attempts`() = runBlocking {
        var calls = 0
        val result = deliverWithRetry(maxAttempts = 3, baseBackoffMs = 0) { calls++; 503 }
        assertFalse(result.delivered)
        assertEquals(503, result.lastStatus)
        assertEquals(3, calls)
    }

    @Test
    fun `a thrown request (null status) counts as a failed attempt`() = runBlocking {
        var calls = 0
        val result = deliverWithRetry(maxAttempts = 2, baseBackoffMs = 0) { calls++; null }
        assertFalse(result.delivered)
        assertEquals(null, result.lastStatus)
        assertEquals(2, calls)
    }

    @Test
    fun `every DomainEvent maps to its wire type and list id`() {
        assertEquals("todo.changed", DomainEvent.TodoChanged("l1").wireType())
        assertEquals("l1", DomainEvent.TodoChanged("l1").listId())
        assertEquals("floater.changed", DomainEvent.FloaterChanged().wireType())
        assertEquals("list.changed", DomainEvent.ListChanged("l2").wireType())
        assertEquals("floaterList.changed", DomainEvent.FloaterListChanged().wireType())
        assertEquals("list.members", DomainEvent.MembersChanged("l3").wireType())
        assertEquals("completed.changed", DomainEvent.CompletedChanged().wireType())
        assertEquals(null, DomainEvent.FloaterChanged().listId())
        // The advertised event-type list must cover every variant.
        assertEquals(6, WEBHOOK_EVENT_TYPES.size)
        assertTrue(WEBHOOK_EVENT_TYPES.containsAll(
            listOf("todo.changed", "floater.changed", "list.changed", "floaterList.changed", "list.members", "completed.changed"),
        ))
    }

    @Test
    fun `parseEvents splits a filter and treats blank as empty`() {
        assertEquals(listOf("todo.changed", "list.changed"), parseEvents("todo.changed, list.changed"))
        assertEquals(emptyList(), parseEvents(null))
        assertEquals(emptyList(), parseEvents(""))
    }
}
