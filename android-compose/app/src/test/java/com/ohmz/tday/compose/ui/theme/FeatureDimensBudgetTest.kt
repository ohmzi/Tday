package com.ohmz.tday.compose.ui.theme

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ratchet on anonymous `.dp` under `feature/`.
 *
 * `TdayDimens` declares a radius scale that 99 `RoundedCornerShape(<n>.dp)` call sites ignore, and
 * that is the small half of it: the screens carry ~845 anonymous `.dp` between them. Migrating them
 * is PR 43c…43m, one screen file per session. This test is what makes that a ratchet instead of a
 * treadmill — **it does not ask anyone to migrate, it asks that nobody add**. Every seeded file has
 * a ceiling equal to what it holds today, so a migration lowers a number and nothing else can raise
 * one, and a file with no ceiling must hold zero.
 *
 * ## The counting rule
 *
 * A count is only meaningful as the output of a rule, so the rule lives here in the code that
 * applies it rather than in a fixture that could drift from it:
 *
 *  - walk `main/java/com/ohmz/tday/compose/feature`, `.kt` only;
 *  - strip block comments and `//`-to-EOL first. `tests/fixtures/motion-budget.json` settled this
 *    for the motion budget and the reason carries over: a call-site comment arguing why a value is
 *    deliberately not a token quotes the value while arguing, and an argument for leaving something
 *    alone must not raise the budget it is arguing about;
 *  - skip a line declaring a named private `Dp` constant. `docs/CODING_STANDARDS.md` explicitly
 *    permits those for local layout geometry, animation offsets and illustration metrics, and 50 of
 *    them exist under `feature/` today (28 in `CalendarScreen.kt` alone). A lint that failed them
 *    would contradict the standard it is enforcing — what the standard forbids is the *anonymous*
 *    literal in the render path, which is exactly what is left after the skip;
 *  - count `<n>.dp` in the remainder.
 *
 * ## The widget exemption
 *
 * `feature/widget/` is out of the walk, permanently — 43n's question, answered here so the file
 * stops carrying it. The split it expected to make, radius out and spacing in, has nothing to cut:
 * `TaskWidgetDesign.kt` declares no radius at all. A Glance surface is painted with
 * `background(ImageProvider(R.drawable.…))`, so the widget's corners live in `res/values/dimens.xml`
 * where `WidgetCornerRadiusTest` pins them against the launcher's own enforced clip. All 77 of its
 * anonymous `.dp` are sizes and insets, and every one of them answers to something that is not our
 * scale:
 *
 *  - `TaskWidgetResponsiveSizes` and the breakpoints in `taskWidgetLayoutFor` are the host's cell
 *    grid. Glance offers the set and the launcher picks from it, so the thresholds have to agree
 *    with the sizes offered, and neither end of that is ours to round to a spacing step;
 *  - the `taskWidgetMetrics` table is a cross-platform contract and the file says so. Inset 14, top
 *    13, bottom 11, header 42, spacing 7, row 22 on 3 are the same numbers `WidgetLayoutMetrics`
 *    holds in `ios-swiftUI/TdayWidget/TodayTasksWidget.swift`, which has never heard of
 *    `TdayDimens`; `taskWidgetVisibleRowCount` then divides by them to decide how many rows fit, so
 *    they are a solver's inputs rather than decoration;
 *  - those same three insets are spelled a third time in `layout/widget_*_loading.xml`, because the
 *    static `initialLayout` is what a host shows until the first composition and the handoff is
 *    meant to be invisible.
 *
 * Routing any of that through `TdayDimens` would hand an Android-only rename of the spacing scale
 * the power to move an iOS widget, a RemoteViews handoff and a launcher's grid — which is
 * `WidgetCornerRadiusTest`'s argument about radius, one layer out and with more surfaces on the far
 * side of it.
 *
 * Out of the scale is not out of a budget, though, or "not on the scale" becomes where new literals
 * go. The fourth assertion freezes the subtree's total instead, on a ceiling's terms: it may fall,
 * it may not rise, and a value that should be neither a rung nor frozen has the same way out every
 * other file has — a named `private val Dp`.
 *
 * Two files are carved back out of it by name. `WidgetListConfigurationActivity.kt` and
 * `WidgetCreateTaskActivity.kt` live in that directory because they belong to a widget's plumbing —
 * one configures an instance, the other is the "+" it opens — but each renders Material into an
 * Activity of our own, the picker in the first and in the second the create sheet
 * `ShareReceiverActivity` also presents. Nothing about either is composited by a launcher, so the
 * reason the exemption exists does not reach them, and a directory is the wrong shape for an
 * argument about what draws the pixels. Carving them back in by name says that; widening the
 * exemption to cover them would have said the opposite. They are the whole set, not the two noticed
 * so far: every other file under `widget/` paints through Glance or nothing at all.
 */
class FeatureDimensBudgetTest {

    /**
     * The ceiling is the file's count on the day it was seeded. It may only ever go down, and it
     * goes down in the same commit as the migration that earned it.
     */
    @Test
    fun `should keep every seeded file at or under its anonymous dp ceiling`() {
        val sources = featureSources()
        CEILINGS.forEach { (path, ceiling) ->
            val file = sources[path] ?: return@forEach // a missing key is B's failure, not A's
            val actual = anonymousDpCount(file)
            assertTrue(
                "feature/$path holds $actual anonymous .dp against a ceiling of $ceiling. Put the " +
                    "new value on a TdayDimens rung, or name it as a private val Dp in the file, " +
                    "then LOWER this file's entry in CEILINGS by what you retired, in the same " +
                    "commit. This number only moves down.",
                actual <= ceiling,
            )
        }
    }

    /**
     * A ceiling outliving its file is a budget nobody is spending and nobody can see: the entry
     * reads as work still to do, and the next file to take that path inherits a head start.
     */
    @Test
    fun `should hold no ceiling open for a file that is gone`() {
        val sources = featureSources()
        assertTrue("no .kt found under feature/ — is the source walk broken?", sources.isNotEmpty())
        assertEquals(
            "these CEILINGS keys name no file under feature/. A deleted or moved screen must take " +
                "its ceiling with it",
            emptySet<String>(),
            CEILINGS.keys - sources.keys,
        )
    }

    /**
     * The half that forbids new literals rather than counting old ones. An existing file gets a
     * descending ceiling; a file that is not in the map gets a hard zero — otherwise the cheapest
     * way past assertion A is a new file, and the corpus grows while every number falls.
     */
    @Test
    fun `should let an unseeded feature file carry no anonymous dp at all`() {
        val offenders = featureSources()
            .filterKeys { it !in CEILINGS }
            .mapValues { (_, file) -> anonymousDpCount(file) }
            .filterValues { it > 0 }
        assertEquals(
            "these files are not in CEILINGS, so their budget is zero. Draw them with TdayDimens, " +
                "or name each value as a private val Dp. Seeding a new entry in CEILINGS is not " +
                "the way through: the map is the backlog, and it does not take deposits",
            emptyMap<String, Int>(),
            offenders,
        )
    }

    /**
     * The exemption argues that the widget's dp answer to a launcher, an iOS struct and an XML
     * layout. It does not argue that a new one may answer to nothing, and a directory nothing
     * counts is the cheapest place under `feature/` to put geometry. So the exempt subtree carries
     * a total of its own on a ceiling's terms: the rule that reaches it is different, the ratchet
     * is the same.
     */
    @Test
    fun `should freeze the exempt subtree rather than leave it uncounted`() {
        val exempt = allFeatureSources()
            .filterKeys { it.startsWith("$EXEMPT_SUBTREE/") && it !in EXEMPT_SUBTREE_CARVE_INS }
        assertTrue("nothing left under $EXEMPT_SUBTREE/ — is the source walk broken?", exempt.isNotEmpty())
        val actual = exempt.values.sumOf { anonymousDpCount(it) }
        assertTrue(
            "$EXEMPT_SUBTREE/ holds $actual anonymous .dp against a frozen $EXEMPT_SUBTREE_FROZEN_DP. " +
                "It is exempt from TdayDimens, not from being counted: name the new value as a " +
                "private val Dp, or retire one and LOWER this number in the same commit",
            actual <= EXEMPT_SUBTREE_FROZEN_DP,
        )
    }

    /** Every `.kt` under `feature/`, keyed by its path below `feature/`. */
    private fun allFeatureSources(): Map<String, File> =
        featureDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associateBy { it.relativeTo(featureDir).invariantSeparatorsPath }

    /** The same, minus the exempt subtree and plus the files carved back out of it. */
    private fun featureSources(): Map<String, File> =
        allFeatureSources()
            .filterKeys { !it.startsWith("$EXEMPT_SUBTREE/") || it in EXEMPT_SUBTREE_CARVE_INS }

    /** The counting rule in the class KDoc, in the order it is written there. */
    private fun anonymousDpCount(file: File): Int =
        BLOCK_COMMENT.replace(file.readText()) { match ->
            "\n".repeat(match.value.count { char -> char == '\n' })
        }
            .lineSequence()
            .map { LINE_COMMENT.replace(it, "") }
            .filterNot { NAMED_PRIVATE_DP.containsMatchIn(it) }
            .sumOf { RAW_DP.findAll(it).count() }

    private companion object {
        /** The unit test's working directory is the Gradle module dir, but do not rely on it. */
        val mainDir: File = generateSequence(File(".").canonicalFile) { it.parentFile }
            .flatMap { sequenceOf(File(it, "src/main"), File(it, "app/src/main")) }
            .firstOrNull { File(it, "res").isDirectory }
            ?: error("could not locate app/src/main from ${File(".").canonicalPath}")

        val featureDir: File = File(mainDir, "java/com/ohmz/tday/compose/feature")

        /** Relative to `featureDir`. See "The widget exemption" above. */
        const val EXEMPT_SUBTREE = "widget"

        /** The files inside it that the exemption's reason does not reach. Same heading. */
        val EXEMPT_SUBTREE_CARVE_INS = setOf(
            "widget/WidgetListConfigurationActivity.kt",
            "widget/WidgetCreateTaskActivity.kt",
        )

        /**
         * What the subtree counted on the day the exemption became permanent, measured with the
         * rule above rather than transcribed: 77, every one of them in `TaskWidgetDesign.kt`.
         */
        const val EXEMPT_SUBTREE_FROZEN_DP = 77

        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//.*""")
        val NAMED_PRIVATE_DP = Regex("""^\s*private (const )?val \w+(\s*:\s*Dp)?\s*=\s*[0-9.]+\.dp""")
        val RAW_DP = Regex("""\b\d+(\.\d+)?\.dp\b""")

        /**
         * Seeded by running the rule above over the tree, not transcribed from anywhere. 845 across
         * 19 files; the other 26 files under `feature/` already count zero and are held there by
         * the third assertion.
         *
         * Every one of the 19 is now 0 — 43c spent the last of them, `TodoListScreen.kt`'s 182 —
         * so the map has stopped being a backlog and is doing the third assertion's job for the
         * files it names. The entries stay anyway: a zero here and a zero there are the same
         * ratchet, and deleting them would make the next migration look like it started from
         * nothing rather than from a number somebody drove down.
         */
        val CEILINGS: Map<String, Int> = mapOf(
            "todos/TodoListScreen.kt" to 0,
            "auth/ForgotPasswordPanel.kt" to 0,
            "app/UpdateRequiredOverlay.kt" to 0,
            "app/PendingApprovalOverlay.kt" to 0,
            "auth/ForgotPasswordScreen.kt" to 0,
            "auth/SetSecurityQuestionsGate.kt" to 0,
            "auth/SecurityQuestionPicker.kt" to 0,
            "todos/ManageMembersSheet.kt" to 0,
            "completed/CompletedScreen.kt" to 0,
            "guide/HelpGuideScreen.kt" to 0,
            "settings/SettingsScreen.kt" to 0,
            "release/LatestReleaseScreen.kt" to 0,
            "calendar/CalendarScreen.kt" to 0,
            "scheduledtaskhome/ScheduledTaskHomeScreen.kt" to 0,
            "onboarding/OnboardingWizardOverlay.kt" to 0,
            "car/CarTaskSurfaceScreen.kt" to 0,
            "sweep/MorningSweepScreen.kt" to 0,
            "lock/AppLock.kt" to 0,
            "guide/GuideHelpLink.kt" to 0,
        )
    }
}
