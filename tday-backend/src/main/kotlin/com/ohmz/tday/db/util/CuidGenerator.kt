package com.ohmz.tday.db.util

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong

object CuidGenerator {
    private val random = SecureRandom()
    private val counter = AtomicLong(random.nextLong() and 0xFFFFL)
    private val fingerprint: String = generateFingerprint()

    fun newCuid(): String {
        val timestamp = System.currentTimeMillis().toString(36).padStart(8, '0')
        val count = counter.getAndIncrement().and(0xFFFFL).toString(36).padStart(4, '0')
        val rand = buildString {
            repeat(8) {
                append("0123456789abcdefghijklmnopqrstuvwxyz"[random.nextInt(36)])
            }
        }
        return "c$timestamp$count$fingerprint$rand"
    }

    private fun generateFingerprint(): String {
        val pid = ProcessHandle.current().pid()
        val hostname = try {
            java.net.InetAddress.getLocalHost().hostName
        } catch (_: Exception) {
            "unknown"
        }
        val combined = (pid.toString(36) + hostname.hashCode().toString(36))
        return combined.takeLast(4).padStart(4, '0')
    }
}
