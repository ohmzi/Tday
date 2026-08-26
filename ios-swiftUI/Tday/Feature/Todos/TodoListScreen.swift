import SwiftUI
import UIKit
import UniformTypeIdentifiers

private let todoDragContentTypes = [UTType.plainText.identifier, UTType.text.identifier]
private let todoTimelineDragCoordinateSpace = "todoTimelineDragCoordinateSpace"

private struct FloaterSearchResultsFrameKey: PreferenceKey {
    static var defaultValue: CGRect = .zero

    static func reduce(value: inout CGRect, nextValue: () -> CGRect) {
        value = nextValue()
    }
}
private let todoTimelineScrollTopID = "todo-timeline-scroll-top"

private final class TodoTaskDragSession {
    static let shared = TodoTaskDragSession()
    var todo: TodoItem?
    var handledDropSignature: String?

    private init() {}
}

private struct TodoInAppDrag: Equatable {
    let todo: TodoItem
    var location: CGPoint
}

private enum TodoCompletionPhase {
    case checked
    case struck
    case fading
}

private struct TodoDropTargetFrame: Equatable {
    let sectionID: String
    let frame: CGRect
}

private struct TodoDropTargetFramePreferenceKey: PreferenceKey {
    static var defaultValue: [String: TodoDropTargetFrame] = [:]

    static func reduce(value: inout [String: TodoDropTargetFrame], nextValue: () -> [String: TodoDropTargetFrame]) {
        value.merge(nextValue(), uniquingKeysWith: { _, new in new })
    }
}

enum TodoTimelineMetrics {
    static let horizontalPadding: CGFloat = 18
    static let heroTitleSize: CGFloat = 32
    static let sectionTitleSize: CGFloat = 22
    static let sectionChevronSize: CGFloat = 14
    static let sectionSpacing: CGFloat = 10
    static let minimalRowToggleSize: CGFloat = 24
    static let minimalRowToggleFrame: CGFloat = 38
    static let minimalRowTitleSize: CGFloat = 18
    static let minimalRowSubtitleSize: CGFloat = 13
    static let minimalRowIndicatorSize: CGFloat = 14
    static let minimalRowTrailingIndicatorPadding: CGFloat = 24
    static let minimalRowVerticalPadding: CGFloat = 8
    static let sameDateTaskSpacing: CGFloat = 2
    static let sectionTopSpacing: CGFloat = 6
    static let sectionHeaderBottomPadding: CGFloat = 2
    // The expanded title now leads with the screen's glyph in a tinted circle,
    // so the row — and therefore the distance over which it collapses — is
    // taller than the 64pt it was when the row held only the title.
    static let expandedTitleHeight: CGFloat = 56
    static let heroMarkBox: CGFloat = 96
    static let heroMarkGlyph: CGFloat = 44
    static let heroMarkTopGap: CGFloat = 8
    static let heroMarkBottomGap: CGFloat = 18
    /// The mark goes first and is gone before the title reaches the bar.
    static let heroMarkFadeEnd: CGFloat = 0.45
    static let heroMarkWashTopAlpha: Double = 0.24
    static let heroMarkWashBottomAlpha: Double = 0.07
    static let heroMarkEchoAlpha: Double = 0.17
    static let heroMarkEchoGlyph: CGFloat = 108
    /// Band below the bar that dissolves rows as they pass under it.
    static let contentFadeHeight: CGFloat = 30
    /// Clear space kept under the collapsing block, so that when the title has
    /// finished docking the first row comes to rest BELOW the fade band rather
    /// than with its top edge dissolved into it. Deliberately not part of
    /// `titleCollapseDistance`: it moves the content down without changing when
    /// the title arrives, and scrolling on past still fades the row as it goes.
    static let settledContentGap: CGFloat = contentFadeHeight + 6

    static let titleCollapseDistance: CGFloat =
        heroMarkTopGap + heroMarkBox + heroMarkBottomGap + expandedTitleHeight
    static let timelineBottomSpacerHeight: CGFloat = 120
    static let floaterTaskHomeBottomSpacerHeight: CGFloat = 12
    static let rootDockCollapseThreshold: CGFloat = 44
    static let topBarRowHeight: CGFloat = 56
    static let topBarButtonFrame: CGFloat = 56
    static let topBarButtonSpacing: CGFloat = 8
    /// What every circle in a pinned bar draws its glyph at, the root feeds'
    /// buttons included — see `RootFeedHeaderCircleButton`, and Android's 22.dp.
    static let topBarButtonIconSize: CGFloat = 22
    static let expandedTitleLiftDistance: CGFloat = 14
    // The hero title now starts far below the bar, so it holds its ground for
    // most of the collapse and hands over to the bar's copy at a single point:
    // one is at zero exactly where the other starts, so there is never a moment
    // with two titles, nor one with none.
    static let expandedTitleFadeStart: CGFloat = 0.60
    static let expandedTitleFadeEnd: CGFloat = 0.82
    static let collapsedTitleRevealDistance: CGFloat = 10
    static let collapsedTitleRevealStart: CGFloat = 0.82
    static let collapsedTitleRevealEnd: CGFloat = 1
    static let searchResultSectionExpandDelay: TimeInterval = 0.08
    static let searchResultScrollDelay: TimeInterval = 0.44
    static let searchResultScrollDuration: TimeInterval = 0.90
    static let searchResultFlashDelay: TimeInterval = 0.62
    static let searchResultPreScrollItemCount = 5

    /// The leg's own septic smootherstep, borrowed from the root feeds' header
    /// rather than restated: the two header families are meant to feel like one
    /// thing, and this one was still on cubic smoothstep — two orders less flat
    /// at both ends, so its per-page collapse started and stopped harder than
    /// the home screen's. Android reads the same function through
    /// `TdayHeroTitleHeader.staggerRange`; web's is `nativeHeaderEasing.ts`.
    static func progress(_ value: CGFloat, from start: CGFloat, to end: CGFloat) -> CGFloat {
        guard end > start else { return value >= end ? 1 : 0 }
        return RootFeedHeroHeaderMetrics.stagger(value - start, to: end - start)
    }
}

private let todoDropPlaceholderAnimation = Animation.spring(response: 0.28, dampingFraction: 0.88, blendDuration: 0.02)

private func isTodoRootDaytime(_ date: Date) -> Bool {
    let hour = Calendar.current.component(.hour, from: date)
    return (6..<18).contains(hour)
}

private func todoTimeOfDaySystemImage(for date: Date) -> String {
    isTodoRootDaytime(date) ? "sun.max.fill" : "moon.stars.fill"
}

private func todoTimeOfDayIconColor(for date: Date) -> Color {
    todoHexColor(isTodoRootDaytime(date) ? 0xF4C542 : 0xA8B8E8)
}

private func normalizedTodoSearchQuery(_ value: String) -> String {
    value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(with: .current)
}

private func todoSearchText(_ value: String) -> String {
    value.lowercased(with: .current)
}

struct TimelinePinnedSectionHeaderBackground: ViewModifier {
    @Environment(\.tdayColors) private var colors

    func body(content: Content) -> some View {
        content
            .background(colors.background)
            .listRowBackground(colors.background)
            .zIndex(1)
    }
}

extension View {
    func timelinePinnedSectionHeaderBackground() -> some View {
        modifier(TimelinePinnedSectionHeaderBackground())
    }
}

struct TimelineRowDivider: View {
    @Environment(\.tdayColors) private var colors

    var body: some View {
        Rectangle()
            .fill(colors.onSurfaceVariant.opacity(0.18))
            .frame(height: 1)
            .padding(.horizontal, TodoTimelineMetrics.horizontalPadding)
            .listRowInsets(EdgeInsets())
            .listRowBackground(colors.background)
            .listRowSeparator(.hidden)
            .environment(\.defaultMinListRowHeight, 1)
            .allowsHitTesting(false)
    }
}

private struct TimelineTaskFlashHighlight: ViewModifier {
    let active: Bool

    @Environment(\.tdayColors) private var colors
    @State private var strength: CGFloat = 0

    func body(content: Content) -> some View {
        content
            .background(alignment: .leading) {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: [
                                colors.primary.opacity(0.42 * strength),
                                colors.primary.opacity(0.28 * strength),
                                .clear
                            ],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
            }
            .onChange(of: active, initial: true) { _, isActive in
                guard isActive else { return }
                pulse()
            }
    }

    private func pulse() {
        Task { @MainActor in
            strength = 0
            for pulseIndex in 0..<2 {
                withAnimation(.easeInOut(duration: 0.42)) {
                    strength = 0.46
                }
                try? await Task.sleep(nanoseconds: 420_000_000)
                withAnimation(.easeInOut(duration: 0.62)) {
                    strength = 0
                }
                try? await Task.sleep(nanoseconds: pulseIndex == 0 ? 770_000_000 : 620_000_000)
            }
        }
    }
}

struct TodoTimelineTaskTitle: View {
    let text: String
    let isCompleted: Bool
    let titleColor: Color
    let strikeColor: Color
    var font: Font = .tdayRounded(size: TodoTimelineMetrics.minimalRowTitleSize, weight: .bold)
    // nil = no line limit; in-app screens show the full title across as many lines
    // as it needs.
    var lineLimit: Int? = nil

    var body: some View {
        Text(text)
            .font(font)
            .foregroundStyle(titleColor)
            // Real per-line strikethrough so every line of a wrapped title is crossed
            // out, instead of a single rule drawn across the middle of the block.
            .strikethrough(isCompleted, color: strikeColor)
            .lineLimit(lineLimit)
            .animation(.easeInOut(duration: 0.32), value: isCompleted)
    }
}

struct TimelineTopBarAction {
    let systemName: String
    /// Preferred over `systemName` when set, so a bar can use the same drawn
    /// glyph the root feeds do rather than the SF symbol that resembles it.
    let assetName: String?
    let tint: Color?
    let usesCircularChrome: Bool
    /// Worth setting for a drawn glyph above all: an asset button otherwise
    /// reads out as its image name.
    let accessibilityLabel: String?
    let action: () -> Void

    init(
        systemName: String,
        assetName: String? = nil,
        tint: Color? = nil,
        usesCircularChrome: Bool = false,
        accessibilityLabel: String? = nil,
        action: @escaping () -> Void
    ) {
        self.systemName = systemName
        self.assetName = assetName
        self.tint = tint
        self.usesCircularChrome = usesCircularChrome
        self.accessibilityLabel = accessibilityLabel
        self.action = action
    }
}

private struct FloaterTaskHomeSearchResultsCard: View {
    let todos: [TodoItem]
    let listsByID: [String: ListSummary]
    let onOpenTodo: (TodoItem) -> Void

    @Environment(\.tdayColors) private var colors
    private let maxResultsHeight: CGFloat = 320
    private let rowHeight: CGFloat = 66

    private var resultsHeight: CGFloat {
        min(CGFloat(max(todos.count, 1)) * rowHeight, maxResultsHeight)
    }

    var body: some View {
        VStack(spacing: 0) {
            if todos.isEmpty {
                Text("No matching tasks")
                    .font(.tdayRounded(size: 14, weight: .bold))
                    .foregroundStyle(colors.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
            } else {
                ScrollView(showsIndicators: true) {
                    VStack(spacing: 0) {
                        ForEach(todos) { todo in
                            let list = todo.listId.flatMap { listsByID[$0] }
                            HStack(spacing: 10) {
                                TdayListIcon(iconKey: list?.iconKey, size: 17)
                                    .foregroundStyle(todoListAccentColor(for: list?.color).opacity(0.92))
                                    .frame(width: 18)

                                VStack(alignment: .leading, spacing: 3) {
                                    Text(todo.title)
                                        .font(.tdayRounded(size: 15, weight: .bold))
                                        .foregroundStyle(colors.onSurface)
                                        .lineLimit(1)

                                    Text(list?.name ?? TaskPriorityDisplay.label(for: todo.priority))
                                        .font(.tdayRounded(size: 12, weight: .bold))
                                        .foregroundStyle(colors.onSurfaceVariant)
                                        .lineLimit(1)
                                }

                                Spacer(minLength: 0)
                            }
                            .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 9)
                            .contentShape(Rectangle())
                            .onTapGesture {
                                HapticManager.gentleTap()
                                onOpenTodo(todo)
                            }
                        }
                    }
                }
                .frame(height: resultsHeight)
            }
        }
        .background(colors.surface, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(colors.onSurface.opacity(0.2), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.12), radius: 12, x: 0, y: 6)
    }
}

private struct FloaterTaskHomeListCard: View {
    let list: ListSummary
    let count: Int
    let onTap: () -> Void

    @Environment(\.tdayColors) private var colors

    private var symbolName: String {
        todoListSymbolName(for: list.iconKey)
    }

    private var containerColor: Color {
        todoBlendColor(colors.surfaceVariant, todoListAccentColor(for: list.color), amount: 0.66)
    }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 26, style: .continuous)

        Button(action: onTap) {
            ZStack {
                shape.fill(containerColor)
                shape.fill(
                    RadialGradient(
                        colors: [Color.white.opacity(0.22), Color.white.opacity(0.08), .clear],
                        center: .topLeading,
                        startRadius: 8,
                        endRadius: 120
                    )
                )
                shape.fill(
                    LinearGradient(
                        colors: [
                            Color.white.opacity(0.12),
                            Color(red: 231.0 / 255.0, green: 243.0 / 255.0, blue: 255.0 / 255.0).opacity(0.1),
                            Color(red: 255.0 / 255.0, green: 242.0 / 255.0, blue: 250.0 / 255.0).opacity(0.08),
                            .clear,
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )

                TdayListIcon(iconKey: list.iconKey, size: 60)
                    .foregroundStyle(todoBlendColor(containerColor, .white, amount: 0.34).opacity(0.42))
                    .offset(x: 18, y: 8)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
                    .allowsHitTesting(false)

                HStack {
                    HStack(spacing: 10) {
                        TdayListIcon(iconKey: list.iconKey, size: 22)
                            .foregroundStyle(.white)
                            .frame(width: 32, height: 32)

                        Text(list.name)
                            .font(.tdayRounded(size: 22, weight: .bold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                    }

                    Spacer()

                    Text("\(count)")
                        .font(.tdayRounded(size: 22, weight: .bold))
                        .foregroundStyle(.white)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .frame(maxWidth: .infinity, minHeight: 70, maxHeight: 70)
            .clipShape(shape)
            .contentShape(shape)
        }
        .buttonStyle(.plain)
        .shadow(color: .black.opacity(0.14), radius: 10, x: 0, y: 7)
    }
}

struct TodoListScreen: View {
    let highlightedTodoId: String?
    let onListDeleted: () -> Void
    let rootFeedTab: RootFeedTab?
    let onRootFeedTabSelected: ((RootFeedTab) -> Void)?
    let showsRootControls: Bool
    let usesRootFeedHeader: Bool
    let createTaskRequestID: Int
    let openCreateTaskOnAppear: Bool
    let scrollToTopRequestID: Int
    let onRootDockCollapsedChange: (Bool) -> Void
    let onRootControlsVisibleChange: (Bool) -> Void
    let pullRefreshEnabled: Bool
    let summaryAvailable: Bool
    let onOpenFloaterList: (String, String) -> Void
    let onOpenSettings: () -> Void
    @State private var viewModel: TodoListViewModel
    @Environment(\.tdayColors) private var colors
    @Environment(\.dismiss) private var dismiss
    @FocusState private var floaterTaskHomeSearchFieldFocused: Bool
    @FocusState private var listSearchFieldFocused: Bool
    @State private var showingCreateTask = false
    @State private var showingCreateList = false
    @State private var editingTodo: TodoItem?
    @State private var promotingFloater: TodoItem?
    @State private var promoteDue = Date()
    @State private var deferringTodo: TodoItem?
    @State private var showingSummary = false
    @State private var showingListSettings = false
    @State private var showingMembers = false
    @State private var pendingMembersAfterSettings = false
    @State private var showingDeleteListConfirmation = false
    @State private var draggedTodo: TodoItem?
    /// Set at drag start so the scroll reader can keep that row realized; see
    /// `beginInAppDrag`. Consumed and cleared by the reader.
    @State private var dragAnchorTodoID: String?
    @State private var inAppDrag: TodoInAppDrag?
    @State private var activeDropSectionId: String?
    @State private var dropTargetFrames: [String: TodoDropTargetFrame] = [:]
    @State private var pendingRescheduleDrop: TodoRescheduleDrop?
    @State private var collapsedSectionIDs: Set<String>
    @State private var timelineScrollOffset: CGFloat = 0
    @State private var headerScroll = RootFeedHeaderScrollState()
    @State private var rootDockCollapsed = false
    @State private var titleScrollToTopRequestID = 0
    @State private var completionPhases: [String: TodoCompletionPhase] = [:]
    @State private var flashTodoId: String?
    @State private var highlightedScrollRequestID = 0
    @State private var floaterTaskHomeSearchExpanded = false
    @State private var floaterSearchResultsFrame: CGRect = .zero
    @State private var floaterTaskHomeSearchQuery = ""
    @State private var openingFloaterTaskHomeSearchResultID: String?
    @State private var listSearchExpanded = false
    @State private var listSearchQuery = ""
    @State private var openSwipeTaskID: String?
    @State private var hasOpenedCreateTaskOnAppear = false

    init(
        container: AppContainer,
        mode: TodoListMode,
        listId: String?,
        listName: String?,
        highlightedTodoId: String?,
        rootFeedTab: RootFeedTab? = nil,
        onRootFeedTabSelected: ((RootFeedTab) -> Void)? = nil,
        showsRootControls: Bool = true,
        pullRefreshEnabled: Bool = false,
        usesRootFeedHeader: Bool = false,
        createTaskRequestID: Int = 0,
        openCreateTaskOnAppear: Bool = false,
        scrollToTopRequestID: Int = 0,
        onRootDockCollapsedChange: @escaping (Bool) -> Void = { _ in },
        onRootControlsVisibleChange: @escaping (Bool) -> Void = { _ in },
        onOpenFloaterList: @escaping (String, String) -> Void = { _, _ in },
        onOpenSettings: @escaping () -> Void = {},
        summaryAvailable: Bool = true,
        onListDeleted: @escaping () -> Void = {}
    ) {
        self.highlightedTodoId = highlightedTodoId
        self.onListDeleted = onListDeleted
        self.rootFeedTab = rootFeedTab
        self.onRootFeedTabSelected = onRootFeedTabSelected
        self.showsRootControls = showsRootControls
        self.pullRefreshEnabled = pullRefreshEnabled
        self.usesRootFeedHeader = usesRootFeedHeader
        self.createTaskRequestID = createTaskRequestID
        self.openCreateTaskOnAppear = openCreateTaskOnAppear
        self.scrollToTopRequestID = scrollToTopRequestID
        self.onRootDockCollapsedChange = onRootDockCollapsedChange
        self.onRootControlsVisibleChange = onRootControlsVisibleChange
        self.onOpenFloaterList = onOpenFloaterList
        self.onOpenSettings = onOpenSettings
        self.summaryAvailable = summaryAvailable
        _viewModel = State(initialValue: TodoListViewModel(container: container, mode: mode, listId: listId, listName: listName))
        _collapsedSectionIDs = State(initialValue: mode == .priority || mode == .all || mode == .list ? ["earlier"] : [])
    }

    /// An empty date bucket is scaffolding, not content: at rest a list holding
    /// three overdue tasks drew one section of rows and twelve bare headers under
    /// it. The buckets are drag-to-reschedule drop targets, though, so the whole
    /// scaffold comes back for the length of a drag and there is still somewhere
    /// to drop a task into an empty day. `draggedTodo` holds still for the whole
    /// gesture, so the target set cannot change shape under the finger.
    private var groupedSections: [TodoTimelineSection] {
        buildSections(
            items: timelineItems,
            mode: viewModel.mode,
            showsEmptyDropTargets: draggedTodo != nil
        )
    }

    /// What the timeline is built from. Only a live list search narrows it — the
    /// query filters the tasks and the sections are rebuilt from the survivors,
    /// exactly as web rebuilds its timeline from the filtered todos.
    private var timelineItems: [TodoItem] {
        guard isSearchingList else {
            return viewModel.items
        }
        return viewModel.items.filter { todo in
            todoSearchText(todo.title).contains(normalizedListSearchQuery) ||
                todoSearchText(flattenNotesToPlainText(todo.description)).contains(normalizedListSearchQuery)
        }
    }

    private var floaterTaskHomeListRows: [(list: ListSummary, count: Int)] {
        guard viewModel.mode == .floater, viewModel.listId == nil else {
            return []
        }
        let counts = Dictionary(grouping: viewModel.items.compactMap(\.listId), by: { $0 }).mapValues(\.count)
        // Show every list, including ones with no tasks yet, so a newly created
        // (still-empty) list is always reachable here.
        return viewModel.lists.map { list in
            (list: list, count: counts[list.id] ?? 0)
        }
    }

    private var isFloaterTaskHomeScreen: Bool {
        viewModel.mode == .floater && viewModel.listId == nil
    }

    // Root floater empty state is shown inline (in the list, above the list
    // names) to mirror the web layout, rather than as a full-screen overlay.
    private var showInlineFloaterTaskHomeEmpty: Bool {
        isFloaterTaskHomeScreen && viewModel.items.isEmpty && !viewModel.isLoading
    }

    private var inlineEmptyStateGapHeight: CGFloat {
        UIScreen.main.bounds.height * 0.42
    }

    private var isListDetailScreen: Bool {
        viewModel.mode == .list ||
            (viewModel.mode == .floater && !(viewModel.listId?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?? true))
    }

    private var selectedListSummary: ListSummary? {
        guard let listId = viewModel.listId else { return nil }
        return viewModel.lists.first(where: { $0.id == listId })
    }

    // VIEWER members of a shared list get a read-only screen: no create FAB,
    // no swipe edit/delete, no complete taps, no drag.
    private var isViewerList: Bool {
        isListDetailScreen && selectedListSummary?.isViewer == true
    }

    private var currentShareKind: ShareListKind {
        viewModel.mode == .floater ? .floater : .scheduled
    }

    private var floaterTaskHomeListByID: [String: ListSummary] {
        Dictionary(viewModel.lists.map { ($0.id, $0) }, uniquingKeysWith: { _, latest in latest })
    }

    private var normalizedFloaterTaskHomeSearchQuery: String {
        normalizedTodoSearchQuery(floaterTaskHomeSearchQuery)
    }

    private var floaterTaskHomeSearchResults: [TodoItem] {
        guard isFloaterTaskHomeScreen, !normalizedFloaterTaskHomeSearchQuery.isEmpty else {
            return []
        }

        return viewModel.items.filter { todo in
            todoSearchText(todo.title).contains(normalizedFloaterTaskHomeSearchQuery) ||
                todoSearchText(flattenNotesToPlainText(todo.description)).contains(normalizedFloaterTaskHomeSearchQuery) ||
                (todo.listId.flatMap { floaterTaskHomeListByID[$0]?.name }.map {
                    todoSearchText($0).contains(normalizedFloaterTaskHomeSearchQuery)
                } ?? false)
        }
        .sorted(by: floaterTodoSortPrecedes)
        .prefix(20)
        .map { $0 }
    }

    private var showFloaterTaskHomeSearchResults: Bool {
        isFloaterTaskHomeScreen && floaterTaskHomeSearchExpanded && !normalizedFloaterTaskHomeSearchQuery.isEmpty
    }

    /// Every screen that pins the timeline bar searches its own tasks from it —
    /// the five scopes as well as a custom list, the way the matching web pages
    /// do. The root floater feed has its own field in the hero header and never
    /// takes this one.
    private var showsListSearch: Bool {
        showsTimelineNavigationTopBar && !isFloaterTaskHomeScreen
    }

    private var normalizedListSearchQuery: String {
        normalizedTodoSearchQuery(listSearchQuery)
    }

    private var isSearchingList: Bool {
        showsListSearch && !normalizedListSearchQuery.isEmpty
    }

    private var listSearchPlaceholder: String {
        let name = (selectedListSummary?.name ?? viewModel.title)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return name.isEmpty ? L("Search") : L("Search in %@", name)
    }

    private var showsListSearchEmptyState: Bool {
        isSearchingList && timelineItems.isEmpty && !viewModel.isLoading
    }

    private var isTodayMode: Bool {
        viewModel.mode == .today
    }

    private var isMinimalTimelineMode: Bool {
        viewModel.mode == .overdue ||
            viewModel.mode == .scheduled ||
            viewModel.mode == .priority ||
            viewModel.mode == .floater ||
            viewModel.mode == .all ||
            viewModel.mode == .list
    }

    private var usesHeroTimelineMode: Bool {
        isTodayMode || isMinimalTimelineMode
    }

    private var showsTimelineNavigationTopBar: Bool {
        usesHeroTimelineMode && !usesRootFeedHeader
    }

    private var modeAccentColor: Color {
        todoModeAccentColor(viewModel.mode, listColorKey: viewModel.lists.first(where: { $0.id == viewModel.listId })?.color)
    }

    private func emptyWatermarkSystemName(for date: Date) -> String {
        emptyTimelineSystemImage(
            for: viewModel.mode,
            listIconKey: viewModel.lists.first(where: { $0.id == viewModel.listId })?.iconKey,
            date: date
        )
    }

    private var emptyStateAssetName: String {
        emptyTimelineBadgeAssetName(
            for: viewModel.mode,
            listIconKey: viewModel.lists.first(where: { $0.id == viewModel.listId })?.iconKey
        )
    }

    private var titleCollapseProgress: CGFloat {
        let distance = TodoTimelineMetrics.titleCollapseDistance
        guard distance > 0 else { return 0 }
        return min(max(timelineScrollOffset / distance, 0), 1)
    }

    private var shouldCollapseRootDock: Bool {
        // Root feed offsets live in the header model so a scroll frame does not
        // invalidate this screen's body; other modes still use @State.
        usesRootFeedHeader
            ? rootDockCollapsed
            : max(timelineScrollOffset, 0) > TodoTimelineMetrics.rootDockCollapseThreshold
    }

    private var minimalTimelineBottomSpacerHeight: CGFloat {
        isFloaterTaskHomeScreen ? TodoTimelineMetrics.floaterTaskHomeBottomSpacerHeight : TodoTimelineMetrics.timelineBottomSpacerHeight
    }

    private var timelineItemAnimationKey: String {
        let itemIDs = viewModel.items.map(\.id).joined(separator: "|")
        let completingIDs = completionPhases.keys.sorted().joined(separator: "|")
        return "\(itemIDs)::\(completingIDs)"
    }

    private var canSummarizeCurrentMode: Bool {
        // Summary button is intentionally hidden on the root floater screen; it
        // remains available on the per-mode screens (today/all/scheduled/etc.)
        // and list detail.
        summaryAvailable && viewModel.aiSummaryEnabled && !viewModel.items.isEmpty && !isFloaterTaskHomeScreen
    }

    private var heroTopBarActions: [TimelineTopBarAction] {
        var actions: [TimelineTopBarAction] = []

        // Leads the cluster, as it does on the web list bar.
        if showsListSearch {
            actions.append(TimelineTopBarAction(
                systemName: "magnifyingglass",
                assetName: "NavSearch",
                usesCircularChrome: true,
                accessibilityLabel: L("Search"),
                action: openListSearch
            ))
        }

        if canSummarizeCurrentMode {
            actions.append(TimelineTopBarAction(
                systemName: "sparkles",
                assetName: "LucideSparkles",
                usesCircularChrome: true,
                accessibilityLabel: L("Summary"),
                action: presentSummary
            ))
        }

        if isListDetailScreen {
            // One entry point per role: owners get list settings (which hosts
            // the Sharing section); members go straight to the members sheet.
            actions.append(TimelineTopBarAction(
                systemName: "ellipsis",
                assetName: "NavEllipsis",
                usesCircularChrome: true,
                accessibilityLabel: L("More"),
                action: {
                    if selectedListSummary?.isOwner == false {
                        showingMembers = true
                    } else {
                        showingListSettings = true
                    }
                }
            ))
        }

        return actions
    }

    private var modeContent: AnyView {
        if isTodayMode {
            // Today reuses the drop-capable minimal-timeline content so tasks can
            // be dragged between the Morning / Afternoon / Tonight buckets.
            return AnyView(minimalTimelineModeContent)
        }
        if isMinimalTimelineMode {
            return AnyView(minimalTimelineModeContent)
        }
        return AnyView(standardModeContent)
    }

    var body: some View {
        refreshableModeContent
        .coordinateSpace(name: todoTimelineDragCoordinateSpace)
        .background(colors.background)
        .onPreferenceChange(FloaterSearchResultsFrameKey.self) { frame in
            floaterSearchResultsFrame = frame
        }
        // A tap on the blank space the feed gave up dismisses the field. The
        // toolbar row is tested by its fixed geometry, not a reported rect, so
        // the tap that opened the field can never read as an outside tap.
        .simultaneousGesture(
            DragGesture(minimumDistance: 0, coordinateSpace: .named(todoTimelineDragCoordinateSpace))
                .onEnded { value in
                    guard usesRootFeedHeader, floaterTaskHomeSearchExpanded else { return }
                    let isTap = abs(value.translation.width) < 8 && abs(value.translation.height) < 8
                    guard isTap else { return }
                    let location = value.startLocation
                    guard location.y > RootFeedHeroHeaderMetrics.barHeight else { return }
                    guard !floaterSearchResultsFrame.contains(location) else { return }
                    closeFloaterTaskHomeSearch()
                }
        )
        .overlay(alignment: .top) {
            if usesRootFeedHeader {
                rootFeedHeroHeader
            }
        }
        .onPreferenceChange(TodoDropTargetFramePreferenceKey.self) { frames in
            dropTargetFrames = frames
        }
        .overlay(alignment: .topLeading) {
            GeometryReader { proxy in
                if let inAppDrag {
                    let rootFrame = proxy.frame(in: .global)
                    let previewLocation = CGPoint(
                        x: inAppDrag.location.x - rootFrame.minX,
                        y: inAppDrag.location.y - rootFrame.minY
                    )
                    TodoDragPreview(todo: inAppDrag.todo)
                        .position(x: previewLocation.x, y: previewLocation.y)
                        .zIndex(20)
                        .allowsHitTesting(false)
                }
            }
            .allowsHitTesting(false)
        }
        .overlay {
            if showingDeleteListConfirmation {
                ListDeleteConfirmationOverlay(
                    onCancel: {
                        withAnimation(.spring(response: 0.24, dampingFraction: 0.9)) {
                            showingDeleteListConfirmation = false
                        }
                    },
                    onDelete: {
                        showingDeleteListConfirmation = false
                        Task {
                            // Navigate from the screen after the delete completes,
                            // so navigation is not dropped while the confirmation
                            // overlay is still animating its dismissal.
                            if await viewModel.deleteList() {
                                onListDeleted()
                            }
                        }
                    }
                )
                .transition(.opacity.combined(with: .scale(scale: 0.96)))
                .zIndex(30)
            }
        }
        .animation(.spring(response: 0.24, dampingFraction: 0.9), value: showingDeleteListConfirmation)
        .navigationBackButtonBehavior()
        .navigationTitleTypography(
            largeTitleColor: modeAccentColor,
            inlineTitleColor: colors.onSurface,
            backgroundColor: colors.background
        )
        .navigationTitle((usesRootFeedHeader || usesHeroTimelineMode) ? "" : viewModel.title)
        .navigationBarBackButtonHidden(usesRootFeedHeader)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar((usesRootFeedHeader || usesHeroTimelineMode) ? .hidden : .visible, for: .navigationBar)
        .toolbar {
            navigationToolbarContent
        }
        .safeAreaInset(edge: .top, spacing: 0) {
            timelineTopInset
        }
        .onChange(of: viewModel.items) {
            handleItemsChanged()
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if showsRootControls {
                floatingActionButtonDock
            } else {
                Color.clear.frame(height: 80)
            }
        }
        .onChange(of: timelineScrollOffset, initial: true) { _, offset in
            guard !usesRootFeedHeader else { return }
            onRootDockCollapsedChange(max(offset, 0) > TodoTimelineMetrics.rootDockCollapseThreshold)
        }
        .onChange(of: floaterTaskHomeSearchExpanded, initial: true) { _, expanded in
            guard isFloaterTaskHomeScreen else {
                onRootControlsVisibleChange(true)
                return
            }
            onRootControlsVisibleChange(!expanded)
            if expanded {
                floaterTaskHomeSearchFieldFocused = false
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.30) {
                    if floaterTaskHomeSearchExpanded {
                        floaterTaskHomeSearchFieldFocused = true
                    }
                }
            } else {
                floaterTaskHomeSearchFieldFocused = false
            }
        }
        // The field only joins the hierarchy once the bar has swapped its row
        // over, so focusing it in the same turn is dropped on the floor.
        .onChange(of: listSearchExpanded) { _, expanded in
            guard expanded else {
                listSearchFieldFocused = false
                return
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.30) {
                if listSearchExpanded {
                    listSearchFieldFocused = true
                }
            }
        }
        .onChange(of: createTaskRequestID) { _, requestID in
            guard requestID > 0 else { return }
            closeFloaterTaskHomeSearch()
            showingCreateTask = true
        }
        .onAppear {
            onRootControlsVisibleChange(!(isFloaterTaskHomeScreen && floaterTaskHomeSearchExpanded))
            onRootDockCollapsedChange(shouldCollapseRootDock)
            if openCreateTaskOnAppear && !hasOpenedCreateTaskOnAppear {
                hasOpenedCreateTaskOnAppear = true
                closeFloaterTaskHomeSearch()
                showingCreateTask = true
            }
        }
        .onDisappear {
            onRootControlsVisibleChange(true)
        }
        .createTaskSheet(isPresented: $showingCreateTask) {
            createTaskSheetContent
        }
        .tdayBottomSheetPresentation(isPresented: $showingCreateList) {
            CreateListSheet { name, color, iconKey in
                Task {
                    await viewModel.createList(name: name, color: color, iconKey: iconKey)
                }
            }
        }
        .createTaskSheet(item: $editingTodo) { todo in
            editTaskSheetContent(for: todo)
        }
        .sheet(item: $promotingFloater) { floater in
            promoteFloaterSheetContent(for: floater)
        }
        // Quick Defer: one tap moves the task to a locally computed instant.
        .confirmationDialog(
            L("Defer"),
            isPresented: Binding(
                get: { deferringTodo != nil },
                set: { if !$0 { deferringTodo = nil } }
            ),
            titleVisibility: .visible
        ) {
            if let todo = deferringTodo {
                ForEach(Array(quickDeferOptions().enumerated()), id: \.offset) { entry in
                    Button(entry.element.choice.label) {
                        deferringTodo = nil
                        Task { await viewModel.deferTask(todo, due: entry.element.due) }
                    }
                }
            }
        }
        .sheet(isPresented: $showingSummary) {
            summarySheetContent
        }
        .sheet(isPresented: $showingListSettings, onDismiss: {
            // Sheet swap requested from the settings sheet's Sharing section:
            // present members only after settings has fully dismissed.
            if pendingMembersAfterSettings {
                pendingMembersAfterSettings = false
                showingMembers = true
            }
        }) {
            listSettingsSheetContent
        }
        .sheet(isPresented: $showingMembers) {
            membersSheetContent
        }
        .confirmationDialog(
            "Move repeating task?",
            isPresented: Binding(
                get: { pendingRescheduleDrop != nil },
                set: { isPresented in
                    if !isPresented {
                        pendingRescheduleDrop = nil
                    }
                }
            ),
            titleVisibility: .visible
        ) {
            Button("This occurrence") {
                commitPendingReschedule(scope: .occurrence)
            }
            Button("Entire series") {
                commitPendingReschedule(scope: .series)
            }
            Button("Cancel", role: .cancel) {
                pendingRescheduleDrop = nil
            }
        } message: {
            Text("Choose whether to move only this task occurrence or the entire repeating series.")
        }
    }

    @ViewBuilder
    private var refreshableModeContent: some View {
        if isFloaterTaskHomeScreen {
            PullToRefreshContainer(
                isRefreshing: viewModel.isLoading,
                isEnabled: pullRefreshEnabled,
                // The header draws the pill itself, so it can fly in from the
                // top of the screen and hover in front of the title instead of
                // being painted underneath the pinned toolbar.
                showsIndicator: false,
                onIndicatorStateChange: { headerScroll.refresh = $0 },
                action: {
                    await viewModel.refresh(userInitiated: true)
                }
            ) {
                watermarkedModeContent
            }
        } else {
            watermarkedModeContent
                .tdayPullToRefresh(isRefreshing: viewModel.isLoading, isEnabled: pullRefreshEnabled) {
                    await viewModel.refresh(userInitiated: true)
                }
        }
    }

    // The watermark is overlaid on `modeContent` *before* the pull-to-refresh
    // wrapper is applied, so the loading/refresh pill (added as an overlay by the
    // pull-to-refresh container on top of this content) renders in front of the
    // watermark rather than behind it.
    private var watermarkedModeContent: some View {
        modeContent
            .overlay {
                TimelineView(.periodic(from: .now, by: 60)) { context in
                    ZStack {
                        EmptyTaskWatermark(
                            systemName: emptyWatermarkSystemName(for: context.date),
                            accentColor: modeAccentColor,
                            assetName: emptyTimelineAssetName(
                                for: viewModel.mode,
                                listIconKey: viewModel.lists.first(where: { $0.id == viewModel.listId })?.iconKey
                            )
                        )
                        // A live query answers for itself in the feed, so the
                        // screen's own "nothing here" line stands down rather
                        // than talking over the no-results state.
                        if viewModel.items.isEmpty, !viewModel.isLoading, !isFloaterTaskHomeScreen, !isSearchingList {
                            // Day Done: "finished everything" is a payoff, not an
                            // absence, so it keeps its own glyph and its date line
                            // rather than the screen's — which would undersell it.
                            if viewModel.mode == .today, viewModel.completedTodayCount > 0 {
                                TdayEmptyState(
                                    assetName: "LucideCheckCheck",
                                    accentColor: modeAccentColor,
                                    title: L("All done for today"),
                                    description: context.date.formatted(.dateTime.weekday(.wide).day().month(.wide))
                                )
                                .onAppear { HapticManager.taskCompleted(); SoundManager.taskCompleted() }
                            } else {
                                TdayEmptyState(
                                    assetName: emptyStateAssetName,
                                    accentColor: modeAccentColor,
                                    title: emptyTimelineTitle(for: viewModel.mode, isListDetail: isListDetailScreen),
                                    description: emptyTimelineDescription(for: viewModel.mode, isListDetail: isListDetailScreen)
                                )
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .allowsHitTesting(false)
                }
            }
    }

    @ToolbarContentBuilder
    private var navigationToolbarContent: some ToolbarContent {
        if !usesHeroTimelineMode {
            // Morning Sweep: guided triage entry, only where there is something
            // to triage (recurring occurrences reschedule via the edit flow).
            if viewModel.mode == .overdue,
               viewModel.items.contains(where: { !$0.isRecurring && $0.instanceDate == nil }) {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        NotificationDeepLinkRouter.shared.route(URL(string: "tday://morning-sweep")!)
                    } label: {
                        Image(systemName: "sunrise")
                    }
                    .accessibilityLabel(Text(L("Sweep")))
                }
            }
            if canSummarizeCurrentMode {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: presentSummary) {
                        Image(systemName: "sparkles")
                    }
                }
            }
            if isListDetailScreen {
                // One entry point per role: owners get list settings (which
                // hosts the Sharing section); members the members sheet.
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        if selectedListSummary?.isOwner == false {
                            showingMembers = true
                        } else {
                            showingListSettings = true
                        }
                    } label: {
                        Image(systemName: "slider.horizontal.3")
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var timelineTopInset: some View {
        if showsTimelineNavigationTopBar {
            TimelineTopBar(
                title: viewModel.title,
                accentColor: modeAccentColor,
                collapseProgress: titleCollapseProgress,
                onBack: { dismiss() },
                actions: heroTopBarActions,
                showsTimeOfDayIcon: viewModel.mode == .today,
                searchActive: showsListSearch && listSearchExpanded,
                searchText: $listSearchQuery,
                searchPlaceholder: listSearchPlaceholder,
                searchFieldFocused: $listSearchFieldFocused,
                onSearchClose: closeListSearch
            )
        }
    }

    private var timelineHeroTitleCollapseProgress: CGFloat {
        usesRootFeedHeader ? 0 : titleCollapseProgress
    }

    /// The screen's own glyph for the hero circle. Prefers the drawn tile art
    /// each mode already owns and falls back to the symbol — which is what
    /// Today wants anyway, since its glyph follows the time of day.
    private var timelineHeroMark: Image {
        let listIconKey = viewModel.lists.first(where: { $0.id == viewModel.listId })?.iconKey
        if let asset = emptyTimelineAssetName(for: viewModel.mode, listIconKey: listIconKey) {
            return Image(asset)
        }
        return Image(systemName: emptyTimelineSystemImage(for: viewModel.mode, listIconKey: listIconKey))
    }

    private var timelineHeroTitleRowBase: some View {
        TimelineExpandedTitleRow(
            title: viewModel.title,
            accentColor: modeAccentColor,
            collapseProgress: timelineHeroTitleCollapseProgress,
            showsTimeOfDayIcon: viewModel.mode == .today,
            mark: timelineHeroMark
        )
        .background {
            TimelineScrollOffsetObserver { timelineScrollOffset = $0 }
                .frame(width: 0, height: 0)
        }
        .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
        .listRowBackground(colors.background)
        .listRowSeparator(.hidden)
    }

    // Reserves the pinned hero header's space at the top of the root feed.
    // Rows scroll behind the header, which folds down into its always-visible
    // toolbar strip as this spacer scrolls away.
    private var rootFeedHeaderSpacerRow: some View {
        Color.clear
            .frame(height: RootFeedHeroHeaderMetrics.expandedHeight)
            .background {
                RootFeedHeaderScrollObserver(
                    state: headerScroll,
                    collapseThreshold: TodoTimelineMetrics.rootDockCollapseThreshold
                ) { collapsed in
                    guard rootDockCollapsed != collapsed else { return }
                    rootDockCollapsed = collapsed
                    onRootDockCollapsedChange(collapsed)
                }
                .frame(width: 0, height: 0)
            }
            .allowsHitTesting(false)
            .onTopPartialScrollSnap(
                anchorDistance: RootFeedHeroHeaderMetrics.collapseDistance,
                isDisabled: floaterTaskHomeSearchExpanded
            )
            .listRowInsets(EdgeInsets())
            .listRowBackground(colors.background)
            .listRowSeparator(.hidden)
    }

    private var rootFeedHeroHeader: some View {
        RootFeedHeroHeader(
            title: viewModel.title,
            searchPlaceholder: L("Search Floater Task"),
            searchPlaceholderShort: L("Search"),
            mark: viewModel.mode == .floater ? .floaterLeaf : .timeOfDay,
            scroll: headerScroll,
            coordinateSpaceName: todoTimelineDragCoordinateSpace,
            searchExpanded: $floaterTaskHomeSearchExpanded,
            searchQuery: $floaterTaskHomeSearchQuery,
            searchFieldFocused: $floaterTaskHomeSearchFieldFocused,
            onSearchClose: {
                closeFloaterTaskHomeSearch()
            },
            onCreateList: {
                closeFloaterTaskHomeSearch()
                showingCreateList = true
            },
            onOpenSettings: {
                closeFloaterTaskHomeSearch()
                onOpenSettings()
            },
            onScrollToTop: {
                titleScrollToTopRequestID += 1
            }
        )
    }

    @ViewBuilder
    private var timelineHeroTitleRow: some View {
        if usesRootFeedHeader {
            rootFeedHeaderSpacerRow
        } else {
            timelineHeroTitleRowBase
                .onVerticalScrollSnap(collapseDistance: TodoTimelineMetrics.titleCollapseDistance)
        }
    }

    private var floatingActionButtonDock: some View {
        HStack(alignment: .bottom) {
            if showsRootControls, let rootFeedTab, let onRootFeedTabSelected {
                RootFeedDock(
                    activeTab: rootFeedTab,
                    collapsed: shouldCollapseRootDock,
                    accentColor: rootFeedTab == .floaterTaskHome ? .tdayFloaterGreen : .tdayTodayBlue,
                    onSelect: onRootFeedTabSelected
                )
                .padding(.leading, 18)
                .padding(.vertical, 8)
            }

            Spacer(minLength: 12)

            if !isViewerList {
                TaskFloatingActionButton(fillColor: modeAccentColor) {
                    HapticManager.buttonTap()
                    showingCreateTask = true
                }
                .padding(.trailing, 18)
                .padding(.vertical, 8)
            }
        }
    }

    private var createTaskSheetContent: some View {
        CreateTaskSheet(
            lists: viewModel.lists,
            titleText: L("New task"),
            submitText: L("Create"),
            initialPayload: CreateTaskPayload(title: "", description: nil, priority: viewModel.mode == .priority ? TaskPriorityDisplay.importantValue : TaskPriorityDisplay.normalValue, due: viewModel.mode == .floater ? nil : Date().addingTimeInterval(60 * 60), rrule: nil, listId: viewModel.listId),
            defaultScheduled: viewModel.mode != .floater,
            showScheduleControls: viewModel.mode != .floater,
            onParseTaskTitleNlp: viewModel.mode == .floater ? nil : { title, dueRef in
                await viewModel.parseTaskTitleNlp(text: title, referenceDueEpochMs: dueRef)
            },
            onDismiss: {
                showingCreateTask = false
            },
            onSubmit: { payload in
                await viewModel.addTask(payload)
            }
        )
    }

    private func editTaskSheetContent(for todo: TodoItem) -> some View {
        CreateTaskSheet(
            lists: viewModel.lists,
            titleText: L("Edit task"),
            submitText: L("Save"),
            initialPayload: CreateTaskPayload(title: todo.title, description: todo.description, priority: todo.priority, due: todo.due, rrule: todo.rrule, listId: todo.listId),
            defaultScheduled: viewModel.mode != .floater,
            showScheduleControls: viewModel.mode != .floater,
            onParseTaskTitleNlp: viewModel.mode == .floater ? nil : { title, dueRef in
                await viewModel.parseTaskTitleNlp(text: title, referenceDueEpochMs: dueRef)
            },
            onDismiss: { editingTodo = nil },
            onSubmit: { payload in
                await viewModel.updateTask(todo, payload: payload)
            }
        )
    }

    /// The mode-specific third swipe action: floaters get "Schedule" (promote
    /// into a dated task), overdue non-recurring todos get "Float" (demote to
    /// Anytime), other dated non-recurring rows get "Defer" (Quick Defer).
    private func promoteOrFloatSwipeAction(for todo: TodoItem) -> TodoSwipeExtraAction? {
        switch viewModel.mode {
        case .floater:
            return TodoSwipeExtraAction(
                title: L("Schedule"),
                assetName: "LucideCalendarClock",
                tint: TaskSwipeActionTint.schedule
            ) {
                promoteDue = defaultPromoteDue()
                promotingFloater = todo
            }
        case .overdue where !todo.isRecurring:
            return TodoSwipeExtraAction(
                title: L("Float"),
                assetName: "LucideWaves",
                tint: TaskSwipeActionTint.float
            ) {
                Task { await viewModel.demoteTodo(todo) }
            }
        case .today, .scheduled, .priority, .list:
            // Recurring todos defer per-occurrence via the edit sheet instead.
            guard !todo.isRecurring else { return nil }
            return TodoSwipeExtraAction(
                title: L("Defer"),
                assetName: "LucideAlarmClock",
                tint: TaskSwipeActionTint.schedule
            ) {
                deferringTodo = todo
            }
        default:
            return nil
        }
    }

    /// Tomorrow 09:00 — a sane starting point for scheduling a floater.
    private func defaultPromoteDue() -> Date {
        let calendar = Calendar.current
        let tomorrow = calendar.date(byAdding: .day, value: 1, to: Date()) ?? Date()
        return calendar.date(bySettingHour: 9, minute: 0, second: 0, of: tomorrow) ?? tomorrow
    }

    private func promoteFloaterSheetContent(for floater: TodoItem) -> some View {
        VStack(spacing: 0) {
            TdaySheetHeader(
                title: L("Schedule"),
                closeAccessibilityLabel: L("Close"),
                onClose: { promotingFloater = nil }
            )

            DatePicker(
                "",
                selection: $promoteDue,
                displayedComponents: [.date, .hourAndMinute]
            )
            .datePickerStyle(.graphical)
            .padding(.horizontal, 18)

            Button {
                let due = promoteDue
                promotingFloater = nil
                Task { await viewModel.promoteFloater(floater, due: due) }
            } label: {
                Text(L("Schedule"))
                    .font(.tdayRounded(size: 17, weight: .heavy))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
            }
            .buttonStyle(.borderedProminent)
            .padding(.horizontal, 18)
            .padding(.bottom, 18)
        }
        .presentationDetents([.medium, .large])
    }

    private var summarySheetContent: some View {
        VStack(spacing: 0) {
            TdaySheetHeader(
                title: L("Summary"),
                closeAccessibilityLabel: L("Close"),
                confirmSystemName: nil,
                onClose: { showingSummary = false }
            )

            ScrollView {
                TdaySheetCard {
                    VStack(alignment: .leading, spacing: 12) {
                        if viewModel.isSummarizing {
                            ProgressView()
                        } else if let summaryText = viewModel.summaryText {
                            Text(summaryText)
                                .font(.tdayRounded(.body, weight: .bold))
                                .frame(maxWidth: .infinity, alignment: .leading)
                        } else if viewModel.summaryConnectivityError {
                            ErrorRetryView(message: L("Summary needs a network connection.")) {
                                Task { await viewModel.summarizeCurrentMode() }
                            }
                        } else if let summaryError = viewModel.summaryError {
                            Text(summaryError)
                                .foregroundStyle(colors.error)
                        } else {
                            Text("No summary available.")
                        }
                    }
                    .padding(18)
                }
                .padding(.horizontal, 18)
                .padding(.top, 14)
                .padding(.bottom, 24)
            }
            .disableVerticalScrollBounce()
        }
        .background(colors.bottomSheetBackground.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.hidden)
        .presentationCornerRadius(34)
        .presentationBackground {
            colors.bottomSheetBackground
                .ignoresSafeArea(.container, edges: .bottom)
        }
    }

    @ViewBuilder
    private var membersSheetContent: some View {
        if let selectedList = selectedListSummary {
            ManageMembersSheet(
                listId: selectedList.id,
                listName: selectedList.name,
                kind: currentShareKind,
                myRole: selectedList.myRole,
                shareText: listShareText(listName: selectedList.name, items: viewModel.items),
                onLeftList: {
                    Task { await viewModel.refresh() }
                    dismiss()
                }
            )
        }
    }

    private var listSettingsSheetContent: some View {
        let selectedList = viewModel.lists.first(where: { $0.id == viewModel.listId })
        return ListSettingsSheet(
            list: selectedList,
            shareText: selectedList.map { listShareText(listName: $0.name, items: viewModel.items) },
            onMembersRequest: {
                pendingMembersAfterSettings = true
                showingListSettings = false
            },
            onSubmit: { name, color, iconKey in
                Task { await viewModel.updateListSettings(name: name, color: color, iconKey: iconKey) }
            },
            onDeleteRequest: {
                showingListSettings = false
                withAnimation(.spring(response: 0.24, dampingFraction: 0.9)) {
                    showingDeleteListConfirmation = true
                }
            }
        )
    }

    private func handleItemsChanged() {
        setActiveDropSection(nil)
        draggedTodo = nil
        inAppDrag = nil
        dropTargetFrames = [:]
        TodoTaskDragSession.shared.todo = nil
        if let openSwipeTaskID, !viewModel.items.contains(where: { $0.id == openSwipeTaskID }) {
            self.openSwipeTaskID = nil
        }
        if viewModel.mode == .all, highlightedTodoId != nil {
            collapsedSectionIDs = []
        }
    }

    private func requestReschedule(_ todo: TodoItem, to targetDate: Date) {
        setActiveDropSection(nil)
        draggedTodo = nil
        inAppDrag = nil
        dropTargetFrames = [:]
        TodoTaskDragSession.shared.todo = nil
        let targetDay = Calendar.current.startOfDay(for: targetDate)
        let dropSignature = "\(todo.id)|\(targetDay.timeIntervalSince1970)"
        guard TodoTaskDragSession.shared.handledDropSignature != dropSignature else {
            return
        }
        TodoTaskDragSession.shared.handledDropSignature = dropSignature
        guard let due = todo.due,
              !Calendar.current.isDate(due, inSameDayAs: targetDay) else {
            return
        }

        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        if todo.isRecurring {
            pendingRescheduleDrop = TodoRescheduleDrop(todo: todo, targetDate: targetDay, targetHour: nil)
        } else {
            Task { await viewModel.moveTask(todo, toDay: targetDay, scope: .occurrence) }
        }
    }

    // Routes a drop to the right reschedule: Today buckets change the time of day,
    // every other section changes the date.
    private func performDrop(_ todo: TodoItem, into section: TodoTimelineSection) {
        if let hour = section.targetHour {
            requestRescheduleTime(todo, toHour: hour)
        } else if let targetDate = section.targetDate {
            requestReschedule(todo, to: targetDate)
        }
    }

    private func todayBucketLabel(forHour hour: Int) -> String {
        if hour < 12 { return "Morning" }
        if hour < 18 { return "Afternoon" }
        return "Tonight"
    }

    // Today screen: set a task's time of day to a bucket's hour (date unchanged).
    // Mirrors `requestReschedule` but for the Morning / Afternoon / Tonight move.
    private func requestRescheduleTime(_ todo: TodoItem, toHour hour: Int) {
        setActiveDropSection(nil)
        draggedTodo = nil
        inAppDrag = nil
        dropTargetFrames = [:]
        TodoTaskDragSession.shared.todo = nil
        let dropSignature = "\(todo.id)|hour-\(hour)"
        guard TodoTaskDragSession.shared.handledDropSignature != dropSignature else {
            return
        }
        TodoTaskDragSession.shared.handledDropSignature = dropSignature
        guard let due = todo.due,
              todayBucketLabel(forHour: Calendar.current.component(.hour, from: due)) != todayBucketLabel(forHour: hour) else {
            return
        }

        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        if todo.isRecurring {
            pendingRescheduleDrop = TodoRescheduleDrop(todo: todo, targetDate: nil, targetHour: hour)
        } else {
            Task { await viewModel.moveTask(todo, toTimeOfDay: hour, scope: .occurrence) }
        }
    }

    private func resolveTodoForDrop(id: String) -> TodoItem? {
        viewModel.items.first { $0.id == id || $0.canonicalId == id }
    }

    private func sectionID(containing todo: TodoItem) -> String? {
        if let exactSection = groupedSections.first(where: { section in
            section.items.contains { item in item.id == todo.id }
        }) {
            return exactSection.id
        }
        return groupedSections.first { section in
            section.items.contains { item in item.canonicalId == todo.canonicalId }
        }?.id
    }

    private func canDropTodo(_ todo: TodoItem, into section: TodoTimelineSection) -> Bool {
        guard let targetDate = section.targetDate else {
            return false
        }
        if sectionID(containing: todo) == section.id {
            return false
        }
        guard let due = todo.due else {
            return false
        }
        // Today time-buckets: a same-day move between Morning / Afternoon /
        // Tonight. The same-section check above already blocks dropping in place.
        if section.targetHour != nil {
            return true
        }
        return !Calendar.current.isDate(due, inSameDayAs: targetDate)
    }

    private func setActiveDropSection(_ sectionId: String?) {
        guard activeDropSectionId != sectionId else { return }
        withAnimation(todoDropPlaceholderAnimation) {
            activeDropSectionId = sectionId
        }
    }

    private func beginInAppDrag(_ todo: TodoItem, at location: CGPoint) {
        openSwipeTaskID = nil
        if draggedTodo?.id != todo.id {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }
        draggedTodo = todo
        TodoTaskDragSession.shared.todo = todo
        TodoTaskDragSession.shared.handledDropSignature = nil
        inAppDrag = TodoInAppDrag(todo: todo, location: location)
        updateInAppDrag(todo, to: location)
        // Keep the picked-up row realized. Setting `draggedTodo` brings back
        // every empty bucket, and on a list whose tasks are months out that can
        // be ten headers inserted ABOVE this row — enough to push it out of the
        // List's realized window. A dismantled row takes its long-press
        // recognizer with it (TodoInAppLongPressBridge's `detach()` calls
        // `onCancel`), so the drag would end the instant it began, silently.
        // The scroll is minimal rather than centred: if the row is still on
        // screen this does nothing.
        dragAnchorTodoID = todo.id
    }

    private func updateInAppDrag(_ todo: TodoItem, to location: CGPoint) {
        inAppDrag = TodoInAppDrag(todo: todo, location: location)
        setActiveDropSection(dropSectionID(at: location, for: todo))
    }

    private func finishInAppDrag(_ todo: TodoItem, at location: CGPoint?) {
        let targetSectionID = location.flatMap { dropSectionID(at: $0, for: todo) } ??
            activeDropSectionId.flatMap { sectionID in
                guard let section = groupedSections.first(where: { $0.id == sectionID }),
                      canDropTodo(todo, into: section) else {
                    return nil
                }
                return sectionID
            }
        let targetSection = targetSectionID
            .flatMap { sectionID in groupedSections.first { $0.id == sectionID } }
        setActiveDropSection(nil)
        draggedTodo = nil
        inAppDrag = nil
        dropTargetFrames = [:]
        if let targetSection {
            performDrop(todo, into: targetSection)
        } else {
            TodoTaskDragSession.shared.todo = nil
        }
    }

    private func cancelInAppDrag() {
        setActiveDropSection(nil)
        draggedTodo = nil
        inAppDrag = nil
        dropTargetFrames = [:]
        TodoTaskDragSession.shared.todo = nil
    }

    private func dropSectionID(at location: CGPoint, for todo: TodoItem) -> String? {
        dropTargetFrames.values
            .filter { $0.frame.contains(location) }
            .filter { target in
                guard let section = groupedSections.first(where: { $0.id == target.sectionID }) else {
                    return false
                }
                return canDropTodo(todo, into: section)
            }
            .min { lhs, rhs in
                (lhs.frame.width * lhs.frame.height) < (rhs.frame.width * rhs.frame.height)
            }?
            .sectionID
    }

    private func commitPendingReschedule(scope: TaskRescheduleScope) {
        guard let drop = pendingRescheduleDrop else {
            return
        }
        pendingRescheduleDrop = nil
        Task {
            if let hour = drop.targetHour {
                await viewModel.moveTask(drop.todo, toTimeOfDay: hour, scope: scope)
            } else if let targetDate = drop.targetDate {
                await viewModel.moveTask(drop.todo, toDay: targetDate, scope: scope)
            }
        }
    }

    private func closeFloaterTaskHomeSearch() {
        floaterTaskHomeSearchFieldFocused = false
        withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
            floaterTaskHomeSearchExpanded = false
        }
        floaterTaskHomeSearchQuery = ""
    }

    private func openListSearch() {
        HapticManager.buttonTap()
        withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
            listSearchExpanded = true
        }
    }

    /// Leaving the search drops the query with it, so the list is whole again
    /// the next time the bar is opened — the same bargain web's close makes.
    private func closeListSearch() {
        HapticManager.sheetDismiss()
        listSearchFieldFocused = false
        withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
            listSearchExpanded = false
        }
        listSearchQuery = ""
    }

    private func openFloaterTaskHomeSearchResult(_ todo: TodoItem, using proxy: ScrollViewProxy) {
        guard openingFloaterTaskHomeSearchResultID == nil else {
            return
        }
        openingFloaterTaskHomeSearchResultID = todo.id
        closeFloaterTaskHomeSearch()

        highlightedScrollRequestID += 1
        let requestID = highlightedScrollRequestID
        DispatchQueue.main.asyncAfter(deadline: .now() + TodoTimelineMetrics.searchResultScrollDelay) {
            guard requestID == highlightedScrollRequestID else {
                return
            }
            openingFloaterTaskHomeSearchResultID = nil
            withAnimation(.easeInOut(duration: TodoTimelineMetrics.searchResultScrollDuration)) {
                proxy.scrollTo(timelineTodoScrollID(todo.id), anchor: .center)
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + TodoTimelineMetrics.searchResultFlashDelay) {
                guard requestID == highlightedScrollRequestID else {
                    return
                }
                flashTodoId = todo.id
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                guard requestID == highlightedScrollRequestID else {
                    return
                }
                if flashTodoId == todo.id || flashTodoId == todo.canonicalId {
                    flashTodoId = nil
                }
            }
        }
    }

    private func matchesHighlightedTodo(_ todo: TodoItem, id: String) -> Bool {
        todo.id == id || todo.canonicalId == id
    }

    private struct HighlightedTodoTarget {
        let todo: TodoItem
        let preScrollTodo: TodoItem
    }

    private func highlightedTodoTarget(for id: String) -> HighlightedTodoTarget? {
        let orderedTodos = groupedSections.flatMap(\.items)
        guard let targetIndex = orderedTodos.firstIndex(where: { matchesHighlightedTodo($0, id: id) }) else {
            return nil
        }
        let preScrollIndex = max(0, targetIndex - TodoTimelineMetrics.searchResultPreScrollItemCount)
        return HighlightedTodoTarget(
            todo: orderedTodos[targetIndex],
            preScrollTodo: orderedTodos[preScrollIndex]
        )
    }

    private func timelineSectionScrollID(_ sectionID: String) -> String {
        "timeline-section-\(sectionID)"
    }

    private func timelineTodoScrollID(_ todoID: String) -> String {
        "timeline-todo-\(todoID)"
    }

    private func shouldFlashTodo(_ todo: TodoItem) -> Bool {
        guard let flashTodoId else {
            return false
        }
        return matchesHighlightedTodo(todo, id: flashTodoId)
    }

    private func scrollToHighlightedTodo(using proxy: ScrollViewProxy) {
        guard viewModel.mode == .all,
              let highlightedTodoId,
              !highlightedTodoId.isEmpty,
              let target = highlightedTodoTarget(for: highlightedTodoId) else {
            return
        }

        let hadCollapsedSections = !collapsedSectionIDs.isEmpty
        if !collapsedSectionIDs.isEmpty {
            withAnimation(.spring(response: 0.28, dampingFraction: 0.9)) {
                collapsedSectionIDs = []
            }
        }

        highlightedScrollRequestID += 1
        let requestID = highlightedScrollRequestID
        let preScrollID = timelineTodoScrollID(target.preScrollTodo.id)
        let targetScrollID = timelineTodoScrollID(target.todo.id)
        let preScrollDelay = hadCollapsedSections ? TodoTimelineMetrics.searchResultSectionExpandDelay : 0

        DispatchQueue.main.asyncAfter(deadline: .now() + preScrollDelay) {
            guard requestID == highlightedScrollRequestID else {
                return
            }
            proxy.scrollTo(preScrollID, anchor: .top)
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + preScrollDelay + TodoTimelineMetrics.searchResultScrollDelay) {
            guard requestID == highlightedScrollRequestID else {
                return
            }

            withAnimation(.easeInOut(duration: TodoTimelineMetrics.searchResultScrollDuration)) {
                proxy.scrollTo(targetScrollID, anchor: .center)
            }

            DispatchQueue.main.asyncAfter(deadline: .now() + TodoTimelineMetrics.searchResultFlashDelay) {
                guard requestID == highlightedScrollRequestID else {
                    return
                }
                flashTodoId = highlightedTodoId
            }

            DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                guard requestID == highlightedScrollRequestID else {
                    return
                }
                if flashTodoId == highlightedTodoId {
                    flashTodoId = nil
                }
            }
        }
    }

    private func presentSummary() {
        Task {
            await viewModel.summarizeCurrentMode()
            showingSummary = true
        }
    }

    private var standardModeContent: some View {
        List {
            if let errorMessage = viewModel.errorMessage {
                Section {
                    ErrorRetryView(message: errorMessage) {
                        Task { await viewModel.refresh() }
                    }
                    .listRowBackground(colors.background)
                }
            }
            ForEach(groupedSections) { section in
                let isDropEligibleSection = draggedTodo.map { canDropTodo($0, into: section) } ?? false
                let isActiveDropSection = activeDropSectionId == section.id && isDropEligibleSection
                Section {
                    ForEach(section.items) { todo in
                        todoRow(todo, in: section)
                            .todoInAppDropTargetFrame(
                                targetID: "standard-row-\(section.id)-\(todo.id)",
                                section: section,
                                enabled: viewModel.mode.supportsTaskReschedule && !isViewerList && isDropEligibleSection
                            )
                            .listRowBackground(todo.id == highlightedTodoId ? colors.surfaceVariant : colors.surface)
                    }
                    if viewModel.mode.supportsTaskReschedule,
                       isActiveDropSection,
                       section.targetDate != nil {
                        TodoDropPlaceholder(isActive: isActiveDropSection)
                            .todoInAppDropTargetFrame(
                                targetID: "standard-placeholder-\(section.id)",
                                section: section,
                                enabled: isDropEligibleSection
                            )
                            .listRowInsets(EdgeInsets(top: 4, leading: 20, bottom: 6, trailing: 20))
                            .listRowBackground(colors.surface)
                            .transition(timelineRowTransition())
                            .scheduledTodoDropTarget(
                                section: section,
                                draggedTodo: draggedTodo,
                                resolveTodo: resolveTodoForDrop,
                                onMove: { todo, targetDate in
                                    requestReschedule(todo, to: targetDate)
                                },
                                canMoveTodo: canDropTodo,
                                onSectionChange: { sectionId in
                                    setActiveDropSection(sectionId)
                                }
                            )
                    }
                    if viewModel.mode.supportsTaskReschedule, !section.items.isEmpty {
                        Color.clear
                            .frame(height: 8)
                            .todoInAppDropTargetFrame(
                                targetID: "standard-spacer-\(section.id)",
                                section: section,
                                enabled: isDropEligibleSection
                            )
                            .listRowInsets(EdgeInsets())
                            .scheduledTodoDropTarget(
                                section: section,
                                draggedTodo: draggedTodo,
                                resolveTodo: resolveTodoForDrop,
                                onMove: { todo, targetDate in
                                    requestReschedule(todo, to: targetDate)
                                },
                                canMoveTodo: canDropTodo,
                                onSectionChange: { sectionId in
                                    setActiveDropSection(sectionId)
                                }
                            )
                    }
                } header: {
                    if section.title.isEmpty {
                        EmptyView()
                    } else {
                        Text(section.title)
                            .foregroundStyle(isActiveDropSection ? colors.error : colors.onSurfaceVariant)
                            .frame(maxWidth: .infinity, minHeight: 38, alignment: .leading)
                            .contentShape(Rectangle())
                            .todoInAppDropTargetFrame(
                                targetID: "standard-header-\(section.id)",
                                section: section,
                                enabled: viewModel.mode.supportsTaskReschedule && !isViewerList && isDropEligibleSection
                            )
                            .timelinePinnedSectionHeaderBackground()
                            .scheduledTodoDropTarget(
                                section: section,
                                draggedTodo: draggedTodo,
                                resolveTodo: resolveTodoForDrop,
                                onMove: { todo, targetDate in
                                    requestReschedule(todo, to: targetDate)
                                },
                                canMoveTodo: canDropTodo,
                                onSectionChange: { sectionId in
                                    setActiveDropSection(sectionId)
                                }
                            )
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(colors.background)
        .disableVerticalScrollBounce()
        .animation(todoDropPlaceholderAnimation, value: activeDropSectionId)
        .animation(.easeInOut(duration: 0.22), value: timelineItemAnimationKey)
    }

    private var todayModeContent: some View {
        ZStack {
            List {
                timelineHeroTitleRow

                if let errorMessage = viewModel.errorMessage {
                    Section {
                        ErrorRetryView(message: errorMessage) {
                            Task { await viewModel.refresh() }
                        }
                        .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 18, trailing: TodoTimelineMetrics.horizontalPadding))
                        .listRowBackground(colors.background)
                        .listRowSeparator(.hidden)
                    }
                }

                ForEach(Array(groupedSections.enumerated()), id: \.element.id) { index, section in
                    Section {
                        if !section.items.isEmpty {
                            ForEach(Array(section.items.enumerated()), id: \.element.id) { itemIndex, todo in
                                minimalTimelineRow(todo, in: section)
                                    .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
                                    .listRowBackground(colors.background)
                                    .listRowSeparator(.hidden)
                                if shouldShowDateDivider(after: itemIndex, inSectionAt: index, sections: groupedSections) {
                                    TimelineRowDivider()
                                }
                            }
                        }
                    } header: {
                        TimelineSectionHeader(
                            title: section.title,
                            isActiveDropTarget: activeDropSectionId == section.id
                        )
                        .padding(.top, index == 0 ? 0 : TodoTimelineMetrics.sectionTopSpacing)
                        .timelinePinnedSectionHeaderBackground()
                        .listRowInsets(
                            EdgeInsets(
                                top: 0,
                                leading: 0,
                                bottom: 0,
                                trailing: 0
                            )
                        )
                        .listRowSeparator(.hidden)
                    }
                }

                Color.clear
                    .frame(height: TodoTimelineMetrics.timelineBottomSpacerHeight)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(colors.background)
                    .listRowSeparator(.hidden)
                    .disableVerticalScrollBounce()
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(colors.background)
            .contentMargins(.top, 0, for: .scrollContent)
            .listRowSpacing(0)
            .listSectionSpacing(0)
            .environment(\.defaultMinListRowHeight, 1)
            .animation(.easeInOut(duration: 0.22), value: timelineItemAnimationKey)

        }
    }

    /// Shown in place of the timeline when a search matches nothing. The screen's
    /// own empty scene carries it, so a screen has one no-results treatment and
    /// not two — with the way out of the query that does not need the keyboard
    /// back hung off the scene's action slot.
    private var listSearchEmptyState: some View {
        TdayEmptyState(
            assetName: "NavSearch",
            accentColor: modeAccentColor,
            title: L("No matching tasks"),
            description: L("Try a different word, or clear the search."),
            action: AnyView(
                Button {
                    HapticManager.gentleTap()
                    listSearchQuery = ""
                    listSearchFieldFocused = true
                } label: {
                    Text(L("Clear search"))
                        .font(.tdayRounded(size: 15, weight: .bold))
                        .foregroundStyle(colors.primary)
                }
                .buttonStyle(.plain)
            )
        )
        .frame(
            maxWidth: .infinity,
            minHeight: inlineEmptyStateGapHeight,
            alignment: .center
        )
        .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
        .listRowBackground(colors.background)
        .listRowSeparator(.hidden)
    }

    private var minimalTimelineModeContent: some View {
        ScrollViewReader { scrollProxy in
            ZStack {
                List {
                    timelineHeroTitleRow
                        .id(todoTimelineScrollTopID)

                    if showFloaterTaskHomeSearchResults {
                        FloaterTaskHomeSearchResultsCard(
                            todos: floaterTaskHomeSearchResults,
                            listsByID: floaterTaskHomeListByID,
                            onOpenTodo: { todo in
                                openFloaterTaskHomeSearchResult(todo, using: scrollProxy)
                            }
                        )
                        .background(
                            GeometryReader { proxy in
                                Color.clear
                                    .preference(
                                        key: FloaterSearchResultsFrameKey.self,
                                        value: proxy.frame(in: .named(todoTimelineDragCoordinateSpace))
                                    )
                            }
                        )
                        .listRowInsets(
                            EdgeInsets(
                                top: 0,
                                leading: TodoTimelineMetrics.horizontalPadding,
                                bottom: 10,
                                trailing: TodoTimelineMetrics.horizontalPadding
                            )
                        )
                        .listRowBackground(colors.background)
                        .listRowSeparator(.hidden)
                    }

                    // While a query is live only the results remain; the rest
                    // of the feed gives way to blank space, and a tap there
                    // dismisses the field.
                    if !showFloaterTaskHomeSearchResults {

                    if let errorMessage = viewModel.errorMessage {
                        Section {
                            ErrorRetryView(message: errorMessage) {
                                Task { await viewModel.refresh() }
                            }
                            .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 18, trailing: TodoTimelineMetrics.horizontalPadding))
                            .listRowBackground(colors.background)
                            .listRowSeparator(.hidden)
                        }
                    }

                    // A query that matches nothing keeps its own counsel: the
                    // section headers would otherwise stay behind with nothing
                    // under them, a floater list's empty header included.
                    if showsListSearchEmptyState {
                        Section {
                            listSearchEmptyState
                        }
                    } else {
                        ForEach(Array(groupedSections.enumerated()), id: \.element.id) { index, section in
                            minimalTimelineSection(
                                section,
                                sectionIndex: index,
                                sections: groupedSections,
                                isFirstSection: index == 0
                            )
                        }
                    }

                    if showInlineFloaterTaskHomeEmpty {
                        Section {
                            TdayEmptyState(
                                assetName: emptyStateAssetName,
                                accentColor: modeAccentColor,
                                title: emptyTimelineTitle(for: viewModel.mode, isListDetail: isListDetailScreen),
                                description: emptyTimelineDescription(for: viewModel.mode, isListDetail: isListDetailScreen)
                            )
                            .frame(maxWidth: .infinity, minHeight: inlineEmptyStateGapHeight, alignment: .center)
                            .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
                            .listRowBackground(colors.background)
                            .listRowSeparator(.hidden)
                        }
                    }

                    if !floaterTaskHomeListRows.isEmpty {
                        Section {
                            ForEach(floaterTaskHomeListRows, id: \.list.id) { row in
                                FloaterTaskHomeListCard(
                                    list: row.list,
                                    count: row.count,
                                    onTap: {
                                        onOpenFloaterList(row.list.id, row.list.name)
                                    }
                                )
                                .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 10, trailing: TodoTimelineMetrics.horizontalPadding))
                                .listRowBackground(colors.background)
                                .listRowSeparator(.hidden)
                            }
                        } header: {
                            Text("My Lists")
                                .font(.tdayRounded(size: 24, weight: .heavy))
                                .foregroundStyle(colors.onSurface)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.top, 4)
                                .padding(.bottom, 10)
                                .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
                                .listRowSeparator(.hidden)
                        }
                    }
                    }

                    Color.clear
                        .frame(height: minimalTimelineBottomSpacerHeight)
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(colors.background)
                        .listRowSeparator(.hidden)
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .background(colors.background)
                .contentMargins(.top, 0, for: .scrollContent)
                .listRowSpacing(0)
                .listSectionSpacing(0)
                .scrollBounceBehavior(pullRefreshEnabled ? .always : .basedOnSize, axes: .vertical)
                // Freeze list scrolling while a task is being dragged so the drop
                // target stays put under the finger (matches Calendar).
                .scrollDisabled(inAppDrag != nil)
                .environment(\.defaultMinListRowHeight, 1)
                .disableVerticalScrollBounce(!pullRefreshEnabled)
                .animation(todoDropPlaceholderAnimation, value: activeDropSectionId)
                .animation(.easeInOut(duration: 0.22), value: timelineItemAnimationKey)

            }
            .onAppear {
                scrollToHighlightedTodo(using: scrollProxy)
            }
            // Set by `beginInAppDrag`. Minimal scroll, no animation and no
            // anchor: it does nothing while the row is still on screen, and only
            // pulls it back when the buckets that appeared above it pushed it out
            // of the realized window — which would otherwise dismantle the row
            // and cancel the drag.
            .onChange(of: dragAnchorTodoID) { _, anchored in
                guard let anchored else { return }
                scrollProxy.scrollTo(timelineTodoScrollID(anchored))
                dragAnchorTodoID = nil
            }
            .onChange(of: highlightedTodoId) {
                scrollToHighlightedTodo(using: scrollProxy)
            }
            .onChange(of: viewModel.items) {
                scrollToHighlightedTodo(using: scrollProxy)
            }
            .onChange(of: scrollToTopRequestID) { _, requestID in
                guard requestID > 0, isFloaterTaskHomeScreen else { return }
                closeFloaterTaskHomeSearch()
                withAnimation(.easeInOut(duration: 0.34)) {
                    scrollProxy.scrollTo(todoTimelineScrollTopID, anchor: .top)
                }
            }
            // Tapping the header mark or title restores the feed's opened
            // state: hero title, full-width search, big leaf.
            .onChange(of: titleScrollToTopRequestID) { _, requestID in
                guard requestID > 0 else { return }
                closeFloaterTaskHomeSearch()
                withAnimation(.easeInOut(duration: 0.34)) {
                    scrollProxy.scrollTo(todoTimelineScrollTopID, anchor: .top)
                }
            }
        }
    }

    private func todoRow(
        _ todo: TodoItem,
        in section: TodoTimelineSection
    ) -> some View {
        let completionPhase = completionPhases[todo.id]
        let isCompleting = completionPhase != nil
        let isFading = completionPhase == .fading
        let rowContent = VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 10) {
                Circle()
                    .fill(priorityColor(todo.priority))
                    .frame(width: 10, height: 10)
                Text(todo.title)
                    .font(.tdayRounded(size: 15, weight: .bold))
                    .foregroundStyle(colors.onSurface)
                Spacer()
                if todo.pinned {
                    Image(systemName: "pin.fill")
                        .foregroundStyle(colors.tertiary)
                }
            }
            if let due = todo.due {
                HStack(spacing: 6) {
                    Text(due.formatted(.dateTime.month(.abbreviated).day().year().hour().minute().locale(AppLocale.current)))
                        .font(.tdayRounded(size: 12, weight: .semibold))
                        .foregroundStyle(colors.onSurfaceVariant)
                }
            }
            let flattenedDescription = flattenNotesToPlainText(todo.description)
            if !flattenedDescription.isEmpty {
                Text(flattenedDescription)
                    .font(.tdayRounded(size: 12, weight: .semibold))
                    .foregroundStyle(colors.onSurfaceVariant)
            }
        }
        .opacity(isFading ? 0 : 1)
        .scaleEffect(isFading ? 0.985 : 1, anchor: .center)
        .offset(y: isFading ? -10 : 0)
        .animation(.easeInOut(duration: 0.26), value: isFading)
        .opacity(draggedTodo?.id == todo.id ? 0.7 : 1)
        .allowsHitTesting(!isCompleting)
        .todoTrailingSwipeActions(
            rowID: todo.id,
            openRowID: $openSwipeTaskID,
            enabled: !isCompleting && !isViewerList,
            extraAction: promoteOrFloatSwipeAction(for: todo),
            onEdit: {
                editingTodo = todo
            },
            onDelete: {
                Task { await viewModel.delete(todo) }
            }
        )
        .swipeActions(edge: .leading, allowsFullSwipe: true) {
            Button {
                completeTodoWithoutReflow(todo)
            } label: {
                Label("Complete", systemImage: "checkmark")
            }
            .tint(.green)
        }

        return rowContent
            .transition(.opacity.combined(with: .scale(scale: 0.985)))
            .scheduledTodoDropTarget(
                section: section,
                draggedTodo: draggedTodo,
                resolveTodo: resolveTodoForDrop,
                onMove: { droppedTodo, targetDate in
                    requestReschedule(droppedTodo, to: targetDate)
                },
                canMoveTodo: canDropTodo,
                onSectionChange: { sectionId in
                    setActiveDropSection(sectionId)
                }
            )
            .modifier(
                TodoInAppDragModifier(
                    enabled: viewModel.mode.supportsTaskReschedule && !isViewerList,
                    todo: todo,
                    onStart: beginInAppDrag,
                    onMove: updateInAppDrag,
                    onEnd: finishInAppDrag,
                    onCancel: cancelInAppDrag
                )
            )
    }

    /// Dim factor for a "resting" floater row: 1 = normal, lower = faded/dormant.
    private func restingRowOpacity(for todo: TodoItem) -> Double {
        guard viewModel.mode == .floater, !todo.completed, RestingFloatersStore().isEnabled else {
            return 1
        }
        switch floaterRestingTier(updatedAt: todo.updatedAt, now: Date()) {
        case .resting: return 0.45
        case .fading: return 0.6
        case .active: return 1
        }
    }

    private func minimalTimelineRow(_ todo: TodoItem, in section: TodoTimelineSection, flashHighlight: Bool = false) -> some View {
        let listMeta = todo.listId.flatMap { listId in
            viewModel.lists.first(where: { $0.id == listId })
        }
        let showListIndicator = listMeta != nil && viewModel.mode != .list
        let priorityIcon = priorityIndicatorSymbolName(todo.priority)
        let subtitleText = minimalTimelineSubtitle(for: todo, in: section)
        let isOverdueTask = !todo.completed && (todo.due ?? .distantFuture) < Date()
        let subtitleColor = isOverdueTask ? colors.error : colors.onSurfaceVariant.opacity(0.8)
        let completionPhase = completionPhases[todo.id]
        let isCompleting = completionPhase != nil
        let isFading = completionPhase == .fading
        let showCheckmark = completionPhase != nil || todo.completed
        let showStrikethrough = completionPhase == .struck || completionPhase == .fading || todo.completed

        return VStack(spacing: 0) {
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Button {
                    completeTodoWithoutReflow(todo)
                } label: {
                    Image(systemName: showCheckmark ? "checkmark.circle.fill" : "circle")
                        .font(.system(size: TodoTimelineMetrics.minimalRowToggleSize, weight: .regular))
                        .foregroundStyle(showCheckmark ? Color.green : colors.onSurfaceVariant.opacity(0.78))
                        .frame(width: TodoTimelineMetrics.minimalRowToggleFrame, height: TodoTimelineMetrics.minimalRowToggleFrame)
                }
                .buttonStyle(
                    TdayPressButtonStyle(
                        shadowColor: Color.black,
                        pressedShadowOpacity: 0,
                        normalShadowOpacity: 0
                    )
                )
                // Keep the toggle on the first line of a multi-line title.
                .alignmentGuide(.firstTextBaseline) { dimension in
                    dimension[VerticalAlignment.center] + 5
                }

                VStack(alignment: .leading, spacing: 4) {
                    TodoTimelineTaskTitle(
                        text: todo.title,
                        isCompleted: showStrikethrough,
                        titleColor: showStrikethrough ? colors.onSurface.opacity(0.78) : colors.onSurface,
                        strikeColor: colors.onSurface.opacity(0.65)
                    )

                    if let subtitleText {
                        Text(subtitleText)
                            .font(.tdayRounded(size: TodoTimelineMetrics.minimalRowSubtitleSize, weight: .semibold))
                            .foregroundStyle(subtitleColor)
                    }

                    let flattenedDescription = flattenNotesToPlainText(todo.description)
                        .trimmingCharacters(in: .whitespacesAndNewlines)
                    if !flattenedDescription.isEmpty {
                        Text(flattenedDescription)
                            .font(.tdayRounded(size: 12, weight: .semibold))
                            .foregroundStyle(colors.onSurfaceVariant)
                            // Struck alongside the title so the whole task reads
                            // as done during the completion animation.
                            .strikethrough(showStrikethrough, color: colors.onSurfaceVariant)
                            .animation(.easeInOut(duration: 0.32), value: showStrikethrough)
                    }
                }

                Spacer(minLength: 0)

                if showListIndicator || priorityIcon != nil {
                    HStack(spacing: 8) {
                        if let listMeta, showListIndicator {
                            TdayListIcon(iconKey: listMeta.iconKey, size: TodoTimelineMetrics.minimalRowIndicatorSize)
                                .foregroundStyle(todoListAccentColor(for: listMeta.color))
                        }
                        if let priorityIcon {
                            Image(systemName: priorityIcon)
                                .font(.system(size: TodoTimelineMetrics.minimalRowIndicatorSize, weight: .semibold))
                                .foregroundStyle(priorityColor(todo.priority))
                        }
                    }
                    .padding(.trailing, TodoTimelineMetrics.minimalRowTrailingIndicatorPadding)
                    // Keep the trailing indicators on the first line too.
                    .alignmentGuide(.firstTextBaseline) { dimension in
                        dimension[VerticalAlignment.center] + 5
                    }
                }
            }
            .padding(.vertical, TodoTimelineMetrics.minimalRowVerticalPadding)
            .contentShape(Rectangle())
        }
        .opacity((isFading ? 0 : (draggedTodo?.id == todo.id ? 0.7 : 1)) * restingRowOpacity(for: todo))
        .scaleEffect(isFading ? 0.985 : 1, anchor: .center)
        .offset(y: isFading ? -10 : 0)
        .animation(.easeInOut(duration: 0.26), value: isFading)
        .allowsHitTesting(!isCompleting)
        .transition(.opacity.combined(with: .scale(scale: 0.985)))
        .modifier(TimelineTaskFlashHighlight(active: flashHighlight))
        .todoTrailingSwipeActions(
            rowID: todo.id,
            openRowID: $openSwipeTaskID,
            enabled: !isCompleting && !isViewerList,
            extraAction: promoteOrFloatSwipeAction(for: todo),
            onEdit: {
                editingTodo = todo
            },
            onDelete: {
                Task { await viewModel.delete(todo) }
            }
        )
        .scheduledTodoDropTarget(
            section: section,
            draggedTodo: draggedTodo,
            resolveTodo: resolveTodoForDrop,
            onMove: { droppedTodo, _ in
                performDrop(droppedTodo, into: section)
            },
            canMoveTodo: canDropTodo,
            onSectionChange: { sectionId in
                setActiveDropSection(sectionId)
            }
        )
        .modifier(
            TodoInAppDragModifier(
                enabled: viewModel.mode.supportsTaskReschedule && !isViewerList,
                todo: todo,
                onStart: beginInAppDrag,
                onMove: updateInAppDrag,
                onEnd: finishInAppDrag,
                onCancel: cancelInAppDrag
            )
        )
    }

    private func completeTodoWithoutReflow(_ todo: TodoItem) {
        guard !isViewerList else { return }
        guard completionPhases[todo.id] == nil else {
            return
        }
        if openSwipeTaskID == todo.id {
            openSwipeTaskID = nil
        }
        HapticManager.taskCompleted()
        SoundManager.taskCompleted()
        withAnimation(.easeInOut(duration: 0.16)) {
            completionPhases[todo.id] = .checked
        }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 160_000_000)
            withAnimation(.easeInOut(duration: 0.22)) {
                completionPhases[todo.id] = .struck
            }
            try? await Task.sleep(nanoseconds: 360_000_000)
            withAnimation(.easeInOut(duration: 0.26)) {
                completionPhases[todo.id] = .fading
            }
            try? await Task.sleep(nanoseconds: 260_000_000)
            await viewModel.complete(todo)
            completionPhases[todo.id] = nil
        }
    }

    @ViewBuilder
    private func minimalTimelineSection(
        _ section: TodoTimelineSection,
        sectionIndex: Int,
        sections: [TodoTimelineSection],
        isFirstSection: Bool
    ) -> some View {
        let canCollapseSection = canCollapseTimelineSection(section)
        let isCollapsed = isTimelineSectionCollapsed(section)
        let isDropEligibleSection = draggedTodo.map { canDropTodo($0, into: section) } ?? false
        let isActiveDropSection = activeDropSectionId == section.id && isDropEligibleSection

        Section {
            if viewModel.mode.supportsTaskReschedule,
               isActiveDropSection,
               section.targetDate != nil {
                TodoDropPlaceholder(isActive: isActiveDropSection)
                    .todoInAppDropTargetFrame(
                        targetID: "minimal-placeholder-\(section.id)",
                        section: section,
                        enabled: isDropEligibleSection
                    )
                    .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 8, trailing: TodoTimelineMetrics.horizontalPadding))
                    .listRowBackground(colors.background)
                    .listRowSeparator(.hidden)
                    .transition(timelineRowTransition())
                    .scheduledTodoDropTarget(
                        section: section,
                        draggedTodo: draggedTodo,
                        resolveTodo: resolveTodoForDrop,
                        onMove: { todo, _ in
                            performDrop(todo, into: section)
                        },
                        canMoveTodo: canDropTodo,
                        onSectionChange: { sectionId in
                            setActiveDropSection(sectionId)
                        }
                    )
            }
            if !isCollapsed {
                ForEach(Array(section.items.enumerated()), id: \.element.id) { itemIndex, todo in
                    minimalTimelineRow(todo, in: section, flashHighlight: shouldFlashTodo(todo))
                        .id(timelineTodoScrollID(todo.id))
                        .todoInAppDropTargetFrame(
                            targetID: "minimal-row-\(section.id)-\(todo.id)",
                            section: section,
                            enabled: viewModel.mode.supportsTaskReschedule && !isViewerList && isDropEligibleSection
                        )
                        .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
                        .listRowBackground(colors.background)
                        .listRowSeparator(.hidden)
                        .transition(timelineRowTransition())
                    if shouldShowDateDivider(after: itemIndex, inSectionAt: sectionIndex, sections: sections) {
                        TimelineRowDivider()
                            .transition(timelineRowTransition())
                    }
                }
            }
        } header: {
            if !usesRootFeedHeader {
                TimelineSectionHeader(
                    title: section.title,
                    isActiveDropTarget: isActiveDropSection,
                    isCollapsible: canCollapseSection,
                    isCollapsed: isCollapsed,
                    onTap: canCollapseSection ? {
                        toggleTimelineSection(section)
                    } : nil
                )
                .id(timelineSectionScrollID(section.id))
                .padding(.top, isFirstSection ? 0 : TodoTimelineMetrics.sectionTopSpacing)
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
                .todoInAppDropTargetFrame(
                    targetID: "minimal-header-\(section.id)",
                    section: section,
                    enabled: viewModel.mode.supportsTaskReschedule && !isViewerList && isDropEligibleSection
                )
                .timelinePinnedSectionHeaderBackground()
                .scheduledTodoDropTarget(
                    section: section,
                    draggedTodo: draggedTodo,
                    resolveTodo: resolveTodoForDrop,
                    onMove: { todo, _ in
                        performDrop(todo, into: section)
                    },
                    canMoveTodo: canDropTodo,
                    onSectionChange: { sectionId in
                        setActiveDropSection(sectionId)
                    }
                )
                .listRowInsets(
                    EdgeInsets(
                        top: 0,
                        leading: 0,
                        bottom: 0,
                        trailing: 0
                    )
                )
                .listRowSeparator(.hidden)
            }
        }
    }

    private func canCollapseTimelineSection(_ section: TodoTimelineSection) -> Bool {
        guard !section.items.isEmpty else {
            return false
        }
        // A live query outranks a shut bucket: a list opens with Earlier closed,
        // and a task the search turned up in there must not stay hidden behind
        // its header. The buckets are therefore not the reader's to shut while
        // the query stands — a header that still took a tap would flip the
        // stored state behind a screen that could not show it.
        guard !isSearchingList else {
            return false
        }
        if viewModel.mode == .all {
            return true
        }
        if viewModel.mode == .overdue {
            return true
        }
        if viewModel.mode == .list {
            return section.id == "earlier" && section.isCollapsible
        }
        return viewModel.mode == .priority && section.isCollapsible
    }

    private func isTimelineSectionCollapsed(_ section: TodoTimelineSection) -> Bool {
        canCollapseTimelineSection(section) && collapsedSectionIDs.contains(section.id)
    }

    private func toggleTimelineSection(_ section: TodoTimelineSection) {
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
        sections: [TodoTimelineSection]
    ) -> Bool {
        guard sections.indices.contains(sectionIndex),
              sections[sectionIndex].items.indices.contains(itemIndex) else {
            return false
        }

        let currentTodo = sections[sectionIndex].items[itemIndex]
        let nextTodoInSection = sections[sectionIndex].items.dropFirst(itemIndex + 1).first
        if let nextTodoInSection {
            guard let currentDue = currentTodo.due,
                  let nextDue = nextTodoInSection.due else {
                return false
            }
            return !Calendar.current.isDate(currentDue, inSameDayAs: nextDue)
        }

        let nextVisibleTodo = sections.dropFirst(sectionIndex + 1)
            .first { !isTimelineSectionCollapsed($0) && !$0.items.isEmpty }?
            .items.first

        guard let nextVisibleTodo else {
            return false
        }
        guard let currentDue = currentTodo.due,
              let nextDue = nextVisibleTodo.due else {
            return false
        }
        return !Calendar.current.isDate(currentDue, inSameDayAs: nextDue)
    }

    private func timelineRowTransition() -> AnyTransition {
        let insertion = AnyTransition.opacity
            .combined(with: .move(edge: .top))
            .animation(todoDropPlaceholderAnimation)
        let removal = AnyTransition.opacity
            .combined(with: .move(edge: .top))
            .animation(todoDropPlaceholderAnimation)
        return .asymmetric(insertion: insertion, removal: removal)
    }

    private func minimalTimelineSubtitle(for todo: TodoItem, in section: TodoTimelineSection) -> String? {
        guard let due = todo.due else {
            return nil
        }
        let timeText = due.formatted(.dateTime.hour().minute().locale(AppLocale.current))
        let dueBodyText = if section.id == "earlier" &&
            (viewModel.mode == .all || viewModel.mode == .priority || viewModel.mode == .list) {
            timelineDateTimeText(due)
        } else {
            timeText
        }

        switch viewModel.mode {
        case .today:
            if !todo.completed && due < Date() {
                return L("Overdue, %@", dueBodyText)
            }
            return L("Due %@", dueBodyText)
        case .overdue:
            return L("Overdue, %@", dueBodyText)
        case .scheduled:
            return L("Due %@", dueBodyText)
        case .all:
            if !todo.completed && due < Date() {
                return L("Overdue, %@", dueBodyText)
            }
            return L("Due %@", dueBodyText)
        case .priority:
            if !todo.completed && due < Date() {
                return L("Overdue, %@", dueBodyText)
            }
            return L("Due %@", dueBodyText)
        case .floater:
            return nil
        case .list:
            if !todo.completed && due < Date() {
                return L("Overdue, %@", dueBodyText)
            }
            return L("Due %@", dueBodyText)
        }
    }
}

struct TimelineTopBar: View {
    let title: String
    let accentColor: Color
    let collapseProgress: CGFloat
    let onBack: () -> Void
    let actions: [TimelineTopBarAction]
    let showsTimeOfDayIcon: Bool
    let titleRevealStart: CGFloat
    let titleRevealEnd: CGFloat
    let titleRevealDistance: CGFloat
    /// While set, the field takes the row: the back chevron stays where it is
    /// and the title and the action cluster give way to it, the way the web
    /// list pages hand their pinned bar over to the search input.
    let searchActive: Bool
    @Binding var searchText: String
    let searchPlaceholder: String
    let searchFieldFocused: FocusState<Bool>.Binding?
    let onSearchClose: () -> Void

    @Environment(\.tdayColors) private var colors

    init(
        title: String,
        accentColor: Color,
        collapseProgress: CGFloat,
        onBack: @escaping () -> Void,
        actions: [TimelineTopBarAction],
        showsTimeOfDayIcon: Bool = false,
        titleRevealStart: CGFloat = TodoTimelineMetrics.collapsedTitleRevealStart,
        titleRevealEnd: CGFloat = TodoTimelineMetrics.collapsedTitleRevealEnd,
        titleRevealDistance: CGFloat = TodoTimelineMetrics.collapsedTitleRevealDistance,
        searchActive: Bool = false,
        searchText: Binding<String> = .constant(""),
        searchPlaceholder: String = "",
        searchFieldFocused: FocusState<Bool>.Binding? = nil,
        onSearchClose: @escaping () -> Void = {}
    ) {
        self.title = title
        self.accentColor = accentColor
        self.collapseProgress = collapseProgress
        self.onBack = onBack
        self.actions = actions
        self.showsTimeOfDayIcon = showsTimeOfDayIcon
        self.titleRevealStart = titleRevealStart
        self.titleRevealEnd = titleRevealEnd
        self.titleRevealDistance = titleRevealDistance
        self.searchActive = searchActive
        _searchText = searchText
        self.searchPlaceholder = searchPlaceholder
        self.searchFieldFocused = searchFieldFocused
        self.onSearchClose = onSearchClose
    }

    private var progress: CGFloat {
        min(max(collapseProgress, 0), 1)
    }

    private var revealProgress: CGFloat {
        TodoTimelineMetrics.progress(
            progress,
            from: titleRevealStart,
            to: titleRevealEnd
        )
    }

    private var titleOffsetY: CGFloat {
        titleRevealDistance * (1 - revealProgress)
    }

    private var titleContent: some View {
        TodoTimelineTitleLabel(
            title: title,
            accentColor: accentColor,
            showsTimeOfDayIcon: showsTimeOfDayIcon
        )
    }

    private var trailingActionReservedWidth: CGFloat {
        let count = CGFloat(max(1, actions.count))
        return count * TodoTimelineMetrics.topBarButtonFrame +
            max(0, count - 1) * TodoTimelineMetrics.topBarButtonSpacing
    }

    /// The capsule is exactly as tall as the bar's button row, so swapping it in
    /// changes what the row holds and never how tall it is — every collapse
    /// progress on these screens is measured from that height.
    private var searchRow: some View {
        HStack(spacing: TodoTimelineMetrics.topBarButtonSpacing) {
            TdaySearchCapsule(
                text: $searchText,
                placeholder: searchPlaceholder,
                clearAccessibilityLabel: L("Clear search"),
                focused: searchFieldFocused
            )

            // The capsule's own X clears the query; leaving the search behind
            // altogether is this one, as on the root feeds.
            TimelineTopBarButton(
                systemName: "xmark",
                assetName: "NavClose",
                chrome: .filled,
                action: onSearchClose
            )
            .accessibilityLabel(Text(L("Cancel search")))
        }
        .padding(.leading, TodoTimelineMetrics.topBarButtonSpacing)
    }

    var body: some View {
        ZStack {
            HStack(spacing: 0) {
                TimelineTopBarButton(
                    systemName: "chevron.left",
                    assetName: "LucideChevronLeft",
                    chrome: .filled,
                    action: onBack
                )
                .accessibilityLabel(Text(L("Back")))

                if searchActive {
                    searchRow
                } else {
                    Spacer(minLength: 0)
                    if actions.isEmpty {
                        Color.clear
                            .frame(width: TodoTimelineMetrics.topBarButtonFrame, height: TodoTimelineMetrics.topBarButtonFrame)
                    } else {
                        // Same gap as the web list header's action cluster (gap-2
                        // between the circular buttons).
                        HStack(spacing: TodoTimelineMetrics.topBarButtonSpacing) {
                            ForEach(actions.indices, id: \.self) { index in
                                let action = actions[index]
                                let button = TimelineTopBarButton(
                                    systemName: action.systemName,
                                    assetName: action.assetName,
                                    chrome: action.usesCircularChrome ? .filled : .plain,
                                    tint: action.tint,
                                    action: action.action
                                )
                                if let label = action.accessibilityLabel {
                                    button.accessibilityLabel(Text(label))
                                } else {
                                    button
                                }
                            }
                        }
                    }
                }
            }

            if !searchActive {
                titleContent
                    .opacity(revealProgress)
                    .offset(y: titleOffsetY)
                    .scaleEffect(0.985 + (0.015 * revealProgress))
                    // Reserve each side for what actually sits there (back button
                    // left, action cluster right) instead of the larger side twice:
                    // with three actions a symmetric reserve exceeds the screen
                    // width and stretches the whole layout edge-to-edge.
                    .padding(.leading, TodoTimelineMetrics.topBarButtonFrame + 12)
                    .padding(.trailing, trailingActionReservedWidth + 12)
                    .frame(maxWidth: .infinity)
                    .allowsHitTesting(false)
            }
        }
        .frame(height: TodoTimelineMetrics.topBarRowHeight)
        .padding(.horizontal, TodoTimelineMetrics.horizontalPadding)
        .padding(.top, 2)
        .padding(.bottom, 4)
        .background(colors.background)
        // Hung off the bar as an overlay that paints outside its own bounds, so
        // rows dissolve into it instead of being guillotined by its edge. NOT
        // inside the bar's stack: that would add layout height, push every row
        // down and shift adjustedContentInset, which every collapse-progress
        // calculation on these screens is derived from.
        //
        // Off until the screen actually moves: painted at rest it would veil the
        // top of whatever sits under the bar — the hero mark on a screen at the
        // top, the first card on one that has settled — and there is nothing
        // passing under the bar for it to dissolve.
        .overlay(alignment: .bottom) {
            LinearGradient(
                colors: [colors.background, colors.background.opacity(0)],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(height: TodoTimelineMetrics.contentFadeHeight)
            .offset(y: TodoTimelineMetrics.contentFadeHeight)
            .opacity(Double(min(1, progress * 8)))
            .allowsHitTesting(false)
        }
    }
}

struct TimelineExpandedTitleRow: View {
    let title: String
    let accentColor: Color
    let collapseProgress: CGFloat
    let showsTimeOfDayIcon: Bool
    /// The screen's own glyph, shown in a tinted circle above the title.
    let mark: Image?
    /// Tint for the circle. Separate from `accentColor` because screens whose
    /// title is plain onSurface — Settings, App Version — still want a coloured
    /// mark rather than a grey disc.
    let markAccentColor: Color

    init(
        title: String,
        accentColor: Color,
        collapseProgress: CGFloat,
        showsTimeOfDayIcon: Bool = false,
        mark: Image? = nil,
        markAccentColor: Color? = nil
    ) {
        self.title = title
        self.accentColor = accentColor
        self.collapseProgress = collapseProgress
        self.showsTimeOfDayIcon = showsTimeOfDayIcon
        self.mark = mark
        self.markAccentColor = markAccentColor ?? accentColor
    }

    /// Clears out well before the title reaches the bar, so the two never
    /// occupy the same space on the way past each other.
    private var markFade: CGFloat {
        1 - TodoTimelineMetrics.progress(progress, from: 0, to: TodoTimelineMetrics.heroMarkFadeEnd)
    }

    private var progress: CGFloat {
        min(max(collapseProgress, 0), 1)
    }

    private var fadeProgress: CGFloat {
        TodoTimelineMetrics.progress(
            progress,
            from: TodoTimelineMetrics.expandedTitleFadeStart,
            to: TodoTimelineMetrics.expandedTitleFadeEnd
        )
    }

    private var titleOffsetY: CGFloat {
        -TodoTimelineMetrics.expandedTitleLiftDistance * fadeProgress
    }

    private var titleOpacity: Double {
        Double(1 - fadeProgress)
    }

    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            if let mark {
                // Fixed spacers rather than Spacer(), so the block's height is
                // exactly `titleCollapseDistance` and nothing can absorb slack
                // and drift the title off the bottom edge.
                Color.clear
                    .frame(height: TodoTimelineMetrics.heroMarkTopGap)
                heroMark(mark)
                    .frame(maxWidth: .infinity, alignment: .center)
                Color.clear
                    .frame(height: TodoTimelineMetrics.heroMarkBottomGap)
            }

            TodoTimelineTitleLabel(
                title: title,
                accentColor: accentColor,
                showsTimeOfDayIcon: showsTimeOfDayIcon
            )
                .frame(
                    maxWidth: .infinity,
                    minHeight: TodoTimelineMetrics.expandedTitleHeight,
                    maxHeight: TodoTimelineMetrics.expandedTitleHeight,
                    alignment: .bottom
                )
                .opacity(titleOpacity)
                .offset(y: titleOffsetY)
        }
        .frame(
            maxWidth: .infinity,
            minHeight: TodoTimelineMetrics.titleCollapseDistance,
            maxHeight: TodoTimelineMetrics.titleCollapseDistance,
            alignment: .bottom
        )
        .clipped()
        .padding(.bottom, TodoTimelineMetrics.settledContentGap)
        .allowsHitTesting(false)
    }

    /// A flat glyph on a flat disc reads as a utility icon. The wash is a
    /// gradient with an oversized echo of the same glyph bleeding out of the
    /// bottom-right — the motif the category tiles already use.
    private func heroMark(_ image: Image) -> some View {
        ZStack {
            image
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .frame(
                    width: TodoTimelineMetrics.heroMarkEchoGlyph,
                    height: TodoTimelineMetrics.heroMarkEchoGlyph
                )
                .foregroundStyle(markAccentColor.opacity(TodoTimelineMetrics.heroMarkEchoAlpha))
                .offset(x: 22, y: 26)

            image
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .frame(
                    width: TodoTimelineMetrics.heroMarkGlyph,
                    height: TodoTimelineMetrics.heroMarkGlyph
                )
                .foregroundStyle(markAccentColor)
        }
        .frame(width: TodoTimelineMetrics.heroMarkBox, height: TodoTimelineMetrics.heroMarkBox)
        .background(
            LinearGradient(
                colors: [
                    markAccentColor.opacity(TodoTimelineMetrics.heroMarkWashTopAlpha),
                    markAccentColor.opacity(TodoTimelineMetrics.heroMarkWashBottomAlpha),
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            ),
            in: Circle()
        )
        .clipShape(Circle())
        .opacity(Double(markFade))
        .scaleEffect(0.85 + (0.15 * markFade))
    }
}

private struct TodoTimelineTitleLabel: View {
    let title: String
    let accentColor: Color
    let showsTimeOfDayIcon: Bool

    var body: some View {
        TimelineView(.periodic(from: .now, by: 60)) { context in
            HStack(spacing: 8) {
                if showsTimeOfDayIcon {
                    Image(systemName: todoTimeOfDaySystemImage(for: context.date))
                        .font(.system(size: 26, weight: .regular))
                        .foregroundStyle(todoTimeOfDayIconColor(for: context.date))
                }

                Text(title)
                    .font(.tdayRounded(size: TodoTimelineMetrics.heroTitleSize, weight: .heavy))
                    .foregroundStyle(accentColor)
                    .lineLimit(1)
            }
            .lineLimit(1)
        }
    }
}

private struct TimelineTopBarButton: View {
    enum Chrome {
        case plain
        case filled
        case outlined
    }

    let systemName: String
    /// Preferred over `systemName` when set, so a bar can use the same drawn
    /// glyph the root feeds do rather than the SF symbol that merely resembles it.
    let assetName: String?
    let chrome: Chrome
    let tint: Color?
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    init(
        systemName: String,
        assetName: String? = nil,
        chrome: Chrome,
        tint: Color? = nil,
        action: @escaping () -> Void
    ) {
        self.systemName = systemName
        self.assetName = assetName
        self.chrome = chrome
        self.tint = tint
        self.action = action
    }

    @ViewBuilder
    private var glyph: some View {
        if let assetName {
            Image(assetName)
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .frame(width: iconSize, height: iconSize)
        } else {
            Image(systemName: systemName)
                .font(.system(size: iconSize, weight: .semibold))
        }
    }

    var body: some View {
        Button(action: action) {
            glyph
                .frame(width: TodoTimelineMetrics.topBarButtonFrame, height: TodoTimelineMetrics.topBarButtonFrame)
                .background {
                    if chrome == .filled {
                        // Fill AND hairline, both: `RootFeedHeaderCircleButton`
                        // wears the ring at this opacity, and a filled circle
                        // without it read as a different control one screen along.
                        Circle()
                            .fill(colors.surface)
                            .overlay {
                                Circle()
                                    .stroke(
                                        colors.onSurface.opacity(
                                            RootFeedHeroHeaderMetrics.barControlBorderOpacity
                                        ),
                                        lineWidth: 1
                                    )
                            }
                    } else if chrome == .outlined {
                        Circle()
                            .fill(outlinedFillColor)
                            .overlay {
                                Circle()
                                    .stroke(outlinedBorderColor, lineWidth: 1)
                            }
                    }
                }
                .contentShape(Circle())
        }
        // The shared bar-button lift, rather than a fourth copy of it: this one
        // was a shade heavier than the root feeds' and landed at a different
        // offset, which is what left the two ⋯ reading as separate controls.
        .buttonStyle(TdayToolbarButtonStyle(shadowsEnabled: chrome == .filled))
        .foregroundStyle(foregroundColor)
    }

    private var iconSize: CGFloat {
        chrome == .filled ? TodoTimelineMetrics.topBarButtonIconSize : 28
    }

    private var foregroundColor: Color {
        switch chrome {
        case .filled:
            return colors.onSurface
        case .outlined:
            return tint ?? colors.onSurface
        case .plain:
            return tint ?? Color.accentColor
        }
    }

    private var outlinedFillColor: Color {
        if let tint {
            return tint.opacity(0.12)
        }
        return colors.background
    }

    private var outlinedBorderColor: Color {
        if let tint {
            return tint.opacity(0.48)
        }
        return colors.onSurfaceVariant.opacity(0.28)
    }
}

private struct TimelineScrollOffsetTrackingRow: View {
    let onChange: (CGFloat) -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        TimelineScrollOffsetObserver(onChange: onChange)
            .frame(height: 0)
            .listRowInsets(EdgeInsets())
            .listRowBackground(colors.background)
            .listRowSeparator(.hidden)
            .allowsHitTesting(false)
    }
}

struct TimelineScrollOffsetObserver: UIViewRepresentable {
    let onChange: (CGFloat) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onChange: onChange)
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.onChange = onChange
        DispatchQueue.main.async {
            context.coordinator.attach(to: uiView)
        }
    }

    final class Coordinator {
        var onChange: (CGFloat) -> Void
        private weak var observedScrollView: UIScrollView?
        private var observation: NSKeyValueObservation?

        init(onChange: @escaping (CGFloat) -> Void) {
            self.onChange = onChange
        }

        func attach(to view: UIView) {
            guard let scrollView = view.enclosingScrollView() else {
                return
            }

            guard observedScrollView !== scrollView else {
                return
            }

            observedScrollView = scrollView
            observation = scrollView.observe(\.contentOffset, options: [.initial, .new]) { [weak self] scrollView, _ in
                let offset = max(scrollView.contentOffset.y + scrollView.adjustedContentInset.top, 0)
                self?.onChange(offset)
            }
        }
    }
}

private extension UIView {
    func enclosingScrollView() -> UIScrollView? {
        var current: UIView? = self
        while let view = current {
            if let scrollView = view as? UIScrollView {
                return scrollView
            }
            current = view.superview
        }
        return nil
    }
}

struct TimelineSectionHeader: View {
    let title: String
    let isActiveDropTarget: Bool
    let isCollapsible: Bool
    let isCollapsed: Bool
    let onTap: (() -> Void)?

    @Environment(\.tdayColors) private var colors

    init(
        title: String,
        isActiveDropTarget: Bool,
        isCollapsible: Bool = false,
        isCollapsed: Bool = false,
        onTap: (() -> Void)? = nil
    ) {
        self.title = title
        self.isActiveDropTarget = isActiveDropTarget
        self.isCollapsible = isCollapsible
        self.isCollapsed = isCollapsed
        self.onTap = onTap
    }

    var body: some View {
        let content = VStack(alignment: .leading, spacing: TodoTimelineMetrics.sectionSpacing) {
            HStack(spacing: 8) {
                Text(title)
                    .font(.tdayRounded(size: TodoTimelineMetrics.sectionTitleSize, weight: .bold))
                    .foregroundStyle(isActiveDropTarget ? colors.error : colors.onSurfaceVariant.opacity(0.78))
                    .textCase(nil)

                if isCollapsible {
                    Image(systemName: "chevron.down")
                        .font(.system(size: TodoTimelineMetrics.sectionChevronSize, weight: .semibold))
                        .foregroundStyle(colors.onSurfaceVariant.opacity(0.72))
                        .rotationEffect(.degrees(isCollapsed ? -90 : 0))
                        .animation(.easeInOut(duration: 0.18), value: isCollapsed)
                }
            }
        }
        .padding(.top, 2)
        .padding(.horizontal, TodoTimelineMetrics.horizontalPadding)
        .padding(.bottom, TodoTimelineMetrics.sectionHeaderBottomPadding)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())

        if let onTap {
            Button(action: onTap) {
                content
            }
            .buttonStyle(TimelineSectionHeaderButtonStyle())
        } else {
            content
        }
    }
}

private struct TodoDragPreview: View {
    let todo: TodoItem

    @Environment(\.tdayColors) private var colors

    var body: some View {
        let previewShape = RoundedRectangle(cornerRadius: 18, style: .continuous)

        HStack(spacing: 10) {
            Image(systemName: "circle")
                .font(.system(size: 22, weight: .regular))
                .foregroundStyle(colors.onSurfaceVariant.opacity(0.76))

            VStack(alignment: .leading, spacing: 3) {
                Text(todo.title)
                    .font(.tdayRounded(size: 16, weight: .bold))
                    .foregroundStyle(colors.onSurface)
                    .lineLimit(1)
                if let due = todo.due {
                    Text(due.formatted(.dateTime.hour().minute().locale(AppLocale.current)))
                        .font(.tdayRounded(size: 12, weight: .semibold))
                        .foregroundStyle(colors.onSurfaceVariant)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 0)

            if let priorityIcon = priorityIndicatorSymbolName(todo.priority) {
                Image(systemName: priorityIcon)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(priorityColor(todo.priority))
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .frame(width: 260, alignment: .leading)
        .background(colors.surface)
        .clipShape(previewShape)
        .overlay(
            previewShape.stroke(colors.onSurfaceVariant.opacity(0.14), lineWidth: 1)
        )
        .contentShape(previewShape)
        .compositingGroup()
        .shadow(color: Color.black.opacity(0.18), radius: 16, x: 0, y: 8)
        .opacity(0.96)
    }
}

private struct TodoDropPlaceholder: View {
    let isActive: Bool

    @Environment(\.tdayColors) private var colors

    var body: some View {
        RoundedRectangle(cornerRadius: 16, style: .continuous)
            .fill(isActive ? colors.error.opacity(0.10) : colors.surfaceVariant.opacity(0.18))
            .overlay(
                placeholderStroke
            )
            .frame(height: isActive ? 70 : 52)
            .animation(.easeInOut(duration: 0.18), value: isActive)
            .accessibilityHidden(true)
    }

    @ViewBuilder
    private var placeholderStroke: some View {
        if isActive {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(colors.error.opacity(0.72), lineWidth: 1.5)
        } else {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(
                    colors.onSurfaceVariant.opacity(0.18),
                    style: StrokeStyle(lineWidth: 1, dash: [7, 7])
                )
        }
    }
}

private struct TodoInAppDragModifier: ViewModifier {
    let enabled: Bool
    let todo: TodoItem
    let onStart: (TodoItem, CGPoint) -> Void
    let onMove: (TodoItem, CGPoint) -> Void
    let onEnd: (TodoItem, CGPoint?) -> Void
    let onCancel: () -> Void

    func body(content: Content) -> some View {
        if enabled {
            content
                .background {
                    GeometryReader { _ in
                        TodoInAppLongPressBridge(
                            enabled: enabled,
                            todo: todo,
                            onStart: onStart,
                            onMove: onMove,
                            onEnd: onEnd,
                            onCancel: onCancel
                        )
                        .allowsHitTesting(false)
                    }
                }
        } else {
            content
        }
    }
}

private struct TodoInAppLongPressBridge: UIViewRepresentable {
    let enabled: Bool
    let todo: TodoItem
    let onStart: (TodoItem, CGPoint) -> Void
    let onMove: (TodoItem, CGPoint) -> Void
    let onEnd: (TodoItem, CGPoint?) -> Void
    let onCancel: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(
            enabled: enabled,
            todo: todo,
            onStart: onStart,
            onMove: onMove,
            onEnd: onEnd,
            onCancel: onCancel
        )
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.backgroundColor = .clear
        view.isUserInteractionEnabled = false
        context.coordinator.markerView = view
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.enabled = enabled
        context.coordinator.todo = todo
        context.coordinator.onStart = onStart
        context.coordinator.onMove = onMove
        context.coordinator.onEnd = onEnd
        context.coordinator.onCancel = onCancel
        DispatchQueue.main.async {
            context.coordinator.attach(to: uiView.enclosingScrollView() ?? uiView.superview, markerView: uiView)
        }
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        coordinator.detach()
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var enabled: Bool
        var todo: TodoItem
        var onStart: (TodoItem, CGPoint) -> Void
        var onMove: (TodoItem, CGPoint) -> Void
        var onEnd: (TodoItem, CGPoint?) -> Void
        var onCancel: () -> Void

        weak var markerView: UIView?
        private weak var attachedView: UIView?
        private let recognizer: UILongPressGestureRecognizer
        private var isDragging = false

        init(
            enabled: Bool,
            todo: TodoItem,
            onStart: @escaping (TodoItem, CGPoint) -> Void,
            onMove: @escaping (TodoItem, CGPoint) -> Void,
            onEnd: @escaping (TodoItem, CGPoint?) -> Void,
            onCancel: @escaping () -> Void
        ) {
            self.enabled = enabled
            self.todo = todo
            self.onStart = onStart
            self.onMove = onMove
            self.onEnd = onEnd
            self.onCancel = onCancel
            self.recognizer = UILongPressGestureRecognizer()
            super.init()

            recognizer.minimumPressDuration = 0.22
            recognizer.allowableMovement = 24
            recognizer.cancelsTouchesInView = false
            recognizer.delaysTouchesBegan = false
            recognizer.delaysTouchesEnded = false
            recognizer.delegate = self
            recognizer.addTarget(self, action: #selector(handleLongPress(_:)))
        }

        func attach(to view: UIView?, markerView: UIView) {
            self.markerView = markerView
            guard enabled, let view else {
                detach()
                return
            }

            guard attachedView !== view else {
                return
            }

            detach()
            attachedView = view
            view.addGestureRecognizer(recognizer)
        }

        func detach() {
            if isDragging {
                isDragging = false
                onCancel()
            }
            attachedView?.removeGestureRecognizer(recognizer)
            attachedView = nil
        }

        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            guard enabled, let markerView else {
                return false
            }

            let localPoint = gestureRecognizer.location(in: markerView)
            return markerView.bounds.insetBy(dx: -6, dy: -6).contains(localPoint)
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
            guard enabled, let markerView else {
                return false
            }

            let localPoint = touch.location(in: markerView)
            return markerView.bounds.insetBy(dx: -6, dy: -6).contains(localPoint)
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }

        @objc private func handleLongPress(_ recognizer: UILongPressGestureRecognizer) {
            let location = globalLocation(for: recognizer)
            switch recognizer.state {
            case .began:
                guard enabled else {
                    return
                }
                isDragging = true
                onStart(todo, location)
            case .changed:
                guard isDragging else {
                    return
                }
                onMove(todo, location)
            case .ended:
                guard isDragging else {
                    return
                }
                isDragging = false
                onEnd(todo, location)
            case .cancelled, .failed:
                guard isDragging else {
                    return
                }
                isDragging = false
                onCancel()
            default:
                break
            }
        }

        private func globalLocation(for recognizer: UILongPressGestureRecognizer) -> CGPoint {
            guard let view = recognizer.view else {
                return .zero
            }

            return view.convert(recognizer.location(in: view), to: nil)
        }
    }
}

private struct TodoInAppDropTargetFrameModifier: ViewModifier {
    let targetID: String
    let section: TodoTimelineSection
    let enabled: Bool

    func body(content: Content) -> some View {
        content.background {
            if enabled, section.targetDate != nil {
                GeometryReader { proxy in
                    Color.clear.preference(
                        key: TodoDropTargetFramePreferenceKey.self,
                        value: [
                            targetID: TodoDropTargetFrame(
                                sectionID: section.id,
                                frame: proxy.frame(in: .global)
                            )
                        ]
                    )
                }
            }
        }
    }
}

private extension View {
    func todoInAppDropTargetFrame(
        targetID: String,
        section: TodoTimelineSection,
        enabled: Bool
    ) -> some View {
        modifier(
            TodoInAppDropTargetFrameModifier(
                targetID: targetID,
                section: section,
                enabled: enabled
            )
        )
    }
}

private struct TimelineSectionHeaderButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .brightness(configuration.isPressed ? -0.055 : 0)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

private let todoListSettingsColorKeys = [
    "PINK",
    "GOLD",
    "DEEP_BLUE",
    "CORAL",
    "TEAL",
    "SLATE",
    "BLUE",
    "PURPLE",
    "ROSE",
    "LIGHT_RED",
    "BRICK",
    "YELLOW",
    "LIME",
    "ORANGE",
    "RED",
]

private let todoListSettingsIconKeys = [
    "inbox",
    "sun",
    "calendar",
    "schedule",
    "flag",
    "check",
    "smile",
    "list",
    "bookmark",
    "key",
    "gift",
    "cake",
    "school",
    "bag",
    "edit",
    "document",
    "book",
    "work",
    "wallet",
    "money",
    "fitness",
    "run",
    "food",
    "drink",
    "health",
    "monitor",
    "music",
    "computer",
    "game",
    "headphones",
    "eco",
    "pets",
    "child",
    "family",
    "basket",
    "cart",
    "mall",
    "inventory",
    "soccer",
    "baseball",
    "basketball",
    "football",
    "tennis",
    "train",
    "flight",
    "boat",
    "car",
    "umbrella",
    "drop",
    "snow",
    "fire",
    "tools",
    "scissors",
    "architecture",
    "code",
    "idea",
    "chat",
    "alert",
    "star",
    "heart",
    "circle",
    "square",
    "triangle",
    "home",
    "city",
    "bank",
    "camera",
    "palette",
]

private struct ListDeleteConfirmationOverlay: View {
    let onCancel: () -> Void
    let onDelete: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        ZStack {
            colors.bottomSheetScrim
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture(perform: onCancel)

            TdaySheetOverlayCard {
                VStack(alignment: .leading, spacing: 22) {
                    VStack(alignment: .leading, spacing: 14) {
                        Text("Delete list?")
                            .font(.tdayRounded(.title2, weight: .black))
                            .foregroundStyle(colors.onSurface)
                            .lineLimit(1)
                            .minimumScaleFactor(0.82)

                        Text("This will delete this list, every task in it, and completed history for those tasks.")
                            .font(.tdayRounded(.body, weight: .heavy))
                            .foregroundStyle(colors.onSurfaceVariant)
                            .lineSpacing(3)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    HStack(spacing: 24) {
                        Spacer(minLength: 0)

                        Button(action: onCancel) {
                            Text("Cancel")
                                .font(.tdayRounded(.headline, weight: .heavy))
                                .foregroundStyle(colors.primary)
                                .padding(.horizontal, 4)
                                .padding(.vertical, 8)
                        }
                        .buttonStyle(.plain)

                        Button(role: .destructive, action: onDelete) {
                            Text("Delete")
                                .font(.tdayRounded(.headline, weight: .heavy))
                                .foregroundStyle(colors.error)
                                .padding(.horizontal, 4)
                                .padding(.vertical, 8)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 24)
                .padding(.top, 24)
                .padding(.bottom, 20)
                .frame(maxWidth: 330, alignment: .leading)
            }
            .padding(.horizontal, 34)
            .contentShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
            .onTapGesture {}
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .contain)
    }
}

private enum ListSettingsSheetMetrics {
    static let sheetHeight: CGFloat = 760
    static let maximumHeightFraction: CGFloat = TdaySheetMetrics.maximumScreenHeightFraction
    static let bottomContentPadding: CGFloat = 24
}

private struct ListSettingsSheet: View {
    let list: ListSummary?
    var shareText: String? = nil
    var onMembersRequest: (() -> Void)? = nil
    let onSubmit: (String, String?, String?) -> Void
    let onDeleteRequest: () -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.tdayColors) private var tdayColors

    @State private var name = ""
    @State private var color = "PINK"
    @State private var iconKey = "inbox"

    private var trimmedName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var canSave: Bool {
        !trimmedName.isEmpty
    }

    private var accentColor: Color {
        todoListAccentColor(for: color)
    }

    private var selectedSymbolName: String {
        todoListSymbolName(for: iconKey)
    }

    private var maximumSheetHeight: CGFloat {
        max(1, UIScreen.main.bounds.height * ListSettingsSheetMetrics.maximumHeightFraction)
    }

    private var stableSheetHeight: CGFloat {
        min(max(ListSettingsSheetMetrics.sheetHeight, 1), maximumSheetHeight)
    }

    var body: some View {
        VStack(spacing: 0) {
            TdaySheetHeader(
                title: L("List settings"),
                closeAccessibilityLabel: "Cancel",
                confirmAccessibilityLabel: "Save",
                isConfirmEnabled: canSave,
                onClose: { dismiss() },
                onConfirm: submit
            )

            ScrollView(showsIndicators: false) {
                VStack(spacing: 14) {
                    TdaySheetSectionTitle(text: "List")
                    TdaySheetCard {
                        VStack(spacing: 18) {
                            ZStack {
                                Circle()
                                    .fill(accentColor)
                                    .frame(width: 86, height: 86)

                                TdayListIcon(iconKey: iconKey, size: 38)
                                    .foregroundStyle(.white)
                            }

                            TextField(
                                "",
                                text: $name,
                                prompt: Text("List name")
                                    .foregroundStyle(tdayColors.onSurfaceVariant.opacity(0.78))
                            )
                            .textInputAutocapitalization(.words)
                            .autocorrectionDisabled()
                            .submitLabel(.done)
                            .onSubmit {
                                if canSave {
                                    submit()
                                }
                            }
                            .multilineTextAlignment(.center)
                            .font(.tdayRounded(size: 22, weight: .bold))
                            .foregroundStyle(accentColor)
                            .padding(.horizontal, 14)
                            .frame(maxWidth: .infinity)
                            .frame(height: 62)
                            .background(
                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .fill(tdayColors.bottomSheetControlSurface)
                            )
                        }
                        .padding(.horizontal, 18)
                        .padding(.vertical, 18)
                    }

                    TdaySheetSectionTitle(text: "Color")
                    TdaySheetCard {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(todoListSettingsColorKeys, id: \.self) { colorKey in
                                    let swatchColor = todoListAccentColor(for: colorKey)
                                    let isSelected = colorKey == color
                                    Button {
                                        color = colorKey
                                    } label: {
                                        Circle()
                                            .fill(swatchColor)
                                            .frame(width: 42, height: 42)
                                            .frame(width: 48, height: 48)
                                            .overlay {
                                                Circle()
                                                    .stroke(
                                                        isSelected ? tdayColors.onSurface.opacity(0.3) : .clear,
                                                        lineWidth: 3
                                                    )
                                                    .frame(width: 42, height: 42)
                                            }
                                    }
                                    .buttonStyle(
                                        TdayPressButtonStyle(
                                            shadowColor: Color.black,
                                            pressedShadowOpacity: 0.04,
                                            normalShadowOpacity: 0.08
                                        )
                                    )
                                    .accessibilityLabel(formattedOptionName(colorKey))
                                }
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 14)
                        }
                    }

                    TdaySheetSectionTitle(text: "Icon")
                    TdaySheetCard {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 10) {
                                ForEach(todoListSettingsIconKeys, id: \.self) { optionKey in
                                    let isSelected = optionKey == iconKey
                                    Button {
                                        iconKey = optionKey
                                    } label: {
                                        Circle()
                                            .fill(isSelected ? accentColor.opacity(0.2) : tdayColors.bottomSheetControlSurface)
                                            .frame(width: 46, height: 46)
                                            .overlay {
                                                Circle()
                                                    .stroke(
                                                        isSelected ? accentColor.opacity(0.55) : .clear,
                                                        lineWidth: 2
                                                    )
                                            }
                                            .overlay {
                                                TdayListIcon(iconKey: optionKey, size: 22)
                                                    .foregroundStyle(isSelected ? accentColor : tdayColors.onSurfaceVariant)
                                            }
                                    }
                                    .buttonStyle(
                                        TdayPressButtonStyle(
                                            shadowColor: Color.black,
                                            pressedShadowOpacity: 0.04,
                                            normalShadowOpacity: 0.08
                                        )
                                    )
                                    .accessibilityLabel(formattedOptionName(optionKey))
                                }
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 14)
                        }
                    }

                    if list != nil, shareText != nil || onMembersRequest != nil {
                        TdaySheetSectionTitle(text: L("Sharing"))
                        HStack(spacing: 10) {
                            if let onMembersRequest {
                                Button {
                                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                    onMembersRequest()
                                } label: {
                                    ListSettingsSheetActionTileLabel(
                                        systemName: "person.2",
                                        label: L("Members")
                                    )
                                }
                                .buttonStyle(
                                    TdayPressButtonStyle(
                                        shadowColor: Color.black,
                                        pressedShadowOpacity: 0.03,
                                        normalShadowOpacity: 0
                                    )
                                )
                            }
                            if let list, let shareText {
                                ShareLink(item: shareText, subject: Text(list.name)) {
                                    ListSettingsSheetActionTileLabel(
                                        systemName: "square.and.arrow.up",
                                        label: L("Share")
                                    )
                                }
                                .buttonStyle(
                                    TdayPressButtonStyle(
                                        shadowColor: Color.black,
                                        pressedShadowOpacity: 0.03,
                                        normalShadowOpacity: 0
                                    )
                                )
                            }
                        }
                    }

                    if list != nil {
                        ListSettingsSheetDeleteButton {
                            dismiss()
                            onDeleteRequest()
                        }
                    }
                }
                .padding(.horizontal, 18)
                .padding(.top, 14)
                .padding(.bottom, ListSettingsSheetMetrics.bottomContentPadding)
            }
            .scrollDismissesKeyboard(.interactively)
            .disableVerticalScrollBounce()
        }
        .frame(maxWidth: .infinity, alignment: .top)
        .background(tdayColors.bottomSheetBackground.ignoresSafeArea())
        .presentationDetents([.height(stableSheetHeight)])
        .presentationDragIndicator(.hidden)
        .presentationCornerRadius(34)
        .presentationBackground {
            tdayColors.bottomSheetBackground
                .ignoresSafeArea(.container, edges: .bottom)
        }
        .ignoresSafeArea(.keyboard, edges: .bottom)
        .task {
            name = list?.name ?? ""
            color = normalizedTodoListColorKey(list?.color)
            iconKey = normalizedTodoListIconKey(list?.iconKey)
        }
    }

    private func submit() {
        guard canSave else { return }
        onSubmit(trimmedName, color, iconKey)
        dismiss()
    }

    private func formattedOptionName(_ value: String) -> String {
        value
            .replacingOccurrences(of: "_", with: " ")
            .replacingOccurrences(of: ".", with: " ")
            .split(separator: " ")
            .map { $0.capitalized }
            .joined(separator: " ")
    }
}

/// Half-width tile used by the Sharing section of the list settings sheet —
/// "Members" (collaboration) and "Share" (external share sheet) side by side.
private struct ListSettingsSheetActionTileLabel: View {
    let systemName: String
    let label: String

    @Environment(\.tdayColors) private var colors

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: systemName)
                .font(.system(size: 20, weight: .semibold))
                .frame(width: 26, height: 26)

            Text(label)
                .font(.tdayRounded(size: 17, weight: .heavy))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .foregroundStyle(colors.onSurface)
        .padding(.horizontal, 14)
        .padding(.vertical, 14)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(colors.bottomSheetControlSurface)
        )
        .overlay {
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .stroke(colors.onSurfaceVariant.opacity(0.3), lineWidth: 1.5)
        }
    }
}


private struct ListSettingsSheetDeleteButton: View {
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(role: .destructive) {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            action()
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "trash")
                    .font(.system(size: 22, weight: .semibold))
                    .frame(width: 28, height: 28)

                Text("Delete list")
                    .font(.tdayRounded(size: 18, weight: .heavy))

                Spacer(minLength: 0)
            }
            .foregroundStyle(colors.error)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity)
            .background(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .fill(colors.error.opacity(colors.isDark ? 0.14 : 0.04))
            )
            .overlay {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(colors.error.opacity(0.45), lineWidth: 1.5)
            }
        }
        .buttonStyle(
            TdayPressButtonStyle(
                shadowColor: Color.black,
                pressedShadowOpacity: 0.03,
                normalShadowOpacity: 0
            )
        )
        .accessibilityLabel("Delete list")
    }
}

private struct TodoTimelineSection: Identifiable, Hashable {
    let id: String
    let title: String
    let items: [TodoItem]
    let isCollapsible: Bool
    let targetDate: Date?
    // Today buckets only: the hour a task is set to when dropped here (Morning 9 /
    // Afternoon 15 / Tonight 20). nil for normal date sections, which reschedule
    // by date instead.
    var targetHour: Int? = nil
}

private struct TodoRescheduleDrop: Equatable {
    let todo: TodoItem
    let targetDate: Date?
    let targetHour: Int?
}

private struct ScheduledDragModifier: ViewModifier {
    let enabled: Bool
    let todo: TodoItem
    let onDragStart: () -> Void

    @ViewBuilder
    func body(content: Content) -> some View {
        if enabled {
            content.onDrag {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                onDragStart()
                TodoTaskDragSession.shared.todo = todo
                TodoTaskDragSession.shared.handledDropSignature = nil
                return NSItemProvider(object: todo.id as NSString)
            }
        } else {
            content
        }
    }
}

private struct ScheduledTodoDropDelegate: DropDelegate {
    let section: TodoTimelineSection
    let draggedTodo: TodoItem?
    let resolveTodo: (String) -> TodoItem?
    let onMove: (TodoItem, Date) -> Void
    let canMoveTodo: (TodoItem, TodoTimelineSection) -> Bool
    let onSectionChange: (String?) -> Void

    func validateDrop(info: DropInfo) -> Bool {
        guard section.targetDate != nil,
              info.hasItemsConforming(to: todoDragContentTypes) else {
            return false
        }
        if let todo = draggedTodo ?? TodoTaskDragSession.shared.todo {
            return canMoveTodo(todo, section)
        }
        return true
    }

    func dropEntered(info: DropInfo) {
        if validateDrop(info: info) {
            onSectionChange(section.id)
        }
    }

    func dropExited(info: DropInfo) {
        onSectionChange(nil)
    }

    func dropUpdated(info: DropInfo) -> DropProposal? {
        DropProposal(operation: .move)
    }

    func performDrop(info: DropInfo) -> Bool {
        defer {
            onSectionChange(nil)
        }
        guard let todo = draggedTodo ?? TodoTaskDragSession.shared.todo,
              let targetDate = section.targetDate else {
            return performProviderDrop(info: info)
        }
        guard canMoveTodo(todo, section) else {
            return false
        }
        onMove(todo, targetDate)
        return true
    }

    private func performProviderDrop(info: DropInfo) -> Bool {
        guard let targetDate = section.targetDate,
              let provider = info.itemProviders(for: todoDragContentTypes).first else {
            return false
        }
        provider.loadObject(ofClass: NSString.self) { object, _ in
            guard let rawId = object as? NSString else {
                return
            }
            let todoId = rawId as String
            DispatchQueue.main.async {
                if let todo = resolveTodo(todoId), canMoveTodo(todo, section) {
                    onMove(todo, targetDate)
                }
            }
        }
        return true
    }
}

private extension View {
    func scheduledTodoDropTarget(
        section: TodoTimelineSection,
        draggedTodo: TodoItem?,
        resolveTodo: @escaping (String) -> TodoItem?,
        onMove: @escaping (TodoItem, Date) -> Void,
        canMoveTodo: @escaping (TodoItem, TodoTimelineSection) -> Bool,
        onSectionChange: @escaping (String?) -> Void
    ) -> some View {
        self
            .onDrop(
                of: todoDragContentTypes,
                delegate: ScheduledTodoDropDelegate(
                    section: section,
                    draggedTodo: draggedTodo,
                    resolveTodo: resolveTodo,
                    onMove: onMove,
                    canMoveTodo: canMoveTodo,
                    onSectionChange: onSectionChange
                )
            )
            .dropDestination(for: String.self) { ids, _ in
                guard let targetDate = section.targetDate else {
                    onSectionChange(nil)
                    return false
                }
                let todo = draggedTodo
                    ?? TodoTaskDragSession.shared.todo
                    ?? ids.compactMap(resolveTodo).first
                guard let todo else {
                    onSectionChange(nil)
                    return false
                }
                guard canMoveTodo(todo, section) else {
                    onSectionChange(nil)
                    return false
                }
                onSectionChange(nil)
                onMove(todo, targetDate)
                return true
            } isTargeted: { active in
                guard section.targetDate != nil else {
                    if !active {
                        onSectionChange(nil)
                    }
                    return
                }
                if active,
                   let todo = draggedTodo ?? TodoTaskDragSession.shared.todo,
                   !canMoveTodo(todo, section) {
                    onSectionChange(nil)
                    return
                }
                onSectionChange(active ? section.id : nil)
            }
    }
}

private func buildSections(
    items: [TodoItem],
    mode: TodoListMode,
    showsEmptyDropTargets: Bool = false
) -> [TodoTimelineSection] {
    let calendar = Calendar.current
    switch mode {
    case .today:
        let startOfToday = calendar.startOfDay(for: Date())
        let grouped = Dictionary(grouping: items.compactMap { item -> TodoItem? in
            item.due == nil ? nil : item
        }) { item -> String in
            let hour = calendar.component(.hour, from: item.due ?? .distantFuture)
            if hour < 12 { return "Morning" }
            if hour < 18 { return "Afternoon" }
            return "Tonight"
        }
        // Canonical drop hour per bucket — lands inside the display boundaries
        // (Morning < 12, Afternoon 12–18, Tonight ≥ 18). Matches web/Android.
        let bucketHours: [String: Int] = ["Morning": 9, "Afternoon": 15, "Tonight": 20]
        return ["Morning", "Afternoon", "Tonight"].map { key in
            return TodoTimelineSection(
                id: key,
                title: key,
                items: grouped[key, default: []].sorted(by: todoTimelineSortPrecedes),
                isCollapsible: false,
                targetDate: startOfToday,
                targetHour: bucketHours[key]
            )
        }
    case .overdue:
        let now = Date()
        let startOfToday = calendar.startOfDay(for: now)
        let overdueItems = items.filter { ($0.due ?? .distantFuture) < now }
        let grouped = Dictionary(grouping: overdueItems) { item in
            calendar.startOfDay(for: item.due ?? now)
        }

        var sections: [TodoTimelineSection] = []
        if let todaysItems = grouped[startOfToday], !todaysItems.isEmpty {
            sections.append(
                TodoTimelineSection(
                    id: "today",
                    title: L("Today"),
                    items: todaysItems.sorted(by: todoTimelineSortPrecedes),
                    isCollapsible: false,
                    targetDate: nil
                )
            )
        }

        let pastDates = grouped.keys
            .filter { $0 < startOfToday }
            .sorted(by: >)

        sections.append(
            contentsOf: pastDates.map { date in
                TodoTimelineSection(
                    id: "overdue-\(date.timeIntervalSince1970)",
                    title: date.formatted(.dateTime.weekday(.abbreviated).month(.abbreviated).day().locale(AppLocale.current)),
                    items: grouped[date]?.sorted(by: todoTimelineSortPrecedes) ?? [],
                    isCollapsible: false,
                    targetDate: nil
                )
            }
        )

        return sections
    case .scheduled:
        let startOfToday = calendar.startOfDay(for: Date())
        let grouped = Dictionary(grouping: items.filter { ($0.due ?? .distantPast) >= startOfToday }) { item in
            calendar.startOfDay(for: item.due ?? startOfToday)
        }
        return grouped.keys.sorted().map { date in
                TodoTimelineSection(
                    id: "scheduled-\(date.timeIntervalSince1970)",
                    title: scheduledSectionTitle(for: date, calendar: calendar),
                    items: grouped[date]?.sorted(by: todoTimelineSortPrecedes) ?? [],
                    isCollapsible: false,
                    targetDate: timelineRescheduleTargetDate(
                        sectionId: "scheduled-\(date.timeIntervalSince1970)",
                        calendar: calendar
                    )
                )
            }
    case .all:
        return buildFutureTimelineSections(
            items: items,
            calendar: calendar,
            placesEarlierBeforeToday: true,
            showsEmptyDropTargets: showsEmptyDropTargets
        )
    case .priority:
        return buildFutureTimelineSections(
            items: items,
            calendar: calendar,
            placesEarlierBeforeToday: true,
            showsEmptyDropTargets: showsEmptyDropTargets
        )
    case .floater:
        return buildFloaterTimelineSections(items: items)
    case .list:
        return buildFutureTimelineSections(
            items: items,
            calendar: calendar,
            placesEarlierBeforeToday: true,
            showsEmptyDropTargets: showsEmptyDropTargets
        )
    }
}

private func buildFloaterTimelineSections(items: [TodoItem]) -> [TodoTimelineSection] {
    let floaterItems = items
        .sorted(by: floaterTodoSortPrecedes)

    return [
        TodoTimelineSection(
            id: "floater-all",
            title: "",
            items: floaterItems,
            isCollapsible: false,
            targetDate: nil
        ),
    ]
}

// Fixed FLOATER ordering (pinned, priority High->Low, modified desc, id).
private func floaterTodoSortPrecedes(_ lhs: TodoItem, _ rhs: TodoItem) -> Bool {
    TaskSortEngine.precedesFloater(taskSortKey(lhs), taskSortKey(rhs))
}

private func scheduledSectionTitle(for date: Date, calendar: Calendar) -> String {
    if calendar.isDateInToday(date) {
        return L("Today")
    }
    if calendar.isDateInTomorrow(date) {
        return L("Tomorrow")
    }
    return timelineDayTitle(for: date)
}

private func buildFutureTimelineSections(
    items: [TodoItem],
    calendar: Calendar,
    placesEarlierBeforeToday: Bool,
    showsEmptyDropTargets: Bool
) -> [TodoTimelineSection] {
    let now = Date()
    let today = calendar.startOfDay(for: now)
    let datedItems = items.filter { $0.due != nil }
    let groupedByDate = Dictionary(grouping: datedItems.sorted(by: todoTimelineSortPrecedes)) { item in
        calendar.startOfDay(for: item.due ?? today)
    }
    let currentYear = calendar.component(.year, from: today)
    let currentMonth = calendar.component(.month, from: today)
    let currentMonthIndex = monthIndex(for: today, calendar: calendar)
    let horizonStart = calendar.date(byAdding: .day, value: 7, to: today) ?? today

    func daySection(for date: Date, title: String) -> TodoTimelineSection {
        let sectionId = "priority-\(date.timeIntervalSince1970)"
        return TodoTimelineSection(
            id: sectionId,
            title: title,
            items: groupedByDate[date] ?? [],
            isCollapsible: false,
            targetDate: timelineRescheduleTargetDate(sectionId: sectionId, calendar: calendar)
        )
    }

    var sections: [TodoTimelineSection] = []

    // The one rule every bucket answers to, Earlier and "Rest of <month>"
    // included: it earns a header by holding tasks, or by being somewhere the
    // drag in hand can land. A `targetDate` is what makes a bucket droppable —
    // "Rest of <month>" loses its target date when the +7 horizon rolls into the
    // next month — and an empty bucket that takes no drop is a stray header.
    func appendIfRendered(_ section: TodoTimelineSection) {
        // "earlier" is excluded from the drag-time restore on purpose: its target
        // date is yesterday, and web never rebuilds an empty Earlier at all, so
        // letting it back would make rescheduling INTO the past a thing you can
        // do on two platforms out of three.
        let isDropTarget = showsEmptyDropTargets
            && section.targetDate != nil
            && section.id != "earlier"
        guard !section.items.isEmpty || isDropTarget else {
            return
        }
        sections.append(section)
    }

    let earlierItems = groupedByDate.keys
        .filter { $0 < today }
        .sorted()
        .flatMap { groupedByDate[$0] ?? [] }

    let earlierSection = TodoTimelineSection(
        id: "earlier",
        title: L("Earlier"),
        items: earlierItems,
        isCollapsible: !earlierItems.isEmpty,
        targetDate: timelineRescheduleTargetDate(sectionId: "earlier", today: today, calendar: calendar)
    )

    if placesEarlierBeforeToday {
        appendIfRendered(earlierSection)
    }

    appendIfRendered(daySection(for: today, title: L("Today")))

    if !placesEarlierBeforeToday {
        appendIfRendered(earlierSection)
    }

    if let tomorrow = calendar.date(byAdding: .day, value: 1, to: today) {
        appendIfRendered(daySection(for: tomorrow, title: L("Tomorrow")))
    }

    for offset in 2...6 {
        guard let date = calendar.date(byAdding: .day, value: offset, to: today) else { continue }
        appendIfRendered(daySection(for: date, title: timelineDayTitle(for: date)))
    }

    let restOfCurrentMonthItems = groupedByDate.keys
        .filter { $0 >= horizonStart && monthIndex(for: $0, calendar: calendar) == currentMonthIndex }
        .sorted()
        .flatMap { groupedByDate[$0] ?? [] }

    if let currentMonthStart = calendar.date(from: DateComponents(year: currentYear, month: currentMonth, day: 1)) {
        appendIfRendered(
            TodoTimelineSection(
                id: "rest-\(currentMonthIndex)",
                title: L("Rest of %@", monthTitle(for: currentMonthStart, currentYear: currentYear, calendar: calendar)),
                items: restOfCurrentMonthItems,
                isCollapsible: false,
                targetDate: timelineRescheduleTargetDate(
                    sectionId: "rest-\(currentMonthIndex)",
                    calendar: calendar
                )
            )
        )
    }

    let futureMonthIndexes = Set(
        groupedByDate.keys
            .filter { $0 >= horizonStart }
            .map { monthIndex(for: $0, calendar: calendar) }
    )
    let minimumFinalMonthIndex = currentYear * 12 + 12
    let finalMonthIndex = max(minimumFinalMonthIndex, futureMonthIndexes.max() ?? minimumFinalMonthIndex)

    var targetYear = currentYear
    var targetMonth = currentMonth + 1

    while (targetYear * 12 + targetMonth) <= finalMonthIndex {
        guard let monthStart = calendar.date(from: DateComponents(year: targetYear, month: targetMonth, day: 1)) else {
            break
        }

        let targetMonthIndex = monthIndex(for: monthStart, calendar: calendar)
        let monthItems = groupedByDate.keys
            .filter { $0 >= horizonStart && monthIndex(for: $0, calendar: calendar) == targetMonthIndex }
            .sorted()
            .flatMap { groupedByDate[$0] ?? [] }

        appendIfRendered(
            TodoTimelineSection(
                id: "month-\(targetMonthIndex)",
                title: monthTitle(for: monthStart, currentYear: currentYear, calendar: calendar),
                items: monthItems,
                isCollapsible: false,
                targetDate: timelineRescheduleTargetDate(
                    sectionId: "month-\(targetMonthIndex)",
                    calendar: calendar
                )
            )
        )

        if targetMonth == 12 {
            targetYear += 1
            targetMonth = 1
        } else {
            targetMonth += 1
        }
    }

    return sections
}

private func timelineDayTitle(for date: Date) -> String {
    TodoTimelineFormatters.dayTitle().string(from: date)
}

private func timelineDateTimeText(_ date: Date) -> String {
    TodoTimelineFormatters.dateTime().string(from: date)
}

private func monthTitle(for date: Date, currentYear: Int, calendar: Calendar) -> String {
    let formatter: DateFormatter
    if calendar.component(.year, from: date) == currentYear {
        formatter = TodoTimelineFormatters.month()
    } else {
        formatter = TodoTimelineFormatters.monthAndYear()
    }
    return formatter.string(from: date)
}

private enum TodoTimelineFormatters {
    static func dayTitle() -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = AppLocale.current
        formatter.setLocalizedDateFormatFromTemplate("EEE MMM d")
        return formatter
    }

    static func dateTime() -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = AppLocale.current
        formatter.setLocalizedDateFormatFromTemplate("MMM d jmm")
        return formatter
    }

    static func month() -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = AppLocale.current
        formatter.setLocalizedDateFormatFromTemplate("LLLL")
        return formatter
    }

    static func monthAndYear() -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = AppLocale.current
        formatter.setLocalizedDateFormatFromTemplate("LLLL yyyy")
        return formatter
    }
}

private func monthIndex(for date: Date, calendar: Calendar) -> Int {
    let year = calendar.component(.year, from: date)
    let month = calendar.component(.month, from: date)
    return year * 12 + month
}

func priorityColor(_ priority: String) -> Color {
    if TaskPriorityDisplay.isUrgent(priority) {
        return .red
    }
    if TaskPriorityDisplay.isImportant(priority) {
        return .orange
    }
    return .blue
}

func priorityIndicatorSymbolName(_ priority: String) -> String? {
    if TaskPriorityDisplay.isImportant(priority) {
        return "flag.fill"
    }
    if TaskPriorityDisplay.isUrgent(priority) {
        return "flag.fill"
    }
    return nil
}

private func emptyTimelineMessage(for mode: TodoListMode) -> String {
    switch mode {
    case .today:
        return L("No tasks for today")
    case .overdue:
        return L("No overdue tasks")
    case .scheduled:
        return L("No scheduled tasks")
    case .all:
        return L("No tasks yet")
    case .priority:
        return L("No priority tasks")
    case .floater:
        return L("No floater tasks")
    case .list:
        return L("No tasks in this list")
    }
}

/// The empty scene's title. Floater splits on whether a list is open: the root
/// feed speaks about floaters at large, a list about its own.
private func emptyTimelineTitle(for mode: TodoListMode, isListDetail: Bool) -> String {
    if mode == .floater, isListDetail {
        return L("No floaters in this list")
    }
    return emptyTimelineMessage(for: mode)
}

/// The line under it — what to do about the emptiness, phrased per screen so an
/// empty screen still says where you are.
private func emptyTimelineDescription(for mode: TodoListMode, isListDetail: Bool) -> String {
    switch mode {
    case .today:
        return L("Add something for today and it will show up right here.")
    case .overdue:
        return L("Nothing has slipped past its due date.")
    case .scheduled:
        return L("Give a task a date and it will line up here.")
    case .all:
        return L("Everything you add shows up here, whatever its date.")
    case .priority:
        return L("Flag a task as priority and it will move up here.")
    case .floater:
        return isListDetail
            ? L("Add a floater and it will wait right here.")
            : L("Floaters have no due date — write one down and it will wait until you are ready.")
    case .list:
        return L("Add your first task and it will show up right here.")
    }
}

/// The glyph on the empty scene's badge. Every mode but Today already owns a
/// template asset; Today's watermark is an SF Symbol that follows the time of
/// day, and the badge takes an asset only — so it takes the sun web's scope
/// config gives Today at every hour.
private func emptyTimelineBadgeAssetName(for mode: TodoListMode, listIconKey: String?) -> String {
    emptyTimelineAssetName(for: mode, listIconKey: listIconKey) ?? "LucideSun"
}

/// Lucide template-asset watermark for the scheduled task home category modes, mirroring web.
/// Returns nil for modes that keep their SF Symbol watermark (today/floater/list).
private func emptyTimelineAssetName(for mode: TodoListMode, listIconKey: String?) -> String? {
    switch mode {
    case .overdue:
        return "TileOverdue"
    case .scheduled:
        return "TileScheduled"
    case .all:
        return "TileAll"
    case .priority:
        return "TilePriority"
    case .list:
        return tdayLucideListAsset(listIconKey)
    case .floater:
        if let listIconKey, !listIconKey.isEmpty {
            return tdayLucideListAsset(listIconKey)
        }
        return "LucideLeaf"
    default:
        return nil
    }
}

private func emptyTimelineSystemImage(for mode: TodoListMode, listIconKey: String?, date: Date = Date()) -> String {
    switch mode {
    case .today:
        return todoTimeOfDaySystemImage(for: date)
    case .overdue:
        return "exclamationmark.circle"
    case .scheduled:
        return "clock"
    case .all:
        return "tray.fill"
    case .priority:
        return "flag.fill"
    case .floater:
        if let listIconKey, !listIconKey.isEmpty {
            return todoListSymbolName(for: listIconKey)
        }
        return "leaf"
    case .list:
        return todoListSymbolName(for: listIconKey)
    }
}

private func todoModeAccentColor(_ mode: TodoListMode, listColorKey: String?) -> Color {
    switch mode {
    case .today:
        return todoHexColor(0x5C9FE7)
    case .overdue:
        return todoHexColor(0xDA7661)
    case .scheduled:
        return todoHexColor(0xF29F38)
    case .all:
        return todoHexColor(0x5E6878)
    case .priority:
        return todoHexColor(0xE65E52)
    case .floater:
        if let listColorKey, !listColorKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return todoListAccentColor(for: listColorKey)
        }
        return todoHexColor(0x4D8F83)
    case .list:
        return todoListAccentColor(for: listColorKey)
    }
}

func todoListAccentColor(for key: String?) -> Color {
    switch key {
    case "PINK":
        return todoHexColor(0xE05299)
    case "GOLD":
        return todoHexColor(0xE8A530)
    case "DEEP_BLUE":
        return todoHexColor(0x3C9ADD)
    case "CORAL":
        return todoHexColor(0xE6664C)
    case "TEAL":
        return todoHexColor(0x2EB8AC)
    case "SLATE", "GRAY":
        return todoHexColor(0x3E4774)
    case "BLUE":
        return todoHexColor(0x6EA8E1)
    case "PURPLE":
        return todoHexColor(0x7D67B6)
    case "ROSE":
        return todoHexColor(0xD1617D)
    case "LIGHT_RED":
        return todoHexColor(0xE06C6C)
    case "BRICK":
        return todoHexColor(0xC64C39)
    case "YELLOW":
        return todoHexColor(0xE8BA30)
    case "LIME", "GREEN":
        return todoHexColor(0x46B963)
    case "ORANGE":
        return todoHexColor(0xE28736)
    case "RED":
        return todoHexColor(0xDF3A3A)
    default:
        return todoHexColor(0xE05299)
    }
}

private func todoBlendColor(_ lhs: Color, _ rhs: Color, amount: CGFloat) -> Color {
    let lhsColor = UIColor(lhs)
    let rhsColor = UIColor(rhs)
    var lhsRed: CGFloat = 0
    var lhsGreen: CGFloat = 0
    var lhsBlue: CGFloat = 0
    var lhsAlpha: CGFloat = 0
    var rhsRed: CGFloat = 0
    var rhsGreen: CGFloat = 0
    var rhsBlue: CGFloat = 0
    var rhsAlpha: CGFloat = 0
    lhsColor.getRed(&lhsRed, green: &lhsGreen, blue: &lhsBlue, alpha: &lhsAlpha)
    rhsColor.getRed(&rhsRed, green: &rhsGreen, blue: &rhsBlue, alpha: &rhsAlpha)
    let mix = min(max(amount, 0), 1)
    return Color(
        uiColor: UIColor(
            red: lhsRed + ((rhsRed - lhsRed) * mix),
            green: lhsGreen + ((rhsGreen - lhsGreen) * mix),
            blue: lhsBlue + ((rhsBlue - lhsBlue) * mix),
            alpha: lhsAlpha + ((rhsAlpha - lhsAlpha) * mix)
        )
    )
}

private func normalizedTodoListColorKey(_ key: String?) -> String {
    switch key {
    case "GREEN":
        return "LIME"
    case "GRAY":
        return "SLATE"
    case let value? where todoListSettingsColorKeys.contains(value):
        return value
    default:
        return "PINK"
    }
}

private func normalizedTodoListIconKey(_ key: String?) -> String {
    guard let key, todoListSettingsIconKeys.contains(key) else {
        return "inbox"
    }
    return key
}

private func todoListSymbolName(for key: String?) -> String {
    switch key {
    case "sun":
        return "sun.max.fill"
    case "calendar":
        return "calendar"
    case "schedule":
        return "clock"
    case "flag":
        return "flag.fill"
    case "check":
        return "checkmark"
    case "smile":
        return "face.smiling"
    case "list":
        return "list.bullet"
    case "bookmark":
        return "bookmark.fill"
    case "key":
        return "key.fill"
    case "gift":
        return "gift.fill"
    case "cake":
        return "birthday.cake.fill"
    case "school":
        return "graduationcap.fill"
    case "bag":
        return "backpack.fill"
    case "edit":
        return "pencil"
    case "document":
        return "doc.text.fill"
    case "book":
        return "book.closed.fill"
    case "work":
        return "briefcase.fill"
    case "wallet":
        return "wallet.pass.fill"
    case "money":
        return "dollarsign.circle.fill"
    case "fitness":
        return "dumbbell.fill"
    case "run":
        return "figure.run"
    case "food":
        return "fork.knife"
    case "drink":
        return "wineglass.fill"
    case "health":
        return "cross.case.fill"
    case "monitor":
        return "display"
    case "music":
        return "music.note"
    case "computer":
        return "desktopcomputer"
    case "game":
        return "gamecontroller.fill"
    case "headphones":
        return "headphones"
    case "eco":
        return "leaf.fill"
    case "pets":
        return "pawprint.fill"
    case "child":
        return "figure.2.and.child.holdinghands"
    case "family":
        return "person.3.fill"
    case "basket":
        return "basket.fill"
    case "cart":
        return "cart.fill"
    case "mall":
        return "bag.fill"
    case "inventory":
        return "archivebox.fill"
    case "soccer":
        return "soccerball"
    case "baseball":
        return "baseball.fill"
    case "basketball":
        return "basketball.fill"
    case "football":
        return "football.fill"
    case "tennis":
        return "tennis.racket"
    case "train":
        return "tram.fill"
    case "flight":
        return "airplane"
    case "boat":
        return "ferry.fill"
    case "car":
        return "car.fill"
    case "umbrella":
        return "umbrella.fill"
    case "drop":
        return "drop.fill"
    case "snow":
        return "snowflake"
    case "fire":
        return "flame.fill"
    case "tools":
        return "hammer.fill"
    case "scissors":
        return "scissors"
    case "architecture", "bank":
        return "building.columns.fill"
    case "code":
        return "chevron.left.forwardslash.chevron.right"
    case "idea":
        return "lightbulb.fill"
    case "chat":
        return "bubble.left.fill"
    case "alert":
        return "exclamationmark.triangle.fill"
    case "star":
        return "star.fill"
    case "heart":
        return "heart.fill"
    case "circle":
        return "circle.fill"
    case "square":
        return "square.fill"
    case "triangle":
        return "triangle.fill"
    case "home":
        return "house.fill"
    case "city":
        return "building.2.fill"
    case "camera":
        return "camera.fill"
    case "palette":
        return "paintpalette.fill"
    default:
        return "tray.fill"
    }
}

private func todoHexColor(_ hex: UInt) -> Color {
    Color(
        .sRGB,
        red: Double((hex >> 16) & 0xFF) / 255,
        green: Double((hex >> 8) & 0xFF) / 255,
        blue: Double(hex & 0xFF) / 255,
        opacity: 1
    )
}
