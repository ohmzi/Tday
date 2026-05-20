import SwiftUI
import UIKit

struct CreateTaskSheet: View {
    let lists: [ListSummary]
    let titleText: String
    let submitText: String
    let initialPayload: CreateTaskPayload?
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
    @State private var repeatRule: String?
    @State private var isSubmitting = false
    @State private var parserTask: Task<Void, Never>?
    @State private var activeSelector: CreateTaskSheetSelector?

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
        repeatOptions.first(where: { $0.value == repeatRule })?.label ?? "No repeat"
    }

    init(
        lists: [ListSummary],
        titleText: String,
        submitText: String,
        initialPayload: CreateTaskPayload?,
        onParseTaskTitleNlp: ((String, Int64) async -> TodoTitleNlpResponse?)?,
        onDismiss: @escaping () -> Void,
        onSubmit: @escaping (CreateTaskPayload) async -> Void
    ) {
        self.lists = lists
        self.titleText = titleText
        self.submitText = submitText
        self.initialPayload = initialPayload
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

            ScrollView(showsIndicators: false) {
                VStack(spacing: 14) {
                    CreateTaskSheetTextCard(title: $title, notes: $notes)

                    CreateTaskSheetSectionTitle(text: "Schedule")
                    CreateTaskSheetGroupCard {
                        CreateTaskSheetDueRow(dueDate: $dueDate)
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
                            onTap: { activeSelector = .recurrence }
                        )
                    }

                    Spacer(minLength: 6)
                }
                .padding(.horizontal, 18)
                .padding(.bottom, 20)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(colors.background.ignoresSafeArea())
        .presentationDetents([.fraction(0.8)])
        .presentationDragIndicator(.hidden)
        .presentationCornerRadius(34)
        .presentationBackground(colors.background)
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
    }

    private func hydrateFromInitialPayload() {
        guard let initialPayload else {
            return
        }
        title = initialPayload.title
        notes = initialPayload.description ?? ""
        priority = initialPayload.priority
        selectedListID = initialPayload.listId
        dueDate = initialPayload.due
        repeatRule = initialPayload.rrule
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
            due: dueDate,
            rrule: repeatRule,
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
            Color.black.opacity(0.48)
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
                }
            }
            .padding(.horizontal, 54)
        }
    }
}

private enum CreateTaskSheetSelector: String, Identifiable {
    case list
    case priority
    case recurrence

    var id: String { rawValue }

    var title: String {
        switch self {
        case .list:
            return "List"
        case .priority:
            return "Priority"
        case .recurrence:
            return "Repeat"
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
        .background(colors.surface, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
    }
}

private struct CreateTaskSheetDueRow: View {
    @Binding var dueDate: Date

    @Environment(\.tdayColors) private var colors

    var body: some View {
        HStack(spacing: 12) {
            leadingContent

            Spacer(minLength: 6)

            CreateTaskSheetDateTimeControl(dueDate: $dueDate)
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

    @Environment(\.tdayColors) private var colors

    private var dateText: String {
        dueDate.formatted(.dateTime.weekday(.abbreviated).month(.abbreviated).day())
    }

    private var timeText: String {
        dueDate.formatted(.dateTime.hour(.defaultDigits(amPM: .abbreviated)).minute())
    }

    var body: some View {
        ZStack {
            HStack(spacing: 0) {
                Text(dateText)
                    .frame(maxWidth: .infinity)

                Rectangle()
                    .fill(colors.onSurfaceVariant.opacity(0.2))
                    .frame(width: 1, height: 22)

                Text(timeText)
                    .frame(maxWidth: .infinity)
            }
            .font(.tdayRounded(size: 13, weight: .heavy))
            .foregroundStyle(colors.onSurfaceVariant)
            .lineLimit(1)
            .minimumScaleFactor(0.74)
            .padding(.horizontal, 10)
            .frame(width: 206, height: 38)
            .overlay {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(colors.onSurfaceVariant.opacity(0.24), lineWidth: 1)
            }
            .background(colors.surfaceVariant.opacity(0.32), in: RoundedRectangle(cornerRadius: 16, style: .continuous))

            HStack(spacing: 0) {
                DatePicker("", selection: $dueDate, displayedComponents: .date)
                    .labelsHidden()
                    .datePickerStyle(.compact)
                    .tint(colors.onSurfaceVariant)
                    .frame(width: 114, height: 38)
                    .opacity(0.02)

                DatePicker("", selection: $dueDate, displayedComponents: .hourAndMinute)
                    .labelsHidden()
                    .datePickerStyle(.compact)
                    .tint(colors.onSurfaceVariant)
                    .frame(width: 92, height: 38)
                    .opacity(0.02)
            }
        }
        .frame(width: 206, height: 38)
        .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

private struct CreateTaskSheetSelectorTriggerRow: View {
    let iconName: String
    let title: String
    let value: String
    let onTap: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 14) {
                Image(systemName: iconName)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(colors.onSurfaceVariant)
                    .frame(width: 22, height: 22)

                Text(title)
                    .font(.tdayRounded(size: 18, weight: .heavy))
                    .foregroundStyle(colors.onSurface)

                Spacer(minLength: 8)

                HStack(spacing: 4) {
                    Text(value)
                        .font(.tdayRounded(size: 14, weight: .heavy))
                        .foregroundStyle(colors.onSurfaceVariant)
                        .lineLimit(1)
                        .minimumScaleFactor(0.78)

                    Image(systemName: "chevron.down")
                        .font(.system(size: 12, weight: .heavy))
                        .foregroundStyle(colors.onSurfaceVariant.opacity(0.72))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
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
        .background(colors.surface, in: RoundedRectangle(cornerRadius: 32, style: .continuous))
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
        .background(colors.background)
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
                .background(colors.surfaceVariant, in: Circle())
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
    case "RED":
        return createTaskSheetHexColor(0xE65E52)
    case "ORANGE":
        return createTaskSheetHexColor(0xF29F38)
    case "YELLOW":
        return createTaskSheetHexColor(0xF3D04A)
    case "LIME":
        return createTaskSheetHexColor(0x8ACF56)
    case "BLUE":
        return createTaskSheetHexColor(0x5C9FE7)
    case "PURPLE":
        return createTaskSheetHexColor(0x8D6CE2)
    case "PINK":
        return createTaskSheetHexColor(0xDF6DAA)
    case "TEAL":
        return createTaskSheetHexColor(0x4EB5B0)
    case "CORAL":
        return createTaskSheetHexColor(0xE3876D)
    case "GOLD":
        return createTaskSheetHexColor(0xCFAB57)
    case "DEEP_BLUE":
        return createTaskSheetHexColor(0x4B73D6)
    case "ROSE":
        return createTaskSheetHexColor(0xD9799A)
    case "LIGHT_RED":
        return createTaskSheetHexColor(0xE48888)
    case "BRICK":
        return createTaskSheetHexColor(0xB86A5C)
    case "SLATE":
        return createTaskSheetHexColor(0x7B8593)
    default:
        return createTaskSheetHexColor(0x5C9FE7)
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
