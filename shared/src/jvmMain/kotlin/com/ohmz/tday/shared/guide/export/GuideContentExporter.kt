package com.ohmz.tday.shared.guide.export

import com.ohmz.tday.shared.guide.GuideBlock
import com.ohmz.tday.shared.guide.GuideCatalog
import com.ohmz.tday.shared.guide.GuideDeepLink
import com.ohmz.tday.shared.guide.GuidePlatform
import com.ohmz.tday.shared.guide.GuideSearch
import com.ohmz.tday.shared.guide.GuideSectionId
import com.ohmz.tday.shared.guide.GuideTopic
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.system.exitProcess

/**
 * Turns the shared [GuideCatalog] plus the web locale files into the committed,
 * platform-native guide artifacts. This is the repo's first cross-platform Gradle
 * codegen, run via `./gradlew :shared:exportGuideContent` and enforced in CI by
 * `./gradlew :shared:verifyGuideContent` (`--check`) — the Gradle twin of
 * `node scripts/version.mjs check`.
 *
 * Direction of truth:
 * - STRUCTURE (ids, sections, icons, platforms, deep links, body shape) flows
 *   FROM the Kotlin catalog TO web (`guide-structure.json`) and iOS.
 * - STRINGS flow FROM `tday-web/messages/<locale>.json` (the i18next source of
 *   truth) TO Android (`GuideStringsGenerated.kt`) and iOS (`guide.<locale>.json`).
 * Web resolves strings from i18next directly, so it only consumes the structure.
 *
 * Everything is committed and bundled, so the guide works fully offline / in
 * Local Mode with no network fetch.
 */
object GuideContentExporter {

    val LOCALES = listOf("en", "es", "fr", "de", "it", "pt", "ru", "zh", "ja", "ms")
    private const val WEB_ARTIFACT = "tday-web/src/generated/guide-structure.json"
    private const val WEB_FIXTURES = "tday-web/tests/fixtures/guide-search-vectors.json"
    private const val ICONS_MANIFEST = "tday-web/tests/fixtures/guide-icons.json"
    private const val ANDROID_STRINGS = "shared/src/commonMain/kotlin/com/ohmz/tday/shared/guide/GuideStringsGenerated.kt"
    private const val SUMMARY_STRINGS = "shared/src/commonMain/kotlin/com/ohmz/tday/shared/summary/SummaryStringBundlesGenerated.kt"
    private const val ROUTES_WHITELIST = "shared/guide-content/routes.json"
    private const val IOS_DIR = "ios-swiftUI/Tday/Resources/Guide"
    private const val IOS_VECTORS = "$IOS_DIR/guide-search-vectors.json"

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json { prettyPrint = true; prettyPrintIndent = "  "; encodeDefaults = true }

    /** Query vectors whose ranking parity the web/iOS ports must reproduce. */
    private val SEARCH_QUERIES = listOf(
        "repeat", "swipe", "widget", "offline", "date", "reminder", "api key",
        "floater", "car", "search", "priority", "pin", "keyboard", "push",
    )

    fun run(rootDir: File, check: Boolean): Boolean {
        val version = readVersion(rootDir)
        val strings: Map<String, Map<String, String>> = LOCALES.associateWith { readGuideStrings(rootDir, it) }
        val en = strings.getValue("en")

        validateCatalog(en)
        validateDeepLinks(rootDir)

        val outputs = LinkedHashMap<String, String>()
        outputs[WEB_ARTIFACT] = buildWebArtifact(version)
        outputs[WEB_FIXTURES] = buildSearchFixtures(en, GuidePlatform.WEB)
        outputs[IOS_VECTORS] = buildSearchFixtures(en, GuidePlatform.IOS)
        outputs[ICONS_MANIFEST] = buildIconsManifest()
        outputs[ANDROID_STRINGS] = buildAndroidStrings(strings)
        outputs[SUMMARY_STRINGS] = buildSummaryBundles(rootDir)
        for (locale in LOCALES) {
            outputs["$IOS_DIR/guide.$locale.json"] = buildIosArtifact(version, resolver(strings, locale))
        }

        return if (check) verify(rootDir, outputs) else write(rootDir, outputs)
    }

    // ── Inputs ───────────────────────────────────────────────────────────
    private fun readVersion(rootDir: File): String {
        val obj = Json.parseToJsonElement(File(rootDir, "version.json").readText()).jsonObject
        return (obj["version"] as JsonPrimitive).content
    }

    /** Flatten the `guide` namespace of one locale into `guide.a.b` -> value. */
    private fun readGuideStrings(rootDir: File, locale: String): Map<String, String> {
        val root = Json.parseToJsonElement(File(rootDir, "tday-web/messages/$locale.json").readText()).jsonObject
        val guide = root["guide"]?.jsonObject ?: error("locale $locale is missing the 'guide' namespace")
        val out = LinkedHashMap<String, String>()
        flatten("guide", guide, out)
        return out
    }

    private fun flatten(prefix: String, obj: JsonObject, out: MutableMap<String, String>) {
        for ((key, value) in obj) {
            val path = "$prefix.$key"
            when (value) {
                is JsonPrimitive -> out[path] = value.content
                is JsonObject -> flatten(path, value, out)
                else -> error("unexpected array/null at $path in guide strings")
            }
        }
    }

    private fun validateCatalog(en: Map<String, String>) {
        val missing = GuideCatalog.topics.flatMap { topic ->
            (topic.allStringKeys() + topic.section.titleKey).filter { it !in en }.map { "${topic.id}: $it" }
        }
        require(missing.isEmpty()) {
            "guide English strings missing ${missing.size} key(s):\n" + missing.joinToString("\n")
        }
    }

    /**
     * Deep links are raw route strings (commonMain cannot see platform navigation
     * types), so this committed whitelist is the only gate that fires in CI for
     * mobile route rot. A new deep link means adding its route to
     * `shared/guide-content/routes.json` in the same PR.
     */
    private fun validateDeepLinks(rootDir: File) {
        val root = Json.parseToJsonElement(File(rootDir, ROUTES_WHITELIST).readText()).jsonObject
        val whitelists = GuidePlatform.entries.associateWith { platform ->
            val key = platform.name.lowercase()
            (root[key] as? JsonArray ?: error("$ROUTES_WHITELIST is missing the '$key' array"))
                .map { (it as JsonPrimitive).content }
                .toSet()
        }
        val bad = GuideCatalog.topics.flatMap { topic ->
            val link = topic.deepLink ?: return@flatMap emptyList<String>()
            GuidePlatform.entries.mapNotNull { platform ->
                val route = link.forPlatform(platform) ?: return@mapNotNull null
                if (route in whitelists.getValue(platform)) null
                else "${topic.id}: ${platform.name.lowercase()} route \"$route\" is not whitelisted in $ROUTES_WHITELIST"
            }
        }
        require(bad.isEmpty()) {
            "guide deep links failed route-whitelist validation:\n" + bad.joinToString("\n")
        }
    }

    private fun resolver(strings: Map<String, Map<String, String>>, locale: String): (String) -> String {
        val loc = strings.getValue(locale)
        val en = strings.getValue("en")
        return { key -> loc[key] ?: en[key] ?: key } // en fallback for untranslated locales
    }

    // ── Web (structure only; strings come from i18next) ──────────────────
    private fun buildWebArtifact(version: String): String {
        val topics = GuideCatalog.topicsFor(GuidePlatform.WEB)
        val artifact = WebArtifact(
            version = 1,
            currentVersion = version,
            sections = GuideSectionId.entries.sortedBy { it.order }.map { SectionDto(it.name, it.titleKey, it.order) },
            topics = topics,
        )
        return json.encodeToString(WebArtifact.serializer(), artifact) + "\n"
    }

    /** Every distinct Lucide glyph the catalog references, for the icon-coverage guardrail. */
    private fun buildIconsManifest(): String {
        val icons = GuideCatalog.topics.map { it.icon }.distinct().sorted()
        return icons.joinToString(separator = ",\n", prefix = "[\n", postfix = "\n]\n") { "  \"$it\"" }
    }

    /**
     * Query→ranked-ids vectors for [platform]'s topic set, replayed by that
     * platform's port (vitest for web, XCTest for iOS) to guard ranking parity.
     */
    private fun buildSearchFixtures(en: Map<String, String>, platform: GuidePlatform): String {
        val docs = GuideCatalog.topicsFor(platform).map { docFor(it, resolverFrom(en)) }
        val vectors = SEARCH_QUERIES.associateWith { GuideSearch.rank(it, docs) }
        return json.encodeToString(SearchFixtures.serializer(), SearchFixtures(vectors)) + "\n"
    }

    // ── Android (generated Kotlin strings for all locales) ───────────────
    private fun buildAndroidStrings(strings: Map<String, Map<String, String>>): String {
        val sb = StringBuilder()
        sb.appendLine("package com.ohmz.tday.shared.guide")
        sb.appendLine()
        sb.appendLine("// GENERATED by :shared:exportGuideContent — do not edit by hand.")
        sb.appendLine("// Source of truth: tday-web/messages/<locale>.json (the i18next `guide` namespace).")
        sb.appendLine("// Regenerate with: ./gradlew :shared:exportGuideContent")
        sb.appendLine()
        sb.appendLine("object GuideStringsGenerated {")
        sb.appendLine("    val stringsByLocale: Map<String, Map<String, String>> = mapOf(")
        for (locale in LOCALES) {
            sb.appendLine("        \"$locale\" to mapOf(")
            val loc = strings.getValue(locale)
            for ((key, value) in loc.entries.sortedBy { it.key }) {
                sb.appendLine("            ${kquote(key)} to ${kquote(value)},")
            }
            sb.appendLine("        ),")
        }
        sb.appendLine("    )")
        sb.appendLine()
        sb.appendLine("    /** Resolve [key] for [locale] (language prefix ok), falling back to English. */")
        sb.appendLine("    fun resolve(locale: String, key: String): String {")
        sb.appendLine("        val lang = locale.substringBefore('-').lowercase()")
        sb.appendLine("        return stringsByLocale[lang]?.get(key)")
        sb.appendLine("            ?: stringsByLocale.getValue(\"en\")[key]")
        sb.appendLine("            ?: key")
        sb.appendLine("    }")
        sb.appendLine("}")
        return sb.toString()
    }

    // ── Summary engine strings (generated Kotlin bundles for all locales) ─
    /**
     * The phrase keys the deterministic [com.ohmz.tday.shared.summary.SummaryEngine]
     * consumes. Their source of truth is the web `summary` namespace; a key used by
     * the engine must be listed here AND exist in every locale file.
     */
    private val SUMMARY_VALUE_KEYS = listOf(
        "and", "clearForNow", "dateMonthDay", "dateMonthDayYear", "dayGroupedPast",
        "dayGroupedPresent", "dueAnytime", "dueOnDate", "dueOnDateWindow", "dueTodayWindow",
        "dueTomorrowWindow", "dueTonight", "dueYesterdayWindow", "nextUp", "night",
        "overdueCatchUp", "qualifierAll", "qualifierBoth", "startWith", "targetOnDate",
        "targetToday", "targetTomorrow", "targetYesterday", "taskPhrasePast", "taskPhrasePresent",
        "thenSeparator", "untitledTask", "windowAfternoon", "windowMorning", "windowNight",
        "window_afternoon", "window_morning",
        "weekNone", "weekCleared", "weekBusiest", "weekOldest",
    )

    private data class SummaryBundleData(
        val values: Map<String, String>,
        val months: List<String>,
        val weekdays: List<String>,
    )

    private fun readSummaryBundle(rootDir: File, locale: String): SummaryBundleData {
        val root = Json.parseToJsonElement(File(rootDir, "tday-web/messages/$locale.json").readText()).jsonObject
        val summary = root["summary"]?.jsonObject ?: error("locale $locale is missing the 'summary' namespace")
        val values = SUMMARY_VALUE_KEYS.associateWith { key ->
            (summary[key] as? JsonPrimitive)?.content ?: error("locale $locale is missing summary.$key")
        }
        val months = (summary["monthsShort"] as? JsonArray ?: error("locale $locale is missing summary.monthsShort"))
            .map { (it as JsonPrimitive).content }
        require(months.size == 12) { "locale $locale summary.monthsShort must have exactly 12 entries" }
        val weekdays = (summary["weekdaysShort"] as? JsonArray ?: error("locale $locale is missing summary.weekdaysShort"))
            .map { (it as JsonPrimitive).content }
        require(weekdays.size == 7) { "locale $locale summary.weekdaysShort must have exactly 7 entries (Mon..Sun)" }
        return SummaryBundleData(values, months, weekdays)
    }

    private fun buildSummaryBundles(rootDir: File): String {
        val sb = StringBuilder()
        sb.appendLine("package com.ohmz.tday.shared.summary")
        sb.appendLine()
        sb.appendLine("// GENERATED by :shared:exportGuideContent — do not edit by hand.")
        sb.appendLine("// Source of truth: tday-web/messages/<locale>.json (the i18next `summary` namespace).")
        sb.appendLine("// Regenerate with: ./gradlew :shared:exportGuideContent")
        sb.appendLine()
        sb.appendLine("internal object SummaryStringBundles {")
        for (locale in LOCALES) {
            val bundle = readSummaryBundle(rootDir, locale)
            sb.appendLine("    val $locale = SummaryStrings(")
            sb.appendLine("        values = mapOf(")
            for ((key, value) in bundle.values) {
                sb.appendLine("            ${kquote(key)} to ${kquote(value)},")
            }
            sb.appendLine("        ),")
            sb.appendLine("        monthsShort = listOf(${bundle.months.joinToString(", ") { kquote(it) }}),")
            sb.appendLine("        weekdaysShort = listOf(${bundle.weekdays.joinToString(", ") { kquote(it) }}),")
            sb.appendLine("    )")
        }
        sb.appendLine()
        sb.appendLine("    private val byLang: Map<String, SummaryStrings> = mapOf(")
        for (locale in LOCALES) {
            sb.appendLine("        \"$locale\" to $locale,")
        }
        sb.appendLine("    )")
        sb.appendLine()
        sb.appendLine("    fun forLocale(locale: String?): SummaryStrings {")
        sb.appendLine("        if (locale.isNullOrBlank()) return en")
        sb.appendLine("        val lang = locale.substringBefore('-').substringBefore('_').lowercase()")
        sb.appendLine("        return byLang[lang] ?: en")
        sb.appendLine("    }")
        sb.appendLine("}")
        return sb.toString()
    }

    // ── iOS (resolved strings + precomputed normalized search fields) ────
    private fun buildIosArtifact(version: String, resolve: (String) -> String): String {
        val topics = GuideCatalog.topicsFor(GuidePlatform.IOS).map { topic ->
            IosTopic(
                id = topic.id,
                section = topic.section.name,
                sectionOrder = topic.section.order,
                icon = topic.icon,
                platforms = topic.platforms.map { it.name },
                badge = topic.badge?.name,
                serverOnly = topic.serverOnly,
                sinceVersion = topic.sinceVersion,
                deepLink = topic.deepLink,
                helpAnchors = topic.helpAnchors,
                title = resolve(topic.titleKey),
                summary = resolve(topic.summaryKey),
                body = topic.body.map { block -> IosBlock(block.type.name, block.keys.map(resolve)) },
                searchTitle = GuideSearch.normalize(resolve(topic.titleKey)),
                searchKeywords = GuideSearch.normalize(resolve(topic.keywordsKey)),
                searchBody = GuideSearch.normalize(
                    (listOf(resolve(topic.summaryKey)) + topic.body.flatMap { it.keys.map(resolve) }).joinToString(" "),
                ),
            )
        }
        val ui = LinkedHashMap<String, String>()
        for (key in UI_KEYS) ui[key] = resolve("guide.$key")
        val sections = GuideSectionId.entries.sortedBy { it.order }
            .map { IosSection(it.name, resolve(it.titleKey), it.order) }
        val artifact = IosArtifact(currentVersion = version, ui = ui, sections = sections, topics = topics)
        return json.encodeToString(IosArtifact.serializer(), artifact) + "\n"
    }

    // ── Shared helpers ───────────────────────────────────────────────────
    private fun resolverFrom(map: Map<String, String>): (String) -> String = { key -> map[key] ?: key }

    private fun docFor(topic: GuideTopic, resolve: (String) -> String): GuideSearch.Doc =
        GuideSearch.buildDoc(
            topicId = topic.id,
            title = resolve(topic.titleKey),
            keywords = resolve(topic.keywordsKey),
            body = (listOf(resolve(topic.summaryKey)) + topic.body.flatMap { it.keys.map(resolve) }).joinToString(" "),
        )

    private fun write(rootDir: File, outputs: Map<String, String>): Boolean {
        outputs.forEach { (rel, content) ->
            val file = File(rootDir, rel)
            file.parentFile.mkdirs()
            file.writeText(content)
            println("  wrote $rel")
        }
        println("Guide content exported (${outputs.size} files).")
        return true
    }

    private fun verify(rootDir: File, outputs: Map<String, String>): Boolean {
        val stale = outputs.filter { (rel, content) ->
            val file = File(rootDir, rel)
            !file.exists() || file.readText() != content
        }.keys
        if (stale.isEmpty()) {
            println("Guide content is up to date (${outputs.size} files).")
            return true
        }
        System.err.println("Guide content is STALE. Run ./gradlew :shared:exportGuideContent and commit:")
        stale.forEach { System.err.println("  - $it") }
        return false
    }

    private fun kquote(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\$", "\\\$") + "\""

    private val UI_KEYS = listOf(
        "title", "subtitle", "searchPlaceholder", "searchAria", "whatsNew", "tryIt",
        "clearSearch", "noResults", "results",
        "badges.hiddenGem", "badges.proTip", "badges.new", "badges.server",
        "sections.gettingStarted", "sections.captureAndDates", "sections.gestures",
        "sections.organizing", "sections.recurrenceAndReminders", "sections.widgetsAndSurfaces",
        "sections.modesAndSync", "sections.integrations", "sections.keyboardShortcuts",
    )

    // ── Export DTOs ──────────────────────────────────────────────────────
    @Serializable
    private data class WebArtifact(
        val version: Int,
        val currentVersion: String,
        val sections: List<SectionDto>,
        val topics: List<GuideTopic>,
    )

    @Serializable
    private data class SectionDto(val id: String, val titleKey: String, val order: Int)

    @Serializable
    private data class SearchFixtures(val vectors: Map<String, List<String>>)

    @Serializable
    private data class IosArtifact(
        val currentVersion: String,
        val ui: Map<String, String>,
        val sections: List<IosSection>,
        val topics: List<IosTopic>,
    )

    @Serializable
    private data class IosSection(val id: String, val title: String, val order: Int)

    @Serializable
    private data class IosTopic(
        val id: String,
        val section: String,
        val sectionOrder: Int,
        val icon: String,
        val platforms: List<String>,
        val badge: String?,
        val serverOnly: Boolean,
        val sinceVersion: String?,
        val deepLink: GuideDeepLink?,
        val helpAnchors: List<String>,
        val title: String,
        val summary: String,
        val body: List<IosBlock>,
        val searchTitle: String,
        val searchKeywords: String,
        val searchBody: String,
    )

    @Serializable
    private data class IosBlock(val type: String, val texts: List<String>)
}

fun main(args: Array<String>) {
    val rootDir = File(args.firstOrNull { !it.startsWith("--") } ?: ".").absoluteFile
    val check = args.contains("--check")
    val ok = GuideContentExporter.run(rootDir, check)
    if (!ok) exitProcess(1)
}
