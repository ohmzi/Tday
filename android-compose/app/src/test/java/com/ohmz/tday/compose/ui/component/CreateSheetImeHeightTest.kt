package com.ohmz.tday.compose.ui.component

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The create sheet's height while the keyboard moves.
 *
 * The defect this pins is not a wrong number, it is a wrong *shape*: the sheet read the IME
 * as a boolean and swapped its whole modifier chain on the crossing, so it made its entire
 * trip to 85 % of the screen on the one frame the keyboard's first pixel appeared and then
 * sat still for the remaining ~250 ms of the keyboard's travel. Nothing about the endpoints
 * was wrong — 85 % is still where it ends up — so a test that checks endpoints would have
 * passed against the defect.
 *
 * What separates the two is continuity in the threshold region, which is what these
 * assertions are about: at one dp of keyboard the sheet has moved one dp, not 248.
 */
class CreateSheetImeHeightTest {

    // A 800 dp-tall screen, with the sheet's own fractions from CreateTaskBottomSheet.kt.
    private val screenHeight = 800.dp
    private val maxSheetHeight = screenHeight * 0.86f
    private val keyboardHeight = (screenHeight * 0.85f).coerceAtMost(maxSheetHeight)
    private val createResting = (screenHeight * 0.54f).coerceAtMost(maxSheetHeight)
    private val editResting = (screenHeight * 0.76f).coerceAtMost(maxSheetHeight)

    /** The travel of a plausible soft keyboard, comfortably past where the cap bites. */
    private val fullKeyboard = 320.dp

    private fun heightAt(resting: Dp, ime: Dp): Dp =
        CreateSheetImeHeight.sheetHeightFor(
            restingHeight = resting,
            imeHeight = ime,
            keyboardHeight = keyboardHeight,
        )

    private fun assertDp(expected: Dp, actual: Dp) {
        assertEquals(expected.value, actual.value, 0.001f)
    }

    @Test
    fun `a closed keyboard leaves every modal at exactly its resting height`() {
        // The resting layout is not a thing this change is allowed to move: with no IME the
        // sheet has to measure precisely as it did before.
        assertDp(createResting, heightAt(createResting, 0.dp))
        assertDp(editResting, heightAt(editResting, 0.dp))
        assertDp(0.dp, heightAt(0.dp, 0.dp))
    }

    @Test
    fun `the first frame of keyboard moves the sheet by one frame of keyboard`() {
        // This is the old boolean's cutoff. It used to answer `keyboardHeight` here — the
        // whole 248 dp — on an inset of a single dp.
        assertDp(createResting + 1.dp, heightAt(createResting, 1.dp))
        assertDp(createResting + 2.dp, heightAt(createResting, 2.dp))
        assertDp(editResting + 1.dp, heightAt(editResting, 1.dp))

        val oldLeap = keyboardHeight - createResting
        assertTrue(
            "the leap being fixed has to be big enough to see, or continuity proves nothing",
            oldLeap > 200.dp,
        )
        assertTrue(
            "one dp of keyboard must not still produce the old boolean's answer",
            heightAt(createResting, 1.dp) < keyboardHeight,
        )
    }

    @Test
    fun `the sheet never moves further in a frame than the keyboard did`() {
        for (resting in listOf(createResting, editResting, 0.dp)) {
            var previousIme = 0.dp
            var previousHeight = heightAt(resting, 0.dp)
            var step = 0.5f
            while (step <= fullKeyboard.value) {
                val ime = step.dp
                val height = heightAt(resting, ime)
                val sheetMoved = height - previousHeight
                val keyboardMoved = ime - previousIme
                assertTrue(
                    "sheet went backwards at ime=$ime for resting=$resting",
                    sheetMoved >= 0.dp,
                )
                assertTrue(
                    "sheet moved $sheetMoved while the keyboard moved $keyboardMoved at ime=$ime",
                    sheetMoved.value <= keyboardMoved.value + 0.001f,
                )
                previousIme = ime
                previousHeight = height
                step += 0.5f
            }
        }
    }

    @Test
    fun `the keyboard height is a ceiling growth stops at, not a destination it jumps to`() {
        // Reached only after the keyboard has actually travelled the whole difference.
        val travelToCap = keyboardHeight - createResting
        assertTrue(heightAt(createResting, travelToCap - 1.dp) < keyboardHeight)
        assertDp(keyboardHeight, heightAt(createResting, travelToCap))
        assertDp(keyboardHeight, heightAt(createResting, fullKeyboard))
        assertDp(keyboardHeight, heightAt(editResting, fullKeyboard))
    }

    @Test
    fun `retraction retraces the same heights it rose through`() {
        // The dismissal half of the device check: the sheet comes down the same curve the
        // keyboard takes it up, because the height is a function of the inset and of
        // nothing else — no stored "was open" state, no direction-dependent branch.
        val rising = generateSequence(0f) { it + 4f }
            .takeWhile { it <= fullKeyboard.value }
            .map { heightAt(createResting, it.dp) }
            .toList()
        val falling = generateSequence(fullKeyboard.value) { it - 4f }
            .takeWhile { it >= 0f }
            .map { heightAt(createResting, it.dp) }
            .toList()
        assertEquals(rising.size, falling.size)
        rising.zip(falling.reversed()).forEach { (up, down) -> assertDp(up, down) }
        assertDp(createResting, falling.last())
    }

    @Test
    fun `a modal already taller than the ceiling is not shrunk by the keyboard appearing`() {
        val tallResting = keyboardHeight + 20.dp
        assertDp(tallResting, heightAt(tallResting, 0.dp))
        assertDp(tallResting, heightAt(tallResting, fullKeyboard))
    }

    @Test
    fun `a negative inset is clamped away rather than eating into the sheet`() {
        // Some devices report a transient negative bottom inset mid-animation; it must not
        // pull the sheet below the height it is resting at.
        assertDp(createResting, heightAt(createResting, (-40).dp))
    }
}
