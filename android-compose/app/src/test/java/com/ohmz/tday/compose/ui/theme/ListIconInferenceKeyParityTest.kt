package com.ohmz.tday.compose.ui.theme

import com.ohmz.tday.shared.listicon.ListIconInference
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The inference table can only name glyphs that exist — mechanically, not by promise.
 *
 * This is the guard that matters most on this platform, because the failure it catches
 * is INVISIBLE. `tdayListIconResForKey` answers with the inbox drawable for null, for
 * blank and for a key it has never heard of, all three, with no log line and no crash.
 * So a typo in the shared keyword table — `"fitnes"`, a key renamed on one client —
 * does not ship as a broken icon. It ships as a list quietly wearing an inbox, which
 * is precisely what the feature was supposed to stop, and nobody would ever connect
 * the two.
 *
 * The second test goes wider than this module on purpose. The three icon tables
 * (Android's [TdayListIconOptions], `tday-web/src/lib/listIcons.ts`,
 * `ios-swiftUI/Tday/UI/Theme/TdayTheme.swift`) hold the same 68 keys today by hand and
 * by luck — nothing has ever enforced it. The moment the inference table starts
 * emitting keys, that coincidence becomes load-bearing: a key present on Android and
 * missing on iOS means the same list shows a briefcase on the phone and an inbox on
 * the tablet, and both clients think they are right. Reading the other two clients'
 * source from a JVM test is the only gate available here; it costs a file read.
 */
class ListIconInferenceKeyParityTest {

    @Test
    fun `every key the inference can emit is a glyph this app can draw`() {
        val supported = TdayListIconOptions.map { it.key }.toSet()
        val unknown = ListIconInference.emittableIconKeys.filterNot { it in supported }

        assertEquals(
            "the shared inference table names glyphs Android has no drawable for. " +
                "tdayListIconResForKey would paint an inbox for these and say nothing",
            emptyList<String>(),
            unknown,
        )
    }

    @Test
    fun `an inferred key resolves to a drawable other than the default`() {
        // The end-to-end version of the claim above: not "the key is in the list" but
        // "asking for it gets you a different picture than asking for nothing". A key
        // that silently normalised away would pass the membership test and fail here.
        val inboxRes = tdayListIconResForKey(TDAY_DEFAULT_LIST_ICON_KEY)
        val collapsed = ListIconInference.emittableIconKeys
            .filter { tdayListIconResForKey(it) == inboxRes }

        assertEquals(emptyList<String>(), collapsed)
    }

    @Test
    fun `a list whose owner chose a glyph keeps it, whatever its name says`() {
        // The one rule the brief calls non-negotiable, asserted at the resolver rather
        // than argued in a comment. "Groceries" infers a cart; a "Groceries" list whose
        // owner picked the flame gets the flame.
        assertEquals(
            tdayListIconResForKey("fire"),
            tdayListIconResForList(iconKey = "fire", listName = "Groceries"),
        )
        // Including when the choice happens to BE the default. This is the case the
        // old data model could not express at all, and the reason the create sheets
        // stopped posting their seeded preview.
        assertEquals(
            tdayListIconResForKey(TDAY_DEFAULT_LIST_ICON_KEY),
            tdayListIconResForList(iconKey = TDAY_DEFAULT_LIST_ICON_KEY, listName = "Groceries"),
        )
    }

    @Test
    fun `a list with no chosen glyph takes the one its name evidences, or the default`() {
        assertEquals(
            tdayListIconResForKey("cart"),
            tdayListIconResForList(iconKey = null, listName = "Groceries"),
        )
        assertEquals(
            tdayListIconResForKey("cart"),
            tdayListIconResForList(iconKey = "  ", listName = "Groceries"),
        )
        // Unsure is not a glyph. "Carpentry" contains `car` and means nothing of the sort.
        assertEquals(
            tdayListIconResForKey(TDAY_DEFAULT_LIST_ICON_KEY),
            tdayListIconResForList(iconKey = null, listName = "Carpentry"),
        )
        assertEquals(
            tdayListIconResForKey(TDAY_DEFAULT_LIST_ICON_KEY),
            tdayListIconResForList(iconKey = null, listName = null),
        )
    }

    @Test
    fun `all three clients still hold the same icon keys`() {
        val android = TdayListIconOptions.map { it.key }.toSet()

        val webSource = repoFile("tday-web/src/lib/listIcons.ts").readText()
        val web = WEB_KEY.findAll(webSource)
            .map { match -> match.groupValues[1].ifEmpty { TDAY_DEFAULT_LIST_ICON_KEY } }
            .toSet()

        val iosTable = repoFile("ios-swiftUI/Tday/UI/Theme/TdayTheme.swift")
            .readText()
            .substringAfter("tdayLucideListAssetTable: [String: String] = [")
            .substringBefore("\n]")
        val ios = IOS_KEY.findAll(iosTable).map { it.groupValues[1] }.toSet()

        assertTrue("the web icon table scan found nothing — its shape changed", web.size > 50)
        assertTrue("the iOS icon table scan found nothing — its shape changed", ios.size > 50)
        assertEquals("Android and web list icon keys have diverged", android, web)
        assertEquals("Android and iOS list icon keys have diverged", android, ios)
    }

    /** `{ key: "cart"` — and `{ key: DEFAULT_LIST_ICON_KEY`, which captures empty. */
    private val WEB_KEY = Regex("""\{\s*key:\s*(?:"([a-z]+)"|DEFAULT_LIST_ICON_KEY)""")

    /** `"cart": "LucideShoppingCart"` */
    private val IOS_KEY = Regex(""""([a-z0-9]+)":\s*"Lucide""")

    private fun repoFile(relativePath: String): File {
        val root = generateSequence(File(".").canonicalFile) { it.parentFile }
            .firstOrNull { File(it, "version.json").isFile && File(it, "shared").isDirectory }
            ?: error("could not locate the repo root from ${File(".").canonicalPath}")
        return File(root, relativePath).also {
            assertTrue("missing $relativePath — this test is scanning the wrong tree", it.isFile)
        }
    }
}
