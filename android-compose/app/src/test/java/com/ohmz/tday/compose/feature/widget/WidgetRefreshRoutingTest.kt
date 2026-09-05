package com.ohmz.tday.compose.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the routing half of the widget-kind fix: every repaint the app performs must pair an
 * `appWidgetId` with the widget class that id's OWN receiver declares.
 *
 * Before the fix there were three refreshers and each caller chose which to call. The add-task path
 * chose by a guessed create target rather than by the instance that was tapped, so the one
 * SYNCHRONOUS repaint could be aimed at the wrong kind — leaving the widget actually tapped to the
 * fire-and-forget request from the cache write, which a short-lived widget process can be killed
 * before it paints. Per-list instances had it worse: nothing outside `OfflineCacheManager` called
 * the list refresher at all, so `MainActivity`, `SyncManager`, `BulkTaskRepository` and (worst) the
 * boot receiver left them stale. The scenario below is the one that reproduces both: a home screen
 * holding all three kinds at once, with the add coming from a floater instance.
 */
class WidgetRefreshRoutingTest {

    /** A home screen with every kind placed, across every size receiver. */
    private val placement: Map<Class<*>, IntArray> = mapOf(
        TodayTasksWidgetSmallReceiver::class.java to intArrayOf(11),
        TodayTasksWidgetReceiver::class.java to intArrayOf(12, 13),
        TodayTasksWidgetLargeReceiver::class.java to intArrayOf(14),
        FloaterTasksWidgetSmallReceiver::class.java to intArrayOf(21),
        FloaterTasksWidgetReceiver::class.java to intArrayOf(22),
        FloaterTasksWidgetLargeReceiver::class.java to intArrayOf(23, 24),
        ListTasksWidgetSmallReceiver::class.java to intArrayOf(31),
        ListTasksWidgetReceiver::class.java to intArrayOf(32),
        ListTasksWidgetLargeReceiver::class.java to intArrayOf(33),
    )

    /** The kind each placed id genuinely is, i.e. what `AppWidgetManager` would report. */
    private val kindById: Map<Int, WidgetInstanceKind> =
        placement.entries.flatMap { (receiverClass, ids) ->
            val kind = requireNotNull(WidgetInstanceCatalog.kindForReceiverClassName(receiverClass.name))
            ids.map { it to kind }
        }.toMap()

    private fun plan(firstAppWidgetId: Int? = null): List<WidgetRenderStep> =
        WidgetInstanceCatalog.renderPlan(
            firstAppWidgetId = firstAppWidgetId,
            kindOf = { kindById[it] },
            idsForReceiver = { placement[it] },
        )

    @Test
    fun `no widget id is ever handed to a foreign widget class`() {
        val steps = plan()
        assertEquals(kindById.size, steps.size)
        for (step in steps) {
            assertEquals(
                "appWidgetId ${step.appWidgetId} was routed to ${step.kind}",
                kindById[step.appWidgetId],
                step.kind,
            )
        }
    }

    @Test
    fun `a full repaint covers every placed instance of every kind`() {
        val steps = plan()
        assertEquals(kindById.keys, steps.map { it.appWidgetId }.toSet())
        // The per-list widgets in particular: half the app's refresh call sites used to skip them
        // entirely, so they simply went stale.
        assertEquals(
            setOf(31, 32, 33),
            steps.filter { it.kind == WidgetInstanceKind.LIST }.map { it.appWidgetId }.toSet(),
        )
    }

    @Test
    fun `an add from a floater instance repaints that instance first, as a floater`() {
        val steps = plan(firstAppWidgetId = 23)
        assertEquals(WidgetRenderStep(23, WidgetInstanceKind.FLOATER), steps.first())
        // ...and exactly once: the priority pass must not double-paint it.
        assertEquals(1, steps.count { it.appWidgetId == 23 })
        assertEquals(kindById.size, steps.size)
    }

    @Test
    fun `an add from a per-list instance repaints that instance first, as a list widget`() {
        val steps = plan(firstAppWidgetId = 32)
        assertEquals(WidgetRenderStep(32, WidgetInstanceKind.LIST), steps.first())
        assertEquals(1, steps.count { it.appWidgetId == 32 })
    }

    @Test
    fun `an unresolvable priority id is dropped, never guessed into a kind`() {
        // The instance was removed from the host while its create sheet was open.
        val steps = plan(firstAppWidgetId = 999)
        assertTrue(steps.none { it.appWidgetId == 999 })
        assertEquals(kindById.size, steps.size)
        for (step in steps) {
            assertEquals(kindById[step.appWidgetId], step.kind)
        }
    }

    @Test
    fun `receivers whose ids cannot be read are skipped without affecting the rest`() {
        val steps = WidgetInstanceCatalog.renderPlan(
            firstAppWidgetId = null,
            kindOf = { kindById[it] },
            idsForReceiver = { receiverClass ->
                if (receiverClass == FloaterTasksWidgetReceiver::class.java) null else placement[receiverClass]
            },
        )
        assertTrue(steps.none { it.appWidgetId == 22 })
        for (step in steps) {
            assertEquals(kindById[step.appWidgetId], step.kind)
        }
    }
}
