package com.ohmz.tday.widget

import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * DataStore Preferences keys shared between the widget state and the update manager.
 * Add any key that the Glance widget UI needs to read here.
 */
object WidgetStateKeys {
    /** Epoch-millis of the last successful widget data refresh. */
    val LAST_UPDATED_MS = longPreferencesKey("widget_last_updated_ms")

    /**
     * JSON-serialised snapshot of tasks for the widget to render.
     * Writing this key triggers Glance's state observer and recomposes the UI
     * without a full update() call, which is cheaper for small state changes.
     */
    val TASKS_JSON = stringPreferencesKey("widget_tasks_json")
    val FLOATERS_JSON = stringPreferencesKey("widget_floaters_json")
}
