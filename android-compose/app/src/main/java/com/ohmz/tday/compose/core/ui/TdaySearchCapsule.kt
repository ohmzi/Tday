package com.ohmz.tday.compose.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.ui.theme.TdayDimens
import kotlin.math.abs

/**
 * The app's search field, in its open state.
 *
 * The root feeds fold theirs down into a round button and so own its width and
 * placement themselves ([RootFeedHeroHeader]). Everywhere else takes it at full
 * width: the guide in ordinary flow, a list's detail screen in its pinned
 * toolbar row. The chrome is the same in all three, and the numbers come from
 * [RootFeedHeroHeaderMetrics] so they cannot drift apart.
 *
 * @param onClear shown as a trailing button only while [value] is non-empty.
 *   The root feeds' equivalent X dismisses a field that has a folded state to
 *   return to; this one has none, so it clears the text instead.
 */
@Composable
fun TdaySearchCapsule(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
    /**
     * Accessible name for the trailing X. It means two different things depending
     * on which callback is set — "Cancel search" where [onClose] dismisses the
     * field, "Clear" where [onClear] only empties it — so it is named for the
     * button rather than for either job, and callers must pass the one that
     * matches what their X actually does.
     */
    trailingContentDescription: String? = null,
    /**
     * Set by a bar the field has taken over, where the X is the only way back
     * out: it is then always on screen and leaves the search altogether, as the
     * root feeds' capsule does. Left null by a field with nowhere to go, whose X
     * appears only alongside text and only clears it.
     */
    onClose: (() -> Unit)? = null,
) {
    val m = RootFeedHeroHeaderMetrics
    val colorScheme = MaterialTheme.colorScheme
    val capsuleShape = RoundedCornerShape(m.BarButtonSize / 2)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(m.BarButtonSize),
        shape = capsuleShape,
        // The same fill and lift as every other bar control — a dynamic light
        // scheme makes `surface` and `background` the same value, which left
        // this field showing as nothing but its own hairline.
        colors = CardDefaults.cardColors(containerColor = tdayBarButtonContainerColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = TdayDimens.BarButtonElevation),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(capsuleShape)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_search),
                contentDescription = null,
                tint = colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
            BasicTextField(
                modifier = Modifier.weight(1f),
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                ),
                cursorBrush = SolidColor(colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isBlank()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (onClose != null || (onClear != null && value.isNotEmpty())) {
                // The root feeds' trailing control down to the numbers — 30 box,
                // 18 glyph, the same dimmed tint — minus its dismiss semantics,
                // since there is no folded state here to dismiss to. It sat at
                // 32/20 and full strength, which beside the other field read as
                // a heavier X in a field that is otherwise the same chrome.
                IconButton(
                    onClick = onClose ?: onClear ?: {},
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_x),
                        contentDescription = trailingContentDescription,
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * Closes an open scoped search when the user taps the content behind it.
 *
 * The root feeds have done this since they got their field; every screen with a
 * pinned toolbar now does it too. The toolbar is an overlay drawn on top of the
 * same box, not a sibling above it, so "outside" has to be measured — hence
 * [barHeightPx], the toolbar row's own fixed height. Reading a reported rect
 * instead would let the very tap that opened the field register as an outside
 * tap on the frame before the bar has moved.
 *
 * Never consumes anything: a row still has to take its own tap and the feed
 * still has to scroll. Both happen, and the field goes away behind them.
 */
fun Modifier.tdayClosesSearchOnOutsideTap(
    isSearchOpen: Boolean,
    barHeightPx: Float,
    close: () -> Unit,
): Modifier = if (!isSearchOpen) {
    this
} else {
    this.pointerInput(barHeightPx) {
        awaitEachGesture {
            // INITIAL pass, and `requireUnconsumed = false`. A row's own `clickable`
            // consumes the down on the Main pass, so by Final there is nothing left to
            // see — which is every tap this is here to catch. Watching the Initial pass
            // gets us the down before any child has had it.
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val startY = down.position.y
            // Drained on FINAL and never consumed: the row still takes its tap, the page
            // still scrolls, and the field goes away behind whichever of those happened.
            var travel = 0f
            var event: PointerEvent
            do {
                event = awaitPointerEvent(PointerEventPass.Final)
                travel += event.changes.fold(0f) { sum, change ->
                    sum + abs(change.positionChange().y) + abs(change.positionChange().x)
                }
            } while (event.changes.any { it.pressed })
            // A scroll is not a tap. Same 8dp slop iOS tests its drag translation
            // against, so a flick down the page leaves the field where it is.
            val wasTap = travel < TAP_SLOP_DP.toPx()
            if (wasTap && startY > barHeightPx) {
                close()
            }
        }
    }
}

/** iOS tests its outside tap against 8pt of translation; this is the same number. */
private val TAP_SLOP_DP = 8.dp
