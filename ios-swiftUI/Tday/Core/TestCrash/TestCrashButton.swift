import SwiftUI

// TEST-CRASH: the visible control for one trigger. See `TestCrash.swift`.

/// A clearly labelled, error-styled button that fires one `TestCrash.ID`, with the one muted line
/// that says when a report is actually sent.
struct TestCrashButton: View {
    let id: TestCrash.ID
    var showsNote: Bool = true

    @Environment(\.tdayColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button {
                TestCrash.fire(id)
            } label: {
                Text(id.buttonTitle)
                    .font(.tdayRounded(.subheadline, weight: .heavy))
                    .foregroundStyle(colors.error)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(colors.error.opacity(0.12), in: Capsule())
                    .contentShape(Capsule())
            }
            .buttonStyle(.plain)

            if showsNote {
                Text(TestCrash.consentNote)
                    .font(.tdayRounded(.caption, weight: .semibold))
                    .foregroundStyle(colors.onSurfaceVariant)
            }
        }
        .padding(.vertical, 8)
    }
}

/// Settings gets four triggers in one place: the fatal one, the handled capture, the main-thread
/// freeze and the NSException.
struct TestCrashSettingsPanel: View {
    @Environment(\.tdayColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            TestCrashButton(id: .settingsCrash, showsNote: false)
            TestCrashButton(id: .settingsError, showsNote: false)
            TestCrashButton(id: .settingsFreeze, showsNote: false)
            TestCrashButton(id: .settingsNSException, showsNote: false)

            Text(TestCrash.consentNote)
                .font(.tdayRounded(.caption, weight: .semibold))
                .foregroundStyle(colors.onSurfaceVariant)
        }
    }
}

extension View {
    /// The row chrome the feed lists give their rows, for a button placed between them.
    func testCrashListRowStyle() -> some View {
        self
            .listRowInsets(
                EdgeInsets(
                    top: 0,
                    leading: TodoTimelineMetrics.horizontalPadding,
                    bottom: 0,
                    trailing: TodoTimelineMetrics.horizontalPadding
                )
            )
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
    }
}
