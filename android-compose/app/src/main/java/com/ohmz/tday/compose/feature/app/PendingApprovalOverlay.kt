package com.ohmz.tday.compose.feature.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.ui.component.TdaySheetDefaults
import com.ohmz.tday.compose.ui.theme.TdayDimens

// What this overlay draws that the scale has no rung for. The card's corner is not among them: it
// has always drawn the 30 that `TdaySheetDefaults.OverlayShape` names, which is the same corner the
// list overlay draws.

/** How wide the card is allowed to grow before the message starts running. */
private val ApprovalCardMaxWidth = 430.dp

/** The gap between the things stacked inside the card. 16 sits between `SpacingXl` and
 *  `SpacingXxl`. */
private val CardContentSpacing = 16.dp

/** Android's minimum touch target, claimed as a fixed height by "Check status". The target, not the
 *  drawing, which is why it is not a spacing rung. */
private val PrimaryButtonHeight = 48.dp

// The spinner that stands in for the button's label while the status check is in flight. Under
// `IconSm` because it is drawn inside the button's text slot.
private val ButtonSpinnerSize = 18.dp
private val ButtonSpinnerStroke = 2.dp

/**
 * Persistent "waiting for admin approval" holding screen. Shown on every launch while a
 * registered account is still PENDING; a silent re-login (on launch and via "Check
 * status") advances to the scheduled task home screen the moment approval lands.
 */
@Composable
fun PendingApprovalOverlay(
    username: String?,
    isChecking: Boolean,
    onCheckStatus: () -> Unit,
    onUseDifferentAccount: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(TdayDimens.Spacing3xl),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = ApprovalCardMaxWidth),
            shape = TdaySheetDefaults.OverlayShape,
            colors = CardDefaults.cardColors(containerColor = colorScheme.background),
        ) {
            Column(
                modifier = Modifier.padding(TdayDimens.Spacing3xl),
                verticalArrangement = Arrangement.spacedBy(CardContentSpacing),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(TdayDimens.SpacingMd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_hourglass),
                        contentDescription = null,
                        tint = colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.pending_approval_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = colorScheme.onSurface,
                    )
                }

                Text(
                    text = if (!username.isNullOrBlank()) {
                        stringResource(R.string.pending_approval_message, username)
                    } else {
                        stringResource(R.string.pending_approval_message_generic)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface.copy(alpha = 0.62f),
                )

                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PrimaryButtonHeight),
                    enabled = !isChecking,
                    onClick = onCheckStatus,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary,
                    ),
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(ButtonSpinnerSize),
                            strokeWidth = ButtonSpinnerStroke,
                            color = colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.pending_approval_check),
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }

                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isChecking,
                    onClick = onUseDifferentAccount,
                ) {
                    Text(
                        text = stringResource(R.string.pending_approval_use_different_account),
                        fontWeight = FontWeight.ExtraBold,
                        color = colorScheme.primary,
                    )
                }
            }
        }
    }
}
