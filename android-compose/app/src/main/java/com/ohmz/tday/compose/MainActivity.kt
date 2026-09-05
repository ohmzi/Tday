package com.ohmz.tday.compose

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ohmz.tday.compose.core.data.AppSecurityPreferenceStore
import com.ohmz.tday.compose.core.data.applyScreenshotProtection
import com.ohmz.tday.compose.core.notification.BootRescheduleReceiver
import com.ohmz.tday.compose.core.notification.NotificationPreferenceStore
import com.ohmz.tday.compose.feature.lock.AppLockOverlay
import com.ohmz.tday.compose.feature.lock.appLockAuthenticators
import com.ohmz.tday.compose.feature.lock.canSatisfyAppLock
import com.ohmz.tday.compose.feature.lock.shouldLockOnForeground
import com.ohmz.tday.compose.feature.widget.WidgetRefresher
import com.ohmz.tday.compose.ui.theme.TdayTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Marked HERE, on the answer, not before the launch. Android only starts
            // returning true from `shouldShowRequestPermissionRationale` after a real
            // denial — a dialog dismissed without one leaves it false. Marking early
            // meant that pair read as "asked, and no rationale allowed" — which the
            // settings switch interprets as permanently denied, and it would send
            // the user to Android settings for a dialog Android would still have
            // shown them.
            notificationPreferences.markPermissionRequested()
        }

    private val _deepLinkIntent = MutableStateFlow<Intent?>(null)
    val deepLinkIntent = _deepLinkIntent.asStateFlow()

    private val securityPreferences by lazy { AppSecurityPreferenceStore(applicationContext) }

    private val notificationPreferences by lazy { NotificationPreferenceStore(applicationContext) }

    @Inject lateinit var widgetRefresher: WidgetRefresher

    private val _locked = MutableStateFlow(false)

    /** Elapsed-realtime stamp of the last onStop; null until the app has been backgrounded once. */
    private var backgroundedAtElapsedMs: Long? = null

    /** Guards against stacking a second BiometricPrompt on top of one already showing. */
    private var unlockPromptVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Tday)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // withoutTaskRestartFlags: Navigation handles the activity's launch intent itself when the
        // graph is set, and its handleDeepLink RESTARTS the whole task (TaskStackBuilder +
        // finish()) whenever that intent carries FLAG_ACTIVITY_NEW_TASK — which every widget and
        // notification PendingIntent must set. Clearing the flags on the activity's own intent is
        // what stops the restart; doing it only at our handleDeepLink call site is not enough.
        val launchIntent = intent.withTdayDeepLinkData().withoutTaskRestartFlags()
        setIntent(launchIntent)
        dispatchDeepLinkIntent(launchIntent)
        setContent {
            TdayApp(
                onFirstFrameDrawn = {
                    (application as? TdayApplication)?.runDeferredStartup()
                    dismissUpdateReadyNotification()
                    requestNotificationPermissionIfNeeded()
                },
            )
            // Layered over TdayApp rather than replacing it so the nav stack and view-model state
            // survive locking; the overlay is opaque and eats touches.
            val locked by _locked.collectAsStateWithLifecycle()
            if (locked) {
                TdayTheme {
                    AppLockOverlay(onRequestUnlock = ::promptForUnlock)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        applyScreenshotProtection(securityPreferences)
        if (shouldLockOnForeground(
                lockEnabled = securityPreferences.isAppLockEnabled(),
                backgroundedAtElapsedMs = backgroundedAtElapsedMs,
                nowElapsedMs = SystemClock.elapsedRealtime(),
            )
        ) {
            _locked.value = true
        }
        // Belt-and-braces: the Settings toggle already forces a widget re-render, but an
        // install that already had the lock on before this feature shipped (or any other
        // drift between the flag and the last-rendered widget) would otherwise sit stale
        // until an unrelated data sync happens to fire. Every foreground is cheap to cover —
        // both refreshers are unconditional, so a re-render with nothing changed is a no-op.
        widgetRefresher.requestRefresh()
    }

    override fun onStop() {
        super.onStop()
        backgroundedAtElapsedMs = SystemClock.elapsedRealtime()
    }

    private fun promptForUnlock() {
        if (unlockPromptVisible) return

        val biometricManager = BiometricManager.from(this)
        // Nothing left to authenticate against (screen lock removed since the setting was turned
        // on): open rather than trap the user out of their own data. Encryption at rest is the
        // control that actually protects the content.
        if (!canSatisfyAppLock(biometricManager)) {
            _locked.value = false
            return
        }

        unlockPromptVisible = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlockPromptVisible = false
                    _locked.value = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Stay locked; the overlay keeps a retry button. Cancelling must not be a way
                    // past the lock.
                    unlockPromptVisible = false
                }
            },
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.app_lock_prompt_title))
                .setSubtitle(getString(R.string.app_lock_prompt_subtitle))
                .setAllowedAuthenticators(appLockAuthenticators())
                .build(),
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val deepLinkIntent = intent.withTdayDeepLinkData().withoutTaskRestartFlags()
        setIntent(deepLinkIntent)
        dismissUpdateReadyNotification()
        dispatchDeepLinkIntent(deepLinkIntent)
    }

    private fun dispatchDeepLinkIntent(intent: Intent) {
        _deepLinkIntent.value = intent.withTdayDeepLinkData()
    }

    /** Clears the pending deep link once it has been navigated, so it fires exactly once. */
    fun consumeDeepLink() {
        _deepLinkIntent.value = null
    }

    private fun dismissUpdateReadyNotification() {
        getSystemService(NotificationManager::class.java)
            .cancel(BootRescheduleReceiver.UPDATE_NOTIFICATION_ID)
    }

    /**
     * The one automatic ask, on the first launch. Android only shows its dialog while
     * shouldShowRequestPermissionRationale still holds, so re-asking on every cold start
     * would spend that on a moment the user did not ask for; after this the Settings
     * notification switch is the way back in, and it knows to send a user Android will no
     * longer prompt straight to the app's notification settings instead.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) return
        if (notificationPreferences.hasRequestedPermission()) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

internal fun Intent.withTdayDeepLinkData(): Intent {
    if (data != null) return this
    val deepLink = getStringExtra(EXTRA_DEEP_LINK)?.takeIf { it.isNotBlank() } ?: return this
    return Intent(this).apply {
        data = Uri.parse(deepLink)
    }
}

private const val EXTRA_DEEP_LINK = "deepLink"
