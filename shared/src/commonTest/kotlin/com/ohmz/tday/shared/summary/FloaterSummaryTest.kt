package com.ohmz.tday.shared.summary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Quality tests for the undated ("Anytime") summary.
 *
 * These deliberately assert PROPERTIES computed from the input rather than literal copy. A test
 * that hardcodes a sentence pins the sentence and nothing else — the previous floater test did
 * exactly that, and nine broken translations shipped underneath it. Everything here is checked
 * in all ten locales, because the bug this rewrite removes ("both are due anytime" rendering as
 * "entrambe sono in scadenza" — *both are expiring*) was invisible from English.
 */
class FloaterSummaryTest {

    // 2026-06-06T09:00:00Z — the same clock SummaryEngineTest uses.
    private val nowMs = 1_780_390_800_000L
    private val utc = "UTC"
    private val day = 86_400_000L

    private val locales = listOf("en", "es", "fr", "de", "it", "pt", "ru", "zh", "ja", "ms")

    private fun floater(
        title: String,
        pinned: Boolean = false,
        priority: String? = "Low",
        untouchedDays: Long? = null,
        listId: String? = null,
    ) = SummaryTaskInput(
        title = title,
        priority = priority,
        dueEpochMs = null,
        pinned = pinned,
        listId = listId,
        kind = "anytime",
        updatedAtEpochMs = untouchedDays?.let { nowMs - it * day },
    )

    private fun summarize(
        tasks: List<SummaryTaskInput>,
        locale: String = "en",
        listId: String? = null,
        scope: SummaryScope = SummaryScope.FLOATER,
    ) = SummaryEngine.summarize(tasks, scope, nowMs, utc, locale, listId)

    private fun plain(count: Int) = (1..count).map { floater("Floater number $it") }

    // ---- what the summary must never say ----

    /**
     * Keys whose wording is *about deadlines*. An undated task has none, so none of their
     * literal fragments may appear in an undated summary — in any locale. This is the test that
     * would have caught the Italian "in scadenza"; it is written against the bundle rather than
     * against a word list so that a future edit to a due phrase is covered automatically.
     *
     * The purely structural frames (`startWith`, `nextUp`, `taskPhrase*`) are deliberately NOT
     * here. They carry no deadline meaning of their own, and their fragments are copulas —
     * German "ist", "war" — which would fail honest copy for the wrong reason. Their reuse is
     * caught instead by [summaryNamesAtMostOneTaskFromTheList], since they exist to name tasks.
     */
    private val dueVocabularyKeys = listOf(
        "dueAnytime", "dueOnDate", "dueOnDateWindow", "dueTodayWindow", "dueTomorrowWindow",
        "dueYesterdayWindow", "dueTonight", "targetToday", "targetTomorrow", "targetYesterday",
        "dayGroupedPresent", "dayGroupedPast", "overdueCatchUp",
    )

    private val placeholder = Regex("\\{\\{[a-zA-Z]+}}")

    private fun isCjk(c: Char): Boolean = c.code in 0x3040..0x9FFF

    /** The fixed words of a template, with the parameters (and the punctuation glue) removed. */
    private fun literalFragments(template: String): List<String> =
        template.split(placeholder)
            .map { it.trim { c -> c.isWhitespace() || c in ",.;:—-·、，。：" } }
            .filter { fragment -> fragment.length >= 3 || (fragment.length >= 2 && fragment.any(::isCjk)) }

    /**
     * Whole-word containment. A bare `contains` would flag German "war" (from "war fällig")
     * inside "wartet" — a false positive that would push the copy around for no reason. CJK
     * writes no word boundaries, so there it falls back to plain containment.
     */
    private fun containsWord(haystack: String, fragment: String): Boolean {
        if (fragment.any(::isCjk)) return haystack.contains(fragment)
        var from = haystack.indexOf(fragment)
        while (from >= 0) {
            val before = haystack.getOrNull(from - 1)
            val after = haystack.getOrNull(from + fragment.length)
            if (before?.isLetter() != true && after?.isLetter() != true) return true
            from = haystack.indexOf(fragment, from + 1)
        }
        return false
    }

    @Test
    fun undatedSummaryNeverUsesDueVocabularyInAnyLocale() {
        val sets = listOf(
            plain(1),
            plain(3),
            plain(12),
            listOf(floater("Renew passport", pinned = true), floater("Fix the bike light")),
            (1..5).map { floater("Dormant $it", untouchedDays = 200) },
        )
        for (locale in locales) {
            val bundle = SummaryStringBundles.forLocale(locale)
            val forbidden = dueVocabularyKeys.flatMap { literalFragments(bundle.t(it)) }.distinct()
            for (tasks in sets) {
                val out = summarize(tasks, locale)
                for (fragment in forbidden) {
                    assertFalse(
                        containsWord(out, fragment),
                        "[$locale] undated summary leaked deadline wording \"$fragment\": $out",
                    )
                }
            }
        }
    }

    @Test
    fun englishSummaryDropsTheRepeatedNoun() {
        // "task"/"anytime" in every clause was the redundancy the rewrite exists to remove.
        val out = summarize(plain(12) + floater("Renew passport", pinned = true)).lowercase()
        for (word in listOf("task", "anytime", "due", "overdue")) {
            assertFalse(out.contains(word), "expected no \"$word\" in: $out")
        }
    }

    // ---- what the summary must not repeat from the screen ----

    @Test
    fun summaryNamesAtMostOneTaskFromTheList() {
        // Zero-padded so no title is a prefix of another: "title 1" inside "title 10" would
        // fail this test for a summary that named exactly one task.
        val titles = (1..12).map { "Distinct title ${it.toString().padStart(2, '0')}" }
        val sets = listOf(
            titles.map { floater(it) },
            titles.mapIndexed { index, title -> floater(title, pinned = index == 3) },
            titles.mapIndexed { index, title -> floater(title, pinned = index >= 9) },
            titles.mapIndexed { index, title -> floater(title, priority = if (index == 5) "High" else "Low") },
        )
        for (locale in locales) {
            for (tasks in sets) {
                val out = summarize(tasks, locale)
                val echoed = titles.count { out.contains(it) }
                assertTrue(echoed <= 1, "[$locale] echoed $echoed titles back at the list: $out")
            }
        }
    }

    @Test
    fun summaryLengthDoesNotGrowWithTheList() {
        // The bar: a summary longer than the list it summarizes has failed. Length is bounded by
        // the template plus one compacted title, so it is flat in the number of tasks.
        for (locale in locales) {
            val twelve = summarize(plain(12), locale).length
            val forty = summarize(plain(40), locale).length
            assertEquals(twelve, forty, "[$locale] summary length must not track the task count")

            val big = plain(40)
            val listLength = big.sumOf { it.title.length }
            val summary = summarize(big, locale)
            assertTrue(
                summary.length * 4 < listLength,
                "[$locale] summary (${summary.length}) is not meaningfully shorter than the list ($listLength)",
            )
        }
    }

    // ---- what the summary must never invent ----

    @Test
    fun everyNumberInTheSummaryIsDerivableFromTheInput() {
        val tasks = (1..7).map { floater("Waiting $it", pinned = it <= 2, untouchedDays = if (it == 7) 120 else 1) }
        val derivable = setOf(
            tasks.size,
            tasks.count { it.pinned },
            tasks.count { it.updatedAtEpochMs != null && nowMs - it.updatedAtEpochMs!! >= 90 * day },
            tasks.count { it.priority == "High" },
        ).map { it.toString() }.toSet()
        for (locale in locales) {
            val out = summarize(tasks, locale)
            for (number in Regex("\\d+").findAll(out).map { it.value }) {
                assertTrue(number in derivable, "[$locale] summary stated $number, which the input cannot justify: $out")
            }
        }
    }

    @Test
    fun theOnlyTaskNamedIsOneThatWasPassedIn() {
        val tasks = listOf(
            floater("Alpha"),
            floater("Bravo", pinned = true),
            floater("Charlie", priority = "High"),
        )
        val plan = FloaterSummaryPlanner.plan(tasks, nowMs)
        assertEquals("Bravo", plan.noteTitle, "the pinned task is the one the note names")
        assertTrue(tasks.any { it.title == plan.noteTitle })
    }

    @Test
    fun restingNotesNameNoTaskAtAll() {
        // `updatedAtEpochMs` is a last-write clock: naming "the oldest" would be a claim about
        // creation that a rename falsifies. Counting dormant tasks is the only honest use.
        val plan = FloaterSummaryPlanner.plan(
            listOf(floater("Sort the loft boxes", untouchedDays = 200), floater("Active thing", untouchedDays = 1)),
            nowMs,
        )
        assertEquals(FloaterNote.RESTING_ONE, plan.note)
        assertEquals(null, plan.noteTitle)
    }

    @Test
    fun everyLocaleRendersRealSentencesRatherThanKeysOrPlaceholders() {
        val tasks = listOf(floater("Renew passport", pinned = true), floater("Call the dentist"))
        for (locale in locales) {
            val out = summarize(tasks, locale)
            assertFalse(out.contains("{{"), "[$locale] unresolved placeholder: $out")
            assertFalse(out.contains("floater"), "[$locale] raw key leaked into copy: $out")
            assertTrue(out.contains("Renew passport"), "[$locale] lost the task it names: $out")
            assertTrue(out.length in 10..220, "[$locale] implausible summary length: $out")
        }
    }

    // ---- the signals must actually move the output ----

    @Test
    fun pinningChangesTheSummary() {
        // Before this change, `pinned` was declared on the input and never read: the same set
        // pinned and unpinned produced byte-identical strings.
        val base = (1..4).map { floater("Waiting $it") }
        val pinned = base.mapIndexed { index, task -> task.copy(pinned = index == 1) }
        for (locale in locales) {
            assertNotEquals(summarize(base, locale), summarize(pinned, locale), "[$locale] pin was ignored")
        }
    }

    @Test
    fun dormancyChangesTheSummary() {
        val fresh = (1..4).map { floater("Waiting $it", untouchedDays = 2) }
        val dormant = (1..4).map { floater("Waiting $it", untouchedDays = 200) }
        for (locale in locales) {
            assertNotEquals(summarize(fresh, locale), summarize(dormant, locale), "[$locale] dormancy was ignored")
        }
    }

    @Test
    fun differentlyShapedSetsReadDifferently() {
        // The old engine emitted one sentence frame with the names swapped, so every set of
        // three or more read the same. Distinct shapes must now produce distinct sentences.
        val shapes = listOf(
            plain(1),
            plain(3),
            plain(8),
            plain(20),
            plain(3).mapIndexed { index, task -> task.copy(pinned = index == 0) },
            plain(3).map { it.copy(pinned = true) },
            plain(3).mapIndexed { index, task -> task.copy(priority = if (index == 0) "High" else "Low") },
            (1..3).map { floater("Dormant $it", untouchedDays = 200) },
        )
        for (locale in locales) {
            val rendered = shapes.map { summarize(it, locale) }
            assertEquals(
                rendered.size, rendered.distinct().size,
                "[$locale] structurally different sets collapsed to the same copy: $rendered",
            )
        }
    }

    // ---- scope contract ----

    @Test
    fun anytimeListViewExcludesFloatersFromOtherLists() {
        // SummaryScope.FLOATER used to return a bare `true`, so an Anytime *list* summary
        // described floaters filed in every other list. The backend already filtered them out;
        // offline Android did not, and the two disagreed about the same screen.
        val tasks = listOf(floater("In list A", listId = "listA"), floater("In list B", listId = "listB"))
        val plan = FloaterSummaryPlanner.plan(listOf(tasks.first()), nowMs)
        assertEquals(FloaterPileBand.ONE, plan.band)
        assertEquals(summarize(listOf(tasks.first())), summarize(tasks, listId = "listA"))
    }

    @Test
    fun anytimeViewExcludesDatedTasks() {
        val out = summarize(
            listOf(
                floater("Someday idea"),
                SummaryTaskInput(title = "Pay rent", priority = "High", dueEpochMs = nowMs - 6 * day),
            ),
        )
        assertEquals(summarize(listOf(floater("Someday idea"))), out)
        assertFalse(out.contains("Pay rent"), "a dated task reached an undated view: $out")
    }

    @Test
    fun emptyAnytimeViewDoesNotClaimNothingNeedsAttention() {
        for (locale in locales) {
            val bundle = SummaryStringBundles.forLocale(locale)
            val out = summarize(emptyList(), locale)
            assertEquals(bundle.t("floaterClear"), out)
            assertNotEquals(bundle.t("clearForNow"), out, "[$locale] Anytime reused the deadline-flavoured empty line")
        }
    }

    @Test
    fun serverRenderedAnytimeFallbackUsesTheSameVoice() {
        // The backend hands its pre-filtered floaters to the engine under SummaryScope.ALL, so
        // the branch must key off the data. If it ever keys off the scope again, this fails.
        val tasks = listOf(floater("Renew passport", pinned = true), floater("Call the dentist"))
        assertEquals(summarize(tasks), summarize(tasks, scope = SummaryScope.ALL))
    }

    // ---- the plan itself ----

    @Test
    fun aSingleRowPileNeverNamesItsOnlyRow() {
        // With one task the title is the list; repeating it is the redundancy in miniature.
        val plan = FloaterSummaryPlanner.plan(listOf(floater("Renew passport", pinned = true)), nowMs)
        assertEquals(FloaterNote.NONE, plan.note)
        assertEquals(null, plan.noteTitle)
        for (locale in locales) {
            assertFalse(
                summarize(listOf(floater("Renew passport", pinned = true)), locale).contains("Renew passport"),
            )
        }
        // A note that counts rather than points still survives: it says something new.
        assertEquals(
            FloaterNote.RESTING_ALL,
            FloaterSummaryPlanner.plan(listOf(floater("Dormant", untouchedDays = 200)), nowMs).note,
        )
    }

    @Test
    fun sentencesJoinWithoutAStrayGapAfterACjkFullStop() {
        val tasks = listOf(floater("Renew passport", pinned = true), floater("Call the dentist"))
        for (locale in listOf("zh", "ja")) {
            val out = summarize(tasks, locale)
            assertFalse(out.contains("。 "), "[$locale] ASCII gap after a CJK full stop: $out")
        }
        // Locales that end a sentence with "." keep the ordinary space between the two.
        assertTrue(summarize(tasks, "en").contains(". "))
    }

    @Test
    fun pileBandsSplitAtTheirDocumentedBoundaries() {
        assertEquals(FloaterPileBand.ONE, FloaterSummaryPlanner.bandFor(1))
        assertEquals(FloaterPileBand.FEW, FloaterSummaryPlanner.bandFor(2))
        assertEquals(FloaterPileBand.FEW, FloaterSummaryPlanner.bandFor(FloaterSummaryPlanner.FEW_MAX))
        assertEquals(FloaterPileBand.SOME, FloaterSummaryPlanner.bandFor(FloaterSummaryPlanner.FEW_MAX + 1))
        assertEquals(FloaterPileBand.SOME, FloaterSummaryPlanner.bandFor(FloaterSummaryPlanner.SOME_MAX))
        assertEquals(FloaterPileBand.MANY, FloaterSummaryPlanner.bandFor(FloaterSummaryPlanner.SOME_MAX + 1))
    }

    @Test
    fun noteFollowsItsDocumentedPrecedence() {
        fun note(tasks: List<SummaryTaskInput>) = FloaterSummaryPlanner.plan(tasks, nowMs).note

        assertEquals(FloaterNote.NONE, note(plain(3)))
        assertEquals(FloaterNote.PINNED_ONE, note(plain(3).mapIndexed { i, t -> t.copy(pinned = i == 0) }))
        assertEquals(FloaterNote.PINNED_MANY, note(plain(3).mapIndexed { i, t -> t.copy(pinned = i < 2) }))
        assertEquals(
            FloaterNote.RESTING_ONE,
            note(listOf(floater("a", untouchedDays = 200), floater("b", untouchedDays = 1))),
        )
        assertEquals(
            FloaterNote.RESTING_MANY,
            note(listOf(floater("a", untouchedDays = 200), floater("b", untouchedDays = 95), floater("c"))),
        )
        assertEquals(
            FloaterNote.RESTING_ALL,
            note((1..3).map { floater("dormant $it", untouchedDays = 200) }),
        )
        // A wholly dormant pile outranks a pin — the dormancy is the story, not the mark.
        assertEquals(
            FloaterNote.RESTING_ALL,
            note((1..3).map { floater("dormant $it", pinned = it == 1, untouchedDays = 200) }),
        )
        assertEquals(
            FloaterNote.PRIORITY_ONE,
            note(plain(3).mapIndexed { i, t -> t.copy(priority = if (i == 0) "High" else "Low") }),
        )
        assertEquals(FloaterNote.PRIORITY_MANY, note(plain(3).map { it.copy(priority = "Urgent") }))
        // Medium is not "high priority": claiming it would be the summary inventing importance.
        assertEquals(FloaterNote.NONE, note(plain(3).map { it.copy(priority = "Medium") }))
    }

    @Test
    fun theNamedTaskIsTheOneAtTheTopOfTheList() {
        // Mirrors TaskSortEngine.compareFloaters: pinned, then priority, then most recent.
        val tasks = listOf(
            floater("Low and old", untouchedDays = 10),
            floater("High priority", priority = "High"),
            floater("Pinned", pinned = true),
        )
        assertEquals("Pinned", FloaterSummaryPlanner.plan(tasks, nowMs).noteTitle)
        assertEquals(
            "High priority",
            FloaterSummaryPlanner.plan(tasks.filterNot { it.pinned }, nowMs).noteTitle,
        )
    }
}
