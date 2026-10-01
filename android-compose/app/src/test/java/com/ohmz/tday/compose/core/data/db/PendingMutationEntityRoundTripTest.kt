package com.ohmz.tday.compose.core.data.db

import com.ohmz.tday.compose.core.data.MutationKind
import com.ohmz.tday.compose.core.data.PendingMutationRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A queued list edit is read back from the Room row after a process death, and it replays
 * whatever that row held. The list fields used to be dropped there, so an offline "Reusable off"
 * replayed as "leave it alone" and the next sync switched it back on.
 */
class PendingMutationEntityRoundTripTest {

    @Test
    fun `a floater list edit keeps reusable and its default priority through the Room row`() {
        val record = listEdit(reusable = false, defaultPriority = HIGH, defaultPriorityChanged = true)

        val restored = record.toEntity().toRecord()

        assertEquals(record, restored)
        assertEquals(false, restored.reusable)
        assertEquals(HIGH, restored.defaultPriority)
        assertEquals(true, restored.defaultPriorityChanged)
    }

    @Test
    fun `a cleared default priority stays distinct from one left alone`() {
        val cleared = listEdit(reusable = null, defaultPriority = null, defaultPriorityChanged = true)
            .toEntity().toRecord()
        val untouched = listEdit(reusable = null, defaultPriority = null, defaultPriorityChanged = null)
            .toEntity().toRecord()

        assertNull(cleared.defaultPriority)
        assertEquals(true, cleared.defaultPriorityChanged)
        assertNull(untouched.defaultPriorityChanged)
    }

    @Test
    fun `a staged marker is still staged after the Room row`() {
        // SyncManager reads its queue from the database, so a marker whose flag the row drops is
        // replayed to the server inside the Undo window it exists to protect.
        val marker = PendingMutationRecord(
            mutationId = "m-staged",
            kind = MutationKind.COMPLETE_TODO,
            targetId = "t-1",
            timestampEpochMs = 2_000L,
            completed = true,
            staged = true,
        )

        val restored = marker.toEntity().toRecord()

        assertEquals(true, restored.staged)
        assertEquals(marker, restored)
    }

    @Test
    fun `an ordinary mutation does not come back staged`() {
        val ordinary = PendingMutationRecord(
            mutationId = "m-plain",
            kind = MutationKind.DELETE_LIST,
            targetId = "l-1",
            timestampEpochMs = 3_000L,
        )

        assertEquals(false, ordinary.toEntity().toRecord().staged)
    }

    private fun listEdit(reusable: Boolean?, defaultPriority: String?, defaultPriorityChanged: Boolean?) =
        PendingMutationRecord(
            mutationId = "m-1",
            kind = MutationKind.UPDATE_FLOATER_LIST,
            targetId = "fl-1",
            timestampEpochMs = 1_000L,
            name = "Packing",
            reusable = reusable,
            defaultPriority = defaultPriority,
            defaultPriorityChanged = defaultPriorityChanged,
        )

    private companion object {
        const val HIGH = "High"
    }
}
