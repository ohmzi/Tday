import Foundation

/// Loads the bundled, per-locale guide artifact. Everything is committed into the
/// app bundle, so this never touches the network — the guide works fully offline
/// and in Local Mode.
enum GuideContentStore {
    /// Load the guide for the current device language, falling back to English.
    static func load() -> GuideArtifact {
        let language = Locale.current.language.languageCode?.identifier ?? "en"
        return load(locale: language)
    }

    static func load(locale: String) -> GuideArtifact {
        let language = String(locale.prefix(2)).lowercased()
        return loadArtifact(locale: language)
            ?? loadArtifact(locale: "en")
            ?? .empty
    }

    private static func loadArtifact(locale: String, bundle: Bundle = .main) -> GuideArtifact? {
        guard
            let url = bundle.url(forResource: "guide.\(locale)", withExtension: "json"),
            let data = try? Data(contentsOf: url),
            let artifact = try? JSONDecoder().decode(GuideArtifact.self, from: data)
        else {
            return nil
        }
        return artifact
    }
}
