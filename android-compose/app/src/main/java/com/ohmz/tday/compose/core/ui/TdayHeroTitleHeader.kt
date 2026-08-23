package com.ohmz.tday.compose.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.sp
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.ui.theme.TdayDimens
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Geometry for the screen header used by every titled page that is not a root
 * feed: a glyph in a tinted circle with the page's title beneath it, folding up
 * into a plain toolbar as the page scrolls.
 *
 * Distinct from [RootFeedHeroHeaderMetrics], which describes the root feeds'
 * header — that one is pinned, has a search field, and centres its title. This
 * one scrolls away and leads with the page's own glyph. They share the easing
 * curve deliberately, so the two kinds of header feel like one family.
 */
object TdayHeroTitleMetrics {
    val HorizontalPadding = 18.dp
    val ToolbarHeight = 56.dp

    val MarkBox = 96.dp
    val MarkGlyph = 44.dp
    /** Gap between the toolbar row and the circle. */
    val MarkTopGap = 8.dp
    /** Gap between the circle and the title beneath it. */
    val TitleTopGap = 18.dp
    val TitleSize = 32.sp
    val HeroBottomGap = 10.dp

    /** Total height the hero block adds below the toolbar when fully expanded. */
    val HeroHeight = MarkTopGap + MarkBox + TitleTopGap + 40.dp + HeroBottomGap

    /**
     * The mark goes first and is gone well before the title reaches the toolbar,
     * so the two never occupy the same space on the way past each other.
     */
    const val MarkFadeEnd = 0.45f
    const val TitleHandoff = 0.82f

    /** Opacity range of the accent wash behind the glyph. */
    const val MarkWashTopAlpha = 0.24f
    const val MarkWashBottomAlpha = 0.07f

    /** The oversized echo of the glyph sitting behind it inside the circle. */
    const val MarkEchoAlpha = 0.17f
    val MarkEchoGlyph = 108.dp
    val MarkEchoOffsetX = 22.dp
    val MarkEchoOffsetY = 26.dp

    /** Height of the band that dissolves content as it reaches the toolbar. */
    val ContentFadeHeight = 30.dp

    fun lerp(from: Dp, to: Dp, fraction: Float): Dp = from + ((to - from) * fraction)
}

/**
 * @param collapseProgress raw 0..1 scroll progress. Pass the unanimated value —
 *   the easing lives here, matching the root feeds' header.
 */
@Composable
fun TdayHeroTitleHeader(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    collapseProgress: Float,
    modifier: Modifier = Modifier,
    /** Screens whose title is not plain onBackground — Completed, Calendar. */
    titleColor: Color? = null,
    onBack: (() -> Unit)? = null,
    backContentDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val m = TdayHeroTitleMetrics
    val colorScheme = MaterialTheme.colorScheme
    val resolvedTitleColor = titleColor ?: colorScheme.onBackground
    val progress = collapseProgress.coerceIn(0f, 1f)

    // Same septic curve the root feeds use, so both kinds of header ease alike.
    val markFade = 1f - RootFeedHeroHeaderMetrics.stagger(progress, m.MarkFadeEnd)
    val heroFade = ((m.TitleHandoff - progress) / m.TitleHandoff).coerceIn(0f, 1f)
    val toolbarFade =
        ((progress - m.TitleHandoff) / (1f - m.TitleHandoff)).coerceIn(0f, 1f)
    val heroHeight = m.lerp(m.HeroHeight, 0.dp, RootFeedHeroHeaderMetrics.stagger(progress, 1f))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = m.HorizontalPadding),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(m.ToolbarHeight),
        ) {
            if (onBack != null) {
                Box(modifier = Modifier.align(Alignment.CenterStart)) {
                    TdayHeroBackButton(
                        contentDescription = backContentDescription.orEmpty(),
                        onClick = onBack,
                    )
                }
            }

            // Only appears once the hero title has left, so the two never read
            // as two titles at once.
            if (toolbarFade > 0.001f) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = resolvedTitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer { alpha = toolbarFade },
                )
            }

            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }

        if (heroHeight > 0.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .graphicsLayer { alpha = heroFade },
            ) {
                Spacer(modifier = Modifier.height(m.MarkTopGap))
                // A flat glyph on a flat disc read as a utility icon rather than
                // as a page mark. The wash is a gradient now, with an oversized
                // echo of the same glyph bleeding out of the bottom-right —
                // the motif the category tiles already use for their watermarks.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(m.MarkBox)
                        .graphicsLayer {
                            alpha = markFade
                            val scale = 0.85f + (0.15f * markFade)
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    accentColor.copy(alpha = m.MarkWashTopAlpha),
                                    accentColor.copy(alpha = m.MarkWashBottomAlpha),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor.copy(alpha = m.MarkEchoAlpha),
                        modifier = Modifier
                            .size(m.MarkEchoGlyph)
                            .offset(x = m.MarkEchoOffsetX, y = m.MarkEchoOffsetY),
                    )
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(m.MarkGlyph),
                    )
                }
                Spacer(modifier = Modifier.height(m.TitleTopGap))
                Text(
                    text = title,
                    fontSize = m.TitleSize,
                    fontWeight = FontWeight.ExtraBold,
                    color = resolvedTitleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The circular back affordance these headers lead with. */
@Composable
private fun TdayHeroBackButton(
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val isDark = colorScheme.background.luminance() < 0.5f

    Card(
        modifier = Modifier
            .size(TdayDimens.FabSize)
            .graphicsLayer {
                val scale = if (pressed) 0.93f else 1f
                scaleX = scale
                scaleY = scale
            },
        onClick = onClick,
        shape = CircleShape,
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) {
                colorScheme.surface.copy(alpha = 0.94f)
            } else {
                Color.White.copy(alpha = 0.96f)
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = TdayDimens.FabElevation),
    ) {
        Box(modifier = Modifier.size(TdayDimens.FabSize), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_left),
                contentDescription = contentDescription,
                tint = colorScheme.onSurface,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}
