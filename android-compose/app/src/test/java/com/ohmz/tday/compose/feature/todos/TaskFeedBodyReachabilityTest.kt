package com.ohmz.tday.compose.feature.todos

import com.ohmz.tday.compose.core.model.TodoListMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ratchet on guards in `TodoListScreen.kt` that enumerate every
 * [TodoListMode] — the shape that grew the Phase 9 skeleton's dead branch, and
 * then grew a second one beside it.
 *
 * ## What went wrong, and why a comment was not enough to stop it
 *
 * `showSectionedTimeline` was declared as a disjunction over all seven modes. It
 * reads as a mode filter, so nobody rereads it; it evaluates as `true`, so its
 * `else` can never run. Two bodies hung off that `else`:
 *
 *  * the Phase 9 task-row skeleton, gated on `!showSectionedTimeline && …`. Never
 *    drawn, on any scope, since it landed. Fixed by giving it the state it had
 *    always meant — `FeedAnswer.AwaitingFirst` — which is what a placeholder is
 *    actually for;
 *  * `flatTodoRowsContent`, the flat unsectioned `items(…)` body, gated on
 *    `!showSectionedTimeline` alone. Deleted, because unlike the skeleton it had
 *    no real state behind it at all: its guard WAS the mode filter, and the scope
 *    it served does not exist.
 *
 * The first fix landed with the tautology written out in a comment beside it. That
 * comment was correct, was several paragraphs long, and did not prevent the
 * identical defect sitting thirty lines further down in the same `LazyColumn` from
 * surviving the same commit. Prose next to one occurrence does not find the
 * others. This does.
 *
 * ## The rule, and where it deliberately stops
 *
 * Scanned: `val <name> = <chain>` in `TodoListScreen.kt`, where `<chain>` is one
 * or more `mode == TodoListMode.X` joined by `||`. If the chain names every value
 * of the enum, it is a constant and it must be in [SEEDED].
 *
 * A local `val` is the line, and it is a line with a reason rather than a
 * convenience. A guard that is a constant **in the function a reader is reading**
 * is dead code they can see and must not find; a guard that arrives as a
 * PARAMETER is merely unexercised by today's callers, and reads as a genuine
 * option until you trace three call sites out of the file. Those are different
 * defects with different fixes, and a test that conflated them would either be
 * unsatisfiable or have to exempt the thing it exists to catch.
 *
 * ## The one seeded entry
 *
 * `usesTodayStyle` is the same tautology, still standing. It is seeded rather than
 * removed, and the cost of removing it is why:
 *
 *  * it is threaded into `sectionedTimelineContent` and on into `TdaySectionHeader`
 *    and `TimelineTaskRow` as `useMinimalStyle`, so retiring the constant retires
 *    a parameter from four functions;
 *  * doing so makes `TimelineTaskRow`'s tail unreachable in the open, which means
 *    deleting `TodayTodoRow` and `TodoRow` — two row composables, one of which is
 *    the shape `TdayTaskRowSkeleton` was originally derived against — and the
 *    non-minimal branch of the section header's styling;
 *  * that is a restyle, on a screen with no Compose UI tests and no device on this
 *    machine to check one against. It is the same argument `TodoListScreen`'s own
 *    KT-R1006 note makes about splitting its state derivation further: a riskier
 *    shape of change, deliberately not rushed into the commit that found it.
 *
 * So it gets a ceiling instead of a pass. [SEEDED] is a backlog, not a permit: an
 * entry may leave it, nothing may join it, and an entry that no longer names a
 * `val` in the file has to leave in the same commit that retires the `val`.
 *
 * ## What the seed does not cover
 *
 * A seeded `val` is tolerated; a dead BODY hanging off one is not, and the two
 * were confused once already. The feed's `LazyColumn` carried a third
 * `contentPadding` arm behind `usesTodayStyle ->`, unreachable for the same
 * reason the skeleton's branch was, three lines above the comment explaining
 * that a locally-constant `else` right there had been dead. It has been taken,
 * because none of the costs listed above reaches it: it retired no parameter,
 * deleted no composable and moved no pixel — four lines and one dimension
 * constant that had no other reference. Written down so the next reader who
 * greps `usesTodayStyle`, finds it still standing, and goes looking for what
 * hangs off it does not re-find that arm as new. What is left are two
 * `else if (usesTodayStyle)` sites with no trailing `else`: a condition that is
 * constant, but no body behind it for a reader to mistake for live code.
 */
class TaskFeedBodyReachabilityTest {

    /**
     * The compiler's half, and the one that outlives this file's regexes.
     *
     * Every scope draws the sectioned timeline — that is what makes the deleted
     * flat body unreachable, so it is asserted where it can fail rather than
     * restated where it cannot. The `when` has no `else` on purpose: adding an
     * eighth [TodoListMode] stops this file compiling, which puts whoever adds it
     * in front of the question "does the new scope draw the sectioned timeline?"
     * before they can ship a screen that silently assumed yes.
     */
    @Test
    fun `every scope draws the sectioned timeline, so a flat body has no scope to serve`() {
        TodoListMode.entries.forEach { mode ->
            val drawsSectionedTimeline = when (mode) {
                TodoListMode.TODAY,
                TodoListMode.OVERDUE,
                TodoListMode.SCHEDULED,
                TodoListMode.ALL,
                TodoListMode.PRIORITY,
                TodoListMode.FLOATER,
                TodoListMode.LIST,
                -> true
            }
            assertTrue(
                "$mode does not draw the sectioned timeline. The flat `items` body that used " +
                    "to serve a scope like this one was deleted as unreachable — reinstate it " +
                    "deliberately, with a guard that is not a mode disjunction over the whole " +
                    "enum, rather than letting this scope fall through to a body built for " +
                    "day and priority headers",
                drawsSectionedTimeline,
            )
        }
    }

    /**
     * The half that catches the next one. A new all-modes `val` is a new dead
     * branch waiting for a body, and this is the assertion that makes writing one
     * cost something at the moment it is written.
     */
    @Test
    fun `should let no new local guard enumerate every TodoListMode`() {
        val found = exhaustiveModeGuards()
        assertEquals(
            "these `val`s in TodoListScreen.kt name every TodoListMode, so each is a constant " +
                "`true` wearing the shape of a mode filter, and any `else` hanging off one is " +
                "dead the moment it is written. Delete the guard and inline what it always " +
                "answers, or narrow it to the modes it actually means. Adding a name to SEEDED " +
                "is not the way through: that map is the backlog, and it does not take deposits",
            SEEDED,
            found,
        )
    }

    /**
     * A seed outliving its `val` reads as debt somebody still owes, and the next
     * reader spends time looking for work that is already done. Same shape as
     * `FeatureDimensBudgetTest`'s "no ceiling for a file that is gone".
     */
    @Test
    fun `should hold no seed open for a guard that is gone`() {
        val source = todoListScreenSource()
        val stale = SEEDED.filterNot { name -> Regex("""\bval\s+$name\b""").containsMatchIn(source) }
        assertEquals(
            "these SEEDED names declare no `val` in TodoListScreen.kt. A guard that has been " +
                "retired must take its seed with it, in the same commit",
            emptySet<String>(),
            stale.toSet(),
        )
    }

    /**
     * Proof that the scan is looking at something. A regex that silently matches
     * nothing passes assertion two forever, which is the failure mode a source
     * scan has and a unit test does not.
     */
    @Test
    fun `should find the mode guards it is scanning for at all`() {
        val source = todoListScreenSource()
        assertTrue(
            "no `val … = mode == TodoListMode.…` chain found in TodoListScreen.kt at all — the " +
                "scan below has stopped matching the file it is pointed at, and every other " +
                "assertion here is passing on an empty set",
            MODE_GUARD.containsMatchIn(stripComments(source)),
        )
    }

    /** The names of every `val` guard in the file that names all seven modes. */
    private fun exhaustiveModeGuards(): Set<String> {
        val allModes = TodoListMode.entries.map { it.name }.toSet()
        return MODE_GUARD.findAll(stripComments(todoListScreenSource()))
            .filter { match ->
                MODE_NAME.findAll(match.groupValues[2])
                    .map { it.groupValues[1] }
                    .toSet() == allModes
            }
            .map { it.groupValues[1] }
            .toSet()
    }

    private fun todoListScreenSource(): String = TODO_LIST_SCREEN.readText()

    /**
     * Comments first, for `FeatureDimensBudgetTest`'s reason: the argument for
     * why a guard was retired quotes the guard while arguing, and an argument
     * against something must not be counted as the thing.
     */
    private fun stripComments(source: String): String =
        LINE_COMMENT.replace(BLOCK_COMMENT.replace(source, " "), "")

    private companion object {
        /** The unit test's working directory is the Gradle module dir, but do not rely on it. */
        val mainDir: File = generateSequence(File(".").canonicalFile) { it.parentFile }
            .flatMap { sequenceOf(File(it, "src/main"), File(it, "app/src/main")) }
            .firstOrNull { File(it, "res").isDirectory }
            ?: error("could not locate app/src/main from ${File(".").canonicalPath}")

        val TODO_LIST_SCREEN: File = File(
            mainDir,
            "java/com/ohmz/tday/compose/feature/todos/TodoListScreen.kt",
        )

        /**
         * The one survivor. See "The one seeded entry" above for what retiring it
         * costs and why that is a separate change; this set may shrink and may
         * never grow.
         */
        val SEEDED = setOf("usesTodayStyle")

        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")

        /**
         * `val NAME = <receiver>.mode == TodoListMode.X || … `, across lines. Group
         * 1 is the name, group 2 the whole chain; [MODE_NAME] then reads the modes
         * out of group 2, because a Kotlin regex keeps only the last repetition of
         * a group and the chain has to be re-scanned to see all of them.
         */
        val MODE_GUARD = Regex(
            """val\s+(\w+)\s*=\s*""" +
                """((?:\w+\.)*mode\s*==\s*TodoListMode\.\w+""" +
                """(?:\s*\|\|\s*(?:\w+\.)*mode\s*==\s*TodoListMode\.\w+)*)""",
        )

        val MODE_NAME = Regex("""TodoListMode\.(\w+)""")
    }
}
