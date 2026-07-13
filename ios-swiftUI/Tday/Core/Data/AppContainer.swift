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
    let realtimeClient: RealtimeClient
    let reminderScheduler: TaskReminderScheduler
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
        realtimeClient = RealtimeClient(configuration: networkConfiguration)
        reminderScheduler = TaskReminderScheduler(reminderPreferenceStore: reminderPreferenceStore)
        dayAheadStore = DayAheadStore()
        dayAheadScheduler = DayAheadScheduler(store: dayAheadStore)
        snackbarManager = SnackbarManager()
        undoableDeleteScheduler = UndoableDeleteScheduler(snackbarManager: snackbarManager)
        bootstrapSession = BootstrapSessionUseCase(authRepository: authRepository, syncManager: syncManager)
        createTodo = CreateTodoUseCase(todoRepository: todoRepository)
        completeTodo = CompleteTodoUseCase(todoRepository: todoRepository)
        syncAndRefresh = SyncAndRefreshUseCase(syncManager: syncManager)
    }
}
