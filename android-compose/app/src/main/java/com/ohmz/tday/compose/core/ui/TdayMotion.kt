package com.ohmz.tday.compose.core.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether decorative animation should play at all.
 *
 * Compose has no equivalent of `prefers-reduced-motion`, so this reads what the
 * platform actually offers: the animator duration scale, which is what the
 * developer-options slider and Settings' "Remove animations" both write. A
 * screen that reads `false` here must still draw its finished state — a scene
 * held at the start of its fade looks half-rendered rather than deliberate,
 * which is the same call the web stylesheet makes.
 */
@Composable
fun rememberTdayMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) != 0f
    }
}
