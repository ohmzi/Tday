package com.ohmz.tday.compose.feature.widget

import android.appwidget.AppWidgetManager
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetListType
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The regression tests for "a widget re-renders as the Today widget, then flips back when the app
 * is opened".
 *
 * Three code paths each answered "which widget is this?" differently, and two of them could answer
 * "the scheduled one" for an instance that is not: the add-task path read a `target=` query
 * parameter whose `else` branch was TODAY, and a per-list instance whose selection would not read
 * fell in with the todo-list branch when picking its visuals — painting the scheduled widget's
 * watermark and accent until the selection re-read. These tests pin the rules that make both
 * impossible:
 *
 *  1. a placed instance's kind and feed are a function of the instance ALONE — its provider binding
 *     plus its own persisted selection — never of a global default;
 *  2. "unknown" stays unknown: `feedFor` never falls through to SCHEDULED, and an unresolved
 *     instance renders with neither kind's identity; and
 *  3. where a fallback genuinely remains (no instance at all), it is lossless — the `target=` a
 *     widget stamps on its own link round-trips back to that widget's feed; and
 *  4. the channel that carries the instance to the sheet in the first place — the `appWidgetId` on
 *     the "+" deep link — is actually there and is per-instance. Without it every one of the rules
 *     above is unreachable: `instanceFeed` is always null and the `target=` parameter decides, which
 *     is exactly the pre-fix behavior.
 *
 * The last test covers the composing log line instead, which is what makes the one mechanism these
 * rules CANNOT reach — a Glance session started with the wrong widget class for an id — visible in
 * a logcat capture rather than only arguable from the code.
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
        // Every kind has at least one receiver. These bindings are now the ONLY thing a repaint
        // walks (WidgetRefresher's `updateAll` sweep over WidgetInstanceKind.entries was removed),
        // so a kind with no binding here would be a widget class nothing ever enumerates.
        assertEquals(
            WidgetInstanceKind.entries.toSet(),
            WidgetInstanceCatalog.bindings.map { it.kind }.toSet(),
        )
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
    fun `a per-list instance's own list type answers without laundering through null`() {
        // The paths that already hold a real list type (rendering a configured instance, building
        // its + link) must get the SAME answer as the nullable resolver, or the deep link a
        // floater-list widget writes could disagree with the feed its own instance resolves to.
        for (listType in WidgetListType.entries) {
            assertEquals(
                "feedForListType($listType) disagrees with feedFor(LIST, $listType)",
                WidgetInstanceCatalog.feedFor(WidgetInstanceKind.LIST, listType),
                WidgetInstanceCatalog.feedForListType(listType),
            )
        }
    }

    @Test
    fun `the target a widget stamps on its own link round-trips back to that same feed`() {
        // This is what makes WidgetCreateTarget.resolve's `else -> TODAY` safe. An instance whose
        // placement cannot be resolved at sheet time (removed from the host while the sheet was
        // open, or a per-list selection that would not read) falls back to the parameter, so the
        // parameter every widget writes has to be a lossless encoding of that widget's feed. If
        // this mapping and resolve's parameter branch ever stop being exact inverses, a floater
        // instance silently starts creating scheduled tasks again — which is the original bug.
        assertEquals(
            WidgetCreateTarget.TODAY,
            WidgetCreateTarget.resolve(null, WidgetCreateRoute.targetFor(WidgetFeed.SCHEDULED)),
        )
        assertEquals(
            WidgetCreateTarget.FLOATER,
            WidgetCreateTarget.resolve(null, WidgetCreateRoute.targetFor(WidgetFeed.FLOATER)),
        )
        // Exhaustively, including via the per-list route, where the target is derived from the
        // stored list type rather than written as a literal.
        for (listType in WidgetListType.entries) {
            val feed = WidgetInstanceCatalog.feedForListType(listType)
            assertEquals(
                "a $listType list widget's + would create the wrong kind of task without its instance",
                WidgetCreateTarget.resolve(feed, null),
                WidgetCreateTarget.resolve(null, WidgetCreateRoute.targetFor(feed)),
            )
        }
    }

    @Test
    fun `an unresolved per-list instance renders as neither kind, not as the scheduled one`() {
        // The render half of the same rule. `null` used to share the TODO branch, so an instance
        // whose selection would not read painted the Today sun watermark and the Today accent —
        // the scheduled widget's whole visual identity — and changed back once it re-read.
        val unresolved = listWidgetVisualsFor(null)
        val scheduled = listWidgetVisualsFor(WidgetListType.TODO)
        val floater = listWidgetVisualsFor(WidgetListType.FLOATER)

        assertNull("an unresolved instance must not claim a kind's watermark", unresolved.emptyWatermark)
        assertNull("an unresolved instance must not claim a kind's watermark", unresolved.setupWatermark)
        // Not just "different from Today" — it must borrow no drawable from EITHER kind, or it
        // would still be asserting an identity it does not have.
        for (known in listOf(scheduled, floater)) {
            assertNotEquals(known.addButtonBackground, unresolved.addButtonBackground)
            assertNotEquals(known.addIcon, unresolved.addIcon)
        }
        // ...while a readable selection still gets its own kind's look, unchanged.
        assertEquals(FloaterWidgetVisuals, floater)
        assertNotNull(scheduled.setupWatermark)
    }

    @Test
    fun `create targets keep their own feed shape`() {
        assertTrue(WidgetCreateTarget.TODAY.showScheduleControls)
        assertTrue(!WidgetCreateTarget.FLOATER.showScheduleControls)
    }

    @Test
    fun `every widget's + link carries the tapped instance's own id`() {
        // The load-bearing half of "the instance wins": WidgetCreateTarget.resolve can only prefer
        // the instance over the `target=` parameter if the sheet can SEE which instance was
        // tapped, and the only channel for that is this link. The pre-fix links were the bare
        // literals `tday://todos/create?target=today|floater` with no id at all, so `instanceFeed`
        // was always null and the parameter always decided — which is the whole bug. Drop the id
        // from `deepLink` and every other test here still passes.
        for (feed in WidgetFeed.entries) {
            val params = queryParamsOf(WidgetCreateRoute.deepLink(WidgetCreateRoute.targetFor(feed), APP_WIDGET_ID_A))
            assertEquals(
                "a $feed widget's + link must identify the instance that was tapped",
                APP_WIDGET_ID_A.toString(),
                params[WidgetCreateRoute.PARAM_APP_WIDGET_ID],
            )
            assertEquals(
                WidgetCreateRoute.targetFor(feed),
                params[WidgetCreateRoute.PARAM_TARGET],
            )
        }
    }

    @Test
    fun `two instances of the same kind do not share a + link`() {
        // Glance builds these PendingIntents with request code 0 and FLAG_UPDATE_CURRENT, and
        // Intent.filterEquals compares the DATA URI while ignoring extras — so two instances whose
        // links are byte-identical share one PendingIntent and the second placement silently
        // overwrites the first. Distinctness is what makes the id usable at all.
        val first = WidgetCreateRoute.deepLink(WidgetCreateRoute.targetFor(WidgetFeed.FLOATER), APP_WIDGET_ID_A)
        val second = WidgetCreateRoute.deepLink(WidgetCreateRoute.targetFor(WidgetFeed.FLOATER), APP_WIDGET_ID_B)
        assertNotEquals(first, second)
    }

    @Test
    fun `a synthetic preview id is left off the link rather than stamped as a real instance`() {
        // provideGlance also runs for Glance's own non-placed ids. Stamping one would make
        // WidgetInstanceResolver.kindOf answer for an instance that does not exist; omitting it
        // falls back to the `target=` the widget wrote, which the round-trip test above pins.
        val params = queryParamsOf(
            WidgetCreateRoute.deepLink(
                WidgetCreateRoute.targetFor(WidgetFeed.FLOATER),
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ),
        )
        assertNull(params[WidgetCreateRoute.PARAM_APP_WIDGET_ID])
        assertEquals(WidgetCreateRoute.TARGET_FLOATER, params[WidgetCreateRoute.PARAM_TARGET])
    }

    @Test
    fun `a stale sheet intent cannot make a floater instance create a scheduled task`() {
        // WidgetCreateTaskActivity is `singleTop`, so tapping a second widget's + while the sheet
        // is still alive (swipe home rather than dismiss) re-delivers into the SAME activity. With
        // no onNewIntent override the sheet kept resolving from the FIRST intent, so a Floater "+"
        // showed the Today sheet, with its scheduling controls, and wrote a scheduled todo.
        //
        // The activity now re-resolves on the new intent; this pins the rule that makes that
        // re-resolution safe even if the two intents disagree. The id comes from the FLOATER
        // instance that was actually tapped, the `target=` from the stale TODAY link.
        val tapped = queryParamsOf(
            WidgetCreateRoute.deepLink(WidgetCreateRoute.targetFor(WidgetFeed.FLOATER), APP_WIDGET_ID_A),
        )
        val stale = queryParamsOf(
            WidgetCreateRoute.deepLink(WidgetCreateRoute.targetFor(WidgetFeed.SCHEDULED), APP_WIDGET_ID_B),
        )
        assertEquals(APP_WIDGET_ID_A.toString(), tapped[WidgetCreateRoute.PARAM_APP_WIDGET_ID])
        assertEquals(WidgetCreateRoute.TARGET_TODAY, stale[WidgetCreateRoute.PARAM_TARGET])

        // What WidgetCreateTaskActivity.renderFor does with those two: the instance the id names
        // decides, the parameter is only consulted when there is no instance to ask.
        val instanceFeed = WidgetInstanceCatalog.feedFor(WidgetInstanceKind.FLOATER, null)
        assertEquals(
            "a floater instance's + must not create a scheduled task from a stale today intent",
            WidgetCreateTarget.FLOATER,
            WidgetCreateTarget.resolve(instanceFeed, stale[WidgetCreateRoute.PARAM_TARGET]),
        )
    }

    @Test
    fun `a composing line names the instance and flags a class the platform disagrees with`() {
        // The remaining unfalsified hypothesis for the report is a Glance session started with the
        // wrong widget class for an id (sessions are keyed by appWidgetId alone). Nothing in this
        // app can start one, but the log line has to be able to SAY so from one capture.
        val agreeing = widgetComposeLogLine(
            composingAs = WidgetInstanceKind.FLOATER,
            appWidgetId = APP_WIDGET_ID_A,
            providerKind = WidgetInstanceKind.FLOATER,
            details = SAMPLE_DETAILS,
        )
        assertTrue(agreeing.startsWith("floater[$APP_WIDGET_ID_A]: composing, provider=FLOATER,"))
        assertTrue("an agreeing line must not cry wolf", !agreeing.contains(WIDGET_KIND_MISMATCH_MARKER))
        assertTrue(!isWidgetKindMismatch(WidgetInstanceKind.FLOATER, WidgetInstanceKind.FLOATER))

        val crossKind = widgetComposeLogLine(
            composingAs = WidgetInstanceKind.TODAY,
            appWidgetId = APP_WIDGET_ID_A,
            providerKind = WidgetInstanceKind.FLOATER,
            details = SAMPLE_DETAILS,
        )
        assertTrue("the reported symptom must print itself", crossKind.contains(WIDGET_KIND_MISMATCH_MARKER))
        assertTrue(isWidgetKindMismatch(WidgetInstanceKind.TODAY, WidgetInstanceKind.FLOATER))

        // "the platform would not tell us" is not a disagreement, and must not be reported as one.
        val unknown = widgetComposeLogLine(
            composingAs = WidgetInstanceKind.TODAY,
            appWidgetId = APP_WIDGET_ID_A,
            providerKind = null,
            details = SAMPLE_DETAILS,
        )
        assertTrue(unknown.contains("provider=unknown"))
        assertTrue(!unknown.contains(WIDGET_KIND_MISMATCH_MARKER))
        assertTrue(!isWidgetKindMismatch(WidgetInstanceKind.TODAY, null))
    }

    /**
     * Parses the built link with `java.net.URI` rather than `android.net.Uri` — the production
     * reader uses the latter, which is not available to a plain JVM unit test, so this asserts on
     * the STRING the widget actually stamps on its PendingIntent. `listId` is deliberately never
     * passed here: that parameter goes through `Uri.encode`, which would throw unmocked.
     */
    private fun queryParamsOf(link: String): Map<String, String> =
        URI(link).query.orEmpty()
            .split("&")
            .filter { it.isNotEmpty() }
            .associate { it.substringBefore('=') to it.substringAfter('=') }

    private companion object {
        const val APP_WIDGET_ID_A = 42
        const val APP_WIDGET_ID_B = 43

        /** Stand-in for the per-widget tail of a composing line; its content is not under test. */
        const val SAMPLE_DETAILS = "snapshotNull=false"
    }
}
