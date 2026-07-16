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
    // A SINGLE task owns the whole lifecycle: connect → listen → (on drop) back off → reconnect,
    // looping until stop() cancels it. This replaces the old task/isConnecting/reconnectTask
    // machine, whose receive loop could exit on clean cancellation without clearing state or
    // scheduling a reconnect — after which connectIfNeeded() no-op'd forever and iOS silently
    // stopped receiving live events. Now there is no "connected but not listening" state to wedge.
    private var connectionTask: Task<Void, Never>?
    private var currentSocket: URLSessionWebSocketTask?
    private var eventHandler: (@Sendable (RealtimeEvent) async -> Void)?

    init(configuration: NetworkConfiguration) {
        self.configuration = configuration
    }

    func start(handler: @escaping @Sendable (RealtimeEvent) async -> Void) {
        // Always refresh the handler (the running loop reads it fresh per event).
        eventHandler = handler
        if connectionTask == nil {
            connectionTask = Task { [weak self] in
                await self?.runConnectionLoop()
            }
        } else {
            // Loop already running (re-invoked on foreground / network-restore): nudge a fresh
            // connection by dropping the current socket. The loop catches the resulting error
            // and reconnects — safe now, because it can no longer wedge.
            currentSocket?.cancel(with: .goingAway, reason: nil)
        }
    }

    func stop() {
        connectionTask?.cancel()
        connectionTask = nil
        currentSocket?.cancel(with: .goingAway, reason: nil)
        currentSocket = nil
        eventHandler = nil
    }

    private func runConnectionLoop() async {
        var failures = 0
        while !Task.isCancelled {
            let wasEstablished = await connectAndListen()
            if Task.isCancelled { break }
            failures = wasEstablished ? 0 : min(failures + 1, 5)
            // Quick reconnect after a real connection dropped; escalating backoff (2,4,8,16,30s)
            // when we can't even establish one (bad URL, auth, server down).
            let delaySeconds = wasEstablished ? 2 : min(30, 1 << min(failures, 5))
            try? await Task.sleep(for: .seconds(delaySeconds))
        }
        currentSocket = nil
    }

    /// Opens a socket and pumps events until it errors/closes or the loop is cancelled.
    /// Returns true if the connection stayed up long enough to count as "established".
    private func connectAndListen() async -> Bool {
        guard let url = websocketURL() else {
            return false
        }
        let webSocketTask = configuration.session.webSocketTask(with: URLRequest(url: url))
        currentSocket = webSocketTask
        TdayTelemetry.addBreadcrumb("realtime.connect", data: ["phase": "start"])
        webSocketTask.resume()

        // Keepalive: a periodic ping tears down a silently-dead socket (NAT/idle timeout that
        // never surfaces as a receive error) so the loop below throws and reconnects.
        let keepAlive = Self.startKeepAlive(for: webSocketTask)
        defer { keepAlive.cancel() }

        let openedAt = Date()
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
        }
        webSocketTask.cancel(with: .goingAway, reason: nil)
        if currentSocket === webSocketTask {
            currentSocket = nil
        }
        return Date().timeIntervalSince(openedAt) >= 5
    }

    private nonisolated static func startKeepAlive(for socket: URLSessionWebSocketTask) -> Task<Void, Never> {
        Task {
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(25))
                if Task.isCancelled {
                    return
                }
                socket.sendPing { error in
                    if error != nil {
                        socket.cancel(with: .abnormalClosure, reason: nil)
                    }
                }
            }
        }
    }

    private func websocketURL() -> URL? {
        guard let baseURL = try? configuration.currentBaseURL(),
              var components = URLComponents(url: baseURL, resolvingAgainstBaseURL: false) else {
            return nil
        }
        components.scheme = components.scheme == "http" ? "ws" : "wss"
        components.path = "/ws"
        components.query = nil
        return components.url
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
}
