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
        NavigationStack {
            Form {
                Section("Task") {
                    TextField("Title", text: $title)
                        .textInputAutocapitalization(.sentences)
                    TextField("Notes", text: $notes, axis: .vertical)
                        .lineLimit(3 ... 6)
                }

                Section("Schedule") {
                    DatePicker("Due", selection: $dueDate)
                }

                Section("Details") {
                    Picker("Priority", selection: $priority) {
                        ForEach(priorityOptions, id: \.self) { option in
                            Text(option).tag(option)
                        }
                    }
                    Picker("List", selection: Binding(get: { selectedListID ?? "" }, set: { selectedListID = $0.isEmpty ? nil : $0 })) {
                        Text("No list").tag("")
                        ForEach(lists) { list in
                            Text(list.name).tag(list.id)
                        }
                    }
                    Picker("Repeat", selection: Binding(get: { repeatRule ?? "" }, set: { newValue in
                        repeatRule = newValue.isEmpty ? nil : newValue
                    })) {
                        ForEach(repeatOptions, id: \.label) { option in
                            Text(option.label).tag(option.value ?? "")
                        }
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(colors.background)
            .navigationTitle(titleText)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        onDismiss()
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isSubmitting ? "Saving..." : submitText) {
                        Task {
                            await submit()
                        }
                    }
                    .disabled(isSubmitting || title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
        .presentationDetents([.large])
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
