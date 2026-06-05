package com.ohmz.tday.compose.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TaskSwipeActionButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    label: String,
    tint: Color,
    background: Color,
    revealProgress: Float,
    revealDelay: Float,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressedScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        label = "taskSwipeActionScale",
    )
    val normalizedReveal = ((revealProgress - revealDelay) / (1f - revealDelay))
        .coerceIn(0f, 1f)
    val easedReveal = FastOutSlowInEasing.transform(normalizedReveal)
    Column(
        modifier = Modifier
            .sizeIn(minWidth = 60.dp)
            .graphicsLayer {
                alpha = easedReveal
                val revealScale = 0.38f + (0.62f * easedReveal)
                scaleX = pressedScale * revealScale
                scaleY = pressedScale * revealScale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Card(
            modifier = Modifier.size(width = 56.dp, height = 34.dp),
            onClick = onClick,
            interactionSource = interactionSource,
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = background),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
            ),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        Text(
            text = label,
            color = colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
    }
}
