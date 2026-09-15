import SwiftUI
import UIKit

private enum CompletedRestorePhase {
    case completed
    case unchecked
    case unstruck
    case fading
}

struct CompletedScreen: View {
    private let pullRefreshEnabled: Bool
    @State private var viewModel: CompletedViewModel
    @Environment(\.tdayColors) private var colors
    @Environment(\.dismiss) private var dismiss
    /// Gates the history's own motion — see `completedTimelineAnimationKey`'s
    /// `.animation(_:value:)` and `completedRowTransition`.
    @Environment(\.tdayAnimation) private var tdayAnimation
    @State private var editingItem: CompletedItem?
    @State private var timelineScrollOffset: CGFloat = 0
    @State private var collapsedSectionIDs: Set<String> = []
    /// The screen's single swipe slot — see `TodoListScreen` for the shape and its one rule:
    /// every dismissal is a WRITE to this and nothing else. No host `body` may read it, or a
    /// cheap write becomes a full re-evaluation of the screen.
    @State private var openSwipeTaskID: String?
    @FocusState private var searchFieldFocused: Bool
    @State private var searchExpanded = false
    @State private var searchQuery = ""

    init(container: AppContainer, pullRefreshEnabled: Bool = false) {
        self.pullRefreshEnabled = pullRefreshEnabled
        _viewModel = State(initialValue: CompletedViewModel(container: container))
    }

    private var groupedItems: [TimelineSection<CompletedItem>] {
        buildCompletedTimelineSections(items: searchedItems)
    }

    private var normalizedSearchQuery: String {
        searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(with: .current)
    }

    private var isSearching: Bool {
        searchExpanded && !normalizedSearchQuery.isEmpty
    }

    /// The history and nothing else: this screen searches what it is showing,
    /// the way each web page searches its own list.
    private var searchedItems: [CompletedItem] {
        guard isSearching else {
            return viewModel.items
        }
        return viewModel.items.filter { item in
            item.title.lowercased(with: .current).contains(normalizedSearchQuery) ||
                flattenNotesToPlainText(item.description)
                    .lowercased(with: .current)
                    .contains(normalizedSearchQuery)
        }
    }

    private var searchPlaceholder: String {
        L("Search in %@", L("Completed"))
    }

    /// No magnifier over an empty history: there is no set for a query to
    /// narrow, and the button would only raise a keyboard over the empty-state
    /// scene, which is the whole of what the screen has to say. Gates the
    /// button, not the bar — a search already open stays open.
    private var topBarActions: [TimelineTopBarAction] {
        guard !viewModel.items.isEmpty else {
            return []
        }
        return [
            TimelineTopBarAction(
                systemName: "magnifyingglass",
                assetName: "NavSearch",
                usesCircularChrome: true,
                accessibilityLabel: L("Search"),
                action: openSearch
            ),
        ]
    }

    private var completedAccentColor: Color {
        Color(.sRGB, red: 94.0 / 255.0, green: 104.0 / 255.0, blue: 120.0 / 255.0, opacity: 1)
    }

    private var completedCheckmarkColor: Color {
        Color(.sRGB, red: 111.0 / 255.0, green: 191.0 / 255.0, blue: 134.0 / 255.0, opacity: 1)
    }

    private var titleCollapseProgress: CGFloat {
        let distance = TodoTimelineMetrics.titleCollapseDistance
        guard distance > 0 else { return 0 }
        return min(max(timelineScrollOffset / distance, 0), 1)
    }

    private var completedTimelineAnimationKey: String {
        searchedItems.map(\.id).joined(separator: "|")
    }

    /// The gate for both empty scenes, and the value the overlay's
    /// `.animation(_:value:)` keys off. One property rather than the condition
    /// written twice, because the thing that decides the scene is on screen and
    /// the thing that supplies the transaction to take it off screen drifting
    /// apart is exactly how a removal transition goes quietly inert.
    private var showsCompletedEmptyState: Bool {
        completedAnswer == .empty
    }

    /// History's answer about itself, and the one place either scene's emptiness
    /// is counted. The `!viewModel.isLoading` / `viewModel.isLoading` pair this
    /// replaced were the same copy-pasted gate the Anytime home had, with the
    /// same defect: `isLoading` is raised by nothing but `refresh()`, so it
    /// always meant "a refresh over an answer already on screen" — the one
    /// condition an empty scene must be held THROUGH — and the two gates tripped
    /// on the same pull, swapping the scene for three grey bars and back again.
    /// `feedAnswer` has no term a pull can move.
    private var completedAnswer: FeedAnswer {
        feedAnswer(
            storeRead: viewModel.hasHydratedFromCache,
            rowsEmpty: searchedItems.isEmpty,
            firstAnswerLanded: viewModel.firstAnswerLanded
        )
    }

    /// The opposite half of [showsCompletedEmptyState], and deliberately its
    /// mirror: history that has not answered yet is neither empty nor a list, and
    /// before this the screen answered that third case with the empty frame it
    /// also uses for "there is nothing".
    ///
    /// The third state is the same one `completedAnswer` names, so the two are
    /// exhaustive by construction rather than by two conditions agreeing.
    private var showsCompletedFeedSkeleton: Bool {
        completedAnswer == .awaitingFirst
    }

    /// The empty scene's insertion and removal. The same shape, for the same
    /// reasons, as `TodoListScreen.emptyStateIllustrationTransition`:
    ///
    /// - Removal is `.opacity`, so the scene fades out. Deleting a character out
    ///   of a query that matched nothing turns the no-match scene back into a
    ///   list of rows, and without a removal leg SwiftUI simply stops drawing
    ///   the scene on that frame — a cut, in the middle of a sequence the user
    ///   is driving one keystroke at a time.
    /// - Insertion is `.identity`, deliberately inert. `TdayEmptyState` gives
    ///   itself a 0.52s rise-and-fade from its own `onAppear` (see its doc
    ///   comment: "it never cuts in") which owes nothing to SwiftUI's transition
    ///   system, so an opacity leg here would stack a second, independent fade
    ///   on the same arrival and buy nothing for it.
    ///
    /// Spelled out again rather than shared with `TodoListScreen`: the two are
    /// in different files and there is no motion-token layer between them yet.
    /// If one moves, move both.
    private var completedEmptyStateTransition: AnyTransition {
        .asymmetric(insertion: .identity, removal: .opacity)
    }

    /// The scene's exit. The same 0.22s ease-in as the timeline's illustration
    /// exit (`TodoListScreen.EarlierIllustrationHandoff.exitDuration`), so the
    /// two screens' empty scenes leave the same way, and shorter than the travel
    /// the rows taking its place ride (`TdayFeedItemMotion.placement`, on
    /// `completedTimelineAnimationKey`) so the scene is out of the way rather than
    /// dissolving over the rows it was standing in for.
    private enum CompletedEmptyStateExit {
        static let duration: Double = 0.22
    }

    var body: some View {
        completedTimelineContent
            .tdayPullToRefresh(isRefreshing: viewModel.isLoading, isEnabled: pullRefreshEnabled) {
                await viewModel.refresh(userInitiated: true)
            }
            .background(colors.background)
            .overlay {
                // No blanket `allowsHitTesting(false)` here any more: the watermark
                // turns its own hits off, and the search empty state has a button
                // that has to stay tappable.
                ZStack {
                    EmptyTaskWatermark(
                        systemName: "checkmark",
                        accentColor: completedAccentColor,
                        assetName: "TileComplete"
                    )
                    if showsCompletedEmptyState {
                        if isSearching {
                            searchEmptyState
                                .transition(completedEmptyStateTransition)
                        } else {
                            TdayEmptyState(
                                assetName: "TileComplete",
                                accentColor: completedAccentColor,
                                title: L("No completed tasks"),
                                description: L("Tick something off and it will land here.")
                            )
                            // This scene is decoration: nothing in it is
                            // tappable, and the overlay it sits in spans the
                            // whole screen. Left hit-testable it would answer
                            // for every touch the history beneath it should have
                            // had — the list's own scroll, and with it the hero
                            // title's collapse, which `timelineHeroTitleRow`
                            // reads off that scroll. The search scene opposite
                            // keeps its hits because it owns a button; this one
                            // has nothing to defend.
                            //
                            // It is *not* about the refresh gesture, whatever
                            // this comment used to say: `pullRefreshEnabled`
                            // defaults to false and `AppRootView` builds this
                            // screen without it, so the `tdayPullToRefresh` in
                            // the body above is wired to nothing and there is no
                            // drag here to protect. That claim cost one verifier
                            // pass a wrong conclusion, so it is written down
                            // rather than left to be re-derived.
                            .allowsHitTesting(false)
                            .transition(completedEmptyStateTransition)
                        }
                    }
                }
                // The transaction the removal leg above runs in. The scene
                // leaves when a query stops matching nothing, and that is a
                // keystroke written straight into `searchQuery` by the field in
                // the top bar with no `withAnimation` within reach of it — so
                // without this there is no animation for the removal to take and
                // SwiftUI drops the view between two frames. Keyed to the gate
                // rather than to `searchQuery`, so the keystrokes that only
                // narrow a list already on screen open no transaction at all.
                .animation(
                    .easeIn(duration: CompletedEmptyStateExit.duration),
                    value: showsCompletedEmptyState
                )
            }
            // Tapping the content puts the field away, as it does on the root feeds.
            // Sits above the bar's own safe-area inset, so the gesture only ever
            // sees taps below the bar and never one on it.
            .tdayClosesSearchOnOutsideTap(isSearchOpen: searchExpanded) {
                closeSearch()
            }
            .navigationBackButtonBehavior()
            .navigationTitleTypography(
                largeTitleColor: completedAccentColor,
                inlineTitleColor: colors.onSurface,
                backgroundColor: colors.background
            )
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(.hidden, for: .navigationBar)
            .safeAreaInset(edge: .top, spacing: 0) {
                TimelineTopBar(
                    title: L("Completed"),
                    accentColor: completedAccentColor,
                    collapseProgress: titleCollapseProgress,
                    onBack: { dismiss() },
                    actions: topBarActions,
                    searchActive: searchExpanded,
                    searchText: $searchQuery,
                    searchPlaceholder: searchPlaceholder,
                    searchFieldFocused: $searchFieldFocused,
                    onSearchClose: closeSearch
                )
            }
            .onChange(of: viewModel.items.map(\.id)) { _, ids in
                guard let openSwipeTaskID, !ids.contains(openSwipeTaskID) else { return }
                self.openSwipeTaskID = nil
            }
            .onDisappear {
                // Returning to a screen must never show an armed Delete pill — see
                // `TodoListScreen`'s `.onDisappear` for why this is also iOS's answer to back.
                openSwipeTaskID = nil
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
            .createTaskSheet(item: $editingItem) { item in
                CreateTaskSheet(
                    lists: item.isFloater ? viewModel.floaterLists : viewModel.lists,
                    titleText: L("Edit task"),
                    submitText: L("Save"),
                    initialPayload: CreateTaskPayload(title: item.title, description: item.description, priority: item.priority, due: item.due, rrule: item.rrule, listId: nil),
                    onParseTaskTitleNlp: nil,
                    onDismiss: { editingItem = nil },
                    onSubmit: { payload in
                        await viewModel.update(item, payload: payload)
                    }
                )
            }
            // Same bargain as the task timeline: the only field on this screen is
            // the search box in the top safe-area inset, which the keyboard cannot
            // reach, so keyboard avoidance had nothing to protect and only shortened
            // the history's region — carrying the centred empty scene up with it.
            .ignoresSafeArea(.keyboard, edges: .bottom)
    }

    private var completedTimelineContent: some View {
        ZStack {
            List {
                timelineHeroTitleRow

                if let errorMessage = viewModel.errorMessage {
                    Section {
                        ErrorRetryView(message: errorMessage) {
                            Task { await viewModel.refresh() }
                        }
                        .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 18, trailing: TodoTimelineMetrics.horizontalPadding))
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                    }
                }

                // The cold open this comment used to describe cannot happen on
                // this client, and saying so was costing the screen its own fix:
                // `CompletedViewModel` hydrates from the local cache
                // synchronously in `init`, so history beats the first body pass
                // and `isLoading` only ever meant a refresh over rows already
                // drawn. Gated on the flag, this placeholder grew three grey bars
                // during a pull on a history that had already answered.
                //
                // What it draws for now is the state it was written for: the
                // cache is empty AND no first answer has ever landed — a fresh
                // install or a fresh login whose first sync failed or is still in
                // flight, which is the one case where "nothing completed yet" is
                // a claim this screen has no business making.
                if showsCompletedFeedSkeleton {
                    // Today stacks its placeholder over its rows so the two share one
                    // slot; a `List` has no such move — its sections are siblings by
                    // construction, and a `Section` cannot be overlaid on the ones after
                    // it. So this one is above the rows it hands over to, and whether the
                    // feed reflows as they swap depends on something source cannot settle:
                    // SwiftUI holds a removing view in the layout, while a `List` on iOS
                    // resolves the same change as a UIKit batch update that animates the
                    // delete and the inserts into their final places together. The two
                    // look different, and only a device can say which one this is — so it
                    // is a line in docs/verification/phase-9-device-pass.md rather than a
                    // claim here.
                    Section {
                        // History's row, not Today's: same toggle, but 8 pt of
                        // vertical padding to Today's 10 and no horizontal padding
                        // of its own, so the default set would stand a line taller
                        // than the rows it is standing in for.
                        TdayTaskRowSkeletonGroup(metrics: .completedTimeline)
                            .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
                            .listRowBackground(Color.clear)
                            .listRowSeparator(.hidden)
                            .transition(.opacity)
                    }
                }

                ForEach(Array(groupedItems.enumerated()), id: \.element.id) { index, section in
                    completedTimelineSection(
                        section,
                        sectionIndex: index,
                        sections: groupedItems,
                        isFirstSection: index == 0
                    )
                }

                Color.clear
                    .frame(height: 120)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                    .disableVerticalScrollBounce()
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .contentMargins(.top, 0, for: .scrollContent)
            .listRowSpacing(0)
            .listSectionSpacing(0)
            .environment(\.defaultMinListRowHeight, 1)
            .disableVerticalScrollBounce()
            // The placeholder's hand-over, on the List rather than beside the
            // branch that holds it: inside a `List` the modifiers written around an
            // `if` are handed down to the rows themselves and leave with them, so
            // the removal would have no transaction left to run in.
            //
            // BELOW the travel, and the order is the point. `searchedItems` drives
            // both values, so the first page landing changes both in one update,
            // and between two `.animation(_:value:)` that fire together the one
            // nearest the content wins. Above the travel this lost every time it
            // mattered and the dissolve ran at Emphasis instead of Enter. Below it,
            // the travel still carries every update this one is not about, because
            // a modifier whose value held still leaves the transaction alone.
            .animation(
                tdayAnimation(TdayTaskRowSkeleton.crossfade),
                value: showsCompletedFeedSkeleton
            )
            // The history's travel. Rows that arrive and leave override this from
            // their own legs (`completedRowTransition`); this is what carries
            // everything a search narrowing the list merely moves.
            .animation(
                tdayAnimation(TdayFeedItemMotion.placement),
                value: completedTimelineAnimationKey
            )

        }
    }

    private var timelineHeroTitleRow: some View {
        TimelineExpandedTitleRow(
            title: L("Completed"),
            accentColor: completedAccentColor,
            collapseProgress: titleCollapseProgress,
            mark: Image("TileComplete")
        )
        .background {
            TimelineScrollOffsetObserver { timelineScrollOffset = $0 }
                .frame(width: 0, height: 0)
        }
        .onVerticalScrollSnap(collapseDistance: TodoTimelineMetrics.titleCollapseDistance)
        .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
    }

    /// Shown in place of the history when a search matches nothing. Same three
    /// beats as web's panel: the scene, the line, and a way out of the query
    /// that does not need the keyboard back.
    private var searchEmptyState: some View {
        TdayEmptyState(
            assetName: "NavSearch",
            accentColor: completedAccentColor,
            title: L("No matching tasks"),
            description: L("Try a different word, or clear the search."),
            action: AnyView(
                Button {
                    HapticManager.buttonPress()
                    searchQuery = ""
                    searchFieldFocused = true
                } label: {
                    Text(L("Clear search"))
                        .font(.tdayRounded(size: 15, weight: .bold))
                        .foregroundStyle(colors.primary)
                }
                .buttonStyle(.plain)
            )
        )
    }

    private func openSearch() {
        HapticManager.buttonPress()
        withAnimation(TdayMotion.snappy) {
            searchExpanded = true
        }
    }

    /// Leaving the search drops the query with it, so the history is whole again
    /// the next time the bar is opened — the same bargain web's close makes.
    private func closeSearch() {
        HapticManager.buttonPress()
        searchFieldFocused = false
        withAnimation(TdayMotion.snappy) {
            searchExpanded = false
        }
        searchQuery = ""
    }

    @ViewBuilder
    private func completedTimelineSection(
        _ section: TimelineSection<CompletedItem>,
        sectionIndex: Int,
        sections: [TimelineSection<CompletedItem>],
        isFirstSection: Bool
    ) -> some View {
        // A live query outranks a shut month: history opens with older months
        // collapsed, and a task the search turned up inside one must not stay
        // hidden behind its header. The month is therefore not the reader's to
        // shut while the query stands — a header that still took a tap would
        // flip the stored state behind a screen that could not show it. Same
        // call as `canCollapseTimelineSection` on the timeline screens.
        let isCollapsible = !isSearching
        let isCollapsed = isCollapsible && collapsedSectionIDs.contains(section.id)

        Section {
            if !isCollapsed {
                ForEach(Array(section.items.enumerated()), id: \.element.id) { itemIndex, item in
                    completedTimelineRow(item)
                        .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                        .transition(completedRowTransition())
                    if shouldShowDateDivider(after: itemIndex, inSectionAt: sectionIndex, sections: sections) {
                        TimelineRowDivider()
                            .transition(completedRowTransition())
                    }
                }
            }
        } header: {
            TimelineSectionHeader(
                title: section.title,
                isActiveDropTarget: false,
                isCollapsible: isCollapsible,
                isCollapsed: isCollapsed,
                onTap: {
                    guard isCollapsible else { return }
                    toggleCompletedSection(section)
                }
            )
            .listRowInsets(
                EdgeInsets(
                    top: isFirstSection ? 0 : TodoTimelineMetrics.sectionTopSpacing,
                    leading: 0,
                    bottom: 0,
                    trailing: 0
                )
            )
            .timelinePinnedSectionHeaderBackground()
            .listRowSeparator(.hidden)
        }
    }

    private func toggleCompletedSection(_ section: TimelineSection<CompletedItem>) {
        let id = section.id
        withAnimation(.spring(response: 0.28, dampingFraction: 0.9)) {
            if collapsedSectionIDs.contains(id) {
                collapsedSectionIDs.remove(id)
            } else {
                collapsedSectionIDs.insert(id)
            }
        }
    }

    private func shouldShowDateDivider(
        after itemIndex: Int,
        inSectionAt sectionIndex: Int,
        sections: [TimelineSection<CompletedItem>]
    ) -> Bool {
        guard sections.indices.contains(sectionIndex),
              sections[sectionIndex].items.indices.contains(itemIndex) else {
            return false
        }

        let currentItem = sections[sectionIndex].items[itemIndex]
        let currentDate = currentItem.completedAt ?? currentItem.due ?? .distantPast
        let nextItemInSection = sections[sectionIndex].items.dropFirst(itemIndex + 1).first
        if let nextItemInSection {
            let nextDate = nextItemInSection.completedAt ?? nextItemInSection.due ?? .distantPast
            return !Calendar.current.isDate(currentDate, inSameDayAs: nextDate)
        }

        let nextVisibleItem = sections.dropFirst(sectionIndex + 1)
            .first { !collapsedSectionIDs.contains($0.id) && !$0.items.isEmpty }?
            .items.first

        guard let nextVisibleItem else {
            return false
        }
        let nextDate = nextVisibleItem.completedAt ?? nextVisibleItem.due ?? .distantPast
        return !Calendar.current.isDate(currentDate, inSameDayAs: nextDate)
    }

    /// The history's rows are the same three events as the timeline's, so they run
    /// the same three specs. This screen already had the asymmetry — it just had it
    /// in numbers of its own (0.16 in, 0.1 out) that no other feed shared, and that
    /// sat under a 0.24s travel nothing else shared either. Both legs also leave
    /// `.easeOut` for the feed's one curve, and the removal lengthens 0.1 -> Quick:
    /// a visible change, made on purpose, and the reason `docs/motion.md` no longer
    /// files that 0.1 under sequencing constants nobody watches.
    private func completedRowTransition() -> AnyTransition {
        TdayFeedItemMotion.row(reduceMotion: !tdayAnimation.isEnabled)
    }

    private func completedTimelineRow(_ item: CompletedItem) -> some View {
        CompletedTimelineRow(
            item: item,
            completedCheckmarkColor: completedCheckmarkColor,
            onUncomplete: {
                await viewModel.uncomplete(item)
            },
            onDelete: {
                await viewModel.delete(item)
            },
            onEdit: {
                editingItem = item
            },
            onCopy: {
                viewModel.copyToClipboard(item)
            },
            openSwipeTaskID: $openSwipeTaskID
        )
    }
}

private struct CompletedTimelineRow: View {
    let item: CompletedItem
    let completedCheckmarkColor: Color
    let onUncomplete: () async -> Void
    let onDelete: () async -> Void
    let onEdit: () -> Void
    let onCopy: () -> Void
    @Binding var openSwipeTaskID: String?

    @Environment(\.tdayColors) private var colors
    @State private var restorePhase = CompletedRestorePhase.completed

    private var showCompletedCheckmark: Bool {
        restorePhase == .completed
    }

    private var showStrikethrough: Bool {
        restorePhase == .completed || restorePhase == .unchecked
    }

    private var isRestoring: Bool {
        restorePhase != .completed
    }

    private var isFading: Bool {
        restorePhase == .fading
    }

    private var toggleColor: Color {
        showCompletedCheckmark ? completedCheckmarkColor : colors.onSurfaceVariant.opacity(0.78)
    }

    private var titleColor: Color {
        showStrikethrough ? colors.onSurface.opacity(0.78) : colors.onSurface
    }

    var body: some View {
        let completedDate = item.completedAt ?? item.due ?? .distantPast
        let completedTimeText = completedDate.formatted(.dateTime.hour().minute().locale(AppLocale.current))
        let showListIndicator = item.listName?.isEmpty == false
        let priorityIcon = priorityIndicatorSymbolName(item.priority)

        VStack(spacing: 0) {
            HStack(alignment: .center, spacing: TodoTimelineMetrics.minimalRowContentSpacing) {
                Button {
                    startRestore()
                } label: {
                    Image(systemName: showCompletedCheckmark ? "checkmark.circle.fill" : "circle")
                        .font(.system(size: TodoTimelineMetrics.minimalRowToggleSize, weight: .regular))
                        .foregroundStyle(toggleColor)
                        .frame(
                            width: TodoTimelineMetrics.minimalRowToggleFrame,
                            height: TodoTimelineMetrics.minimalRowToggleFrame
                        )
                }
                .buttonStyle(
                    TdayPressButtonStyle(
                        shadowColor: Color.black,
                        pressedShadowOpacity: 0,
                        normalShadowOpacity: 0
                    )
                )
                .disabled(isRestoring)
                .accessibilityLabel("Undo complete")

                VStack(alignment: .leading, spacing: TodoTimelineMetrics.minimalRowTextSpacing) {
                    TodoTimelineTaskTitle(
                        text: item.title,
                        isCompleted: showStrikethrough,
                        titleColor: titleColor,
                        strikeColor: colors.onSurface.opacity(0.65)
                    )

                    HStack(spacing: 5) {
                        Image(systemName: "clock")
                            .font(.system(size: 10, weight: .bold))
                        Text(completedTimeText)
                            .font(.tdayRounded(size: TodoTimelineMetrics.minimalRowSubtitleSize, weight: .semibold))
                    }
                    .foregroundStyle(colors.onSurfaceVariant.opacity(0.78))
                }

                Spacer(minLength: 0)

                if showListIndicator || priorityIcon != nil {
                    HStack(spacing: 8) {
                        if showListIndicator {
                            Image(systemName: "tray.fill")
                                .font(.system(size: TodoTimelineMetrics.minimalRowIndicatorSize, weight: .semibold))
                                .foregroundStyle(todoListAccentColor(for: item.listColor))
                        }
                        if let priorityIcon {
                            Image(systemName: priorityIcon)
                                .font(.system(size: TodoTimelineMetrics.minimalRowIndicatorSize, weight: .semibold))
                                .foregroundStyle(priorityColor(item.priority))
                        }
                    }
                    .padding(.trailing, TodoTimelineMetrics.minimalRowTrailingIndicatorPadding)
                }
            }
            .padding(.vertical, TodoTimelineMetrics.minimalRowVerticalPadding)
            .contentShape(Rectangle())
        }
        .opacity(isFading ? 0 : 1)
        .scaleEffect(isFading ? 0.985 : 1, anchor: .center)
        .offset(y: isFading ? -10 : 0)
        .animation(TdayMotion.standard(duration: TdayMotion.Durations.change), value: isFading)
        .transition(.opacity.combined(with: .scale(scale: 0.985)))
        .allowsHitTesting(!isRestoring)
        .todoTrailingSwipeActions(
            rowID: item.id,
            openRowID: $openSwipeTaskID,
            enabled: !isRestoring,
            onEdit: onEdit,
            onCopy: onCopy,
            onDelete: {
                Task { await onDelete() }
            }
        )
    }

    private func startRestore() {
        guard restorePhase == .completed else {
            return
        }
        // Any restore clears the slot, not only this row's: the toggle is a `Button` inside the
        // row's content, so it eats the touch and the reveal's own `.onTapGesture` never runs.
        // See `TodoListScreen.completeTodoWithoutReflow` for the full argument.
        openSwipeTaskID = nil

        HapticManager.toggle(on: false)
        // The check-off's own beats, run backwards. This row kept a third set —
        // 180 / 180 — so undoing a completion took a different length of time from
        // making one. The offsets and the rungs are now the ones every task row in
        // every client plays; only the direction differs.
        Task { @MainActor in
            withAnimation(TdayMotion.standard(duration: TdayMotion.Durations.quick)) {
                restorePhase = .unchecked
            }
            try? await Task.sleep(nanoseconds: 160_000_000)
            withAnimation(TdayMotion.standard(duration: TdayMotion.Durations.emphasis)) {
                restorePhase = .unstruck
            }
            try? await Task.sleep(nanoseconds: 360_000_000)
            withAnimation(TdayMotion.standard(duration: TdayMotion.Durations.change)) {
                restorePhase = .fading
            }
            try? await Task.sleep(nanoseconds: 260_000_000)
            await onUncomplete()
        }
    }
}

private func buildCompletedTimelineSections(items: [CompletedItem]) -> [TimelineSection<CompletedItem>] {
    let calendar = Calendar.current
    let grouped = Dictionary(grouping: items) { item in
        calendar.startOfDay(for: item.completedAt ?? item.due ?? .distantPast)
    }

    return grouped.keys.sorted(by: >).map { date in
        let sectionItems = (grouped[date] ?? []).sorted { lhs, rhs in
            let lhsCompletedAt = lhs.completedAt ?? lhs.due ?? .distantPast
            let rhsCompletedAt = rhs.completedAt ?? rhs.due ?? .distantPast
            if lhsCompletedAt != rhsCompletedAt {
                return lhsCompletedAt > rhsCompletedAt
            }
            return lhs.title.localizedCaseInsensitiveCompare(rhs.title) == .orderedAscending
        }

        return TimelineSection(
            id: "completed-\(date.timeIntervalSince1970)",
            title: completedTimelineSectionTitle(for: date),
            items: sectionItems,
            isCollapsible: false
        )
    }
}

private func completedTimelineSectionTitle(for date: Date) -> String {
    CompletedTimelineFormatters.sectionTitle().string(from: date)
}

private enum CompletedTimelineFormatters {
    static func sectionTitle() -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = AppLocale.current
        formatter.setLocalizedDateFormatFromTemplate("EEEE MMM d")
        return formatter
    }
}
