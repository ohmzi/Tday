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
    fun `failure outcome retries while under the attempt limit`() {
        val outcome = Result.failure<Unit>(RuntimeException("offline"))

        val first = WidgetSyncWorker.mapWidgetSyncOutcome(outcome, runAttemptCount = 1, maxAttempts = 3)
        val second = WidgetSyncWorker.mapWidgetSyncOutcome(outcome, runAttemptCount = 2, maxAttempts = 3)

        assertTrue(first is ListenableWorker.Result.Retry)
        assertTrue(second is ListenableWorker.Result.Retry)
    }

    @Test
    fun `failure outcome gives up once the attempt limit is reached`() {
        val outcome = Result.failure<Unit>(RuntimeException("offline"))

        val atLimit = WidgetSyncWorker.mapWidgetSyncOutcome(outcome, runAttemptCount = 3, maxAttempts = 3)
        val pastLimit = WidgetSyncWorker.mapWidgetSyncOutcome(outcome, runAttemptCount = 4, maxAttempts = 3)

        assertTrue(atLimit is ListenableWorker.Result.Failure)
        assertTrue(pastLimit is ListenableWorker.Result.Failure)
    }

    @Test
    fun `custom attempt limit is respected`() {
        val outcome = Result.failure<Unit>(RuntimeException("offline"))

        val underCustomLimit = WidgetSyncWorker.mapWidgetSyncOutcome(outcome, runAttemptCount = 0, maxAttempts = 1)
        val atCustomLimit = WidgetSyncWorker.mapWidgetSyncOutcome(outcome, runAttemptCount = 1, maxAttempts = 1)

        assertTrue(underCustomLimit is ListenableWorker.Result.Retry)
        assertTrue(atCustomLimit is ListenableWorker.Result.Failure)
    }
}
