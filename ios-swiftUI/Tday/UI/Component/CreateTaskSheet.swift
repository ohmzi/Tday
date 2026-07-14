import SwiftUI
import UIKit

private enum CreateTaskSheetMetrics {
    static let scheduledSheetHeight: CGFloat = 660
    static let floaterSheetHeight: CGFloat = 430
    static let maximumHeightFraction: CGFloat = TdaySheetMetrics.maximumScreenHeightFraction
    static let formSpacing: CGFloat = 10
    static let bottomContentPadding: CGFloat = 12
    static let textFieldVerticalPadding: CGFloat = 12
    static let textFieldMinHeight: CGFloat = 52
    static let rowVerticalPadding: CGFloat = 11
    static let scheduledRowMinHeight: CGFloat = 64
}

private enum CreateTaskSheetInputField: Hashable {
    case title
    case notes
}

struct CreateTaskSheet: View {
    let lists: [ListSummary]
    let titleText: String
    let submitText: String
    let initialPayload: CreateTaskPayload?
    let defaultScheduled: Bool
    let showScheduleControls: Bool
    let onParseTaskTitleNlp: ((String, Int64) async -> TodoTitleNlpResponse?)?
    let onDismiss: () -> Void
    let onSubmit: (CreateTaskPayload) async -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.tdayColors) private var colors

    @State private var title = ""
    @State private var notes = ""
    @State private var priority = TaskPriorityDisplay.normalValue
    @State private var selectedListID: String?
    @State private var dueDate = Date().addingTimeInterval(60 * 60)
    @State private var scheduleEnabled = true
    @State private var repeatRule: String?
    @State private var isSubmitting = false
    @State private var parserTask: Task<Void, Never>?
    // The detected date phrase (e.g. "July 29 at 8pm"). Stays visible & highlighted
    // in the title field as you type; stripped from the saved title on submit.
    @State private var nlpMatchedText: String?
    // The parser's cleaned title and the exact title it was computed from, so submit
    // strips the precise matched span rather than the first range(of:) occurrence.
    @State private var nlpCleanTitle: String?
    @State private var nlpSourceTitle: String?
    // User-intent flags so the debounced NLP autofill doesn't fight explicit choices:
    // don't re-enable schedule the user turned off, don't overwrite a hand-picked due.
    @State private var userTurnedScheduleOff = false
    @State private var userPickedDueDate = false
    @State private var activeSelector: CreateTaskSheetSelector?
    @FocusState private var focusedInputField: CreateTaskSheetInputField?

    private let priorityOptions = TaskPriorityDisplay.options
    private var repeatOptions: [(label: String, value: String?)] {
        [
            (L("No repeat"), nil),
            (L("Daily"), "RRULE:FREQ=DAILY;INTERVAL=1"),
            (L("Weekly"), "RRULE:FREQ=WEEKLY;INTERVAL=1"),
            (L("Weekdays"), "RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR"),
            (L("Monthly"), "RRULE:FREQ=MONTHLY;INTERVAL=1"),
            (L("Yearly"), "RRULE:FREQ=YEARLY;INTERVAL=1"),
        ]
    }

    private var selectedListName: String {
        guard let selectedListID,
              let list = lists.first(where: { $0.id == selectedListID }) else {
            return L("No list")
        }
        return list.name
    }

    private var selectedRepeatLabel: String {
        guard scheduleEnabled else {
            return L("No repeat")
        }
        return repeatOptions.first(where: { $0.value == repeatRule })?.label ?? L("No repeat")
    }

    private var maximumSheetHeight: CGFloat {
        max(1, UIScreen.main.bounds.height * CreateTaskSheetMetrics.maximumHeightFraction)
    }

    private var sheetHeight: CGFloat {
        min(
            max(
                showScheduleControls ? CreateTaskSheetMetrics.scheduledSheetHeight : CreateTaskSheetMetrics.floaterSheetHeight,
                1
            ),
            maximumSheetHeight
        )
    }

    init(
        lists: [ListSummary],
        titleText: String,
        submitText: String,
        initialPayload: CreateTaskPayload?,
        defaultScheduled: Bool = true,
        showScheduleControls: Bool = true,
        onParseTaskTitleNlp: ((String, Int64) async -> TodoTitleNlpResponse?)?,
        onDismiss: @escaping () -> Void,
        onSubmit: @escaping (CreateTaskPayload) async -> Void
    ) {
        self.lists = lists
        self.titleText = titleText
        self.submitText = submitText
        self.initialPayload = initialPayload
        self.defaultScheduled = defaultScheduled
        self.showScheduleControls = showScheduleControls
        self.onParseTaskTitleNlp = onParseTaskTitleNlp
        self.onDismiss = onDismiss
        self.onSubmit = onSubmit
    }

    init(
        title: String,
        lists: [ListSummary],
        initialPayload: CreateTaskPayload?,
        onSave: @escaping (CreateTaskPayload) -> Void,
        onParseTaskTitleNlp: ((String, Int64) async -> TodoTitleNlpResponse?)?
    ) {
        self.init(
            lists: lists,
            titleText: title,
            submitText: L("Save"),
            initialPayload: initialPayload,
            showScheduleControls: true,
            onParseTaskTitleNlp: onParseTaskTitleNlp,
            onDismiss: {},
            onSubmit: { payload in
                onSave(payload)
            }
        )
    }

    var body: some View {
        VStack(spacing: 0) {
            TdaySheetHeader(
                title: titleText,
                closeAccessibilityLabel: L("Cancel"),
                confirmAccessibilityLabel: submitText,
                isConfirmEnabled: !isSubmitting && !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                onClose: {
                    onDismiss()
                    dismiss()
                },
                onConfirm: {
                    Task {
                        await submit()
                    }
                }
            )

            formContent
        }
        .frame(maxWidth: .infinity, minHeight: sheetHeight, maxHeight: sheetHeight, alignment: .top)
        .background(colors.bottomSheetBackground)
        .clipShape(
            UnevenRoundedRectangle(
                topLeadingRadius: TdaySheetMetrics.sheetCornerRadius,
                bottomLeadingRadius: 0,
                bottomTrailingRadius: 0,
                topTrailingRadius: TdaySheetMetrics.sheetCornerRadius,
                style: .continuous
            )
        )
        .overlay {
            if let activeSelector {
                selectorOverlay(for: activeSelector)
            }
        }
        .task {
            hydrateFromInitialPayload()
        }
        .onChange(of: title) { _, _ in
            scheduleNlpParse()
        }
        .onChange(of: activeSelector) { _, selector in
            if selector != nil {
                focusedInputField = nil
            }
        }
        .onChange(of: scheduleEnabled) { _, isEnabled in
            if !isEnabled {
                repeatRule = nil
                if activeSelector == .date || activeSelector == .time || activeSelector == .recurrence {
                    activeSelector = nil
                }
            }
        }
    }

    private var formContent: some View {
        VStack(spacing: CreateTaskSheetMetrics.formSpacing) {
            CreateTaskSheetTextCard(
                title: $title,
                notes: $notes,
                titleHighlight: nlpMatchedText,
                focusedInputField: $focusedInputField
            )

            if showScheduleControls {
                TdaySheetSectionTitle(text: "Schedule")
                TdaySheetCard {
                    CreateTaskSheetScheduleToggleRow(
                        isOn: Binding(
                            get: { scheduleEnabled },
                            set: { newValue in
                                scheduleEnabled = newValue
                                userTurnedScheduleOff = !newValue
                            }
                        )
                    )

                    if scheduleEnabled {
                        TdaySheetDivider()

                        CreateTaskSheetDueRow(
                            dueDate: $dueDate,
                            onDateTap: { activeSelector = .date },
                            onTimeTap: { activeSelector = .time }
                        )
                        .transition(.opacity.combined(with: .move(edge: .top)))
                    }
                }
            }

            TdaySheetSectionTitle(text: "Details")
            TdaySheetCard {
                CreateTaskSheetSelectorTriggerRow(
                    iconName: "LucideList",
                    title: "List",
                    value: selectedListName,
                    valueLeading: lists.first(where: { $0.id == selectedListID }).map { list in
                        AnyView(
                            TdayListIcon(iconKey: list.iconKey, size: 16)
                                .foregroundStyle(createTaskSheetListSwatchColor(list.color))
                        )
                    },
                    onTap: { activeSelector = .list }
                )

                TdaySheetDivider()

                CreateTaskSheetSelectorTriggerRow(
                    iconName: "LucideFlagFilled",
                    title: "Priority",
                    value: TaskPriorityDisplay.label(for: priority),
                    valueLeading: AnyView(
                        Image("LucideFlagFilled")
                            .renderingMode(.template)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 16, height: 16)
                            .foregroundStyle(createTaskSheetPrioritySwatchColor(priority))
                    ),
                    onTap: { activeSelector = .priority }
                )

                if showScheduleControls {
                    TdaySheetDivider()

                    CreateTaskSheetSelectorTriggerRow(
                        iconName: "LucideRepeat",
                        title: "Repeat",
                        value: selectedRepeatLabel,
                        isEnabled: scheduleEnabled,
                        titleTrailing: AnyView(
                            GuideHelpLink(topicId: GuideTopicId.recurrencePresets)
                        ),
                        onTap: {
                            guard scheduleEnabled else { return }
                            activeSelector = .recurrence
                        }
                    )
                }
            }
        }
        .padding(.horizontal, 18)
        .padding(.bottom, CreateTaskSheetMetrics.bottomContentPadding)
    }

    private func hydrateFromInitialPayload() {
        guard let initialPayload else {
            scheduleEnabled = showScheduleControls && defaultScheduled
            repeatRule = scheduleEnabled ? repeatRule : nil
            return
        }
        title = initialPayload.title
        notes = initialPayload.description ?? ""
        priority = TaskPriorityDisplay.canonicalValue(initialPayload.priority)
        selectedListID = initialPayload.listId
        if showScheduleControls, let due = initialPayload.due {
            dueDate = due
            scheduleEnabled = true
            repeatRule = initialPayload.rrule
        } else {
            scheduleEnabled = false
            repeatRule = nil
        }
    }

    private func scheduleNlpParse() {
        guard showScheduleControls, let onParseTaskTitleNlp else {
            return
        }
        parserTask?.cancel()
        parserTask = Task {
            try? await Task.sleep(for: .milliseconds(260))
            guard !Task.isCancelled else {
                return
            }
            let source = title
            let parsed = await onParseTaskTitleNlp(source, dueDate.epochMilliseconds)
            await MainActor.run {
                // Keep the full typed text in the field (the phrase stays visible &
                // highlighted); captured phrases are stripped from the saved title at
                // submit via nlpCleanTitle.
                guard let parsed else {
                    nlpMatchedText = nil
                    nlpCleanTitle = nil
                    nlpSourceTitle = nil
                    return
                }
                // Date span highlight only when a date phrase actually matched.
                if let matched = parsed.matchedText, !matched.isEmpty {
                    nlpMatchedText = matched
                } else {
                    nlpMatchedText = nil
                }
                nlpCleanTitle = parsed.cleanTitle
                nlpSourceTitle = source
                // Respect explicit user choices: don't clobber a hand-picked date, and
                // don't re-enable schedule the user just turned off.
                if let dueEpochMs = parsed.dueEpochMs {
                    if !userPickedDueDate {
                        dueDate = Date(epochMilliseconds: dueEpochMs)
                    }
                    if !userTurnedScheduleOff {
                        scheduleEnabled = true
                    }
                }
                // A captured recurrence needs a schedule to start from.
                if let parsedRrule = parsed.rrule {
                    if !userTurnedScheduleOff {
                        scheduleEnabled = true
                    }
                    repeatRule = parsedRrule
                }
                if let parsedPriority = parsed.priority {
                    priority = TaskPriorityDisplay.canonicalValue(parsedPriority)
                }
            }
        }
    }

    /// Due-date binding for the user-facing pickers; flags that the user set the date
    /// so the debounced NLP autofill won't overwrite their choice.
    private var userPickedDueDateBinding: Binding<Date> {
        Binding(
            get: { dueDate },
            set: { newValue in
                dueDate = newValue
                userPickedDueDate = true
            }
        )
    }

    /// The saved title: the typed text with the highlighted date phrase removed
    /// (it stays visible in the field but isn't part of the task name) — same as web.
    private func effectiveTitle() -> String {
        // Prefer the parser's cleaned title when the field still holds the exact text it
        // parsed: that removes the precise matched span. The range(of:) fallback below
        // strips the FIRST occurrence, which mangles titles where the phrase repeats.
        if let clean = nlpCleanTitle, let source = nlpSourceTitle, source == title {
            return clean
        }
        guard let matched = nlpMatchedText, !matched.isEmpty,
              let range = title.range(of: matched) else {
            return title.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        var stripped = title
        stripped.removeSubrange(range)
        return stripped
            .replacingOccurrences(of: "\\s{2,}", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func submit() async {
        HapticManager.sheetConfirm()
        isSubmitting = true
        let payload = CreateTaskPayload(
            title: effectiveTitle(),
            description: notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : notes.trimmingCharacters(in: .whitespacesAndNewlines),
            priority: TaskPriorityDisplay.canonicalValue(priority),
            due: showScheduleControls && scheduleEnabled ? dueDate : nil,
            rrule: showScheduleControls && scheduleEnabled ? repeatRule : nil,
            listId: selectedListID
        )
        await onSubmit(payload)
        isSubmitting = false
        onDismiss()
        dismiss()
    }

    @ViewBuilder
    private func selectorOverlay(for selector: CreateTaskSheetSelector) -> some View {
        ZStack {
            colors.bottomSheetScrim
                .ignoresSafeArea()
                .onTapGesture {
                    activeSelector = nil
                }

            TdayCenteredSelectorCard(title: selector.title) {
                switch selector {
                case .list:
                    TdayCenteredSelectorRow(
                        title: L("No list"),
                        swatchColor: colors.onSurfaceVariant.opacity(0.35),
                        selected: selectedListID == nil
                    ) {
                        selectedListID = nil
                        activeSelector = nil
                    }

                    ForEach(lists) { list in
                        TdaySheetDivider(horizontalPadding: 20, opacity: 0.16)
                        TdayCenteredSelectorRow(
                            title: list.name,
                            swatchColor: createTaskSheetListSwatchColor(list.color),
                            selected: selectedListID == list.id
                        ) {
                            selectedListID = list.id
                            activeSelector = nil
                        }
                    }

                case .priority:
                    ForEach(Array(priorityOptions.enumerated()), id: \.element.value) { index, option in
                        if index > 0 {
                            TdaySheetDivider(horizontalPadding: 20, opacity: 0.16)
                        }
                        TdayCenteredSelectorRow(
                            title: option.label,
                            swatchColor: createTaskSheetPrioritySwatchColor(option.value),
                            selected: TaskPriorityDisplay.canonicalValue(priority) == option.value
                        ) {
                            priority = option.value
                            activeSelector = nil
                        }
                    }

                case .recurrence:
                    ForEach(Array(repeatOptions.enumerated()), id: \.element.label) { index, option in
                        if index > 0 {
                            TdaySheetDivider(horizontalPadding: 20, opacity: 0.16)
                        }
                        TdayCenteredSelectorRow(
                            title: option.label,
                            swatchColor: createTaskSheetRepeatSwatchColor(option.value),
                            selected: repeatRule == option.value
                        ) {
                            repeatRule = option.value
                            activeSelector = nil
                        }
                    }

                case .date:
                    CreateTaskSheetDateSelectorContent(dueDate: userPickedDueDateBinding) {
                        activeSelector = nil
                    }

                case .time:
                    CreateTaskSheetTimeSelectorContent(dueDate: userPickedDueDateBinding) {
                        activeSelector = nil
                    }
                }
            }
            .padding(.horizontal, selector.horizontalPadding)
        }
    }
}

extension View {
    func createTaskSheet<SheetContent: View>(
        isPresented: Binding<Bool>,
        @ViewBuilder content: @escaping () -> SheetContent
    ) -> some View {
        tdayBottomSheetPresentation(isPresented: isPresented) {
            content()
        }
    }

    func createTaskSheet<Item: Identifiable, SheetContent: View>(
        item: Binding<Item?>,
        @ViewBuilder content: @escaping (Item) -> SheetContent
    ) -> some View {
        tdayBottomSheetPresentation(item: item) { item in
            content(item)
        }
    }
}

private enum CreateTaskSheetSelector: String, Identifiable {
    case list
    case priority
    case recurrence
    case date
    case time

    var id: String { rawValue }

    var title: String {
        switch self {
        case .list:
            return L("List")
        case .priority:
            return L("Priority")
        case .recurrence:
            return L("Repeat")
        case .date:
            return L("Due date")
        case .time:
            return L("Due time")
        }
    }

    var horizontalPadding: CGFloat {
        switch self {
        case .date, .time:
            return 24
        case .list, .priority, .recurrence:
            return 54
        }
    }
}

private struct CreateTaskSheetTextCard: View {
    @Binding var title: String
    @Binding var notes: String
    let titleHighlight: String?
    var focusedInputField: FocusState<CreateTaskSheetInputField?>.Binding

    var body: some View {
        TdaySheetCard {
            HStack(spacing: 0) {
                CreateTaskSheetTextField(
                    placeholder: L("Title"),
                    text: $title,
                    lineLimit: 1 ... 1,
                    field: .title,
                    focusedInputField: focusedInputField,
                    highlightText: titleHighlight
                )

                GuideHelpLink(topicId: GuideTopicId.nlpDateSyntax)
                    .padding(.trailing, 10)
            }

            TdaySheetDivider()

            CreateTaskSheetTextField(
                placeholder: L("Notes"),
                text: $notes,
                lineLimit: 1 ... 1,
                field: .notes,
                focusedInputField: focusedInputField
            )
        }
    }
}

private struct CreateTaskSheetTextField: View {
    let placeholder: String
    @Binding var text: String
    let lineLimit: ClosedRange<Int>
    let field: CreateTaskSheetInputField
    var focusedInputField: FocusState<CreateTaskSheetInputField?>.Binding
    // When set and present in `text`, the detected date phrase is shown with a
    // warm highlight (matching the web). The TextField's own glyphs are hidden and
    // an attributed overlay is drawn so editing/caret behaviour is unchanged.
    var highlightText: String? = nil

    @Environment(\.tdayColors) private var colors

    // Matches the web's --nlp tint; translucent so it reads on light & dark.
    private static let nlpHighlightColor = Color(red: 0.88, green: 0.45, blue: 0.16).opacity(0.38)

    private var normalizedText: Binding<String> {
        Binding(
            get: { text },
            set: {
                text = $0
                    .replacingOccurrences(of: "\n", with: " ")
                    .replacingOccurrences(of: "\r", with: " ")
            }
        )
    }

    /// The text rendered with the matched phrase highlighted, or nil when there's
    /// no active highlight (then the TextField draws its glyphs normally).
    private var highlightedText: AttributedString? {
        guard let highlightText, !highlightText.isEmpty,
              let range = text.range(of: highlightText) else {
            return nil
        }
        var before = AttributedString(String(text[text.startIndex ..< range.lowerBound]))
        before.foregroundColor = colors.onSurface
        var matched = AttributedString(String(text[range]))
        matched.foregroundColor = colors.onSurface
        matched.backgroundColor = Self.nlpHighlightColor
        var after = AttributedString(String(text[range.upperBound...]))
        after.foregroundColor = colors.onSurface
        return before + matched + after
    }

    var body: some View {
        let highlighted = highlightedText
        TextField(
            "",
            text: normalizedText,
            prompt: Text(placeholder)
                .foregroundStyle(colors.onSurfaceVariant.opacity(0.65))
        )
        .lineLimit(lineLimit)
        .focused(focusedInputField, equals: field)
        .submitLabel(.done)
        .onSubmit {
            focusedInputField.wrappedValue = nil
            UIApplication.shared.sendAction(
                #selector(UIResponder.resignFirstResponder),
                to: nil,
                from: nil,
                for: nil
            )
        }
        .textInputAutocapitalization(.sentences)
        .font(.tdayRounded(size: 18, weight: .heavy))
        // Hide the field's own glyphs while highlighting; the overlay draws them.
        .foregroundStyle(highlighted == nil ? AnyShapeStyle(colors.onSurface) : AnyShapeStyle(Color.clear))
        .tint(colors.primary)
        .overlay(alignment: .leading) {
            if let highlighted {
                Text(highlighted)
                    .font(.tdayRounded(size: 18, weight: .heavy))
                    .lineLimit(1)
                    .truncationMode(.tail)
                    .allowsHitTesting(false)
            }
        }
        .padding(.horizontal, 18)
        .padding(.vertical, CreateTaskSheetMetrics.textFieldVerticalPadding)
        .frame(minHeight: CreateTaskSheetMetrics.textFieldMinHeight)
        .contentShape(Rectangle())
        .onTapGesture {
            focusedInputField.wrappedValue = field
        }
    }
}

private struct CreateTaskSheetScheduleToggleRow: View {
    @Binding var isOn: Bool

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Toggle(isOn: $isOn.animation(.spring(response: 0.28, dampingFraction: 0.9))) {
            HStack(spacing: 10) {
                Group {
                    if isOn {
                        Image(systemName: "calendar.badge.clock")
                            .font(.system(size: 20, weight: .semibold))
                    } else {
                        Image("LucideLeaf")
                            .renderingMode(.template)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 20, height: 20)
                    }
                }
                .foregroundStyle(colors.onSurfaceVariant)
                .frame(width: 22, height: 22)

                VStack(alignment: .leading, spacing: 2) {
                    Text("Schedule")
                        .font(.tdayRounded(size: 18, weight: .heavy))
                        .foregroundStyle(colors.onSurface)
                    Text(isOn ? L("Task has a due date") : L("Floater task"))
                        .font(.tdayRounded(size: 12, weight: .bold))
                        .foregroundStyle(colors.onSurfaceVariant.opacity(0.78))
                }
            }
        }
        .toggleStyle(.switch)
        .tint(colors.primary)
        .padding(.horizontal, 16)
        .padding(.vertical, CreateTaskSheetMetrics.rowVerticalPadding)
        .frame(minHeight: CreateTaskSheetMetrics.scheduledRowMinHeight)
    }
}

private struct CreateTaskSheetDueRow: View {
    @Binding var dueDate: Date
    let onDateTap: () -> Void
    let onTimeTap: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        HStack(spacing: 12) {
            leadingContent

            Spacer(minLength: 6)

            CreateTaskSheetDateTimeControl(
                dueDate: $dueDate,
                onDateTap: onDateTap,
                onTimeTap: onTimeTap
            )
        }
        .padding(.horizontal, 16)
        .padding(.vertical, CreateTaskSheetMetrics.rowVerticalPadding)
        .frame(minHeight: CreateTaskSheetMetrics.scheduledRowMinHeight)
    }

    private var leadingContent: some View {
        HStack(spacing: 10) {
            Image(systemName: "calendar")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(colors.onSurfaceVariant)
                .frame(width: 22, height: 22)

            Text("Due")
                .font(.tdayRounded(size: 18, weight: .heavy))
                .foregroundStyle(colors.onSurface)
        }
    }
}

private struct CreateTaskSheetDateTimeControl: View {
    @Binding var dueDate: Date
    let onDateTap: () -> Void
    let onTimeTap: () -> Void

    @Environment(\.tdayColors) private var colors

    private var dateText: String {
        dueDate.formatted(.dateTime.weekday(.abbreviated).month(.abbreviated).day().locale(AppLocale.current))
    }

    private var timeText: String {
        dueDate.formatted(.dateTime.hour(.defaultDigits(amPM: .abbreviated)).minute().locale(AppLocale.current))
    }

    var body: some View {
        HStack(spacing: 0) {
            Button(action: { HapticManager.gentleTap(); onDateTap() }) {
                Text(dateText)
                    .frame(width: 113, height: 38)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Due date")
            .accessibilityValue(dateText)

            Rectangle()
                .fill(colors.onSurfaceVariant.opacity(0.2))
                .frame(width: 1, height: 22)

            Button(action: { HapticManager.gentleTap(); onTimeTap() }) {
                Text(timeText)
                    .frame(width: 92, height: 38)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Due time")
            .accessibilityValue(timeText)
        }
        .font(.tdayRounded(size: 13, weight: .heavy))
        .foregroundStyle(colors.onSurfaceVariant)
        .lineLimit(1)
        .minimumScaleFactor(0.74)
        .frame(width: 206, height: 38)
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(colors.onSurfaceVariant.opacity(0.24), lineWidth: 1)
        }
        .background(
            colors.bottomSheetControlSurface.opacity(0.32),
            in: RoundedRectangle(cornerRadius: 16, style: .continuous)
        )
        .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

private struct CreateTaskSheetDateSelectorContent: View {
    @Binding var dueDate: Date
    let onDone: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        VStack(spacing: 12) {
            DatePicker("", selection: $dueDate, displayedComponents: .date)
                .datePickerStyle(.graphical)
                .labelsHidden()
                .tint(colors.primary)
                .padding(.horizontal, 12)

            CreateTaskSheetSelectorDoneButton(action: { HapticManager.gentleTap(); onDone() })
        }
    }
}

private struct CreateTaskSheetTimeSelectorContent: View {
    @Binding var dueDate: Date
    let onDone: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        VStack(spacing: 12) {
            DatePicker("", selection: $dueDate, displayedComponents: .hourAndMinute)
                .datePickerStyle(.wheel)
                .labelsHidden()
                .tint(colors.primary)
                .frame(height: 154)
                .clipped()
                .padding(.horizontal, 12)

            CreateTaskSheetSelectorDoneButton(action: { HapticManager.gentleTap(); onDone() })
        }
    }
}

private struct CreateTaskSheetSelectorDoneButton: View {
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: action) {
            Text("Done")
                .font(.tdayRounded(size: 17, weight: .heavy))
                .foregroundStyle(colors.primary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 13)
                .background(
                    colors.bottomSheetControlSurface.opacity(0.45),
                    in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                )
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 16)
        .padding(.bottom, 4)
    }
}

private struct CreateTaskSheetSelectorTriggerRow: View {
    /// Asset-catalog name of the lucide field glyph (shared with web/Android).
    let iconName: String
    let title: String
    let value: String
    var isEnabled = true
    var valueLeading: AnyView? = nil
    /// Rendered right after the title (e.g. a contextual GuideHelpLink, which
    /// stays independently tappable because it uses a tap gesture).
    var titleTrailing: AnyView? = nil
    let onTap: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 14) {
                Image(iconName)
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 22, height: 22)
                    .foregroundStyle(colors.onSurfaceVariant.opacity(isEnabled ? 1 : 0.42))

                HStack(spacing: 2) {
                    Text(L(title))
                        .font(.tdayRounded(size: 18, weight: .heavy))
                        .foregroundStyle(colors.onSurface.opacity(isEnabled ? 1 : 0.5))

                    if let titleTrailing {
                        titleTrailing
                    }
                }

                Spacer(minLength: 8)

                HStack(spacing: 4) {
                    if let valueLeading {
                        valueLeading
                    }

                    Text(value)
                        .font(.tdayRounded(size: 14, weight: .heavy))
                        .foregroundStyle(colors.onSurfaceVariant.opacity(isEnabled ? 1 : 0.48))
                        .lineLimit(1)
                        .minimumScaleFactor(0.78)

                    Image(systemName: "chevron.down")
                        .font(.system(size: 12, weight: .heavy))
                        .foregroundStyle(colors.onSurfaceVariant.opacity(isEnabled ? 0.72 : 0.3))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
    }
}

private func createTaskSheetListSwatchColor(_ raw: String?) -> Color {
    switch raw?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
    case "PINK":
        return createTaskSheetHexColor(0xC987A5)
    case "GOLD":
        return createTaskSheetHexColor(0xC7AA63)
    case "DEEP_BLUE":
        return createTaskSheetHexColor(0x6F86C6)
    case "CORAL":
        return createTaskSheetHexColor(0xD39A82)
    case "TEAL":
        return createTaskSheetHexColor(0x67AAA7)
    case "SLATE", "GRAY":
        return createTaskSheetHexColor(0x7F8996)
    case "BLUE":
        return createTaskSheetHexColor(0x6F9FCE)
    case "PURPLE":
        return createTaskSheetHexColor(0x9A86CF)
    case "ROSE":
        return createTaskSheetHexColor(0xC98299)
    case "LIGHT_RED":
        return createTaskSheetHexColor(0xD58D8D)
    case "BRICK":
        return createTaskSheetHexColor(0xAD786E)
    case "YELLOW":
        return createTaskSheetHexColor(0xCFB866)
    case "LIME", "GREEN":
        return createTaskSheetHexColor(0x8DBB73)
    case "ORANGE":
        return createTaskSheetHexColor(0xD69B63)
    case "RED":
        return createTaskSheetHexColor(0xD97873)
    default:
        return createTaskSheetHexColor(0xC987A5)
    }
}

private func createTaskSheetPrioritySwatchColor(_ priority: String) -> Color {
    if TaskPriorityDisplay.isUrgent(priority) {
        return createTaskSheetHexColor(0xE56A6A)
    }
    if TaskPriorityDisplay.isImportant(priority) {
        return createTaskSheetHexColor(0xE3B368)
    }
    return createTaskSheetHexColor(0x6FBF86)
}

private func createTaskSheetRepeatSwatchColor(_ rrule: String?) -> Color {
    switch rrule {
    case "RRULE:FREQ=DAILY;INTERVAL=1":
        return createTaskSheetHexColor(0x6FBF86)
    case "RRULE:FREQ=WEEKLY;INTERVAL=1":
        return createTaskSheetHexColor(0x6FA6E8)
    case "RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR":
        return createTaskSheetHexColor(0x8C7AE6)
    case "RRULE:FREQ=MONTHLY;INTERVAL=1":
        return createTaskSheetHexColor(0xE3B368)
    case "RRULE:FREQ=YEARLY;INTERVAL=1":
        return createTaskSheetHexColor(0xE56A6A)
    default:
        return createTaskSheetHexColor(0xB7BCC8)
    }
}

private func createTaskSheetHexColor(_ hex: UInt) -> Color {
    Color(
        .sRGB,
        red: Double((hex >> 16) & 0xFF) / 255,
        green: Double((hex >> 8) & 0xFF) / 255,
        blue: Double(hex & 0xFF) / 255,
        opacity: 1
    )
}
