package com.ohmz.tday.compose.feature.telemetry

import com.ohmz.tday.compose.core.observability.TelemetryConsentState
import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryConsentGateTest {
    private fun shows(
        available: Boolean = true,
        state: TelemetryConsentState = TelemetryConsentState.UNANSWERED,
        deferredThisSession: Boolean = false,
        workspaceOpen: Boolean = true,
        aHigherGateIsUp: Boolean = false,
    ) = shouldShowTelemetryCard(available, state, deferredThisSession, workspaceOpen, aHigherGateIsUp)

    @Test
    fun `shows once, in an open workspace, to a device nobody has asked yet`() {
        assertEquals(true, shows())
    }

    @Test
    fun `never shows on a build without a dsn`() {
        assertEquals(false, shows(available = false))
    }

    @Test
    fun `never shows again once the question has been answered, either way`() {
        assertEquals(false, shows(state = TelemetryConsentState.GRANTED))
        assertEquals(false, shows(state = TelemetryConsentState.DENIED))
    }

    @Test
    fun `stays away for the rest of the session once deferred`() {
        assertEquals(false, shows(deferredThisSession = true))
    }

    @Test
    fun `waits for a workspace, so it never appears over the sign in wizard`() {
        assertEquals(false, shows(workspaceOpen = false))
    }

    @Test
    fun `yields to update required, security questions and the app lock`() {
        assertEquals(false, shows(aHigherGateIsUp = true))
    }
}

/**
 * The wizard's own version of the same question. The workspace is absent from these cases on
 * purpose: whether the workspace opened without a gate in front of it is the caller's half of the
 * decision (`privacyStepDue` in `TdayApp`), and what is pinned here is the half about the device and
 * the flow it is in.
 */
class TelemetryWizardPrivacyStepTest {
    private fun offers(
        available: Boolean = true,
        answeredInConnectFlow: Boolean = false,
        wizardWasOnScreen: Boolean = true,
    ) = shouldPresentWizardPrivacyStep(available, answeredInConnectFlow, wizardWasOnScreen)

    @Test
    fun `offers the step to a wizard that was on screen for a flow nobody has answered`() {
        assertEquals(true, offers())
    }

    @Test
    fun `never offers it on a build without a dsn`() {
        assertEquals(false, offers(available = false))
    }

    @Test
    fun `stops offering it once this flow has been answered, either way`() {
        assertEquals(false, offers(answeredInConnectFlow = true))
    }

    @Test
    fun `offers it again on a new flow, because the stored answer is not an input here`() {
        // Every connect flow starts with `answeredInConnectFlow` false, whatever the device answered
        // before: that is what makes a sign-in owe the question again. The stored answer is not part
        // of this decision at all — it is what the SDK obeys, and it stays untouched.
        assertEquals(true, offers(answeredInConnectFlow = false))
    }

    @Test
    fun `never offers it to an install that never saw the wizard`() {
        // Already signed in at launch, or restarted mid-step: the card asks, not the stepper.
        assertEquals(false, offers(wizardWasOnScreen = false))
    }
}

/**
 * The two answers carry equal weight. How a button looks is not something a JVM can check, so this
 * pins how the file is written: both choices come from one helper, in a full-width column, and
 * nothing else in the card is a filled button that could tip the balance.
 */
class TelemetryConsentChoiceWeightTest {
    private val source: String = generateSequence(java.io.File(".").canonicalFile) { it.parentFile }
        .flatMap { sequenceOf(java.io.File(it, "src/main"), java.io.File(it, "app/src/main")) }
        .map { java.io.File(it, "java/com/ohmz/tday/compose/feature/telemetry/TelemetryConsentGate.kt") }
        .firstOrNull { it.isFile }
        ?.readText()
        ?: error("could not locate TelemetryConsentGate.kt")

    @Test
    fun `share and not now are drawn by the same helper, share first`() {
        val calls = Regex("""ConsentChoiceButton\(\s*text = stringResource\(R\.string\.(\w+)\)""")
            .findAll(source).map { it.groupValues[1] }.toList()
        assertEquals(listOf("telemetry_card_share", "telemetry_card_not_now"), calls)
    }

    @Test
    fun `only the helper builds a filled button, so the two cannot differ in style`() {
        assertEquals(1, Regex("""(?<![A-Za-z])Button\(""").findAll(source).count())
        assertEquals(false, source.contains("FilledTonalButton"))
        assertEquals(false, source.contains("weight(1f)"))
    }
}
