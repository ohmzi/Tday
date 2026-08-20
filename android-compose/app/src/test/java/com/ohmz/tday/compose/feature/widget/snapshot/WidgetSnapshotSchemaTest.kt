package com.ohmz.tday.compose.feature.widget.snapshot

import org.junit.Assert.assertEquals
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
    fun `decodes a payload missing every optional field using defaults`() {
        val minimal = """
            {"generatedAtEpochMs":1000,"status":"EMPTY","taskCount":0}
        """.trimIndent()

        val decoded = WidgetSnapshotJson.decodeFromString(WidgetSnapshot.serializer(), minimal)

        assertEquals(WidgetSnapshotStatus.EMPTY, decoded.status)
        assertEquals(0, decoded.taskCount)
        assertEquals(null, decoded.dayStartEpochMs)
        assertEquals(null, decoded.dayEndEpochMs)
        assertTrue(decoded.rows.isEmpty())
    }
}
