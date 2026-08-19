package com.ohmz.tday.compose.feature.widget.snapshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSnapshotSchemaTest {

    @Test
    fun `round trips through the exact json config the store uses`() {
        val original = WidgetSnapshot(
            generatedAtEpochMs = 1_000L,
            status = WidgetSnapshotStatus.TASKS,
            taskCount = 2,
            dayStartEpochMs = 500L,
            dayEndEpochMs = 86_500L,
            rows = listOf(
                WidgetSnapshotRow(
                    id = "a",
                    key = "a".hashCode().toLong(),
                    title = "Alpha",
                    priorityRing = WidgetPriorityRing.HIGH,
                    dueEpochMs = 600L,
                    description = "notes",
                ),
                WidgetSnapshotRow(
                    id = "b",
                    key = "b".hashCode().toLong(),
                    title = "Beta",
                    priorityRing = WidgetPriorityRing.LOW,
                ),
            ),
        )

        val encoded = WidgetSnapshotJson.encodeToString(WidgetSnapshot.serializer(), original)
        val decoded = WidgetSnapshotJson.decodeFromString(WidgetSnapshot.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `decodes a v1 payload missing every optional field using defaults`() {
        val minimal = """
            {"generatedAtEpochMs":1000,"status":"EMPTY","taskCount":0}
        """.trimIndent()

        val decoded = WidgetSnapshotJson.decodeFromString(WidgetSnapshot.serializer(), minimal)

        assertEquals(WIDGET_SNAPSHOT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(WidgetSnapshotStatus.EMPTY, decoded.status)
        assertEquals(0, decoded.taskCount)
        assertEquals(null, decoded.dayStartEpochMs)
        assertEquals(null, decoded.dayEndEpochMs)
        assertTrue(decoded.rows.isEmpty())
    }

    @Test
    fun `an unknown key does not throw`() {
        val fromTheFuture = """
            {
              "generatedAtEpochMs": 1000,
              "status": "TASKS",
              "taskCount": 1,
              "somethingAddedLater": "ignored",
              "rows": [
                {"id":"a","key":1,"title":"A","priorityRing":"LOW","fromTheFuture":true}
              ]
            }
        """.trimIndent()

        val decoded = WidgetSnapshotJson.decodeFromString(WidgetSnapshot.serializer(), fromTheFuture)

        assertEquals(1, decoded.rows.size)
        assertEquals("a", decoded.rows.single().id)
    }

    @Test
    fun `a snapshot at the current schema version is supported`() {
        val snapshot = WidgetSnapshot(
            schemaVersion = WIDGET_SNAPSHOT_SCHEMA_VERSION,
            generatedAtEpochMs = 0L,
            status = WidgetSnapshotStatus.EMPTY,
            taskCount = 0,
        )

        assertTrue(snapshot.isSupported())
    }

    @Test
    fun `a snapshot from a newer schema version is not supported`() {
        val fromTheFuture = WidgetSnapshot(
            schemaVersion = WIDGET_SNAPSHOT_SCHEMA_VERSION + 1,
            generatedAtEpochMs = 0L,
            status = WidgetSnapshotStatus.EMPTY,
            taskCount = 0,
        )

        assertFalse(fromTheFuture.isSupported())
    }

    @Test
    fun `a snapshot from an older schema version is still supported`() {
        val fromThePast = WidgetSnapshot(
            schemaVersion = 0,
            generatedAtEpochMs = 0L,
            status = WidgetSnapshotStatus.EMPTY,
            taskCount = 0,
        )

        assertTrue(fromThePast.isSupported())
    }
}
