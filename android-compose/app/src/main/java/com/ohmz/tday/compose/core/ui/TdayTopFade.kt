package com.ohmz.tday.compose.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Dissolves a scroll container's content as it reaches the top of its viewport,
 * i.e. as it passes under the toolbar.
 *
 * A real alpha fade rather than an opaque strip painted over the top: these
 * screens sit above [com.ohmz.tday.compose.ui.component.EmptyTaskWatermark], and
 * an opaque band would erase the watermark along with the rows. `DstIn` against
 * a vertical gradient keeps whatever is behind the list visible while the rows
 * themselves go transparent.
 *
 * Needs an offscreen compositing layer: without it the blend would apply against
 * the window rather than against just this subtree.
 */
fun Modifier.tdayTopFade(height: Dp = TdayHeroTitleMetrics.ContentFadeHeight): Modifier {
    if (height <= 0.dp) return this
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fadePx = height.toPx().coerceAtMost(size.height)
            if (fadePx <= 0f) return@drawWithContent
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = 0f,
                    endY = fadePx,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
}
