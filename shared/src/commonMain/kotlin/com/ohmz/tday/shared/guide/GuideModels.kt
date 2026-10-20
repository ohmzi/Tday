package com.ohmz.tday.shared.guide

import kotlinx.serialization.Serializable

/**
 * The in-app How-To / feature guide, modelled once in the shared module and
 * consumed by every platform.
 *
 * Content strings are NOT stored here — only stable i18n keys into the `guide`
 * namespace whose source of truth is `tday-web/messages/<locale>.json`. The
 * backend and Android consume this catalog natively via `project(":shared")`;
 * the web app and iOS receive committed, build-time-generated artifacts (see
 * `GuideContentExporter`). Because everything is compiled or bundled into each
 * client, the guide is fully available offline and in Local Mode — no network
 * fetch is ever required to read it.
 */

/** Which platform a topic applies to. Powers per-platform filtering. */
@Serializable
enum class GuidePlatform { WEB, ANDROID, IOS }

/**
 * An authored emphasis marker. `NEW` is deliberately NOT here — it is derived at
 * render time from [GuideTopic.sinceVersion] versus the running app version, so
 * it clears itself once the user has seen the current release.
 */
@Serializable
enum class GuideBadge { HIDDEN_GEM, PRO_TIP }

/** The kind of a body block; each maps to a native component per platform. */
@Serializable
enum class GuideBlockType {
    /** A prose paragraph. Uses the first key. */
    PARAGRAPH,

    /** An ordered how-to list. Uses every key as one step. */
    STEPS,

    /** A quiet inline hint — the styling that gives hidden gems their look. */
    TIP,

    /** A keyboard chord, rendered as <kbd> on web. Uses the first key. */
    KBD,

    /** A literal example (e.g. an NLP phrase to type). Uses the first key. */
    EXAMPLE,
}

/**
 * One renderable block of a topic body. [keys] are always a list so the schema
 * is uniform: single-string blocks use `keys[0]`, [GuideBlockType.STEPS] uses
 * all of them in order.
 */
@Serializable
data class GuideBlock(
    val type: GuideBlockType,
    val keys: List<String>,
)

/**
 * A "Try it" target. Each field is that platform's own route identity:
 * - [web]: the path segment under `/:locale/app` (e.g. "overdue").
 * - [android]: the `AppRoute.route` string (e.g. "todos/overdue").
 * - [ios]: the `AppRoute.deepLinkPath` string (e.g. "overdueTodos").
 * Raw strings because commonMain cannot import platform navigation types; the
 * values are kept honest by the exporter's route-whitelist validation.
 */
@Serializable
data class GuideDeepLink(
    val web: String? = null,
    val android: String? = null,
    val ios: String? = null,
) {
    fun forPlatform(platform: GuidePlatform): String? = when (platform) {
        GuidePlatform.WEB -> web
        GuidePlatform.ANDROID -> android
        GuidePlatform.IOS -> ios
    }
}

/** A section of the guide. [order] fixes display and search tie-break ordering. */
@Serializable
enum class GuideSectionId(val titleKey: String, val order: Int) {
    GETTING_STARTED("guide.sections.gettingStarted", 0),
    CAPTURE_AND_DATES("guide.sections.captureAndDates", 1),
    GESTURES("guide.sections.gestures", 2),
    ORGANIZING("guide.sections.organizing", 3),
    RECURRENCE_AND_REMINDERS("guide.sections.recurrenceAndReminders", 4),
    WIDGETS_AND_SURFACES("guide.sections.widgetsAndSurfaces", 5),
    MODES_AND_SYNC("guide.sections.modesAndSync", 6),
    INTEGRATIONS("guide.sections.integrations", 7),
    KEYBOARD_SHORTCUTS("guide.sections.keyboardShortcuts", 8),
}

/**
 * A single guide entry. Hidden features and tips are ordinary topics carrying a
 * [GuideBadge] — so they are sectioned and searched like everything else.
 *
 * @param id stable slug (e.g. "nlp-date-syntax"); also the deep-link topic id.
 * @param icon Lucide glyph name; must exist in the shared-glyph table (docs/ICONS.md).
 * @param platforms which platforms show this topic.
 * @param serverOnly true for features that need Server Mode; shown with a badge
 *   in Local Mode (teaching the difference) rather than hidden.
 * @param sinceVersion semver matching root version.json; feeds "What's New".
 * @param helpAnchors screen ids that render a contextual "?" pointing here.
 */
@Serializable
data class GuideTopic(
    val id: String,
    val section: GuideSectionId,
    val icon: String,
    val platforms: Set<GuidePlatform>,
    val titleKey: String,
    val summaryKey: String,
    val keywordsKey: String,
    val body: List<GuideBlock> = emptyList(),
    val badge: GuideBadge? = null,
    val deepLink: GuideDeepLink? = null,
    val helpAnchors: List<String> = emptyList(),
    val serverOnly: Boolean = false,
    val sinceVersion: String? = null,
) {
    /** Every i18n key this topic references, for validation and translation tooling. */
    fun allStringKeys(): List<String> =
        buildList {
            add(titleKey)
            add(summaryKey)
            add(keywordsKey)
            body.forEach { addAll(it.keys) }
        }
}
