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
            TestCrashFreezeControl()
            TestCrashButton(id: .settingsNSException, showsNote: false)

            Text(TestCrash.consentNote)
                .font(.tdayRounded(.caption, weight: .semibold))
                .foregroundStyle(colors.onSurfaceVariant)
        }
    }
}

/// The freeze trigger, swapped for a Stop control while the main thread is held, with a short line
/// either way: what it is waiting for, or what the block cost.
///
/// The block is real, so this is the one control in the panel that spends most of its life unable to
/// draw: the swap reaches the screen on the freeze's first pass of the run loop. That is also the
/// first moment the tap below can be recorded, which is why the freeze keeps listening after it.
private struct TestCrashFreezeControl: View {
    @Environment(\.tdayColors) private var colors

    private var freeze = TestCrash.freezeState

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if freeze.isFrozen {
                Button {
                    TestCrash.cancelFreeze()
                } label: {
                    Text("Stop the freeze")
                        .font(.tdayRounded(.subheadline, weight: .heavy))
                        .foregroundStyle(colors.primary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(colors.primary.opacity(0.12), in: Capsule())
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)

                Text("The main thread is blocked. Stop ends it at the next half second.")
                    .font(.tdayRounded(.caption, weight: .semibold))
                    .foregroundStyle(colors.onSurfaceVariant)
            } else {
                TestCrashButton(id: .settingsFreeze, showsNote: false)

                if let result = freeze.resultLine {
                    Text(result)
                        .font(.tdayRounded(.caption, weight: .semibold))
                        .foregroundStyle(colors.onSurfaceVariant)
                }
            }
        }
        .padding(.vertical, 8)
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
