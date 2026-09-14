package com.ohmz.tday.compose.feature.sweep

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.ui.TdayHeroTitleBlock
import com.ohmz.tday.compose.core.ui.TdayHeroToolbar
import com.ohmz.tday.compose.core.ui.rememberScrollHeroTitleCollapse
import com.ohmz.tday.compose.ui.component.ThemedDatePickerDialog
import com.ohmz.tday.compose.ui.theme.TdayDimens
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Morning Sweep: guided one-card-at-a-time triage of carried-over tasks —
 * Today / Tomorrow / Pick a date / Make it a floater / Let it go, plus
 * "Sweep all to today" behind one undoable toast.
 */
@Composable
fun MorningSweepScreen(
    onBack: () -> Unit,
    viewModel: MorningSweepViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var pickingDateForId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!uiState.loaded) viewModel.load()
    }

    val colorScheme = MaterialTheme.colorScheme
    val card = uiState.cards.firstOrNull()
    val scrollState = rememberScrollState()
    val heroCollapse = rememberScrollHeroTitleCollapse(scrollState = scrollState)

    Scaffold(
        containerColor = colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = TdayDimens.ContentPaddingHorizontal)
                .padding(bottom = TdayDimens.Spacing4xl),
        ) {
            TdayHeroTitleBlock(
                title = stringResource(R.string.sweep_title),
                icon = ImageVector.vectorResource(R.drawable.ic_lucide_sun),
                accentColor = TdaySweepAccent,
                collapseProgress = heroCollapse.progress,
            )
            if (card == null) {
                Text(
                    text = stringResource(R.string.sweep_done),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = SweepDoneVerticalInset),
                )
            } else {
                SweepCard(card = card, remaining = uiState.cards.size)
                Spacer(Modifier.height(SweepCardActionGap))

                SweepAction(R.drawable.ic_lucide_alarm_clock, stringResource(R.string.sweep_today)) {
                    viewModel.moveToToday(card)
                }
                SweepAction(R.drawable.ic_lucide_calendar_clock, stringResource(R.string.sweep_tomorrow)) {
                    viewModel.moveToTomorrow(card)
                }
                SweepAction(R.drawable.ic_lucide_calendar, stringResource(R.string.sweep_pick_date)) {
                    pickingDateForId = card.id
                }
                SweepAction(R.drawable.ic_lucide_waves, stringResource(R.string.sweep_float)) {
                    viewModel.makeFloater(card)
                }
                SweepAction(R.drawable.ic_lucide_trash, stringResource(R.string.sweep_let_go)) {
                    viewModel.letGo(card)
                }

                Spacer(Modifier.height(TdayDimens.SpacingLg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { viewModel.skip(card) }) {
                        Text(
                            text = stringResource(R.string.sweep_skip),
                            fontWeight = FontWeight.ExtraBold,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = { viewModel.sweepAllToToday() }) {
                        Text(
                            text = stringResource(R.string.sweep_all_to_today),
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }

        // Last, so it draws over the content passing behind it.
        TdayHeroToolbar(
            title = stringResource(R.string.sweep_title),
            collapseProgress = heroCollapse.progress,
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back),
            modifier = Modifier.align(Alignment.TopStart),
        )
        }
    }

    val pickingCard = uiState.cards.firstOrNull { it.id == pickingDateForId }
    if (pickingCard != null) {
        ThemedDatePickerDialog(
            initialEpochMs = System.currentTimeMillis(),
            onDismiss = { pickingDateForId = null },
            onConfirm = { pickedDayUtcMidnightMs ->
                pickingDateForId = null
                val pickedDay = Instant.ofEpochMilli(pickedDayUtcMidnightMs)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                viewModel.moveTo(pickingCard, pickedDay)
            },
        )
    }
}

@Composable
private fun SweepCard(card: TodoItem, remaining: Int) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(TdayDimens.RadiusLg),
        color = colorScheme.surface,
        border = BorderStroke(TdayDimens.BorderWidth, colorScheme.onSurface.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(TdayDimens.SpacingXxl)) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = colorScheme.onSurface,
            )
            Spacer(Modifier.height(TdayDimens.SpacingXs))
            val dueText = card.due?.let { due ->
                due.atZone(ZoneId.systemDefault()).format(
                    DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()),
                )
            }.orEmpty()
            Text(
                text = if (remaining > 1) "$dueText · $remaining" else dueText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SweepAction(icon: Int, label: String, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(TdayDimens.RadiusMd),
        color = colorScheme.surface,
        border = BorderStroke(TdayDimens.BorderWidth, colorScheme.onSurface.copy(alpha = 0.06f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = TdayDimens.SpacingMd)
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = SweepActionHorizontalPadding,
                vertical = SweepActionVerticalPadding,
            ),
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(icon),
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(SweepActionIconSize),
            )
            Spacer(Modifier.width(TdayDimens.SpacingLg))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = colorScheme.onSurface,
            )
        }
    }
}

private const val SWEEP_TITLE_COLLAPSE_DISTANCE_DP = 180f

// What the sweep draws that the scale has no rung for. The card and the action rows corner on
// `RadiusLg` and `RadiusMd` and inset on the spacing steps; these are the leftovers.

/** How far the "nothing left to sweep" line sits off the hero title once the cards run out. */
private val SweepDoneVerticalInset = 48.dp

/** The gap under the card, wider than the one between the actions so the choices read as a set. */
private val SweepCardActionGap = 16.dp

// One action row's interior, named as a pair so a later edit cannot move one half onto a step and
// leave the other behind.
private val SweepActionHorizontalPadding = 16.dp
private val SweepActionVerticalPadding = 13.dp

/** The action's glyph, under `IconSm` because the label beside it carries the row. */
private val SweepActionIconSize = 18.dp

/** Overdue's accent — sweeping is what you do with what has slipped. */
private val TdaySweepAccent = Color(0xFFDA7661)
