package com.ohmz.tday.compose.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.ohmz.tday.compose.core.observability.SlowOperation
import com.ohmz.tday.compose.core.observability.SlowOperationKind
import com.ohmz.tday.compose.core.observability.SlowOperationLedger

/**
 * Fires a failure on demand, to check by hand what reaches Sentry (and, with the consent switch off,
 * that nothing does). Debug builds only; the manifest entry lives in `src/debug`.
 *
 * ```
 * adb shell am broadcast -n com.ohmz.tday.compose/.debug.TelemetryDebugReceiver \
 *     -a com.ohmz.tday.compose.debug.TELEMETRY_TRIGGER --es kind crash|anr|slow_op
 * ```
 *
 * `crash` throws on the main thread. `anr` blocks it for long enough that touching the app raises
 * a real "not responding" (Sentry reports that on the next launch). `slow_op` files a
 * `slow_operation` event, bypassing the one-a-day limit that would otherwise swallow a repeat.
 */
class TelemetryDebugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (val kind = intent.getStringExtra(EXTRA_KIND)) {
            KIND_CRASH -> throw IllegalStateException("Debug telemetry trigger: crash")
            KIND_ANR -> Handler(Looper.getMainLooper()).post { Thread.sleep(ANR_BLOCK_MS) }
            KIND_SLOW_OP -> SlowOperation(NoCooldownLedger).report(
                SlowOperationKind.COLD_START,
                durationMs = SLOW_OPERATION_DURATION_MS,
            )
            else -> error("Unknown telemetry trigger kind: $kind")
        }
    }

    private object NoCooldownLedger : SlowOperationLedger {
        override fun lastReportedMs(operation: String): Long? = null

        override fun markReported(operation: String, nowMs: Long) = Unit
    }

    private companion object {
        const val EXTRA_KIND = "kind"
        const val KIND_CRASH = "crash"
        const val KIND_ANR = "anr"
        const val KIND_SLOW_OP = "slow_op"
        const val ANR_BLOCK_MS = 30_000L
        const val SLOW_OPERATION_DURATION_MS = 12_000L
    }
}
