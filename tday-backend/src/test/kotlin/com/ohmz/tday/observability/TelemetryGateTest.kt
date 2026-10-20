package com.ohmz.tday.observability

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelemetryGateTest {
    @Test
    fun `a new gate is closed so nothing is sent until an admin opts in`() {
        assertFalse(TelemetryGate().isOpen)
    }

    @Test
    fun `set opens and closes the gate`() {
        val gate = TelemetryGate()

        gate.set(true)
        assertTrue(gate.isOpen)

        gate.set(false)
        assertFalse(gate.isOpen)
    }

    @Test
    fun `gates are independent of each other`() {
        val first = TelemetryGate().apply { set(true) }
        val second = TelemetryGate()

        assertTrue(first.isOpen)
        assertFalse(second.isOpen)
    }

    @Test
    fun `a change made on one thread is visible on another`() {
        val gate = TelemetryGate()
        val seen = mutableListOf<Boolean>()
        val done = CountDownLatch(1)

        thread {
            gate.set(true)
            done.countDown()
        }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        thread { seen += gate.isOpen }.join()

        assertEquals(listOf(true), seen)
    }
}
