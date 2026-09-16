import Foundation

/// Guesses a list's glyph from its name, for lists whose owner never picked one.
///
/// Swift half of the shared matcher. The TABLE it reads is generated from
/// `shared/.../listicon/ListIconInference.kt` into `TdayListIconInferenceGenerated.swift`
/// by `:shared:exportListIconTable`, so the words themselves cannot fork; `verifyListIconTable`
/// fails CI the moment the committed copy drifts. What lives here is the RULE, hand-written
/// the way `floaterRestingTier` in `DomainModels.swift` is the hand-written twin of
/// `FloaterResting.tierFor` — fifteen lines that need this platform's own string handling,
/// against two hundred words that need none.
///
/// The THREE rules — this one, `ListIconInference.kt` and `listIconInference.ts` — are held
/// to one set of questions by `tday-web/tests/guardrails/list-icon-inference-parity.test.ts`,
/// which extracts the titles pinned in `ListIconInferenceTest.kt`, `ListIconInferenceTests.swift`
/// and `list-icon-inference.test.ts` and fails when one of them learns a case the others have
/// not. It compares the QUESTIONS, not the answers: each case table already asserts its own
/// answers against its own real implementation, and that is the assertion with teeth. What no
/// single-language file can see is whether a case exists in the other two at all — and that
/// was the hole a real divergence came through on this branch, web splitting words on an ASCII
/// class while these two split on Unicode, with no non-ASCII row anywhere to catch it.
///
/// ENGLISH ONLY, as the shared KDoc says at length: the i18n parity guardrail compares key
/// SETS, so it would pass nine locales of untranslated English without a murmur, and the
/// limitation has to be written down or it is written nowhere.
///
/// - Parameter name: the list's title, as the user typed it.
/// - Returns: the inferred icon key, or `nil` when the title evidences nothing or evidences
///   two different glyphs. Never the default key — see `tdayResolvedListIconKey`.
func tdayInferredListIconKey(forListName name: String?) -> String? {
    guard let name, !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
        return nil
    }

    var found: String?
    for word in tdayListIconInferenceWords(of: name) {
        guard let iconKey = TdayListIconInferenceTable.iconKeyByKeyword[word] else { continue }
        if found == nil {
            found = iconKey
        } else if found != iconKey {
            // Two subjects, one glyph slot. "Work Travel" is a briefcase and a plane with
            // equal warrant, and picking the leftmost would only hide the coin toss behind
            // reading order.
            return nil
        }
    }
    return found
}

/// The glyph key for a list that may never have been given one.
///
/// Two sources, in this order, and the order is the whole rule:
///  1. `iconKey` — what the user picked. Set means chosen; it wins, always, including when
///     the choice happens to be the default, and nothing below is consulted.
///  2. `listName` — the inference, which answers `nil` whenever it is unsure.
///
/// Android resolves a third source between them, the device-local `SecureConfigStore` icon
/// shadow. iOS has the same store (`SecureStore.saveListIcon`) but has never wired it:
/// `mapListDTO`'s `iconFallback` is nil at every call site, so `dto.iconKey` is the only
/// stored opinion that exists here. One source fewer, same order.
///
/// DISPLAY ONLY. The inferred key is never handed to `ListRepository.updateList` or to
/// `SecureStore.saveListIcon`. A guess that persists stops being a guess: the next rename
/// would inherit the old one, and the user's "never chose" would have been spent by a side
/// effect they never saw. The sheets enforce the other end of this — an untouched picker
/// submits `nil`, not the glyph it was previewing.
///
/// Returns `String?` rather than defaulting here, because `tdayLucideListAsset` already
/// answers `LucideInbox` for nil, for blank and for an unknown key alike. Collapsing the
/// three at this layer instead would leave callers that need the distinction — the sheets,
/// the tests — with no way back to it.
func tdayResolvedListIconKey(_ iconKey: String?, listName: String?) -> String? {
    if let iconKey, !iconKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
        return iconKey
    }
    return tdayInferredListIconKey(forListName: listName)
}

/// The title cut into lowercase alphanumeric runs.
///
/// WHOLE WORDS, NEVER SUBSTRINGS, which is the single decision that keeps this feature from
/// being worse than what it replaced: `contains` reads "Carpentry" as `car`, "Firewood" as
/// `fire` and "Workaround" as `work`, and a cart on a carpentry list is a confident lie where
/// the inbox was only uninformative.
///
/// Hand-rolled rather than `components(separatedBy: CharacterSet.alphanumerics.inverted)`,
/// which returns empty strings between adjacent separators — "Groceries!!!" would yield
/// three of them — and would then need filtering back out. This loop is the same shape as
/// the Kotlin `wordsOf`, deliberately: two implementations that read alike are two that can
/// be compared when the parity test says they disagree.
private func tdayListIconInferenceWords(of title: String) -> [String] {
    var words: [String] = []
    var word = ""
    for character in title.lowercased() {
        if character.isLetter || character.isNumber {
            word.append(character)
        } else if !word.isEmpty {
            words.append(word)
            word = ""
        }
    }
    if !word.isEmpty { words.append(word) }
    return words
}
