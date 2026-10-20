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
                    overdue = true,
                ),
                WidgetSnapshotRow(
                    id = "b",
                    key = "b".hashCode().toLong(),
                    title = "Beta",
                    priorityRing = WidgetPriorityRing.LOW,
                ),
                WidgetSnapshotRow(
                    id = "c",
                    key = "c".hashCode().toLong(),
                    title = "Gamma",
                    priorityRing = WidgetPriorityRing.LOWEST,
                ),
            ),
            upcomingDays = listOf(
                WidgetSnapshotDay(
                    dayStartEpochMs = 86_500L,
                    dayEndEpochMs = 172_900L,
                    taskCount = 0,
                    overdueCount = 1,
                    overdueRows = listOf(
                        WidgetSnapshotRow(id = "d", key = "d".hashCode().toLong(), title = "Delta", priorityRing = WidgetPriorityRing.MEDIUM),
                    ),
                ),
            ),
            completedCount = 3,
            overdueCount = 1,
            overdueRows = listOf(
                WidgetSnapshotRow(id = "d", key = "d".hashCode().toLong(), title = "Delta", priorityRing = WidgetPriorityRing.MEDIUM),
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
        assertEquals(0, decoded.completedCount)
        assertEquals(0, decoded.overdueCount)
        assertTrue(decoded.overdueRows.isEmpty())
    }

    @Test
    fun `a row missing overdue decodes to false`() {
        val minimal = """
            {"generatedAtEpochMs":1000,"status":"TASKS","taskCount":1,"rows":[
                {"id":"a","key":1,"title":"Alpha","priorityRing":"LOW"}
            ]}
        """.trimIndent()

        val decoded = WidgetSnapshotJson.decodeFromString(WidgetSnapshot.serializer(), minimal)

        assertEquals(false, decoded.rows.single().overdue)
    }
}
