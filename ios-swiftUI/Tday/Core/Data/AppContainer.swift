import Foundation
import Observation
import SwiftData

@MainActor
@Observable
final class AppContainer {
    static let shared = AppContainer()

    let secureStore: SecureStore
    let themeStore: ThemeStore
    let reminderPreferenceStore: ReminderPreferenceStore
    let serverURLState: ServerURLState
    let cookieStore: CookieStore
    let networkConfiguration: NetworkConfiguration
    let apiService: TdayAPIService
    let modelContainer: ModelContainer
    let cacheManager: OfflineCacheManager
    let serverConfigRepository: ServerConfigRepository
    let authRepository: AuthRepository
    let syncManager: SyncManager
    let todoRepository: TodoRepository
    let listRepository: ListRepository
    let completedRepository: CompletedRepository
    let settingsRepository: SettingsRepository
    let realtimeClient: RealtimeClient
    let reminderScheduler: TaskReminderScheduler
    let snackbarManager: SnackbarManager
    let bootstrapSession: BootstrapSessionUseCase
    let createTodo: CreateTodoUseCase
    let completeTodo: CompleteTodoUseCase
    let syncAndRefresh: SyncAndRefreshUseCase

    private init() {
        secureStore = SecureStore()
        themeStore = ThemeStore()
        reminderPreferenceStore = ReminderPreferenceStore()
        serverURLState = ServerURLState(currentURL: secureStore.loadPersistedServerURL())
        cookieStore = CookieStore(secureStore: secureStore)
        networkConfiguration = NetworkConfiguration(
            secureStore: secureStore,
            serverURLState: serverURLState,
            cookieStore: cookieStore
        )
        apiService = TdayAPIService(configuration: networkConfiguration)
        modelContainer = try! ModelContainer(
            for: CachedTodoEntity.self,
            CachedListEntity.self,
            CachedCompletedEntity.self,
            PendingMutationEntity.self,
            SyncMetadataEntity.self
        )
        cacheManager = OfflineCacheManager(modelContainer: modelContainer)
        serverConfigRepository = ServerConfigRepository(
            secureStore: secureStore,
            serverURLState: serverURLState,
            api: apiService
        )
        authRepository = AuthRepository(
            api: apiService,
            secureStore: secureStore,
            serverConfigRepository: serverConfigRepository,
            cacheManager: cacheManager,
            cookieStore: cookieStore,
            themeStore: themeStore,
            reminderPreferenceStore: reminderPreferenceStore
        )
        syncManager = SyncManager(api: apiService, cacheManager: cacheManager)
        todoRepository = TodoRepository(api: apiService, cacheManager: cacheManager, syncManager: syncManager)
        listRepository = ListRepository(api: apiService, cacheManager: cacheManager, syncManager: syncManager)
        completedRepository = CompletedRepository(api: apiService, cacheManager: cacheManager, syncManager: syncManager)
        settingsRepository = SettingsRepository(api: apiService, cacheManager: cacheManager)
        realtimeClient = RealtimeClient(configuration: networkConfiguration)
        reminderScheduler = TaskReminderScheduler(reminderPreferenceStore: reminderPreferenceStore)
        snackbarManager = SnackbarManager()
        bootstrapSession = BootstrapSessionUseCase(authRepository: authRepository, syncManager: syncManager)
        createTodo = CreateTodoUseCase(todoRepository: todoRepository)
        completeTodo = CompleteTodoUseCase(todoRepository: todoRepository)
        syncAndRefresh = SyncAndRefreshUseCase(syncManager: syncManager)
    }
}
