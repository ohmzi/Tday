package com.ohmz.tday.compose.feature.lock

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import com.ohmz.tday.compose.R

/**
 * Which authenticators the app lock accepts.
 *
 * `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` is rejected outright by the framework on API 28–29, so
 * those levels fall back to `BIOMETRIC_WEAK`. No key is bound to the result — the database key is
 * deliberately not user-authentication-gated so the widget keeps rendering on a locked device —
 * so the class-3/class-2 distinction buys nothing here beyond the API-level compatibility.
 */
internal fun appLockAuthenticators(sdkInt: Int = Build.VERSION.SDK_INT): Int =
    if (sdkInt >= Build.VERSION_CODES.R) {
        BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    } else {
        BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }

/**
 * Whether the app should be showing the lock screen as it comes to the foreground.
 *
 * [backgroundedAtElapsedMs] is null on a cold start, which always locks. Returning from the
 * background re-locks once [graceMs] has passed, so the brief detour through `onStop` that a
 * system dialog or a share sheet can cause does not demand a fingerprint every time.
 */
internal fun shouldLockOnForeground(
    lockEnabled: Boolean,
    backgroundedAtElapsedMs: Long?,
    nowElapsedMs: Long,
    graceMs: Long = APP_LOCK_GRACE_MS,
): Boolean {
    if (!lockEnabled) return false
    if (backgroundedAtElapsedMs == null) return true
    return nowElapsedMs - backgroundedAtElapsedMs >= graceMs
}

internal const val APP_LOCK_GRACE_MS = 2_000L

/**
 * Whether this device can actually satisfy the lock right now.
 *
 * If the user turns off their screen lock after enabling the app lock there is nothing left to
 * authenticate against, and a lock we cannot open would leave them permanently shut out of their
 * own tasks with no recovery short of reinstalling. So an unsatisfiable lock fails **open**: the
 * data is still encrypted at rest, which is the control that matters, and this gate is only about
 * the screen of an already-unlocked phone.
 */
internal fun canSatisfyAppLock(biometricManager: BiometricManager, sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    biometricManager.canAuthenticate(appLockAuthenticators(sdkInt)) == BiometricManager.BIOMETRIC_SUCCESS

/**
 * Opaque cover shown over the app while it is locked.
 *
 * Drawn on top of the real UI rather than replacing it so navigation and view-model state survive
 * the lock. It swallows pointer input so nothing underneath can be touched, and it is opaque so
 * nothing underneath can be read.
 */
@Composable
fun AppLockOverlay(onRequestUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onRequestUnlock() }

    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(Float.MAX_VALUE)
            .background(colorScheme.background)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(R.string.app_lock_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRequestUnlock) {
            Text(text = stringResource(R.string.app_lock_unlock))
        }
    }
}
