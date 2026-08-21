package com.ohmz.tday.mcp

import kotlin.math.max
import kotlin.math.min

/** T'Day's two list namespaces. A name can exist in one and not the other. */
enum class ListNamespace(val label: String, val holds: String, private val article: String) {
    SCHEDULED("scheduled", "dated tasks", "a"),
    ANYTIME("Anytime", "undated tasks", "an");

    /** "a scheduled list" / "an Anytime list" — the article differs, so don't hardcode it. */
    val indefinite: String get() = "$article $label"
}

data class NamedList(val id: String, val name: String, val namespace: ListNamespace)

/** The outcome of looking a list name up — enough for the model to explain itself. */
data class ListLookup(
    val query: String,
    val namespace: ListNamespace?,
    /** The list the name resolves to, if any. */
    val match: NamedList?,
    /** Close-but-not-equal names in the requested namespace, best first. */
    val suggestions: List<NamedList>,
    /** A list of this exact name that lives in the *other* namespace. */
    val crossNamespace: NamedList?,
    /** Every list name available in the requested namespace. */
    val available: List<NamedList>,
) {
    val found: Boolean get() = match != null
}

/**
 * Name → list resolution for the MCP tools.
 *
 * The point is the miss, not the hit: when a user names a list that doesn't exist, the
 * model has to be able to say so — and say what does exist — rather than quietly
 * creating a near-duplicate ("Groceries" / "groceries" / "Grocery").
 */
object McpListResolver {

    /** Max edit distance for a suggestion, scaled so short names don't match everything. */
    private const val SHORT_NAME_LENGTH = 5
    private const val MEDIUM_NAME_LENGTH = 9
    private const val MAX_SUGGESTIONS = 3

    fun lookup(query: String, candidates: List<NamedList>, namespace: ListNamespace?): ListLookup {
        val wanted = if (namespace == null) candidates else candidates.filter { it.namespace == namespace }
        val others = if (namespace == null) emptyList() else candidates.filter { it.namespace != namespace }

        val match = exactMatch(query, wanted)
        val suggestions = if (match != null) emptyList() else nearMatches(query, wanted)

        return ListLookup(
            query = query,
            namespace = namespace,
            match = match,
            suggestions = suggestions,
            crossNamespace = if (match != null) null else exactMatch(query, others),
            available = wanted,
        )
    }

    private fun exactMatch(query: String, candidates: List<NamedList>): NamedList? {
        candidates.firstOrNull { it.name == query }?.let { return it }
        candidates.firstOrNull { it.name.equals(query, ignoreCase = true) }?.let { return it }
        val normalizedQuery = normalize(query)
        return candidates.firstOrNull { normalize(it.name) == normalizedQuery }
    }

    private fun nearMatches(query: String, candidates: List<NamedList>): List<NamedList> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) return emptyList()

        return candidates
            .mapNotNull { candidate ->
                val normalized = normalize(candidate.name)
                val distance = when {
                    normalized.contains(normalizedQuery) || normalizedQuery.contains(normalized) -> 0
                    else -> editDistance(normalizedQuery, normalized)
                }
                val budget = toleranceFor(max(normalizedQuery.length, normalized.length))
                if (distance <= budget) candidate to distance else null
            }
            .sortedWith(compareBy({ it.second }, { it.first.name.length }))
            .take(MAX_SUGGESTIONS)
            .map { it.first }
    }

    private fun toleranceFor(length: Int): Int = when {
        length <= SHORT_NAME_LENGTH -> 1
        length <= MEDIUM_NAME_LENGTH -> 2
        else -> 3
    }

    private fun normalize(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }

    /** Levenshtein distance, two-row variant. */
    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), substitution)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
