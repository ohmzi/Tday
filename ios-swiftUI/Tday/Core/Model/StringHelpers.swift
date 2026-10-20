import Foundation

extension Optional where Wrapped == String {
    var nilIfBlank: String? {
        guard let self else {
            return nil
        }
        let trimmed = self.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
