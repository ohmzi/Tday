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
 * No path in this app can start one: [WidgetRefresher] only ever executes
 * [WidgetInstanceCatalog.renderPlan], which pairs every id with the kind of the receiver it was
 * enumerated FROM; `WidgetFastPaint` composes with the calling receiver's own `glanceAppWidget`
 * over that receiver's own broadcast ids; and Glance's `updateAll` resolves a class only through
 * that class's own receivers. But that is an argument, and a user report is not settled by one. A
 * line whose `provider=` disagrees with its own prefix is that bug, printed; the absence of one
 * across a reproduction rules the mechanism out and points the investigation elsewhere.
 */
internal fun widgetComposeLogLine(
    composingAs: WidgetInstanceKind,
    appWidgetId: Int,
    providerKind: WidgetInstanceKind?,
    details: String,
): String {
    val self = composingAs.name.lowercase(Locale.ROOT)
    val provider = providerKind?.name ?: "unknown"
    val mismatch = if (isWidgetKindMismatch(composingAs, providerKind)) " KIND-MISMATCH" else ""
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
