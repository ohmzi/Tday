package com.ohmz.tday.compose.feature.widget

import android.util.Log
import java.util.Locale

/**
 * One tag for every widget-lifecycle log, so a single Logcat filter (`tag:TdayWidget`) captures
 * the whole chain end to end: boot broadcast -> cache render -> widget render -> server sync.
 * Without that, diagnosing "the widget is blank" means guessing which link broke.
 *
 * These lines carry counts and states only — never task titles or notes. A diagnostic log is not
 * a place to spill user content, and logcat is readable by more than just the app.
 */
const val WIDGET_LOG_TAG = "TdayWidget"

/**
 * What a composing line carries when the kind rendering an instance is not the one the platform
 * says owns it. Grep for this in a capture: one occurrence is the reported symptom caught in the
 * act. Named rather than inlined so the test asserts on the same string the widget writes.
 */
internal const val WIDGET_KIND_MISMATCH_MARKER = "KIND-MISMATCH"

/**
 * The identity half of a widget's "composing" line, written once per render: which kind is being
 * rendered, for which placed instance, and which receiver the PLATFORM says owns that instance.
 *
 * `provider=` makes "my widget rendered as another kind" decidable from a single
 * `adb logcat -s TdayWidget` capture rather than from code review. That bug was real under the
 * old Glance renderer: Glance keyed its render session by `appWidgetId` alone and kept whichever
 * widget class started it, and R8 merged the three widget classes into one, so a Today session
 * could own a Floater instance until the process died. Rendering now routes by kind
 * ([WidgetInstanceCatalog.renderPlan], and each receiver's own kind for its own broadcast ids),
 * with no session and no class lookup, so the mechanism is gone — the line stays because a
 * routing rule is not a proof about a device. A line whose `provider=` disagrees with its own
 * prefix is the bug, printed.
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
