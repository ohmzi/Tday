package com.ohmz.tday.compose.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import com.ohmz.tday.compose.core.model.TodoListMode

/**
 * The `tday://todos/create` deep link a widget's "+" opens, and the rules for reading it back.
 *
 * The instance id travels in the DATA URI rather than in an intent extra, and that is load-bearing.
 * Glance turns an `actionStartActivity` into `PendingIntent.getActivity(context, 0, intent,
 * FLAG_UPDATE_CURRENT or ...)`, and stamps its own per-instance disambiguating URI ONLY when the
 * intent has no data of its own. These intents all set data, so `PendingIntent` identity comes down
 * to `Intent.filterEquals` — which compares the data URI and IGNORES extras. An
 * `EXTRA_APPWIDGET_ID` here would therefore be silently overwritten across instances of the same
 * kind; a query parameter makes each instance's PendingIntent genuinely distinct.
 */
internal object WidgetCreateRoute {
    const val PARAM_TARGET = "target"
    const val PARAM_LIST_ID = "listId"
    const val PARAM_APP_WIDGET_ID = "appWidgetId"

    const val TARGET_TODAY = "today"
    const val TARGET_FLOATER = "floater"

    private const val CREATE_PATH = "tday://todos/create"

    /**
     * [appWidgetId] is omitted when it is not a real placed id — `provideGlance` also runs for
     * Glance's own synthetic (negative) preview ids, and those resolve to nothing.
     */
    fun deepLink(target: String, appWidgetId: Int, listId: String? = null): String = buildString {
        append(CREATE_PATH)
        append("?").append(PARAM_TARGET).append("=").append(target)
        if (appWidgetId > AppWidgetManager.INVALID_APPWIDGET_ID) {
            append("&").append(PARAM_APP_WIDGET_ID).append("=").append(appWidgetId)
        }
        if (listId != null) {
            append("&").append(PARAM_LIST_ID).append("=").append(Uri.encode(listId))
        }
    }

    /** The placed instance that opened this sheet, or null when it was not opened from a widget. */
    fun appWidgetIdFrom(intent: Intent?): Int? =
        intent?.data?.getQueryParameter(PARAM_APP_WIDGET_ID)
            ?.toIntOrNull()
            ?.takeIf { it > AppWidgetManager.INVALID_APPWIDGET_ID }

    fun targetParamFrom(intent: Intent?): String? = intent?.data?.getQueryParameter(PARAM_TARGET)

    fun listIdFrom(intent: Intent?): String? = intent?.data?.getQueryParameter(PARAM_LIST_ID)
}

/**
 * Which feed the widget create sheet writes into.
 *
 * Resolution is deliberately split in two (see [resolve]): the placed instance decides when there
 * is one, and the `target=` parameter is only a fallback for the entry points that have no widget
 * at all — the Quick Settings tile, the launcher shortcut, and the share sheet.
 */
internal enum class WidgetCreateTarget(
    val mode: TodoListMode,
    val showScheduleControls: Boolean,
) {
    TODAY(TodoListMode.TODAY, true),
    FLOATER(TodoListMode.FLOATER, false);

    companion object {
        /**
         * [instanceFeed] is the feed of the widget instance whose "+" was tapped, resolved from
         * the platform's own provider binding by [WidgetInstanceResolver]. When it is known it
         * WINS OUTRIGHT, including over a contradicting [targetParam] — a stale PendingIntent
         * cannot then create a scheduled task from a floater widget.
         *
         * [targetParam] is consulted only when there is no widget instance to ask. The final
         * `else` is the no-widget default (the QS tile and launcher shortcut both send
         * `target=today` explicitly; the share sheet passes [TODAY] directly), NOT a fallback for
         * a widget whose kind failed to resolve — that case never reaches it, because the
         * parameter the widget itself wrote is still there to answer.
         */
        fun resolve(instanceFeed: WidgetFeed?, targetParam: String?): WidgetCreateTarget = when {
            instanceFeed == WidgetFeed.FLOATER -> FLOATER
            instanceFeed == WidgetFeed.SCHEDULED -> TODAY
            targetParam?.lowercase() == WidgetCreateRoute.TARGET_FLOATER -> FLOATER
            else -> TODAY
        }
    }
}
