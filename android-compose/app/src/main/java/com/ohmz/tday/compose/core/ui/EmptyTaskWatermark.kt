package com.ohmz.tday.compose.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun EmptyTaskWatermark(
    imageVector: ImageVector = Icons.Rounded.Inbox,
    accentColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    val neutralTint = MaterialTheme.colorScheme.onSurfaceVariant
    val watermarkTint = accentColor
        ?.let { lerp(neutralTint, it, 0.36f) }
        ?: neutralTint

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val iconSize = 212.dp
        val iconCenterY = maxHeight * (2f / 3f)

        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = watermarkTint.copy(alpha = 0.10f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 28.dp, y = iconCenterY - (iconSize / 2))
                .size(iconSize)
                .graphicsLayer {
                    rotationZ = -7f
                },
        )
    }
}

@Composable
fun EmptyTaskBackgroundMessage(
    message: String,
    modifier: Modifier = Modifier,
    horizontalOffset: Dp = 24.dp,
    verticalOffset: Dp = 0.dp,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .offset(x = horizontalOffset, y = verticalOffset)
                .padding(horizontal = 24.dp),
        )
    }
}
