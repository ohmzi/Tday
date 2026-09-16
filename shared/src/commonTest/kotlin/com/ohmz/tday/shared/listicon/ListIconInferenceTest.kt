package com.ohmz.tday.shared.listicon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The inference table, pinned as a table.
 *
 * There is no device in this repo's CI and no way to look at a rendered glyph, so
 * "does the icon read as right" is not a claim this file can make. What it CAN make
 * is the two claims the feature actually rests on: the matcher emits the key we meant
 * for the titles we meant, and — the half that earns its keep — it emits NOTHING for
 * the titles that would fool a lazier matcher. The must-not-match block below is the
 * whole argument for whole-word lookup; delete it and the next person can swap in
 * `title.contains(word)` with every other test still green.
 */
class ListIconInferenceTest {

    @Test
    fun `a title whose subject is in the table infers that subject's glyph`() {
        val expected = mapOf(
            "Groceries" to "cart",
            "Grocery Shopping" to "cart",
            "Work" to "work",
            "Gym" to "fitness",
            "Reading" to "book",
            "Reading List" to "book",
            "Travel" to "flight",
            "Recipes" to "food",
            "Budget" to "money",
            "Home" to "home",
            "Passwords" to "key",
            "Dog" to "pets",
            "Birthday" to "cake",
        )

        assertEquals(
            expected,
            expected.keys.associateWith { ListIconInference.inferIconKey(it) },
        )
    }

    @Test
    fun `case and punctuation are not part of the subject`() {
        // The same list, typed four ways by four people, is one list.
        listOf("groceries", "GROCERIES", "  Groceries  ", "Groceries!!!", "Groceries 🛒")
            .forEach { title ->
                assertEquals("cart", ListIconInference.inferIconKey(title), title)
            }
    }

    /**
     * The words a substring matcher gets wrong, asserted by name.
     *
     * Each of these CONTAINS a table keyword and is about something else entirely:
     * "Carpentry" holds `car`, "Firewood" holds `fire`, "Scarlett" holds `car`,
     * "Homework" holds `home`, "Barcelona" holds `bar`. A cart on a carpentry list
     * is worse than the inbox it replaced, which is the brief's whole rule about
     * being unsure.
     */
    @Test
    fun `a keyword buried inside another word evidences nothing`() {
        listOf("Carpentry", "Firewood", "Scarlett", "Homework", "Barcelona", "Workaround", "Bookkeeping")
            .forEach { title ->
                assertNull(ListIconInference.inferIconKey(title), title)
            }
    }

    @Test
    fun `a title with no subject at all evidences nothing`() {
        listOf("", "   ", "!!!", "---", "2026", "Misc", "Stuff", "Untitled", "Q3")
            .forEach { title ->
                assertNull(ListIconInference.inferIconKey(title), title)
            }
        assertNull(ListIconInference.inferIconKey(null))
    }

    @Test
    fun `two different subjects in one title evidence nothing`() {
        // Equal warrant for two glyphs is not a reason to pick the leftmost.
        listOf("Work Travel", "Gym and Groceries", "Dog Food", "Music Books")
            .forEach { title ->
                assertNull(ListIconInference.inferIconKey(title), title)
            }
    }

    @Test
    fun `two words pointing at the same glyph still agree`() {
        assertEquals("cart", ListIconInference.inferIconKey("Shopping Errands"))
        assertEquals("fitness", ListIconInference.inferIconKey("Gym Workout"))
    }

    /**
     * The matcher must never answer with the default key.
     *
     * On Android `tdayListIconResForKey` paints the inbox drawable for null, for blank
     * and for an unrecognised key alike, so "I inferred inbox" and "I have nothing"
     * would arrive at the render site as the same pixel — and the whole never-override
     * rule is built on the caller being able to tell them apart.
     */
    @Test
    fun `the matcher never emits the default key`() {
        assertTrue(
            "inbox" !in ListIconInference.emittableIconKeys,
            "inbox is the caller's fallback, not an inference",
        )
        assertNull(ListIconInference.inferIconKey("Inbox"))
    }

    /**
     * No word may evidence two glyphs.
     *
     * A word listed under two keys would make the answer depend on map iteration
     * order, which is exactly the silent nondeterminism the ambiguity rule exists to
     * refuse — and it would be invisible in every other test here.
     */
    @Test
    fun `no keyword appears under two icon keys`() {
        val duplicates = ListIconInference.keywordTable
            .flatMap { (iconKey, words) -> words.map { it to iconKey } }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size > 1 }

        assertEquals(emptyMap(), duplicates, "a keyword claimed by two icon keys")
    }

    @Test
    fun `every keyword is lowercase and alphanumeric, or it can never be matched`() {
        // The lookup happens after lowercase()-and-split, so a table entry with a
        // capital or a space is dead weight that no title can ever reach.
        val unreachable = ListIconInference.keywordTable.values.flatten()
            .filter { word -> word != word.lowercase() || !word.all { it.isLetterOrDigit() } }

        assertEquals(emptyList(), unreachable)
    }
}
