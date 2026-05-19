import SwiftUI

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
                        CreateTaskSheetMenuRow(
                            iconName: "list.bullet",
                            title: "List",
                            value: selectedListName
                        ) {
                            Button {
                                selectedListID = nil
                            } label: {
                                CreateTaskSheetMenuItemLabel(
                                    title: "No list",
                                    selected: selectedListID == nil,
                                    swatchColor: colors.onSurfaceVariant.opacity(0.35)
                                )
                            }

                            ForEach(lists) { list in
                                Button {
                                    selectedListID = list.id
                                } label: {
                                    CreateTaskSheetMenuItemLabel(
                                        title: list.name,
                                        selected: selectedListID == list.id,
                                        swatchColor: createTaskSheetListSwatchColor(list.color)
                                    )
                                }
                            }
                        }

                        CreateTaskSheetDivider()

                        CreateTaskSheetMenuRow(
                            iconName: "text.badge.checkmark",
                            title: "Priority",
                            value: priority
                        ) {
                            ForEach(priorityOptions, id: \.self) { option in
                                Button {
                                    priority = option
                                } label: {
                                    CreateTaskSheetMenuItemLabel(
                                        title: option,
                                        selected: priority == option,
                                        swatchColor: createTaskSheetPrioritySwatchColor(option)
                                    )
                                }
                            }
                        }

                        CreateTaskSheetDivider()

                        CreateTaskSheetMenuRow(
                            iconName: "repeat",
                            title: "Repeat",
                            value: selectedRepeatLabel
                        ) {
                            ForEach(repeatOptions, id: \.label) { option in
                                Button {
                                    repeatRule = option.value
                                } label: {
                                    CreateTaskSheetMenuItemLabel(
                                        title: option.label,
                                        selected: repeatRule == option.value,
                                        swatchColor: createTaskSheetRepeatSwatchColor(option.value)
                                    )
                                }
                            }
                        }
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
                axis: .horizontal,
                lineLimit: 1 ... 1
            )

            CreateTaskSheetDivider()

            CreateTaskSheetTextField(
                placeholder: "Notes",
                text: $notes,
                axis: .vertical,
                lineLimit: 1 ... 3
            )
        }
    }
}

private struct CreateTaskSheetTextField: View {
    let placeholder: String
    @Binding var text: String
    let axis: Axis
    let lineLimit: ClosedRange<Int>

    @Environment(\.tdayColors) private var colors

    var body: some View {
        TextField(
            "",
            text: $text,
            prompt: Text(placeholder)
                .foregroundStyle(colors.onSurfaceVariant.opacity(0.65)),
            axis: axis
        )
        .lineLimit(lineLimit)
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
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 14) {
                leadingContent

                Spacer(minLength: 8)

                HStack(spacing: 8) {
                    CreateTaskSheetDatePickerChip(dueDate: $dueDate, components: .date, width: 130)
                    CreateTaskSheetDatePickerChip(dueDate: $dueDate, components: .hourAndMinute, width: 90)
                }
            }

            VStack(alignment: .leading, spacing: 12) {
                leadingContent

                HStack(spacing: 8) {
                    CreateTaskSheetDatePickerChip(dueDate: $dueDate, components: .date, width: 130)
                    CreateTaskSheetDatePickerChip(dueDate: $dueDate, components: .hourAndMinute, width: 90)
                }
                .padding(.leading, 36)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
    }

    private var leadingContent: some View {
        HStack(spacing: 14) {
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

private struct CreateTaskSheetDatePickerChip: View {
    @Binding var dueDate: Date
    let components: DatePickerComponents
    let width: CGFloat

    @Environment(\.tdayColors) private var colors

    var body: some View {
        DatePicker("", selection: $dueDate, displayedComponents: components)
            .labelsHidden()
            .datePickerStyle(.compact)
            .tint(colors.onSurfaceVariant)
            .font(.tdayRounded(size: 13, weight: .heavy))
            .frame(width: width, height: 38)
            .background(colors.surfaceVariant.opacity(0.66), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

private struct CreateTaskSheetMenuRow<MenuContent: View>: View {
    let iconName: String
    let title: String
    let value: String
    @ViewBuilder let menuContent: () -> MenuContent

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Menu {
            menuContent()
        } label: {
            HStack(spacing: 14) {
                Image(systemName: iconName)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(colors.onSurfaceVariant)
                    .frame(width: 22, height: 22)

                Text(title)
                    .font(.tdayRounded(size: 18, weight: .heavy))
                    .foregroundStyle(colors.onSurface)

                Spacer(minLength: 8)

                Text(value)
                    .font(.tdayRounded(size: 14, weight: .heavy))
                    .foregroundStyle(colors.onSurfaceVariant)
                    .lineLimit(1)
                    .minimumScaleFactor(0.78)

                Image(systemName: "chevron.down")
                    .font(.system(size: 12, weight: .heavy))
                    .foregroundStyle(colors.onSurfaceVariant.opacity(0.72))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

private struct CreateTaskSheetMenuItemLabel: View {
    let title: String
    let selected: Bool
    let swatchColor: Color

    var body: some View {
        HStack(spacing: 10) {
            Circle()
                .fill(swatchColor)
                .frame(width: 10, height: 10)

            Text(title)

            if selected {
                Image(systemName: "checkmark")
            }
        }
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
