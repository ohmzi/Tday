package com.ohmz.tday.shared.nlp

/**
 * Splits a free-text "brain dump" into candidate task fragments, deterministically and
 * on-device. Shared so every platform (and the backend's non-AI fallback) fragments
 * identically: split on newlines and bullets, then on "and then" / ";" separators,
 * strip bullet/number prefixes, trim, drop blanks, de-dupe (case-insensitive), cap.
 *
 * Each fragment is then run through the platform's existing NLP parser (dates,
 * recurrence, priority); dated fragments become Todos, undated ones become Floaters.
 */
object BrainDumpSplitter {
    const val MAX_FRAGMENTS = 50
    private const val MIN_LENGTH = 2

    private val bulletPrefix = Regex("""^\s*(?:[-*•·▪◦]|\d+[.)])\s+""")
    private val inlineSeparators = Regex("""\s+and then\s+|;|\s+·\s+""", RegexOption.IGNORE_CASE)
    private val collapseSpaces = Regex("""\s{2,}""")

    fun split(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val fragments = mutableListOf<String>()
        val seen = HashSet<String>()

        for (rawLine in text.split('\n')) {
            val line = rawLine.replace('\r', ' ').trim()
            if (line.isEmpty()) continue
            val withoutBullet = line.replace(bulletPrefix, "")
            for (piece in withoutBullet.split(inlineSeparators)) {
                val fragment = piece.replace(bulletPrefix, "").replace(collapseSpaces, " ").trim()
                    .trim(',', ';', '-', '•', '·')
                    .trim()
                if (fragment.length < MIN_LENGTH) continue
                val key = fragment.lowercase()
                if (seen.add(key)) {
                    fragments.add(fragment)
                    if (fragments.size >= MAX_FRAGMENTS) return fragments
                }
            }
        }
        return fragments
    }
}
