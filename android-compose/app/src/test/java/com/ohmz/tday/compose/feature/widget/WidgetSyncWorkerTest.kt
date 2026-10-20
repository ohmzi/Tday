package com.ohmz.tday.compose.feature.widget

import androidx.work.ListenableWorker
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSyncWorkerTest {
    @Test
    fun `success outcome always succeeds regardless of attempt count`() {
        val result = WidgetSyncWorker.mapWidgetSyncOutcome(
            outcome = Result.success(Unit),
            runAttemptCount = 5,
        )

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `failure outcome retries under the limit then gives up at it`() {
        val outcome = Result.failure<Unit>(RuntimeException("offline"))

        val underLimit = WidgetSyncWorker.mapWidgetSyncOutcome(outcome, runAttemptCount = 2, maxAttempts = 3)
        val atLimit = WidgetSyncWorker.mapWidgetSyncOutcome(outcome, runAttemptCount = 3, maxAttempts = 3)

        assertTrue(underLimit is ListenableWorker.Result.Retry)
        assertTrue(atLimit is ListenableWorker.Result.Failure)
    }
}
