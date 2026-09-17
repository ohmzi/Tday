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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

/**
 * The box the watermark's glyph is drawn in — 212dp, the native twin of iOS's
 * 194pt. Public because a caller that hands in a drawing through `markContent`
 * has to draw it in this box: the slot centres whatever it is given, so a mark
 * built at any other size lands at the wrong scale inside the same rotation and
 * offset.
 */
val WatermarkGlyphSize = 212.dp

@Composable
fun EmptyTaskWatermark(
    imageVector: ImageVector = ImageVector.vectorResource(R.drawable.ic_lucide_inbox),
    accentColor: Color? = null,
    modifier: Modifier = Modifier,
    flipHorizontal: Boolean = false,
    markContent: (@Composable () -> Unit)? = null,
) {
    val neutralTint = MaterialTheme.colorScheme.onSurfaceVariant
    val watermarkTint = accentColor
        ?.let { lerp(neutralTint, it, 0.36f) }
        ?: neutralTint
    val resolvedTint = watermarkTint.copy(alpha = 0.10f)

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val iconSize = WatermarkGlyphSize
        val iconCenterY = maxHeight * (2f / 3f)

        WatermarkSlot(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 28.dp, y = iconCenterY - (iconSize / 2))
                .size(iconSize)
                .graphicsLayer {
                    rotationZ = -7f
                    scaleX = if (flipHorizontal) -1f else 1f
                },
            tint = resolvedTint,
            markContent = markContent,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = resolvedTint,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Drawable-backed variant so screens can render the shared lucide tile glyphs. */
@Composable
fun EmptyTaskWatermark(
    @DrawableRes iconRes: Int,
    accentColor: Color? = null,
    modifier: Modifier = Modifier,
    flipHorizontal: Boolean = false,
    markContent: (@Composable () -> Unit)? = null,
) {
    val neutralTint = MaterialTheme.colorScheme.onSurfaceVariant
    val watermarkTint = accentColor
        ?.let { lerp(neutralTint, it, 0.36f) }
        ?: neutralTint
    val resolvedTint = watermarkTint.copy(alpha = 0.10f)

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val iconSize = WatermarkGlyphSize
        val iconCenterY = maxHeight * (2f / 3f)

        WatermarkSlot(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 28.dp, y = iconCenterY - (iconSize / 2))
                .size(iconSize)
                .graphicsLayer {
                    rotationZ = -7f
                    scaleX = if (flipHorizontal) -1f else 1f
                },
            tint = resolvedTint,
            markContent = markContent,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = resolvedTint,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The watermark's one glyph-sized slot, whichever way the glyph arrives.
 *
 * `markContent` is for a screen whose watermark is more than one glyph — the
 * Completion history's mark is three stacked — and is drawn exactly where and at
 * exactly the size the single glyph would have been, so the two paths cannot
 * drift.
 *
 * `tint` travels on `LocalContentColor` as well as being handed to the fallback
 * icon, so a composite mark leaves its own layers untinted and is drawn in this
 * component's blended watermark colour at this component's own fade. The
 * alternative is a call site re-deriving `lerp(onSurfaceVariant, accent, 0.36f)`
 * to say what colour it is standing in.
 */
@Composable
private fun WatermarkSlot(
    modifier: Modifier = Modifier,
    tint: Color,
    markContent: (@Composable () -> Unit)?,
    fallback: @Composable () -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides tint) {
            if (markContent != null) markContent() else fallback()
        }
    }
}


