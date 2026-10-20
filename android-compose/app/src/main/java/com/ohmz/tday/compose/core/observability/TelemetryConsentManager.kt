package com.ohmz.tday.compose.core.observability

import android.content.Context
import com.ohmz.tday.compose.core.coroutines.BackgroundDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the UI sees of the crash-report consent: the answer as a flow, whether the connect flow now
 * in progress has been answered, and the one place that changes it. The consent card and the
 * Settings row both go through here, so there is one writer and Settings always agrees with the
 * card.
 *
 * Kept out of `AppViewModel` on purpose: consent is per device, not per workspace, and the app
 * state is rebuilt on every sign-in.
 */
@Singleton
class TelemetryConsentManager @Inject constructor(
    private val bootstrap: TelemetryBootstrap,
    @BackgroundDispatcher dispatcher: CoroutineDispatcher,
) {
    // The manager's own scope, not a ViewModel's: closing the SDK takes seconds, and a screen
    // leaving composition must not cancel a no half way through.
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    // One consumer in front of the bootstrap: answers are applied one at a time, in the order they
    // were given. Separate coroutines racing for a lock would not promise that on a thread pool.
    private val answers = Channel<Boolean>(Channel.UNLIMITED)

    private val _state = MutableStateFlow(bootstrap.state())
    val state: StateFlow<TelemetryConsentState> = _state.asStateFlow()

    // Whether the connect flow now on screen has been answered. The device answer above outlives
    // sign-out on purpose, so it cannot answer this: the answer given on a previous sign-in is not
    // an answer for this one, and the wizard's last step comes due again on every connect flow until
    // this flow is answered. Memory only, and never reported.
    private val _answeredInConnectFlow = MutableStateFlow(false)
    val answeredInConnectFlow: StateFlow<Boolean> = _answeredInConnectFlow.asStateFlow()

    /** False on a build with no DSN. */
    val isAvailable: Boolean = bootstrap.isAvailable

    init {
        scope.launch {
            for (granted in answers) {
                // A failure applying one answer must not stop the next from being applied.
                val persisted = runCatching { bootstrap.apply(granted) }.getOrDefault(true)
                // A yes that could not be stored was undone, so the switch must stop saying on.
                if (!persisted) _state.value = bootstrap.state()
            }
        }
    }

    /**
     * A new connect flow has begun — the sign-in wizard is on screen — so it owes an answer: the
     * question is its to ask again, and the stored answer is not one. Nothing else changes: the
     * device answer, and therefore the SDK the app is running, is left exactly as it was.
     */
    fun beginConnectFlow() {
        _answeredInConnectFlow.value = false
    }

    /**
     * Applies the answer, off the main thread. The flow moves at once: the first thing [bootstrap]
     * does is persist the answer, so it already is the truth, and a switch that waited for the SDK
     * to finish closing would lag by seconds. Answers are applied in the order they were given,
     * so the last tap wins however fast they come.
     *
     * Answering ends the question for this flow whether or not the answer changes anything on disk:
     * saying yes to a device that already said yes is still this flow's answer, and the step does
     * not stay up because the store had nothing left to write.
     */
    fun setShareReports(granted: Boolean) {
        if (!isAvailable) return
        _answeredInConnectFlow.value = true
        _state.value = if (granted) TelemetryConsentState.GRANTED else TelemetryConsentState.DENIED
        answers.trySend(granted)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object TelemetryModule {
    @Provides
    @Singleton
    fun provideTelemetryBootstrap(@ApplicationContext context: Context): TelemetryBootstrap =
        TelemetryBootstrap.shared(context)
}
