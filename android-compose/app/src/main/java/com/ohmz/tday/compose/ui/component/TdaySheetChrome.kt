package com.ohmz.tday.compose.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.ohmz.tday.compose.ui.theme.TdayDimens

object TdaySheetDefaults {
    val CloseAccent = Color(0xFFE35A5A)
    val ConfirmAccent = Color(0xFF2FA35B)
    val TopShape =
        RoundedCornerShape(topStart = TdayDimens.RadiusSheet, topEnd = TdayDimens.RadiusSheet)
    val DialogShape = RoundedCornerShape(TdayDimens.RadiusSheet)
    val CardShape = RoundedCornerShape(28.dp)
    val OverlayShape = RoundedCornerShape(30.dp)
    val SelectorShape = RoundedCornerShape(32.dp)
    val ControlShape = CircleShape

    val HorizontalPadding: Dp = TdayDimens.ContentPaddingHorizontal
    val VerticalPadding: Dp = TdayDimens.ContentPaddingVertical
    val SectionSpacing: Dp = TdayDimens.SpacingXl
    val ActionSize: Dp = TdayDimens.FabSize
    val ActionIconSize: Dp = 22.dp
    val ActionBorderWidth: Dp = TdayDimens.BorderWidthThick
    val MaxContentWidth: Dp = 520.dp

    @Composable
    fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

    @Composable
    fun containerColor(): Color {
        val colorScheme = MaterialTheme.colorScheme
        return if (isDarkTheme()) {
            lerp(colorScheme.background, colorScheme.surfaceVariant, 0.34f)
        } else {
            colorScheme.background
        }
    }

    @Composable
    fun surfaceColor(): Color {
        val colorScheme = MaterialTheme.colorScheme
        return if (isDarkTheme()) {
            lerp(colorScheme.surface, colorScheme.surfaceVariant, 0.18f)
        } else {
            colorScheme.surface
        }
    }

    @Composable
    fun controlSurfaceColor(): Color = MaterialTheme.colorScheme.surfaceVariant

    @Composable
    fun scrimColor(): Color = Color.Black.copy(alpha = if (isDarkTheme()) 0.68f else 0.40f)

    @Composable
    fun tonalElevation(): Dp = if (isDarkTheme()) TdayDimens.BottomSheetTonalElevationDark else 0.dp

    @Composable
    fun cardStrokeColor(): Color {
        return if (isDarkTheme()) {
            Color.White.copy(alpha = 0.10f)
        } else {
            Color.White.copy(alpha = 0.45f)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TdayModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        shape = TdaySheetDefaults.TopShape,
        containerColor = TdaySheetDefaults.containerColor(),
        tonalElevation = TdaySheetDefaults.tonalElevation(),
        scrimColor = TdaySheetDefaults.scrimColor(),
        modifier = modifier,
    ) {
        TdayCenteredSheetContent(content = content)
    }
}

@Composable
fun TdayCenteredSheetContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val contentWidth = if (maxWidth < TdaySheetDefaults.MaxContentWidth) {
            maxWidth
        } else {
            TdaySheetDefaults.MaxContentWidth
        }

        Column(
            modifier = Modifier.width(contentWidth),
            content = content,
        )
    }
}

@Composable
fun <T> TdayCenteredSelectorDialog(
    title: String,
    options: List<T>,
    optionLabel: (T) -> String,
    optionSwatchColor: (T) -> Color,
    isSelected: (T) -> Boolean,
    onDismiss: () -> Unit,
    onOptionSelected: (T) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = TdaySheetDefaults.surfaceColor()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TdaySheetDefaults.scrimColor())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.74f)
                    .heightIn(max = 380.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = TdaySheetDefaults.SelectorShape,
                colors = CardDefaults.cardColors(containerColor = containerColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 10.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    )

                    options.forEachIndexed { index, option ->
                        if (index > 0) {
                            TdayCenteredSelectorDivider()
                        }
                        TdayCenteredSelectorRow(
                            title = optionLabel(option),
                            swatchColor = optionSwatchColor(option),
                            selected = isSelected(option),
                            onClick = { onOptionSelected(option) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TdayCenteredSelectorDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 18.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    )
}

@Composable
private fun TdayCenteredSelectorRow(
    title: String,
    swatchColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = {
                ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
                onClick()
            })
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = swatchColor,
                    shape = RoundedCornerShape(999.dp),
                ),
        )

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.onSurface,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Spacer(modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun TdaySheetHeader(
    title: String,
    leftIcon: ImageVector,
    leftContentDescription: String,
    onLeftClick: () -> Unit,
    confirmContentDescription: String = "",
    onConfirm: () -> Unit = {},
    confirmEnabled: Boolean = false,
    modifier: Modifier = Modifier,
    confirmIcon: ImageVector = Icons.Rounded.Check,
    showConfirmAction: Boolean = true,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TdaySheetActionButton(
                icon = leftIcon,
                contentDescription = leftContentDescription,
                enabled = true,
                accentColor = TdaySheetDefaults.CloseAccent,
                onClick = onLeftClick,
            )

            Spacer(modifier = Modifier.weight(1f))

            if (showConfirmAction) {
                TdaySheetActionButton(
                    icon = confirmIcon,
                    contentDescription = confirmContentDescription,
                    enabled = confirmEnabled,
                    accentColor = TdaySheetDefaults.ConfirmAccent,
                    onClick = onConfirm,
                )
            } else {
                Spacer(modifier = Modifier.size(TdaySheetDefaults.ActionSize))
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = TdaySheetDefaults.ActionSize + 14.dp),
        )
    }
}

@Composable
fun TdaySheetActionButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.93f else 1f,
        label = "tdaySheetActionButtonScale",
    )
    val elevation by animateDpAsState(
        targetValue = when {
            pressed && enabled -> 2.dp
            enabled -> 8.dp
            else -> 5.dp
        },
        label = "tdaySheetActionButtonElevation",
    )
    val offsetY by animateDpAsState(
        targetValue = if (pressed && enabled) 1.dp else 0.dp,
        label = "tdaySheetActionButtonOffsetY",
    )

    Card(
        modifier = modifier
            .size(TdaySheetDefaults.ActionSize)
            .offset(y = offsetY)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = TdaySheetDefaults.ActionBorderWidth,
                color = accentColor.copy(alpha = if (enabled) 0.55f else 0.30f),
                shape = TdaySheetDefaults.ControlShape,
            ),
        onClick = {
            if (enabled) {
                ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
            }
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        shape = TdaySheetDefaults.ControlShape,
        colors = CardDefaults.cardColors(containerColor = TdaySheetDefaults.controlSurfaceColor()),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation,
            pressedElevation = elevation,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.55f),
                modifier = Modifier.size(TdaySheetDefaults.ActionIconSize),
            )
        }
    }
}

@Composable
fun TdaySheetSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

@Composable
fun TdaySheetCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = TdaySheetDefaults.CardShape,
        colors = CardDefaults.cardColors(containerColor = TdaySheetDefaults.surfaceColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}
