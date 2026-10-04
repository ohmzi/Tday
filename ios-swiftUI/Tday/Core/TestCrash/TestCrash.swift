import Foundation
import Observation
import Sentry

// TEST-CRASH: temporary cross-check triggers. The whole feature is this file, `TestCrashButton.swift`,
// `Tests/TdayCoreTests/TestCrashTests.swift` and the lines tagged `TEST-CRASH` elsewhere;
// `grep -rn "TEST-CRASH" ios-swiftUI` lists every touch point. Remove them all together.
//
// Each trigger is a plain, visible control in the regular build. Nothing here bypasses the consent
// gate, the scrubber or the DSN check: a crash is only reported when the person opted in and a DSN is
// built in. The message of every trigger is the same string on web, Android and iOS, so one search in
// Sentry shows all three platforms.

/// The failure modes, one per screen, so Sentry groups them as different issues.
enum TestCrashKind: String, CaseIterable {
    case fatalError
    case preconditionFailure
    case arrayIndexOutOfRange
    case forceUnwrapNil
    case integerOverflow
    case divideByZero
    case invalidPointer
    case tryBang
    case duplicateDictionaryKeys
    case negativeArrayCount
    case forcedCast
    case invalidRange
    case negativeToUnsigned
    case narrowingConversion
    case emptyCollectionRemove
    case stringIndexOutOfBounds
    case nsException
    /// Not a crash: reported through `TdayTelemetry.capture` while the app keeps running.
    case handledCapture
    /// Not a crash: blocks the main thread so the SDK's app-hang detection reports it.
    case mainThreadFreeze

    var isFatal: Bool {
        self != .handledCapture && self != .mainThreadFreeze
    }
}

/// Raised by `try!` for `TC-BUILTIN-DONE`, carrying the required message.
struct TestCrashError: Error, CustomStringConvertible {
    let message: String
    var description: String { message }
}

enum TestCrash {
    /// The stable id of each trigger, the string the owner searches for in Sentry.
    enum ID: String, CaseIterable {
        case feedScheduled = "TC-FEED-SCHED"
        case feedAnytime = "TC-FEED-ANY"
        case builtinToday = "TC-BUILTIN-TODAY"
        case builtinOverdue = "TC-BUILTIN-OVERDUE"
        case builtinScheduled = "TC-BUILTIN-SCHED"
        case builtinAll = "TC-BUILTIN-ALL"
        case builtinPriority = "TC-BUILTIN-PRIO"
        case builtinDone = "TC-BUILTIN-DONE"
        case listScheduled = "TC-LIST-SCHED"
        case listAnytime = "TC-LIST-ANY"
        case taskOpen = "TC-TASK-OPEN"
        case taskEdit = "TC-TASK-EDIT"
        case newList = "TC-NEW-LIST"
        case newTask = "TC-NEW-TASK"
        case calendar = "TC-CALENDAR"
        case settingsCrash = "TC-SET-CRASH"
        case settingsError = "TC-SET-ERROR"
        case settingsFreeze = "TC-SET-FREEZE"
        case settingsNSException = "TC-SET-NSEXC"

        var kind: TestCrashKind {
            switch self {
            case .feedScheduled: return .fatalError
            case .feedAnytime: return .arrayIndexOutOfRange
            case .builtinToday: return .preconditionFailure
            case .builtinOverdue: return .forceUnwrapNil
            case .builtinScheduled: return .integerOverflow
            case .builtinAll: return .divideByZero
            case .builtinPriority: return .invalidPointer
            case .builtinDone: return .tryBang
            case .listScheduled: return .duplicateDictionaryKeys
            case .listAnytime: return .negativeArrayCount
            case .taskOpen: return .forcedCast
            case .taskEdit: return .invalidRange
            case .newList: return .negativeToUnsigned
            case .newTask: return .narrowingConversion
            case .calendar: return .emptyCollectionRemove
            case .settingsCrash: return .stringIndexOutOfBounds
            case .settingsError: return .handledCapture
            case .settingsFreeze: return .mainThreadFreeze
            case .settingsNSException: return .nsException
            }
        }

        /// Short, ASCII, no dots (a dotted word reads as a host to the scrubber) and no digit runs.
        var summary: String {
            switch self {
            case .feedScheduled: return "scheduled home feed"
            case .feedAnytime: return "anytime feed"
            case .builtinToday: return "Today list"
            case .builtinOverdue: return "Overdue list"
            case .builtinScheduled: return "Scheduled list"
            case .builtinAll: return "All Tasks list"
            case .builtinPriority: return "Priority list"
            case .builtinDone: return "Completed list"
            case .listScheduled: return "user scheduled list"
            case .listAnytime: return "user anytime list"
            case .taskOpen: return "first task opened"
            case .taskEdit: return "first task edited"
            case .newList: return "create list sheet"
            case .newTask: return "create task sheet"
            case .calendar: return "calendar"
            case .settingsCrash: return "settings fatal crash"
            case .settingsError: return "settings handled capture"
            case .settingsFreeze: return "settings main thread freeze"
            case .settingsNSException: return "settings NSException"
            }
        }

        /// Identical on every client: `TEST-CRASH <ID>: <description>`.
        var message: String {
            "TEST-CRASH \(rawValue): \(summary)"
        }

        /// What the button says.
        var buttonTitle: String {
            "Test crash: \(rawValue)"
        }
    }

    /// Longer than the SDK's hang threshold (2 s) and than the 5 s the cross-check asks for.
    static let freezeSeconds: TimeInterval = 6

    /// The longest one slice of the freeze may hold the main thread. The budget is only ever looked
    /// at between slices, so this is also how long a tap waits at worst.
    static let freezeSliceSeconds: TimeInterval = 0.5

    /// How much of the budget runs without servicing the run loop at all.
    ///
    /// The SDK reports an app hang only once the main thread has been off its run loop for the whole
    /// `appHangTimeoutInterval` window (2 s, set in `SentryConfiguration`), because staying off it is
    /// what stops the frames tracker's display link and lets the frame delay grow past the timeout.
    /// Servicing the run loop — the only way a queued Stop tap is ever delivered — also lets that
    /// display link fire, so the freeze listens only after the detector has had its window: it ticks
    /// five times per window, so it has seen the hang by roughly 2.4 s and this leaves it a tick and
    /// a half more. The rest of the budget is sliced, so Stop still ends it well before 6 s.
    static let freezeRunLoopHoldOffSeconds: TimeInterval = 3

    /// How long one pass of the run loop is given to hand over a tap that is already queued.
    static let freezeRunLoopPumpSeconds: TimeInterval = 0.01

    /// The frozen main thread's own state: what lets the Settings panel swap its trigger for a Stop
    /// control and then say what the block cost. `@Observable` for the reason the rest of the app is
    /// — the panel's body reads it, so the swap happens the frame the block starts and ends. It is
    /// written from the main thread and only ever read from there.
    @Observable
    final class FreezeState {
        /// True from the first slice to the last, and what the panel shows Stop for.
        private(set) var isFrozen = false

        /// Whether the last block was cut short by `cancelFreeze()`.
        private(set) var wasCancelled = false

        /// How long the last block held the main thread, in milliseconds. Nil until one has run.
        private(set) var lastBlockedMillis: Int?

        /// Set by `cancelFreeze()` and read between slices. No view reads it, so writing it does not
        /// invalidate anything: the panel changes when the block actually ends.
        fileprivate var cancellationRequested = false

        /// The short line under the trigger once a block has ended: what it cost, and whether Stop
        /// cut it short.
        var resultLine: String? {
            guard let millis = lastBlockedMillis else {
                return nil
            }
            let seconds = String(format: "%.1f", Double(millis) / 1000)
            return wasCancelled
                ? "Cancelled after \(seconds) s of blocked main thread."
                : "Main thread was blocked for \(seconds) s."
        }

        fileprivate func begin() {
            isFrozen = true
            wasCancelled = false
            lastBlockedMillis = nil
            cancellationRequested = false
        }

        fileprivate func finish(cancelled: Bool, blocked: TimeInterval) {
            isFrozen = false
            wasCancelled = cancelled
            lastBlockedMillis = Int((blocked * 1000).rounded())
        }
    }

    /// The one freeze the Settings panel observes and stops.
    static let freezeState = FreezeState()

    /// The muted line under every button.
    static let consentNote = "Reports are sent only if crash reports are on in Settings > Privacy."

    // MARK: - Test hooks the unit test asserts

    static func makeError(for id: ID) -> TestCrashError {
        TestCrashError(message: id.message)
    }

    static func makeException(for id: ID) -> NSException {
        NSException(name: .internalInconsistencyException, reason: id.message, userInfo: nil)
    }

    /// The error handed to `TdayTelemetry.capture`. The helper reports only an error's domain and
    /// code, so the message travels as the domain.
    static func makeHandledError(for id: ID) -> NSError {
        NSError(domain: id.message, code: 1)
    }

    /// True for the first row that is actually on screen: the first non-empty section, row 0. A
    /// section that is collapsed counts as empty, because nothing of it is displayed.
    static func isFirstVisibleRow(sectionIndex: Int, itemIndex: Int, visibleCounts: [Int]) -> Bool {
        guard itemIndex == 0, let first = visibleCounts.firstIndex(where: { $0 > 0 }) else {
            return false
        }
        return first == sectionIndex
    }

    // MARK: - Firing

    /// Fires the trigger for `id`. A fatal kind never returns.
    static func fire(_ id: ID) {
        // The id rides on the trail so a report of a runtime trap (which carries the runtime's own
        // message) still names the trigger — and the screen it was fired from rides with it, because
        // such a report has no exception value for Sentry to title the issue with.
        // `Event.applyTestCrashTitle` reads both back before the report goes out. Category `tday` and
        // a label are what the scrubber keeps.
        TdayTelemetry.addBreadcrumb(
            "test_crash:\(id.rawValue): \(id.summary)",
            data: ["id": id.rawValue, "kind": id.kind.rawValue],
        )
        switch id.kind {
        case .fatalError:
            fatalError(id.message)
        case .preconditionFailure:
            preconditionFailure(id.message)
        case .arrayIndexOutOfRange:
            let values = [1, 2, 3]
            sink = values[opaque(7)]
        case .forceUnwrapNil:
            let missing: String? = opaque(0) == 0 ? nil : "present"
            sink = missing!.count
        case .integerOverflow:
            let largest = Int.max - opaque(0)
            sink = largest + opaque(1)
        case .divideByZero:
            sink = opaque(10) / opaque(0)
        case .invalidPointer:
            if let pointer = UnsafeMutablePointer<Int>(bitPattern: opaque(16)) {
                pointer.pointee = opaque(1)
            }
        case .tryBang:
            sink = try! throwing(id)
        case .duplicateDictionaryKeys:
            sink = Dictionary(uniqueKeysWithValues: [(1, "first"), (opaque(1), "second")]).count
        case .negativeArrayCount:
            sink = [Int](repeating: 0, count: opaque(-1)).count
        case .forcedCast:
            let value: Any = opaque(1)
            sink = (value as! String).count
        case .invalidRange:
            sink = Array(opaque(5)..<opaque(3)).count
        case .negativeToUnsigned:
            sink = Int(truncatingIfNeeded: UInt(opaque(-1)))
        case .narrowingConversion:
            sink = Int(Int8(opaque(1_000)))
        case .emptyCollectionRemove:
            var values = [Int]()
            values.reserveCapacity(opaque(0))
            sink = values.removeFirst()
        case .stringIndexOutOfBounds:
            let text = "abc"
            sink = String(text[text.index(text.startIndex, offsetBy: opaque(10))]).count
        case .nsException:
            makeException(for: id).raise()
        case .handledCapture:
            TdayTelemetry.capture(makeHandledError(for: id), operation: "test_crash:\(id.rawValue)")
        case .mainThreadFreeze:
            runFreeze()
        }
    }

    // MARK: - The freeze's slices

    /// Holds the main thread for `budget`, in slices it can leave early.
    ///
    /// The block is real and on this thread, as the app-hang cross-check needs it to be; what is new
    /// is that it comes back to the run loop between slices, so a Stop tap queued behind the block is
    /// delivered and `cancelFreeze()` can set the flag the next slice checks. Returns whether it was
    /// cancelled. `budget`, `sliceSeconds` and `holdOffSeconds` are injectable so the unit tests drive
    /// this exact path with durations too small to notice; nothing in the app passes anything.
    @discardableResult
    static func runFreeze(
        budget: TimeInterval = freezeSeconds,
        sliceSeconds: TimeInterval = freezeSliceSeconds,
        holdOffSeconds: TimeInterval = freezeRunLoopHoldOffSeconds,
        state: FreezeState = TestCrash.freezeState
    ) -> Bool {
        // A second tap cannot stack a second block: the panel hides the trigger while this one holds
        // the thread, and a tap that was already queued for it is swallowed here.
        guard !state.isFrozen, budget > 0 else {
            return false
        }

        let started = Date()
        let deadline = started.addingTimeInterval(budget)
        state.begin()

        while !state.cancellationRequested {
            let remaining = deadline.timeIntervalSinceNow
            guard remaining > 0 else {
                break
            }
            // Never longer than a slice, and never past the budget: both the sleep and the pass of
            // the run loop below are capped by what is left of it, so a full run cannot overrun.
            Thread.sleep(forTimeInterval: min(sliceSeconds, remaining))

            let left = deadline.timeIntervalSinceNow
            guard left > 0 else {
                break
            }
            if Date().timeIntervalSince(started) >= holdOffSeconds {
                // Running the run loop is what delivers the tap, and also what gives the frames
                // tracker a frame — hence the hold-off above. It is bundled into one slice, so it
                // costs a tap's delivery at most a slice plus this window.
                RunLoop.current.run(until: Date().addingTimeInterval(min(freezeRunLoopPumpSeconds, left)))
            }
        }

        // Only a tap that arrived while there was budget left to save is a cancellation: one that
        // lands in the last slice has spent the budget anyway and is reported as a full block.
        let cancelled = state.cancellationRequested && Date() < deadline
        state.finish(cancelled: cancelled, blocked: Date().timeIntervalSince(started))
        return cancelled
    }

    /// Ends a running freeze at the next slice boundary, and says whether there was one to end.
    /// Called by the Settings panel's Stop control, from a tap the freeze's own run-loop pass hands
    /// over while the block is still running.
    @discardableResult
    static func cancelFreeze(state: FreezeState = TestCrash.freezeState) -> Bool {
        guard state.isFrozen else {
            return false
        }
        state.cancellationRequested = true
        return true
    }

    // MARK: - Private

    /// Where a trapping computation's result goes, so the optimiser cannot drop the computation
    /// (and with it the trap) as dead code.
    private static var sink = 0

    private static func throwing(_ id: ID) throws -> Int {
        throw makeError(for: id)
    }

    /// A value the optimiser cannot fold, so the fault happens at run time and not as a compile error.
    private static func opaque(_ value: Int) -> Int {
        CommandLine.arguments.isEmpty ? 0 : value
    }
}

// MARK: - Firing without a tap

extension TestCrash {
    /// Fires the trigger named by `-testCrash <ID>` (or `-testCrash=<ID>`) as the app starts.
    ///
    /// This is not only for convenience. A *tap* is what makes the next launch replay the button's
    /// action — UIKit re-delivers the touch-up that was in flight when the process died, which re-runs
    /// the trap on the way in and looks like an app stuck on its launch screen until the queued event
    /// is finally consumed. An argument is read once, at start, and cannot loop:
    ///
    ///     xcrun devicectl device process launch --console --device <udid> \
    ///         com.ohmz.tday.ios -testCrash TC-FEED-ANY
    static func fireFromLaunchArgumentsIfRequested() {
        let arguments = ProcessInfo.processInfo.arguments
        let inline = arguments.first { $0.hasPrefix("-testCrash=") }?
            .dropFirst("-testCrash=".count)
        let following = arguments.firstIndex(of: "-testCrash").flatMap { index -> Substring? in
            let next = arguments.index(after: index)
            return next < arguments.endIndex ? Substring(arguments[next]) : nil
        }
        guard let raw = inline ?? following, let id = ID(rawValue: String(raw)) else {
            return
        }
        fire(id)
    }
}

// MARK: - Naming a test crash on the way out

extension Event {
    /// TEST-CRASH: gives a test trigger's report a title that names the screen it was fired from, and
    /// marks it as a test.
    ///
    /// A Swift trap's report carries no exception value, so Sentry titles the issue after the crashing
    /// symbol — `_assertionFailure`, which says nothing about the screen and nothing about it being a
    /// test. The breadcrumb the trigger leaves behind is the one thing that survives the crash, so the
    /// title is rebuilt from it: `EXC_BREAKPOINT: TEST-CRASH TC-FEED-ANY: anytime feed`.
    ///
    /// Called from `beforeSend`, so it runs while the report is being sent rather than while it is
    /// being written. An event with no `test_crash:` breadcrumb — every real one — is left untouched.
    func applyTestCrashTitle() {
        let prefix = "test_crash:"
        guard let payload = breadcrumbs?
            .last(where: { $0.message?.hasPrefix(prefix) == true })?
            .message?
            .dropFirst(prefix.count),
            !payload.isEmpty
        else {
            return
        }

        // The scrubber rewrites the spaces in a breadcrumb label as underscores; the other clients'
        // titles keep them, so they are put back: `TEST-CRASH TC-FEED-ANY: anytime feed`, exactly the
        // string Android's exception and the web app's error carry.
        let title = "TEST-CRASH \(payload.replacingOccurrences(of: "_", with: " "))"
        // `exception.value` is what Sentry titles a report with, and `message` is the fallback the
        // server uses when there is no exception at all.
        if let exception = exceptions?.first {
            // A crash-derived exception carries the runtime's own trap text — `Fatal error: Index out
            // of range`, `Can't remove first element from an empty collection` — which says nothing
            // about the screen, so the title is written over it.
            //
            // A captured error is the exception: `capture(error:)` rebuilds its value from the NSError
            // *after* `beforeSend`, so anything written here is appended rather than substituted and
            // the title came out as `TEST-CRASH TC-SET-ERROR: settings handled capture: TEST-CRASH
            // TC-SET-ERROR`. It already names the trigger, so it is left as the app wrote it.
            if exception.mechanism?.type != "NSError" {
                exception.value = title
            }
        } else {
            message = SentryMessage(formatted: title)
        }
        // A crash-derived exception is marked `synthetic`, and Sentry titles a synthetic one after the
        // crashing symbol instead — `closure in _assertionFailure`. Clearing the flag on a test event
        // is what puts the screen in the title: `EXC_BREAKPOINT: TEST-CRASH TC-FEED-ANY: anytime feed`.
        exceptions?.first?.mechanism?.synthetic = NSNumber(value: false)

        var merged = tags ?? [:]
        merged["test_crash"] = "true"
        merged["test_crash_id"] = String(payload.prefix(while: { $0 != ":" }))
        tags = merged
        // One issue per trigger. Every trap in this harness crashes in the same function, so without a
        // fingerprint Sentry groups several of them into one issue and the merge's title stands for
        // all of them — which is the opposite of what a per-screen cross-check is for.
        fingerprint = ["test-crash", String(payload.prefix(while: { $0 != ":" }))]
    }
}
