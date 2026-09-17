package com.ohmz.tday.compose.core.data.cache

import com.ohmz.tday.compose.core.data.CachedFloaterListRecord
import com.ohmz.tday.compose.core.data.db.toEntity
import com.ohmz.tday.compose.core.data.db.toRecord
import com.ohmz.tday.compose.core.model.FloaterListDto
import com.ohmz.tday.compose.core.model.ListSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The floater list's `reusable` flag is what draws the settings switch and the
 * header's Reset, and it swaps the sheets' own no-op guard against the stored
 * value. It therefore has to survive every hop between the DTO, the domain model,
 * the offline cache and the Room row — dropping it at any one of them makes the
 * switch read OFF on a list that is still reusable, which is exactly the web bug
 * (`FloaterListContainer.editableList` omitted the field). Local Mode has no
 * server to re-ask, so this round-trip IS the local source of truth.
 */
class FloaterListReusableRoundTripTest {

    private val updatedInstant: Instant = Instant.parse("2025-06-15T12:00:00Z")
    private val createdInstant: Instant = Instant.parse("2025-06-10T12:00:00Z")

    private fun makeFloaterListDto(reusable: Boolean) = FloaterListDto(
        id = "fl-1",
        name = "Packing",
        color = null,
        todoCount = 3,
        iconKey = null,
        updatedAt = updatedInstant.toString(),
        createdAt = createdInstant.toString(),
        reusable = reusable,
    )

    private fun makeListSummary(reusable: Boolean) = ListSummary(
        id = "fl-1",
        name = "Packing",
        color = null,
        iconKey = null,
        todoCount = 3,
        updatedAt = updatedInstant,
        createdAt = createdInstant,
        reusable = reusable,
    )

    @Test
    fun `mapFloaterListDto carries reusable off the DTO`() {
        assertTrue(mapFloaterListDto(makeFloaterListDto(reusable = true)).reusable)
        assertFalse(mapFloaterListDto(makeFloaterListDto(reusable = false)).reusable)
    }

    @Test
    fun `floaterListToCache then floaterListFromCache preserves reusable`() {
        val cached = floaterListToCache(makeListSummary(reusable = true))
        assertTrue(cached.reusable)
        assertTrue(floaterListFromCache(cached, todoCountOverride = cached.todoCount).reusable)
    }

    @Test
    fun `floaterListToCache then floaterListFromCache preserves a cleared flag`() {
        val cached = floaterListToCache(makeListSummary(reusable = false))
        assertFalse(cached.reusable)
        assertFalse(floaterListFromCache(cached, todoCountOverride = cached.todoCount).reusable)
    }

    @Test
    fun `CachedFloaterListRecord round-trips reusable through the Room entity`() {
        val record = CachedFloaterListRecord(
            id = "fl-1",
            name = "Packing",
            color = null,
            iconKey = null,
            todoCount = 3,
            updatedAtEpochMs = updatedInstant.toEpochMilli(),
            createdAtEpochMs = createdInstant.toEpochMilli(),
            reusable = true,
        )

        assertTrue(record.toEntity().reusable)
        assertTrue(record.toEntity().toRecord().reusable)
        assertEquals(record, record.toEntity().toRecord())
    }

    @Test
    fun `a pre-v11 cache row decodes as not reusable`() {
        // Migration10To11 adds the column with DEFAULT 0, so an old row's entity
        // must read back as false rather than as an absent flag.
        val legacyRow = CachedFloaterListRecord(
            id = "fl-1",
            name = "Packing",
            color = null,
            iconKey = null,
            todoCount = 3,
            updatedAtEpochMs = updatedInstant.toEpochMilli(),
            createdAtEpochMs = createdInstant.toEpochMilli(),
        )

        assertFalse(legacyRow.reusable)
        assertFalse(legacyRow.toEntity().toRecord().reusable)
    }
}
