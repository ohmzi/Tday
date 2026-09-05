package com.ohmz.tday.compose.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetListType

/**
 * Which of the three widget classes a PLACED instance is, derived from the receiver the platform
 * actually bound that `appWidgetId` to.
 *
 * This exists because the app used to answer "which widget is this?" three different ways — the
 * Glance class a refresher happened to construct, the per-instance selection in
 * [WidgetListSelectionStore], and a `target=` query parameter on the create deep link that
 * silently defaulted to "today". The tap on a widget's "+" threw the instance identity away and
 * the activity guessed it back, so which task the "+" created, and which widget got the one
 * synchronous repaint afterwards, both came from the guess rather than from the placement. There
 * is now exactly one source of truth, and it is the placement itself.
 */
internal enum class WidgetInstanceKind { TODAY, FLOATER, LIST }

/**
 * Which task feed a placed instance shows — the only thing outside the render path ever needs to
 * know about a widget's kind (which tasks its "+" creates, which shape its rows take).
 *
 * Deliberately NULLABLE everywhere it is produced: "we could not work out what this instance is"
 * must stay distinguishable from "this instance is the scheduled one". Collapsing the two is the
 * bug this type exists to prevent.
 */
internal enum class WidgetFeed { SCHEDULED, FLOATER }

/** One placed-receiver -> widget-kind binding; the catalog below is the full set of nine. */
internal data class WidgetReceiverBinding(
    val kind: WidgetInstanceKind,
    val receiverClass: Class<*>,
)

/**
 * The single mapping from "a receiver this app declares in its manifest" to "the kind of widget
 * instances bound to it are". Everything that needs to resolve, refresh, or route by widget kind
 * goes through here, so no call site can invent a fourth answer.
 *
 * Pure and framework-free on purpose (`WidgetInstanceKindTest` covers it as a plain JVM test):
 * the only Android call in the whole resolution is [WidgetInstanceResolver]'s single
 * `getAppWidgetInfo` lookup.
 */
internal object WidgetInstanceCatalog {

    /**
     * Every receiver in `AndroidManifest.xml`, in the order a full repaint walks them. Keyed by
     * `Class` rather than by name so a receiver rename is a compile error here instead of a
     * silently unresolvable instance at runtime.
     */
    val bindings: List<WidgetReceiverBinding> = listOf(
        WidgetReceiverBinding(WidgetInstanceKind.TODAY, TodayTasksWidgetSmallReceiver::class.java),
        WidgetReceiverBinding(WidgetInstanceKind.TODAY, TodayTasksWidgetReceiver::class.java),
        WidgetReceiverBinding(WidgetInstanceKind.TODAY, TodayTasksWidgetLargeReceiver::class.java),
        WidgetReceiverBinding(WidgetInstanceKind.FLOATER, FloaterTasksWidgetSmallReceiver::class.java),
        WidgetReceiverBinding(WidgetInstanceKind.FLOATER, FloaterTasksWidgetReceiver::class.java),
        WidgetReceiverBinding(WidgetInstanceKind.FLOATER, FloaterTasksWidgetLargeReceiver::class.java),
        WidgetReceiverBinding(WidgetInstanceKind.LIST, ListTasksWidgetSmallReceiver::class.java),
        WidgetReceiverBinding(WidgetInstanceKind.LIST, ListTasksWidgetReceiver::class.java),
        WidgetReceiverBinding(WidgetInstanceKind.LIST, ListTasksWidgetLargeReceiver::class.java),
    )

    private val kindByReceiverClassName: Map<String, WidgetInstanceKind> =
        bindings.associate { it.receiverClass.name to it.kind }

    /**
     * `null` for anything this app did not declare — a removed instance, another app's widget, a
     * provider we no longer ship. Callers must treat that as "unknown", never as a kind.
     */
    fun kindForReceiverClassName(className: String?): WidgetInstanceKind? =
        className?.let { kindByReceiverClassName[it] }

    /**
     * The feed a placed instance shows.
     *
     * [listType] is only consulted for [WidgetInstanceKind.LIST], and a LIST instance whose
     * selection is missing resolves to `null` rather than to [WidgetFeed.SCHEDULED]. That is the
     * whole point: a per-list widget on a floater list must never be treated as the scheduled
     * widget just because its configuration could not be read at that instant. What the caller
     * does with that `null` is the caller's business — [WidgetCreateTarget.resolve] falls back to
     * the `target=` the instance itself stamped on its deep link (see [WidgetCreateRoute.targetFor],
     * which keeps that fallback lossless), and [ListTasksWidget] renders a kind-neutral setup state.
     * Neither silently promotes the unknown to "scheduled".
     *
     * Nothing here reads the "default home screen" preference, or any other global default — the
     * feed of a placed instance is a function of that instance alone.
     */
    fun feedFor(kind: WidgetInstanceKind?, listType: WidgetListType?): WidgetFeed? = when (kind) {
        WidgetInstanceKind.TODAY -> WidgetFeed.SCHEDULED
        WidgetInstanceKind.FLOATER -> WidgetFeed.FLOATER
        WidgetInstanceKind.LIST -> listType?.let(::feedForListType)
        null -> null
    }

    /**
     * The feed a per-list instance whose selection IS readable shows — the same rule as
     * [feedFor]'s LIST branch, split out so the paths that already hold a real [WidgetListType]
     * (rendering a configured instance, building its "+" link) do not have to launder a non-null
     * answer back through a nullable one.
     */
    fun feedForListType(listType: WidgetListType): WidgetFeed = when (listType) {
        WidgetListType.TODO -> WidgetFeed.SCHEDULED
        WidgetListType.FLOATER -> WidgetFeed.FLOATER
    }

    /**
     * The Glance class that renders [kind]. A fresh instance per call, matching what the refreshers
     * already did — a `GlanceAppWidget` is a stateless renderer, and this is the ONLY place that
     * decides which one an `appWidgetId` is handed to.
     */
    fun newWidget(kind: WidgetInstanceKind): GlanceAppWidget = when (kind) {
        WidgetInstanceKind.TODAY -> TodayTasksWidget()
        WidgetInstanceKind.FLOATER -> FloaterTasksWidget()
        WidgetInstanceKind.LIST -> ListTasksWidget()
    }

    /**
     * The exact (`appWidgetId`, kind) pairs a full repaint will execute, as a pure function of the
     * ids the platform reports per receiver. [WidgetRefresher] does nothing but run this plan, so
     * the routing rule that used to be spread across three refreshers and their callers is one
     * testable expression here.
     *
     * The invariant it exists to hold: an id is ALWAYS paired with the kind of the receiver it was
     * enumerated from, so no id can reach a foreign widget class. [firstAppWidgetId] only reorders
     * the plan — the instance the user just interacted with is painted first, still with its own
     * kind, and is not painted twice.
     *
     * @param kindOf resolves a single id (the platform's provider binding); returns null when the
     *   id is unknown, which drops it from the head of the plan rather than guessing a kind.
     * @param idsForReceiver the live ids bound to one declared receiver; null when they could not
     *   be read.
     */
    fun renderPlan(
        firstAppWidgetId: Int?,
        kindOf: (Int) -> WidgetInstanceKind?,
        idsForReceiver: (Class<*>) -> IntArray?,
    ): List<WidgetRenderStep> {
        val steps = mutableListOf<WidgetRenderStep>()
        val planned = mutableSetOf<Int>()

        if (firstAppWidgetId != null) {
            val kind = kindOf(firstAppWidgetId)
            if (kind != null && planned.add(firstAppWidgetId)) {
                steps += WidgetRenderStep(firstAppWidgetId, kind)
            }
        }

        for ((kind, receiverClass) in bindings) {
            val ids = idsForReceiver(receiverClass) ?: continue
            for (appWidgetId in ids) {
                if (!planned.add(appWidgetId)) continue
                steps += WidgetRenderStep(appWidgetId, kind)
            }
        }
        return steps
    }
}

/** One repaint: this id, rendered by the Glance class this kind names. */
internal data class WidgetRenderStep(
    val appWidgetId: Int,
    val kind: WidgetInstanceKind,
)

/**
 * Resolves what a live `appWidgetId` actually is, from the platform's own provider binding.
 *
 * `AppWidgetManager.getAppWidgetInfo` returns the receiver the host bound this id to when it was
 * placed, so it cannot disagree with what is on the home screen — unlike a query parameter carried
 * on an intent, or the last widget class some refresher happened to construct. An id that no
 * longer exists (the instance was removed while a create sheet was open) returns `null`, which
 * every caller treats as "unknown", never as "today".
 */
internal class WidgetInstanceResolver(context: Context) {
    private val appContext = context.applicationContext

    fun kindOf(appWidgetId: Int): WidgetInstanceKind? {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return null
        val providerClassName = runCatching {
            AppWidgetManager.getInstance(appContext).getAppWidgetInfo(appWidgetId)?.provider?.className
        }.getOrNull()
        return WidgetInstanceCatalog.kindForReceiverClassName(providerClassName)
    }

    fun feedOf(appWidgetId: Int): WidgetFeed? {
        val kind = kindOf(appWidgetId) ?: return null
        val listType = if (kind == WidgetInstanceKind.LIST) {
            WidgetListSelectionStore(appContext).selectionFor(appWidgetId)?.listType
        } else {
            null
        }
        return WidgetInstanceCatalog.feedFor(kind, listType)
    }
}
