import SwiftUI

/// Opens the How-To guide pre-scrolled to a topic id. Set once at the app root
/// (AppRootView); the nil default hides every [GuideHelpLink] in previews or
/// surfaces without the main navigation stack.
private struct OpenGuideTopicKey: EnvironmentKey {
    static let defaultValue: ((String) -> Void)? = nil
}

extension EnvironmentValues {
    var openGuideTopic: ((String) -> Void)? {
        get { self[OpenGuideTopicKey.self] }
        set { self[OpenGuideTopicKey.self] = newValue }
    }
}

/// Topic ids mirrored from the shared `GuideTopicIds` (content is
/// contract-tested against the bundled artifact, which carries these ids).
enum GuideTopicId {
    static let nlpDateSyntax = "nlp-date-syntax"
    static let recurrencePresets = "recurrence-presets"
}

/// A quiet contextual "?" that deep-links from a feature surface into its
/// guide topic — the iOS counterpart of the web/Android `GuideHelpLink`.
/// Uses a tap gesture (not a Button) so it stays tappable when embedded
/// inside a larger Button row, and dismisses the presenting sheet before
/// pushing the guide onto the navigation stack.
struct GuideHelpLink: View {
    let topicId: String

    @Environment(\.openGuideTopic) private var openGuideTopic
    @Environment(\.dismiss) private var dismiss
    @Environment(\.tdayColors) private var colors

    var body: some View {
        if let openGuideTopic {
            Image("LucideCircleHelp")
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .frame(width: 18, height: 18)
                .foregroundStyle(colors.onSurfaceVariant.opacity(0.85))
                .padding(6)
                .contentShape(Rectangle())
                .onTapGesture {
                    dismiss()
                    openGuideTopic(topicId)
                }
                .accessibilityLabel(Text(L("How-To & Tips")))
                .accessibilityAddTraits(.isButton)
        }
    }
}
