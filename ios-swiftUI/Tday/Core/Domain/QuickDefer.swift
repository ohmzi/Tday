import Foundation

/// The Quick Defer instants, computed locally (same semantics as the web and
/// Android helpers): +3h, today 19:00, tomorrow 09:00, next Monday 09:00.
/// "Tonight" hides once the evening is effectively here (18:30) so it can
/// never produce an instant in the past.
enum QuickDeferChoice: CaseIterable {
    case laterToday
    case tonight
    case tomorrow
    case nextWeek

    var label: String {
        switch self {
        case .laterToday: return L("Later today")
        case .tonight: return L("Tonight")
        case .tomorrow: return L("Tomorrow morning")
        case .nextWeek: return L("Next week")
        }
    }
}

struct QuickDeferOption {
    let choice: QuickDeferChoice
    let due: Date
}

func quickDeferOptions(now: Date = Date(), calendar: Calendar = .current) -> [QuickDeferOption] {
    let startOfDay = calendar.startOfDay(for: now)
    func at(_ day: Date, hour: Int, minute: Int = 0) -> Date {
        calendar.date(bySettingHour: hour, minute: minute, second: 0, of: day) ?? day
    }

    var options: [QuickDeferOption] = [
        QuickDeferOption(choice: .laterToday, due: now.addingTimeInterval(3 * 3600)),
    ]
    if now < at(startOfDay, hour: 18, minute: 30) {
        options.append(QuickDeferOption(choice: .tonight, due: at(startOfDay, hour: 19)))
    }
    let tomorrow = calendar.date(byAdding: .day, value: 1, to: startOfDay) ?? startOfDay
    options.append(QuickDeferOption(choice: .tomorrow, due: at(tomorrow, hour: 9)))

    // Next Monday, strictly after today.
    var nextMonday = calendar.nextDate(
        after: startOfDay,
        matching: DateComponents(weekday: 2),
        matchingPolicy: .nextTime
    ) ?? startOfDay
    if calendar.isDate(nextMonday, inSameDayAs: startOfDay) {
        nextMonday = calendar.date(byAdding: .day, value: 7, to: nextMonday) ?? nextMonday
    }
    options.append(QuickDeferOption(choice: .nextWeek, due: at(nextMonday, hour: 9)))
    return options
}
