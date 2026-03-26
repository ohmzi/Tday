package com.ohmz.tday.services

import com.ohmz.tday.domain.DomainEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.ConcurrentHashMap

interface RealtimeService {
    fun channelFor(userId: String): SharedFlow<DomainEvent>
    suspend fun emit(userId: String, event: DomainEvent)
}

class RealtimeServiceImpl : RealtimeService {
    private val userChannels = ConcurrentHashMap<String, MutableSharedFlow<DomainEvent>>()

    override fun channelFor(userId: String): SharedFlow<DomainEvent> =
        userChannels.getOrPut(userId) {
            MutableSharedFlow(replay = 0, extraBufferCapacity = 64)
        }

    override suspend fun emit(userId: String, event: DomainEvent) {
        userChannels[userId]?.emit(event)
    }
}
