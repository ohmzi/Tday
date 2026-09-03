import SwiftData
import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// Pure mapping between the "Default home screen" preference's wire value and `RootFeedTab`.
final class RootFeedTabDefaultHomeScreenMappingTests: XCTestCase {
    func testKnownApiValuesMapToTheMatchingTab() {
        XCTAssertEqual(rootFeedTabFromDefaultHomeScreenApiValue("scheduled"), .scheduledTaskHome)
        XCTAssertEqual(rootFeedTabFromDefaultHomeScreenApiValue("floater"), .floaterTaskHome)
    }

    func testUnrecognizedOrAbsentValuesFallBackToScheduled() {
        XCTAssertEqual(rootFeedTabFromDefaultHomeScreenApiValue(nil), .scheduledTaskHome)
        XCTAssertEqual(rootFeedTabFromDefaultHomeScreenApiValue(""), .scheduledTaskHome)
        XCTAssertEqual(rootFeedTabFromDefaultHomeScreenApiValue("unknown"), .scheduledTaskHome)
    }

    func testTabToApiValueIsTheInverseOfTheMapping() {
        for tab: RootFeedTab in [.scheduledTaskHome, .floaterTaskHome] {
            XCTAssertEqual(
                rootFeedTabFromDefaultHomeScreenApiValue(tab.defaultHomeScreenApiValue),
                tab
            )
        }
    }
}

/// `SettingsRepository`'s Local Mode path for the default-home-screen preference: reads and
/// writes go straight to the offline cache with no network involved, unlike server mode (which
/// the backend's own `PreferencesRoutesTest` already covers for the wire contract).
@MainActor
final class SettingsRepositoryDefaultHomeScreenLocalModeTests: XCTestCase {
    private var secureStore: SecureStore!
    private var defaults: UserDefaults!
    private var defaultsSuiteName: String!
    private var modelContainer: ModelContainer!
    private var cacheManager: OfflineCacheManager!
    private var settingsRepository: SettingsRepository!

    override func setUp() {
        super.setUp()
        defaultsSuiteName = "com.ohmz.tday.tests.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: defaultsSuiteName)!
        defaults.removePersistentDomain(forName: defaultsSuiteName)
        secureStore = SecureStore(
            service: "com.ohmz.tday.tests.secure-store.\(UUID().uuidString)",
            defaults: defaults
        )
        secureStore.setAppDataMode(.local)

        modelContainer = try! ModelContainer(
            for: CachedTodoEntity.self,
            CachedFloaterEntity.self,
            CachedListEntity.self,
            CachedFloaterListEntity.self,
            CachedCompletedEntity.self,
            CachedCompletedFloaterEntity.self,
            PendingMutationEntity.self,
            SyncMetadataEntity.self,
            configurations: ModelConfiguration(isStoredInMemoryOnly: true)
        )
        cacheManager = OfflineCacheManager(modelContainer: modelContainer, secureStore: secureStore)

        let networkConfiguration = NetworkConfiguration(
            secureStore: secureStore,
            serverURLState: ServerURLState(currentURL: nil),
            cookieStore: CookieStore(secureStore: secureStore)
        )
        settingsRepository = SettingsRepository(
            api: TdayAPIService(configuration: networkConfiguration),
            cacheManager: cacheManager,
            secureStore: secureStore
        )
    }

    override func tearDown() {
        settingsRepository = nil
        cacheManager = nil
        modelContainer = nil
        secureStore.clearAllUserValues()
        defaults.removePersistentDomain(forName: defaultsSuiteName)
        secureStore = nil
        defaults = nil
        defaultsSuiteName = nil
        super.tearDown()
    }

    func testSnapshotDefaultsToScheduledWhenTheCacheIsEmpty() {
        XCTAssertEqual(settingsRepository.defaultHomeScreenSnapshot(), "scheduled")
    }

    func testSetDefaultHomeScreenPersistsToTheCacheWithoutTouchingTheNetwork() async throws {
        _ = try await settingsRepository.setDefaultHomeScreen("floater")

        XCTAssertEqual(settingsRepository.defaultHomeScreenSnapshot(), "floater")
    }

    func testRefreshDefaultHomeScreenReturnsTheCachedValueUntouchedInLocalMode() async throws {
        _ = try await settingsRepository.setDefaultHomeScreen("floater")

        let refreshed = await settingsRepository.refreshDefaultHomeScreen()

        XCTAssertEqual(refreshed, "floater")
    }
}
