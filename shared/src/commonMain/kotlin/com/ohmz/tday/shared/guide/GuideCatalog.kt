package com.ohmz.tday.shared.guide

import com.ohmz.tday.shared.guide.GuidePlatform.ANDROID
import com.ohmz.tday.shared.guide.GuidePlatform.IOS
import com.ohmz.tday.shared.guide.GuidePlatform.WEB

/**
 * The canonical, hand-authored list of guide topics — the single source of truth
 * consumed natively by Android and the backend, and exported (structure + strings)
 * to web and iOS.
 *
 * The rule that keeps this current (see AGENTS.md): a PR that ships user-visible
 * behavior adds its topic here with [GuideTopic.sinceVersion] set, plus its
 * `guide.topics.<id>.*` strings in every `messages/<locale>.json`. "What's New"
 * then maintains itself.
 */
object GuideCatalog {

    val topics: List<GuideTopic> = listOf(
        // ── Getting started ──────────────────────────────────────────────
        topic(
            GuideTopicIds.ROOT_FEEDS, GuideSectionId.GETTING_STARTED, "layout-dashboard",
            setOf(WEB, ANDROID, IOS), body = listOf(para(GuideTopicIds.ROOT_FEEDS)),
        ),
        topic(
            GuideTopicIds.CREATE_TASK, GuideSectionId.GETTING_STARTED, "plus",
            setOf(WEB, ANDROID, IOS),
            body = listOf(para(GuideTopicIds.CREATE_TASK), tip(GuideTopicIds.CREATE_TASK)),
        ),

        // ── Capture & dates ──────────────────────────────────────────────
        topic(
            GuideTopicIds.NLP_DATE_SYNTAX, GuideSectionId.CAPTURE_AND_DATES, "wand-sparkles",
            setOf(WEB, ANDROID, IOS), badge = GuideBadge.HIDDEN_GEM,
            body = listOf(para(GuideTopicIds.NLP_DATE_SYNTAX), example(GuideTopicIds.NLP_DATE_SYNTAX)),
            helpAnchors = listOf("nlp-title-input"),
        ),
        topic(
            GuideTopicIds.CALENDAR_VIEWS, GuideSectionId.CAPTURE_AND_DATES, "calendar",
            setOf(WEB, ANDROID, IOS), body = listOf(para(GuideTopicIds.CALENDAR_VIEWS)),
            deepLink = GuideDeepLink(web = "calendar", android = "calendar", ios = "calendar"),
        ),
        topic(
            GuideTopicIds.PRIORITIES, GuideSectionId.CAPTURE_AND_DATES, "flag",
            setOf(WEB, ANDROID, IOS), body = listOf(para(GuideTopicIds.PRIORITIES)),
        ),
        topic(
            GuideTopicIds.PIN_TASK, GuideSectionId.CAPTURE_AND_DATES, "pin",
            setOf(WEB, ANDROID, IOS), badge = GuideBadge.PRO_TIP,
            body = listOf(para(GuideTopicIds.PIN_TASK)),
        ),

        // ── Gestures ─────────────────────────────────────────────────────
        topic(
            GuideTopicIds.SWIPE_ACTIONS, GuideSectionId.GESTURES, "pointer",
            setOf(ANDROID, IOS), badge = GuideBadge.HIDDEN_GEM,
            body = listOf(para(GuideTopicIds.SWIPE_ACTIONS), tip(GuideTopicIds.SWIPE_ACTIONS)),
            deepLink = GuideDeepLink(android = "todos/today", ios = "today"),
        ),
        topic(
            GuideTopicIds.LONG_PRESS_ACTIONS, GuideSectionId.GESTURES, "hand",
            setOf(ANDROID, IOS), badge = GuideBadge.HIDDEN_GEM,
            body = listOf(para(GuideTopicIds.LONG_PRESS_ACTIONS)),
        ),
        topic(
            GuideTopicIds.DRAG_REORDER, GuideSectionId.GESTURES, "grip-vertical",
            setOf(WEB, ANDROID, IOS), badge = GuideBadge.PRO_TIP,
            body = listOf(para(GuideTopicIds.DRAG_REORDER)),
        ),

        // ── Organizing ───────────────────────────────────────────────────
        topic(
            GuideTopicIds.FLOATERS_VS_TODOS, GuideSectionId.ORGANIZING, "waves",
            setOf(WEB, ANDROID, IOS),
            body = listOf(para(GuideTopicIds.FLOATERS_VS_TODOS), tip(GuideTopicIds.FLOATERS_VS_TODOS)),
        ),
        topic(
            GuideTopicIds.FLOATER_LISTS, GuideSectionId.ORGANIZING, "list",
            setOf(WEB, ANDROID, IOS), body = listOf(para(GuideTopicIds.FLOATER_LISTS)),
        ),
        topic(
            GuideTopicIds.SCHEDULED_LISTS, GuideSectionId.ORGANIZING, "list-todo",
            setOf(WEB, ANDROID, IOS), body = listOf(para(GuideTopicIds.SCHEDULED_LISTS)),
        ),
        topic(
            GuideTopicIds.COMPLETED_HISTORY, GuideSectionId.ORGANIZING, "check-check",
            setOf(WEB, ANDROID, IOS), body = listOf(para(GuideTopicIds.COMPLETED_HISTORY)),
        ),
        topic(
            GuideTopicIds.OVERDUE_VIEW, GuideSectionId.ORGANIZING, "alarm-clock",
            setOf(WEB, ANDROID, IOS), body = listOf(para(GuideTopicIds.OVERDUE_VIEW)),
            deepLink = GuideDeepLink(web = "overdue", android = "todos/overdue", ios = "overdueTodos"),
        ),
        topic(
            GuideTopicIds.SEARCH_TASKS, GuideSectionId.ORGANIZING, "search",
            setOf(WEB, ANDROID, IOS), badge = GuideBadge.HIDDEN_GEM,
            body = listOf(para(GuideTopicIds.SEARCH_TASKS)),
        ),

        // ── Recurrence & reminders ───────────────────────────────────────
        topic(
            GuideTopicIds.RECURRENCE_PRESETS, GuideSectionId.RECURRENCE_AND_REMINDERS, "repeat",
            setOf(WEB, ANDROID, IOS),
            body = listOf(para(GuideTopicIds.RECURRENCE_PRESETS), example(GuideTopicIds.RECURRENCE_PRESETS)),
            helpAnchors = listOf("recurrence-picker"),
        ),
        topic(
            GuideTopicIds.REMINDERS, GuideSectionId.RECURRENCE_AND_REMINDERS, "bell",
            setOf(ANDROID, IOS),
            body = listOf(para(GuideTopicIds.REMINDERS), tip(GuideTopicIds.REMINDERS)),
        ),
        topic(
            GuideTopicIds.PUSH_NOTIFICATIONS, GuideSectionId.RECURRENCE_AND_REMINDERS, "bell-ring",
            setOf(WEB), serverOnly = true, sinceVersion = "0.3.0",
            body = listOf(para(GuideTopicIds.PUSH_NOTIFICATIONS)),
            helpAnchors = listOf("settings-notifications"),
        ),

        // ── Widgets & surfaces ───────────────────────────────────────────
        topic(
            GuideTopicIds.HOME_WIDGET, GuideSectionId.WIDGETS_AND_SURFACES, "layout-grid",
            setOf(ANDROID, IOS), body = listOf(para(GuideTopicIds.HOME_WIDGET)),
        ),
        topic(
            GuideTopicIds.WIDGET_QUICK_ADD, GuideSectionId.WIDGETS_AND_SURFACES, "square-plus",
            setOf(ANDROID, IOS), badge = GuideBadge.PRO_TIP,
            body = listOf(para(GuideTopicIds.WIDGET_QUICK_ADD)),
        ),
        topic(
            GuideTopicIds.CARPLAY, GuideSectionId.WIDGETS_AND_SURFACES, "car",
            setOf(IOS), badge = GuideBadge.HIDDEN_GEM,
            body = listOf(para(GuideTopicIds.CARPLAY)),
        ),
        topic(
            GuideTopicIds.ANDROID_CAR, GuideSectionId.WIDGETS_AND_SURFACES, "car-front",
            setOf(ANDROID), body = listOf(para(GuideTopicIds.ANDROID_CAR)),
        ),

        // ── Modes & sync ─────────────────────────────────────────────────
        topic(
            GuideTopicIds.LOCAL_MODE, GuideSectionId.MODES_AND_SYNC, "wifi-off",
            setOf(ANDROID, IOS),
            body = listOf(para(GuideTopicIds.LOCAL_MODE), tip(GuideTopicIds.LOCAL_MODE)),
        ),
        topic(
            GuideTopicIds.SERVER_MODE, GuideSectionId.MODES_AND_SYNC, "cloud",
            setOf(WEB, ANDROID, IOS), body = listOf(para(GuideTopicIds.SERVER_MODE)),
        ),
        topic(
            GuideTopicIds.OFFLINE_SYNC, GuideSectionId.MODES_AND_SYNC, "refresh-cw",
            setOf(ANDROID, IOS), body = listOf(para(GuideTopicIds.OFFLINE_SYNC)),
        ),
        topic(
            GuideTopicIds.IN_APP_UPDATE, GuideSectionId.MODES_AND_SYNC, "download",
            setOf(ANDROID), body = listOf(para(GuideTopicIds.IN_APP_UPDATE)),
        ),

        // ── Integrations ─────────────────────────────────────────────────
        topic(
            GuideTopicIds.API_KEY_HOMARR, GuideSectionId.INTEGRATIONS, "key-round",
            setOf(WEB), serverOnly = true,
            body = listOf(para(GuideTopicIds.API_KEY_HOMARR), steps(GuideTopicIds.API_KEY_HOMARR, 3)),
            deepLink = GuideDeepLink(web = "settings"),
            helpAnchors = listOf("settings-api-key"),
        ),
        topic(
            GuideTopicIds.AI_SUMMARY, GuideSectionId.INTEGRATIONS, "sparkles",
            setOf(WEB, ANDROID, IOS), serverOnly = true,
            body = listOf(para(GuideTopicIds.AI_SUMMARY), tip(GuideTopicIds.AI_SUMMARY)),
        ),

        // ── Keyboard shortcuts ───────────────────────────────────────────
        topic(
            GuideTopicIds.KEYBOARD_SUBMIT, GuideSectionId.KEYBOARD_SHORTCUTS, "keyboard",
            setOf(WEB),
            body = listOf(para(GuideTopicIds.KEYBOARD_SUBMIT), kbd(GuideTopicIds.KEYBOARD_SUBMIT)),
        ),
    )

    /** Fast id lookup. */
    val byId: Map<String, GuideTopic> = topics.associateBy { it.id }

    /** Topics that apply to [platform], in authored (section) order. */
    fun topicsFor(platform: GuidePlatform): List<GuideTopic> =
        topics.filter { platform in it.platforms }

    /**
     * Topics introduced in [currentVersion] for [platform] — the "What's New"
     * group. Empty when nothing shipped in this version applies here.
     */
    fun whatsNew(currentVersion: String, platform: GuidePlatform): List<GuideTopic> =
        topicsFor(platform).filter { it.sinceVersion == currentVersion }

    // ── Authoring helpers ────────────────────────────────────────────────
    private fun topic(
        id: String,
        section: GuideSectionId,
        icon: String,
        platforms: Set<GuidePlatform>,
        body: List<GuideBlock>,
        badge: GuideBadge? = null,
        deepLink: GuideDeepLink? = null,
        helpAnchors: List<String> = emptyList(),
        serverOnly: Boolean = false,
        sinceVersion: String? = null,
    ) = GuideTopic(
        id = id,
        section = section,
        icon = icon,
        platforms = platforms,
        titleKey = "guide.topics.$id.title",
        summaryKey = "guide.topics.$id.summary",
        keywordsKey = "guide.topics.$id.keywords",
        body = body,
        badge = badge,
        deepLink = deepLink,
        helpAnchors = helpAnchors,
        serverOnly = serverOnly,
        sinceVersion = sinceVersion,
    )

    private fun para(id: String) = GuideBlock(GuideBlockType.PARAGRAPH, listOf("guide.topics.$id.body"))
    private fun tip(id: String) = GuideBlock(GuideBlockType.TIP, listOf("guide.topics.$id.tip"))
    private fun example(id: String) = GuideBlock(GuideBlockType.EXAMPLE, listOf("guide.topics.$id.example"))
    private fun kbd(id: String) = GuideBlock(GuideBlockType.KBD, listOf("guide.topics.$id.kbd"))
    private fun steps(id: String, count: Int) =
        GuideBlock(GuideBlockType.STEPS, (0 until count).map { "guide.topics.$id.step$it" })
}
