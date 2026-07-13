import SwiftUI

/// Morning Sweep: guided one-card-at-a-time triage of carried-over tasks —
/// Today / Tomorrow / Pick a date / Make it a floater / Let it go, plus
/// "Sweep all to today" behind one undoable toast. Recurring occurrences are
/// excluded; they reschedule via the per-instance edit flow.
struct MorningSweepScreen: View {
    let viewModel: AppViewModel

    @Environment(\.tdayColors) private var colors

    @State private var cards: [TodoItem] = []
    @State private var loaded = false
    @State private var pickingDate = false
    @State private var pickedDate = Date()

    private var container: AppContainer { viewModel.container }
    private var card: TodoItem? { cards.first }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                backButton

                Text(L("Morning Sweep"))
                    .font(.tdayRounded(size: 28, weight: .heavy))
                    .foregroundStyle(colors.onSurface)

                if let card {
                    cardView(card)
                    actionList(card)
                    bottomRow(card)
                } else {
                    Text(L("All caught up"))
                        .font(.tdayRounded(size: 20, weight: .heavy))
                        .foregroundStyle(colors.onSurfaceVariant.opacity(0.7))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 48)
                }
            }
            .padding(.horizontal, 18)
        }
        .background(colors.background)
        .navigationBarBackButtonHidden(true)
        .onAppear {
            guard !loaded else { return }
            loaded = true
            loadCards()
        }
        .sheet(isPresented: $pickingDate) {
            datePickerSheet
        }
    }

    private var backButton: some View {
        Button(action: { viewModel.goBack() }) {
            HStack(spacing: 6) {
                Image("LucideArrowLeft")
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 18, height: 18)
                Text(L("Back"))
                    .font(.tdayRounded(size: 16, weight: .heavy))
            }
            .foregroundStyle(colors.onSurfaceVariant)
        }
        .buttonStyle(.plain)
        .padding(.top, 8)
    }

    private func cardView(_ card: TodoItem) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(card.title)
                .font(.tdayRounded(size: 19, weight: .heavy))
                .foregroundStyle(colors.onSurface)
            HStack(spacing: 6) {
                if let due = card.due {
                    Text(due.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated)))
                }
                if cards.count > 1 {
                    Text("· \(cards.count)")
                }
            }
            .font(.tdayRounded(size: 13, weight: .bold))
            .foregroundStyle(colors.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: 18)
                .fill(colors.surface)
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(colors.onSurface.opacity(0.06)))
        )
    }

    private func actionList(_ card: TodoItem) -> some View {
        VStack(spacing: 8) {
            sweepAction(title: L("Today"), assetName: "LucideAlarmClock") {
                move(card, toDayOffset: 0)
            }
            sweepAction(title: L("Tomorrow"), assetName: "LucideCalendarClock") {
                move(card, toDayOffset: 1)
            }
            sweepAction(title: L("Pick a date"), assetName: "LucideCalendar") {
                pickedDate = Date()
                pickingDate = true
            }
            sweepAction(title: L("Make it a floater"), assetName: "LucideWaves") {
                advance(past: card)
                Task { try? await container.todoRepository.demoteTodo(card) }
            }
            sweepAction(title: L("Let it go"), assetName: "LucideTrash") {
                advance(past: card)
                Task { try? await container.todoRepository.deleteTodo(card) }
            }
        }
    }

    private func bottomRow(_ card: TodoItem) -> some View {
        HStack {
            Button(L("Skip")) {
                advance(past: card)
            }
            .font(.tdayRounded(size: 15, weight: .heavy))
            .foregroundStyle(colors.onSurfaceVariant)
            .buttonStyle(.plain)

            Spacer()

            Button {
                sweepAllToToday()
            } label: {
                Text(L("Sweep all to today"))
                    .font(.tdayRounded(size: 15, weight: .heavy))
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(.top, 4)
    }

    private func sweepAction(title: String, assetName: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(assetName)
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 18, height: 18)
                    .foregroundStyle(colors.onSurfaceVariant)
                Text(title)
                    .font(.tdayRounded(size: 16, weight: .heavy))
                    .foregroundStyle(colors.onSurface)
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 13)
            .background(
                RoundedRectangle(cornerRadius: 14)
                    .fill(colors.surface)
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(colors.onSurface.opacity(0.06)))
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var datePickerSheet: some View {
        VStack(spacing: 0) {
            TdaySheetHeader(
                title: L("Pick a date"),
                closeAccessibilityLabel: L("Close"),
                onClose: { pickingDate = false }
            )
            DatePicker("", selection: $pickedDate, displayedComponents: [.date])
                .datePickerStyle(.graphical)
                .padding(.horizontal, 18)
            Button {
                pickingDate = false
                if let card {
                    move(card, toDay: pickedDate)
                }
            } label: {
                Text(L("Save"))
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

    // MARK: - Data

    private func loadCards() {
        let snapshot = container.todoRepository.fetchTodoListCacheSnapshot(mode: .overdue, listId: nil)
        cards = snapshot.items.filter { !$0.isRecurring && $0.instanceDate == nil }
    }

    private func advance(past card: TodoItem) {
        cards.removeAll { $0.id == card.id }
    }

    /// Old due's time-of-day carried onto today+offset.
    private func move(_ card: TodoItem, toDayOffset offset: Int) {
        let calendar = Calendar.current
        let day = calendar.date(byAdding: .day, value: offset, to: Date()) ?? Date()
        move(card, toDay: day)
    }

    private func move(_ card: TodoItem, toDay day: Date) {
        let calendar = Calendar.current
        let time = card.due.map { calendar.dateComponents([.hour, .minute], from: $0) }
        let due = calendar.date(
            bySettingHour: time?.hour ?? 9,
            minute: time?.minute ?? 0,
            second: 0,
            of: calendar.startOfDay(for: day)
        ) ?? day
        advance(past: card)
        Task { try? await container.todoRepository.moveTodo(card, due: due) }
    }

    /// One undoable toast covers the whole batch; nothing commits until the
    /// undo window closes.
    private func sweepAllToToday() {
        let swept = cards
        guard !swept.isEmpty else { return }
        let container = container
        cards = []
        container.undoableDeleteScheduler.schedule(
            message: String(format: L("Swept %d tasks"), swept.count),
            restore: {
                self.cards = swept
            },
            commit: {
                let calendar = Calendar.current
                for todo in swept {
                    let time = todo.due.map { calendar.dateComponents([.hour, .minute], from: $0) }
                    let due = calendar.date(
                        bySettingHour: time?.hour ?? 9,
                        minute: time?.minute ?? 0,
                        second: 0,
                        of: calendar.startOfDay(for: Date())
                    ) ?? Date()
                    try? await container.todoRepository.moveTodo(todo, due: due)
                }
            }
        )
    }
}
