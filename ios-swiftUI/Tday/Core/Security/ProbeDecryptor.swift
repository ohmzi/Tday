import Foundation
import CryptoKit
import LocalAuthentication
import Observation
#if canImport(WidgetKit)
import WidgetKit
#endif

struct ProbeCompatibilityPayload: Codable, Equatable {
    let appVersion: String
    let updateRequired: Bool
    let compatibilityMode: String?
}

enum ProbeDecryptor {
    private static let ivLength = 12

    static func decrypt(_ encryptedBase64URL: String) -> ProbeCompatibilityPayload? {
        guard let keyString = Bundle.main.object(forInfoDictionaryKey: "TdayProbeEncryptionKey") as? String,
              !keyString.isEmpty,
              !keyString.hasPrefix("$(") else {
            return nil
        }

        guard let keyData = base64URLDecode(keyString),
              keyData.count == 32,
              let blob = base64URLDecode(encryptedBase64URL),
              blob.count > ivLength else {
            return nil
        }

        do {
            let key = SymmetricKey(data: keyData)
            let sealedBox = try AES.GCM.SealedBox(combined: blob)
            let plaintext = try AES.GCM.open(sealedBox, using: key)
            return try JSONDecoder().decode(ProbeCompatibilityPayload.self, from: plaintext)
        } catch {
            return nil
        }
    }

    private static func base64URLDecode(_ input: String) -> Data? {
        var base64 = input
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let remainder = base64.count % 4
        if remainder > 0 {
            base64.append(String(repeating: "=", count: 4 - remainder))
        }
        return Data(base64Encoded: base64)
    }
}

// MARK: - App lock

/// Opt-in "Require Face ID / Touch ID to open T'Day". DEFAULT OFF — with nothing stored, the
/// app behaves exactly as it did before this setting existed. UserDefaults-backed, mirroring
/// ThemeStore's style; the flag itself is not a secret.
///
/// Also mirrored into the App Group suite so the widget extension — a separate process with
/// its own `UserDefaults.standard` container — can read it too (see
/// `TdayWidget/TodayTasksWidget.swift`'s `WidgetAppLockStore`). `.standard` stays the primary
/// store so no existing install's saved value moves or resets.
struct AppLockStore {
    private let defaults = UserDefaults.standard
    private let sharedDefaults = UserDefaults(suiteName: appGroupSuiteName)
    private static let key = "app.lock.enabled"
    static let appGroupSuiteName = "group.com.ohmz.tday"

    var isEnabled: Bool {
        get { defaults.object(forKey: Self.key) as? Bool ?? false }
        nonmutating set {
            defaults.set(newValue, forKey: Self.key)
            sharedDefaults?.set(newValue, forKey: Self.key)
        }
    }

    /// Re-mirrors the current value into the App Group suite. Called once at every cold
    /// launch (see `AppLockController.init`) so an install that already had the lock on
    /// before the widget learned about this flag gets covered too, not just future toggles.
    nonmutating func mirrorToSharedStore() {
        sharedDefaults?.set(isEnabled, forKey: Self.key)
    }
}

/// What the lock layer has to be rendering right now.
///
/// Extracted from the view so the two places that draw it — the window that sits above every
/// presented sheet, and the in-hierarchy fallback overlay — cannot drift apart, and so the
/// "inert while disabled" promise is testable without a running scene.
enum AppLockCoverMode: Equatable {
    /// Draw nothing at all. The only mode reachable while the setting is off.
    case hidden
    /// Full gate with the Unlock button.
    case gate
    /// Contentless cover for the app-switcher snapshot, before the gate re-arms.
    case privacyCover
}

/// Drives the biometric gate: the locked state, the LocalAuthentication call, and (via the
/// view) the app-switcher privacy cover.
///
/// This gate hides the UI. It deliberately holds NO key material and gates nothing on disk:
/// the widget snapshot and the widget's session file stay readable at
/// `.completeUntilFirstUserAuthentication`, so home-screen widgets keep rendering while the
/// device is locked whether or not this setting is on.
@MainActor
@Observable
final class AppLockController {
    private(set) var isLocked: Bool
    private(set) var isAuthenticating = false
    /// Set only when authentication actually failed — a user-initiated cancel leaves it nil so
    /// the gate doesn't accuse them of anything.
    private(set) var failureMessage: String?

    private let store: AppLockStore

    var isEnabled: Bool { store.isEnabled }

    init(store: AppLockStore = AppLockStore()) {
        self.store = store
        // Cold start: locked from the first frame when enabled, so no content renders first.
        isLocked = store.isEnabled
        // Covers an install that already had the lock on before the widget learned to read
        // this flag — every launch re-syncs it, not just the next toggle.
        store.mirrorToSharedStore()
        // Mirroring alone doesn't repaint an already-on-screen widget — WidgetKit only
        // re-renders on an explicit reload or its own timeline schedule (up to ~30 min
        // away). Without this, a device that already had the lock on before this build
        // installed would keep showing its last (unlocked-era) snapshot until then.
        // Scoped to the enabled case: when the lock is off there is nothing stale to hide.
        #if canImport(WidgetKit)
        if store.isEnabled {
            WidgetCenter.shared.reloadAllTimelines()
        }
        #endif
    }

    func coverMode(isSceneActive: Bool) -> AppLockCoverMode {
        Self.coverMode(isEnabled: isEnabled, isLocked: isLocked, isSceneActive: isSceneActive)
    }

    /// `isEnabled` is checked first and wins outright: with the setting off there is no state
    /// this can be in that renders anything, which is what keeps the default configuration
    /// byte-for-byte the app it was before the lock existed.
    nonisolated static func coverMode(isEnabled: Bool, isLocked: Bool, isSceneActive: Bool) -> AppLockCoverMode {
        guard isEnabled else {
            return .hidden
        }
        if isLocked {
            return .gate
        }
        // Covers the snapshot iOS takes for the app switcher while the app is merely leaving the
        // foreground — the gate itself only re-arms at .background.
        return isSceneActive ? .hidden : .privacyCover
    }

    /// Re-arms the gate when the app leaves the foreground.
    ///
    /// Also the point where a toggle flipped in Settings takes effect: the setting is read
    /// fresh from the store here rather than cached, so turning the lock on arms it at the next
    /// backgrounding, and turning it off clears the armed state.
    func lockIfEnabled() {
        guard store.isEnabled else {
            isLocked = false
            return
        }
        isLocked = true
        failureMessage = nil
    }

    /// Prompts only when the gate is actually armed, so this is a no-op in the default
    /// (disabled) configuration and cannot stack prompts.
    func authenticateIfNeeded() async {
        guard store.isEnabled, isLocked, !isAuthenticating else {
            return
        }
        await authenticate()
    }

    func authenticate() async {
        guard store.isEnabled else {
            isLocked = false
            return
        }
        isAuthenticating = true
        defer { isAuthenticating = false }

        let context = LAContext()
        // .deviceOwnerAuthentication, not .deviceOwnerAuthenticationWithBiometrics: the device
        // passcode is the fallback, so a failed/unavailable Face ID still has a way through.
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: nil) else {
            // No biometrics AND no passcode enrolled: there is no credential left to check, so
            // staying locked would brick the app with no recovery. Fail open — this gate hides
            // the UI, it is not what protects data at rest (file protection is).
            isLocked = false
            failureMessage = nil
            return
        }

        do {
            let succeeded = try await context.evaluatePolicy(
                .deviceOwnerAuthentication,
                localizedReason: L("Unlock T'Day")
            )
            isLocked = !succeeded
            failureMessage = succeeded ? nil : L("Could not verify it's you. Try again.")
        } catch {
            isLocked = true
            failureMessage = Self.isUserCancellation(error) ? nil : L("Could not verify it's you. Try again.")
        }
    }

    /// A cancel (user tapped Cancel, or the system pulled the prompt for a call/notification)
    /// is not a failure to report — the gate just stays up with its Unlock button.
    private static func isUserCancellation(_ error: Error) -> Bool {
        guard let laError = error as? LAError else {
            return false
        }
        switch laError.code {
        case .userCancel, .systemCancel, .appCancel:
            return true
        default:
            return false
        }
    }
}
