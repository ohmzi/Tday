package com.ohmz.tday.compose.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.R

@Composable
fun EmptyTaskWatermark(
    imageVector: ImageVector = ImageVector.vectorResource(R.drawable.ic_lucide_inbox),
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

/** Drawable-backed variant so screens can render the shared lucide tile glyphs. */
@Composable
fun EmptyTaskWatermark(
    @DrawableRes iconRes: Int,
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
            painter = painterResource(iconRes),
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

/**
 * The Day Done state: shown instead of "No tasks for today" when everything
 * scheduled for today has actually been completed — a quiet payoff, not a
 * generic empty screen.
 */
@Composable
fun DayDoneBackgroundMessage(
    message: String,
    dateText: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .offset(x = 24.dp)
                .padding(horizontal = 24.dp),
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_check_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f),
                modifier = Modifier.size(44.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = dateText,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
        }
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
