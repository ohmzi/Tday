import AVFoundation
import Foundation

/// The short pop played when a task is checked off — the same clip the web app plays, so
/// completing a task sounds identical on web, Android and iOS.
///
/// The player is built once and kept: `AVAudioPlayer` decodes on init, and creating one per tap
/// would put that decode on the main thread right as the completion animation starts.
enum SoundManager {

    private static let player: AVAudioPlayer? = {
        guard let url = Bundle.main.url(forResource: "task_complete", withExtension: "wav") else {
            return nil
        }
        let player = try? AVAudioPlayer(contentsOf: url)
        player?.volume = 0.5
        // Decode and buffer now so the first completion is as prompt as the rest.
        player?.prepareToPlay()
        return player
    }()

    /// Plays the completion pop.
    ///
    /// The session is `.ambient`, which is what makes this behave like a UI sound rather than
    /// media: it mixes with whatever is already playing instead of interrupting it, and it stays
    /// silent when the ring/silent switch is off — the same expectation the keyboard clicks set.
    static func taskCompleted() {
        guard let player else { return }
        try? AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
        // Rewind rather than ignoring the call: checking off two tasks quickly should pop twice.
        player.currentTime = 0
        player.play()
    }
}
