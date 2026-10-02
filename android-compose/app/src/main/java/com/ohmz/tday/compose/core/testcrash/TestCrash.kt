package com.ohmz.tday.compose.core.testcrash

// TEST-CRASH: temporary crash-reporting cross-check. The whole file, every TEST-CRASH marker in the
// rest of the tree and the two test_crash_* strings go together in one revert.

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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

object TestCrash {
    private const val FREEZE_MILLIS = 13_000L
    private const val OOM_CHUNK_BYTES = 8 * 1024 * 1024

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
        TdayTelemetry.addBreadcrumb(
            operation = "test_crash",
            level = SentryLevel.WARNING,
            data = mapOf("crash_id" to id.id),
        )
        triggers.getValue(id)(id.message)
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
        TestCrashId.SET_FREEZE to { _: String -> Thread.sleep(FREEZE_MILLIS) },
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

/** The four Settings triggers as one block: fatal, handled, freeze, out of memory. */
@Composable
fun TestCrashSettingsBlock() {
    Column(verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingXs)) {
        TestCrashButton(TestCrashId.SET_CRASH)
        TestCrashButton(TestCrashId.SET_ERROR)
        TestCrashButton(TestCrashId.SET_FREEZE)
        TestCrashButton(TestCrashId.SET_OOM)
    }
}
