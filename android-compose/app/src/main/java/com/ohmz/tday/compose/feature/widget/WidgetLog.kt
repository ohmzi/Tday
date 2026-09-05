package com.ohmz.tday.compose.feature.widget

import android.util.Log
import java.util.Locale

/**
 * One tag for every widget-lifecycle log, so a single Logcat filter (`tag:TdayWidget`) captures
 * the whole chain end to end: boot broadcast -> cache render -> Glance composition -> server sync.
 * Without that, diagnosing "the widget is blank" means guessing which link broke.
 *
 * These lines carry counts and states only — never task titles or notes. A diagnostic log is not
 * a place to spill user content, and logcat is readable by more than just the app.
 */
const val WIDGET_LOG_TAG = "TdayWidget"

/**
 * What a composing line carries when the Glance class rendering an instance is not the one the
 * platform says owns it. Grep for this in a capture: one occurrence is the reported symptom caught
 * in the act. Named rather than inlined so the test asserts on the same string the widget writes.
 */
internal const val WIDGET_KIND_MISMATCH_MARKER = "KIND-MISMATCH"

/**
 * The identity half of a widget's "composing" line: which Glance class is composing, for which
 * placed instance, and which receiver the PLATFORM says owns that instance.
 *
 * `provider=` is here to make the one remaining "my widget rendered as another kind" hypothesis
 * decidable from a single `adb logcat -s TdayWidget` capture, rather than from code review.
 *
 * The hypothesis: Glance keys its render session by `appWidgetId` ALONE — `AppWidgetSession`'s
 * constructor derives the session key from `createUniqueRemoteUiName(appWidgetId)`, and
 * `getOrCreateAppWidgetSession` REUSES a running session with the `GlanceAppWidget` it was
 * constructed with, whatever class a later `update()` passes in. So a session started with the
 * wrong class would render the wrong kind for that instance until the process died — which is
 * exactly "my Floater widget is showing the Today widget", including the "it flips back" once the
 * process is replaced.
 *
 * A release build DID start one, and the argument that said it could not is instructive: no path in
 * this app hands an id to a foreign widget class — [WidgetRefresher] only ever executes
 * [WidgetInstanceCatalog.renderPlan], which pairs every id with the kind of the receiver it was
 * enumerated FROM, and `WidgetFastPaint` composes with the calling receiver's own
 * `glanceAppWidget` over that receiver's own broadcast ids. What that reasoning missed is that
 * "class" is not an identity in a minified build: R8 merged the three widget classes into one, so
 * `TodayTasksWidget().updateAll` enumerated Floater ids and Glance's `updateAll` no longer resolved
 * "a class's own receivers" at all. See the Glance section of `proguard-rules.pro`, the keep rules
 * that fix it, and `:app:verifyReleaseWidgetClassIdentity`, which fails the build if it recurs.
 *
 * This line stays because a keep rule is not a proof about a device. A line whose `provider=`
 * disagrees with its own prefix is the bug, printed; its absence across a reproduction rules the
 * mechanism out and points the investigation elsewhere.
 */
internal fun widgetComposeLogLine(
    composingAs: WidgetInstanceKind,
    appWidgetId: Int,
    providerKind: WidgetInstanceKind?,
    details: String,
): String {
    val self = composingAs.name.lowercase(Locale.ROOT)
    val provider = providerKind?.name ?: "unknown"
    val mismatch = if (isWidgetKindMismatch(composingAs, providerKind)) " $WIDGET_KIND_MISMATCH_MARKER" else ""
    return "$self[$appWidgetId]: composing, provider=$provider$mismatch, $details"
}

/**
 * `null` means the platform could not tell us (the instance was removed mid-session, or
 * `getAppWidgetInfo` threw) — unknown is not a disagreement, so it is never reported as one.
 */
internal fun isWidgetKindMismatch(
    composingAs: WidgetInstanceKind,
    providerKind: WidgetInstanceKind?,
): Boolean = providerKind != null && providerKind != composingAs

/** Logs [widgetComposeLogLine], at ERROR rather than INFO when the two kinds disagree. */
internal fun logWidgetComposition(
    composingAs: WidgetInstanceKind,
    appWidgetId: Int,
    providerKind: WidgetInstanceKind?,
    details: String,
) {
    val line = widgetComposeLogLine(composingAs, appWidgetId, providerKind, details)
    if (isWidgetKindMismatch(composingAs, providerKind)) {
        Log.e(WIDGET_LOG_TAG, line)
    } else {
        Log.i(WIDGET_LOG_TAG, line)
    }
}
