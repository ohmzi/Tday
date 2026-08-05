import Foundation
import Observation
import SwiftData

@MainActor
@Observable
final class AppContainer {
    static let shared = AppContainer()

    let secureStore: SecureStore
    let themeStore: ThemeStore
    let languageStore: LanguageStore
    let reminderPreferenceStore: ReminderPreferenceStore
    let serverURLState: ServerURLState
    let cookieStore: CookieStore
    let networkConfiguration: NetworkConfiguration
    let apiService: TdayAPIService
    let modelContainer: ModelContainer
    /// On-disk location of the SwiftData store, kept so the protection attributes can be
    /// re-applied on every foreground/background transition (see `reapplyDatabaseProtection`).
    let modelStoreURL: URL
    let cacheManager: OfflineCacheManager
    let serverConfigRepository: ServerConfigRepository
    let systemCredentialService: SystemCredentialService
    let authRepository: AuthRepository
    let syncManager: SyncManager
    let todoRepository: TodoRepository
    let listRepository: ListRepository
    let floaterListRepository: FloaterListRepository
    let listShareRepository: ListShareRepository
    let completedRepository: CompletedRepository
    let settingsRepository: SettingsRepository
    let dataExportRepository: DataExportRepository
    let realtimeClient: RealtimeClient
    let reminderScheduler: TaskReminderScheduler
    let securityAlertPoller: SecurityAlertPoller
    let dayAheadStore: DayAheadStore
    let dayAheadScheduler: DayAheadScheduler
    let snackbarManager: SnackbarManager
    let undoableDeleteScheduler: UndoableDeleteScheduler
    let bootstrapSession: BootstrapSessionUseCase
    let createTodo: CreateTodoUseCase
    let completeTodo: CompleteTodoUseCase
    let syncAndRefresh: SyncAndRefreshUseCase

    private init() {
        secureStore = SecureStore()
        let didClearInstallScopedValues = secureStore.clearInstallScopedValuesIfAppReinstalled()
        themeStore = ThemeStore()
        languageStore = LanguageStore()
        reminderPreferenceStore = ReminderPreferenceStore()
        serverURLState = ServerURLState(currentURL: secureStore.loadPersistedServerURL())
        cookieStore = CookieStore(
            secureStore: secureStore,
            clearAuthCookiesBeforeRestore: didClearInstallScopedValues
        )
        networkConfiguration = NetworkConfiguration(
            secureStore: secureStore,
            serverURLState: serverURLState,
            cookieStore: cookieStore
        )
        apiService = TdayAPIService(configuration: networkConfiguration)
        modelContainer = try! ModelContainer(
            for: CachedTodoEntity.self,
            CachedFloaterEntity.self,
            CachedListEntity.self,
            CachedFloaterListEntity.self,
            CachedCompletedEntity.self,
            CachedCompletedFloaterEntity.self,
            PendingMutationEntity.self,
            SyncMetadataEntity.self
        )
        // Deliberately NOT built with an explicit `ModelConfiguration(url:)`: the store URL is
        // read back off the container SwiftData already opened. Every existing install holds its
        // cache — and its UNSYNCED pending mutations, the only copy of offline edits — at
        // SwiftData's default location, and a hand-written URL that differs from that default by
        // so much as one path component would quietly open an empty second store and strand
        // them. That is not hypothetical here: because this app declares an App Group, the
        // default lands in the GROUP container ("…/Shared/AppGroup/<id>/Library/Application
        // Support/default.store"), not the app's own Application Support, so the obvious
        // hand-written path would have been the wrong one. Reading it back leaves the file
        // exactly where it is and still gives us the handle needed to protect it.
        //
        // The fallback below never opens anything — it is only a path handed to `apply`, which
        // skips files that do not exist — so a miss degrades to "no attributes stamped", never
        // to a second store.
        modelStoreURL = modelContainer.configurations.first?.url
            ?? URL.applicationSupportDirectory.appending(path: "default.store")
        LocalStoreFileProtection.apply(to: modelStoreURL)
        cacheManager = OfflineCacheManager(modelContainer: modelContainer, secureStore: secureStore)
        serverConfigRepository = ServerConfigRepository(
            secureStore: secureStore,
            serverURLState: serverURLState,
            api: apiService
        )
        systemCredentialService = SystemCredentialService(secureStore: secureStore)
        authRepository = AuthRepository(
            api: apiService,
            secureStore: secureStore,
            serverConfigRepository: serverConfigRepository,
            cacheManager: cacheManager,
            cookieStore: cookieStore,
            themeStore: themeStore,
            reminderPreferenceStore: reminderPreferenceStore
        )
        syncManager = SyncManager(api: apiService, cacheManager: cacheManager, secureStore: secureStore)
        todoRepository = TodoRepository(api: apiService, cacheManager: cacheManager, syncManager: syncManager)
        listRepository = ListRepository(api: apiService, cacheManager: cacheManager, syncManager: syncManager)
        floaterListRepository = FloaterListRepository(api: apiService, cacheManager: cacheManager, syncManager: syncManager)
        listShareRepository = ListShareRepository(api: apiService)
        completedRepository = CompletedRepository(api: apiService, cacheManager: cacheManager, syncManager: syncManager)
        settingsRepository = SettingsRepository(api: apiService, cacheManager: cacheManager, secureStore: secureStore)
        dataExportRepository = DataExportRepository(api: apiService, cacheManager: cacheManager, syncManager: syncManager, secureStore: secureStore)
        realtimeClient = RealtimeClient(configuration: networkConfiguration)
        reminderScheduler = TaskReminderScheduler(reminderPreferenceStore: reminderPreferenceStore)
        securityAlertPoller = SecurityAlertPoller(api: apiService, secureStore: secureStore)
        dayAheadStore = DayAheadStore()
        dayAheadScheduler = DayAheadScheduler(store: dayAheadStore)
        snackbarManager = SnackbarManager()
        undoableDeleteScheduler = UndoableDeleteScheduler(snackbarManager: snackbarManager)
        bootstrapSession = BootstrapSessionUseCase(authRepository: authRepository, syncManager: syncManager)
        createTodo = CreateTodoUseCase(todoRepository: todoRepository)
        completeTodo = CompleteTodoUseCase(todoRepository: todoRepository)
        syncAndRefresh = SyncAndRefreshUseCase(syncManager: syncManager)
    }

    /// Re-applies the store's protection attributes.
    ///
    /// SQLite deletes and recreates the `-wal`/`-shm` sidecars as it checkpoints, and a
    /// recreated file is born with the container default instead of whatever was stamped on it
    /// at launch. Called on every foreground/background transition so a sidecar full of task
    /// text can't end up backup-eligible between launches.
    func reapplyDatabaseProtection() {
        LocalStoreFileProtection.apply(to: modelStoreURL)
    }
}

/// Data Protection + backup exclusion for the local SwiftData store.
///
/// Scope, stated plainly: this is NOT encryption we control. SwiftData exposes no passphrase or
/// cipher hook, so the SQLite file stays a normal SQLite file — what changes is who the OS lets
/// read it and where copies of it are allowed to travel. The task titles, notes, list names and
/// pending mutations inside it are covered by iOS Data Protection (hardware-backed, keyed to the
/// device) and are kept out of device/iCloud backups, which is the leg of the threat model an
/// unencrypted iTunes/Finder backup would otherwise walk straight through.
///
/// `.completeUntilFirstUserAuthentication` is the class the rest of the app already standardised
/// on (widget snapshot, widget session file) and is REQUIRED rather than a compromise:
/// `.complete` makes files unreadable while the device is locked, which would break the
/// home-screen widgets and every background refresh.
enum LocalStoreFileProtection {
    static let protectionClass = FileProtectionType.completeUntilFirstUserAuthentication

    /// The store plus the two SQLite sidecars. The `-wal` holds committed-but-not-checkpointed
    /// rows — i.e. the text the user typed most recently — so protecting only the `.store` file
    /// protects almost nothing.
    static func protectedURLs(for storeURL: URL) -> [URL] {
        [
            storeURL,
            URL(fileURLWithPath: storeURL.path + "-wal"),
            URL(fileURLWithPath: storeURL.path + "-shm")
        ]
    }

    /// Best-effort by design: a store that cannot be re-stamped must still open, because failing
    /// here would take the user's unsynced offline edits down with it.
    @discardableResult
    static func apply(to storeURL: URL, fileManager: FileManager = .default) -> [URL] {
        var stamped: [URL] = []
        for url in protectedURLs(for: storeURL) where fileManager.fileExists(atPath: url.path) {
            try? fileManager.setAttributes(
                [.protectionKey: protectionClass],
                ofItemAtPath: url.path
            )
            var mutableURL = url
            var resourceValues = URLResourceValues()
            resourceValues.isExcludedFromBackup = true
            try? mutableURL.setResourceValues(resourceValues)
            stamped.append(url)
        }
        return stamped
    }
}
