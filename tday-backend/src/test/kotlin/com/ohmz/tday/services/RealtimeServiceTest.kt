package com.ohmz.tday.services

import com.ohmz.tday.domain.DomainEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RealtimeServiceTest {

    @Test
    fun `emitToUsers delivers the event to every subscribed user`() = runBlocking {
        val service = RealtimeServiceImpl()
        val event = DomainEvent.TodoChanged(listId = "list_1")

        val userA = async { service.channelFor("user_a").first() }
        val userB = async { service.channelFor("user_b").first() }
        yield()

        service.emitToUsers(listOf("user_a", "user_b", "user_a"), event)

        assertEquals(event, userA.await())
        assertEquals(event, userB.await())
    }

    @Test
    fun `emitToUsers ignores users without an open channel`() = runBlocking {
        val service = RealtimeServiceImpl()
        val event = DomainEvent.ListChanged(listId = "list_1")

        val userA = async { service.channelFor("user_a").first() }
        yield()

        // user_offline never subscribed; emitting must not throw or block.
        service.emitToUsers(listOf("user_a", "user_offline"), event)

        assertEquals(event, userA.await())
    }
}
