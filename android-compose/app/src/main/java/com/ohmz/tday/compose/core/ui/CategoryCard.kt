package com.ohmz.tday.compose.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat

/**
 * The colored, watermarked tile used to launch a task category from a root
 * feed's home screen — originally the scheduled-task home's `CategoryGrid`
 * tile, promoted here so the Floater root feed's own nav entries (e.g.
 * Completed) render with the exact same look instead of a re-implementation.
 */
@Composable
fun CategoryCard(
    modifier: Modifier,
    color: Color,
    @DrawableRes iconRes: Int,
    @DrawableRes watermarkRes: Int? = null,
    title: String,
    count: Int? = null,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        label = "categoryCardScale",
    )
    val animatedOffsetY by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 0.dp,
        label = "categoryCardOffsetY",
    )
    val animatedElevation by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 9.dp,
        label = "categoryCardElevation",
    )

    Card(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .offset(y = animatedOffsetY)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            },
        onClick = {
            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
            onClick()
        },
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(
            defaultElevation = animatedElevation,
            pressedElevation = animatedElevation,
        ),
        shape = RoundedCornerShape(26.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
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
