import Foundation

extension Optional where Wrapped == String {
    var nilIfBlank: String? {
        guard let self else {
            return nil
        }
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
