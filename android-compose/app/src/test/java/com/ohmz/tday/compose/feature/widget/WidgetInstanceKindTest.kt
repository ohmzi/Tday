package com.ohmz.tday.compose.feature.widget

import com.ohmz.tday.compose.feature.widget.snapshot.WidgetListType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The regression tests for "the Floater widget re-renders as the Today widget after using its own
 * +, then flips back when the app is opened".
 *
 * The defect was that three code paths each answered "which widget is this?" differently, and the
 * add-task path answered it from a `target=` query parameter whose `else` branch was TODAY — so a
 * missing, stale or unreadable per-instance answer silently became "the scheduled widget". These
 * tests pin the two rules that make that impossible:
 *
 *  1. a placed instance's kind and feed are a function of the instance ALONE — its provider binding
 *     plus its own persisted selection — never of a global default; and
 *  2. "unknown" stays unknown. No input combination is allowed to fall through to SCHEDULED.
 */
class WidgetInstanceKindTest {

    @Test
    fun `every declared receiver maps to the kind of widget it hosts`() {
        assertEquals(
            WidgetInstanceKind.TODAY,
            WidgetInstanceCatalog.kindForReceiverClassName(TodayTasksWidgetSmallReceiver::class.java.name),
        )
        assertEquals(
            WidgetInstanceKind.TODAY,
            WidgetInstanceCatalog.kindForReceiverClassName(TodayTasksWidgetReceiver::class.java.name),
        )
        assertEquals(
            WidgetInstanceKind.TODAY,
            WidgetInstanceCatalog.kindForReceiverClassName(TodayTasksWidgetLargeReceiver::class.java.name),
        )
        assertEquals(
            WidgetInstanceKind.FLOATER,
            WidgetInstanceCatalog.kindForReceiverClassName(FloaterTasksWidgetSmallReceiver::class.java.name),
        )
        assertEquals(
            WidgetInstanceKind.FLOATER,
            WidgetInstanceCatalog.kindForReceiverClassName(FloaterTasksWidgetReceiver::class.java.name),
        )
        assertEquals(
            WidgetInstanceKind.FLOATER,
            WidgetInstanceCatalog.kindForReceiverClassName(FloaterTasksWidgetLargeReceiver::class.java.name),
        )
        assertEquals(
            WidgetInstanceKind.LIST,
            WidgetInstanceCatalog.kindForReceiverClassName(ListTasksWidgetSmallReceiver::class.java.name),
        )
        assertEquals(
            WidgetInstanceKind.LIST,
            WidgetInstanceCatalog.kindForReceiverClassName(ListTasksWidgetReceiver::class.java.name),
        )
        assertEquals(
            WidgetInstanceKind.LIST,
            WidgetInstanceCatalog.kindForReceiverClassName(ListTasksWidgetLargeReceiver::class.java.name),
        )
        assertEquals(9, WidgetInstanceCatalog.bindings.size)
    }

    @Test
    fun `an unknown provider resolves to no kind rather than to today`() {
        assertNull(WidgetInstanceCatalog.kindForReceiverClassName(null))
        assertNull(WidgetInstanceCatalog.kindForReceiverClassName(""))
        assertNull(WidgetInstanceCatalog.kindForReceiverClassName("com.example.SomeOtherAppWidget"))
        // A receiver we used to ship, on an instance that outlived it.
        assertNull(WidgetInstanceCatalog.kindForReceiverClassName("com.ohmz.tday.compose.feature.widget.GoneReceiver"))
    }

    @Test
    fun `the fixed widgets' feeds come from their own class, not from any preference`() {
        assertEquals(WidgetFeed.SCHEDULED, WidgetInstanceCatalog.feedFor(WidgetInstanceKind.TODAY, null))
        assertEquals(WidgetFeed.FLOATER, WidgetInstanceCatalog.feedFor(WidgetInstanceKind.FLOATER, null))
        // A stray list type must not be able to move a fixed widget's feed either.
        assertEquals(
            WidgetFeed.SCHEDULED,
            WidgetInstanceCatalog.feedFor(WidgetInstanceKind.TODAY, WidgetListType.FLOATER),
        )
        assertEquals(
            WidgetFeed.FLOATER,
            WidgetInstanceCatalog.feedFor(WidgetInstanceKind.FLOATER, WidgetListType.TODO),
        )
    }

    @Test
    fun `a per-list instance's feed follows its own stored list type`() {
        assertEquals(
            WidgetFeed.SCHEDULED,
            WidgetInstanceCatalog.feedFor(WidgetInstanceKind.LIST, WidgetListType.TODO),
        )
        assertEquals(
            WidgetFeed.FLOATER,
            WidgetInstanceCatalog.feedFor(WidgetInstanceKind.LIST, WidgetListType.FLOATER),
        )
    }

    @Test
    fun `a per-list instance with no readable selection fails closed instead of becoming scheduled`() {
        // This is the exact shape of the bug: an unreadable per-instance selection used to make a
        // floater-list widget render, and route its +, as the scheduled widget.
        assertNull(WidgetInstanceCatalog.feedFor(WidgetInstanceKind.LIST, null))
        assertNull(WidgetInstanceCatalog.feedFor(null, null))
        assertNull(WidgetInstanceCatalog.feedFor(null, WidgetListType.FLOATER))
        assertNull(WidgetInstanceCatalog.feedFor(null, WidgetListType.TODO))
    }

    @Test
    fun `feed resolution is total and depends on nothing but the instance`() {
        // Exhaustive over every input the resolver accepts: the answer is fully determined by
        // (kind, that instance's own list type). There is no third input — in particular no
        // default-home-screen preference — that could move any of these.
        val kinds = WidgetInstanceKind.entries + listOf(null)
        val listTypes = WidgetListType.entries + listOf(null)
        for (kind in kinds) {
            for (listType in listTypes) {
                val first = WidgetInstanceCatalog.feedFor(kind, listType)
                val second = WidgetInstanceCatalog.feedFor(kind, listType)
                assertEquals("feedFor($kind, $listType) is not stable", first, second)
                val resolvable = kind != null && (kind != WidgetInstanceKind.LIST || listType != null)
                if (resolvable) {
                    assertNotNull("feedFor($kind, $listType) should resolve", first)
                } else {
                    assertNull("feedFor($kind, $listType) must stay unknown", first)
                }
            }
        }
    }

    @Test
    fun `the tapped instance decides the create sheet, not the deep link's target parameter`() {
        // The add-task path used to read ONLY the parameter, so a floater instance whose parameter
        // was missing or stale created a scheduled task and repainted the scheduled widget. The
        // instance now wins outright, including over a parameter that says otherwise.
        assertEquals(WidgetCreateTarget.FLOATER, WidgetCreateTarget.resolve(WidgetFeed.FLOATER, null))
        assertEquals(WidgetCreateTarget.FLOATER, WidgetCreateTarget.resolve(WidgetFeed.FLOATER, "today"))
        assertEquals(WidgetCreateTarget.FLOATER, WidgetCreateTarget.resolve(WidgetFeed.FLOATER, ""))
        assertEquals(WidgetCreateTarget.TODAY, WidgetCreateTarget.resolve(WidgetFeed.SCHEDULED, "floater"))
        assertEquals(WidgetCreateTarget.TODAY, WidgetCreateTarget.resolve(WidgetFeed.SCHEDULED, null))
    }

    @Test
    fun `the target parameter still drives the entry points that have no widget instance`() {
        // The Quick Settings tile, the launcher shortcut and the share sheet all reach the same
        // sheet with no appWidgetId at all, so they keep the parameter-driven behavior.
        assertEquals(WidgetCreateTarget.FLOATER, WidgetCreateTarget.resolve(null, "floater"))
        assertEquals(WidgetCreateTarget.FLOATER, WidgetCreateTarget.resolve(null, "FLOATER"))
        assertEquals(WidgetCreateTarget.TODAY, WidgetCreateTarget.resolve(null, "today"))
        assertEquals(WidgetCreateTarget.TODAY, WidgetCreateTarget.resolve(null, null))
    }

    @Test
    fun `create targets keep their own feed shape`() {
        assertTrue(WidgetCreateTarget.TODAY.showScheduleControls)
        assertTrue(!WidgetCreateTarget.FLOATER.showScheduleControls)
    }
}
