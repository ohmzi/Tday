package com.ohmz.tday.compose.ui.component

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How tall the create sheet stands while the keyboard is on its way in or out.
 *
 * The sheet used to read the IME as a yes/no — `WindowInsets.ime.getBottom(density) > 0`
 * — and swap its whole modifier chain on the answer. The keyboard's inset is not a
 * yes/no: the platform animates it up over roughly 250 ms and publishes it every frame.
 * Watching only the crossing means the sheet learns exactly one thing about the keyboard,
 * on the frame the first pixel of it appears, and takes its entire trip to 85 % of the
 * screen in that single frame while the keyboard still has 249 ms of travel left. The
 * keyboard then slides up past an already-tall sheet, which is the leap.
 *
 * The mapping below is the fix, and the only property that matters about it is that it is
 * **continuous**: one dp more keyboard is one dp more sheet, so the sheet arrives when the
 * keyboard arrives and there is no frame on which it moves further than the keyboard did.
 * Feeding it the live inset is what puts the sheet on the keyboard's clock; an
 * `animateDpAsState` around the old boolean would instead have started a second, 320 ms
 * clock at the crossing and raced the platform's, which reads as a laggy follow rather
 * than a leap — a different defect, not a fix. (It would also be a dead animation: the
 * target is a `const val` fraction of the screen height, which is what
 * `and-create-sheet-dead-keyboard-height-animation` already removed once.)
 *
 * Growing by the inset itself, rather than by a fraction of some assumed keyboard size, is
 * also the physically honest quantity: the sheet is anchored to the bottom of the screen,
 * so every dp the keyboard takes from the bottom is a dp the sheet has to grow by to keep
 * the same amount of form above it. No nominal keyboard height has to be guessed at, and a
 * split, floating or resized keyboard is handled by the same arithmetic.
 */
internal object CreateSheetImeHeight {

    /**
     * The height the sheet is held to when the keyboard is [imeHeight] up the screen.
     *
     * @param restingHeight what the sheet stands at with no keyboard — the modal's own
     *   minimum. Returned unchanged at [imeHeight] `== 0.dp`, so a closed keyboard leaves
     *   the resting layout exactly as it was.
     * @param imeHeight the live `WindowInsets.ime` bottom inset, in dp. Negative values
     *   are clamped away rather than shrinking the sheet.
     * @param keyboardHeight the ceiling the sheet stops growing at — the height the old
     *   boolean branch teleported to. It is a cap now instead of a destination.
     */
    fun sheetHeightFor(
        restingHeight: Dp,
        imeHeight: Dp,
        keyboardHeight: Dp,
    ): Dp {
        val risen = restingHeight + imeHeight.coerceAtLeast(0.dp)
        // A modal whose resting height is already taller than the cap is never shrunk by
        // the keyboard appearing; the cap only ever limits growth.
        val ceiling = maxOf(restingHeight, keyboardHeight)
        return minOf(risen, ceiling)
    }
}
