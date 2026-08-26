import SwiftUI

/// What a screen shows when it has nothing to show.
///
/// Every one of these used to be a single line of grey text in the middle of an
/// otherwise blank screen, which reads as a page that failed to load rather than
/// as one that is simply empty. This gives the state something to look at: a
/// little stack of cards with the screen's own glyph on the corner, tinted with
/// that screen's accent so the empty view still tells you where you are.
///
/// The twin of the web `EmptyState` component — same geometry, same tints, so a
/// user moving between the PWA and the app sees one drawing, not two.
///
/// It never cuts in. The scene rises and fades up over half a second, because
/// the frame before it is the row the user just ticked off leaving the screen,
/// and a state that simply appears in that gap reads as the list breaking
/// rather than as the list being finished.
///
/// - Parameters:
///   - assetName: a Lucide template glyph in `Assets.xcassets`, the same one the
///     screen shows in its own header.
///   - accentColor: the screen's accent. Every tint in the scene is this colour
///     at an alpha, which is what the web's `color-mix(… , transparent)` resolves
///     to — an arbitrary accent has to carry four different alphas at once.
///   - celebrate: the list emptied because the user finished it, rather than
///     because there was never anything in it: confetti flies first and the
///     scene comes up through it a beat later.
struct TdayEmptyState: View {
    let assetName: String
    let accentColor: Color
    let title: String
    var description: String? = nil
    var action: AnyView? = nil
    var celebrate: Bool = false

    @Environment(\.tdayColors) private var colors
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var animating = false
    /// The arrival, once: 0 is low and invisible, 1 is the finished state.
    @State private var entered = false

    /// Reduced motion holds the scene at the top of the twinkle instead of
    /// switching it off — a half-drawn scene looks broken, not calm.
    private var floating: Bool { reduceMotion ? false : animating }
    private var twinkling: Bool { reduceMotion ? true : animating }

    /// Half of the web keyframe's duration in each direction: `autoreverses` makes
    /// the round trip, so 3s here is the 6s float there.
    private var floatAnimation: Animation? {
        guard !reduceMotion else { return nil }
        return .easeInOut(duration: 3).repeatForever(autoreverses: true)
    }

    private func twinkleAnimation(delay: Double) -> Animation? {
        guard !reduceMotion else { return nil }
        return .easeInOut(duration: 1.4).repeatForever(autoreverses: true).delay(delay)
    }

    var body: some View {
        VStack(spacing: 0) {
            scene
                // Hung off the scene rather than off the whole state: the scene
                // has a size of its own, and an overlay on the outer stack is
                // proposed a zero width — a canvas given that draws nothing.
                // Outside the fade below too, so the paper is at full strength
                // while the illustration is still coming up through it.
                .overlay {
                    if celebrate {
                        TdayConfetti(accentColor: accentColor)
                            .frame(width: BurstBox.width, height: BurstBox.height)
                    }
                }
                .padding(.bottom, 28)
                .allowsHitTesting(false)
                .accessibilityHidden(true)

            VStack(spacing: 8) {
                Text(title)
                    .font(.tdayRounded(size: 24, weight: .black))
                    .foregroundStyle(colors.onSurface)
                    .multilineTextAlignment(.center)
                if let description {
                    Text(description)
                        .font(.tdayRounded(size: 14, weight: .semibold))
                        .foregroundStyle(colors.onSurfaceVariant)
                        .multilineTextAlignment(.center)
                }
            }
            .accessibilityElement(children: .combine)

            if let action {
                action.padding(.top, 24)
            }
        }
        .padding(.horizontal, 24)
        .frame(maxWidth: 384)
        .frame(maxWidth: .infinity)
        // Opted out of keyboard avoidance. SwiftUI shrinks the safe area when a
        // keyboard or a sheet comes up, and every screen that shows this scene
        // centres it in what is left — so opening the create-task sheet, or a
        // search field, slid the whole illustration upward. Nothing here is
        // typed into and nothing here can be hidden behind a keyboard, so it has
        // no reason to move at all.
        .ignoresSafeArea(.keyboard, edges: .bottom)
        .opacity(entered ? 1 : 0)
        .scaleEffect(entered ? 1 : EmptyStateEnter.startScale)
        .offset(y: entered ? 0 : EmptyStateEnter.rise)
        .onAppear {
            animating = true
            // Reduced motion still gets the finished state, just not the trip:
            // a scene held at the start of its fade looks half-drawn.
            guard !reduceMotion else {
                entered = true
                return
            }
            // The burst leads; the scene follows through it. Without the wait the
            // illustration is already sitting there when the first piece of paper
            // clears it, and the confetti reads as decoration on a static page.
            withAnimation(
                .easeOut(duration: EmptyStateEnter.duration)
                    .delay(celebrate ? TdayConfettiMetrics.sceneLead : 0)
            ) {
                entered = true
            }
        }
    }

    private var scene: some View {
        ZStack(alignment: .topLeading) {
            fannedCard(rotation: -9, tint: 0.14).offset(x: 12, y: 16)
            fannedCard(rotation: 7, tint: 0.22).offset(x: 40, y: 12)
            frontCard.offset(x: 24, y: 24)
            glyphBadge.offset(x: 120, y: 76)
            sparkle(size: 13, delay: 0).offset(x: 6, y: 2)
            sparkle(size: 9, delay: 0.7).offset(x: 0, y: 96)
            sparkle(size: 11, delay: 1.4).offset(x: 150, y: 12)
        }
        .frame(width: 172, height: 136, alignment: .topLeading)
        .offset(y: floating ? -7 : 0)
        .animation(floatAnimation, value: floating)
    }

    /// Tinted rather than filled with `surface`, so the two behind read as depth
    /// instead of as two more empty cards.
    private func fannedCard(rotation: Double, tint: Double) -> some View {
        RoundedRectangle(cornerRadius: 18, style: .continuous)
            .fill(accentColor.opacity(tint))
            .frame(width: 128, height: 86)
            .rotationEffect(.degrees(rotation))
    }

    private var frontCard: some View {
        VStack(alignment: .leading, spacing: 9) {
            taskRow(barWidth: 44, barTint: 0.30, done: true)
            taskRow(barWidth: 62, barTint: 0.52, done: false)
            taskRow(barWidth: 38, barTint: 0.52, done: false)
        }
        .padding(.horizontal, 16)
        .frame(width: 130, height: 88, alignment: .leading)
        .background(colors.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(colors.cardStroke, lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(colors.isDark ? 0.34 : 0.14), radius: 14, x: 0, y: 10)
    }

    private func taskRow(barWidth: CGFloat, barTint: Double, done: Bool) -> some View {
        HStack(spacing: 10) {
            ZStack {
                if done {
                    Circle().fill(accentColor)
                    // Hand-drawn rather than `LucideCheck`: the shared glyph carries a
                    // 2/24 stroke, which is a third of a point inside a 12pt dot and
                    // vanishes on screen.
                    TdayEmptyTickShape()
                        .stroke(
                            colors.onPrimary,
                            style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round)
                        )
                        .frame(width: 8, height: 8)
                } else {
                    Circle().strokeBorder(accentColor.opacity(0.45), lineWidth: 1.5)
                }
            }
            .frame(width: 12, height: 12)

            Capsule()
                .fill(accentColor.opacity(barTint))
                .frame(width: barWidth, height: 5)
        }
    }

    /// The ring is drawn as a wider circle behind rather than a stroke, because a
    /// SwiftUI stroke centres on the path and would eat 2pt of the accent fill.
    private var glyphBadge: some View {
        Circle()
            .fill(accentColor)
            .frame(width: 52, height: 52)
            .overlay(
                Image(assetName)
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 24, height: 24)
                    .foregroundStyle(colors.onPrimary)
            )
            .padding(4)
            .background(Circle().fill(colors.background))
            .shadow(color: Color.black.opacity(colors.isDark ? 0.34 : 0.14), radius: 10, x: 0, y: 8)
    }

    private func sparkle(size: CGFloat, delay: Double) -> some View {
        TdayEmptySparkleShape()
            .fill(accentColor.opacity(0.70))
            .frame(width: size, height: size)
            .scaleEffect(twinkling ? 1 : 0.7)
            .opacity(twinkling ? 1 : 0.3)
            .animation(twinkleAnimation(delay: delay), value: twinkling)
    }
}

/// The four-point sparkle, traced from the same path the web scene uses so the
/// two drawings stay identical.
private struct TdayEmptySparkleShape: Shape {
    func path(in rect: CGRect) -> Path {
        func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(
                x: rect.minX + (x / 24 * rect.width),
                y: rect.minY + (y / 24 * rect.height)
            )
        }

        var path = Path()
        path.move(to: point(12, 0))
        path.addCurve(to: point(24, 12), control1: point(12.6, 6.2), control2: point(17.2, 10.8))
        path.addCurve(to: point(12, 24), control1: point(17.2, 13.2), control2: point(12.6, 17.8))
        path.addCurve(to: point(0, 12), control1: point(11.4, 17.8), control2: point(6.8, 13.2))
        path.addCurve(to: point(12, 0), control1: point(6.8, 10.8), control2: point(11.4, 6.2))
        path.closeSubpath()
        return path
    }
}

private struct TdayEmptyTickShape: Shape {
    func path(in rect: CGRect) -> Path {
        func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(
                x: rect.minX + (x / 12 * rect.width),
                y: rect.minY + (y / 12 * rect.height)
            )
        }

        var path = Path()
        path.move(to: point(3, 6.2))
        path.addLine(to: point(5, 8.2))
        path.addLine(to: point(9, 3.9))
        return path
    }
}

/// The scene's own arrival: long enough to read as a hand-off, not as a page load.
private enum BurstBox {
    /// The burst's own canvas, centred on the illustration. Fixed rather than
    /// inherited: a `Canvas` clips to its frame, and this is the area the paper
    /// is allowed to cross.
    static let width: CGFloat = 340
    static let height: CGFloat = 460
}

private enum EmptyStateEnter {
    static let duration: Double = 0.52
    static let startScale: Double = 0.92
    static let rise: Double = 18
}
