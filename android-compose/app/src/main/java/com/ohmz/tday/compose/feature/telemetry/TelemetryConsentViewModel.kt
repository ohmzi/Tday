package com.ohmz.tday.compose.feature.telemetry

import androidx.lifecycle.ViewModel
import com.ohmz.tday.compose.core.observability.TelemetryConsentManager
import com.ohmz.tday.compose.core.observability.TelemetryConsentState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * The consent card and the Settings row share this, and both reach the answer only through
 * [TelemetryConsentManager]: answering in either place counts as answering, and the two cannot
 * disagree.
 *
 * The one thing it holds itself is whether the card was put off. That belongs to the screen the
 * card is drawn on, not to the device: it lasts until the app is started again, which is exactly
 * how long this instance, kept by the home destination, lives.
 */
@HiltViewModel
class TelemetryConsentViewModel @Inject constructor(
    private val manager: TelemetryConsentManager,
) : ViewModel() {

    /** False on a build with no DSN: no card, no Settings row. */
    val isAvailable: Boolean = manager.isAvailable

    val state: StateFlow<TelemetryConsentState> = manager.state

    /**
     * Whether the connect flow now on screen has been answered. Every sign-in asks the crash-report
     * question again, so the answer the device carries is not an answer for the flow in progress.
     */
    val answeredInConnectFlow: StateFlow<Boolean> = manager.answeredInConnectFlow

    private val _cardDeferred = MutableStateFlow(false)
    val cardDeferred: StateFlow<Boolean> = _cardDeferred.asStateFlow()

    /** A new connect flow is on screen: it owes an answer, whatever the device answered before. */
    fun beginConnectFlow() {
        manager.beginConnectFlow()
    }

    fun setShareReports(granted: Boolean) {
        manager.setShareReports(granted)
    }

    /** Back press and "Read the full FAQ": ask again next start, not never. */
    fun deferCard() {
        _cardDeferred.value = true
    }
}
