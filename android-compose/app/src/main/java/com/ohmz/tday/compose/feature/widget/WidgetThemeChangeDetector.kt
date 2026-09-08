package com.ohmz.tday.compose.feature.widget

import android.content.res.Configuration

/**
 * Whether a system configuration change actually flipped day/night, isolated from
 * [TdayApplication] so the decision is a plain JVM unit test.
 *
 * Takes raw `Configuration.uiMode` ints rather than a `Configuration` instance: this module's
 * unit tests run against the unmocked Android SDK jar (no Robolectric), which throws on real
 * framework method calls. [Configuration.UI_MODE_NIGHT_MASK] and friends are `public static final
 * int` constants the compiler inlines as literals, so referencing them here never touches the stub
 * jar at runtime — but a `Configuration` object's fields would still have to come from somewhere
 * real, which only [TdayApplication.onConfigurationChanged] has.
 */
internal fun didNightModeFlip(oldUiMode: Int, newUiMode: Int): Boolean =
    (oldUiMode and Configuration.UI_MODE_NIGHT_MASK) != (newUiMode and Configuration.UI_MODE_NIGHT_MASK)
