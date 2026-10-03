package com.ohmz.tday.compose.core.testcrash

// TEST-CRASH: temporary crash-reporting cross-check. The whole file, every TEST-CRASH marker in the
// rest of the tree and the two test_crash_* strings go together in one revert.

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import com.ohmz.tday.compose.ui.theme.TdayDimens
import io.sentry.SentryLevel
import java.io.IOException
import java.time.DateTimeException
import java.time.LocalDate
import java.util.Collections
import java.util.ConcurrentModificationException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Every test trigger. [id] is the stable string the owner searches for in Sentry (identical on web,
 * Android and iOS); the message of the error each one raises is exactly `TEST-CRASH <id>: <what>`.
 */
enum class TestCrashId(val id: String, val what: String) {
    FEED_SCHED("TC-FEED-SCHED", "scheduled home feed"),
    FEED_ANY("TC-FEED-ANY", "anytime feed"),
    BUILTIN_TODAY("TC-BUILTIN-TODAY", "built-in Today list"),
    BUILTIN_OVERDUE("TC-BUILTIN-OVERDUE", "built-in Overdue list"),
    BUILTIN_SCHEDULED("TC-BUILTIN-SCHED", "built-in Scheduled list"),
    BUILTIN_ALL("TC-BUILTIN-ALL", "built-in All tasks list"),
    BUILTIN_PRIORITY("TC-BUILTIN-PRIO", "built-in Priority list"),
    BUILTIN_DONE("TC-BUILTIN-DONE", "built-in Completed list"),
    LIST_SCHED("TC-LIST-SCHED", "user scheduled list"),
    LIST_ANY("TC-LIST-ANY", "user anytime list"),
    TASK_OPEN("TC-TASK-OPEN", "first task opened"),
    TASK_EDIT("TC-TASK-EDIT", "first task edited"),
    NEW_LIST("TC-NEW-LIST", "create list"),
    NEW_TASK("TC-NEW-TASK", "create task"),
    CALENDAR("TC-CALENDAR", "calendar"),
    SET_CRASH("TC-SET-CRASH", "settings fatal crash"),
    SET_ERROR("TC-SET-ERROR", "settings handled error"),
    SET_FREEZE("TC-SET-FREEZE", "settings main thread freeze"),
    SET_OOM("TC-SET-OOM", "settings out of memory"),
    ;

    val message: String get() = "TEST-CRASH $id: $what"
}

/**
 * What the freeze trigger is doing: [active] while it holds the main thread, then the result of the
 * last run — how much of its budget it blocked for, and whether Stop ended it early.
 */
data class FreezeState(
    val active: Boolean = false,
    val blockedMillis: Long = 0L,
    val cancelled: Boolean = false,
)

object TestCrash {
    internal const val FREEZE_MILLIS = 13_000L

    /**
     * Slices stay short so the looper can take over between them: a Stop tap queued behind a slice
     * is handled there, and the slice list keeps the block a real main-thread block.
     */
    internal const val FREEZE_SLICE_MILLIS = 500L

    /**
     * How long the freeze holds the thread with no yield at all, before it starts handing slices
     * back to the looper. Android records an ANR when a window stops responding to input for about
     * 5 s (the input-dispatch timeout), and this trigger exists to produce that ANR, so the solid
     * window has to be comfortably past the threshold: the pending input event it fires on is the
     * tap that fired the trigger, which cannot finish while the window is solid. After the window
     * the rest of the budget yields between slices, which is where a Stop tap that queued up behind
     * the block finally lands.
     */
    internal const val FREEZE_HOLD_OFF_MILLIS = 6_000L

    /**
     * A pause between yielding slices, so the frame pipeline gets a turn.
     *
     * Posting the next slice immediately hands the looper a runnable that blocks again before the
     * vsync and recomposition passes behind it have run, which is why a device check found the Stop
     * control never drawn: the tree still showed the pre-freeze layout when the slices were running.
     * Two frames' worth of idle time per slice is enough to compose and draw it, and it costs about
     * 6 % of the wall clock, none of the budget.
     */
    internal const val FREEZE_SLICE_GAP_MILLIS = 32L

    private const val OOM_CHUNK_BYTES = 8 * 1024 * 1024

    private val _freezeState = MutableStateFlow(FreezeState())

    /** Compose-observable freeze status, read by the Settings block. */
    val freezeState: StateFlow<FreezeState> = _freezeState.asStateFlow()

    private val freezeCancelRequested = AtomicBoolean(false)

    @Volatile
    private var freezeRunning = false

    fun idForMode(mode: TodoListMode, listId: String?): TestCrashId = when (mode) {
        TodoListMode.TODAY -> TestCrashId.BUILTIN_TODAY
        TodoListMode.OVERDUE -> TestCrashId.BUILTIN_OVERDUE
        TodoListMode.SCHEDULED -> TestCrashId.BUILTIN_SCHEDULED
        TodoListMode.ALL -> TestCrashId.BUILTIN_ALL
        TodoListMode.PRIORITY -> TestCrashId.BUILTIN_PRIORITY
        TodoListMode.LIST -> TestCrashId.LIST_SCHED
        TodoListMode.FLOATER -> if (listId.isNullOrBlank()) TestCrashId.FEED_ANY else TestCrashId.LIST_ANY
    }

    /** First task opened by tapping its row (a no-op for every other row). */
    fun onTaskOpen(isFirst: Boolean) {
        if (isFirst) fire(TestCrashId.TASK_OPEN)
    }

    /** First task edited through the swipe Edit action (a no-op for every other row). */
    fun onTaskEdit(isFirst: Boolean) {
        if (isFirst) fire(TestCrashId.TASK_EDIT)
    }

    /**
     * Runs the trigger. A genuine fault is provoked where the language allows it and rethrown as
     * the same type carrying the documented message; the breadcrumb lands first so even a fault
     * whose message the runtime owns (the heap) still names its ID.
     */
    fun fire(id: TestCrashId) {
        // The freeze's own button is the second way out of it. A frozen thread cannot redraw, so the
        // frame the tap lands on may still be the one that started the freeze: a Stop tapped there is
        // dispatched to this button, and it has to cancel rather than be ignored.
        if (id == TestCrashId.SET_FREEZE) {
            fireFreeze()
            return
        }
        TdayTelemetry.addBreadcrumb(
            operation = "test_crash",
            level = SentryLevel.WARNING,
            data = mapOf("crash_id" to id.id),
        )
        triggers.getValue(id)(id.message)
    }

    /**
     * The freeze button's own tap: ends a run that is already going, or starts one. Returns whether
     * it started a run, so the caller (and the test) can tell the two apart.
     */
    internal fun fireFreeze(): Boolean {
        if (cancelFreeze()) return false
        freeze()
        return true
    }

    /**
     * Ends a running freeze early. Returns whether one was actually running, so a Stop that lands
     * after the last slice is not mistaken for one that cut the block short.
     */
    fun cancelFreeze(): Boolean {
        if (!freezeRunning) return false
        freezeCancelRequested.set(true)
        return true
    }

    /**
     * Runs the freeze: [budgetMillis] of blocking work, in slices of at most [sliceMillis], starting
     * with a solid window of [holdOffMillis] that runs on the calling thread with no yield at all —
     * see [FREEZE_HOLD_OFF_MILLIS] for why. Every slice really holds the thread; once the window is
     * over, the next slice is handed to [schedule] so the looper gets a turn between slices (that is
     * where a queued Stop tap lands) and the cancellation flag is read there, so Stop ends the block
     * instead of waiting it out. A second freeze while one is running is ignored. Budget, slice
     * length, hold-off, scheduling and the blocking work are parameters so a test can drive the same
     * loop without spending the real 13 s.
     */
    internal fun freeze(
        budgetMillis: Long = FREEZE_MILLIS,
        sliceMillis: Long = FREEZE_SLICE_MILLIS,
        holdOffMillis: Long = FREEZE_HOLD_OFF_MILLIS,
        schedule: ((() -> Unit) -> Unit) = ::scheduleOnMainThread,
        block: (Long) -> Unit = { Thread.sleep(it) },
    ) {
        if (freezeRunning) return
        freezeCancelRequested.set(false)
        freezeRunning = true
        _freezeState.value = FreezeState(active = true)
        var blocked = 0L
        var cancelled = false
        val solidUntil = minOf(holdOffMillis, budgetMillis)

        fun finish() {
            freezeRunning = false
            freezeCancelRequested.set(false)
            _freezeState.value = FreezeState(
                active = false,
                blockedMillis = blocked,
                cancelled = cancelled,
            )
        }

        /** Blocks the thread for one slice, never past [limit], and records it. */
        fun blockSlice(limit: Long) {
            val current = minOf(sliceMillis, limit - blocked).coerceAtLeast(1L)
            try {
                block(current)
            } catch (interrupted: InterruptedException) {
                // The one way the production block ends early: a block that was interrupted must not
                // leave the freeze marked as running.
                finish()
                throw interrupted
            }
            blocked += current
            _freezeState.value = FreezeState(active = true, blockedMillis = blocked)
        }

        /** The yielding part: one slice, then the looper gets its turn, then the next slice. */
        fun slice() {
            if (blocked >= budgetMillis) {
                finish()
            } else if (freezeCancelRequested.get()) {
                cancelled = true
                finish()
            } else {
                blockSlice(budgetMillis)
                if (blocked < budgetMillis) schedule { slice() } else finish()
            }
        }

        // Solid first window, still inside the dispatch of the tap that fired the trigger, so
        // nothing — not even the Stop control — can be handled until it is over.
        while (blocked < solidUntil) blockSlice(solidUntil)
        if (blocked < budgetMillis) schedule { slice() } else finish()
    }

    /**
     * Hands one slice to the main looper, so the app gets a turn — a frame can be drawn, and a Stop
     * tap queued behind the block gets handled — before the next slice blocks again. Off the main
     * looper it runs the slice inline.
     */
    private fun scheduleOnMainThread(next: () -> Unit) {
        val looper = Looper.getMainLooper()
        if (looper == null) next() else Handler(looper).postDelayed(next, FREEZE_SLICE_GAP_MILLIS)
    }

    /**
     * One entry per trigger, so no screen shares a kind with another. A fault the runtime can
     * provoke without a catch is provoked; the types a catch block would have to name
     * (NullPointerException, IndexOutOfBoundsException, ArrayIndexOutOfBoundsException) are thrown
     * directly with the documented message.
     */
    private val triggers: Map<TestCrashId, (String) -> Unit> = mapOf(
        TestCrashId.FEED_SCHED to { message: String -> throw IllegalStateException(message) },
        TestCrashId.FEED_ANY to { message: String -> throw NullPointerException(message) },
        TestCrashId.BUILTIN_TODAY to { message: String -> throw IndexOutOfBoundsException(message) },
        TestCrashId.BUILTIN_OVERDUE to { message: String -> divideByZero(message) },
        TestCrashId.BUILTIN_SCHEDULED to { message: String -> badCast(message) },
        TestCrashId.BUILTIN_ALL to { message: String -> badNumber(message) },
        TestCrashId.BUILTIN_PRIORITY to { message: String -> unsupported(message) },
        TestCrashId.BUILTIN_DONE to { message: String -> concurrentModification(message) },
        TestCrashId.LIST_SCHED to { message: String -> throw IllegalArgumentException(message) },
        TestCrashId.LIST_ANY to { message: String -> throw NoSuchElementException(message) },
        TestCrashId.TASK_OPEN to { message: String -> throw ArrayIndexOutOfBoundsException(message) },
        TestCrashId.TASK_EDIT to { message: String -> throw NegativeArraySizeException(message) },
        TestCrashId.NEW_LIST to { message: String -> arrayStore(message) },
        TestCrashId.NEW_TASK to { message: String -> throw IllegalMonitorStateException(message) },
        TestCrashId.CALENDAR to { message: String -> badDate(message) },
        TestCrashId.SET_CRASH to { message: String -> throw SecurityException(message) },
        TestCrashId.SET_ERROR to { message: String -> TdayTelemetry.capture(IOException(message), operation = "test_crash") },
        TestCrashId.SET_FREEZE to { _: String -> freeze() },
        TestCrashId.SET_OOM to { _: String -> exhaustHeap() },
    )

    private fun zero(): Int = (System.nanoTime() and 0L).toInt()

    private fun divideByZero(message: String) {
        try {
            1 / zero()
        } catch (e: ArithmeticException) {
            throw ArithmeticException(message).also { it.initCause(e) }
        }
    }

    private fun badCast(message: String) {
        try {
            val value: Any = zero()
            String::class.java.cast(value)
        } catch (e: ClassCastException) {
            throw ClassCastException(message).also { it.initCause(e) }
        }
    }

    private fun badNumber(message: String) {
        try {
            "not-a-number".toInt()
        } catch (e: NumberFormatException) {
            throw NumberFormatException(message).also { it.initCause(e) }
        }
    }

    private fun unsupported(message: String) {
        try {
            val readOnly: MutableList<Int> = Collections.unmodifiableList(mutableListOf(1))
            readOnly.add(2)
        } catch (e: UnsupportedOperationException) {
            throw UnsupportedOperationException(message).also { it.initCause(e) }
        }
    }

    private fun concurrentModification(message: String) {
        try {
            val items = mutableListOf(1, 2, 3)
            for (item in items) {
                if (item == 1) items.add(4)
            }
        } catch (e: ConcurrentModificationException) {
            throw ConcurrentModificationException(message).also { it.initCause(e) }
        }
    }

    private fun arrayStore(message: String) {
        try {
            System.arraycopy(arrayOf<Any>(1), 0, arrayOf("a"), 0, 1)
        } catch (e: ArrayStoreException) {
            throw ArrayStoreException(message).also { it.initCause(e) }
        }
    }

    private fun badDate(message: String) {
        try {
            LocalDate.of(2026, 2, 30 + zero())
        } catch (e: DateTimeException) {
            throw DateTimeException(message, e)
        }
    }

    private fun exhaustHeap() {
        val hog = ArrayList<ByteArray>()
        while (true) hog.add(ByteArray(OOM_CHUNK_BYTES))
    }
}

/** True for the first task a list displays; read by the task row to arm the open/edit crashes. */
val LocalTestCrashFirstRow = compositionLocalOf { false }

/** Places the button once per list, right after the first task (or at the end of an empty list). */
class TestCrashSlot(private val id: TestCrashId) {
    private var claimed = false

    fun claimFirst(): Boolean {
        if (claimed) return false
        claimed = true
        return true
    }

    fun placeButton(scope: LazyListScope) = scope.testCrashItem(id)

    fun placeIfNoTasks(scope: LazyListScope) {
        if (claimFirst()) placeButton(scope)
    }
}

fun LazyListScope.testCrashItem(id: TestCrashId) {
    item(key = "test-crash-${id.id}", contentType = "test-crash") {
        TestCrashButton(id)
    }
}

@Composable
fun TestCrashButton(id: TestCrashId, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = TdayDimens.SpacingMd),
        verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingXs),
    ) {
        Button(
            onClick = { TestCrash.fire(id) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(TdayDimens.RadiusMd),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text(text = stringResource(R.string.test_crash_button, id.id))
        }
        Text(
            text = stringResource(R.string.test_crash_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The four Settings triggers as one block: fatal, handled, freeze (with its Stop), out of memory. */
@Composable
fun TestCrashSettingsBlock() {
    val freeze by TestCrash.freezeState.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingXs)) {
        TestCrashButton(TestCrashId.SET_CRASH)
        TestCrashButton(TestCrashId.SET_ERROR)
        // The Stop takes the trigger's place while a freeze is running, the way iOS does it. A frozen
        // thread may still be showing the frame that started the freeze, and a tap that lands there is
        // delivered to the trigger itself — which cancels a running freeze for exactly that reason.
        if (freeze.active) {
            TestCrashStopFreezeButton(onStop = { TestCrash.cancelFreeze() })
        } else {
            TestCrashButton(TestCrashId.SET_FREEZE)
        }
        if (!freeze.active && (freeze.cancelled || freeze.blockedMillis > 0L)) {
            TestCrashFreezeResult(freeze)
        }
        TestCrashButton(TestCrashId.SET_OOM)
    }
}

/** The way out of a running freeze, shown for as long as the block holds the main thread. */
@Composable
private fun TestCrashStopFreezeButton(onStop: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onStop,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = TdayDimens.SpacingMd),
        shape = RoundedCornerShape(TdayDimens.RadiusMd),
    ) {
        Text(text = stringResource(R.string.test_crash_freeze_stop))
    }
}

/** What the last freeze did: how long it held the main thread, and whether Stop ended it early. */
@Composable
private fun TestCrashFreezeResult(state: FreezeState, modifier: Modifier = Modifier) {
    val seconds = String.format(Locale.getDefault(), "%.1f", state.blockedMillis / 1_000.0)
    Text(
        text = stringResource(
            if (state.cancelled) {
                R.string.test_crash_freeze_cancelled
            } else {
                R.string.test_crash_freeze_blocked
            },
            seconds,
        ),
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
