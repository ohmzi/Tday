import Foundation

/// Swift port of the shared Kotlin GuideSearch (query side only). The doc-side
/// text is pre-normalized in the artifact by the same Kotlin `normalize`, so the
/// only normalization iOS reproduces is for the query. Behaviour must match the
/// Kotlin engine and the web TS port; the generated search fixtures guard parity.
enum GuideSearch {
    private static let titleWeight = 3
    private static let keywordWeight = 2
    private static let bodyWeight = 1

    // Same Latin diacritic groups as the Kotlin DIACRITIC_FOLD.
    private static let foldGroups: [(String, Character)] = [
        ("àáâãäåāăą", "a"),
        ("çćčĉ", "c"),
        ("ďđ", "d"),
        ("èéêëēĕėęě", "e"),
        ("ìíîïĩīĭįı", "i"),
        ("ñńņň", "n"),
        ("òóôõöøōŏő", "o"),
        ("ùúûüũūŭůűų", "u"),
        ("ýÿŷ", "y"),
        ("śšşŝ", "s"),
        ("žźż", "z"),
        ("ğĝ", "g"),
        ("ß", "s"),
    ]

    private static let fold: [Character: Character] = {
        var map: [Character: Character] = [:]
        for (chars, base) in foldGroups {
            for ch in chars { map[ch] = base }
        }
        return map
    }()

    /// Lowercase, fold Latin diacritics, collapse whitespace runs to one space.
    static func normalize(_ input: String) -> String {
        var out = ""
        var pendingSpace = false
        for ch in input.lowercased() {
            if ch.isWhitespace {
                pendingSpace = !out.isEmpty
                continue
            }
            if pendingSpace {
                out.append(" ")
                pendingSpace = false
            }
            out.append(fold[ch] ?? ch)
        }
        return out
    }

    /// Rank topics against a query, best-first. Every token must appear (token-AND);
    /// score sums per-token title(3)/keyword(2)/body(1); ties keep input order.
    static func rank(_ query: String, _ topics: [GuideTopicDTO]) -> [String] {
        let tokens = normalize(query).split(separator: " ").map(String.init).filter { !$0.isEmpty }
        if tokens.isEmpty { return [] }

        var scored: [(id: String, score: Int, index: Int)] = []
        for (index, topic) in topics.enumerated() {
            var total = 0
            var allMatched = true
            for token in tokens {
                var tokenScore = 0
                if topic.searchTitle.contains(token) { tokenScore += titleWeight }
                if topic.searchKeywords.contains(token) { tokenScore += keywordWeight }
                if topic.searchBody.contains(token) { tokenScore += bodyWeight }
                if tokenScore == 0 {
                    allMatched = false
                    break
                }
                total += tokenScore
            }
            if allMatched { scored.append((topic.id, total, index)) }
        }

        return scored
            .sorted { $0.score != $1.score ? $0.score > $1.score : $0.index < $1.index }
            .map { $0.id }
    }
}
