package com.ohmz.tday.compose.feature.widget

import android.content.Context
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetListType

/** What one placed list-widget instance is configured to show. */
internal data class WidgetListSelection(
    val listId: String,
    val listType: WidgetListType,
    /** Shown as the widget's header — same trade-off as Today/Floater's title (see
     *  `WidgetSnapshot`'s KDoc): storing the name here, chosen once at configure time, is simpler
     *  than re-reading the cache on every render, and a rename is rare enough that a stale header
     *  until the next reconfigure is an acceptable trade. */
    val listName: String,
)

/**
 * Per-`appWidgetId` config for the list-scoped widget (widgets v3) — genuinely new state this app
 * had no precedent for: every existing widget (Today, Floater) is configured once per KIND, not
 * once per placed instance. Plain `SharedPreferences`, matching every other lightweight
 * on-device-only setting in this app (`AppSecurityPreferenceStore`, `ThemePreferenceStore`, etc.)
 * — nothing stored here is sensitive (a list id and its cached display name), so there is no
 * reason to pay for `EncryptedSharedPreferences`' Keystore round trip.
 *
 * Read from three places: [WidgetListConfigurationActivity] (writes on pick), `ListTasksWidget`'s
 * `provideGlance` (reads the instance it is rendering), and [WidgetSnapshotWriter] (enumerates
 * every configured instance so a cache write can rebuild each one's snapshot). None of those are
 * Hilt-reachable from a Glance render path, so — like [WidgetSnapshotStore] — this is constructed
 * directly from `applicationContext`, not injected.
 */
internal class WidgetListSelectionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun selectionFor(appWidgetId: Int): WidgetListSelection? {
        val listId = preferences.getString(keyListId(appWidgetId), null) ?: return null
        val typeRaw = preferences.getString(keyListType(appWidgetId), null) ?: return null
        val listType = runCatching { WidgetListType.valueOf(typeRaw) }.getOrNull() ?: return null
        val listName = preferences.getString(keyListName(appWidgetId), null).orEmpty()
        return WidgetListSelection(listId, listType, listName)
    }

    fun setSelection(appWidgetId: Int, selection: WidgetListSelection) {
        val knownIds = mutableKnownIds().apply { add(appWidgetId.toString()) }
        preferences.edit()
            .putString(keyListId(appWidgetId), selection.listId)
            .putString(keyListType(appWidgetId), selection.listType.name)
            .putString(keyListName(appWidgetId), selection.listName)
            .putStringSet(KEY_KNOWN_IDS, knownIds)
            .apply()
    }

    /** Called from the widget's `onDeleted` — the host removed this instance for good. */
    fun clearSelection(appWidgetId: Int) {
        val knownIds = mutableKnownIds().apply { remove(appWidgetId.toString()) }
        preferences.edit()
            .remove(keyListId(appWidgetId))
            .remove(keyListType(appWidgetId))
            .remove(keyListName(appWidgetId))
            .putStringSet(KEY_KNOWN_IDS, knownIds)
            .apply()
    }

    /** Every appWidgetId with a live selection — what [WidgetSnapshotWriter] iterates to rebuild
     *  each instance's snapshot on a cache write. */
    fun configuredWidgetIds(): Set<Int> =
        preferences.getStringSet(KEY_KNOWN_IDS, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    // `SharedPreferences.getStringSet` hands back a set callers must not mutate in place — the
    // platform docs warn the underlying instance may be the one still held internally. Copying
    // into a fresh HashSet before add/remove avoids a class of "worked once, then silently stopped
    // persisting" bugs.
    private fun mutableKnownIds(): MutableSet<String> =
        HashSet(preferences.getStringSet(KEY_KNOWN_IDS, emptySet()).orEmpty())

    private fun keyListId(appWidgetId: Int) = "list_id_$appWidgetId"
    private fun keyListType(appWidgetId: Int) = "list_type_$appWidgetId"
    private fun keyListName(appWidgetId: Int) = "list_name_$appWidgetId"

    private companion object {
        const val PREF_NAME = "tday_widget_list_selection_prefs"
        const val KEY_KNOWN_IDS = "known_widget_ids"
    }
}
