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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassEmpty
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.R

/**
 * Persistent "waiting for admin approval" holding screen. Shown on every launch while a
 * registered account is still PENDING; a silent re-login (on launch and via "Check
 * status") advances to Home the moment approval lands.
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
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 430.dp),
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.background),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.HourglassEmpty,
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
                        .height(48.dp),
                    enabled = !isChecking,
                    onClick = onCheckStatus,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary,
                    ),
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
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
