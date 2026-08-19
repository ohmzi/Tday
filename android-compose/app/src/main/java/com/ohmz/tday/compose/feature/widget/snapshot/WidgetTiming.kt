package com.ohmz.tday.compose.feature.widget.snapshot

import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.ohmz.tday.compose.BuildConfig

/**
 * Debug-only checkpoint logging for the post-reboot widget render path (Stage 0 of the widget
 * rewrite). Every timestamp is milliseconds since THIS PROCESS started
 * ([Process.getStartElapsedRealtime]), not since boot — the number that actually answers "how much
 * of the post-reboot delay is unavoidable process/Hilt cold start versus the widget's own read
 * path". No-ops in release builds; never on a hot path outside of process cold start, so the
 * [BuildConfig.DEBUG] check is not worth caching.
 */
internal object WidgetTiming {
    private const val TAG = "WidgetTiming"

    fun mark(label: String) {
        if (!BuildConfig.DEBUG) return
        val sinceStartMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        Log.d(TAG, "$label at +${sinceStartMs}ms")
    }

    inline fun <T> time(label: String, block: () -> T): T {
        if (!BuildConfig.DEBUG) return block()
        val start = SystemClock.elapsedRealtime()
        val result = block()
        val elapsed = SystemClock.elapsedRealtime() - start
        mark("$label done (took ${elapsed}ms)")
        return result
    }
}
