import SwiftUI

struct CompletedScreen: View {
    @State private var viewModel: CompletedViewModel
    @Environment(\.tdayColors) private var colors
    @State private var editingItem: CompletedItem?

    init(container: AppContainer) {
        _viewModel = State(initialValue: CompletedViewModel(container: container))
    }

    private var groupedItems: [TimelineSection<CompletedItem>] {
        let grouped = Dictionary(grouping: viewModel.items) { item in
            (item.completedAt ?? item.due).formatted(.dateTime.weekday(.wide).month(.abbreviated).day())
        }
        return grouped.keys.sorted().map { key in
            TimelineSection(id: key, title: key, items: grouped[key] ?? [], isCollapsible: key == "Earlier")
        }
    }

    var body: some View {
        List {
            if let errorMessage = viewModel.errorMessage {
                Section {
                    ErrorRetryView(message: errorMessage) {
                        Task { await viewModel.refresh() }
                    }
                    .listRowBackground(Color.clear)
                }
            }
            ForEach(groupedItems) { section in
                Section(section.title) {
                    ForEach(section.items) { item in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(item.title)
                                .font(.subheadline.weight(.semibold))
                            Text((item.completedAt ?? item.due).formatted(date: .abbreviated, time: .shortened))
                                .font(.caption)
                                .foregroundStyle(colors.onSurfaceVariant)
                        }
                        .swipeActions(edge: .leading, allowsFullSwipe: true) {
                            Button {
                                Task { await viewModel.uncomplete(item) }
                            } label: {
                                Label("Restore", systemImage: "arrow.uturn.backward")
                            }
                            .tint(.blue)
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            Button(role: .destructive) {
                                Task { await viewModel.delete(item) }
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                            Button {
                                editingItem = item
                            } label: {
                                Label("Edit", systemImage: "square.and.pencil")
                            }
                            .tint(colors.secondary)
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(colors.background)
        .navigationBackHistoryTitle("Completed")
        .navigationTitle("Completed")
        .refreshable {
            await viewModel.refresh()
        }
        .sheet(item: $editingItem) { item in
            CreateTaskSheet(
                lists: viewModel.lists,
                titleText: "Edit Completed Task",
                submitText: "Save",
                initialPayload: CreateTaskPayload(title: item.title, description: item.description, priority: item.priority, due: item.due, rrule: item.rrule, listId: nil),
                onParseTaskTitleNlp: nil,
                onDismiss: { editingItem = nil },
                onSubmit: { payload in
                    await viewModel.update(item, payload: payload)
                }
            )
        }
    }
}
