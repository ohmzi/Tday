package com.ohmz.tday.compose.core.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The row-stacking rule, asserted where it can be.
 *
 * jsdom lays nothing out and a JVM unit test has no layout pass either, so nothing
 * here can look at a pixel. What it CAN do is check the arithmetic the layout will
 * be given, which is the whole of the fix: the insets are a pure function of a text
 * style's line box and a control's height, and that function is
 * [taskRowFirstLineAlignment].
 *
 * Two kinds of claim, and they are deliberately different in kind.
 *
 * The ARITHMETIC claims pin literals — 12 dp for the task list, 2 dp for Completed,
 * 7 dp for the car surface. Re-deriving the expected value from the same call would
 * assert `f(x) == f(x)` and pass no matter what `f` became, which is the trap
 * `TdayTaskRowSkeletonTest` names when it writes `48.dp` out by hand rather than
 * reading it back out of the metrics.
 *
 * The INVARIANT claims go the other way: they state the property the whole change
 * exists to guarantee — the control's centre and the first line's centre are the
 * same point — and sweep it across font scales and control sizes, because the one
 * thing a hand-written inset cannot do is stay right when the user turns the text
 * up. That is the sweep an emulator would have been for.
 */
class TaskRowFirstLineAlignmentTest {

    private val density = Density(density = 2f, fontScale = 1f)

    /** The shipped `titleMedium`: 18 sp on a 24 sp line. */
    private val titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp)

    @Test
    fun `the task list's 48 dp toggle drops its title by the 12 dp the row shipped`() {
        // TodoListScreen had `top = TdayDimens.SpacingLg` hand-written under the comment
        // "top pad centres the first title line against the (taller) toggle". That 12 dp
        // is what this call now returns, which is the evidence that the derivation is the
        // existing decision rather than a new one — the one row that was already right
        // does not move by so much as a pixel.
        val alignment = taskRowFirstLineAlignment(density, titleMedium, controlHeight = 48.dp)

        assertEquals(24.dp, alignment.titleLineHeight)
        assertEquals(24.dp, alignment.firstLineCenter)
        assertEquals(12.dp, alignment.titleTopInset)
        // The toggle is the tallest thing in the row, so it is what the line is placed
        // against and it takes no inset of its own.
        assertEquals(0.dp, alignment.topInsetFor(48.dp))
        // The trailing list/priority indicators are 18 dp.
        assertEquals(15.dp, alignment.topInsetFor(18.dp))
    }

    @Test
    fun `Completed's shorter 28 dp toggle asks for a different inset, not the same one`() {
        // The screen in the bug report. Its restore toggle is 28 dp, not 48, so a single
        // constant copied from the task list would have dropped this title 10 dp too far
        // and taken the whole feed's rhythm with it.
        val alignment = taskRowFirstLineAlignment(density, titleMedium, controlHeight = 28.dp)

        assertEquals(14.dp, alignment.firstLineCenter)
        assertEquals(2.dp, alignment.titleTopInset)
        assertEquals(0.dp, alignment.topInsetFor(28.dp))
        assertEquals(5.dp, alignment.topInsetFor(18.dp))
    }

    @Test
    fun `a control SHORTER than the line takes the inset instead of giving it`() {
        // The car surface leads with a 10 dp priority dot. Nothing about the text moves —
        // the dot comes down to meet it. Both directions are the same formula, which is
        // why there is one function and not two.
        val alignment = taskRowFirstLineAlignment(density, titleMedium, controlHeight = 10.dp)

        assertEquals(12.dp, alignment.firstLineCenter)
        assertEquals(0.dp, alignment.titleTopInset)
        assertEquals(7.dp, alignment.topInsetFor(10.dp))
    }

    @Test
    fun `a row whose title line box is overridden is aligned to the override`() {
        // ScheduledTaskHomeScreen writes `lineHeight = 22.sp` at the call site rather than
        // taking titleMedium's 24. A derivation that read the theme would align that row to
        // a line box it is not drawn in — off by 1 dp, forever, invisibly.
        val overridden = titleMedium.copy(lineHeight = 22.sp)
        val alignment = taskRowFirstLineAlignment(density, overridden, controlHeight = 48.dp)

        assertEquals(22.dp, alignment.titleLineHeight)
        assertEquals(13.dp, alignment.titleTopInset)
    }

    @Test
    fun `the inset tracks the font scale, which is the whole reason it is derived`() {
        // A 48 dp control is dp and a 24 sp line is sp, so the gap between them is not a
        // constant — it closes as the user turns the text up and is gone entirely before
        // 3x. The hand-written 12 dp was wrong by 2 dp at 1.5x, by 6 at 2x and by 12 at 3x,
        // in the accessibility setting where being wrong shows most.
        //
        // The numbers are also not `24 * scale`, and that is the point of asserting them
        // rather than computing them: since Android 14, sp above 1x is converted through a
        // NON-LINEAR curve, so 24 sp at 1.5x is 28 dp and not 36. Anybody deriving this by
        // hand would have got the linear answer, and would have been wrong by 4 dp at the
        // one scale that is easiest to reach for by hand.
        val expected = mapOf(
            0.85f to 13.8f,
            1f to 12f,
            1.15f to 11.4f,
            1.3f to 10.8f,
            1.5f to 10f,
            2f to 6f,
            3f to 0f,
        )
        expected.forEach { (scale, inset) ->
            val alignment = taskRowFirstLineAlignment(
                Density(density = 2f, fontScale = scale),
                titleMedium,
                controlHeight = 48.dp,
            )
            assertEquals("at fontScale $scale", inset, alignment.titleTopInset.value, 0.01f)
        }
    }

    @Test
    fun `the control's centre and the first line's centre are always the same point`() {
        // The property the fix exists to guarantee, swept rather than sampled. If this
        // holds, a single-line row cannot have moved either: with one line, the text
        // column's centre IS its first line's centre, so centring the control on one is
        // exactly what `Alignment.CenterVertically` was already doing.
        val controls = listOf(10.dp, 18.dp, 24.dp, 28.dp, 38.dp, 48.dp)
        val scales = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f)
        for (control in controls) {
            for (scale in scales) {
                val alignment = taskRowFirstLineAlignment(
                    Density(density = 2f, fontScale = scale),
                    titleMedium,
                    controlHeight = control,
                )
                val controlCenter = alignment.topInsetFor(control) + control / 2
                val firstLineCenter = alignment.titleTopInset + alignment.titleLineHeight / 2
                assertNear(
                    "control ${control.value} dp at fontScale $scale",
                    firstLineCenter,
                    controlCenter,
                )
            }
        }
    }

    @Test
    fun `a trailing indicator lands on the same centre the control does`() {
        // The flag in the screenshot. Deciding it separately is how a half-done fix
        // happens: the toggle moves to line one and the flag stays in the gap.
        val controls = listOf(28.dp, 48.dp)
        val indicators = listOf(13.dp, 14.dp, 18.dp, 24.dp)
        for (control in controls) {
            val alignment = taskRowFirstLineAlignment(density, titleMedium, control)
            for (indicator in indicators) {
                assertNear(
                    "indicator ${indicator.value} dp beside a ${control.value} dp control",
                    alignment.firstLineCenter,
                    alignment.topInsetFor(indicator) + indicator / 2,
                )
            }
        }
    }

    @Test
    fun `nothing is ever asked to hang above the row's top edge`() {
        // `topInsetFor` is the only place a negative padding could have been produced, and
        // Compose throws on one rather than clamping. A 38 dp indicator beside a 24 sp line
        // is not a shape any row draws today — it is the shape the next row might.
        val alignment = taskRowFirstLineAlignment(density, titleMedium, controlHeight = 24.dp)
        assertEquals(0.dp, alignment.topInsetFor(38.dp))
        assertTrue(alignment.topInsetFor(96.dp) >= 0.dp)
    }

    @Test
    fun `a theme that spells its line height in em falls back rather than throwing`() {
        // `TextUnit.toDp()` throws on anything that is not sp, and a row that crashed the
        // feed it is stacking would be a poor trade for exact arithmetic. Same argument,
        // and the same fallback, as `SkeletonTextBar` two functions down.
        val em = TextStyle(fontSize = 18.sp, lineHeight = 1.4.em)
        val alignment = taskRowFirstLineAlignment(density, em, controlHeight = 48.dp)
        assertEquals(24.dp, alignment.titleLineHeight)
        assertEquals(12.dp, alignment.titleTopInset)
    }

    /**
     * Which rows are stacked this way — the structural half of the claim.
     *
     * The arithmetic above is worth nothing if a screen quietly goes back to centring, and
     * "everywhere" was the whole of the request: a fix applied to the screen in the report
     * and to no other leaves the same defect in eight more places. So the rows are pinned
     * by file AND BY COUNT, which is the part a membership test cannot do: `CalendarScreen`
     * draws two of these rows, and a file-level `contains("Alignment.Top")` is satisfied by
     * the first of them no matter what the second does. That is not hypothetical — it is
     * how a half-converted completed row shipped past this test once already.
     */
    @Test
    fun `every task row in the app stacks against its title's first line`() {
        val expected = mapOf(
            "core/ui/TdayTaskRowSkeleton.kt" to 1,
            "feature/todos/TodoListScreen.kt" to 3,
            "feature/completed/CompletedScreen.kt" to 1,
            "feature/calendar/CalendarScreen.kt" to 2,
            "feature/scheduledtaskhome/ScheduledTaskHomeScreen.kt" to 1,
            "feature/car/CarTaskSurfaceScreen.kt" to 1,
        )
        assertEquals(
            "a task row either derives its first line or it does not draw one, and a file " +
                "that gains or loses one says so here. Add the new row, or take the retired " +
                "one out, in the same commit",
            expected,
            rowBlocks().groupingBy { it.label.substringBefore(':') }.eachCount(),
        )
        rowBlocks().forEach { row ->
            assertTrue(
                "${row.label} derives a first line and then stacks its row centred anyway",
                row.text.contains("verticalAlignment = Alignment.Top"),
            )
        }
    }

    /**
     * The leading control is inset too, and this is the half the first cut left out.
     *
     * Only the TEXT was ever given an inset, which is exactly right while the control is
     * the taller of the two and silently wrong the moment it is not: a 28 dp toggle beside
     * a line box that an accessibility font scale has grown to 36 dp is the row's SHORTEST
     * element, and an un-inset one sits 4 dp above the line it is supposed to be the bullet
     * for — above its own trailing flag, too, which did get the call. `topInsetFor` is 0 dp
     * in the common case, so asking for it costs nothing and closes the one direction the
     * row could not otherwise express.
     *
     * Asserted against the control the row DERIVED against, by name: a row that aligns to a
     * 48 dp target and then insets a 24 dp glyph has answered a different question.
     */
    @Test
    fun `every row insets the control it derived its line against`() {
        rowBlocks().forEach { row ->
            val control = CONTROL_HEIGHT.find(row.text)?.groupValues?.get(1)
                ?: error("${row.label} derives a first line without naming a control height")
            assertTrue(
                "${row.label} derives its line against $control and then never puts " +
                    "$control on it — `topInsetFor($control)` is 0 dp at fontScale 1, and " +
                    "is the whole fix above it",
                row.text.contains("topInsetFor($control)"),
            )
        }
    }

    /**
     * Every trailing mark in those rows is on the same line, per MARK and not per file.
     *
     * A trailing mark is found structurally rather than by eye: a `padding(` with an `end`
     * component, on an element whose own call mentions one of the `…TrailingIconSize`
     * tokens. That picks out the list/priority containers and nothing else — a row's own
     * `horizontal`/`vertical` padding has no `end`, and the swipe tray's `end` padding
     * belongs to a call that sizes no mark.
     *
     * The count is pinned for the reason the row count is: a scan that silently matched
     * nothing would pass this test every time.
     */
    @Test
    fun `every trailing mark in those rows is dropped onto the same line`() {
        var checked = 0
        rowBlocks().forEach { row ->
            paddingGroups(row.text).forEach { group ->
                if (!group.text.contains("end =")) return@forEach
                val token = TRAILING_TOKEN.find(enclosingCall(row.text, group.start))
                    ?: return@forEach
                checked++
                assertTrue(
                    "${row.label} stacks from the top and then leaves a ${token.value} mark " +
                        "at the row's top edge. It is an annotation on the title, so it takes " +
                        "`top = firstLine.topInsetFor(${token.value})` like the toggle does",
                    group.text.contains("topInsetFor("),
                )
            }
        }
        assertEquals(
            "the trailing-mark scan found a different number of marks than the tree has. " +
                "Update the count with the row that gained or lost one",
            7,
            checked,
        )
    }

    /** One row's worth of source: its derivation, and everything up to the next row's. */
    private data class RowBlock(val label: String, val text: String)

    /** A `padding(...)` argument list, and where it starts inside its row block. */
    private data class SourceRange(val start: Int, val text: String)

    /**
     * Every row in the tree, one entry each — NOT one entry per file.
     *
     * A row begins where it derives its first line and ends where the next row derives
     * its own, which is what makes the assertions above per-row properties. Only the
     * `val … = remember…` call form opens a block, so the declaration of
     * `rememberTaskRowFirstLineAlignment` itself does not read as a row.
     */
    private fun rowBlocks(): List<RowBlock> = composeSources()
        .filterValues { it.readText().contains("rememberTaskRowFirstLineAlignment(") }
        .flatMap { (path, file) ->
            val text = file.readText()
            val starts = DERIVATION.findAll(text).map { it.range.first }.toList()
            starts.mapIndexed { index, start ->
                RowBlock(
                    label = "$path:${text.take(start).count { it == '\n' } + 1}",
                    text = text.substring(start, starts.getOrNull(index + 1) ?: text.length),
                )
            }
        }

    /** Every `padding(` argument list in [text], balanced rather than line-matched. */
    private fun paddingGroups(text: String): List<SourceRange> {
        val groups = mutableListOf<SourceRange>()
        var from = 0
        while (true) {
            val open = text.indexOf("padding(", from)
            if (open < 0) return groups
            val close = matchForward(text, open + "padding(".length - 1)
            groups += SourceRange(open, text.substring(open, close))
            from = close
        }
    }

    /**
     * The call that owns the argument list [at] sits in — its arguments and, when it has
     * one, its trailing lambda. That is what makes "an element that sizes a trailing mark"
     * a structural question rather than a guess about how far away the mark is written.
     */
    private fun enclosingCall(text: String, at: Int): String {
        var depth = 0
        var index = at - 1
        while (index >= 0) {
            val char = text[index]
            if (char == ')') {
                depth++
            } else if (char == '(') {
                if (depth == 0) break
                depth--
            }
            index--
        }
        if (index < 0) return ""
        val argsEnd = matchForward(text, index)
        var body = argsEnd
        while (body < text.length && text[body].isWhitespace()) body++
        if (body >= text.length || text[body] != '{') return text.substring(index, argsEnd)
        var braces = 1
        var end = body + 1
        while (braces > 0 && end < text.length) {
            when (text[end]) {
                '{' -> braces++
                '}' -> braces--
            }
            end++
        }
        return text.substring(index, end)
    }

    /** The index just past the `)` that closes the `(` at [open]. */
    private fun matchForward(text: String, open: Int): Int {
        var depth = 1
        var index = open + 1
        while (depth > 0 && index < text.length) {
            when (text[index]) {
                '(' -> depth++
                ')' -> depth--
            }
            index++
        }
        return index
    }

    private fun assertNear(message: String, expected: Dp, actual: Dp) {
        assertEquals(message, expected.value, actual.value, 0.001f)
    }

    private companion object {
        /** Only the call form opens a row block — never the function's own declaration. */
        val DERIVATION = Regex("""val\s+\w+\s*=\s*rememberTaskRowFirstLineAlignment\(""")
        val CONTROL_HEIGHT = Regex("""controlHeight\s*=\s*([\w.]+)""")
        val TRAILING_TOKEN = Regex("""\b\w*TrailingIconSize\b""")
    }

    private fun composeSources(): Map<String, File> {
        val root = generateSequence(File(".").canonicalFile) { it.parentFile }
            .map { File(it, "app/src/main/java/com/ohmz/tday/compose") }
            .firstOrNull { it.isDirectory }
            ?: error("could not locate the compose source root from ${File(".").canonicalPath}")
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associateBy { it.relativeTo(root).invariantSeparatorsPath }
    }
}
