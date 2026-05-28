import SwiftUI
import UIKit

private enum CreateTaskSheetMetrics {
    static let initialSheetHeight: CGFloat = 560
    static let maximumHeightFraction: CGFloat = 0.86
    static let bottomContentPadding: CGFloat = 8
}

private struct CreateTaskSheetHeaderHeightKey: PreferenceKey {
    static var defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

private struct CreateTaskSheetFormHeightKey: PreferenceKey {
    static var defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

struct CreateTaskSheet: View {
    let lists: [ListSummary]
    let titleText: String
    let submitText: String
    let initialPayload: CreateTaskPayload?
    let defaultScheduled: Bool
    let onParseTaskTitleNlp: ((String, Int64) async -> TodoTitleNlpResponse?)?
    let onDismiss: () -> Void
    let onSubmit: (CreateTaskPayload) async -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.tdayColors) private var colors

    @State private var title = ""
    @State private var notes = ""
    @State private var priority = "Low"
    @State private var selectedListID: String?
    @State private var dueDate = Date().addingTimeInterval(60 * 60)
    @State private var scheduleEnabled = true
    @State private var repeatRule: String?
    @State private var isSubmitting = false
    @State private var parserTask: Task<Void, Never>?
    @State private var activeSelector: CreateTaskSheetSelector?
    @State private var headerHeight: CGFloat = 84
    @State private var formHeight: CGFloat = CreateTaskSheetMetrics.initialSheetHeight - 84

    private let priorityOptions = ["Low", "Medium", "High"]
    private let repeatOptions: [(label: String, value: String?)] = [
        ("No repeat", nil),
        ("Daily", "RRULE:FREQ=DAILY;INTERVAL=1"),
        ("Weekly", "RRULE:FREQ=WEEKLY;INTERVAL=1"),
        ("Weekdays", "RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR"),
        ("Monthly", "RRULE:FREQ=MONTHLY;INTERVAL=1"),
        ("Yearly", "RRULE:FREQ=YEARLY;INTERVAL=1"),
    ]

    private var selectedListName: String {
        guard let selectedListID,
              let list = lists.first(where: { $0.id == selectedListID }) else {
            return "No list"
        }
        return list.name
    }

    private var selectedRepeatLabel: String {
        guard scheduleEnabled else {
            return "No repeat"
        }
        return repeatOptions.first(where: { $0.value == repeatRule })?.label ?? "No repeat"
    }

    private var maximumSheetHeight: CGFloat {
        max(1, UIScreen.main.bounds.height * CreateTaskSheetMetrics.maximumHeightFraction)
    }

    private var measuredSheetHeight: CGFloat {
        min(max(headerHeight + formHeight, 1), maximumSheetHeight)
    }

    private var formNeedsScrolling: Bool {
        headerHeight + formHeight > maximumSheetHeight
    }

    init(
        lists: [ListSummary],
        titleText: String,
        submitText: String,
        initialPayload: CreateTaskPayload?,
        defaultScheduled: Bool = true,
        onParseTaskTitleNlp: ((String, Int64) async -> TodoTitleNlpResponse?)?,
        onDismiss: @escaping () -> Void,
        onSubmit: @escaping (CreateTaskPayload) async -> Void
    ) {
        self.lists = lists
        self.titleText = titleText
        self.submitText = submitText
        self.initialPayload = initialPayload
        self.defaultScheduled = defaultScheduled
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
            submitText: "Save",
            initialPayload: initialPayload,
            onParseTaskTitleNlp: onParseTaskTitleNlp,
            onDismiss: {},
            onSubmit: { payload in
                onSave(payload)
            }
        )
    }

    var body: some View {
        VStack(spacing: 0) {
            CreateTaskSheetHeader(
                title: titleText,
                submitAccessibilityLabel: submitText,
                isSubmitEnabled: !isSubmitting && !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                onCancel: {
                    onDismiss()
                    dismiss()
                },
                onSubmit: {
                    Task {
                        await submit()
                    }
                }
            )
            .background(
                GeometryReader { proxy in
                    Color.clear
                        .preference(key: CreateTaskSheetHeaderHeightKey.self, value: ceil(proxy.size.height))
                }
            )

            ScrollView(showsIndicators: false) {
                VStack(spacing: 14) {
                    CreateTaskSheetTextCard(title: $title, notes: $notes)

                    CreateTaskSheetSectionTitle(text: "Schedule")
                    CreateTaskSheetGroupCard {
                        CreateTaskSheetScheduleToggleRow(
                            isOn: $scheduleEnabled
                        )

                        if scheduleEnabled {
                            CreateTaskSheetDivider()

                            CreateTaskSheetDueRow(
                                dueDate: $dueDate,
                                onDateTap: { activeSelector = .date },
                                onTimeTap: { activeSelector = .time }
                            )
                            .transition(.opacity.combined(with: .move(edge: .top)))
                        }
                    }

                    CreateTaskSheetSectionTitle(text: "Details")
                    CreateTaskSheetGroupCard {
                        CreateTaskSheetSelectorTriggerRow(
                            iconName: "list.bullet",
                            title: "List",
                            value: selectedListName,
                            onTap: { activeSelector = .list }
                        )

                        CreateTaskSheetDivider()

                        CreateTaskSheetSelectorTriggerRow(
                            iconName: "text.badge.checkmark",
                            title: "Priority",
                            value: priority,
                            onTap: { activeSelector = .priority }
                        )

                        CreateTaskSheetDivider()

                        CreateTaskSheetSelectorTriggerRow(
                            iconName: "repeat",
                            title: "Repeat",
                            value: selectedRepeatLabel,
                            isEnabled: scheduleEnabled,
                            onTap: {
                                guard scheduleEnabled else { return }
                                activeSelector = .recurrence
                            }
                        )
                    }
                }
                .padding(.horizontal, 18)
                .padding(.bottom, CreateTaskSheetMetrics.bottomContentPadding)
                .background(
                    GeometryReader { proxy in
                        Color.clear
                            .preference(key: CreateTaskSheetFormHeightKey.self, value: ceil(proxy.size.height))
                    }
                )
            }
            .scrollDisabled(!formNeedsScrolling)
        }
        .frame(maxWidth: .infinity, alignment: .top)
        .background(colors.bottomSheetBackground.ignoresSafeArea())
        .presentationDetents([.height(measuredSheetHeight)])
        .presentationDragIndicator(.hidden)
        .presentationCornerRadius(34)
        .presentationBackground {
            colors.bottomSheetBackground
                .ignoresSafeArea(.container, edges: .bottom)
        }
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
        .onChange(of: scheduleEnabled) { _, isEnabled in
            if !isEnabled {
                repeatRule = nil
                if activeSelector == .date || activeSelector == .time || activeSelector == .recurrence {
                    activeSelector = nil
                }
            }
        }
        .onPreferenceChange(CreateTaskSheetHeaderHeightKey.self) { height in
            headerHeight = max(height, 1)
        }
        .onPreferenceChange(CreateTaskSheetFormHeightKey.self) { height in
            formHeight = max(height, 1)
        }
    }

    private func hydrateFromInitialPayload() {
        guard let initialPayload else {
            scheduleEnabled = defaultScheduled
            repeatRule = defaultScheduled ? repeatRule : nil
            return
        }
        title = initialPayload.title
        notes = initialPayload.description ?? ""
        priority = initialPayload.priority
        selectedListID = initialPayload.listId
        if let due = initialPayload.due {
            dueDate = due
            scheduleEnabled = true
            repeatRule = initialPayload.rrule
        } else {
            scheduleEnabled = false
            repeatRule = nil
        }
    }

    private func scheduleNlpParse() {
        guard let onParseTaskTitleNlp else {
            return
        }
        parserTask?.cancel()
        parserTask = Task {
            try? await Task.sleep(for: .milliseconds(260))
            guard !Task.isCancelled else {
                return
            }
            guard let parsed = await onParseTaskTitleNlp(title, dueDate.epochMilliseconds) else {
                return
            }
            await MainActor.run {
                title = parsed.cleanTitle
                if let dueEpochMs = parsed.dueEpochMs {
                    dueDate = Date(epochMilliseconds: dueEpochMs)
                    scheduleEnabled = true
                }
            }
        }
    }

    private func submit() async {
        isSubmitting = true
        let payload = CreateTaskPayload(
            title: title.trimmingCharacters(in: .whitespacesAndNewlines),
            description: notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : notes.trimmingCharacters(in: .whitespacesAndNewlines),
            priority: priority,
            due: scheduleEnabled ? dueDate : nil,
            rrule: scheduleEnabled ? repeatRule : nil,
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

            CreateTaskSheetSelectorCard(title: selector.title) {
                switch selector {
                case .list:
                    CreateTaskSheetSelectorRow(
                        title: "No list",
                        swatchColor: colors.onSurfaceVariant.opacity(0.35),
                        selected: selectedListID == nil
                    ) {
                        selectedListID = nil
                        activeSelector = nil
                    }

                    ForEach(lists) { list in
                        CreateTaskSheetSelectorDivider()
                        CreateTaskSheetSelectorRow(
                            title: list.name,
                            swatchColor: createTaskSheetListSwatchColor(list.color),
                            selected: selectedListID == list.id
                        ) {
                            selectedListID = list.id
                            activeSelector = nil
                        }
                    }

                case .priority:
                    ForEach(Array(priorityOptions.enumerated()), id: \.element) { index, option in
                        if index > 0 {
                            CreateTaskSheetSelectorDivider()
                        }
                        CreateTaskSheetSelectorRow(
                            title: option,
                            swatchColor: createTaskSheetPrioritySwatchColor(option),
                            selected: priority == option
                        ) {
                            priority = option
                            activeSelector = nil
                        }
                    }

                case .recurrence:
                    ForEach(Array(repeatOptions.enumerated()), id: \.element.label) { index, option in
                        if index > 0 {
                            CreateTaskSheetSelectorDivider()
                        }
                        CreateTaskSheetSelectorRow(
                            title: option.label,
                            swatchColor: createTaskSheetRepeatSwatchColor(option.value),
                            selected: repeatRule == option.value
                        ) {
                            repeatRule = option.value
                            activeSelector = nil
                        }
                    }

                case .date:
                    CreateTaskSheetDateSelectorContent(dueDate: $dueDate) {
                        activeSelector = nil
                    }

                case .time:
                    CreateTaskSheetTimeSelectorContent(dueDate: $dueDate) {
                        activeSelector = nil
                    }
                }
            }
            .padding(.horizontal, selector.horizontalPadding)
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
            return "List"
        case .priority:
            return "Priority"
        case .recurrence:
            return "Repeat"
        case .date:
            return "Due date"
        case .time:
            return "Due time"
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

    @Environment(\.tdayColors) private var colors

    var body: some View {
        CreateTaskSheetGroupCard {
            CreateTaskSheetTextField(
                placeholder: "Title",
                text: $title,
                lineLimit: 1 ... 1
            )

            CreateTaskSheetDivider()

            CreateTaskSheetTextField(
                placeholder: "Notes",
                text: $notes,
                lineLimit: 1 ... 1
            )
        }
    }
}

private struct CreateTaskSheetTextField: View {
    let placeholder: String
    @Binding var text: String
    let lineLimit: ClosedRange<Int>

    @Environment(\.tdayColors) private var colors

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

    var body: some View {
        TextField(
            "",
            text: normalizedText,
            prompt: Text(placeholder)
                .foregroundStyle(colors.onSurfaceVariant.opacity(0.65))
        )
        .lineLimit(lineLimit)
        .submitLabel(.done)
        .onSubmit {
            UIApplication.shared.sendAction(
                #selector(UIResponder.resignFirstResponder),
                to: nil,
                from: nil,
                for: nil
            )
        }
        .textInputAutocapitalization(.sentences)
        .font(.tdayRounded(size: 18, weight: .heavy))
        .foregroundStyle(colors.onSurface)
        .tint(colors.primary)
        .padding(.horizontal, 18)
        .padding(.vertical, 15)
        .frame(minHeight: 56)
    }
}

private struct CreateTaskSheetSectionTitle: View {
    let text: String

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Text(text)
            .font(.tdayRounded(size: 22, weight: .bold))
            .foregroundStyle(colors.onSurfaceVariant)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 4)
    }
}

private struct CreateTaskSheetGroupCard<Content: View>: View {
    @ViewBuilder let content: Content

    @Environment(\.tdayColors) private var colors

    var body: some View {
        VStack(spacing: 0) {
            content
        }
        .frame(maxWidth: .infinity)
        .background(colors.bottomSheetSurface, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
    }
}

private struct CreateTaskSheetScheduleToggleRow: View {
    @Binding var isOn: Bool

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Toggle(isOn: $isOn.animation(.spring(response: 0.28, dampingFraction: 0.9))) {
            HStack(spacing: 10) {
                Image(systemName: isOn ? "calendar.badge.clock" : "tray.full")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(colors.onSurfaceVariant)
                    .frame(width: 22, height: 22)

                VStack(alignment: .leading, spacing: 2) {
                    Text("Schedule")
                        .font(.tdayRounded(size: 18, weight: .heavy))
                        .foregroundStyle(colors.onSurface)
                    Text(isOn ? "Task has a due date" : "Anytime task")
                        .font(.tdayRounded(size: 12, weight: .bold))
                        .foregroundStyle(colors.onSurfaceVariant.opacity(0.78))
                }
            }
        }
        .toggleStyle(.switch)
        .tint(colors.primary)
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .frame(minHeight: 72)
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
        .padding(.vertical, 14)
        .frame(minHeight: 72)
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
        dueDate.formatted(.dateTime.weekday(.abbreviated).month(.abbreviated).day())
    }

    private var timeText: String {
        dueDate.formatted(.dateTime.hour(.defaultDigits(amPM: .abbreviated)).minute())
    }

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onDateTap) {
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

            Button(action: onTimeTap) {
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

            CreateTaskSheetSelectorDoneButton(action: onDone)
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

            CreateTaskSheetSelectorDoneButton(action: onDone)
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
    let iconName: String
    let title: String
    let value: String
    var isEnabled = true
    let onTap: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 14) {
                Image(systemName: iconName)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(colors.onSurfaceVariant.opacity(isEnabled ? 1 : 0.42))
                    .frame(width: 22, height: 22)

                Text(title)
                    .font(.tdayRounded(size: 18, weight: .heavy))
                    .foregroundStyle(colors.onSurface.opacity(isEnabled ? 1 : 0.5))

                Spacer(minLength: 8)

                HStack(spacing: 4) {
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
            .padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
    }
}

private struct CreateTaskSheetSelectorCard<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content

    @Environment(\.tdayColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title)
                .font(.tdayRounded(size: 18, weight: .heavy))
                .foregroundStyle(colors.onSurfaceVariant)
                .padding(.horizontal, 20)
                .padding(.top, 20)
                .padding(.bottom, 12)

            content
        }
        .padding(.bottom, 14)
        .frame(maxWidth: 330)
        .background(colors.bottomSheetSurface, in: RoundedRectangle(cornerRadius: 32, style: .continuous))
        .shadow(color: Color.black.opacity(0.18), radius: 24, x: 0, y: 18)
    }
}

private struct CreateTaskSheetSelectorRow: View {
    let title: String
    let swatchColor: Color
    let selected: Bool
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Circle()
                    .fill(swatchColor)
                    .frame(width: 10, height: 10)

                Text(title)
                    .font(.tdayRounded(size: 18, weight: .heavy))
                    .foregroundStyle(colors.onSurface)
                    .lineLimit(1)

                Spacer(minLength: 12)

                if selected {
                    Image(systemName: "checkmark")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(colors.primary)
                } else {
                    Color.clear
                        .frame(width: 18, height: 18)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

private struct CreateTaskSheetSelectorDivider: View {
    @Environment(\.tdayColors) private var colors

    var body: some View {
        Rectangle()
            .fill(colors.onSurfaceVariant.opacity(0.16))
            .frame(height: 1)
            .padding(.horizontal, 20)
    }
}

private struct CreateTaskSheetDivider: View {
    @Environment(\.tdayColors) private var colors

    var body: some View {
        Rectangle()
            .fill(colors.onSurfaceVariant.opacity(0.18))
            .frame(height: 1)
            .padding(.horizontal, 18)
    }
}

private struct CreateTaskSheetHeader: View {
    let title: String
    let submitAccessibilityLabel: String
    let isSubmitEnabled: Bool
    let onCancel: () -> Void
    let onSubmit: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        HStack {
            CreateTaskSheetHeaderButton(
                systemName: "xmark",
                accessibilityLabel: "Cancel",
                accentColor: Color(red: 227.0 / 255.0, green: 90.0 / 255.0, blue: 90.0 / 255.0),
                isEnabled: true,
                action: onCancel
            )

            Spacer(minLength: 0)

            Text(title)
                .font(.tdayRounded(size: 24, weight: .heavy))
                .foregroundStyle(colors.onSurface)
                .lineLimit(1)
                .minimumScaleFactor(0.78)

            Spacer(minLength: 0)

            CreateTaskSheetHeaderButton(
                systemName: "checkmark",
                accessibilityLabel: submitAccessibilityLabel,
                accentColor: Color(red: 47.0 / 255.0, green: 163.0 / 255.0, blue: 91.0 / 255.0),
                isEnabled: isSubmitEnabled,
                action: onSubmit
            )
        }
        .padding(.horizontal, 18)
        .padding(.top, 14)
        .padding(.bottom, 14)
        .background(colors.bottomSheetBackground)
    }
}

private struct CreateTaskSheetHeaderButton: View {
    let systemName: String
    let accessibilityLabel: String
    let accentColor: Color
    let isEnabled: Bool
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(colors.onSurface.opacity(isEnabled ? 1 : 0.55))
                .frame(width: 56, height: 56)
                .background(colors.bottomSheetControlSurface, in: Circle())
                .overlay {
                    Circle()
                        .stroke(accentColor.opacity(isEnabled ? 0.55 : 0.3), lineWidth: 1.5)
                }
                .contentShape(Circle())
        }
        .buttonStyle(
            TdayPressButtonStyle(
                shadowColor: Color.black,
                pressedShadowOpacity: 0.04,
                normalShadowOpacity: isEnabled ? 0.16 : 0.06
            )
        )
        .disabled(!isEnabled)
        .accessibilityLabel(accessibilityLabel)
        .accessibilityAddTraits(.isButton)
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
    switch priority.lowercased() {
    case "high":
        return createTaskSheetHexColor(0xE56A6A)
    case "medium":
        return createTaskSheetHexColor(0xE3B368)
    default:
        return createTaskSheetHexColor(0x6FBF86)
    }
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
