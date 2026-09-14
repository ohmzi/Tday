import SwiftUI

/// The burst that plays when the user ticks off the last thing they had left.
///
/// Deliberately not a package and not a sprite sheet: a few dozen rounded
/// rectangles on one `Canvas` is the whole effect. What makes them read as paper
/// is the model behind them — one linear drag, which spends the throw, gives the
/// fall a terminal speed instead of letting it accelerate forever, and lets
/// every attribute be written in closed form. That model lives in
/// `TdayConfettiKinematics` and this view only draws what it returns.
///
/// Pieces turn edge-on on an axis of their own, unrelated to the in-plane spin.
/// The two used to be the same angle, which made every piece a propeller and a
/// coin at once and is what the old version's mechanical wobble was.
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

    @Environment(\.tdayAnimation) private var tdayAnimation
    /// Set on appear rather than at init: a `View` is re-initialised freely, and
    /// a start date taken in `init` restarts the flight on every one of those.
    @State private var startedAt: Date?
    /// The burst is over. `TimelineView(.animation)` ticks for as long as it is
    /// on screen, and the empty state it sits on can stand for minutes — so the
    /// view takes itself out rather than redrawing an empty canvas at 60fps.
    @State private var landed = false

    private static let pieces = ConfettiPiece.fan()

    var body: some View {
        // The pop that should land on the frame the first pieces leave is not
        // here yet, and cannot hang off this shape's `.onAppear`: that modifier
        // sits inside the animating branch, so with reduce motion on it never
        // runs at all — and that is the case it matters in most, because a
        // finished list is still finished and a haptic is not motion. A
        // persistent `ZStack` around the branch is what makes one mount equal one
        // pop on either side of it; see the haptic section of
        // `docs/confetti-spec.md`, which argues the restructure and the
        // dedupe against Today's scene haptic together.
        if !tdayAnimation.isEnabled || landed {
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
            // Staggered launches: one salvo of forty-six pieces reads as a single
            // expanding ring rather than as confetti. A piece still waiting, or
            // one whose own flight is over, has no frame at all.
            guard let frame = TdayConfettiKinematics.frame(piece, at: t) else { continue }

            let width = piece.width * frame.widthScale
            let height = piece.height

            var piecePainter = context
            piecePainter.opacity = frame.alpha
            piecePainter.translateBy(x: origin.x + frame.dx * span, y: origin.y + frame.dy * span)
            piecePainter.rotate(by: .radians(frame.rot))
            piecePainter.fill(
                Path(
                    roundedRect: CGRect(x: -width / 2, y: -height / 2, width: width, height: height),
                    // The DRAWN width, so a piece caught edge-on stays a rounded
                    // sliver rather than growing a flat side as it narrows.
                    cornerRadius: width * 0.4
                ),
                with: .color(palette[piece.colorIndex % palette.count])
            )
        }
    }
}

/// One piece of paper: thrown in fractions of the burst box, sized in points.
///
/// The six values at the bottom are derived from the rolled ones and cached
/// here, not recomputed per frame: forty-six pieces times sixty frames times a
/// `cos` is work nobody can see, and the invariants are easier to state about a
/// piece that already knows its own terminal speed than about one that does not.
struct ConfettiPiece: Equatable {
    let angle: Double
    /// Launch SPEED in span per flight, not the distance this piece will cover.
    /// Drag turns it into a finite reach of `speed / k`, and it is the spread of
    /// those reaches that gives the cloud its depth.
    let speed: Double
    /// The TOTAL in-plane turn, in radians, spent as the throw is spent — so a
    /// piece stops turning as it stops travelling.
    let spin: Double
    let spinPhase: Double
    /// Turning edge-on runs on its own axis at its own rate, undamped, and is
    /// deliberately unrelated to `spin`: locking them together is what made the
    /// old burst look mechanical.
    let flipRate: Double
    let flipPhase: Double
    let width: Double
    let height: Double
    let colorIndex: Int
    let delay: Double
    /// Per-piece drag, so forty-six pieces do not share one terminal speed and
    /// fall as a sheet.
    let dragScale: Double
    /// How far along its own throw angle this piece starts: the burst leaves a
    /// thumb-sized patch rather than a single pixel.
    let muzzle: Double
    let swayAmp: Double
    let swayRate: Double
    let swayPhase: Double

    // Derived once, in `init`, from the rolled values above.
    let vx0: Double
    let vy0: Double
    /// This piece's drag coefficient, per flight.
    let k: Double
    /// Terminal speed, `gravity / k`. The number the eye actually reads off the
    /// fall — the gravity constant on its own says nothing without it.
    let vt: Double
    let mx: Double
    let my: Double

    init(
        angle: Double,
        speed: Double,
        spin: Double,
        spinPhase: Double,
        flipRate: Double,
        flipPhase: Double,
        width: Double,
        height: Double,
        colorIndex: Int,
        delay: Double,
        dragScale: Double,
        muzzle: Double,
        swayAmp: Double,
        swayRate: Double,
        swayPhase: Double
    ) {
        self.angle = angle
        self.speed = speed
        self.spin = spin
        self.spinPhase = spinPhase
        self.flipRate = flipRate
        self.flipPhase = flipPhase
        self.width = width
        self.height = height
        self.colorIndex = colorIndex
        self.delay = delay
        self.dragScale = dragScale
        self.muzzle = muzzle
        self.swayAmp = swayAmp
        self.swayRate = swayRate
        self.swayPhase = swayPhase

        let cosAngle = cos(angle)
        let sinAngle = sin(angle)
        let drag = TdayConfettiMetrics.drag * dragScale
        self.vx0 = cosAngle * speed
        self.vy0 = sinAngle * speed
        self.k = drag
        self.vt = TdayConfettiMetrics.gravity / drag
        self.mx = muzzle * cosAngle
        self.my = muzzle * sinAngle
    }

    /// The fan, rolled from a fixed seed: the burst is the same every time, which
    /// is what makes it read as a designed celebration rather than a random one.
    ///
    /// What the three clients share is the ORDER of the draws and the
    /// distributions they come from, never the values — Compose's `Random`, this
    /// LCG and web's mulberry32 give three different sequences from the same
    /// seed. Sixteen draws, in exactly this order:
    ///
    /// 1 angle jitter U[0,1) · 2 speed U[1.10,1.95] · 3 spin sign · 4 spin
    /// magnitude U[1.5,4.5] · 5 spinPhase U[0,2π) · 6 flipRate U[6,12] ·
    /// 7 flipPhase U[0,2π) · 8 width U[5,9] · 9 height U[8,13] · 10 colorIndex
    /// U[0,7] · 11 delay U[0,0.16) · 12 dragScale U[0.90,1.10] · 13 muzzle
    /// U[0,0.03] · 14 swayAmp U[0.018,0.04] · 15 swayRate U[9,15] · 16 swayPhase
    /// U[0,2π).
    ///
    /// Inserting a draw in the middle re-rolls every piece after it. That is a
    /// choreography change on all three clients, not a refactor.
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
                speed: .random(in: 1.10...1.95, using: &random),
                // A total turn rather than a rate. The 3.5...12.5 this replaces
                // was rad-per-flight, and — checked, not assumed — it was the
                // same 3.5...12.5 on all three clients: the iOS/Android spin
                // parity bug the confetti brief claimed never existed. What was
                // wrong was that the number drove the flip as well as the turn.
                spin: (Bool.random(using: &random) ? 1 : -1) * .random(in: 1.5...4.5, using: &random),
                spinPhase: .random(in: 0..<(2 * .pi), using: &random),
                flipRate: .random(in: 6...12, using: &random),
                flipPhase: .random(in: 0..<(2 * .pi), using: &random),
                width: .random(in: 5...9, using: &random),
                height: .random(in: 8...13, using: &random),
                colorIndex: Int.random(in: 0...TdayConfettiMetrics.palette.count, using: &random),
                delay: .random(in: 0..<0.16, using: &random),
                dragScale: .random(in: 0.90...1.10, using: &random),
                muzzle: .random(in: 0..<0.03, using: &random),
                swayAmp: .random(in: 0.018...0.04, using: &random),
                swayRate: .random(in: 9...15, using: &random),
                swayPhase: .random(in: 0..<(2 * .pi), using: &random)
            )
        }
    }
}

/// Where the burst's physics lives, with no `Canvas` anywhere near it.
///
/// Split off the drawing because every claim worth making about this effect is a
/// claim about these few lines and none of it needs a screen: that a piece
/// leaves from the muzzle patch and not from a point, that outward travel is
/// concave and its reach finite, that the fall approaches a terminal speed from
/// both sides and never crosses it, that nothing leaves the box, that the fade
/// ends at zero with zero slope. `TdayConfettiKinematicsTests` asserts all of
/// them; the view above is then only arithmetic on the result.
///
/// One model — linear (Stokes) drag — in closed form. As `drag → 0` the fall
/// term collapses back to `gravity · tau²`, which is the old burst exactly, so
/// this is a strict superset of the physics it replaces rather than a different
/// effect that happens to look similar.
enum TdayConfettiKinematics {

    /// One piece at one instant: a position in fractions of the box's WIDTH from
    /// the origin, a rotation in radians, and a width scale that is the edge-on
    /// flip alone. The height never changes — a piece of paper seen at an angle
    /// loses width, not length.
    struct ConfettiFrame: Equatable {
        let dx: Double
        let dy: Double
        let rot: Double
        let widthScale: Double
        let alpha: Double
    }

    /// `nil` whenever the piece is not in the air: before its own launch, and at
    /// or after the end of its own flight. The `tau >= 1` exit is here rather
    /// than only at the draw site so that "the burst has nothing left to show"
    /// is a property of the model, provable without rendering a frame.
    static func frame(_ piece: ConfettiPiece, at t: Double) -> ConfettiFrame? {
        let tau = (t - piece.delay) / (1 - piece.delay)
        guard tau > 0, tau < 1 else { return nil }

        // Integrating v' = -k·v gives both of these: the fraction of the launch
        // velocity still left, and the ground a unit launch speed has covered by
        // now. Nothing here needs clamping — k is at least 5.4 and `1 - delay`
        // at least 0.84, so neither denominator can approach zero.
        let decay = exp(-piece.k * tau)
        let travel = (1 - decay) / piece.k
        // The throw spent so far, which doubles as the flutter envelope: sway and
        // turn arrive as the throw runs out instead of being there from the
        // first frame, when the piece is moving too fast for either to be seen.
        let spent = 1 - decay

        let sway = piece.swayRate * tau + piece.swayPhase
        let flip = piece.flipPhase + piece.flipRate * tau

        return ConfettiFrame(
            dx: piece.mx + piece.vx0 * travel + piece.swayAmp * sin(sway) * spent,
            // `tau - travel` is the ground the terminal fall has covered while
            // the throw was dying: the two add rather than compete, which is why
            // a piece thrown upward comes back down at the same speed as one
            // thrown along.
            dy: piece.my + piece.vy0 * travel + piece.vt * (tau - travel),
            // The lean rides the sway's own phase, so a piece tips into the
            // swing rather than turning on a clock of its own.
            rot: piece.spinPhase + spent * (piece.spin + TdayConfettiMetrics.rock * sin(sway)),
            // |cos| of an axis all of its own is the piece turning edge-on; the
            // floor keeps it from disappearing completely on the way round.
            widthScale: TdayConfettiMetrics.minFlip
                + (1 - TdayConfettiMetrics.minFlip) * abs(cos(flip)),
            alpha: alpha(tau)
        )
    }

    /// Opaque, then a smoothstep to nothing.
    ///
    /// Its own entry point rather than only a line inside `frame`, because the
    /// one value most worth pinning is `alpha(1) == 0` — the frame that proves
    /// the burst ends without an edge — and `frame` returns `nil` there. Defined
    /// on `[0, 1]`, which is the only interval `frame` ever asks about; the
    /// smoothstep is deliberately not clamped, since a clamp here would be dead
    /// code pretending to be a safeguard.
    static func alpha(_ tau: Double) -> Double {
        guard tau >= TdayConfettiMetrics.fadeStart else { return 1 }
        let u = (tau - TdayConfettiMetrics.fadeStart) / (1 - TdayConfettiMetrics.fadeStart)
        // Written out rather than reached for through `pow`: the three clients
        // have to agree to the last bit, and this is the one form all three
        // spell the same way.
        return 1 - u * u * (3 - 2 * u)
    }
}

/// Shared numbers, kept in one place so the Compose and web bursts can be read
/// against them line for line.
enum TdayConfettiMetrics {
    static let pieceCount = 46

    /// Not a token — see docs/motion.md. This is the burst's own physics clock,
    /// not a length chosen off the ladder: drag, gravity, spin and sway are all
    /// expressed *per flight*, so this number is the unit they are measured in.
    /// Naming a rung here would claim the burst is an animation somebody watches
    /// end, and it is a simulation that runs out.
    ///
    /// Read twice in this file — the clock the `Canvas` normalises against, and
    /// the sleep that takes the view back out — and those two must always be the
    /// same constant, or the view survives its own last frame.
    static let flightSeconds: Double = 2.0

    /// Where the burst is thrown from, as a fraction of the box: the scene's heart.
    static let originX: Double = 0.5
    static let originY: Double = 0.28

    /// Up and out: 200°..340°, measured with y growing downward.
    static let fanStart: Double = 200 * .pi / 180
    static let fanSweep: Double = 140 * .pi / 180

    /// Linear drag, per flight. Everything the eye reads follows from it: a
    /// throw is 63 % spent one time constant in (a third of a second), reach is
    /// finite at `speed / k` instead of growing forever, and the fall settles
    /// rather than accelerating.
    static let drag: Double = 6.0

    /// Span per flight squared, and not comparable with the 0.95 it replaces —
    /// that was g/2 with nothing to fall against. What is actually visible is
    /// the terminal speed it implies with `drag`, 0.85 of the box per flight.
    static let gravity: Double = 5.1

    /// The lean a piece takes into its own sway, in radians: ±29° at the ends of
    /// the swing, in phase with sway position rather than on a clock of its own.
    /// Zero it here if the cloud reads busy — nothing else depends on it.
    static let rock: Double = 0.5

    static let minFlip: Double = 0.25

    /// Opaque for the first 1.2 s, then out on a smoothstep. A linear tail ends
    /// with a slope, and the eye reads a slope at zero as pieces being switched
    /// off rather than as paper leaving.
    static let fadeStart: Double = 0.60

    /// How long the burst has the screen to itself before the scene comes up.
    static let sceneLead: Double = TdayMotion.Delays.celebrationLead

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
