package com.ohmz.tday.compose.feature.shortcut

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.quicksettings.TileService
import com.ohmz.tday.compose.BuildConfig
import com.ohmz.tday.compose.feature.widget.WidgetCreateTaskActivity

/**
 * Quick Settings "New task" tile: one swipe-and-tap from anywhere into the
 * same frameless create sheet the widgets use. The tile runs in our own uid,
 * so it can target the non-exported WidgetCreateTaskActivity directly.
 */
class QuickAddTileService : TileService() {

    override fun onClick() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(CREATE_TODAY_DEEP_LINK)).apply {
            component = ComponentName(
                BuildConfig.APPLICATION_ID,
                WidgetCreateTaskActivity::class.java.name,
            )
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34+ rejects the Intent overload for apps targeting U.
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}

private const val CREATE_TODAY_DEEP_LINK = "tday://todos/create?target=today"
