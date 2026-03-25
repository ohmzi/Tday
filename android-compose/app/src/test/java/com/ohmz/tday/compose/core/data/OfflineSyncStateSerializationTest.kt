package com.ohmz.tday.compose.core.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSyncStateSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `empty state round-trips`() {
        val state = OfflineSyncState()
        val encoded = json.encodeToString(OfflineSyncState.serializer(), state)
        val decoded = json.decodeFromString(OfflineSyncState.serializer(), encoded)

        assertEquals(state, decoded)
    }

    @Test
    fun `state with todos round-trips`() {
        val state = OfflineSyncState(
            lastSuccessfulSyncEpochMs = 1700000000000L,
            todos = listOf(
                CachedTodoRecord(
                    id = "t1",
                    canonicalId = "t1",
                    title = "Test",
                    dtstartEpochMs = 1700000000000L,
                    dueEpochMs = 1700003600000L,
                    priority = "High",
                    listId = "list-1",
                ),
            ),
        )
        val encoded = json.encodeToString(OfflineSyncState.serializer(), state)
        val decoded = json.decodeFromString(OfflineSyncState.serializer(), encoded)

        assertEquals(state, decoded)
        assertEquals(1, decoded.todos.size)
        assertEquals("Test", decoded.todos[0].title)
    }

    @Test
    fun `state with all entity types round-trips`() {
        val state = OfflineSyncState(
            lastSuccessfulSyncEpochMs = 1700000000000L,
            lastSyncAttemptEpochMs = 1700000100000L,
            todos = listOf(
                CachedTodoRecord(
                    id = "t1",
                    canonicalId = "t1",
                    title = "Todo",
                    dtstartEpochMs = 1700000000000L,
                    dueEpochMs = 1700003600000L,
                ),
            ),
            completedItems = listOf(
                CachedCompletedRecord(
                    id = "c1",
                    title = "Completed",
                    priority = "Low",
                    dueEpochMs = 1700003600000L,
                    completedAtEpochMs = 1700004000000L,
                ),
            ),
            lists = listOf(
                CachedListRecord(id = "l1", name = "Work"),
            ),
            pendingMutations = listOf(
                PendingMutationRecord(
                    mutationId = "m1",
                    kind = MutationKind.CREATE_TODO,
                    timestampEpochMs = 1700000000000L,
                    targetId = "t1",
                    title = "New task",
                ),
            ),
            aiSummaryEnabled = false,
        )

        val encoded = json.encodeToString(OfflineSyncState.serializer(), state)
        val decoded = json.decodeFromString(OfflineSyncState.serializer(), encoded)

        assertEquals(state, decoded)
        assertEquals(1, decoded.todos.size)
        assertEquals(1, decoded.completedItems.size)
        assertEquals(1, decoded.lists.size)
        assertEquals(1, decoded.pendingMutations.size)
        assertEquals(false, decoded.aiSummaryEnabled)
    }

    @Test
    fun `default field values are preserved when absent from JSON`() {
        val minimalJson = """{"lastSuccessfulSyncEpochMs":0}"""
        val decoded = json.decodeFromString(OfflineSyncState.serializer(), minimalJson)

        assertEquals(0L, decoded.lastSuccessfulSyncEpochMs)
        assertEquals(0L, decoded.lastSyncAttemptEpochMs)
        assertTrue(decoded.todos.isEmpty())
        assertTrue(decoded.completedItems.isEmpty())
        assertTrue(decoded.lists.isEmpty())
        assertTrue(decoded.pendingMutations.isEmpty())
        assertTrue(decoded.aiSummaryEnabled)
    }

    @Test
    fun `CachedTodoRecord default values are stable`() {
        val minimalJson = """{"id":"x","canonicalId":"x","title":"T","dtstartEpochMs":0,"dueEpochMs":0}"""
        val record = json.decodeFromString(CachedTodoRecord.serializer(), minimalJson)

        assertEquals("Low", record.priority)
        assertEquals(null, record.description)
        assertEquals(null, record.rrule)
        assertEquals(null, record.instanceDateEpochMs)
        assertEquals(false, record.pinned)
        assertEquals(false, record.completed)
        assertEquals(null, record.listId)
        assertEquals(0L, record.updatedAtEpochMs)
    }

    @Test
    fun `MutationKind all values serialize correctly`() {
        for (kind in MutationKind.entries) {
            val encoded = json.encodeToString(MutationKind.serializer(), kind)
            val decoded = json.decodeFromString(MutationKind.serializer(), encoded)
            assertEquals(kind, decoded)
        }
    }

    @Test
    fun `unknown fields in JSON are ignored`() {
        val extraFieldJson = """{"lastSuccessfulSyncEpochMs":100,"futureField":"hello","todos":[]}"""
        val decoded = json.decodeFromString(OfflineSyncState.serializer(), extraFieldJson)

        assertEquals(100L, decoded.lastSuccessfulSyncEpochMs)
        assertTrue(decoded.todos.isEmpty())
    }
}
