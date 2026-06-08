import SwiftUI
import UIKit
import UniformTypeIdentifiers

private let todoDragContentTypes = [UTType.plainText.identifier, UTType.text.identifier]
private let todoTimelineDragCoordinateSpace = "todoTimelineDragCoordinateSpace"
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
    static let titleCollapseDistance: CGFloat = 64
    static let rootFeedTitleTopInset: CGFloat = 32
    static let rootFeedTitleBottomInset: CGFloat = 12
    static let timelineBottomSpacerHeight: CGFloat = 120
    static let rootFloaterBottomSpacerHeight: CGFloat = 12
    static let rootDockCollapseThreshold: CGFloat = 44
    static let topBarRowHeight: CGFloat = 56
    static let topBarButtonFrame: CGFloat = 56
    static let topBarButtonIconSize: CGFloat = 24
    static let expandedTitleHeight: CGFloat = 56
    static let expandedTitleLiftDistance: CGFloat = 14
    static let expandedTitleFadeStart: CGFloat = 0.08
    static let expandedTitleFadeEnd: CGFloat = 0.44
    static let collapsedTitleRevealDistance: CGFloat = 10
    static let collapsedTitleRevealStart: CGFloat = 0.68
    static let collapsedTitleRevealEnd: CGFloat = 1
    static let searchResultSectionExpandDelay: TimeInterval = 0.08
    static let searchResultScrollDelay: TimeInterval = 0.44
    static let searchResultScrollDuration: TimeInterval = 0.90
    static let searchResultFlashDelay: TimeInterval = 0.62
    static let searchResultPreScrollItemCount = 5

    static func smoothstep(_ value: CGFloat) -> CGFloat {
        let clamped = min(max(value, 0), 1)
        return clamped * clamped * (3 - (2 * clamped))
    }

    static func progress(_ value: CGFloat, from start: CGFloat, to end: CGFloat) -> CGFloat {
        guard end > start else { return value >= end ? 1 : 0 }
        return smoothstep((value - start) / (end - start))
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
    var lineLimit: Int = 2

    private var strikeProgress: CGFloat {
        isCompleted ? 1 : 0
    }

    var body: some View {
        Text(text)
            .font(font)
            .foregroundStyle(titleColor)
            .lineLimit(lineLimit)
            .overlay {
                GeometryReader { proxy in
                    Rectangle()
                        .fill(strikeColor)
                        .frame(width: proxy.size.width * strikeProgress, height: 1.4)
                        .position(
                            x: (proxy.size.width * strikeProgress) / 2,
                            y: proxy.size.height * 0.55
                        )
                }
                .allowsHitTesting(false)
            }
            .animation(.easeInOut(duration: 0.32), value: isCompleted)
    }
}

struct TimelineTopBarAction {
    let systemName: String
    let tint: Color?
    let usesCircularChrome: Bool
    let action: () -> Void

    init(
        systemName: String,
        tint: Color? = nil,
        usesCircularChrome: Bool = false,
        action: @escaping () -> Void
    ) {
        self.systemName = systemName
        self.tint = tint
        self.usesCircularChrome = usesCircularChrome
        self.action = action
    }
}

private struct RootFeedTitleRow: View {
    let title: String

    @Environment(\.tdayColors) private var colors

    var body: some View {
        let daytime = isTodoRootDaytime(Date())
        let iconColor = daytime
            ? Color(red: 244.0 / 255.0, green: 197.0 / 255.0, blue: 66.0 / 255.0)
            : Color(red: 168.0 / 255.0, green: 184.0 / 255.0, blue: 232.0 / 255.0)

        HStack(spacing: 8) {
            Image(systemName: daytime ? "sun.max.fill" : "moon.stars.fill")
                .font(.system(size: 26, weight: .regular))
                .foregroundStyle(iconColor)

            Text(title)
                .font(.tdayRounded(size: 32, weight: .heavy))
                .foregroundStyle(colors.onSurface)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.leading, 2)
        .frame(height: 56)
    }
}

private struct RootFeedSearchTitleRow: View {
    let title: String
    @Binding var searchExpanded: Bool
    @Binding var searchQuery: String
    var searchFieldFocused: FocusState<Bool>.Binding
    let onSearchClose: () -> Void
    let onCreateList: () -> Void
    let onOpenSettings: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        let buttonSize = TodoTimelineMetrics.topBarButtonFrame
        let buttonGap: CGFloat = 8

        GeometryReader { proxy in
            let totalWidth = proxy.size.width
            let searchWidth = searchExpanded ? max(buttonSize, totalWidth) : buttonSize
            let actionCount: CGFloat = 2
            let collapsedSearchOffset = -((buttonSize * actionCount) + (buttonGap * actionCount))
            let searchOffsetX = searchExpanded ? 0 : collapsedSearchOffset
            let daytime = isTodoRootDaytime(Date())
            let iconColor = daytime
                ? Color(red: 244.0 / 255.0, green: 197.0 / 255.0, blue: 66.0 / 255.0)
                : Color(red: 168.0 / 255.0, green: 184.0 / 255.0, blue: 232.0 / 255.0)

            ZStack(alignment: .trailing) {
                HStack(spacing: 8) {
                    Image(systemName: daytime ? "sun.max.fill" : "moon.stars.fill")
                        .font(.system(size: 26, weight: .regular))
                        .foregroundStyle(iconColor)

                    Text(title)
                        .font(.tdayRounded(size: 32, weight: .heavy))
                        .foregroundStyle(colors.onSurface)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.leading, 2)
                .opacity(searchExpanded ? 0 : 1)
                .allowsHitTesting(false)

                HStack(spacing: buttonGap) {
                    RootFeedHeaderIconButton(icon: "NavListPlus", action: onCreateList)
                        .accessibilityLabel("Create list")
                    RootFeedHeaderIconButton(icon: "NavEllipsis", action: onOpenSettings)
                        .accessibilityLabel("More")
                }
                .opacity(searchExpanded ? 0 : 1)
                .allowsHitTesting(!searchExpanded)

                ZStack {
                    Button {
                        HapticManager.buttonTap()
                        withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
                            searchExpanded = true
                        }
                    } label: {
                        Image("NavSearch")
                            .renderingMode(.template)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 22, height: 22)
                            .foregroundStyle(colors.onSurface)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                    .buttonStyle(
                        TdayToolbarButtonStyle()
                    )
                    .opacity(searchExpanded ? 0 : 1)
                    .allowsHitTesting(!searchExpanded)
                    .accessibilityLabel("Search")

                    HStack(spacing: 10) {
                        Image("NavSearch")
                            .renderingMode(.template)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 20, height: 20)
                            .foregroundStyle(colors.onSurface)
                            .frame(width: 30, height: 30)

                        TextField("", text: $searchQuery, prompt: Text("Search").foregroundStyle(colors.onSurfaceVariant))
                            .focused(searchFieldFocused)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .font(.tdayRounded(size: 18, weight: .bold))
                            .foregroundStyle(colors.onSurface)
                            .tint(colors.primary)
                            .disabled(!searchExpanded)

                        Button(action: { HapticManager.sheetDismiss(); onSearchClose() }) {
                            Image("NavClose")
                                .renderingMode(.template)
                                .resizable()
                                .scaledToFit()
                                .frame(width: 18, height: 18)
                                .foregroundStyle(colors.onSurfaceVariant.opacity(0.78))
                        }
                        .buttonStyle(
                            TdayPressButtonStyle(
                                shadowColor: Color.black,
                                pressedShadowOpacity: 0,
                                normalShadowOpacity: 0
                            )
                        )
                        .accessibilityLabel("Cancel search")
                    }
                    .padding(.horizontal, 14)
                    .opacity(searchExpanded ? 1 : 0)
                    .allowsHitTesting(searchExpanded)
                }
                .frame(width: searchWidth, height: buttonSize)
                .background(colors.surface, in: Capsule())
                .overlay(
                    Capsule()
                        .stroke(colors.onSurface.opacity(0.26), lineWidth: 1)
                )
                .offset(x: searchOffsetX)
                .zIndex(2)
                .animation(.spring(response: 0.28, dampingFraction: 0.86), value: searchExpanded)
            }
        }
        .frame(height: TodoTimelineMetrics.topBarRowHeight)
    }
}

private struct RootFeedHeaderIconButton: View {
    /// Asset-catalog name of the lucide template glyph (shared with home/web/Android).
    let icon: String
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: action) {
            Image(icon)
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .frame(width: 22, height: 22)
                .foregroundStyle(colors.onSurface)
                .frame(
                    width: TodoTimelineMetrics.topBarButtonFrame,
                    height: TodoTimelineMetrics.topBarButtonFrame
                )
                .background(colors.surface)
                .clipShape(Circle())
                .overlay {
                    Circle()
                        .stroke(colors.onSurface.opacity(0.34), lineWidth: 1)
                }
        }
        .buttonStyle(TdayToolbarButtonStyle())
    }
}

private struct FloaterSearchResultsCard: View {
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

private struct FloaterListCard: View {
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
    @FocusState private var rootFloaterSearchFieldFocused: Bool
    @State private var showingCreateTask = false
    @State private var showingCreateList = false
    @State private var editingTodo: TodoItem?
    @State private var showingSummary = false
    @State private var showingListSettings = false
    @State private var showingDeleteListConfirmation = false
    @State private var draggedTodo: TodoItem?
    @State private var inAppDrag: TodoInAppDrag?
    @State private var activeDropSectionId: String?
    @State private var dropTargetFrames: [String: TodoDropTargetFrame] = [:]
    @State private var pendingRescheduleDrop: TodoRescheduleDrop?
    @State private var collapsedSectionIDs: Set<String>
    @State private var timelineScrollOffset: CGFloat = 0
    @State private var completionPhases: [String: TodoCompletionPhase] = [:]
    @State private var flashTodoId: String?
    @State private var highlightedScrollRequestID = 0
    @State private var rootFloaterSearchExpanded = false
    @State private var rootFloaterSearchQuery = ""
    @State private var openingRootFloaterSearchResultID: String?
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

    private var groupedSections: [TodoTimelineSection] {
        buildSections(
            items: viewModel.items,
            mode: viewModel.mode,
            includeEmptyEarlierTarget: false
        )
    }

    private var floaterListRows: [(list: ListSummary, count: Int)] {
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

    private var isRootFloaterScreen: Bool {
        viewModel.mode == .floater && viewModel.listId == nil
    }

    // Root floater empty state is shown inline (in the list, above the list
    // names) to mirror the web layout, rather than as a full-screen overlay.
    private var showInlineFloaterEmpty: Bool {
        isRootFloaterScreen && viewModel.items.isEmpty && !viewModel.isLoading
    }

    private var floaterEmptyGapHeight: CGFloat {
        UIScreen.main.bounds.height * 0.42
    }

    private var isListDetailScreen: Bool {
        viewModel.mode == .list ||
            (viewModel.mode == .floater && !(viewModel.listId?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?? true))
    }

    private var floaterListByID: [String: ListSummary] {
        Dictionary(viewModel.lists.map { ($0.id, $0) }, uniquingKeysWith: { _, latest in latest })
    }

    private var normalizedRootFloaterSearchQuery: String {
        normalizedTodoSearchQuery(rootFloaterSearchQuery)
    }

    private var rootFloaterSearchResults: [TodoItem] {
        guard isRootFloaterScreen, !normalizedRootFloaterSearchQuery.isEmpty else {
            return []
        }

        return viewModel.items.filter { todo in
            todoSearchText(todo.title).contains(normalizedRootFloaterSearchQuery) ||
                (todo.description.map { todoSearchText($0).contains(normalizedRootFloaterSearchQuery) } ?? false) ||
                (todo.listId.flatMap { floaterListByID[$0]?.name }.map {
                    todoSearchText($0).contains(normalizedRootFloaterSearchQuery)
                } ?? false)
        }
        .sorted(by: floaterTodoSortPrecedes)
        .prefix(20)
        .map { $0 }
    }

    private var showRootFloaterSearchResults: Bool {
        isRootFloaterScreen && rootFloaterSearchExpanded && !normalizedRootFloaterSearchQuery.isEmpty
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

    private var titleCollapseProgress: CGFloat {
        let distance = TodoTimelineMetrics.titleCollapseDistance
        guard distance > 0 else { return 0 }
        return min(max(timelineScrollOffset / distance, 0), 1)
    }

    private var shouldCollapseRootDock: Bool {
        max(timelineScrollOffset, 0) > TodoTimelineMetrics.rootDockCollapseThreshold
    }

    private var minimalTimelineBottomSpacerHeight: CGFloat {
        isRootFloaterScreen ? TodoTimelineMetrics.rootFloaterBottomSpacerHeight : TodoTimelineMetrics.timelineBottomSpacerHeight
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
        summaryAvailable && viewModel.aiSummaryEnabled && !viewModel.items.isEmpty && !isRootFloaterScreen
    }

    private var heroTopBarActions: [TimelineTopBarAction] {
        var actions: [TimelineTopBarAction] = []

        if canSummarizeCurrentMode {
            actions.append(TimelineTopBarAction(
                systemName: "sparkles",
                usesCircularChrome: true,
                action: presentSummary
            ))
        }

        if isListDetailScreen {
            actions.append(TimelineTopBarAction(
                systemName: "ellipsis",
                usesCircularChrome: true,
                action: { showingListSettings = true }
            ))
        }

        return actions
    }

    private var modeContent: AnyView {
        if isTodayMode {
            return AnyView(todayModeContent)
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
                            await viewModel.deleteList(onOptimisticDelete: onListDeleted)
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
            onRootDockCollapsedChange(max(offset, 0) > TodoTimelineMetrics.rootDockCollapseThreshold)
        }
        .onChange(of: rootFloaterSearchExpanded, initial: true) { _, expanded in
            guard isRootFloaterScreen else {
                onRootControlsVisibleChange(true)
                return
            }
            onRootControlsVisibleChange(!expanded)
            if expanded {
                rootFloaterSearchFieldFocused = false
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.30) {
                    if rootFloaterSearchExpanded {
                        rootFloaterSearchFieldFocused = true
                    }
                }
            } else {
                rootFloaterSearchFieldFocused = false
            }
        }
        .onChange(of: createTaskRequestID) { _, requestID in
            guard requestID > 0 else { return }
            closeRootFloaterSearch()
            showingCreateTask = true
        }
        .onAppear {
            onRootControlsVisibleChange(!(isRootFloaterScreen && rootFloaterSearchExpanded))
            onRootDockCollapsedChange(shouldCollapseRootDock)
            if openCreateTaskOnAppear && !hasOpenedCreateTaskOnAppear {
                hasOpenedCreateTaskOnAppear = true
                closeRootFloaterSearch()
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
        .sheet(isPresented: $showingSummary) {
            summarySheetContent
        }
        .sheet(isPresented: $showingListSettings) {
            listSettingsSheetContent
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
        if isRootFloaterScreen {
            PullToRefreshContainer(
                isRefreshing: viewModel.isLoading,
                isEnabled: pullRefreshEnabled,
                action: {
                    await viewModel.refresh()
                }
            ) {
                watermarkedModeContent
            }
        } else {
            watermarkedModeContent
                .tdayPullToRefresh(isRefreshing: viewModel.isLoading, isEnabled: pullRefreshEnabled) {
                    await viewModel.refresh()
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
                        if viewModel.items.isEmpty, !viewModel.isLoading, !isRootFloaterScreen {
                            EmptyTaskBackgroundMessage(
                                message: emptyTimelineMessage(for: viewModel.mode)
                            )
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
            if canSummarizeCurrentMode {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: presentSummary) {
                        Image(systemName: "sparkles")
                    }
                }
            }
            if isListDetailScreen {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showingListSettings = true
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
                showsTimeOfDayIcon: viewModel.mode == .today
            )
        }
    }

    private var timelineHeroTitleCollapseProgress: CGFloat {
        usesRootFeedHeader ? 0 : titleCollapseProgress
    }

    private var timelineHeroTitleRowBase: some View {
        TimelineExpandedTitleRow(
            title: viewModel.title,
            accentColor: modeAccentColor,
            collapseProgress: timelineHeroTitleCollapseProgress,
            showsTimeOfDayIcon: viewModel.mode == .today
        )
        .background {
            TimelineScrollOffsetObserver { timelineScrollOffset = $0 }
                .frame(width: 0, height: 0)
        }
        .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
        .listRowBackground(colors.background)
        .listRowSeparator(.hidden)
    }

    private var rootFeedTitleRow: some View {
        Group {
            if isRootFloaterScreen {
                RootFeedSearchTitleRow(
                    title: viewModel.title,
                    searchExpanded: $rootFloaterSearchExpanded,
                    searchQuery: $rootFloaterSearchQuery,
                    searchFieldFocused: $rootFloaterSearchFieldFocused,
                    onSearchClose: {
                        closeRootFloaterSearch()
                    },
                    onCreateList: {
                        closeRootFloaterSearch()
                        HapticManager.buttonTap()
                        showingCreateList = true
                    },
                    onOpenSettings: {
                        closeRootFloaterSearch()
                        onOpenSettings()
                    }
                )
            } else {
                RootFeedTitleRow(title: viewModel.title)
            }
        }
            .background {
                TimelineScrollOffsetObserver { timelineScrollOffset = $0 }
                    .frame(width: 0, height: 0)
            }
            .listRowInsets(
                EdgeInsets(
                    top: TodoTimelineMetrics.rootFeedTitleTopInset,
                    leading: TodoTimelineMetrics.horizontalPadding,
                    bottom: TodoTimelineMetrics.rootFeedTitleBottomInset,
                    trailing: TodoTimelineMetrics.horizontalPadding
                )
            )
            .listRowBackground(colors.background)
            .listRowSeparator(.hidden)
    }

    @ViewBuilder
    private var timelineHeroTitleRow: some View {
        if usesRootFeedHeader {
            rootFeedTitleRow
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
                    accentColor: rootFeedTab == .floater ? .tdayFloaterGreen : .tdayTodayBlue,
                    onSelect: onRootFeedTabSelected
                )
                .padding(.leading, 18)
                .padding(.vertical, 8)
            }

            Spacer(minLength: 12)

            TaskFloatingActionButton(fillColor: modeAccentColor) {
                HapticManager.buttonTap()
                showingCreateTask = true
            }
            .padding(.trailing, 18)
            .padding(.vertical, 8)
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

    private var listSettingsSheetContent: some View {
        ListSettingsSheet(
            list: viewModel.lists.first(where: { $0.id == viewModel.listId }),
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
            pendingRescheduleDrop = TodoRescheduleDrop(todo: todo, targetDate: targetDay)
        } else {
            Task { await viewModel.moveTask(todo, toDay: targetDay, scope: .occurrence) }
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
        let targetDate = targetSectionID
            .flatMap { sectionID in groupedSections.first { $0.id == sectionID }?.targetDate }
        setActiveDropSection(nil)
        draggedTodo = nil
        inAppDrag = nil
        dropTargetFrames = [:]
        if let targetDate {
            requestReschedule(todo, to: targetDate)
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
            await viewModel.moveTask(drop.todo, toDay: drop.targetDate, scope: scope)
        }
    }

    private func closeRootFloaterSearch() {
        rootFloaterSearchFieldFocused = false
        withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
            rootFloaterSearchExpanded = false
        }
        rootFloaterSearchQuery = ""
    }

    private func openRootFloaterSearchResult(_ todo: TodoItem, using proxy: ScrollViewProxy) {
        guard openingRootFloaterSearchResultID == nil else {
            return
        }
        openingRootFloaterSearchResultID = todo.id
        closeRootFloaterSearch()

        highlightedScrollRequestID += 1
        let requestID = highlightedScrollRequestID
        DispatchQueue.main.asyncAfter(deadline: .now() + TodoTimelineMetrics.searchResultScrollDelay) {
            guard requestID == highlightedScrollRequestID else {
                return
            }
            openingRootFloaterSearchResultID = nil
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
                                enabled: viewModel.mode.supportsTaskReschedule && isDropEligibleSection
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
                                enabled: viewModel.mode.supportsTaskReschedule && isDropEligibleSection
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

    private var minimalTimelineModeContent: some View {
        ScrollViewReader { scrollProxy in
            ZStack {
                List {
                    timelineHeroTitleRow
                        .id(todoTimelineScrollTopID)

                    if showRootFloaterSearchResults {
                        FloaterSearchResultsCard(
                            todos: rootFloaterSearchResults,
                            listsByID: floaterListByID,
                            onOpenTodo: { todo in
                                openRootFloaterSearchResult(todo, using: scrollProxy)
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
                        minimalTimelineSection(
                            section,
                            sectionIndex: index,
                            sections: groupedSections,
                            isFirstSection: index == 0
                        )
                    }

                    if showInlineFloaterEmpty {
                        Section {
                            Text(emptyTimelineMessage(for: viewModel.mode))
                                .font(.tdayRounded(size: 28, weight: .bold))
                                .foregroundStyle(colors.onSurfaceVariant.opacity(0.66))
                                .multilineTextAlignment(.center)
                                .lineLimit(2)
                                .minimumScaleFactor(0.82)
                                .frame(maxWidth: .infinity, minHeight: floaterEmptyGapHeight, alignment: .center)
                                .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
                                .listRowBackground(colors.background)
                                .listRowSeparator(.hidden)
                        }
                    }

                    if !floaterListRows.isEmpty {
                        Section {
                            ForEach(floaterListRows, id: \.list.id) { row in
                                FloaterListCard(
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
                .environment(\.defaultMinListRowHeight, 1)
                .disableVerticalScrollBounce(!pullRefreshEnabled)
                .animation(todoDropPlaceholderAnimation, value: activeDropSectionId)
                .animation(.easeInOut(duration: 0.22), value: timelineItemAnimationKey)

            }
            .onAppear {
                scrollToHighlightedTodo(using: scrollProxy)
            }
            .onChange(of: highlightedTodoId) {
                scrollToHighlightedTodo(using: scrollProxy)
            }
            .onChange(of: viewModel.items) {
                scrollToHighlightedTodo(using: scrollProxy)
            }
            .onChange(of: scrollToTopRequestID) { _, requestID in
                guard requestID > 0, isRootFloaterScreen else { return }
                closeRootFloaterSearch()
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
            if let description = todo.description, !description.isEmpty {
                Text(description)
                    .font(.tdayRounded(size: 12, weight: .semibold))
                    .foregroundStyle(colors.onSurfaceVariant)
                    .lineLimit(2)
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
            enabled: !isCompleting,
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
                    enabled: viewModel.mode.supportsTaskReschedule,
                    todo: todo,
                    onStart: beginInAppDrag,
                    onMove: updateInAppDrag,
                    onEnd: finishInAppDrag,
                    onCancel: cancelInAppDrag
                )
            )
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
            HStack(alignment: .center, spacing: 12) {
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
                }
            }
            .padding(.vertical, TodoTimelineMetrics.minimalRowVerticalPadding)
            .contentShape(Rectangle())
        }
        .opacity(isFading ? 0 : (draggedTodo?.id == todo.id ? 0.7 : 1))
        .scaleEffect(isFading ? 0.985 : 1, anchor: .center)
        .offset(y: isFading ? -10 : 0)
        .animation(.easeInOut(duration: 0.26), value: isFading)
        .allowsHitTesting(!isCompleting)
        .transition(.opacity.combined(with: .scale(scale: 0.985)))
        .modifier(TimelineTaskFlashHighlight(active: flashHighlight))
        .todoTrailingSwipeActions(
            rowID: todo.id,
            openRowID: $openSwipeTaskID,
            enabled: !isCompleting,
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
                enabled: viewModel.mode.supportsTaskReschedule,
                todo: todo,
                onStart: beginInAppDrag,
                onMove: updateInAppDrag,
                onEnd: finishInAppDrag,
                onCancel: cancelInAppDrag
            )
        )
    }

    private func completeTodoWithoutReflow(_ todo: TodoItem) {
        guard completionPhases[todo.id] == nil else {
            return
        }
        if openSwipeTaskID == todo.id {
            openSwipeTaskID = nil
        }
        HapticManager.taskCompleted()
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
        let isCollapsed = canCollapseSection && collapsedSectionIDs.contains(section.id)
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
                        onMove: { todo, targetDate in
                            requestReschedule(todo, to: targetDate)
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
                            enabled: viewModel.mode.supportsTaskReschedule && isDropEligibleSection
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
                    enabled: viewModel.mode.supportsTaskReschedule && isDropEligibleSection
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
        titleRevealDistance: CGFloat = TodoTimelineMetrics.collapsedTitleRevealDistance
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
        CGFloat(max(1, actions.count)) * TodoTimelineMetrics.topBarButtonFrame
    }

    var body: some View {
        ZStack {
            HStack(spacing: 0) {
                TimelineTopBarButton(systemName: "chevron.left", chrome: .filled, action: onBack)
                Spacer(minLength: 0)
                if actions.isEmpty {
                    Color.clear
                        .frame(width: TodoTimelineMetrics.topBarButtonFrame, height: TodoTimelineMetrics.topBarButtonFrame)
                } else {
                    HStack(spacing: 0) {
                        ForEach(actions.indices, id: \.self) { index in
                            let action = actions[index]
                            TimelineTopBarButton(
                                systemName: action.systemName,
                                chrome: action.usesCircularChrome ? .outlined : .plain,
                                tint: action.tint,
                                action: action.action
                            )
                        }
                    }
                }
            }

            titleContent
                .opacity(revealProgress)
                .offset(y: titleOffsetY)
                .scaleEffect(0.985 + (0.015 * revealProgress))
                .padding(.horizontal, max(TodoTimelineMetrics.topBarButtonFrame, trailingActionReservedWidth) + 12)
                .frame(maxWidth: .infinity)
                .allowsHitTesting(false)
        }
        .frame(height: TodoTimelineMetrics.topBarRowHeight)
        .padding(.horizontal, TodoTimelineMetrics.horizontalPadding)
        .padding(.top, 2)
        .padding(.bottom, 4)
        .background(colors.background)
    }
}

struct TimelineExpandedTitleRow: View {
    let title: String
    let accentColor: Color
    let collapseProgress: CGFloat
    let showsTimeOfDayIcon: Bool

    init(
        title: String,
        accentColor: Color,
        collapseProgress: CGFloat,
        showsTimeOfDayIcon: Bool = false
    ) {
        self.title = title
        self.accentColor = accentColor
        self.collapseProgress = collapseProgress
        self.showsTimeOfDayIcon = showsTimeOfDayIcon
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
        ZStack(alignment: .bottomLeading) {
            TodoTimelineTitleLabel(
                title: title,
                accentColor: accentColor,
                showsTimeOfDayIcon: showsTimeOfDayIcon
            )
                .frame(
                    maxWidth: .infinity,
                    minHeight: TodoTimelineMetrics.expandedTitleHeight,
                    maxHeight: TodoTimelineMetrics.expandedTitleHeight,
                    alignment: .bottomLeading
                )
                .opacity(titleOpacity)
                .offset(y: titleOffsetY)
        }
        .frame(
            maxWidth: .infinity,
            minHeight: TodoTimelineMetrics.titleCollapseDistance,
            maxHeight: TodoTimelineMetrics.titleCollapseDistance,
            alignment: .bottomLeading
        )
        .clipped()
        .allowsHitTesting(false)
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
    let chrome: Chrome
    let tint: Color?
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    init(systemName: String, chrome: Chrome, tint: Color? = nil, action: @escaping () -> Void) {
        self.systemName = systemName
        self.chrome = chrome
        self.tint = tint
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: iconSize, weight: .semibold))
                .frame(width: TodoTimelineMetrics.topBarButtonFrame, height: TodoTimelineMetrics.topBarButtonFrame)
                .background {
                    if chrome == .filled {
                        Circle()
                            .fill(colors.surface)
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
        .buttonStyle(TimelineTopBarButtonStyle(isFilled: chrome == .filled))
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

private struct TimelineTopBarButtonStyle: ButtonStyle {
    let isFilled: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .tdayRippleEffect(isPressed: configuration.isPressed)
            .scaleEffect(configuration.isPressed ? 0.95 : 1)
            .offset(y: configuration.isPressed ? 1 : 0)
            .shadow(
                color: Color.black.opacity(shadowOpacity(isPressed: configuration.isPressed)),
                radius: shadowRadius(isPressed: configuration.isPressed),
                x: 0,
                y: shadowOffsetY(isPressed: configuration.isPressed)
            )
            .animation(.easeOut(duration: 0.14), value: configuration.isPressed)
    }

    private func shadowOpacity(isPressed: Bool) -> Double {
        guard isFilled else {
            return 0
        }

        return isPressed ? 0.04 : 0.08
    }

    private func shadowRadius(isPressed: Bool) -> CGFloat {
        guard isFilled else {
            return 0
        }

        return isPressed ? 3 : 7
    }

    private func shadowOffsetY(isPressed: Bool) -> CGFloat {
        guard isFilled else {
            return 0
        }

        return isPressed ? 1 : 3
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

                    if list != nil {
                        ListSettingsSheetDeleteButton {
                            dismiss()
                            onDeleteRequest()
                        }
                        .padding(.top, 2)
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
}

private struct TodoRescheduleDrop: Equatable {
    let todo: TodoItem
    let targetDate: Date
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
    includeEmptyEarlierTarget: Bool = false
) -> [TodoTimelineSection] {
    let calendar = Calendar.current
    switch mode {
    case .today:
        let grouped = Dictionary(grouping: items.compactMap { item -> TodoItem? in
            item.due == nil ? nil : item
        }) { item -> String in
            let hour = calendar.component(.hour, from: item.due ?? .distantFuture)
            if hour < 12 { return "Morning" }
            if hour < 18 { return "Afternoon" }
            return "Tonight"
        }
        return ["Morning", "Afternoon", "Tonight"].map { key in
            return TodoTimelineSection(
                id: key,
                title: key,
                items: grouped[key, default: []].sorted(by: todoTimelineSortPrecedes),
                isCollapsible: false,
                targetDate: nil
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
            includeEmptyEarlierTarget: includeEmptyEarlierTarget
        )
    case .priority:
        return buildFutureTimelineSections(
            items: items,
            calendar: calendar,
            placesEarlierBeforeToday: true,
            includeEmptyEarlierTarget: includeEmptyEarlierTarget
        )
    case .floater:
        return buildFloaterTimelineSections(items: items)
    case .list:
        return buildFutureTimelineSections(
            items: items,
            calendar: calendar,
            placesEarlierBeforeToday: true,
            includeEmptyEarlierTarget: includeEmptyEarlierTarget
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

private func floaterTodoSortPrecedes(_ lhs: TodoItem, _ rhs: TodoItem) -> Bool {
    if lhs.pinned != rhs.pinned {
        return lhs.pinned && !rhs.pinned
    }
    let lhsPriority = floaterPriorityRank(lhs.priority)
    let rhsPriority = floaterPriorityRank(rhs.priority)
    if lhsPriority != rhsPriority {
        return lhsPriority < rhsPriority
    }
    // Same priority — sort by updatedAt (newest first), then title
    if let lhsUpdated = lhs.updatedAt, let rhsUpdated = rhs.updatedAt, lhsUpdated != rhsUpdated {
        return lhsUpdated > rhsUpdated
    }
    return lhs.title.localizedCaseInsensitiveCompare(rhs.title) == .orderedAscending
}

private func floaterPriorityRank(_ priority: String) -> Int {
    if TaskPriorityDisplay.isUrgent(priority) {
        return 0
    }
    if TaskPriorityDisplay.isImportant(priority) {
        return 1
    }
    return 2
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
    includeEmptyEarlierTarget: Bool
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

    let earlierItems = groupedByDate.keys
        .filter { $0 < today }
        .sorted()
        .flatMap { groupedByDate[$0] ?? [] }

    let earlierSection: TodoTimelineSection?
    if !earlierItems.isEmpty || includeEmptyEarlierTarget {
        earlierSection = TodoTimelineSection(
            id: "earlier",
            title: L("Earlier"),
            items: earlierItems,
            isCollapsible: !earlierItems.isEmpty,
            targetDate: timelineRescheduleTargetDate(sectionId: "earlier", today: today, calendar: calendar)
        )
    } else {
        earlierSection = nil
    }

    if placesEarlierBeforeToday, let earlierSection {
        sections.append(earlierSection)
    }

    sections.append(daySection(for: today, title: L("Today")))

    if !placesEarlierBeforeToday, let earlierSection {
        sections.append(earlierSection)
    }

    if let tomorrow = calendar.date(byAdding: .day, value: 1, to: today) {
        sections.append(daySection(for: tomorrow, title: L("Tomorrow")))
    }

    for offset in 2...6 {
        guard let date = calendar.date(byAdding: .day, value: offset, to: today) else { continue }
        sections.append(daySection(for: date, title: timelineDayTitle(for: date)))
    }

    let restOfCurrentMonthItems = groupedByDate.keys
        .filter { $0 >= horizonStart && monthIndex(for: $0, calendar: calendar) == currentMonthIndex }
        .sorted()
        .flatMap { groupedByDate[$0] ?? [] }

    if let currentMonthStart = calendar.date(from: DateComponents(year: currentYear, month: currentMonth, day: 1)) {
        sections.append(
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

        sections.append(
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

/// Lucide template-asset watermark for the home-category modes, mirroring web.
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
