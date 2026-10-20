package com.ohmz.tday.compose.feature.telemetry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
 * Whether the standalone "send crash reports?" card should be up.
 *
 * It is what is left for the device itself: a device nobody has ever asked, in a workspace of
 * either kind. It never asks on a build with no DSN (nothing could be sent), never after an answer
 * in either place, not for the rest of the session once put off, and not while something more
 * urgent has the screen.
 *
 * The wizard's last step asks the same question in the flow that earned it, and this card is then
 * what a session that never saw the wizard falls back to: already signed in at launch, or a
 * restart in the middle of that step. A caller holding that step up passes it in
 * [shouldShowTelemetryCard]'s `aHigherGateIsUp`, so the two cannot be up at once.
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
 * Whether the crash-report question is this connect flow's to ask, as the wizard's own last step:
 * a build that can send, a flow nobody has answered yet, and a wizard that was actually on screen
 * for it.
 *
 * The device's stored answer is deliberately not part of this. That answer outlives sign-out — it is
 * what Settings reads and writes, and what the SDK obeys — but it is not an answer for a new flow:
 * every sign-in asks again, and only an answer given in this flow (either button on the step, or the
 * Settings switch) takes the step away. A flow that reaches the workspace without one asks rather
 * than sends, because an unanswered device still reads as off.
 *
 * The workspace is deliberately not part of it, for the opposite reason it is part of
 * [shouldShowTelemetryCard]: the step is what the sign-in flow ENDS on, so it comes due in the same
 * breath as the workspace opening. The caller owns that breath (see `privacyStepDue` in `TdayApp`),
 * because only the caller knows whether the workspace opened with a gate still in front of it — a
 * required update or security questions put the question back on the card.
 */
internal fun shouldPresentWizardPrivacyStep(
    available: Boolean,
    answeredInConnectFlow: Boolean,
    wizardWasOnScreen: Boolean,
): Boolean = available && !answeredInConnectFlow && wizardWasOnScreen

/**
 * The consent card, drawn as a dialog the way [com.ohmz.tday.compose.feature.auth.SetSecurityQuestionsGate]
 * is. [aHigherGateIsUp] covers the gates that live in the caller (update required, security
 * questions, the wizard's own consent step); the app lock is read from [LocalAppLocked], because a
 * dialog would otherwise draw over the lock screen.
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
                TelemetryDisclosure()

                // Two identical full-width buttons, stacked: declining is as easy as agreeing, and
                // a long translation of either label wraps inside its own button.
                Column(verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingMd)) {
                    ConsentChoiceButton(
                        text = stringResource(R.string.telemetry_card_share),
                        onClick = onShare,
                    )
                    ConsentChoiceButton(
                        text = stringResource(R.string.telemetry_card_not_now),
                        onClick = onNotNow,
                    )
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

/**
 * Everything the person is told before they answer — the glyph, the question, why it is asked, what
 * a report never carries, and where the answer can be changed later — with no answers and no
 * container of its own.
 *
 * Extracted rather than copied because the sign-in wizard's last step makes the same offer: two
 * places asking for the same thing must not be able to drift into asking for two different things.
 * The answers stay with whichever surface is asking, because the wizard draws them on its own card
 * in its own idiom.
 */
@Composable
internal fun TelemetryDisclosure() {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
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
            label = stringResource(R.string.telemetry_card_never_sent_label),
            body = stringResource(R.string.telemetry_card_never_sent),
        )
        Text(
            text = stringResource(R.string.telemetry_card_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

/** One of the two answers. Both are drawn here so that neither can be styled differently. */
@Composable
private fun ConsentChoiceButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text)
    }
}

/** A bold heading over the sentence it introduces: what is never sent. */
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
