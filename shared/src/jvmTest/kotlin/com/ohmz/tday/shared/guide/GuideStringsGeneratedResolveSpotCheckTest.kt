package com.ohmz.tday.shared.guide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Locks down [GuideStringsGenerated.resolve]'s dispatch behavior so future
 * regenerations of the underlying storage shape (key-major vs. locale-major,
 * etc.) can't silently change it. Compares against `resolve("en", ...)`
 * rather than hardcoding translated text, so it stays valid as translations
 * are edited.
 */
class GuideStringsGeneratedResolveSpotCheckTest {
    private val sampleKey = "guide.clearSearch"

    @Test
    fun resolvesKnownKeyDifferentlyForDifferentLocales() {
        val en = GuideStringsGenerated.resolve("en", sampleKey)
        val fr = GuideStringsGenerated.resolve("fr", sampleKey)
        assertNotEquals(sampleKey, en)
        assertNotEquals(sampleKey, fr)
        assertNotEquals(en, fr)
    }

    @Test
    fun resolvesRegionTaggedLocaleViaLanguagePrefix() {
        assertEquals(
            GuideStringsGenerated.resolve("fr", sampleKey),
            GuideStringsGenerated.resolve("fr-CA", sampleKey),
        )
        assertEquals(
            GuideStringsGenerated.resolve("es", sampleKey),
            GuideStringsGenerated.resolve("es-MX", sampleKey),
        )
    }

    @Test
    fun fallsBackToEnglishForUnknownLocale() {
        assertEquals(
            GuideStringsGenerated.resolve("en", sampleKey),
            GuideStringsGenerated.resolve("xx-ZZ", sampleKey),
        )
    }

    @Test
    fun returnsKeyItselfWhenKeyUnknown() {
        val bogus = "guide.totally.made.up.key"
        assertEquals(bogus, GuideStringsGenerated.resolve("en", bogus))
        assertEquals(bogus, GuideStringsGenerated.resolve("fr", bogus))
    }

    @Test
    fun isCaseInsensitiveOnLanguagePrefix() {
        assertEquals(
            GuideStringsGenerated.resolve("ja", sampleKey),
            GuideStringsGenerated.resolve("JA", sampleKey),
        )
    }
}
