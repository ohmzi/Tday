package com.ohmz.tday.compose.core.data.cache

import com.ohmz.tday.compose.core.data.MutationKind
import com.ohmz.tday.compose.core.data.PendingMutationRecord
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rule that keeps a widget check-off from being silently dropped when it lands while a
 * sync is mid-flight. A sync's saves are built from a snapshot loaded before its network
 * phase, so anything queued during that window is missing from the state it writes.
 */
class ConcurrentMutationPreservationTest {

    private fun mutation(id: String, at: Long) = PendingMutationRecord(
        mutationId = id,
        kind = MutationKind.COMPLETE_TODO,
        targetId = "todo-$id",
        timestampEpochMs = at,
    )

    @Test
    fun preservesMutationQueuedWhileSyncWasInFlight() {
        // Sync loaded [a] and replayed it. Meanwhile the widget queued [b].
        val persisted = listOf(mutation("a", 100), mutation("b", 150))
        val next = emptyList<PendingMutationRecord>()

        val merged = mergeConcurrentlyQueuedMutations(
            persisted = persisted,
            next = next,
            consumedMutationIds = setOf("a"),
        )

        // "a" stays consumed; "b" survives instead of being clobbered.
        assertEquals(listOf("b"), merged.map { it.mutationId })
    }

    @Test
    fun doesNotResurrectMutationsTheSyncLegitimatelyConsumed() {
        val persisted = listOf(mutation("a", 100), mutation("b", 150))

        val merged = mergeConcurrentlyQueuedMutations(
            persisted = persisted,
            next = emptyList(),
            consumedMutationIds = setOf("a", "b"),
        )

        assertEquals(emptyList<String>(), merged.map { it.mutationId })
    }

    @Test
    fun keepsMutationsStillPendingWithoutDuplicatingThem() {
        // "a" failed to replay (connectivity) so it's still in next; "b" arrived mid-sync.
        val persisted = listOf(mutation("a", 100), mutation("b", 150))
        val next = listOf(mutation("a", 100))

        val merged = mergeConcurrentlyQueuedMutations(
            persisted = persisted,
            next = next,
            consumedMutationIds = setOf("a"),
        )

        assertEquals(listOf("a", "b"), merged.map { it.mutationId })
    }

    @Test
    fun ordersPreservedMutationsBackInByTimestamp() {
        // The concurrent mutation is OLDER than one the sync still holds: replay order matters,
        // so it must sort back ahead of it rather than being appended at the end.
        val concurrent = mutation("early", 50)
        val persisted = listOf(mutation("a", 100), concurrent)
        val next = listOf(mutation("a", 100))

        val merged = mergeConcurrentlyQueuedMutations(
            persisted = persisted,
            next = next,
            consumedMutationIds = setOf("a"),
        )

        assertEquals(listOf("early", "a"), merged.map { it.mutationId })
    }

    @Test
    fun returnsNextUntouchedWhenNothingWasQueuedConcurrently() {
        val persisted = listOf(mutation("a", 100))
        val next = listOf(mutation("a", 100))

        val merged = mergeConcurrentlyQueuedMutations(
            persisted = persisted,
            next = next,
            consumedMutationIds = setOf("a"),
        )

        assertEquals(next, merged)
    }
}
