import SwiftUI
import UIKit

/// The id of the list's first row, which the screen scrolls back to when the tab
/// changes. The same shape `TodoListScreen` uses for its own top (`todoTimelineScrollTopID`):
/// a `List` row cannot be named by offset, so the way to say "the top" is to give the
/// first row an id and ask for it.
private let completedTimelineScrollTopID = "completed-timeline-scroll-top"

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
    @Environment(\.tdayAnimation) private var tdayAnimation
    @Environment(\.dismiss) private var dismiss
    @State private var editingItem: CompletedItem?
    @State private var timelineScrollOffset: CGFloat = 0
    @State private var collapsedSectionIDs: Set<String> = []
    /// The screen's single swipe slot — see `TodoListScreen` for the shape and its one rule:
    /// every dismissal is a WRITE to this and nothing else. No host `body` may read it, or a
    /// cheap write becomes a full re-evaluation of the screen.
    @State private var openSwipeTaskID: String?
    /// Which of the history's two tabs is showing.
    ///
    /// The vocabulary is `HomeTileOrigin`, which this client already speaks for exactly this
    /// question — "which board did this arrival come through" — and which the route already
    /// carries, unused for the tab until now. A third enum of `tasks` / `floater` beside it
    /// would be the same idea spelled twice, so there is none.
    @State private var scope: HomeTileOrigin
    @FocusState private var searchFieldFocused: Bool
    @State private var searchExpanded = false
    @State private var searchQuery = ""

    init(
        container: AppContainer,
        pullRefreshEnabled: Bool = false,
        origin: HomeTileOrigin? = nil
    ) {
        self.pullRefreshEnabled = pullRefreshEnabled
        _viewModel = State(initialValue: CompletedViewModel(container: container))
        // Everything that is not the Floater feed opens the first tab, which is web's own
        // rule for `?scope=floater` and the safe polarity: a deep link, a shortcut or a
        // notification names no board and lands on the history rather than nowhere.
        //
        // Read ONCE, into state, and never written back to the route. Pushing a new
        // `.completed(origin:)` on a tab switch would change an `AppRoute`'s `Hashable`
        // payload, which SwiftUI resolves as a different destination — re-running
        // `.navigationTransition` and re-zooming the screen mid-flight.
        _scope = State(initialValue: origin ?? .scheduledBoard)
    }

    /// The active tab's own rows, and nothing else's: this is the whole of the split.
    ///
    /// Each list already comes from its own half of the cache, so nothing here filters on
    /// `isFloater` — a partition of a concatenation would be a second definition of what a
    /// floater is, and the tab is the only thing that decides which list is drawn.
    private var activeItems: [CompletedItem] {
        scope == .floaterFeed ? viewModel.floaterItems : viewModel.completedItems
    }

    private var isFloaterTab: Bool {
        scope == .floaterFeed
    }

    /// Which tab, as an opaque discriminator for the section identity.
    ///
    /// Not a name — there is no third spelling of the tab vocabulary here, and the two
    /// section sets never have to agree on what to call themselves. It exists so a day that
    /// both histories have rows for is not one section id twice: the collapse state is keyed
    /// by section id, so without it a month shut on the Scheduled tab would arrive shut on
    /// the Floater one.
    private var tabDiscriminator: Int {
        isFloaterTab ? 1 : 0
    }

    private var groupedItems: [TimelineSection<CompletedItem>] {
        buildCompletedTimelineSections(items: searchedItems, tabDiscriminator: tabDiscriminator)
    }

    private var normalizedSearchQuery: String {
        searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(with: .current)
    }

    private var isSearching: Bool {
        searchExpanded && !normalizedSearchQuery.isEmpty
    }

    /// The active tab, and nothing else: each web container searches what it is showing, and
    /// this screen searches what its own tab is showing.
    ///
    /// The FLOATER tab has a third term the scheduled one does not — the list's name —
    /// because web's `CompletedFloaterContainer` matches it and `CompletedTodoContainer`
    /// does not. That asymmetry is web's and it is copied rather than tidied.
    private var searchedItems: [CompletedItem] {
        guard isSearching else {
            return activeItems
        }
        let query = normalizedSearchQuery
        return activeItems.filter { item in
            if item.title.lowercased(with: .current).contains(query) {
                return true
            }
            if flattenNotesToPlainText(item.description)
                .lowercased(with: .current)
                .contains(query) {
                return true
            }
            guard isFloaterTab, let listName = item.listName else {
                return false
            }
            return listName.lowercased(with: .current).contains(query)
        }
    }

    private var searchPlaceholder: String {
        L("Search in %@", L("Completed"))
    }

    /// No magnifier over an empty history: there is no set for a query to narrow,
    /// and the button would only raise a keyboard over the empty-state
    /// scene, which is the whole of what the screen has to say. Gates the
    /// button, not the bar — a search already open stays open.
    ///
    /// Read against the ACTIVE TAB, the same per-tab gate web's two containers each write
    /// for themselves: an empty Floater tab gets no magnifier even while the scheduled
    /// history behind it is full.
    private var topBarActions: [TimelineTopBarAction] {
        guard !activeItems.isEmpty else {
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

    /// The completed mark's one accent, shared by both tabs. Both the Scheduled and
    /// the Floater history tint their mark with `.tdayCompletedGreen` — the same
    /// token the dashboard entry tile already uses for both variants (see
    /// `ScheduledTaskHomeScreen`'s `FloaterTaskHomeCompletedCard` and
    /// `TodoListScreen`'s equivalent) — rather than each tab wearing a colour of its
    /// own. This screen used to pick `.tdayFloaterGreen` for the Floater tab, which
    /// put a different green here than the tile the user completed the task from
    /// ever showed; the icon is now what tells the two variants apart, not the
    /// colour.
    ///
    /// It tints the mark and everything the mark is drawn in — the hero's front
    /// glyph, its echo, the page watermark, the empty state's badge — and the tab
    /// strip. Those are the four sites this accent reaches on this page.
    ///
    /// The disc's wash, the hero title and the top bar keep `completedAccentColor`, the
    /// page's slate chrome. The wash is the one of those that touches the mark, and it is
    /// left alone deliberately: here it sits directly above a slate title, and re-tinting it
    /// alone would put a green disc over a slate title where web has the two the same colour.
    private var activeScopeAccent: Color {
        .tdayCompletedGreen
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
                        // Both glyph names are what a watermark drawn as an
                        // asset would use, and are kept in step with
                        // `markContent`; the mark itself is the composite.
                        // `systemName` is already dead on the asset path —
                        // `assetName` wins — and stays only because it is not
                        // optional.
                        systemName: "checkmark",
                        // The tab's accent, not the page's slate. The watermark is
                        // the same mark at another size, and it and the badge were
                        // the two sites still wearing the page's slate while the
                        // hero wore the tab's colour — this page drawing its own
                        // mark in two colours at once. Web draws all three of its
                        // own mark sites from the one accent.
                        accentColor: activeScopeAccent,
                        assetName: "LucideCalendarCheck",
                        // No internal fade to carry any more: the mark itself is
                        // now always drawn fully opaque, and `EmptyTaskWatermark`
                        // is what dims it — its own 0.10 `watermarkColor` fade,
                        // applied uniformly over whatever `markContent` draws.
                        markContent: AnyView(CompletedMark(
                            variant: isFloaterTab ? .floater : .scheduled,
                            size: EmptyTaskWatermark.markGlyphSize
                        ))
                    )
                    if showsCompletedEmptyState {
                        if isSearching {
                            searchEmptyState
                                .transition(completedEmptyStateTransition)
                        } else {
                            TdayEmptyState(
                                // The fallback for a badge drawn as an asset;
                                // `markContent` is what actually draws here.
                                assetName: "LucideCalendarCheck",
                                // The tab's accent, for the watermark's reason above.
                                accentColor: activeScopeAccent,
                                // Two scenes, not one with a swapped word: web keeps a
                                // Floater empty state of its own beside the scheduled
                                // one, because "Tick something off and it will land
                                // here" is only half true on a tab whose way in is the
                                // Floater board rather than the schedule.
                                title: L(
                                    isFloaterTab
                                        ? "No finished floaters yet"
                                        : "No completed tasks"
                                ),
                                description: L(
                                    isFloaterTab
                                        ? "Tick something off in Floater and it will land here."
                                        : "Tick something off and it will land here."
                                ),
                                markContent: AnyView(CompletedMark(
                                    variant: isFloaterTab ? .floater : .scheduled,
                                    size: CompletedMark.badgeGlyphSize,
                                    tint: colors.onPrimary
                                ))
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
            .onChange(of: activeItems.map(\.id)) { _, ids in
                guard let openSwipeTaskID, !ids.contains(openSwipeTaskID) else { return }
                self.openSwipeTaskID = nil
            }
            // A switch lands on a different list, so the field starts empty there — web's
            // own consequence rather than a rule invented here: its two tabs are two
            // independent screens, so the query belongs to the screen the user was on and a
            // freshly mounted one has none. The field itself stays open, so a user who was
            // searching keeps the keyboard and the placeholder.
            .onChange(of: scope) { _, _ in
                searchQuery = ""
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
                    isEditingExistingTask: true,
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

    /// The screen's list, under the reader that can name the row it scrolls back to.
    ///
    /// A tab switch lands on a different list, so the newly-selected one opens at its own
    /// first row rather than at the scroll offset the other tab left behind — web's
    /// `scrollCompletedToTop()` and Android's `listState.scrollToItem(0)`, without which a
    /// Floater tab with a short history opens showing its middle or nothing where web shows
    /// its first row. Deliberately OUTSIDE `withAnimation`: the jump is the cut, so there is
    /// no motion for the app's Reduce Motion switch to have to take back.
    private var completedTimelineContent: some View {
        ScrollViewReader { scrollProxy in
            completedTimelineList
                .onChange(of: scope) { _, _ in
                    scrollProxy.scrollTo(completedTimelineScrollTopID, anchor: .top)
                }
        }
    }

    private var completedTimelineList: some View {
        ZStack {
            List {
                timelineHeroTitleRow
                    .id(completedTimelineScrollTopID)

                completedScopeTabsRow

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
            // The disc's echo is the check alone — `frontMark` says why — so the
            // mark the echo repeats is the check, not the calendar.
            mark: Image("LucideCheck"),
            frontMark: AnyView(CompletedMark(
                variant: isFloaterTab ? .floater : .scheduled,
                size: TodoTimelineMetrics.heroMarkGlyph,
                tint: activeScopeAccent
            )),
            // The echo is a drawing of the mark, so it takes the mark's colour
            // rather than the disc's chrome: left on `markAccentColor` the disc
            // drew the same check twice in two colours — slate behind, green in
            // front — where the web draws both from its one accent.
            markEchoColor: activeScopeAccent
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

    /// The two tabs, directly under the title and inside the block that scrolls away —
    /// web's `NativePageHeader` `beneathTitle` slot, and the position the calendar screen
    /// already puts its own segmented strip in on this client.
    ///
    /// An in-list row rather than anything in `TimelineTopBar`, which has no slot to put
    /// it in: its only content parameters (`searchActive`, `selectionActive`) are whole-row
    /// take-overs rather than insertion points.
    private var completedScopeTabsRow: some View {
        CompletedScopeTabs(
            isFloaterTab: isFloaterTab,
            scheduledCount: viewModel.completedItems.count,
            floaterCount: viewModel.floaterItems.count,
            accentColor: activeScopeAccent,
            onSelect: { next in
                scope = next
            }
        )
        .listRowInsets(EdgeInsets(top: CompletedScopeTabsMetrics.topSpacing, leading: TodoTimelineMetrics.horizontalPadding, bottom: CompletedScopeTabsMetrics.bottomSpacing, trailing: TodoTimelineMetrics.horizontalPadding))
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
            // Both namespaces, so the row's own kind picks the collection: the two
            // stores are disjoint and their ids are prefixed differently, so a
            // completed Floater resolved against the scheduled lists would lose its
            // mark rather than draw the wrong one.
            scheduledLists: viewModel.lists,
            floaterLists: viewModel.floaterLists,
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

/// The completion history's two tabs.
///
/// A hand-rolled SwiftUI strip rather than `TdayNativeSegmentedControl`, and the reason is
/// the motion requirement rather than the look. That control is a `UISegmentedControl`, so
/// its indicator stays on UIKit's own timing — a deviation the dock's pill already carries
/// with a written argument (`AppRootView`) and which the calendar's view-mode strip inherits.
/// This control cannot take it: web's Completed strip runs on the **Enter** rung, argued at
/// its call site ("Enter, not Emphasis, and that is a choice rather than an oversight"), and
/// it has to be a clean cut when the app's own Reduce Motion gate is on. A `UISegmentedControl`
/// can express neither, so this one draws its own thumb and takes both from
/// `tdayAnimation(TdayMotion.enter(duration: TdayMotion.Durations.enter))` — `nil` under the
/// gate, which is the clean cut, and the token's 200 ms and `(0, 0, 0.2, 1)` curve when it plays.
///
/// Both counts are the FULL length of their own history and never the search-narrowed one,
/// which is web's rule too: a tab's number says how much history it holds, not how much of it
/// the current query happens to match.
private struct CompletedScopeTabs: View {
    let isFloaterTab: Bool
    let scheduledCount: Int
    let floaterCount: Int
    let accentColor: Color
    let onSelect: (HomeTileOrigin) -> Void

    @Environment(\.tdayColors) private var colors
    @Environment(\.tdayAnimation) private var tdayAnimation

    private let options: [HomeTileOrigin] = [.scheduledBoard, .floaterFeed]

    private var selectedIndex: Int {
        options.firstIndex(of: isFloaterTab ? .floaterFeed : .scheduledBoard) ?? 0
    }

    var body: some View {
        GeometryReader { proxy in
            let segmentWidth = proxy.size.width / CGFloat(options.count)
            ZStack(alignment: .leading) {
                RoundedRectangle(
                    cornerRadius: CompletedScopeTabsMetrics.trackCorner,
                    style: .continuous
                )
                .fill(colors.surfaceVariant.opacity(0.76))

                RoundedRectangle(
                    cornerRadius: CompletedScopeTabsMetrics.thumbCorner,
                    style: .continuous
                )
                .fill(colors.surface)
                .frame(width: segmentWidth - CompletedScopeTabsMetrics.inset * 2)
                .padding(.vertical, CompletedScopeTabsMetrics.inset)
                .padding(.leading, CompletedScopeTabsMetrics.inset)
                .offset(x: CGFloat(selectedIndex) * segmentWidth)
                .animation(
                    tdayAnimation(TdayMotion.enter(duration: TdayMotion.Durations.enter)),
                    value: selectedIndex
                )

                HStack(spacing: 0) {
                    ForEach(options, id: \.self) { option in
                        let selected = option == (isFloaterTab ? .floaterFeed : .scheduledBoard)
                        Button {
                            guard !selected else { return }
                            HapticManager.selection()
                            onSelect(option)
                        } label: {
                            HStack(spacing: CompletedScopeTabsMetrics.labelBadgeSpacing) {
                                Text(option == .floaterFeed ? L("Floater") : L("Scheduled"))
                                Text(option == .floaterFeed
                                    ? String(floaterCount)
                                    : String(scheduledCount))
                                    .opacity(0.6)
                            }
                            .font(TdayFont.font(size: CompletedScopeTabsMetrics.fontSize, weight: .black))
                            .foregroundStyle(selected ? accentColor : colors.onSurfaceVariant)
                            // The labels' own tint travels on the same rung as the thumb, as it
                            // does on web (`transition-colors duration-enter` beside the thumb's
                            // `transition-transform duration-enter`), and goes instant with it
                            // under the motion gate.
                            .animation(
                                tdayAnimation(TdayMotion.enter(duration: TdayMotion.Durations.enter)),
                                value: selectedIndex
                            )
                            .lineLimit(1)
                            .frame(maxWidth: .infinity)
                            .frame(height: CompletedScopeTabsMetrics.height)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .frame(height: CompletedScopeTabsMetrics.height)
    }
}

private enum CompletedScopeTabsMetrics {
    /// The strip's own height, taken from the app's other segmented control so the two sit
    /// at the same weight in a page.
    static let height: CGFloat = TdayNativeSegmentedControlMetrics.height
    /// From the track's edge to the thumb's, on every side of it.
    static let inset: CGFloat = 5
    static let trackCorner: CGFloat = 16
    static let thumbCorner: CGFloat = 12
    /// The same count size the task lists' own segmented control draws its number at
    /// (`TdaySegmentedSlider`), so the two strips' badges read alike.
    static let fontSize: CGFloat = 13
    static let labelBadgeSpacing: CGFloat = 4
    /// The hero block above already leaves the settled content gap; this is the distance
    /// from the title's block to the control and from the control to the first section.
    static let topSpacing: CGFloat = 4
    static let bottomSpacing: CGFloat = 10
}

/// The Completion-history page's mark: one coherent, fully-opaque icon per tab
/// rather than a shared stack tinted by scope. The Scheduled board draws a bare
/// `calendar-check`; the Floater board draws its leaf with a small checkmark
/// badge over its lower-right — "calendar with checkmark" and "leaf with
/// checkmark", each already legible as itself with nothing faded behind it.
///
/// A view and not an asset, because there is no compositing primitive to reach
/// for: `Image`s in a `ZStack` is the whole thing. Built once — here — and used
/// at all three of the page's own mark sites: the hero disc, the page watermark
/// and the empty state's badge. A composite that reached only the hero would
/// leave the page drawing two different marks.
///
/// This supersedes the previous design, which layered the same three glyphs
/// (calendar-check and leaf, both ghost-faint, with a bold check on top) behind
/// every variant regardless of scope — never actually differing by variant,
/// only the surrounding accent colour did. There is no more "rear vs front"
/// relationship to fade between, so there is no internal opacity knob left to
/// carry: see each call site for why the dimming it used to need is handled
/// elsewhere (or was never needed).
private struct CompletedMark: View {
    /// Which board's mark this is. Drives which glyph(s) are drawn — nothing
    /// else about this view varies by scope.
    enum Variant {
        case scheduled
        case floater
    }

    let variant: Variant
    /// The box the glyph(s) are drawn in.
    let size: CGFloat
    /// Both glyphs' colour — the leaf and its checkmark badge share one tint,
    /// same as the calendar-check does alone. `nil` inherits the environment's
    /// foreground style, which is how the page watermark tints the whole mark
    /// from its own blended colour without a call site re-deriving it.
    var tint: Color? = nil

    /// The badge's glyph box. The badge's circle is 52pt and every other screen
    /// draws its single glyph at 24pt inside it; raised here rather than in
    /// `TdayEmptyState`, so the eight other screens that draw a badge keep the
    /// drawing they have.
    static let badgeGlyphSize: CGFloat = 32

    /// The floater variant's checkmark badge, as a fraction of the box it sits
    /// in, and where its centre lands — 80% across and 80% down the box, i.e.
    /// +0.30 on both axes from the box's centre.
    static let floaterBadgeScale: CGFloat = 0.42
    static let floaterBadgeOffset: CGFloat = 0.30

    var body: some View {
        ZStack {
            switch variant {
            case .scheduled:
                glyph("LucideCalendarCheck", scale: 1)
            case .floater:
                glyph("LucideLeaf", scale: 1)
                glyph(
                    "LucideCircleCheckBig",
                    scale: Self.floaterBadgeScale,
                    offsetX: Self.floaterBadgeOffset,
                    offsetY: Self.floaterBadgeOffset
                )
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }

    @ViewBuilder
    private func glyph(
        _ name: String,
        scale: CGFloat,
        offsetX: CGFloat = 0,
        offsetY: CGFloat = 0
    ) -> some View {
        let image = Image(name)
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .frame(width: size * scale, height: size * scale)
        Group {
            if let tint {
                image.foregroundStyle(tint)
            } else {
                image
            }
        }
        // Displacement from the box's centre as a fraction of the box, so the
        // drawing is the same proportion at every size the mark is drawn.
        .offset(x: size * offsetX, y: size * offsetY)
    }
}

private struct CompletedTimelineRow: View {
    let item: CompletedItem
    /// The two list namespaces the trailing mark resolves against.
    ///
    /// BOTH are handed over rather than the call site choosing one, because the choice
    /// belongs to `item.isFloater` and is answered inside `tdayResolvedRowList` — the
    /// function the unit tests pin. A call site that picked would be the one untested
    /// place a completed Floater's mark could be looked for in the scheduled lists,
    /// find nothing, and silently vanish.
    let scheduledLists: [ListSummary]
    let floaterLists: [ListSummary]
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
        let resolvedList = tdayResolvedRowList(
            for: item,
            scheduledLists: scheduledLists,
            floaterLists: floaterLists
        )
        let showListIndicator = item.listName?.isEmpty == false || resolvedList != nil
        let listIndicatorColor = todoListAccentColor(for: resolvedList?.color ?? item.listColor)
        let priorityIcon = priorityIndicatorSymbolName(item.priority)

        VStack(spacing: 0) {
            // Hung off the title's FIRST baseline, not centred across the column.
            // Completed is the screen the defect was reported on: its titles wrap to
            // two lines and the restore toggle settled in the gap between them,
            // reading as decoration floating beside the task rather than as the
            // task's own mark. The idiom is the task list's, verbatim — see
            // `TodoListScreen.minimalTimelineRow`, which has shipped it since the
            // timeline learned to wrap.
            HStack(alignment: .firstTextBaseline, spacing: TodoTimelineMetrics.minimalRowContentSpacing) {
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
                // A button holds no text, so it reports no text baseline and SwiftUI
                // would align it by its bottom edge — a good half-line low. The guide
                // hands back its centre instead, nudged by the distance from a line's
                // centre to that line's baseline.
                .alignmentGuide(.firstTextBaseline) { dimension in
                    dimension[VerticalAlignment.center] + TodoTimelineMetrics.minimalRowBaselineNudge
                }

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
                            TdayListIcon(
                                iconKey: resolvedList?.iconKey,
                                listName: resolvedList?.name ?? item.listName,
                                size: TodoTimelineMetrics.minimalRowIndicatorSize
                            )
                            .foregroundStyle(listIndicatorColor)
                        }
                        if let priorityIcon {
                            Image(systemName: priorityIcon)
                                .font(.system(size: TodoTimelineMetrics.minimalRowIndicatorSize, weight: .semibold))
                                .foregroundStyle(priorityColor(item.priority))
                        }
                    }
                    .padding(.trailing, TodoTimelineMetrics.minimalRowTrailingIndicatorPadding)
                    // Keep the trailing indicators on the first line too. The flag is
                    // an annotation on the task, so it reads with the title's first
                    // line; leaving it centred while the toggle moved would have been
                    // half a fix, and would have looked like one.
                    .alignmentGuide(.firstTextBaseline) { dimension in
                        dimension[VerticalAlignment.center] + TodoTimelineMetrics.minimalRowBaselineNudge
                    }
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

private func buildCompletedTimelineSections(
    items: [CompletedItem],
    tabDiscriminator: Int
) -> [TimelineSection<CompletedItem>] {
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
            // The tab is part of the identity, and it is load-bearing: the collapse state
            // is keyed by section id, so a day both histories have rows for would be one
            // id twice — a duplicate `ForEach` identity, and a month shut on one tab
            // arriving shut on the other.
            id: "completed-\(tabDiscriminator)-\(date.timeIntervalSince1970)",
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
