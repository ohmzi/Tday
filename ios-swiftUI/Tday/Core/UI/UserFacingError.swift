import Foundation

struct MessageError: LocalizedError, Equatable {
    let message: String

    var errorDescription: String? {
        message
    }
}

/// Distinct, plain-language messages for the ways a request can fail. Each is
/// written so a non-technical user knows the next step AND carries enough signal
/// ("contact your administrator", "update the app") to report the right problem —
/// instead of one catch-all "something went wrong" / "cannot reach server".
enum ConnectionFailureMessage {
    /// The request never reached the server (no internet, wrong address, DNS, refused, timeout).
    static let cannotReach = "Can't reach the server. Check your internet connection and that the server address is correct."
    /// The server answered but is unhealthy (down, restarting, gateway/5xx) — an admin needs to look.
    static let serverUnavailable = "The server isn't responding right now — it may be down or restarting. If this keeps happening, contact your administrator."
    /// This app build is older than the server requires.
    static let appOutdated = "Your app is out of date. Please update to the latest version to continue."
    /// This app build is newer than the server — the admin needs to upgrade the server.
    static let serverOutdated = "This app is newer than the server. Ask your administrator to update the server."
    /// Nothing more specific could be determined.
    static let generic = "Something went wrong. Please try again. If it keeps happening, contact your administrator."

    /// The full set, so callers can detect an already-classified message and pass it through unchanged.
    static let all: Set<String> = [cannotReach, serverUnavailable, appOutdated, serverOutdated, generic]
}

/// Classifies an error into one of the distinct connection / version / availability
/// messages, or returns nil when it is a different kind of failure (so the caller can
/// surface a more specific domain message, e.g. "Incorrect username or password").
func connectionFailureMessage(for error: Error) -> String? {
    if let apiError = error as? APIError {
        if apiError.statusCode == 426 || apiError.reason == "app_update_required" {
            return ConnectionFailureMessage.appOutdated
        }
        if apiError.reason == "server_update_required" {
            return ConnectionFailureMessage.serverOutdated
        }
        if let code = apiError.statusCode {
            // A real HTTP response: only 5xx/gateway codes are "server is unhealthy".
            return isLikelyServerUnavailableStatusCode(code) ? ConnectionFailureMessage.serverUnavailable : nil
        }
        // No HTTP status code means the request never reached the server.
        return ConnectionFailureMessage.cannotReach
    }

    if let urlError = error as? URLError {
        switch urlError.code {
        case .cannotConnectToHost, .cannotFindHost, .dnsLookupFailed,
             .networkConnectionLost, .notConnectedToInternet, .timedOut,
             .cannotLoadFromNetwork:
            return ConnectionFailureMessage.cannotReach
        default:
            return nil
        }
    }

    if error is DecodingError || "\(error)".contains("DecodingError") {
        return ConnectionFailureMessage.appOutdated
    }

    return nil
}

func userFacingMessage(for error: Error, fallback: String = ConnectionFailureMessage.generic) -> String {
    if let message = connectionFailureMessage(for: error) {
        return message
    }

    if let apiError = error as? APIError, let code = apiError.statusCode {
        switch code {
        case 401:
            return "Session expired. Please sign in again."
        case 403:
            return "You don't have permission to do that."
        case 404:
            return "The requested item was not found."
        case 500...599:
            return ConnectionFailureMessage.serverUnavailable
        default:
            break
        }
    }

    let desc = error.localizedDescription.lowercased()
    if desc.contains("serial name") || desc.contains("required for type") ||
       desc.contains("codingkeys") || desc.contains("no value associated with key") {
        return ConnectionFailureMessage.appOutdated
    }

    return fallback
}
