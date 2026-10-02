package com.ohmz.tday.compose.core.testcrash

// TEST-CRASH: safety net for the temporary crash triggers; removed with them.

import com.ohmz.tday.compose.core.observability.TelemetryScrubber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.DateTimeException
import java.util.ConcurrentModificationException

class TestCrashTest {

    private val expected: Map<TestCrashId, Class<out Throwable>> = mapOf(
        TestCrashId.FEED_SCHED to IllegalStateException::class.java,
        TestCrashId.FEED_ANY to NullPointerException::class.java,
        TestCrashId.BUILTIN_TODAY to IndexOutOfBoundsException::class.java,
        TestCrashId.BUILTIN_OVERDUE to ArithmeticException::class.java,
        TestCrashId.BUILTIN_SCHEDULED to ClassCastException::class.java,
        TestCrashId.BUILTIN_ALL to NumberFormatException::class.java,
        TestCrashId.BUILTIN_PRIORITY to UnsupportedOperationException::class.java,
        TestCrashId.BUILTIN_DONE to ConcurrentModificationException::class.java,
        TestCrashId.LIST_SCHED to IllegalArgumentException::class.java,
        TestCrashId.LIST_ANY to NoSuchElementException::class.java,
        TestCrashId.TASK_OPEN to ArrayIndexOutOfBoundsException::class.java,
        TestCrashId.TASK_EDIT to NegativeArraySizeException::class.java,
        TestCrashId.NEW_LIST to ArrayStoreException::class.java,
        TestCrashId.NEW_TASK to IllegalMonitorStateException::class.java,
        TestCrashId.CALENDAR to DateTimeException::class.java,
        TestCrashId.SET_CRASH to SecurityException::class.java,
    )

    // Not thrown from a unit test: SET_ERROR is a handled capture, SET_FREEZE blocks the main
    // thread and SET_OOM exhausts the heap.
    private val notThrown = setOf(TestCrashId.SET_ERROR, TestCrashId.SET_FREEZE, TestCrashId.SET_OOM)

    @Test
    fun `every thrown id raises its documented type with the exact message`() {
        for ((id, type) in expected) {
            try {
                TestCrash.fire(id)
                fail("${id.id} did not throw")
            } catch (t: Throwable) {
                assertEquals(id.id, type, t.javaClass)
                assertEquals("TEST-CRASH ${id.id}: ${id.what}", t.message)
            }
        }
    }

    @Test
    fun `every id is mapped to a kind or is one of the three special triggers`() {
        assertEquals(TestCrashId.entries.toSet(), expected.keys + notThrown)
    }

    @Test
    fun `ids and messages survive the scrubber unchanged`() {
        for (id in TestCrashId.entries) {
            assertTrue(id.id, id.id.length <= 18)
            assertTrue(id.id, id.message.length <= 80)
            assertTrue(id.id, id.message.all { it.code in 32..126 })
            assertEquals(id.id, id.message, TelemetryScrubber.scrubText(id.message))
        }
    }

    @Test
    fun `the handled error type is an IOException message the scrubber leaves alone`() {
        val message = IOException(TestCrashId.SET_ERROR.message).message
        assertEquals(message, TelemetryScrubber.scrubText(message.orEmpty()))
    }
}
