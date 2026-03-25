import Foundation

struct RealtimeEvent: Equatable {
    let name: String
    let rawPayload: String

    var requiresRefresh: Bool {
        name.hasPrefix("todo.") ||
        name.hasPrefix("list.") ||
        name.hasPrefix("completed.") ||
        name.hasPrefix("completedtodo.")
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
        components.path = "/api/realtime"
        guard let url = components.url else {
            return
        }

        let request = URLRequest(url: url)
        let webSocketTask = configuration.session.webSocketTask(with: request)
        task = webSocketTask
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
            task = nil
            scheduleReconnect()
        }
    }

    private func parse(text: String) -> RealtimeEvent? {
        guard let data = text.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let eventName = json["event"] as? String
        else {
            return nil
        }
        return RealtimeEvent(name: eventName, rawPayload: text)
    }

    private func scheduleReconnect() {
        reconnectTask?.cancel()
        reconnectTask = Task {
            try? await Task.sleep(for: .seconds(3))
            await connectIfNeeded()
        }
    }
}
