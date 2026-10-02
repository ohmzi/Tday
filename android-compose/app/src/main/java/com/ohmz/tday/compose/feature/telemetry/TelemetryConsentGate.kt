package com.ohmz.tday.compose.feature.telemetry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.observability.TelemetryConsentState
import com.ohmz.tday.compose.feature.guide.LocalOpenGuideTopic
import com.ohmz.tday.compose.feature.lock.LocalAppLocked
import com.ohmz.tday.compose.ui.component.TdaySheetDefaults
import com.ohmz.tday.compose.ui.theme.TdayDimens
import com.ohmz.tday.shared.guide.GuideTopicIds

/**
 * Whether the one-time "send crash reports?" card should be up.
 *
 * It asks once, after the sign-in wizard, in a workspace of either kind. It never asks on a build
 * with no DSN (nothing could be sent), never again after an answer in either place, not for the
 * rest of the session once put off, and not while something more urgent has the screen.
 */
internal fun shouldShowTelemetryCard(
    available: Boolean,
    state: TelemetryConsentState,
    deferredThisSession: Boolean,
    workspaceOpen: Boolean,
    aHigherGateIsUp: Boolean,
): Boolean =
    available &&
        state == TelemetryConsentState.UNANSWERED &&
        !deferredThisSession &&
        workspaceOpen &&
        !aHigherGateIsUp

/**
 * The consent card, drawn as a dialog the way [com.ohmz.tday.compose.feature.auth.SetSecurityQuestionsGate]
 * is. [aHigherGateIsUp] covers the gates that live in the caller (update required, security
 * questions); the app lock is read from [LocalAppLocked], because a dialog would otherwise draw
 * over the lock screen.
 *
 * Back press puts the card off until the next launch rather than answering it: the two buttons
 * are the only ways to say yes or no.
 */
@Composable
fun TelemetryConsentGate(
    workspaceOpen: Boolean,
    aHigherGateIsUp: Boolean,
    viewModel: TelemetryConsentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deferredThisSession by viewModel.cardDeferred.collectAsStateWithLifecycle()
    val appLocked = LocalAppLocked.current

    val show = shouldShowTelemetryCard(
        available = viewModel.isAvailable,
        state = state,
        deferredThisSession = deferredThisSession,
        workspaceOpen = workspaceOpen,
        aHigherGateIsUp = aHigherGateIsUp || appLocked,
    )
    if (!show) return

    // Null where there is no navigation host to open the guide in; the link is then not drawn.
    val openGuideTopic = LocalOpenGuideTopic.current
    TelemetryConsentCard(
        onShare = { viewModel.setShareReports(true) },
        onNotNow = { viewModel.setShareReports(false) },
        onDefer = viewModel::deferCard,
        onReadFaq = openGuideTopic?.let { open ->
            {
                // Deferred first: the guide opens beneath a dialog otherwise, behind the very
                // card it is explaining.
                viewModel.deferCard()
                open(GuideTopicIds.CRASH_REPORTS)
            }
        },
    )
}

@Composable
private fun TelemetryConsentCard(
    onShare: () -> Unit,
    onNotNow: () -> Unit,
    onDefer: () -> Unit,
    onReadFaq: (() -> Unit)?,
) {
    val colorScheme = MaterialTheme.colorScheme

    Dialog(
        onDismissRequest = onDefer,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            shape = TdaySheetDefaults.CardShape,
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(TdayDimens.Spacing3xl),
                verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingLg),
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_activity),
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(TdayDimens.IconXl),
                )
                Text(
                    text = stringResource(R.string.telemetry_card_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.telemetry_card_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface.copy(alpha = 0.7f),
                )
                DisclosureBlock(
                    label = stringResource(R.string.telemetry_card_sent_label),
                    body = stringResource(R.string.telemetry_card_sent),
                )
                DisclosureBlock(
                    label = stringResource(R.string.telemetry_card_never_sent_label),
                    body = stringResource(R.string.telemetry_card_never_sent),
                )
                Text(
                    text = stringResource(R.string.telemetry_card_footnote),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurface.copy(alpha = 0.6f),
                )

                // Two buttons of the same size in one row: declining is as easy as agreeing.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TdayDimens.SpacingMd),
                ) {
                    Button(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary,
                        ),
                    ) {
                        Text(stringResource(R.string.telemetry_card_share))
                    }
                    FilledTonalButton(
                        onClick = onNotNow,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.telemetry_card_not_now))
                    }
                }

                if (onReadFaq != null) {
                    TextButton(
                        onClick = onReadFaq,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.telemetry_card_read_faq))
                    }
                }
            }
        }
    }
}

/** A bold heading over the sentence it introduces: what is sent, and what never is. */
@Composable
private fun DisclosureBlock(label: String, body: String) {
    val colorScheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingXxs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}
