import Foundation

struct RealtimeEvent: Equatable {
    let name: String
    let rawPayload: String

    var requiresRefresh: Bool {
        let normalizedName = name.lowercased()
        return normalizedName.hasPrefix("todo.") ||
            normalizedName.contains("todocreated") ||
            normalizedName.contains("todoupdated") ||
            normalizedName.contains("tododeleted") ||
            // "list.members" (membership changes) intentionally matches the
            // "list." prefix; floater events fan in here too.
            normalizedName.hasPrefix("list.") ||
            normalizedName.hasPrefix("floater.") ||
            normalizedName.hasPrefix("floaterlist.") ||
            normalizedName.contains("listchanged") ||
            normalizedName.hasPrefix("completed.") ||
            normalizedName.hasPrefix("completedtodo.") ||
            normalizedName.contains("completedchanged") ||
            normalizedName.contains("completedtodo")
    }
}

actor RealtimeClient {
    private let configuration: NetworkConfiguration
    private var task: URLSessionWebSocketTask?
    private var reconnectTask: Task<Void, Never>?
    private var isConnecting = false
    private var eventHandler: (@Sendable (RealtimeEvent) async -> Void)?

    init(configuration: NetworkConfiguration) {
        self.configuration = configuration
    }

    func start(handler: @escaping @Sendable (RealtimeEvent) async -> Void) {
        eventHandler = handler
        reconnectTask?.cancel()
        reconnectTask = Task {
            await connectIfNeeded()
        }
    }

    func stop() {
        reconnectTask?.cancel()
        task?.cancel(with: .goingAway, reason: nil)
        task = nil
        eventHandler = nil
        isConnecting = false
    }

    private func connectIfNeeded() async {
        guard task == nil, !isConnecting else {
            return
        }
        isConnecting = true
        defer { isConnecting = false }

        guard let baseURL = try? configuration.currentBaseURL(), var components = URLComponents(url: baseURL, resolvingAgainstBaseURL: false) else {
            return
        }

        components.scheme = components.scheme == "http" ? "ws" : "wss"
        components.path = "/ws"
        components.query = nil
        guard let url = components.url else {
            return
        }

        let request = URLRequest(url: url)
        let webSocketTask = configuration.session.webSocketTask(with: request)
        task = webSocketTask
        TdayTelemetry.addBreadcrumb("realtime.connect", data: ["phase": "start"])
        webSocketTask.resume()
        await listen(on: webSocketTask)
    }

    private func listen(on webSocketTask: URLSessionWebSocketTask) async {
        do {
            while !Task.isCancelled {
                let message = try await webSocketTask.receive()
                switch message {
                case let .string(text):
                    if let event = parse(text: text) {
                        await eventHandler?(event)
                    }
                case let .data(data):
                    if let text = String(data: data, encoding: .utf8), let event = parse(text: text) {
                        await eventHandler?(event)
                    }
                @unknown default:
                    break
                }
            }
        } catch {
            let nsError = error as NSError
            TdayTelemetry.addBreadcrumb(
                "realtime.connect",
                level: .warning,
                data: [
                    "phase": "failure",
                    "domain": TdayTelemetry.safeLabel(nsError.domain),
                    "code": nsError.code
                ]
            )
            task = nil
            scheduleReconnect()
        }
    }

    private func parse(text: String) -> RealtimeEvent? {
        let eventName = Self.eventName(from: text)
        guard !eventName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return nil
        }
        return RealtimeEvent(name: eventName, rawPayload: text)
    }

    nonisolated static func eventName(from text: String) -> String {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let data = trimmed.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else {
            return trimmed
        }

        if let eventName = json["event"] as? String {
            return eventName
        }
        if let typeName = json["type"] as? String {
            return typeName
        }
        return trimmed
    }

    private func scheduleReconnect() {
        TdayTelemetry.addBreadcrumb("realtime.disconnect")
        reconnectTask?.cancel()
        reconnectTask = Task {
            try? await Task.sleep(for: .seconds(3))
            await connectIfNeeded()
        }
    }
}
