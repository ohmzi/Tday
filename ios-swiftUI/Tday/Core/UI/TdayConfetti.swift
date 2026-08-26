import SwiftUI

/// The burst that plays when the user ticks off the last thing they had left.
///
/// Deliberately not a package and not a sprite sheet: a few dozen rounded
/// rectangles on one `Canvas`, thrown from a single point and pulled back down,
/// is the whole effect. Pieces flip as they fly — the width is scaled by the
/// cosine of their own spin — which is what reads as paper rather than as
/// coloured dots.
///
/// The twin of the Compose `TdayConfetti` and the web `Confetti` component; the
/// three share piece count, fan, timing and palette so finishing a list feels
/// the same wherever the user does it.
///
/// Draws outside its own bounds on purpose (pieces fly above the scene it sits
/// on), so it belongs in an `.overlay` that is not clipped.
struct TdayConfetti: View {
    /// The screen's own accent, mixed into the palette so the celebration still
    /// belongs to the list it happened on.
    let accentColor: Color

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Set on appear rather than at init: a `View` is re-initialised freely, and
    /// a start date taken in `init` restarts the flight on every one of those.
    @State private var startedAt: Date?
    /// The burst is over. `TimelineView(.animation)` ticks for as long as it is
    /// on screen, and the empty state it sits on can stand for minutes — so the
    /// view takes itself out rather than redrawing an empty canvas at 60fps.
    @State private var landed = false

    private static let pieces = ConfettiPiece.fan()

    var body: some View {
        if reduceMotion || landed {
            Color.clear.frame(width: 0, height: 0)
        } else {
            TimelineView(.animation) { timeline in
                Canvas { context, size in
                    guard let startedAt else { return }
                    let t = timeline.date.timeIntervalSince(startedAt) / TdayConfettiMetrics.flightSeconds
                    guard t > 0, t < 1 else { return }
                    draw(in: &context, size: size, at: t)
                }
            }
            .allowsHitTesting(false)
            .accessibilityHidden(true)
            .onAppear { startedAt = .now }
            .task {
                try? await Task.sleep(for: .seconds(TdayConfettiMetrics.flightSeconds))
                landed = true
            }
        }
    }

    private func draw(in context: inout GraphicsContext, size: CGSize, at t: Double) {
        // Everything is thrown in fractions of the box's WIDTH — not of its
        // longest side, which is the screen's height on a phone and throws every
        // piece clean off the sides before it can be seen.
        let span = size.width
        let origin = CGPoint(
            x: size.width * TdayConfettiMetrics.originX,
            y: size.height * TdayConfettiMetrics.originY
        )
        let palette = TdayConfettiMetrics.palette + [accentColor]

        for piece in Self.pieces {
            // Staggered launches: one salvo of forty pieces reads as a single
            // expanding ring rather than as confetti.
            let local = (t - piece.delay) / (1 - piece.delay)
            guard local > 0 else { continue }

            let travelled = piece.speed * local
            let x = origin.x + cos(piece.angle) * travelled * span
            let y = origin.y
                + sin(piece.angle) * travelled * span
                + TdayConfettiMetrics.gravity * local * local * span

            // Full opacity for the first half of the flight, then out — pieces
            // that vanish at the apex look like a dropped frame.
            let alpha = local < TdayConfettiMetrics.fadeStart
                ? 1
                : 1 - (local - TdayConfettiMetrics.fadeStart) / (1 - TdayConfettiMetrics.fadeStart)

            let spin = piece.spinPhase + piece.spin * local
            // |cos| of the spin is the piece turning edge-on to the viewer; the
            // floor keeps it from disappearing completely on the way round.
            let flip = TdayConfettiMetrics.minFlip + (1 - TdayConfettiMetrics.minFlip) * abs(cos(spin))
            let width = piece.width * flip
            let height = piece.height

            var piecePainter = context
            piecePainter.opacity = alpha
            piecePainter.translateBy(x: x, y: y)
            piecePainter.rotate(by: .radians(spin))
            piecePainter.fill(
                Path(
                    roundedRect: CGRect(x: -width / 2, y: -height / 2, width: width, height: height),
                    cornerRadius: width * 0.4
                ),
                with: .color(palette[piece.colorIndex % palette.count])
            )
        }
    }
}

/// One piece of paper: thrown in fractions of the burst box, sized in points.
private struct ConfettiPiece {
    let angle: Double
    let speed: Double
    let spin: Double
    let spinPhase: Double
    let width: Double
    let height: Double
    let colorIndex: Int
    let delay: Double

    /// The fan, rolled from a fixed seed: the burst is the same every time, which
    /// is what makes it read as a designed celebration rather than a random one.
    static func fan() -> [ConfettiPiece] {
        var random = SeededGenerator(seed: 0x7D_A9_10_2B)
        return (0..<TdayConfettiMetrics.pieceCount).map { index in
            // Fanned up and out rather than in a full circle. A ring throws half
            // its pieces straight down through the copy, where they read as a
            // glitch.
            let step = (Double(index) + Double.random(in: 0..<1, using: &random))
                / Double(TdayConfettiMetrics.pieceCount)
            return ConfettiPiece(
                angle: TdayConfettiMetrics.fanStart + TdayConfettiMetrics.fanSweep * step,
                speed: .random(in: 0.30...0.78, using: &random),
                spin: (Bool.random(using: &random) ? 1 : -1) * .random(in: 3.5...12.5, using: &random),
                spinPhase: .random(in: 0..<(2 * .pi), using: &random),
                width: .random(in: 5...9, using: &random),
                height: .random(in: 8...13, using: &random),
                colorIndex: Int.random(in: 0...TdayConfettiMetrics.palette.count, using: &random),
                delay: .random(in: 0..<0.16, using: &random)
            )
        }
    }
}

/// Shared numbers, kept in one place so the Compose and web bursts can be read
/// against them line for line.
enum TdayConfettiMetrics {
    static let pieceCount = 46
    static let flightSeconds: Double = 1.8

    /// Where the burst is thrown from, as a fraction of the box: the scene's heart.
    static let originX: Double = 0.5
    static let originY: Double = 0.28

    /// Up and out: 200°..340°, measured with y growing downward.
    static let fanStart: Double = 200 * .pi / 180
    static let fanSweep: Double = 140 * .pi / 180

    static let gravity: Double = 0.95
    static let minFlip: Double = 0.25
    static let fadeStart: Double = 0.55

    /// How long the burst has the screen to itself before the scene comes up.
    static let sceneLead: Double = 0.32

    /// A festive subset of the list palette rather than a new set of colours, so
    /// the burst is made of shades the app already uses.
    static let palette: [Color] = [
        Color(red: 0.878, green: 0.322, blue: 0.600), // PINK
        Color(red: 0.910, green: 0.647, blue: 0.188), // GOLD
        Color(red: 0.235, green: 0.604, blue: 0.867), // DEEP_BLUE
        Color(red: 0.180, green: 0.722, blue: 0.675), // TEAL
        Color(red: 0.275, green: 0.725, blue: 0.388), // LIME
        Color(red: 0.490, green: 0.404, blue: 0.714), // PURPLE
        Color(red: 0.902, green: 0.400, blue: 0.298), // CORAL
    ]
}

/// A three-line LCG. `SystemRandomNumberGenerator` would re-roll the fan on every
/// launch; the burst is choreography, so it is seeded and repeatable.
private struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64

    init(seed: UInt64) {
        state = seed &* 6_364_136_223_846_793_005 &+ 1_442_695_040_888_963_407
    }

    mutating func next() -> UInt64 {
        state = state &* 6_364_136_223_846_793_005 &+ 1_442_695_040_888_963_407
        // The low bits of an LCG are famously poor; the shuffle spreads them.
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }
}
