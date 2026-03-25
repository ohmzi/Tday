import Foundation
import Security

final class SecureStore {
    private let service = "com.ohmz.tday.ios.secure-store"
    private let defaults = UserDefaults.standard
    private let trustedHostsKey = "secure.trusted.hosts"
    private let runtimeServerURLKey = "runtime.server.url"
    private let listIconsKey = "list.icons"

    enum Key: String {
        case persistedServerURL = "persisted-server-url"
        case deviceID = "device-id"
        case lastEmail = "last-email"
    }

    func loadPersistedServerURL() -> URL? {
        guard let raw = loadString(for: .persistedServerURL) else {
            return nil
        }
        return URL(string: raw)
    }

    func savePersistedServerURL(_ url: URL) {
        saveString(url.absoluteString, for: .persistedServerURL)
    }

    func clearPersistedServerURL() {
        deleteValue(for: .persistedServerURL)
    }

    func loadOrCreateDeviceID() -> String {
        if let existing = loadString(for: .deviceID), !existing.isEmpty {
            return existing
        }

        let created = UUID().uuidString.lowercased()
        saveString(created, for: .deviceID)
        return created
    }

    func saveLastEmail(_ email: String) {
        saveString(email, for: .lastEmail)
    }

    func loadLastEmail() -> String? {
        loadString(for: .lastEmail)
    }

    func clearLastEmail() {
        deleteValue(for: .lastEmail)
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

    func clearAllUserValues() {
        clearPersistedServerURL()
        clearLastEmail()
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

    func lastEmail() -> String? {
        loadLastEmail()
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
        saveString(value, forRawKey: key.rawValue)
    }

    private func loadString(for key: Key) -> String? {
        loadString(forRawKey: key.rawValue)
    }

    private func deleteValue(for key: Key) {
        deleteValue(forRawKey: key.rawValue)
    }

    private func saveString(_ value: String, forRawKey key: String) {
        guard let data = value.data(using: .utf8) else {
            return
        }

        let query = keychainQuery(for: key)
        let attributes = [kSecValueData as String: data]
        let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            var insertQuery = query
            insertQuery[kSecValueData as String] = data
            SecItemAdd(insertQuery as CFDictionary, nil)
        }
    }

    private func loadString(forRawKey key: String) -> String? {
        var query = keychainQuery(for: key)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else {
            return nil
        }

        return String(data: data, encoding: .utf8)
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
