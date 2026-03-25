import Foundation
import Observation

@Observable
final class ServerURLState {
    var currentURL: URL?

    init(currentURL: URL?) {
        self.currentURL = currentURL
    }
}
