import Foundation

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
        // message) still names the trigger. Category `tday` and a label are what the scrubber keeps.
        TdayTelemetry.addBreadcrumb(
            "test_crash:\(id.rawValue)",
            data: ["id": id.rawValue, "kind": id.kind.rawValue]
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
            Thread.sleep(forTimeInterval: freezeSeconds)
        }
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
