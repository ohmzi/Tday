package com.ohmz.tday.security

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CryptoEncodingTest {
    @Test
    fun `toHex is lowercase and zero padded for every byte value`() {
        val bytes = byteArrayOf(0, 1, 15, 16, 127, -128, -1)
        assertEquals("00010f107f80ff", bytes.toHex())
        assertEquals("", ByteArray(0).toHex())

        val everyByte = ByteArray(256) { it.toByte() }
        assertEquals(everyByte.joinToString("") { "%02x".format(it) }, everyByte.toHex())
    }

    @Test
    fun `hexToBytes round trips and rejects malformed input`() {
        val bytes = byteArrayOf(0, 9, 10, -86, -1)
        assertContentEquals(bytes, bytes.toHex().hexToBytes())
        assertContentEquals(bytes, bytes.toHex().uppercase().hexToBytes())

        assertNull("abc".hexToBytes(), "odd length")
        assertNull("zz".hexToBytes(), "not hex")
        assertContentEquals(ByteArray(0), "".hexToBytes())
    }

    @Test
    fun `isHex needs at least one hex digit and nothing else`() {
        assertTrue("00ffAB".isHex())
        assertFalse("".isHex())
        assertFalse("0g".isHex())
        assertFalse(" ab".isHex())
    }
}
