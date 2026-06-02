import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

final class ServerURLPersistenceTests: XCTestCase {
    private var secureStore: SecureStore!
    private var defaults: UserDefaults!
    private var defaultsSuiteName: String!

    override func setUp() {
        super.setUp()
        defaultsSuiteName = "com.ohmz.tday.tests.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: defaultsSuiteName)!
        defaults.removePersistentDomain(forName: defaultsSuiteName)
        secureStore = SecureStore(
            service: "com.ohmz.tday.tests.secure-store.\(UUID().uuidString)",
            defaults: defaults
        )
    }

    override func tearDown() {
        secureStore.clearAllUserValues()
        defaults.removePersistentDomain(forName: defaultsSuiteName)
        secureStore = nil
        defaults = nil
        defaultsSuiteName = nil
        super.tearDown()
    }

    func testPersistedServerURLUsesKeychainInsteadOfDefaults() {
        let url = URL(string: "https://tday.ohmz.cloud")!

        secureStore.savePersistedServerURL(url)

        XCTAssertEqual(secureStore.loadPersistedServerURL(), url)
        XCTAssertNil(defaults.string(forKey: SecureStore.Key.persistedServerURL.rawValue))
    }

    func testAppDataModePersistsLocalAndInfersServerFromSavedURL() {
        XCTAssertEqual(secureStore.appDataMode(), .unset)

        secureStore.savePersistedServerURL(URL(string: "https://tday.ohmz.cloud")!)
        XCTAssertEqual(secureStore.appDataMode(), .server)

        secureStore.setAppDataMode(.local)
        XCTAssertEqual(secureStore.appDataMode(), .local)

        secureStore.clearAllUserValues()
        XCTAssertEqual(secureStore.appDataMode(), .unset)
    }

    func testUserCleanupCanPreservePersistedServerURL() {
        let url = URL(string: "https://tday.ohmz.cloud")!
        secureStore.savePersistedServerURL(url)
        secureStore.saveLastEmail("user@example.com")

        secureStore.clearAllUserValues(preservingServerURL: true)

        XCTAssertEqual(secureStore.loadPersistedServerURL(), url)
        XCTAssertNil(secureStore.loadLastEmail())
    }

    func testSavedServerURLSuggestionSurvivesReinstallCleanup() {
        let url = URL(string: "https://demo.tday.example")!
        secureStore.saveServerURLSuggestion(url)

        XCTAssertTrue(secureStore.clearInstallScopedValuesIfAppReinstalled())

        XCTAssertEqual(secureStore.loadServerURLSuggestion(), url)
    }

    func testReinstallCleanupClearsInstallScopedValues() {
        let url = URL(string: "https://tday.ohmz.cloud")!
        secureStore.savePersistedServerURL(url)
        secureStore.saveLastEmail("user@example.com")
        secureStore.savePersistedAuthSessionCookieData(Data("cookie".utf8))

        XCTAssertTrue(secureStore.clearInstallScopedValuesIfAppReinstalled())

        XCTAssertNil(secureStore.loadPersistedServerURL())
        XCTAssertNil(secureStore.loadLastEmail())
        XCTAssertNil(secureStore.loadPersistedAuthSessionCookieData())
    }

    func testReinstallCleanupRunsOnlyOncePerInstall() {
        let url = URL(string: "https://tday.ohmz.cloud")!

        XCTAssertTrue(secureStore.clearInstallScopedValuesIfAppReinstalled())
        secureStore.savePersistedServerURL(url)
        secureStore.saveLastEmail("user@example.com")
        secureStore.savePersistedAuthSessionCookieData(Data("cookie".utf8))

        XCTAssertFalse(secureStore.clearInstallScopedValuesIfAppReinstalled())

        XCTAssertEqual(secureStore.loadPersistedServerURL(), url)
        XCTAssertEqual(secureStore.loadLastEmail(), "user@example.com")
        XCTAssertEqual(secureStore.loadPersistedAuthSessionCookieData(), Data("cookie".utf8))
    }

    func testReinstallCleanupClearsPersistedURLBeforeServerURLStateIsCreated() {
        let url = URL(string: "https://tday.ohmz.cloud")!
        secureStore.savePersistedServerURL(url)
        secureStore.savePersistedAuthSessionCookieData(Data("cookie".utf8))

        let didCleanInstallScopedValues = secureStore.clearInstallScopedValuesIfAppReinstalled()
        let serverURLState = ServerURLState(currentURL: secureStore.loadPersistedServerURL())

        XCTAssertTrue(didCleanInstallScopedValues)
        XCTAssertNil(serverURLState.currentURL)
        XCTAssertNil(secureStore.loadPersistedAuthSessionCookieData())
        XCTAssertEqual(secureStore.appDataMode(), .unset)
    }
}
