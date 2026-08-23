package com.ohmz.tday.compose.core.ui

import androidx.compose.foundation.BorderStroke
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

/**
 * The app's search field, in its open state.
 *
 * The root feeds fold theirs down into a round button and so own its width and
 * placement themselves ([RootFeedHeroHeader]); everywhere else — the guide —
 * takes it at full width, in ordinary flow. The chrome is the same either way,
 * and the numbers come from [RootFeedHeroHeaderMetrics] so the two cannot drift
 * apart.
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
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.26f)),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                // Matches the root feeds' trailing control, minus its dismiss
                // semantics — there is no folded state here to dismiss to.
                IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_x),
                        contentDescription = clearContentDescription,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
