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
    // NEW badges show until the guide has been opened in this release; the
    // last-seen version persists in UserDefaults (GuideStore).
    @State private var showNewBadges = false
    @State private var scrollOffset: CGFloat = 0
    @State private var searchExpanded = false
    @FocusState private var searchFieldFocused: Bool

    private var titleCollapseProgress: CGFloat {
        let distance = TodoTimelineMetrics.titleCollapseDistance
        guard distance > 0 else { return 0 }
        return min(max(scrollOffset / distance, 0), 1)
    }

    private var guideTitle: String { artifact.ui["title"] ?? "How-To & Tips" }

    private var trimmed: String { query.trimmingCharacters(in: .whitespaces) }

    /// Results only stand while the bar is actually holding the field — the
    /// same reading of "searching" the completed and settings screens use.
    private var isSearching: Bool { searchExpanded && !trimmed.isEmpty }

    private var rankedIds: [String] {
        isSearching ? GuideSearch.rank(query, artifact.topics) : []
    }

    private var topBarActions: [TimelineTopBarAction] {
        [
            TimelineTopBarAction(
                systemName: "magnifyingglass",
                assetName: "NavSearch",
                usesCircularChrome: true,
                accessibilityLabel: L("Search"),
                action: openSearch
            ),
        ]
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
                TimelineExpandedTitleRow(
                    title: guideTitle,
                    accentColor: colors.onSurface,
                    collapseProgress: titleCollapseProgress,
                    mark: Image("LucideCircleHelp"),
                    markAccentColor: colors.primary
                )
                .background {
                    TimelineScrollOffsetObserver { scrollOffset = $0 }
                        .frame(width: 0, height: 0)
                }
                // The settle every other collapsing screen has and this one did
                // not: without it the guide's title could be left parked half
                // way up, which is the one place the header reads as broken
                // rather than as mid-gesture. Attached beside the offset
                // observer, as CompletedScreen and SettingsScreen both do.
                .onVerticalScrollSnap(
                    collapseDistance: TodoTimelineMetrics.titleCollapseDistance
                )
                // The shared header carries a title only, so the guide's
                // subtitle rides just below it and fades on the same curve.
                if let subtitle = artifact.ui["subtitle"] {
                    Text(subtitle)
                        .font(.tdayRounded(size: 14, weight: .regular))
                        .foregroundStyle(colors.onSurface.opacity(0.6))
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.top, 4)
                        .opacity(1 - Double(TodoTimelineMetrics.progress(
                            titleCollapseProgress,
                            from: TodoTimelineMetrics.expandedTitleFadeStart,
                            to: TodoTimelineMetrics.expandedTitleFadeEnd
                        )))
                }
                // The field has moved up into the bar, so the sections take the
                // spacing the capsule used to hold rather than leaving its slot
                // empty under the subtitle.
                content
                    .padding(.top, 16)
            }
            .padding(.horizontal, TodoTimelineMetrics.horizontalPadding)
            .padding(.bottom, 32)
        }
        .background(colors.background)
        .safeAreaInset(edge: .top, spacing: 0) {
            TimelineTopBar(
                title: guideTitle,
                accentColor: colors.onSurface,
                collapseProgress: titleCollapseProgress,
                onBack: { viewModel.goBack() },
                actions: topBarActions,
                searchActive: searchExpanded,
                searchText: $query,
                searchPlaceholder: artifact.ui["searchPlaceholder"] ?? "Search features…",
                searchFieldFocused: $searchFieldFocused,
                onSearchClose: closeSearch
            )
        }
        // The field only joins the hierarchy once the bar has swapped its row
        // over, so focusing it in the same turn is dropped on the floor.
        .onChange(of: searchExpanded) { _, expanded in
            guard expanded else {
                searchFieldFocused = false
                return
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.30) {
                if searchExpanded {
                    searchFieldFocused = true
                }
            }
        }
        // This screen draws its own bar, so the system one would only stack a
        // second back button above it.
        .navigationTitle("")
        .navigationBarBackButtonHidden(true)
        .toolbar(.hidden, for: .navigationBar)
        .onAppear {
            guard !loaded else { return }
            artifact = GuideContentStore.load()
            expandedId = initialTopic
            loaded = true

            let store = GuideStore()
            showNewBadges = store.lastSeenGuideVersion() != artifact.currentVersion
            store.setLastSeenGuideVersion(artifact.currentVersion)
        }
    }

    private func openSearch() {
        HapticManager.buttonTap()
        withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
            searchExpanded = true
        }
    }

    /// Leaving the search drops the query with it, so the guide is whole again
    /// the next time the bar is opened — the same bargain every other screen makes.
    private func closeSearch() {
        HapticManager.sheetDismiss()
        searchFieldFocused = false
        withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
            searchExpanded = false
        }
        query = ""
    }

    @ViewBuilder
    private var content: some View {
        if isSearching {
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
        if showNewBadges, topic.sinceVersion == artifact.currentVersion {
            pill(artifact.ui["badges.new"] ?? "New")
        }
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
