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
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.ui.theme.TdayDimens

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
    clearContentDescription: String? = null,
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
            if (onClear != null && value.isNotEmpty()) {
                // The root feeds' trailing control down to the numbers — 30 box,
                // 18 glyph, the same dimmed tint — minus its dismiss semantics,
                // since there is no folded state here to dismiss to. It sat at
                // 32/20 and full strength, which beside the other field read as
                // a heavier X in a field that is otherwise the same chrome.
                IconButton(onClick = onClear, modifier = Modifier.size(30.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_x),
                        contentDescription = clearContentDescription,
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
