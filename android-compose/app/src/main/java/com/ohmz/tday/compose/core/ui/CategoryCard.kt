package com.ohmz.tday.compose.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The colored, watermarked tile used to launch a task category from a root
 * feed's home screen — originally the scheduled-task home's `CategoryGrid`
 * tile, promoted here so the Floater root feed's own nav entries (e.g.
 * Completed) render with the exact same look instead of a re-implementation.
 *
 * [tileTransitionKey] is the rectangle this tile publishes to the screen it opens; see
 * `TdayTileTransition.kt`. It is carried by a bounds-only sibling of the Card, NOT by the
 * Card itself, and the Box below is why: whatever carries the key is what the library
 * scales or re-measures into the destination, and a tile that handed over its Card would
 * be handing over its icon, its label and its watermark. On the sibling, the tile end
 * contributes the rectangle and the tile's own content stays where it is drawn — inside
 * the route's own fade, not scaled.
 *
 * The Card keeps its own modifier chain and its own sizing, so the wrapper is layout-
 * neutral: the Box takes the same slot the Card used to, and its height is still the
 * Card's.
 *
 * [inlineIcon] is the full-width form a single-column feed uses: a 70dp row with the glyph
 * beside the title on one line, as iOS's `FloaterTaskHomeCompletedCard` and web's tile draw
 * it. The default is the 2-up grid tile, glyph over title with room for a [count].
 */
@Composable
fun CategoryCard(
    modifier: Modifier,
    color: Color,
    @DrawableRes iconRes: Int,
    @DrawableRes watermarkRes: Int? = null,
    title: String,
    count: Int? = null,
    tileTransitionKey: String? = null,
    inlineIcon: Boolean = false,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .tdayTileTransitionSource(tileTransitionKey),
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .tdayPressable(interactionSource, scale = TdayMotionTokens.PressScales.Card),
            onClick = {
                TdayHaptics.buttonPress(view)
                onClick()
            },
            interactionSource = interactionSource,
            colors = CardDefaults.cardColors(containerColor = color),
            // The lift was a third `animateDpAsState` reading the same press and fed
            // into both slots, which is the pair `cardElevation` already holds and
            // already animates between. Two states, two numbers, one animation.
            elevation = CardDefaults.cardElevation(
                defaultElevation = 9.dp,
                pressedElevation = 2.dp,
            ),
            shape = RoundedCornerShape(26.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (inlineIcon) Modifier.height(InlineCardHeight) else Modifier)
                    .drawWithCache {
                        val iconSideGlow = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.22f),
                                Color.White.copy(alpha = 0.08f),
                                Color.Transparent,
                            ),
                            center = Offset(
                                x = size.width * 0.22f,
                                y = size.height * 0.2f,
                            ),
                            radius = size.maxDimension * 0.9f,
                        )
                        val pearlWash = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.12f),
                                Color(0xFFE7F3FF).copy(alpha = 0.1f),
                                Color(0xFFFFF2FA).copy(alpha = 0.08f),
                                Color.Transparent,
                            ),
                            start = Offset(
                                x = size.width * 0.05f,
                                y = size.height * 0.04f,
                            ),
                            end = Offset(
                                x = size.width * 0.9f,
                                y = size.height * 0.75f,
                            ),
                        )
                        onDrawWithContent {
                            drawRect(iconSideGlow)
                            drawRect(pearlWash)
                            drawContent()
                        }
                    },
            ) {
                if (watermarkRes != null) {
                    Box(modifier = Modifier.matchParentSize()) {
                        Icon(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .offset(x = 22.dp, y = 12.dp)
                                .size(124.dp),
                            painter = painterResource(watermarkRes),
                            contentDescription = null,
                            tint = lerp(color, Color.White, 0.28f).copy(alpha = 0.4f),
                        )
                    }
                }

                if (inlineIcon) {
                    Row(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp),
                            )
                            if (count != null) {
                                Text(
                                    text = count.toString(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                        }

                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
    }
}

/** The full-width tile's height — iOS's `minHeight: 70, maxHeight: 70` and web's `h-[70px]`. */
private val InlineCardHeight = 70.dp
