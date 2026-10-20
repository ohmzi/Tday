package com.ohmz.tday.compose.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The device-side half of the v0.7.28 icon cutover, pinned.
 *
 * `V29__clear_seeded_list_icons.sql` clears the seeded `"inbox"` out of Postgres so a list
 * whose owner never chose an icon can take one from its name. On Android that is not enough
 * on its own: `SyncManager` feeds `SecureConfigStore`'s local icon shadow to `mapListDto` as
 * `iconFallback`, so `dto.iconKey ?: iconFallback` puts the device's own remembered
 * `"inbox"` straight back over the column the migration just cleared. This function is what
 * stops that, and these are the cases that decide whether it is safe to run.
 *
 * The one that matters most is [a shadow with no seeded entries is left alone]: it is the
 * difference between a one-time cutover and a policy that deletes a user's chosen Inbox
 * every launch. The flag in `SecureConfigStore` is the other half of that, and it is asserted
 * there by construction rather than here — this function has no memory and cannot know how
 * many times it has been called.
 */
class SeededListIconShadowTest {

    private fun shadow(vararg entries: Pair<String, String>): String =
        JsonObject(entries.associate { (id, key) -> id to JsonPrimitive(key) }).toString()

    private fun parse(raw: String): Map<String, String?> =
        (Json.parseToJsonElement(raw) as JsonObject)
            .mapValues { (_, value) -> (value as? JsonPrimitive)?.content }

    @Test
    fun `the seeded default is pruned and every real choice survives`() {
        val pruned = prunedSeededListIconShadow(
            shadow(
                "list_groceries" to "inbox",
                "list_work" to "work",
                "list_gym" to "inbox",
                "list_reading" to "book",
            ),
        )

        assertEquals(
            mapOf("list_work" to "work", "list_reading" to "book"),
            parse(requireNotNull(pruned)),
        )
    }

    @Test
    fun `a shadow with no seeded entries is left alone`() {
        // null means "do not write", not "write an empty map". A device whose every entry
        // is a deliberate choice must come out of the cutover byte-identical: rewriting it
        // would be a no-op today and a way to lose an entry the day this function learns a
        // second rule.
        assertNull(prunedSeededListIconShadow(shadow("list_work" to "work")))
    }

    @Test
    fun `nothing stored is nothing to do`() {
        assertNull(prunedSeededListIconShadow(null))
        assertNull(prunedSeededListIconShadow(""))
        assertNull(prunedSeededListIconShadow("   "))
    }

    @Test
    fun `an unreadable shadow is left alone rather than clobbered`() {
        // `saveListIcon` reacts to unparseable bytes by starting a fresh map, which is the
        // right call when it is about to add an entry the user just asked for. Here there is
        // no user asking for anything, so the same reaction would be destroying a map on the
        // strength of failing to read it.
        assertNull(prunedSeededListIconShadow("not json at all"))
        assertNull(prunedSeededListIconShadow("{\"list_work\": "))
        // Valid JSON of the wrong shape is the same answer for the same reason.
        assertNull(prunedSeededListIconShadow("[\"list_work\"]"))
    }

    @Test
    fun `a seeded entry is recognised however it was cased or padded`() {
        // `tdayListIconResForKey` trims and lowercases before looking a key up, so " Inbox "
        // paints the inbox drawable exactly as "inbox" does. A prune that compared raw
        // strings would leave those lists frozen on the value it exists to clear, and the
        // mismatch would be invisible — both spellings render identically.
        val pruned = prunedSeededListIconShadow(
            shadow("a" to " Inbox ", "b" to "INBOX", "c" to "Inbox", "d" to "cart"),
        )

        assertEquals(mapOf("d" to "cart"), parse(requireNotNull(pruned)))
    }

    @Test
    fun `a shadow that is entirely seeded prunes to an empty map`() {
        // The common case for a user who never touched the icon row: everything goes, and
        // "{}" is what `getListIcon` reads as "no opinion" — `optString` on a missing key
        // answers "", which it maps to null.
        assertEquals(emptyMap<String, String?>(), parse(requireNotNull(prunedSeededListIconShadow(shadow("a" to "inbox")))))
    }

    @Test
    fun `a value this function did not write is carried through`() {
        // The remit is one known string. Dropping anything unrecognised would make a
        // targeted cutover into a general cleanup, which is a larger promise than the
        // migration it mirrors makes.
        val raw = "{\"a\":\"inbox\",\"b\":42,\"c\":null}"
        val pruned = requireNotNull(prunedSeededListIconShadow(raw))
        val survivors = Json.parseToJsonElement(pruned) as JsonObject

        assertEquals(setOf("b", "c"), survivors.keys)
    }
}
