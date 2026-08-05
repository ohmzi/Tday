package com.ohmz.tday.compose.feature.lock

import android.os.Build
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {

    @Test
    fun `lock disabled never locks`() {
        assertFalse(
            shouldLockOnForeground(
                lockEnabled = false,
                backgroundedAtElapsedMs = null,
                nowElapsedMs = 10_000L,
            ),
        )
        assertFalse(
            shouldLockOnForeground(
                lockEnabled = false,
                backgroundedAtElapsedMs = 0L,
                nowElapsedMs = 10_000_000L,
            ),
        )
    }

    @Test
    fun `cold start locks when enabled`() {
        assertTrue(
            shouldLockOnForeground(
                lockEnabled = true,
                backgroundedAtElapsedMs = null,
                nowElapsedMs = 0L,
            ),
        )
    }

    @Test
    fun `returning after the grace period locks`() {
        assertTrue(
            shouldLockOnForeground(
                lockEnabled = true,
                backgroundedAtElapsedMs = 1_000L,
                nowElapsedMs = 1_000L + APP_LOCK_GRACE_MS,
            ),
        )
    }

    @Test
    fun `a brief detour through the background does not re-lock`() {
        assertFalse(
            shouldLockOnForeground(
                lockEnabled = true,
                backgroundedAtElapsedMs = 1_000L,
                nowElapsedMs = 1_000L + APP_LOCK_GRACE_MS - 1,
            ),
        )
    }

    @Test
    fun `device credential is always an accepted fallback`() {
        listOf(Build.VERSION_CODES.O, Build.VERSION_CODES.P, Build.VERSION_CODES.Q, Build.VERSION_CODES.R, 35)
            .forEach { sdkInt ->
                assertEquals(
                    "sdk $sdkInt",
                    DEVICE_CREDENTIAL,
                    appLockAuthenticators(sdkInt) and DEVICE_CREDENTIAL,
                )
            }
    }

    @Test
    fun `API 28 and 29 avoid the STRONG plus DEVICE_CREDENTIAL combination the framework rejects`() {
        listOf(Build.VERSION_CODES.P, Build.VERSION_CODES.Q).forEach { sdkInt ->
            assertEquals(
                "sdk $sdkInt",
                BIOMETRIC_WEAK or DEVICE_CREDENTIAL,
                appLockAuthenticators(sdkInt),
            )
        }
    }

    @Test
    fun `API 30 and up use strong biometrics`() {
        assertEquals(
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL,
            appLockAuthenticators(Build.VERSION_CODES.R),
        )
    }
}
