import Foundation

struct VersionCompatibility: Equatable {
    let appVersion: String
    let updateRequired: Bool
}

enum VersionCheckResult: Equatable {
    case compatible
    case appUpdateRequired(requiredVersion: String)
    case serverUpdateRequired(serverVersion: String)
}

func checkVersionCompatibility(payload: ProbeCompatibilityPayload?) -> VersionCheckResult {
    guard let payload else { return .compatible }

    let localVersion = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0.0.0"
    let cmp = AppViewModel.compareVersions(localVersion, payload.appVersion)

    guard payload.updateRequired else { return .compatible }

    if cmp < 0 {
        return .appUpdateRequired(requiredVersion: payload.appVersion)
    } else if cmp > 0 {
        return .serverUpdateRequired(serverVersion: payload.appVersion)
    }
    return .compatible
}
