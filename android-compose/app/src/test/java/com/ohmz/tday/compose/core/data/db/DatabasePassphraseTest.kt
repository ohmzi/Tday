package com.ohmz.tday.compose.core.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class DatabasePassphraseTest {

    @Test
    fun `every byte survives encoding, including NUL and the high half of the range`() {
        val keyBytes = byteArrayOf(0x00, 0x0f, 0x10, 0x7f, 0x80.toByte(), 0xff.toByte())

        // A raw-bytes passphrase would be cut at the leading NUL; hex keeps all of it.
        assertEquals("000f107f80ff", encodePassphrase(keyBytes))
    }

    @Test
    fun `a 32-byte key encodes to 64 hex characters`() {
        val keyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }

        val encoded = encodePassphrase(keyBytes)

        assertEquals(64, encoded.length)
        assertTrue(isHexPassphrase(encoded))
    }

    @Test
    fun `generated passphrases are always quote-free, so the ATTACH literal cannot be broken out of`() {
        repeat(200) {
            val encoded = encodePassphrase(ByteArray(32).also { bytes -> SecureRandom().nextBytes(bytes) })

            assertTrue(encoded, isHexPassphrase(encoded))
            assertFalse(encoded, encoded.contains('\''))
        }
    }

    @Test
    fun `anything outside lowercase hex is rejected`() {
        listOf("", "AABB", "abcg", "ab cd", "ab'cd", "ab-cd", "'; DROP TABLE x --")
            .forEach { candidate ->
                assertFalse(candidate, isHexPassphrase(candidate))
            }
    }
}
