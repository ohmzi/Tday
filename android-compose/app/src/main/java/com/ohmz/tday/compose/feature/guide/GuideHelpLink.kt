package com.ohmz.tday.compose.feature.guide

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.R

/**
 * Opens the How-To guide pre-scrolled to a topic id (from the shared
 * `GuideTopicIds`). Provided once in TdayApp around the NavHost; null wherever
 * there is no navigation host (e.g. the widget quick-add activity), which
 * simply hides every [GuideHelpLink].
 */
val LocalOpenGuideTopic = staticCompositionLocalOf<((String) -> Unit)?> { null }

/**
 * A quiet contextual "?" that deep-links from a feature surface into its guide
 * topic — the Android counterpart of the web `GuideHelpLink`. Renders nothing
 * when no [LocalOpenGuideTopic] provider is in scope.
 */
@Composable
fun GuideHelpLink(topicId: String, modifier: Modifier = Modifier) {
    val openGuideTopic = LocalOpenGuideTopic.current ?: return
    IconButton(
        onClick = { openGuideTopic(topicId) },
        modifier = modifier.size(32.dp),
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_circle_help),
            contentDescription = stringResource(R.string.settings_help_guide),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
