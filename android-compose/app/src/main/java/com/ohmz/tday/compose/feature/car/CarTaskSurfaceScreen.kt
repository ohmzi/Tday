package com.ohmz.tday.compose.feature.car

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.ui.component.RootCreateTaskButton
import com.ohmz.tday.compose.ui.priority.priorityDisplayLabelRes
import com.ohmz.tday.compose.ui.theme.TdayDimens
import com.ohmz.tday.compose.ui.theme.tdayPriorityColor

@Composable
fun CarTaskSurfaceScreen(
    uiState: CarTaskSurfaceState,
    onModeSelected: (CarTaskMode) -> Unit,
    onCreateWithVoice: () -> Unit,
    onComplete: (CarTaskItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var completionCandidate by remember { mutableStateOf<CarTaskItem?>(null) }
    val plusColor by animateColorAsState(
        targetValue = uiState.mode.plusColor,
        label = "carPlusColor",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = TdayDimens.ContentPaddingHorizontal),
        ) {
            CarTaskHeader(
                mode = uiState.mode,
                plusColor = plusColor,
                onModeSelected = onModeSelected,
                onCreateWithVoice = onCreateWithVoice,
            )

            AnimatedContent(
                targetState = uiState.mode,
                transitionSpec = {
                    val direction = if (targetState.ordinal >= initialState.ordinal) {
                        AnimatedContentTransitionScope.SlideDirection.Left
                    } else {
                        AnimatedContentTransitionScope.SlideDirection.Right
                    }
                    val enter = slideInHorizontally(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        initialOffsetX = { width ->
                            if (direction == AnimatedContentTransitionScope.SlideDirection.Left) width else -width
                        },
                    ) + fadeIn()
                    val exit = slideOutHorizontally(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        targetOffsetX = { width ->
                            if (direction == AnimatedContentTransitionScope.SlideDirection.Left) -width else width
                        },
                    ) + fadeOut()
                    enter togetherWith exit using SizeTransform(clip = false)
                },
                label = "carTaskModeContent",
            ) {
                CarTaskContent(
                    uiState = uiState,
                    onSelectTask = { completionCandidate = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    completionCandidate?.let { task ->
        AlertDialog(
            onDismissRequest = { completionCandidate = null },
            title = { Text(stringResource(R.string.car_complete_dialog_title)) },
            text = {
                Text(
                    text = task.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        completionCandidate = null
                        onComplete(task)
                    },
                ) {
                    Text(stringResource(R.string.car_complete_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { completionCandidate = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CarTaskHeader(
    mode: CarTaskMode,
    plusColor: Color,
    onModeSelected: (CarTaskMode) -> Unit,
    onCreateWithVoice: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp),
    ) {
        Text(
            text = stringResource(mode.titleRes),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(end = 150.dp),
        )

        CarModeSlider(
            mode = mode,
            onModeSelected = onModeSelected,
            modifier = Modifier.align(Alignment.Center),
        )

        RootCreateTaskButton(
            onClick = onCreateWithVoice,
            backgroundColor = plusColor,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(TdayDimens.FabSize),
        )
    }
}

@Composable
private fun CarModeSlider(
    mode: CarTaskMode,
    onModeSelected: (CarTaskMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val trackColor = colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.72f else 0.62f)
    val selectorColor = if (isDark) {
        colorScheme.background.copy(alpha = 0.90f)
    } else {
        colorScheme.surface.copy(alpha = 0.96f)
    }
    val shape = RoundedCornerShape(22.dp)
    val selectorShape = RoundedCornerShape(18.dp)

    BoxWithConstraints(
        modifier = modifier
            .width(112.dp)
            .height(48.dp)
            .clip(shape)
            .background(trackColor, shape)
            .border(
                BorderStroke(
                    1.dp,
                    colorScheme.onSurfaceVariant.copy(alpha = if (isDark) 0.12f else 0.10f)
                ),
                shape,
            )
            .padding(4.dp),
    ) {
        val tabWidth = maxWidth / 2
        val selectorOffset by animateDpAsState(
            targetValue = if (mode == CarTaskMode.TODAY) 0.dp else tabWidth,
            animationSpec = spring(
                dampingRatio = 0.86f,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "carModeSelectorOffset",
        )
        Box(
            modifier = Modifier
                .offset(x = selectorOffset)
                .width(tabWidth)
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .padding(1.dp)
                .clip(selectorShape)
                .background(selectorColor, selectorShape)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            CarModeButton(
                selected = mode == CarTaskMode.TODAY,
                mode = CarTaskMode.TODAY,
                onModeSelected = onModeSelected,
                modifier = Modifier.weight(1f),
            )
            CarModeButton(
                selected = mode == CarTaskMode.FLOATER,
                mode = CarTaskMode.FLOATER,
                onModeSelected = onModeSelected,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CarModeButton(
    selected: Boolean,
    mode: CarTaskMode,
    onModeSelected: (CarTaskMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = { onModeSelected(mode) },
        modifier = modifier.fillMaxHeight(),
    ) {
        Icon(
            imageVector = if (mode == CarTaskMode.TODAY) Icons.Rounded.Home else Icons.Rounded.Eco,
            contentDescription = stringResource(mode.titleRes),
            tint = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun CarTaskContent(
    uiState: CarTaskSurfaceState,
    onSelectTask: (CarTaskItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        uiState.loading -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.car_surface_loading),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        uiState.errorMessage != null -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.car_surface_load_error),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        uiState.isEmpty -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(uiState.mode.emptyTitleRes),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        else -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(uiState.items, key = { it.id }) { item ->
                    CarTaskRow(
                        item = item,
                        onClick = { onSelectTask(item) },
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun CarTaskRow(
    item: CarTaskItem,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(tdayPriorityColor(item.priority)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = listOfNotNull(
                    stringResource(priorityDisplayLabelRes(item.priority)),
                    item.dueLabel
                ).joinToString(" - ")
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = stringResource(R.string.car_complete_dialog_confirm),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
