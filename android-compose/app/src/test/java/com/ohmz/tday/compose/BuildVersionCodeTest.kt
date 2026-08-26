package com.ohmz.tday.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `versionCode` is derived in `app/build.gradle.kts` from root `version.json`.
 *
 * Android refuses to install an APK whose `versionCode` is not strictly greater
 * than the installed one, so the in-app updater silently stops working — with no
 * useful error — if the encoding ever stops being strictly increasing or drops
 * below something already shipped. These tests re-derive the encoding from
 * `BuildConfig`, so a future formula change has to come with a deliberate update
 * here.
 */
class BuildVersionCodeTest {

    @Test
    fun `should encode the built version name into the built version code`() {
        assertEquals(encode(BuildConfig.VERSION_NAME), BuildConfig.VERSION_CODE.toLong())
    }

    @Test
    fun `should stay above every version code this project has ever shipped`() {
        assertTrue(
            "versionCode ${BuildConfig.VERSION_CODE} must beat every shipped build",
            BuildConfig.VERSION_CODE > HIGHEST_SHIPPED_VERSION_CODE,
        )
    }

    @Test
    fun `should increase strictly across ordered releases`() {
        val ordered = listOf(
            "0.7.2",
            "0.7.3",
            "0.7.99",
            // The pair the old formula collided on: both used to encode to 800.
            "0.7.100",
            "0.7.9999",
            "0.8.0",
            "0.999.0",
            "0.999.9999",
            "1.0.0",
            "1.44.0",
            "2.0.0",
            "209.999.9999",
        )

        ordered.zipWithNext().forEach { (lower, higher) ->
            assertTrue(
                "$lower (${encode(lower)}) must encode below $higher (${encode(higher)})",
                encode(lower) < encode(higher),
            )
        }
    }

    @Test
    fun `should keep apart the versions the old formula collided on`() {
        assertNotEquals(encode("0.7.100"), encode("0.8.0"))
    }

    @Test
    fun `should fit inside the signed 32-bit version code ceiling`() {
        val largest = encode("209.999.9999")

        assertTrue("$largest exceeds the Play ceiling", largest <= PLAY_VERSION_CODE_CEILING)
        assertTrue("$largest exceeds Int.MAX_VALUE", largest <= Int.MAX_VALUE.toLong())
    }

    private fun encode(version: String): Long {
        val (major, minor, patch) = version.split(".").map { it.toInt() }
        require(minor < MINOR_SLOT) { "minor $minor overflows its versionCode slot" }
        require(patch < PATCH_SLOT) { "patch $patch overflows its versionCode slot" }
        return major.toLong() * MAJOR_SCALE + minor.toLong() * PATCH_SLOT + patch.toLong()
    }

    private companion object {
        const val PATCH_SLOT = 10_000
        const val MINOR_SLOT = 1_000
        const val MAJOR_SCALE = PATCH_SLOT * MINOR_SLOT

        // Play caps versionCode at 2_100_000_000, below Int.MAX_VALUE.
        const val PLAY_VERSION_CODE_CEILING = 2_100_000_000L

        // Highest code the old `major * 10000 + minor * 100 + patch` formula ever
        // produced, from the legacy v1.44.0 tag. The current 0.7.2 release scored
        // only 702 under that formula.
        const val HIGHEST_SHIPPED_VERSION_CODE = 14_400
    }
}
