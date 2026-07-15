import Foundation

struct APIError: Error, LocalizedError, Equatable {
    let message: String
    let statusCode: Int?
    let reason: String?
    let field: String?
    let retryAfterSeconds: Int?

    init(
        message: String,
        statusCode: Int?,
        reason: String? = nil,
        field: String? = nil,
        retryAfterSeconds: Int? = nil
    ) {
        self.message = message
        self.statusCode = statusCode
        self.reason = reason
        self.field = field
        self.retryAfterSeconds = retryAfterSeconds
    }

    var errorDescription: String? {
        message
    }

    static func makeDecoder() -> JSONDecoder {
        .tdayDecoder
    }
}

private struct ServerErrorResponse: Decodable {
    let message: String?
    let code: String?
    let reason: String?
    let field: String?
    let retryAfterSeconds: Int?

    private enum CodingKeys: String, CodingKey {
        case message
        case code
        case reason
        case field
        case retryAfterSeconds
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        message = try container.decodeIfPresent(String.self, forKey: .message)
        if let stringCode = try? container.decodeIfPresent(String.self, forKey: .code) {
            code = stringCode
        } else if let intCode = try? container.decodeIfPresent(Int.self, forKey: .code) {
            code = String(intCode)
        } else {
            code = nil
        }
        reason = try container.decodeIfPresent(String.self, forKey: .reason)
        field = try container.decodeIfPresent(String.self, forKey: .field)
        retryAfterSeconds = try container.decodeIfPresent(Int.self, forKey: .retryAfterSeconds)
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
        guard let statusCode = apiError.statusCode else {
            return true
        }
        return isLikelyServerUnavailableStatusCode(statusCode)
    }

    return false
}

func isSessionAuthenticationIssue(_ error: Error) -> Bool {
    guard let apiError = error as? APIError else {
        return false
    }
    return apiError.statusCode == 401
}

func isLikelyServerUnavailableStatusCode(_ statusCode: Int) -> Bool {
    statusCode == 408 ||
        statusCode == 502 ||
        statusCode == 503 ||
        statusCode == 504 ||
        (520 ... 524).contains(statusCode)
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

    /// True (and clears the record) if the last connection to `host` was cancelled
    /// by the TLS pinning check because its certificate fingerprint changed.
    func consumeTrustFailure(forHost host: String) -> Bool {
        configuration.consumeTrustFailure(host: host)
    }

    func probeServer(at url: URL) async throws -> MobileProbeResponse {
        try await probeServer(url: url)
    }

    func probeServer(url: URL) async throws -> MobileProbeResponse {
        try await request(
            path: url.absoluteString,
            method: "GET",
            allowRewrite: false,
            session: configuration.probeSession,
            responseType: MobileProbeResponse.self
        )
    }

    func probeConfiguredServer(timeoutInterval: TimeInterval? = nil) async throws -> MobileProbeResponse {
        try await request(
            path: "/api/mobile/probe",
            method: "GET",
            timeoutInterval: timeoutInterval,
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

    func getAllSecurityQuestions() async throws -> SecurityQuestionsResponse {
        try await request(path: "/api/auth/security-questions/all", method: "GET", responseType: SecurityQuestionsResponse.self)
    }

    func getSecurityQuestions(username: String) async throws -> SecurityQuestionsResponse {
        try await request(
            path: "/api/auth/security-questions",
            method: "GET",
            queryItems: [URLQueryItem(name: "username", value: username)],
            responseType: SecurityQuestionsResponse.self
        )
    }

    // The error body (HTTP status + `reason`, e.g. "reset_failed" / "reset_locked")
    // is surfaced through the thrown APIError so the caller can branch on the outcome.
    func resetPassword(payload: SelfServiceResetRequest) async throws -> MessageResponse {
        try await request(path: "/api/auth/reset-password", method: "POST", body: payload, responseType: MessageResponse.self)
    }

    func verifySecurityAnswers(payload: VerifySecurityAnswersRequest) async throws -> VerifySecurityAnswersResponse {
        try await request(path: "/api/auth/verify-security-answers", method: "POST", body: payload, responseType: VerifySecurityAnswersResponse.self)
    }

    func requestAdminReset(payload: RequestAdminResetRequest) async throws -> MessageResponse {
        try await request(path: "/api/auth/request-admin-reset", method: "POST", body: payload, responseType: MessageResponse.self)
    }

    func getUserSecurityQuestions() async throws -> SecurityQuestionStatusResponse {
        try await request(path: "/api/user/security-questions", method: "GET", responseType: SecurityQuestionStatusResponse.self)
    }

    func setUserSecurityQuestions(payload: SetSecurityQuestionsRequest) async throws -> MessageResponse {
        try await request(path: "/api/user/security-questions", method: "POST", body: payload, responseType: MessageResponse.self)
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

    func getFloaters() async throws -> FloatersResponse {
        try await request(path: "/api/floater", method: "GET", responseType: FloatersResponse.self)
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

    func createFloater(payload: CreateFloaterRequest) async throws -> CreateFloaterResponse {
        try await request(path: "/api/floater", method: "POST", body: payload, responseType: CreateFloaterResponse.self)
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

    func patchFloaterByBody(payload: UpdateFloaterRequest) async throws -> MessageResponse {
        try await request(path: "/api/floater", method: "PATCH", body: payload, responseType: MessageResponse.self)
    }

    func deleteFloaterByBody(payload: DeleteFloaterRequest) async throws -> MessageResponse {
        try await request(path: "/api/floater", method: "DELETE", body: payload, responseType: MessageResponse.self)
    }

    func completeFloaterByBody(payload: FloaterCompleteRequest) async throws -> MessageResponse {
        try await request(path: "/api/floater/complete", method: "PATCH", body: payload, responseType: MessageResponse.self)
    }

    /// Schedules a floater into a real Todo (the floater row is consumed).
    func promoteFloater(id: String, payload: PromoteFloaterRequest) async throws -> PromoteFloaterResponse {
        try await request(path: "/api/floater/\(id)/promote", method: "POST", body: payload, responseType: PromoteFloaterResponse.self)
    }

    /// Lets a stale todo float: consumes the todo, creating an Anytime floater.
    func demoteTodo(id: String) async throws -> DemoteTodoResponse {
        try await request(path: "/api/todo/\(id)/demote", method: "POST", responseType: DemoteTodoResponse.self)
    }

    func uncompleteFloaterByBody(payload: FloaterUncompleteRequest) async throws -> MessageResponse {
        try await request(path: "/api/floater/uncomplete", method: "PATCH", body: payload, responseType: MessageResponse.self)
    }

    func prioritizeFloaterByBody(payload: FloaterPrioritizeRequest) async throws -> MessageResponse {
        try await request(path: "/api/floater/prioritize", method: "PATCH", body: payload, responseType: MessageResponse.self)
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

    func getCompletedFloaters() async throws -> CompletedFloatersResponse {
        try await request(path: "/api/completedFloater", method: "GET", responseType: CompletedFloatersResponse.self)
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

    func patchCompletedFloaterByBody(payload: UpdateCompletedFloaterRequest) async throws -> MessageResponse {
        try await request(path: "/api/completedFloater", method: "PATCH", body: payload, responseType: MessageResponse.self)
    }

    func deleteCompletedFloaterByBody(payload: DeleteCompletedFloaterRequest) async throws -> MessageResponse {
        try await request(path: "/api/completedFloater", method: "DELETE", body: payload, responseType: MessageResponse.self)
    }

    func deleteCompletedTodo(payload: DeleteCompletedTodoRequest) async throws -> MessageResponse {
        try await deleteCompletedTodoByBody(payload: payload)
    }

    func getLists() async throws -> ListsResponse {
        try await request(path: "/api/list", method: "GET", responseType: ListsResponse.self)
    }

    func getFloaterLists() async throws -> FloaterListsResponse {
        try await request(path: "/api/floaterList", method: "GET", responseType: FloaterListsResponse.self)
    }

    func getListTodos(listID: String, start: Int64, end: Int64) async throws -> ListDetailResponse {
        try await request(
            path: "/api/list/\(listID)",
            method: "GET",
            queryItems: [
                URLQueryItem(name: "start", value: String(start)),
                URLQueryItem(name: "end", value: String(end)),
            ],
            responseType: ListDetailResponse.self
        )
    }

    func getFloaterListTodos(listID: String) async throws -> FloaterListDetailResponse {
        try await request(
            path: "/api/floaterList/\(listID)",
            method: "GET",
            responseType: FloaterListDetailResponse.self
        )
    }

    func createList(payload: CreateListRequest) async throws -> CreateListResponse {
        try await request(path: "/api/list", method: "POST", body: payload, responseType: CreateListResponse.self)
    }

    func createFloaterList(payload: CreateFloaterListRequest) async throws -> CreateFloaterListResponse {
        try await request(path: "/api/floaterList", method: "POST", body: payload, responseType: CreateFloaterListResponse.self)
    }

    func patchListByBody(payload: UpdateListRequest) async throws -> MessageResponse {
        try await request(path: "/api/list", method: "PATCH", body: payload, responseType: MessageResponse.self)
    }

    func patchFloaterListByBody(payload: UpdateFloaterListRequest) async throws -> MessageResponse {
        try await request(path: "/api/floaterList", method: "PATCH", body: payload, responseType: MessageResponse.self)
    }

    func patchList(payload: UpdateListRequest) async throws -> MessageResponse {
        try await patchListByBody(payload: payload)
    }

    func deleteListByBody(payload: DeleteListRequest) async throws -> DeleteListResponse {
        try await request(path: "/api/list", method: "DELETE", body: payload, responseType: DeleteListResponse.self)
    }

    func deleteFloaterListByBody(payload: DeleteFloaterListRequest) async throws -> DeleteFloaterListResponse {
        try await request(path: "/api/floaterList", method: "DELETE", body: payload, responseType: DeleteFloaterListResponse.self)
    }

    /// Reset a reusable floater list — un-completes all its floaters.
    func resetFloaterList(id: String) async throws -> MessageResponse {
        try await request(path: "/api/floaterList/\(id)/reset", method: "POST", responseType: MessageResponse.self)
    }

    func deleteList(payload: DeleteListRequest) async throws -> DeleteListResponse {
        try await deleteListByBody(payload: payload)
    }

    // MARK: List sharing (membersBase = "list" or "floaterList")

    func getListMembers(base: String, listID: String) async throws -> ListMembersResponse {
        try await request(path: "/api/\(base)/\(listID)/members", method: "GET", responseType: ListMembersResponse.self)
    }

    func addListMember(base: String, listID: String, payload: AddMemberRequest) async throws -> AddMemberResponse {
        try await request(path: "/api/\(base)/\(listID)/members", method: "POST", body: payload, responseType: AddMemberResponse.self)
    }

    func updateListMemberRole(base: String, listID: String, payload: UpdateMemberRoleRequest) async throws -> MessageResponse {
        try await request(path: "/api/\(base)/\(listID)/members", method: "PATCH", body: payload, responseType: MessageResponse.self)
    }

    func removeListMember(base: String, listID: String, payload: RemoveMemberRequest) async throws -> MessageResponse {
        try await request(path: "/api/\(base)/\(listID)/members", method: "DELETE", body: payload, responseType: MessageResponse.self)
    }

    func leaveList(base: String, listID: String) async throws -> MessageResponse {
        try await request(path: "/api/\(base)/\(listID)/leave", method: "POST", responseType: MessageResponse.self)
    }

    func searchUsers(query: String) async throws -> UserSearchResponse {
        try await request(
            path: "/api/user/search",
            method: "GET",
            queryItems: [URLQueryItem(name: "q", value: query)],
            responseType: UserSearchResponse.self
        )
    }

    func getPreferences() async throws -> PreferencesResponse {
        try await request(path: "/api/preferences", method: "GET", responseType: PreferencesResponse.self)
    }

    func patchPreferences(payload: PreferencesDTO) async throws -> PreferencesResponse {
        try await request(path: "/api/preferences", method: "PATCH", body: payload, responseType: PreferencesResponse.self)
    }

    func getExport() async throws -> TdayExport {
        try await request(path: "/api/export", method: "GET", responseType: TdayExport.self)
    }

    func postImport(payload: ImportRequest) async throws -> ImportResponse {
        try await request(path: "/api/import", method: "POST", body: payload, responseType: ImportResponse.self)
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
        session: URLSession? = nil,
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
            allowRewrite: allowRewrite,
            session: session
        )
        return try decode(response.data, as: responseType)
    }

    private func request<Response: Decodable>(
        path: String,
        method: String,
        queryItems: [URLQueryItem] = [],
        extraHeaders: [String: String] = [:],
        allowRewrite: Bool = true,
        session: URLSession? = nil,
        timeoutInterval: TimeInterval? = nil,
        responseType: Response.Type
    ) async throws -> Response {
        let response = try await requestRaw(
            path: path,
            method: method,
            queryItems: queryItems,
            extraHeaders: extraHeaders,
            allowRewrite: allowRewrite,
            session: session,
            timeoutInterval: timeoutInterval
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
        allowRewrite: Bool = true,
        session: URLSession? = nil,
        timeoutInterval: TimeInterval? = nil
    ) async throws -> (data: String, httpResponse: HTTPURLResponse) {
        try await performRequestRaw(
            path: path,
            method: method,
            queryItems: queryItems,
            body: body,
            contentType: contentType,
            extraHeaders: extraHeaders,
            allowRewrite: allowRewrite,
            session: session,
            timeoutInterval: timeoutInterval,
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
        allowRewrite: Bool = true,
        session: URLSession? = nil,
        timeoutInterval: TimeInterval? = nil
    ) async throws -> (data: String, httpResponse: HTTPURLResponse) {
        try await performRequestRaw(
            path: path,
            method: method,
            queryItems: queryItems,
            body: body,
            contentType: contentType,
            extraHeaders: extraHeaders,
            allowRewrite: allowRewrite,
            session: session,
            timeoutInterval: timeoutInterval,
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
        session: URLSession? = nil,
        timeoutInterval: TimeInterval? = nil,
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
        request.cachePolicy = .reloadIgnoringLocalAndRemoteCacheData
        if let timeoutInterval {
            request.timeoutInterval = timeoutInterval
        }
        request.httpMethod = method
        request.httpBody = body
        for (key, value) in configuration.defaultHeaders(extraHeaders: extraHeaders, allowRewrite: allowRewrite) {
            request.setValue(value, forHTTPHeaderField: key)
        }
        if let contentType {
            request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        }

        let urlSession = session ?? configuration.session
        // iOS routinely hands back a dead pooled keep-alive connection as
        // .networkConnectionLost (and occasionally a one-off .timedOut / DNS blip on a
        // network switch). A single immediate retry on a fresh connection almost always
        // succeeds, instead of falsely flipping the whole app to "offline". Only
        // idempotent (GET/HEAD) requests are retried so mutations are never double-applied.
        let upperMethod = method.uppercased()
        let isIdempotent = upperMethod == "GET" || upperMethod == "HEAD"
        let maxTransportAttempts = isIdempotent ? 2 : 1
        var transportAttempt = 0

        while true {
            transportAttempt += 1
            do {
                let (data, response) = try await urlSession.data(for: request)
                guard let httpResponse = response as? HTTPURLResponse else {
                    throw APIError(message: "Unexpected server response", statusCode: nil)
                }
                configuration.syncPersistedAuthCookie()
                let bodyString = String(data: data, encoding: .utf8) ?? ""
                guard !validateStatus || (200 ..< 300).contains(httpResponse.statusCode) else {
                    let serverError = decodeServerError(from: bodyString)
                    let serverMessage = serverError?.message ?? HTTPURLResponse.localizedString(forStatusCode: httpResponse.statusCode)
                    TdayTelemetry.addBreadcrumb(
                        "api.request",
                        category: "api",
                        level: .error,
                        data: [
                            "method": method,
                            "route": TdayTelemetry.sanitizePath(url.path),
                            "status": httpResponse.statusCode
                        ]
                    )
                    throw APIError(
                        message: serverMessage,
                        statusCode: httpResponse.statusCode,
                        reason: serverError?.reason ?? serverError?.code,
                        field: serverError?.field,
                        retryAfterSeconds: serverError?.retryAfterSeconds
                    )
                }
                return (bodyString, httpResponse)
            } catch let error as APIError {
                throw error
            } catch let urlError as URLError where isIdempotent
                && transportAttempt < maxTransportAttempts
                && Self.isRetriableTransportError(urlError) {
                TdayTelemetry.addBreadcrumb(
                    "api.transport.retry",
                    category: "api",
                    data: [
                        "method": method,
                        "route": TdayTelemetry.sanitizePath(url.path),
                        "code": urlError.code.rawValue
                    ]
                )
                try? await Task.sleep(nanoseconds: 250_000_000)
                continue
            } catch {
                TdayTelemetry.capture(
                    error,
                    operation: "api.transport",
                    data: [
                        "method": method,
                        "route": TdayTelemetry.sanitizePath(url.path)
                    ]
                )
                throw APIError(message: error.localizedDescription, statusCode: nil)
            }
        }
    }

    /// Transport-level errors that are usually transient (stale pooled connection,
    /// brief timeout, DNS blip on a network switch) and worth one retry on a fresh
    /// connection. `.notConnectedToInternet` is intentionally excluded — there is no
    /// route, so retrying only delays the legitimate offline result.
    private static func isRetriableTransportError(_ error: URLError) -> Bool {
        switch error.code {
        case .networkConnectionLost, .timedOut, .cannotConnectToHost, .cannotFindHost, .dnsLookupFailed:
            return true
        default:
            return false
        }
    }

    private func decode<Response: Decodable>(_ data: String, as type: Response.Type) throws -> Response {
        guard let encoded = data.data(using: .utf8) else {
            throw APIError(message: "Unable to decode server response", statusCode: nil)
        }
        return try decoder.decode(type, from: encoded)
    }

    private func decodeServerError(from body: String) -> ServerErrorResponse? {
        guard let data = body.data(using: .utf8) else {
            return nil
        }
        return try? decoder.decode(ServerErrorResponse.self, from: data)
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
