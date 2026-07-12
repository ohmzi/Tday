import SwiftUI

/// The in-app How-To / feature guide. Loads the bundled per-locale artifact
/// (generated from the shared Kotlin GuideCatalog) and searches it with the
/// shared-parity GuideSearch — so content and ranking match web and Android.
/// Fully offline / Local-Mode safe.
struct HelpGuideScreen: View {
    let viewModel: AppViewModel
    var initialTopic: String?

    @Environment(\.tdayColors) private var colors

    @State private var artifact = GuideArtifact.empty
    @State private var query = ""
    @State private var expandedId: String?
    @State private var loaded = false

    private var trimmed: String { query.trimmingCharacters(in: .whitespaces) }

    private var rankedIds: [String] {
        trimmed.isEmpty ? [] : GuideSearch.rank(query, artifact.topics)
    }

    private var byId: [String: GuideTopicDTO] {
        Dictionary(artifact.topics.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    }

    private var whatsNew: [GuideTopicDTO] {
        artifact.topics.filter { $0.sinceVersion == artifact.currentVersion }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                backButton
                Text(artifact.ui["title"] ?? "How-To & Tips")
                    .font(.tdayRounded(size: 28, weight: .heavy))
                    .foregroundStyle(colors.onSurface)
                    .padding(.top, 8)
                if let subtitle = artifact.ui["subtitle"] {
                    Text(subtitle)
                        .font(.tdayRounded(size: 14, weight: .regular))
                        .foregroundStyle(colors.onSurface.opacity(0.6))
                        .padding(.top, 4)
                }
                searchField
                    .padding(.top, 16)
                content
                    .padding(.top, 16)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 32)
        }
        .background(colors.background)
        .onAppear {
            guard !loaded else { return }
            artifact = GuideContentStore.load()
            expandedId = initialTopic
            loaded = true
        }
    }

    private var backButton: some View {
        Button(action: { viewModel.goBack() }) {
            HStack(spacing: 6) {
                Image("LucideArrowLeft")
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 18, height: 18)
                Text(artifact.ui["title"] ?? "Guide")
                    .font(.tdayRounded(size: 15, weight: .regular))
            }
            .foregroundStyle(colors.onSurface.opacity(0.7))
            .padding(.vertical, 8)
        }
        .buttonStyle(.plain)
    }

    private var searchField: some View {
        HStack(spacing: 8) {
            Image("LucideSearch")
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .frame(width: 18, height: 18)
                .foregroundStyle(colors.onSurface.opacity(0.5))
            TextField(artifact.ui["searchPlaceholder"] ?? "Search features…", text: $query)
                .font(.tdayRounded(size: 15, weight: .regular))
                .foregroundStyle(colors.onSurface)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
            if !query.isEmpty {
                Button(action: { query = "" }) {
                    Image("LucideX")
                        .renderingMode(.template)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 16, height: 16)
                        .foregroundStyle(colors.onSurface.opacity(0.5))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(colors.surface)
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(colors.onSurface.opacity(0.12)))
        )
    }

    @ViewBuilder
    private var content: some View {
        if !trimmed.isEmpty {
            let count = rankedIds.count
            Text((artifact.ui["results"] ?? "{{count}} results").replacingOccurrences(of: "{{count}}", with: "\(count)"))
                .font(.tdayRounded(size: 12, weight: .semibold))
                .foregroundStyle(colors.onSurface.opacity(0.6))
                .padding(.bottom, 8)
            if rankedIds.isEmpty {
                Text(artifact.ui["noResults"] ?? "No matches.")
                    .font(.tdayRounded(size: 15, weight: .regular))
                    .foregroundStyle(colors.onSurface.opacity(0.6))
                    .padding(.vertical, 24)
            } else {
                VStack(spacing: 10) {
                    ForEach(rankedIds, id: \.self) { id in
                        if let topic = byId[id] { topicCard(topic) }
                    }
                }
            }
        } else {
            VStack(alignment: .leading, spacing: 24) {
                if !whatsNew.isEmpty {
                    section(title: artifact.ui["whatsNew"] ?? "What's new", topics: whatsNew)
                }
                ForEach(artifact.sections.sorted { $0.order < $1.order }, id: \.id) { sec in
                    let topics = artifact.topics.filter { $0.section == sec.id }
                    if !topics.isEmpty {
                        section(title: sec.title, topics: topics)
                    }
                }
            }
        }
    }

    private func section(title: String, topics: [GuideTopicDTO]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title.uppercased())
                .font(.tdayRounded(size: 12, weight: .bold))
                .foregroundStyle(colors.onSurface.opacity(0.55))
                .padding(.leading, 4)
            ForEach(topics, id: \.id) { topic in topicCard(topic) }
        }
    }

    private func topicCard(_ topic: GuideTopicDTO) -> some View {
        let expanded = expandedId == topic.id
        return VStack(alignment: .leading, spacing: 0) {
            Button(action: { withAnimation(.easeInOut(duration: 0.15)) { expandedId = expanded ? nil : topic.id } }) {
                HStack(spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 10).fill(colors.primary.opacity(0.10)).frame(width: 36, height: 36)
                        Image(Self.iconAsset(topic.icon))
                            .renderingMode(.template)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 18, height: 18)
                            .foregroundStyle(colors.primary)
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 6) {
                            Text(topic.title)
                                .font(.tdayRounded(size: 15, weight: .bold))
                                .foregroundStyle(colors.onSurface)
                            badges(topic)
                        }
                        Text(topic.summary)
                            .font(.tdayRounded(size: 13, weight: .regular))
                            .foregroundStyle(colors.onSurface.opacity(0.6))
                            .lineLimit(1)
                    }
                    Spacer(minLength: 8)
                    Image("LucideChevronRight")
                        .renderingMode(.template)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 16, height: 16)
                        .foregroundStyle(colors.onSurface.opacity(0.4))
                        .rotationEffect(.degrees(expanded ? 90 : 0))
                }
                .padding(14)
            }
            .buttonStyle(.plain)

            if expanded {
                VStack(alignment: .leading, spacing: 10) {
                    Divider().background(colors.onSurface.opacity(0.06))
                    ForEach(Array(topic.body.enumerated()), id: \.offset) { _, block in
                        bodyBlock(block)
                    }
                    if let path = topic.deepLink?.ios, !(viewModel.isLocalMode && topic.serverOnly) {
                        Button(action: { openDeepLink(path) }) {
                            Text(artifact.ui["tryIt"] ?? "Try it")
                                .font(.tdayRounded(size: 14, weight: .bold))
                                .foregroundStyle(colors.onPrimary)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(RoundedRectangle(cornerRadius: 12).fill(colors.primary))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 14)
            }
        }
        .background(
            RoundedRectangle(cornerRadius: 18)
                .fill(colors.surface)
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(colors.onSurface.opacity(0.06)))
        )
    }

    @ViewBuilder
    private func badges(_ topic: GuideTopicDTO) -> some View {
        if topic.sinceVersion == artifact.currentVersion { pill(artifact.ui["badges.new"] ?? "New") }
        if topic.badge == "HIDDEN_GEM" { pill(artifact.ui["badges.hiddenGem"] ?? "Hidden gem") }
        if topic.badge == "PRO_TIP" { pill(artifact.ui["badges.proTip"] ?? "Pro tip") }
        if topic.serverOnly { pill(artifact.ui["badges.server"] ?? "Server mode") }
    }

    private func pill(_ text: String) -> some View {
        Text(text.uppercased())
            .font(.tdayRounded(size: 9, weight: .bold))
            .foregroundStyle(colors.primary)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(RoundedRectangle(cornerRadius: 6).fill(colors.primary.opacity(0.12)))
    }

    @ViewBuilder
    private func bodyBlock(_ block: GuideBlockDTO) -> some View {
        let text = block.texts.first ?? ""
        switch block.type {
        case "STEPS":
            VStack(alignment: .leading, spacing: 6) {
                ForEach(Array(block.texts.enumerated()), id: \.offset) { i, step in
                    HStack(alignment: .top, spacing: 10) {
                        ZStack {
                            Circle().fill(colors.primary.opacity(0.12)).frame(width: 20, height: 20)
                            Text("\(i + 1)").font(.tdayRounded(size: 11, weight: .bold)).foregroundStyle(colors.primary)
                        }
                        Text(step).font(.tdayRounded(size: 14, weight: .regular)).foregroundStyle(colors.onSurface)
                    }
                }
            }
        case "TIP":
            Text(text)
                .font(.tdayRounded(size: 13, weight: .regular))
                .foregroundStyle(colors.onSurface.opacity(0.75))
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 10).fill(colors.primary.opacity(0.06)))
        case "KBD", "EXAMPLE":
            Text(text)
                .font(.system(size: 14, design: .monospaced))
                .foregroundStyle(colors.onSurface)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(RoundedRectangle(cornerRadius: 8).fill(colors.onSurface.opacity(0.06)))
        default:
            Text(text)
                .font(.tdayRounded(size: 14, weight: .regular))
                .foregroundStyle(colors.onSurface.opacity(0.85))
        }
    }

    private func openDeepLink(_ path: String) {
        guard let url = URL(string: "tday://\(path)"), let route = AppRoute.from(url: url) else { return }
        viewModel.navigationPath.append(route)
    }

    // Lucide asset-catalog name for a kebab glyph (e.g. "wand-sparkles" ->
    // "LucideWandSparkles"). Every catalog glyph has an imageset, guarded by the
    // guide-icons coverage test.
    private static func iconAsset(_ name: String) -> String {
        let pascal = name.split(separator: "-").map { word in
            String(word.prefix(1)).uppercased() + String(word.dropFirst())
        }.joined()
        return "Lucide" + pascal
    }
}
