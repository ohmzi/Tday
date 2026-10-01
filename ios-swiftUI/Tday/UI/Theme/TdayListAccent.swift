import SwiftUI

/// A list's own accent — its Lucide glyph and its colour — resolved from the opaque KEYS the
/// list carries, with no SwiftUI view, environment or app singleton involved.
///
/// This file exists to be compiled by BOTH the app target and the TdayWidget extension, which
/// is the whole reason the glyph table below no longer lives in `TdayTheme.swift`: that file is
/// a SwiftUI theme — environments, `UIViewRepresentable`, global appearance proxies — and adding
/// it wholesale to the extension would drag all of that across a process boundary that has
/// neither `AppContainer` nor SwiftData linked in. Only the two lookup tables cross; the views
/// that draw them stay where they are. Same pattern as `TodayWidgetDayWindow.swift` and
/// `TdayMotionGenerated.swift`, which the extension already compiles.
///
/// KEYS, not pixels, are what reach the widget. A list's colour travels to the widget snapshot
/// as "TEAL", never as `#2EB8AC`: a key is a name this layer owns and can re-point, and a hex
/// baked into a persisted snapshot is a decision frozen at write time that no later theme —
/// a dark-mode variant, a contrast mode, a palette revision — can revise without the app
/// re-writing every snapshot it has ever written. See `WidgetConfigurableListEntry`.

// MARK: - Glyph

// List icons are Lucide glyphs shared across web/Android/iOS — see docs/ICONS.md.
// Each iconKey maps to a template SVG imageset named Lucide<Glyph>.
private let tdayLucideListAssetTable: [String: String] = [
    "inbox": "LucideInbox", "sun": "LucideSun", "calendar": "LucideCalendar",
    "schedule": "LucideClock", "flag": "LucideFlag", "check": "LucideCheck",
    "smile": "LucideSmile", "list": "LucideList", "bookmark": "LucideBookmark",
    "key": "LucideKey", "gift": "LucideGift", "cake": "LucideCake",
    "school": "LucideGraduationCap", "bag": "LucideBackpack", "edit": "LucidePencil",
    "document": "LucideFileText", "book": "LucideBook", "work": "LucideBriefcaseBusiness",
    "wallet": "LucideWalletCards", "money": "LucideCircleDollarSign", "fitness": "LucideDumbbell",
    "run": "LucideActivity", "food": "LucideUtensils", "drink": "LucideWine",
    "health": "LucideBriefcaseMedical", "monitor": "LucideMonitor", "music": "LucideMusic",
    "computer": "LucideMonitor", "game": "LucideGamepad2", "headphones": "LucideHeadphones",
    "eco": "LucideLeaf", "pets": "LucidePawPrint", "child": "LucideBaby",
    "family": "LucideUsersRound", "basket": "LucideShoppingBasket", "cart": "LucideShoppingCart",
    "mall": "LucideShoppingBag", "inventory": "LucideArchive", "soccer": "LucideCircle",
    "baseball": "LucideCircle", "basketball": "LucideCircle", "football": "LucideCircle",
    "tennis": "LucideCircle", "train": "LucideTrain", "flight": "LucidePlane",
    "boat": "LucideShip", "car": "LucideCar", "umbrella": "LucideUmbrella",
    "drop": "LucideDroplet", "snow": "LucideSnowflake", "fire": "LucideFlame",
    "tools": "LucideHammer", "scissors": "LucideScissors", "architecture": "LucideLandmark",
    "code": "LucideCode", "idea": "LucideLightbulb", "chat": "LucideMessageCircle",
    "alert": "LucideTriangleAlert", "star": "LucideStar", "heart": "LucideHeart",
    "circle": "LucideCircle", "square": "LucideSquare", "triangle": "LucideTriangle",
    "home": "LucideHouse", "city": "LucideBuilding2", "bank": "LucideLandmark",
    "camera": "LucideCamera", "palette": "LucidePalette",
]

/// Asset-catalog name of the shared Lucide template glyph for a list iconKey.
func tdayLucideListAsset(_ key: String?) -> String {
    let candidate = (key ?? "").trimmingCharacters(in: .whitespaces).lowercased()
    let normalized: String
    switch candidate {
    case "briefcase": normalized = "work"
    case "cocktail": normalized = "drink"
    case "travel": normalized = "flight"
    case "": normalized = "inbox"
    default: normalized = candidate
    }
    return tdayLucideListAssetTable[normalized] ?? "LucideInbox"
}

// MARK: - Colour

/// The list-colour palette, key by key, in the order the pickers offer it.
///
/// Numerically identical to Android's `TdayListColorOptions` in `TdaySemanticColors.kt` and to
/// web's list-colour tokens — the same fifteen sRGB values, because one list has one colour on
/// every client the user opens.
///
/// A SECOND copy of these values already exists on this platform, in
/// `todoListAccentColor(for:)` (`Feature/Todos/TodoListScreen.swift`) and in
/// `scheduledTaskHomeListColorOptions` (`Feature/ScheduledTaskHome/ScheduledTaskHomeScreen.swift`).
/// Those are the app's own feature-local copies and are deliberately untouched here: both live
/// in files this change does not own, and repointing them is a separate edit. What stands in for
/// that edit is `TdayListAccentTests`, which asserts this table and `todoListAccentColor` answer
/// the same `Color` for every key — so the fork is pinned even while it exists, and whoever does
/// repoint them will not have to re-derive the values.
let tdayListAccentColorKeys: [String] = [
    "PINK", "GOLD", "DEEP_BLUE", "CORAL", "TEAL", "SLATE", "BLUE", "PURPLE",
    "ROSE", "LIGHT_RED", "BRICK", "YELLOW", "LIME", "ORANGE", "RED",
]

/// Mirrors Android's `TDAY_DEFAULT_LIST_COLOR_KEY`.
let tdayDefaultListAccentColorKey = "PINK"

private let tdayListAccentRGBByKey: [String: UInt32] = [
    "PINK": 0xE05299,
    "GOLD": 0xE8A530,
    "DEEP_BLUE": 0x3C9ADD,
    "CORAL": 0xE6664C,
    "TEAL": 0x2EB8AC,
    "SLATE": 0x3E4774,
    "BLUE": 0x6EA8E1,
    "PURPLE": 0x7D67B6,
    "ROSE": 0xD1617D,
    "LIGHT_RED": 0xE06C6C,
    "BRICK": 0xC64C39,
    "YELLOW": 0xE8BA30,
    "LIME": 0x46B963,
    "ORANGE": 0xE28736,
    "RED": 0xDF3A3A,
]

/// The list's colour, or nil when the list has none this layer recognises.
///
/// Nil rather than the default pink is the point of this entry: a caller that has something
/// better to fall back to — the widget, whose two kinds each already have an accent of their
/// own — must be able to tell "no colour" from "pink", and a defaulting resolver throws that
/// distinction away at the only place it matters. Twin of Android's
/// `tdayListAccentColorOrNull`, for the same reason.
func tdayListAccentColorOrNil(colorKey: String?) -> Color? {
    guard let key = tdayNormalizedListAccentColorKeyOrNil(colorKey),
          let rgb = tdayListAccentRGBByKey[key] else {
        return nil
    }
    return tdaySRGB(rgb)
}

/// The list's colour, defaulting to pink — for a caller with nothing else to show.
func tdayListAccentColor(colorKey: String?) -> Color {
    tdayListAccentColorOrNil(colorKey: colorKey)
        ?? tdaySRGB(tdayListAccentRGBByKey[tdayDefaultListAccentColorKey] ?? 0xE05299)
}

/// The canonical spelling of a stored colour key, or nil when it names no colour.
///
/// Case-folded and trimmed because the key arrives from a server DTO, a local cache record or
/// an export file, and none of those three guarantees the casing the picker wrote. GREEN and
/// GRAY are honoured as the historical spellings of LIME and SLATE — the same two aliases
/// Android's normalizer carries, and the same two the app's own
/// `normalizedTodoListColorKey` carries; a list created before the rename still has one stored.
func tdayNormalizedListAccentColorKeyOrNil(_ colorKey: String?) -> String? {
    guard let candidate = colorKey?
        .trimmingCharacters(in: .whitespacesAndNewlines)
        .uppercased(),
        !candidate.isEmpty else {
        return nil
    }
    let normalized: String
    switch candidate {
    case "GREEN": normalized = "LIME"
    case "GRAY": normalized = "SLATE"
    default: normalized = candidate
    }
    return tdayListAccentRGBByKey[normalized] == nil ? nil : normalized
}

private func tdaySRGB(_ rgb: UInt32) -> Color {
    Color(
        .sRGB,
        red: Double((rgb >> 16) & 0xFF) / 255,
        green: Double((rgb >> 8) & 0xFF) / 255,
        blue: Double(rgb & 0xFF) / 255,
        opacity: 1
    )
}
