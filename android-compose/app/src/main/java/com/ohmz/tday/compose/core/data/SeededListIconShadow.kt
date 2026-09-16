package com.ohmz.tday.compose.core.data

import com.ohmz.tday.compose.ui.theme.TDAY_DEFAULT_LIST_ICON_KEY
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The device-side half of `V29__clear_seeded_list_icons.sql`.
 *
 * [SecureConfigStore.saveListIcon] keeps a `listId -> iconKey` shadow of every icon this
 * device has submitted, and [com.ohmz.tday.compose.core.data.sync.SyncManager] hands it to
 * `mapListDto` as `iconFallback` — so a list whose server column is null still comes back
 * wearing whatever the shadow remembers. The shadow exists for a real case: a server that
 * drops the field on a round trip should not cost the user the icon they picked.
 *
 * Which makes it the one place the server backfill cannot reach. Until v0.7.28 the create
 * sheet posted its seeded default, and this store recorded that default beside it, so on
 * any device that has created a list the shadow holds `"inbox"` for it. Clear the column in
 * Postgres and `dto.iconKey ?: iconFallback` puts `"inbox"` straight back — the migration
 * would run, the SQL would report rows updated, and Android alone would still show every
 * list wearing the glyph the feature exists to replace, for a reason nothing on the server
 * could explain.
 *
 * Same trade as the migration, argued there at length: a pre-cutover `"inbox"` cannot be
 * told from a deliberate one, because nothing ever wrote the difference down; clearing it
 * costs the deliberate minority two taps that then stick forever, and keeping it costs
 * everyone the feature. ONE-SHOT, because after the prune `"inbox"` in this store means
 * what it says — the sheets no longer submit a default — and pruning it a second time
 * would start eating real choices.
 *
 * Pure, and separate from [SecureConfigStore], so it can be tested without a device: the
 * store itself is `EncryptedSharedPreferences` over a `Context` and there is no Robolectric
 * in this module, so a prune written inline there would be unprovable locally in a repo
 * that proves this kind of claim with a pure function and a test.
 *
 * `kotlinx.serialization` rather than the `org.json.JSONObject` the store itself uses, for
 * the same reason. `org.json` on the unit-test classpath is the android.jar STUB — every
 * method throws `RuntimeException("Stub!")` — and this module adds neither Robolectric nor a
 * real `org.json`, so a version of this function written in the store's own spelling could
 * not be run by a single test here. `kotlinx-serialization-json` is already an
 * `implementation` dependency and is real JVM code in unit tests. The two agree on the
 * bytes: both read and write ordinary JSON, so what this writes `getListIcon` still parses.
 *
 * @param raw the stored `list_icon_map` JSON, or null when nothing has ever been stored.
 * @return the JSON to store in its place, or null when there is nothing worth writing —
 *   no shadow, unparseable bytes (left alone rather than clobbered; a write here is not
 *   worth destroying a map this function failed to read), or no seeded entries in it.
 */
internal fun prunedSeededListIconShadow(raw: String?): String? {
    if (raw.isNullOrBlank()) return null

    val stored = runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonObject
        ?: return null

    val survivors = mutableMapOf<String, JsonElement>()
    var pruned = 0
    for ((listId, value) in stored) {
        val iconKey = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
        // Trimmed and lowercased before comparing, because the question has to be the one
        // `normalizeTdayListIconKeyOrNull` answers at the render site: a shadow entry of
        // " Inbox " paints the inbox drawable, so it is just as much a seeded default as
        // "inbox" is, and leaving it behind would freeze that list on the very value this
        // prune exists to clear.
        if (iconKey != null && iconKey.trim().lowercase() == TDAY_DEFAULT_LIST_ICON_KEY) {
            pruned++
        } else {
            // A non-string value is something this function did not write and does not
            // understand. Carried through untouched rather than dropped: the prune's
            // remit is one known value, and a cleanup that also quietly discarded whatever
            // it failed to recognise would be a different, larger promise.
            survivors[listId] = value
        }
    }

    return if (pruned == 0) null else JsonObject(survivors).toString()
}
