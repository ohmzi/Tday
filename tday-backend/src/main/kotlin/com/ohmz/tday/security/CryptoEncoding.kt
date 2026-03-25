package com.ohmz.tday.security

fun ByteArray.toHex(): String = joinToString("") { b -> "%02x".format(b) }

fun String.hexToBytes(): ByteArray? {
    val normalized = trim()
    if (normalized.length % 2 != 0) return null
    if (!normalized.matches(Regex("^[0-9a-fA-F]*$"))) return null
    return ByteArray(normalized.length / 2) { i ->
        normalized.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}

fun String.isHex(): Boolean = Regex("^[0-9a-fA-F]+$").matches(this)
