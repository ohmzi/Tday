package com.ohmz.tday.compose.feature.widget

import com.ohmz.tday.compose.core.ui.TdayMotionTokens
import java.util.concurrent.ConcurrentHashMap

/**
 * Tasks checked off on a widget that are still showing their check: ring filled and ticked, text
 * struck through, for [holdMs] before the completion is written and the row leaves the list. The
 * same beat the iOS widget plays (`WidgetPendingCompletionStore.beginChecking`), timed by the same
 * shared token.
 *
 * In memory on purpose. Every render runs in this process, and the tap's broadcast keeps it alive
 * for the whole beat (see [WidgetTaskActions.complete]); a process that dies mid-beat takes the
 * entry with it, so a row can never be stranded in the checked state.
 */
internal object WidgetCheckOff {
    val holdMs: Long = TdayMotionTokens.Delays.WidgetCheckHold.toLong()

    private val checking: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Starts [taskId]'s beat. False when it is already running — a second tap on the same ring. */
    fun begin(taskId: String): Boolean = checking.add(taskId)

    fun end(taskId: String) {
        checking.remove(taskId)
    }

    fun ids(): Set<String> = checking.toSet()
}
