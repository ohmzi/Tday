package com.ohmz.tday.services

import com.ohmz.tday.domain.DomainEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.ConcurrentHashMap

interface RealtimeService {
    fun channelFor(userId: String): SharedFlow<DomainEvent>
    suspend fun emit(userId: String, event: DomainEvent)
    suspend fun emitToUsers(userIds: Collection<String>, event: DomainEvent)
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

    override suspend fun emitToUsers(userIds: Collection<String>, event: DomainEvent) {
        userIds.toSet().forEach { emit(it, event) }
    }
}

/**
 * Fans a mutation's realtime event out to the acting user plus everyone who
 * collaborates with them through a shared list, and drops those users' cached
 * responses so the refetch the event triggers can't be served stale data.
 *
 * The collaborator set is intentionally coarse (all share-connected users, not
 * just the touched list's members): it avoids threading a listId through every
 * mutation path, and a spurious event only costs a no-op refetch.
 */
class RealtimePublisher(
    private val realtime: RealtimeService,
    private val shareService: ListShareService,
    private val cache: CacheService,
    private val webhookDispatch: WebhookDispatchService,
) {
    suspend fun publishToCollaborators(actorId: String, event: DomainEvent) {
        val recipients = buildSet {
            add(actorId)
            addAll(shareService.collaboratorIdsFor(actorId))
        }
        publishTo(actorId, recipients, event)
    }

    suspend fun publishTo(actorId: String, recipients: Set<String>, event: DomainEvent) {
        recipients.forEach { userId ->
            if (userId != actorId) cache.invalidateForUser(userId)
        }
        realtime.emitToUsers(recipients, event)
        // Fan the same "something changed" signal out to any registered webhooks
        // (fire-and-forget; never blocks the mutation response).
        webhookDispatch.dispatch(recipients, event)
    }
}
