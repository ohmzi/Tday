import Foundation
import Sentry

struct APIError: Error, LocalizedError, Equatable {
    let message: String
    let statusCode: Int?

    var errorDescription: String? {
        message
    }

    static func makeDecoder() -> JSONDecoder {
        .tdayDecoder
    }
}

func isLikelyConnectivityIssue(_ error: Error) -> Bool {
    if let urlError = error as? URLError {
        switch urlError.code {
        case .cannotConnectToHost, .cannotFindHost, .networkConnectionLost, .notConnectedToInternet, .timedOut:
            return true
        default:
            break
        }
    }

    if let apiError = error as? APIError {
        return apiError.statusCode == nil
    }

    return false
}

func isLikelyUnrecoverableMutationError(_ error: Error) -> Bool {
    guard let apiError = error as? APIError, let statusCode = apiError.statusCode else {
        return false
    }
    return (400 ..< 500).contains(statusCode) && statusCode != 408 && statusCode != 429
}

final class TdayAPIService {
    private let configuration: NetworkConfiguration
    private let decoder = JSONDecoder.tdayDecoder
    private let encoder = JSONEncoder.tdayEncoder

    init(configuration: NetworkConfiguration) {
        self.configuration = configuration
    }

    func probeServer(at url: URL) async throws -> MobileProbeResponse {
        try await probeServer(url: url)
    }

    func probeServer(url: URL) async throws -> MobileProbeResponse {
        try await request(
            path: url.absoluteString,
            method: "GET",
            allowRewrite: false,
            responseType: MobileProbeResponse.self
        )
    }

    func getCsrfToken() async throws -> CsrfResponse {
        try await request(path: "/api/auth/csrf", method: "GET", responseType: CsrfResponse.self)
    }

    func getCredentialKey() async throws -> CredentialKeyResponse {
        try await request(path: "/api/auth/credentials-key", method: "GET", responseType: CredentialKeyResponse.self)
    }

    func signInWithCredentials(payload: CredentialsCallbackRequest) async throws -> (response: AuthRedirectResponse, statusCode: Int) {
        let encodedBody = try encoder.encode(payload)
        let response = try await requestRawAllowingStatus(
            path: "/api/auth/callback/credentials",
            method: "POST",
            body: encodedBody,
            contentType: "application/json"
        )
        let locationHeader = response.httpResponse.value(forHTTPHeaderField: "Location")
        let trimmedBody = response.data.trimmingCharacters(in: .whitespacesAndNewlines)
        let decoded = trimmedBody.isEmpty || trimmedBody == "null"
            ? nil
            : try? decode(response.data, as: AuthRedirectResponse.self)

        return (
            response: AuthRedirectResponse(
                url: decoded?.url ?? locationHeader,
                message: decoded?.message,
                code: decoded?.code
            ),
            statusCode: response.httpResponse.statusCode
        )
    }

    func getSession() async throws -> AuthSession? {
        let response = try await requestRaw(path: "/api/auth/session", method: "GET")
        let trimmedBody = response.data.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedBody.isEmpty || trimmedBody == "null" {
            return nil
        }
        return try decode(response.data, as: AuthSession.self)
    }

    func signOut() async throws -> MessageResponse {
        let response = try await requestRaw(path: "/api/auth/logout", method: "POST")
        if response.data.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return MessageResponse(message: "Logged out")
        }
        return try decode(response.data, as: MessageResponse.self)
    }

    func register(payload: RegisterRequest) async throws -> RegisterResponse {
        try await request(path: "/api/auth/register", method: "POST", body: payload, responseType: RegisterResponse.self)
    }

    func getTodos(start: Int64? = nil, end: Int64? = nil, timeline: Bool? = nil, recurringFutureDays: Int? = nil) async throws -> TodosResponse {
        var queryItems: [URLQueryItem] = []
        if let start {
            queryItems.append(URLQueryItem(name: "start", value: String(start)))
        }
        if let end {
            queryItems.append(URLQueryItem(name: "end", value: String(end)))
        }
        if let timeline {
            queryItems.append(URLQueryItem(name: "timeline", value: timeline ? "true" : "false"))
        }
        if let recurringFutureDays {
            queryItems.append(URLQueryItem(name: "recurringFutureDays", value: String(recurringFutureDays)))
        }
        return try await request(path: "/api/todo", method: "GET", queryItems: queryItems, responseType: TodosResponse.self)
    }

    func getAppSettings() async throws -> AppSettingsResponse {
        try await request(path: "/api/app-settings", method: "GET", responseType: AppSettingsResponse.self)
    }

    func getAdminSettings() async throws -> AdminSettingsResponse {
        try await request(path: "/api/admin/settings", method: "GET", responseType: AdminSettingsResponse.self)
    }

    func patchAdminSettings(payload: UpdateAdminSettingsRequest) async throws -> AdminSettingsResponse {
        try await request(path: "/api/admin/settings", method: "PATCH", body: payload, responseType: AdminSettingsResponse.self)
    }

    func summarizeTodos(payload: TodoSummaryRequest) async throws -> TodoSummaryResponse {
        try await request(path: "/api/todo/summary", method: "POST", body: payload, responseType: TodoSummaryResponse.self)
    }

    func parseTodoTitleNlp(payload: TodoTitleNlpRequest) async throws -> TodoTitleNlpResponse {
        try await request(path: "/api/todo/nlp", method: "POST", body: payload, responseType: TodoTitleNlpResponse.self)
    }

    func createTodo(payload: CreateTodoRequest) async throws -> CreateTodoResponse {
        try await request(path: "/api/todo", method: "POST", body: payload, responseType: CreateTodoResponse.self)
    }

    func patchTodo(payload: UpdateTodoRequest) async throws -> MessageResponse {
        try await patchTodoByBody(payload: payload)
    }

    func deleteTodo(payload: DeleteTodoRequest) async throws -> MessageResponse {
        try await deleteTodoByBody(payload: payload)
    }

    func completeTodo(payload: TodoCompleteRequest) async throws -> MessageResponse {
        try await completeTodoByBody(payload: payload)
    }

    func uncompleteTodo(payload: TodoUncompleteRequest) async throws -> MessageResponse {
        try await uncompleteTodoByBody(payload: payload)
    }

    func prioritizeTodo(payload: TodoPrioritizeRequest) async throws -> MessageResponse {
        try await prioritizeTodoByBody(payload: payload)
    }

    func deleteTodoInstance(payload: TodoInstanceDeleteRequest) async throws -> MessageResponse {
        try await deleteTodoInstanceByBody(payload: payload)
    }

    func patchTodoByBody(payload: UpdateTodoRequest) async throws -> MessageResponse {
        try await request(path: "/api/todo", method: "PATCH", body: payload, responseType: MessageResponse.self)
    }

    func deleteTodoByBody(payload: DeleteTodoRequest) async throws -> MessageResponse {
        try await request(path: "/api/todo", method: "DELETE", body: payload, responseType: MessageResponse.self)
    }

    func completeTodoByBody(payload: TodoCompleteRequest) async throws -> MessageResponse {
        try await request(path: "/api/todo/complete", method: "PATCH", body: payload, responseType: MessageResponse.self)
    }

    func uncompleteTodoByBody(payload: TodoUncompleteRequest) async throws -> MessageResponse {
        try await request(path: "/api/todo/uncomplete", method: "PATCH", body: payload, responseType: MessageResponse.self)
    }

    func prioritizeTodoByBody(payload: TodoPrioritizeRequest) async throws -> MessageResponse {
        try await request(path: "/api/todo/prioritize", method: "PATCH", body: payload, responseType: MessageResponse.self)
    }

    func reorderTodos(payload: [ReorderItemRequest]) async throws -> MessageResponse {
        try await request(path: "/api/todo/reorder", method: "PATCH", body: payload, responseType: MessageResponse.self)
    }

    func getOverdueTodos(start: Int64, end: Int64) async throws -> TodosResponse {
        try await request(
            path: "/api/todo/overdue",
            method: "GET",
            queryItems: [
                URLQueryItem(name: "start", value: String(start)),
                URLQueryItem(name: "end", value: String(end)),
            ],
            responseType: TodosResponse.self
        )
    }

    func patchTodoInstanceByBody(payload: TodoInstancePatchRequest) async throws -> MessageResponse {
        try await request(path: "/api/todo/instance", method: "PATCH", body: payload, responseType: MessageResponse.self)
    }

    func deleteTodoInstanceByBody(payload: TodoInstanceDeleteRequest) async throws -> MessageResponse {
        try await request(path: "/api/todo/instance", method: "DELETE", body: payload, responseType: MessageResponse.self)
    }

    func getCompletedTodos() async throws -> CompletedTodosResponse {
        try await request(path: "/api/completedTodo", method: "GET", responseType: CompletedTodosResponse.self)
    }

    func patchCompletedTodoByBody(payload: UpdateCompletedTodoRequest) async throws -> MessageResponse {
        try await request(path: "/api/completedTodo", method: "PATCH", body: payload, responseType: MessageResponse.self)
    }

    func patchCompletedTodo(payload: UpdateCompletedTodoRequest) async throws -> MessageResponse {
        try await patchCompletedTodoByBody(payload: payload)
    }

    func deleteCompletedTodoByBody(payload: DeleteCompletedTodoRequest) async throws -> MessageResponse {
        try await request(path: "/api/completedTodo", method: "DELETE", body: payload, responseType: MessageResponse.self)
    }

    func deleteCompletedTodo(payload: DeleteCompletedTodoRequest) async throws -> MessageResponse {
        try await deleteCompletedTodoByBody(payload: payload)
    }

    func getLists() async throws -> ListsResponse {
        try await request(path: "/api/list", method: "GET", responseType: ListsResponse.self)
    }

    func getListTodos(listID: String, start: Int64, end: Int64) async throws -> TodosResponse {
        try await request(
            path: "/api/list/\(listID)",
            method: "GET",
            queryItems: [
                URLQueryItem(name: "start", value: String(start)),
                URLQueryItem(name: "end", value: String(end)),
            ],
            responseType: TodosResponse.self
        )
    }

    func createList(payload: CreateListRequest) async throws -> CreateListResponse {
        try await request(path: "/api/list", method: "POST", body: payload, responseType: CreateListResponse.self)
    }

    func patchListByBody(payload: UpdateListRequest) async throws -> MessageResponse {
        try await request(path: "/api/list", method: "PATCH", body: payload, responseType: MessageResponse.self)
    }

    func patchList(payload: UpdateListRequest) async throws -> MessageResponse {
        try await patchListByBody(payload: payload)
    }

    func deleteListByBody(payload: DeleteListRequest) async throws -> MessageResponse {
        try await request(path: "/api/list", method: "DELETE", body: payload, responseType: MessageResponse.self)
    }

    func deleteList(payload: DeleteListRequest) async throws -> MessageResponse {
        try await deleteListByBody(payload: payload)
    }

    func getPreferences() async throws -> PreferencesResponse {
        try await request(path: "/api/preferences", method: "GET", responseType: PreferencesResponse.self)
    }

    func patchPreferences(payload: PreferencesDTO) async throws -> PreferencesResponse {
        try await request(path: "/api/preferences", method: "PATCH", body: payload, responseType: PreferencesResponse.self)
    }

    func getUserDetails() async throws -> UserResponse {
        try await request(path: "/api/user", method: "GET", responseType: UserResponse.self)
    }

    func patchUserProfile(payload: UpdateProfileRequest) async throws -> [String: String] {
        try await request(path: "/api/user/profile", method: "PATCH", body: payload, responseType: [String: String].self)
    }

    func changePassword(payload: ChangePasswordRequest) async throws -> MessageResponse {
        try await request(path: "/api/user/change-password", method: "POST", body: payload, responseType: MessageResponse.self)
    }

    func syncTimezone(_ timezone: String) async throws -> [String: String] {
        try await request(
            path: "/api/timezone",
            method: "GET",
            extraHeaders: ["X-User-Timezone": timezone],
            responseType: [String: String].self
        )
    }

    private func request<Body: Encodable, Response: Decodable>(
        path: String,
        method: String,
        queryItems: [URLQueryItem] = [],
        body: Body,
        extraHeaders: [String: String] = [:],
        allowRewrite: Bool = true,
        responseType: Response.Type
    ) async throws -> Response {
        let encodedBody = try encoder.encode(body)
        let response = try await requestRaw(
            path: path,
            method: method,
            queryItems: queryItems,
            body: encodedBody,
            contentType: "application/json",
            extraHeaders: extraHeaders,
            allowRewrite: allowRewrite
        )
        return try decode(response.data, as: responseType)
    }

    private func request<Response: Decodable>(
        path: String,
        method: String,
        queryItems: [URLQueryItem] = [],
        extraHeaders: [String: String] = [:],
        allowRewrite: Bool = true,
        responseType: Response.Type
    ) async throws -> Response {
        let response = try await requestRaw(
            path: path,
            method: method,
            queryItems: queryItems,
            extraHeaders: extraHeaders,
            allowRewrite: allowRewrite
        )
        return try decode(response.data, as: responseType)
    }

    private func requestRaw(
        path: String,
        method: String,
        queryItems: [URLQueryItem] = [],
        body: Data? = nil,
        contentType: String? = nil,
        extraHeaders: [String: String] = [:],
        allowRewrite: Bool = true
    ) async throws -> (data: String, httpResponse: HTTPURLResponse) {
        try await performRequestRaw(
            path: path,
            method: method,
            queryItems: queryItems,
            body: body,
            contentType: contentType,
            extraHeaders: extraHeaders,
            allowRewrite: allowRewrite,
            validateStatus: true
        )
    }

    private func requestRawAllowingStatus(
        path: String,
        method: String,
        queryItems: [URLQueryItem] = [],
        body: Data? = nil,
        contentType: String? = nil,
        extraHeaders: [String: String] = [:],
        allowRewrite: Bool = true
    ) async throws -> (data: String, httpResponse: HTTPURLResponse) {
        try await performRequestRaw(
            path: path,
            method: method,
            queryItems: queryItems,
            body: body,
            contentType: contentType,
            extraHeaders: extraHeaders,
            allowRewrite: allowRewrite,
            validateStatus: false
        )
    }

    private func performRequestRaw(
        path: String,
        method: String,
        queryItems: [URLQueryItem] = [],
        body: Data? = nil,
        contentType: String? = nil,
        extraHeaders: [String: String] = [:],
        allowRewrite: Bool = true,
        validateStatus: Bool
    ) async throws -> (data: String, httpResponse: HTTPURLResponse) {
        var url = try configuration.makeURL(path: path, allowRewrite: allowRewrite)
        if !queryItems.isEmpty {
            var components = URLComponents(url: url, resolvingAgainstBaseURL: false)
            components?.queryItems = queryItems
            if let resolved = components?.url {
                url = resolved
            }
        }

        if configuration.isSecureTransportRequired(for: url), url.scheme?.lowercased() != "https" {
            throw APIError(message: "HTTPS is required for remote servers", statusCode: nil)
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.httpBody = body
        for (key, value) in configuration.defaultHeaders(extraHeaders: extraHeaders, allowRewrite: allowRewrite) {
            request.setValue(value, forHTTPHeaderField: key)
        }
        if let contentType {
            request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        }

        do {
            let (data, response) = try await configuration.session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                throw APIError(message: "Unexpected server response", statusCode: nil)
            }
            let bodyString = String(data: data, encoding: .utf8) ?? ""
            guard !validateStatus || (200 ..< 300).contains(httpResponse.statusCode) else {
                let serverMessage = decodeServerErrorMessage(from: bodyString) ?? HTTPURLResponse.localizedString(forStatusCode: httpResponse.statusCode)
                let breadcrumb = Breadcrumb(level: .error, category: "api")
                breadcrumb.message = "\(method) \(url.path) — \(httpResponse.statusCode)"
                breadcrumb.data = ["status": httpResponse.statusCode, "url": url.absoluteString]
                SentrySDK.addBreadcrumb(breadcrumb)
                throw APIError(message: serverMessage, statusCode: httpResponse.statusCode)
            }
            return (bodyString, httpResponse)
        } catch let error as APIError {
            throw error
        } catch {
            SentrySDK.capture(error: error)
            throw APIError(message: error.localizedDescription, statusCode: nil)
        }
    }

    private func decode<Response: Decodable>(_ data: String, as type: Response.Type) throws -> Response {
        guard let encoded = data.data(using: .utf8) else {
            throw APIError(message: "Unable to decode server response", statusCode: nil)
        }
        return try decoder.decode(type, from: encoded)
    }

    private func decodeServerErrorMessage(from body: String) -> String? {
        guard let data = body.data(using: .utf8) else {
            return nil
        }
        return (try? decoder.decode(MessageResponse.self, from: data))?.message
    }
}

private extension JSONDecoder {
    static let tdayDecoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }()
}

private extension JSONEncoder {
    static let tdayEncoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }()
}
