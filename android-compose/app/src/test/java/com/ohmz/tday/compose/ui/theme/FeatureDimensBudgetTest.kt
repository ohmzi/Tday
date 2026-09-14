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
 * is PR 43c…43n, one screen file per session. This test is what makes that a ratchet instead of a
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
 * `feature/widget/` is out of the walk. Its dp are RemoteViews metrics — the Glance runtime and the
 * layouts it replaces have to agree with `res/values/dimens.xml`, which `WidgetCornerRadiusTest`
 * already pins, and `TdayDimens` is not the layer that owns them. TODO(43n): that unit decides
 * whether the exemption is permanent or whether the widget gets a scale of its own.
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

    /** Every `.kt` under `feature/` except the exempt subtree, keyed by its path below `feature/`. */
    private fun featureSources(): Map<String, File> =
        featureDir.walkTopDown()
            .onEnter { it.relativeTo(featureDir).invariantSeparatorsPath != EXEMPT_SUBTREE }
            .filter { it.isFile && it.extension == "kt" }
            .associateBy { it.relativeTo(featureDir).invariantSeparatorsPath }

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

        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//.*""")
        val NAMED_PRIVATE_DP = Regex("""^\s*private (const )?val \w+(\s*:\s*Dp)?\s*=\s*[0-9.]+\.dp""")
        val RAW_DP = Regex("""\b\d+(\.\d+)?\.dp\b""")

        /**
         * Seeded by running the rule above over the tree, not transcribed from anywhere. 845 across
         * 19 files; the other 26 files under `feature/` already count zero and are held there by
         * the third assertion.
         */
        val CEILINGS: Map<String, Int> = mapOf(
            "todos/TodoListScreen.kt" to 182,
            "scheduledtaskhome/ScheduledTaskHomeScreen.kt" to 116,
            "onboarding/OnboardingWizardOverlay.kt" to 85,
            "release/LatestReleaseScreen.kt" to 77,
            "settings/SettingsScreen.kt" to 55,
            "guide/HelpGuideScreen.kt" to 49,
            "todos/ManageMembersSheet.kt" to 41,
            "completed/CompletedScreen.kt" to 37,
            "car/CarTaskSurfaceScreen.kt" to 21,
            "sweep/MorningSweepScreen.kt" to 16,
            "auth/ForgotPasswordPanel.kt" to 13,
            "app/UpdateRequiredOverlay.kt" to 10,
            "app/PendingApprovalOverlay.kt" to 9,
            "auth/ForgotPasswordScreen.kt" to 7,
            "auth/SetSecurityQuestionsGate.kt" to 7,
            "auth/SecurityQuestionPicker.kt" to 3,
            "lock/AppLock.kt" to 3,
            "guide/GuideHelpLink.kt" to 2,
            "calendar/CalendarScreen.kt" to 0,
        )
    }
}
