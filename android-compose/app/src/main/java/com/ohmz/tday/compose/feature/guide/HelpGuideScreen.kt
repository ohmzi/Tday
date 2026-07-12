package com.ohmz.tday.compose.feature.guide

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmz.tday.compose.BuildConfig
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.GuidePreferenceStore
import com.ohmz.tday.shared.guide.GuideBadge
import com.ohmz.tday.shared.guide.GuideBlockType
import com.ohmz.tday.shared.guide.GuideCatalog
import com.ohmz.tday.shared.guide.GuidePlatform
import com.ohmz.tday.shared.guide.GuideSearch
import com.ohmz.tday.shared.guide.GuideSectionId
import com.ohmz.tday.shared.guide.GuideStringsGenerated
import com.ohmz.tday.shared.guide.GuideTopic

/**
 * The in-app How-To / feature guide. Reads the shared [GuideCatalog] natively via
 * the :shared dependency, resolves localized strings from the generated
 * [GuideStringsGenerated], and searches with the shared [GuideSearch] — so its
 * content and ranking are identical to web and iOS. Fully offline / Local-Mode
 * safe: nothing here needs the network.
 */
@Composable
fun HelpGuideScreen(
    isLocalMode: Boolean,
    onBack: () -> Unit,
    onOpenDeepLink: (String) -> Unit,
    initialTopic: String? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0].language
    fun res(key: String): String = GuideStringsGenerated.resolve(locale, key)

    val topics = remember { GuideCatalog.topicsFor(GuidePlatform.ANDROID) }
    val byId = remember { topics.associateBy { it.id } }
    val docs = remember(locale) {
        topics.map { topic ->
            GuideSearch.buildDoc(
                topic.id,
                res(topic.titleKey),
                res(topic.keywordsKey),
                (listOf(res(topic.summaryKey)) + topic.body.flatMap { it.keys.map(::res) }).joinToString(" "),
            )
        }
    }

    var query by rememberSaveable { mutableStateOf("") }
    var expandedId by remember { mutableStateOf(initialTopic) }
    val trimmed = query.trim()
    val rankedIds = remember(query, docs) {
        if (trimmed.isEmpty()) emptyList() else GuideSearch.rank(query, docs)
    }
    val whatsNew = remember { topics.filter { it.sinceVersion == BuildConfig.VERSION_NAME } }

    // NEW badges show until the guide has been opened in this release: read the
    // persisted last-seen version once, then mark the running release as seen.
    val context = LocalContext.current
    val guidePrefs = remember { GuidePreferenceStore(context) }
    val showNewBadges = remember { guidePrefs.lastSeenGuideVersion() != BuildConfig.VERSION_NAME }
    LaunchedEffect(Unit) { guidePrefs.setLastSeenGuideVersion(BuildConfig.VERSION_NAME) }

    Scaffold(containerColor = colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            // Back
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_arrow_left),
                    contentDescription = null,
                    tint = colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.settings_help_guide),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = res("guide.title"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = colorScheme.onSurface,
            )
            Text(
                text = res("guide.subtitle"),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                placeholder = { Text(res("guide.searchPlaceholder")) },
                leadingIcon = {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_search),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_x),
                                contentDescription = res("guide.clearSearch"),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorScheme.primary,
                    unfocusedBorderColor = colorScheme.onSurface.copy(alpha = 0.15f),
                ),
            )

            Spacer(Modifier.height(16.dp))

            if (trimmed.isNotEmpty()) {
                Text(
                    text = res("guide.results").replace("{{count}}", rankedIds.size.toString()),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
                )
                if (rankedIds.isEmpty()) {
                    Text(
                        text = res("guide.noResults"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    rankedIds.forEach { id ->
                        byId[id]?.let { topic ->
                            TopicCard(topic, expandedId == id, ::res, isLocalMode, showNewBadges, onOpenDeepLink) {
                                expandedId = if (expandedId == id) null else id
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            } else {
                if (whatsNew.isNotEmpty()) {
                    SectionLabel(res("guide.whatsNew"))
                    whatsNew.forEach { topic ->
                        TopicCard(topic, expandedId == topic.id, ::res, isLocalMode, showNewBadges, onOpenDeepLink) {
                            expandedId = if (expandedId == topic.id) null else topic.id
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                }
                GuideSectionId.entries.sortedBy { it.order }.forEach { section ->
                    val sectionTopics = topics.filter { it.section == section }
                    if (sectionTopics.isNotEmpty()) {
                        SectionLabel(res(section.titleKey))
                        sectionTopics.forEach { topic ->
                            TopicCard(topic, expandedId == topic.id, ::res, isLocalMode, showNewBadges, onOpenDeepLink) {
                                expandedId = if (expandedId == topic.id) null else topic.id
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
    )
}

@Composable
private fun TopicCard(
    topic: GuideTopic,
    expanded: Boolean,
    res: (String) -> String,
    isLocalMode: Boolean,
    showNewBadge: Boolean,
    onOpenDeepLink: (String) -> Unit,
    onToggle: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colorScheme.primary.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(guideIconRes(topic.icon)),
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = res(topic.titleKey),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                        )
                        Badges(topic, res, showNewBadge)
                    }
                    Text(
                        text = res(topic.summaryKey),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_right),
                    contentDescription = null,
                    tint = colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp),
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colorScheme.onSurface.copy(alpha = 0.06f)),
                    )
                    topic.body.forEach { block ->
                        BodyBlock(block.type, block.keys.map(res))
                    }
                    val deepLink = topic.deepLink?.android
                    if (deepLink != null && !(isLocalMode && topic.serverOnly)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenDeepLink(deepLink) },
                        ) {
                            Text(
                                text = res("guide.tryIt"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Badges(topic: GuideTopic, res: (String) -> String, showNewBadge: Boolean) {
    val isNew = showNewBadge && topic.sinceVersion == BuildConfig.VERSION_NAME
    if (isNew) Pill(res("guide.badges.new"))
    when (topic.badge) {
        GuideBadge.HIDDEN_GEM -> Pill(res("guide.badges.hiddenGem"))
        GuideBadge.PRO_TIP -> Pill(res("guide.badges.proTip"))
        null -> Unit
    }
    if (topic.serverOnly) Pill(res("guide.badges.server"))
}

@Composable
private fun Pill(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun BodyBlock(type: GuideBlockType, texts: List<String>) {
    val colorScheme = MaterialTheme.colorScheme
    val text = texts.firstOrNull().orEmpty()
    when (type) {
        GuideBlockType.STEPS -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            texts.forEachIndexed { i, step ->
                Row {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${i + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(step, style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface)
                }
            }
        }

        GuideBlockType.TIP -> Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colorScheme.primary.copy(alpha = 0.06f))
                .padding(12.dp),
        )

        GuideBlockType.KBD, GuideBlockType.EXAMPLE -> Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = colorScheme.onSurface,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(colorScheme.onSurface.copy(alpha = 0.06f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )

        GuideBlockType.PARAGRAPH -> Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurface.copy(alpha = 0.85f),
        )
    }
}

// Per-topic Lucide drawable. Every catalog glyph has an ic_lucide_* vector
// (guarded by the guide-icons coverage test); book is a defensive fallback.
@DrawableRes
private fun guideIconRes(name: String): Int = when (name) {
    "alarm-clock" -> R.drawable.ic_lucide_alarm_clock
    "bell" -> R.drawable.ic_lucide_bell
    "bell-ring" -> R.drawable.ic_lucide_bell_ring
    "calendar" -> R.drawable.ic_lucide_calendar
    "car" -> R.drawable.ic_lucide_car
    "car-front" -> R.drawable.ic_lucide_car_front
    "check-check" -> R.drawable.ic_lucide_check_check
    "cloud" -> R.drawable.ic_lucide_cloud
    "download" -> R.drawable.ic_lucide_download
    "flag" -> R.drawable.ic_lucide_flag
    "grip-vertical" -> R.drawable.ic_lucide_grip_vertical
    "hand" -> R.drawable.ic_lucide_hand
    "key-round" -> R.drawable.ic_lucide_key_round
    "keyboard" -> R.drawable.ic_lucide_keyboard
    "layout-dashboard" -> R.drawable.ic_lucide_layout_dashboard
    "layout-grid" -> R.drawable.ic_lucide_layout_grid
    "list" -> R.drawable.ic_lucide_list
    "list-todo" -> R.drawable.ic_lucide_list_todo
    "pin" -> R.drawable.ic_lucide_pin
    "plus" -> R.drawable.ic_lucide_plus
    "pointer" -> R.drawable.ic_lucide_pointer
    "refresh-cw" -> R.drawable.ic_lucide_refresh_cw
    "repeat" -> R.drawable.ic_lucide_repeat
    "search" -> R.drawable.ic_lucide_search
    "sparkles" -> R.drawable.ic_lucide_sparkles
    "square-plus" -> R.drawable.ic_lucide_square_plus
    "wand-sparkles" -> R.drawable.ic_lucide_wand_sparkles
    "waves" -> R.drawable.ic_lucide_waves
    "wifi-off" -> R.drawable.ic_lucide_wifi_off
    else -> R.drawable.ic_lucide_book
}
