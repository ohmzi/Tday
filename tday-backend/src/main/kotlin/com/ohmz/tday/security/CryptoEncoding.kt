package com.ohmz.tday.security

private val hexFormat = java.util.HexFormat.of()
private val hexDigitsOrEmpty = Regex("^[0-9a-fA-F]*$")
private val hexDigits = Regex("^[0-9a-fA-F]+$")

/** Lowercase, zero-padded hex (two digits per byte). */
fun ByteArray.toHex(): String = hexFormat.formatHex(this)

fun String.hexToBytes(): ByteArray? {
    val normalized = trim()
    if (normalized.length % 2 != 0) return null
    if (!normalized.matches(hexDigitsOrEmpty)) return null
    return ByteArray(normalized.length / 2) { i ->
        normalized.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}

fun String.isHex(): Boolean = hexDigits.matches(this)
