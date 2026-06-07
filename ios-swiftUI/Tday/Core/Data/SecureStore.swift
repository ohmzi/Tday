import Foundation
import Security
import os

final class SecureStore {
    private static let log = Logger(subsystem: "com.ohmz.tday.ios", category: "SecureStore")
    private let service: String
    private let defaults: UserDefaults
    private let trustedHostsKey = "secure.trusted.hosts"
    private let runtimeServerURLKey = "runtime.server.url"
    private let listIconsKey = "list.icons"
    private let installSentinelKey = "app.install.sentinel"

    init(
        service: String = "com.ohmz.tday.ios.secure-store",
        defaults: UserDefaults = .standard
    ) {
        self.service = service
        self.defaults = defaults
    }

    enum Key: String {
        case persistedServerURL = "persisted-server-url"
        case deviceID = "device-id"
        case lastUsername = "last-username"
        case persistedAuthSessionCookie = "persisted-auth-session-cookie"
        case cachedSessionUser = "cached-session-user"
        case savedServerURLSuggestion = "saved-server-url-suggestion"
        case appDataMode = "app-data-mode-v1"
        case pendingApprovalUsername = "pending-approval-username"
        case pendingApprovalPassword = "pending-approval-password"
    }

    // MARK: - Pending admin approval

    /// Credentials kept after a registration/login that is awaiting admin approval, so
    /// the holding screen survives app relaunch and can silently re-attempt login until
    /// the account is approved. Cleared on approval, sign-out, or uninstall.
    func savePendingApproval(username: String, password: String) {
        saveString(username, for: .pendingApprovalUsername)
        saveString(password, for: .pendingApprovalPassword)
    }

    func loadPendingApproval() -> (username: String, password: String)? {
        guard let username = loadString(for: .pendingApprovalUsername),
              let password = loadString(for: .pendingApprovalPassword) else {
            return nil
        }
        return (username, password)
    }

    func clearPendingApproval() {
        deleteValue(for: .pendingApprovalUsername)
        deleteValue(for: .pendingApprovalPassword)
    }

    func appDataMode() -> AppDataMode {
        if let rawValue = loadString(for: .appDataMode),
           let mode = AppDataMode(rawValue: rawValue) {
            return mode
        }
        return (loadRuntimeServerURL() == nil && loadPersistedServerURL() == nil) ? .unset : .server
    }

    func isLocalMode() -> Bool {
        appDataMode() == .local
    }

    func setAppDataMode(_ mode: AppDataMode) {
        saveString(mode.rawValue, for: .appDataMode)
    }

    func clearAppDataMode() {
        deleteValue(for: .appDataMode)
    }

    func loadPersistedServerURL() -> URL? {
        guard let raw = loadString(for: .persistedServerURL) else {
            return nil
        }
        return URL(string: raw)
    }

    func savePersistedServerURL(_ url: URL) {
        saveString(url.absoluteString, for: .persistedServerURL)
        setAppDataMode(.server)
    }

    func clearPersistedServerURL() {
        deleteValue(for: .persistedServerURL)
    }

    func saveServerURLSuggestion(_ url: URL) {
        saveString(url.absoluteString, for: .savedServerURLSuggestion)
    }

    func loadServerURLSuggestion() -> URL? {
        guard let raw = loadString(for: .savedServerURLSuggestion) else {
            return nil
        }
        return URL(string: raw)
    }

    func loadOrCreateDeviceID() -> String {
        if let existing = loadString(for: .deviceID), !existing.isEmpty {
            return existing
        }

        let created = UUID().uuidString.lowercased()
        saveString(created, for: .deviceID)
        return created
    }

    func saveLastUsername(_ username: String) {
        saveString(username, for: .lastUsername)
    }

    func loadLastUsername() -> String? {
        loadString(for: .lastUsername)
    }

    func clearLastUsername() {
        deleteValue(for: .lastUsername)
    }

    func loadCachedSessionUserData() -> Data? {
        loadData(for: .cachedSessionUser)
    }

    func saveCachedSessionUserData(_ data: Data) {
        saveData(data, for: .cachedSessionUser)
    }

    func clearCachedSessionUser() {
        deleteValue(for: .cachedSessionUser)
    }

    func trustedFingerprint(for host: String) -> String? {
        guard !host.isEmpty else {
            return nil
        }
        return loadString(forRawKey: trustKey(for: host))
    }

    func saveTrustedFingerprint(_ fingerprint: String, for host: String) {
        guard !host.isEmpty else {
            return
        }
        saveString(fingerprint, forRawKey: trustKey(for: host))
        var hosts = defaults.stringArray(forKey: trustedHostsKey) ?? []
        if !hosts.contains(host) {
            hosts.append(host)
            defaults.set(hosts, forKey: trustedHostsKey)
        }
    }

    func clearTrustedFingerprint(for host: String) {
        guard !host.isEmpty else {
            return
        }
        deleteValue(forRawKey: trustKey(for: host))
        let remainingHosts = (defaults.stringArray(forKey: trustedHostsKey) ?? []).filter { $0 != host }
        defaults.set(remainingHosts, forKey: trustedHostsKey)
    }

    func clearAllTrustedFingerprints() {
        let hosts = defaults.stringArray(forKey: trustedHostsKey) ?? []
        for host in hosts {
            deleteValue(forRawKey: trustKey(for: host))
        }
        defaults.removeObject(forKey: trustedHostsKey)
    }

    func clearAllUserValues(preservingServerURL: Bool = false) {
        if !preservingServerURL {
            clearPersistedServerURL()
            clearAppDataMode()
        }
        clearPersistedAuthSessionCookie()
        clearCachedSessionUser()
        clearPendingApproval()
        clearLastUsername()
        clearAllTrustedFingerprints()
        defaults.removeObject(forKey: runtimeServerURLKey)
        defaults.removeObject(forKey: listIconsKey)
    }

    func hasServerURL() -> Bool {
        loadRuntimeServerURL() != nil || loadPersistedServerURL() != nil
    }

    func serverURL() -> String? {
        loadRuntimeServerURL()?.absoluteString ?? loadPersistedServerURL()?.absoluteString
    }

    @discardableResult
    func saveServerURL(rawURL: String, persist: Bool) throws -> String {
        guard let normalized = normalizeServerURL(rawURL) else {
            throw APIError(message: "Enter a valid server URL", statusCode: nil)
        }
        defaults.set(normalized.absoluteString, forKey: runtimeServerURLKey)
        if persist {
            savePersistedServerURL(normalized)
        }
        return normalized.absoluteString
    }

    func persistRuntimeServerURL() throws {
        guard let runtime = loadRuntimeServerURL() else {
            return
        }
        savePersistedServerURL(runtime)
    }

    func clearSessionOnly() {
        defaults.removeObject(forKey: runtimeServerURLKey)
    }

    func clearAllLocalData() {
        clearAllUserValues()
    }

    func lastUsername() -> String? {
        loadLastUsername()
    }

    func loadPersistedAuthSessionCookieData() -> Data? {
        loadData(for: .persistedAuthSessionCookie)
    }

    func savePersistedAuthSessionCookieData(_ data: Data) {
        saveData(data, for: .persistedAuthSessionCookie)
    }

    func clearPersistedAuthSessionCookie() {
        deleteValue(for: .persistedAuthSessionCookie)
    }

    @discardableResult
    func clearInstallScopedValuesIfAppReinstalled() -> Bool {
        guard defaults.string(forKey: installSentinelKey) == nil else {
            return false
        }

        clearPersistedServerURL()
        clearAppDataMode()
        clearPersistedAuthSessionCookie()
        clearCachedSessionUser()
        clearPendingApproval()
        clearLastUsername()
        clearAllTrustedFingerprints()
        defaults.removeObject(forKey: runtimeServerURLKey)
        defaults.removeObject(forKey: listIconsKey)
        defaults.set(UUID().uuidString.lowercased(), forKey: installSentinelKey)
        return true
    }

    func normalizeServerURL(_ rawURL: String) -> URL? {
        let trimmed = rawURL.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return nil
        }
        let candidate = trimmed.contains("://") ? trimmed : "https://\(trimmed)"
        guard var components = URLComponents(string: candidate) else {
            return nil
        }
        components.path = components.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        components.query = nil
        components.fragment = nil
        return components.url
    }

    func buildAbsoluteAppURL(path: String) -> URL? {
        guard let serverURLString = serverURL(), let baseURL = URL(string: serverURLString) else {
            return nil
        }
        return baseURL.appending(path: path.trimmingCharacters(in: CharacterSet(charactersIn: "/")))
    }

    func serverTrustKey(for rawURL: String) -> String? {
        guard let normalized = normalizeServerURL(rawURL), let host = normalized.host else {
            return nil
        }
        if let port = normalized.port {
            return "\(host.lowercased()):\(port)"
        }
        return host.lowercased()
    }

    func saveTrustedFingerprint(_ fingerprint: String, serverTrustKey: String) {
        saveTrustedFingerprint(fingerprint, for: serverTrustKey)
    }

    func saveListIcon(_ iconKey: String, for listId: String) {
        var icons = defaults.dictionary(forKey: listIconsKey) as? [String: String] ?? [:]
        icons[listId] = iconKey
        defaults.set(icons, forKey: listIconsKey)
    }

    func listIcon(for listId: String) -> String? {
        let icons = defaults.dictionary(forKey: listIconsKey) as? [String: String] ?? [:]
        return icons[listId]
    }

    private func loadRuntimeServerURL() -> URL? {
        guard let raw = defaults.string(forKey: runtimeServerURLKey) else {
            return nil
        }
        return URL(string: raw)
    }

    private func trustKey(for host: String) -> String {
        "fingerprint.\(host.lowercased())"
    }

    private func saveString(_ value: String, for key: Key) {
        guard let data = value.data(using: .utf8) else {
            return
        }
        saveData(data, for: key)
    }

    private func loadString(for key: Key) -> String? {
        guard let data = loadData(for: key) else {
            return nil
        }
        return String(data: data, encoding: .utf8)
    }

    private func deleteValue(for key: Key) {
        deleteValue(forRawKey: key.rawValue)
    }

    private func saveData(_ data: Data, for key: Key) {
        saveData(data, forRawKey: key.rawValue)
    }

    private func loadData(for key: Key) -> Data? {
        loadData(forRawKey: key.rawValue)
    }

    private func saveString(_ value: String, forRawKey key: String) {
        guard let data = value.data(using: .utf8) else {
            return
        }
        saveData(data, forRawKey: key)
    }

    private func saveData(_ data: Data, forRawKey key: String) {
        let query = keychainQuery(for: key)
        // AfterFirstUnlock so background contexts (CarPlay intents, widgets, App
        // Intents) can read the session/cookie on a locked-but-already-unlocked
        // device; the default (WhenUnlocked) denies those reads. Set on writes only,
        // not on the search query (which would otherwise fail to match older items).
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
        ]
        let updateStatus = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if updateStatus == errSecItemNotFound {
            var insertQuery = query
            insertQuery[kSecValueData as String] = data
            insertQuery[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
            let addStatus = SecItemAdd(insertQuery as CFDictionary, nil)
            if addStatus != errSecSuccess {
                // Don't fail silently: a dropped write means the user looks logged in
                // now but is signed out on next launch with no diagnostic.
                Self.log.error("keychain add failed for \(key, privacy: .public): \(addStatus)")
            }
        } else if updateStatus != errSecSuccess {
            Self.log.error("keychain update failed for \(key, privacy: .public): \(updateStatus)")
        }
    }

    private func loadString(forRawKey key: String) -> String? {
        guard let data = loadData(forRawKey: key) else {
            return nil
        }
        return String(data: data, encoding: .utf8)
    }

    private func loadData(forRawKey key: String) -> Data? {
        var query = keychainQuery(for: key)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else {
            return nil
        }

        return data
    }

    private func deleteValue(forRawKey key: String) {
        let query = keychainQuery(for: key)
        SecItemDelete(query as CFDictionary)
    }

    private func keychainQuery(for key: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
        ]
    }
}
