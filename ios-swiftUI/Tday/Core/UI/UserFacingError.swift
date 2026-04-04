import Foundation

func userFacingMessage(for error: Error, fallback: String = "Something went wrong. Please try again.") -> String {
    if error is DecodingError || "\(error)".contains("DecodingError") {
        return "This version of the app is out of date. Please update to continue."
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
            return "Server error. Please try again later."
        default:
            break
        }
    }

    if let urlError = error as? URLError {
        switch urlError.code {
        case .cannotConnectToHost, .cannotFindHost, .networkConnectionLost,
             .notConnectedToInternet, .timedOut:
            return "Connection error. Check your internet and try again."
        default:
            break
        }
    }

    let desc = error.localizedDescription.lowercased()
    if desc.contains("serial name") || desc.contains("required for type") ||
       desc.contains("codingkeys") || desc.contains("no value associated with key") {
        return "This version of the app is out of date. Please update to continue."
    }

    return fallback
}
