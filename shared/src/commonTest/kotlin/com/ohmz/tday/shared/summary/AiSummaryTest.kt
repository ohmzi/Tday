package com.ohmz.tday.shared.summary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The AI half of the Anytime summary: what the model is asked for, and what it is allowed to
 * return.
 *
 * These matter more than they look. On any deployment running the `ai` profile the model's answer
 * is what renders on web, iOS and online Android, and the deterministic copy is a fallback nobody
 * sees — so a prompt that still orders "Mention urgency" for tasks with no deadline puts the exact
 * sentence back on screen that [FloaterSummaryTest] proves the engine no longer says.
 */
class AiSummaryTest {

    // 2026-06-06T09:00:00Z — the same clock the other summary tests use.
    private val nowMs = 1_780_390_800_000L
    private val day = 86_400_000L

    private fun floater(
        title: String,
        pinned: Boolean = false,
        priority: String? = "Low",
        untouchedDays: Long? = null,
    ) = SummaryTaskInput(
        title = title,
        priority = priority,
        dueEpochMs = null,
        pinned = pinned,
        kind = "anytime",
        updatedAtEpochMs = untouchedDays?.let { nowMs - it * day },
    )

    private fun dated(title: String, dueInDays: Long) = SummaryTaskInput(
        title = title,
        priority = "High",
        dueEpochMs = nowMs + dueInDays * day,
        kind = "scheduled",
    )

    private fun undatedPrompt(
        tasks: List<SummaryTaskInput> = listOf(floater("Renew passport", pinned = true), floater("Sort the loft")),
        locale: String? = "en",
    ) = AiSummaryPrompt.build(tasks, SummaryScope.FLOATER, nowMs, "UTC", locale)

    // ---- what the undated brief must not ask for ----

    @Test
    fun undatedPromptNeverAsksForUrgencyOrDeadlines() {
        val prompt = undatedPrompt().lowercase()
        // "Mention urgency, priority, and where to start" was the instruction that survived the
        // deterministic rewrite untouched. Each of these words may appear only inside the
        // sentence that FORBIDS it, so they are checked against the affirmative phrasing.
        for (ask in listOf("mention urgency", "where to start when helpful")) {
            assertFalse(prompt.contains(ask), "undated prompt still asks for \"$ask\": $prompt")
        }
    }

    @Test
    fun undatedPromptNeverRestatesTheCount() {
        // "Task count: N" invites the model to print a number that is already on screen as rows —
        // the redundancy this whole change exists to remove, and a number it can get wrong.
        val prompt = undatedPrompt((1..12).map { floater("Waiting $it") })
        assertFalse(prompt.contains("Task count"), "undated prompt hands the model a count: $prompt")
        assertTrue(prompt.contains("State no counts"), "undated prompt should forbid counts: $prompt")
    }

    @Test
    fun undatedPromptSaysAnytimeRatherThanTheInternalWord() {
        // "floater" is an implementation word; the screen says "Anytime". A model briefed with the
        // wrong noun writes the wrong noun.
        val prompt = undatedPrompt()
        assertTrue(prompt.contains("Anytime"), "prompt should name the view as the user sees it: $prompt")
        assertFalse(prompt.lowercase().contains("floater"), "internal vocabulary leaked: $prompt")
    }

    @Test
    fun undatedPromptCarriesTheSameSignalsThePlannerReads() {
        val tasks = listOf(
            floater("Renew passport", pinned = true),
            floater("Sort the loft boxes", untouchedDays = 200),
            floater("Fix the bike light", priority = "High"),
        )
        val prompt = AiSummaryPrompt.build(tasks, SummaryScope.FLOATER, nowMs, "UTC", "en")
        assertTrue(prompt.contains("pinned"), prompt)
        assertTrue(prompt.contains("untouched for months"), prompt)
        assertTrue(prompt.contains("High"), prompt)
        assertTrue(prompt.contains("Name at most one task"), prompt)
    }

    @Test
    fun theUndatedBranchIsChosenByTheDataNotTheScope() {
        // The backend hands its already-filtered floaters over under whatever scope it likes; the
        // engine keys its own undated branch off the data, and so must this.
        val tasks = listOf(floater("Renew passport"))
        assertEquals(
            AiSummaryPrompt.build(tasks, SummaryScope.FLOATER, nowMs, "UTC", "en"),
            AiSummaryPrompt.build(tasks, SummaryScope.ALL, nowMs, "UTC", "en"),
        )
        assertTrue(AiSummaryPrompt.isUndated(tasks))
        assertFalse(AiSummaryPrompt.isUndated(tasks + dated("Pay rent", 2)))
        // An empty set is not "undated": there is nothing to be undated.
        assertFalse(AiSummaryPrompt.isUndated(emptyList()))
    }

    @Test
    fun datedPromptKeepsItsUrgencyBriefAndItsCount() {
        val prompt = AiSummaryPrompt.build(
            listOf(dated("Pay rent", -2), dated("Call the dentist", 3)),
            SummaryScope.TODAY,
            nowMs,
            "UTC",
            "en",
        )
        assertTrue(prompt.contains("Mention urgency"), prompt)
        assertTrue(prompt.contains("Task count: 2"), prompt)
        assertTrue(prompt.contains("overdue"), prompt)
        assertTrue(prompt.contains("- scheduled; High, overdue; Pay rent"), prompt)
    }

    @Test
    fun everyPromptNamesTheLanguageToAnswerIn() {
        // The prompt carried no locale at all, so a French user with the `ai` profile on read an
        // English summary above a French screen — while the deterministic engine has been
        // translating into ten languages the whole time.
        assertTrue(undatedPrompt(locale = "fr-FR").contains("fr-FR"))
        assertTrue(
            AiSummaryPrompt.build(listOf(dated("Pay rent", 1)), SummaryScope.TODAY, nowMs, "UTC", "ja")
                .contains("IETF locale tag: ja."),
        )
        assertTrue(undatedPrompt(locale = null).contains("Write in English."))
    }

    @Test
    fun promptLengthIsBoundedByTheTaskCap() {
        val huge = (1..500).map { floater("Waiting number $it") }
        val lines = AiSummaryPrompt.build(huge, SummaryScope.FLOATER, nowMs, "UTC", "en")
            .lines().count { it.startsWith("- ") }
        assertEquals(AiSummaryPrompt.MAX_TASKS, lines)
    }

    // ---- what the model is allowed to return ----

    private val tasks = (1..7).map { floater("Waiting $it", pinned = it <= 2, untouchedDays = if (it == 7) 120 else 1) }

    private fun vet(text: String?) = AiSummaryGuard.vet(text, tasks, nowMs)

    @Test
    fun anUndatedAnswerStatingACountTheSetCannotJustifyIsRefused() {
        // 7 rows, 2 pinned, 1 dormant, 0 high. Any other number is a claim nothing supports.
        assertNull(vet("You have 9 things waiting here."), "a wrong count reached the user")
        assertNull(vet("Three of these have sat for 400 days."))
        assertEquals("Two of these are pinned.", vet("Two of these are pinned."))
        assertEquals("You pinned 2 of these.", vet("You pinned 2 of these."))
    }

    @Test
    fun digitsThatCameFromATitleAreNotTreatedAsAClaim() {
        // Rejecting "Pay 2026 taxes" because it contains 2026 would silently disable the AI
        // summary for anyone whose task names contain a number.
        val withNumbers = listOf(floater("Pay 2026 taxes"), floater("Renew passport"))
        assertEquals(
            "Pay 2026 taxes is the one you pinned.",
            AiSummaryGuard.vet("Pay 2026 taxes is the one you pinned.", withNumbers, nowMs),
        )
    }

    @Test
    fun theNumericRuleAppliesOnlyWhereEveryNumberWouldBeACount() {
        // A dated summary may legitimately print a date; judging those would take a second
        // engine, so the guard does not pretend to.
        val datedSet = listOf(dated("Pay rent", 6))
        assertEquals(
            "Pay rent is due on Jun 12.",
            AiSummaryGuard.vet("Pay rent is due on Jun 12.", datedSet, nowMs),
        )
    }

    @Test
    fun markdownAndRunawayAnswersAreRefused() {
        assertNull(vet("- one thing\n- another thing\n- a third\n- a fourth"))
        assertNull(vet("**Nothing** is waiting."))
        assertNull(vet("```\nNothing is waiting.\n```"))
        assertNull(vet("1. Start here."))
        assertNull(vet("x".repeat(AiSummaryGuard.MAX_LENGTH + 1)))
        assertNull(vet("   "))
        assertNull(vet(null))
    }

    @Test
    fun anAnswerThatPassesIsHandedBackUnchangedApartFromTrimming() {
        // The guard is a gate, not an editor: silently rewriting a model's sentence would make
        // what shipped untraceable to either half of the feature.
        assertEquals("Quite a few undated things have gathered here.", vet("  Quite a few undated things have gathered here.  "))
    }
}
