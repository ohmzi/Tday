package com.ohmz.tday.shared.guide

/**
 * The one guide search algorithm, shared by every platform.
 *
 * Matching mirrors the app's existing lowercase-substring search culture but adds
 * light weighting. To guarantee cross-platform parity, normalization is defined
 * here once: Android calls [buildDoc] at runtime, and the exporter calls the same
 * [normalize] to precompute the blobs shipped to web and iOS, so all three search
 * over byte-identical normalized text. The web (TS) and iOS (Swift) query-side
 * ports of [normalize] are kept honest by generated fixtures.
 */
object GuideSearch {

    /** A topic reduced to normalized, searchable text. */
    data class Doc(
        val topicId: String,
        val titleNorm: String,
        val keywordsNorm: String,
        val bodyNorm: String,
    )

    private const val TITLE_WEIGHT = 3
    private const val KEYWORD_WEIGHT = 2
    private const val BODY_WEIGHT = 1

    // Common Latin diacritics folded so "café"/"cafe" and "répéter"/"repeter" match.
    // Cyrillic and CJK need no folding — lowercase + raw substring already works.
    private val DIACRITIC_FOLD: Map<Char, Char> = buildMap {
        "àáâãäåāăą".forEach { put(it, 'a') }
        "çćčĉ".forEach { put(it, 'c') }
        "ďđ".forEach { put(it, 'd') }
        "èéêëēĕėęě".forEach { put(it, 'e') }
        "ìíîïĩīĭįı".forEach { put(it, 'i') }
        "ñńņň".forEach { put(it, 'n') }
        "òóôõöøōŏő".forEach { put(it, 'o') }
        "ùúûüũūŭůűų".forEach { put(it, 'u') }
        "ýÿŷ".forEach { put(it, 'y') }
        "śšşŝ".forEach { put(it, 's') }
        "žźż".forEach { put(it, 'z') }
        "ğĝ".forEach { put(it, 'g') }
        "ß".forEach { put(it, 's') }
    }

    /**
     * Lowercase, fold Latin diacritics, and collapse all whitespace runs to a
     * single space. Deterministic and dependency-free so every platform can
     * reproduce it exactly.
     */
    fun normalize(input: String): String {
        val lowered = input.lowercase()
        val sb = StringBuilder(lowered.length)
        var pendingSpace = false
        for (ch in lowered) {
            if (ch.isWhitespace()) {
                pendingSpace = sb.isNotEmpty()
                continue
            }
            if (pendingSpace) {
                sb.append(' ')
                pendingSpace = false
            }
            sb.append(DIACRITIC_FOLD[ch] ?: ch)
        }
        return sb.toString()
    }

    /** Build a normalized [Doc] from a topic's resolved localized strings. */
    fun buildDoc(topicId: String, title: String, keywords: String, body: String): Doc =
        Doc(
            topicId = topicId,
            titleNorm = normalize(title),
            keywordsNorm = normalize(keywords),
            bodyNorm = normalize(body),
        )

    /**
     * Rank [docs] against [query], returning matching topic ids best-first.
     *
     * A doc matches only if EVERY query token appears somewhere in it (token-AND).
     * Score sums, per token, a title hit (3) + keyword hit (2) + body hit (1).
     * Ties keep the input order, so pass [docs] in catalog order for a stable,
     * section-ordered tie-break. An empty query returns an empty list (callers
     * show the sectioned catalog instead).
     */
    fun rank(query: String, docs: List<Doc>): List<String> {
        val tokens = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()

        data class Scored(val topicId: String, val score: Int, val index: Int)

        val scored = ArrayList<Scored>(docs.size)
        for ((index, doc) in docs.withIndex()) {
            var total = 0
            var allTokensMatched = true
            for (token in tokens) {
                var tokenScore = 0
                if (doc.titleNorm.contains(token)) tokenScore += TITLE_WEIGHT
                if (doc.keywordsNorm.contains(token)) tokenScore += KEYWORD_WEIGHT
                if (doc.bodyNorm.contains(token)) tokenScore += BODY_WEIGHT
                if (tokenScore == 0) {
                    allTokensMatched = false
                    break
                }
                total += tokenScore
            }
            if (allTokensMatched) scored.add(Scored(doc.topicId, total, index))
        }
        // Higher score first; ties fall back to catalog (input) order.
        return scored
            .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.index })
            .map { it.topicId }
    }
}
