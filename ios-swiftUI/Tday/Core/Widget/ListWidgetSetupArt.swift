import SwiftUI

/// The List widget's setup picture — the same shapes, in the same 120 × 100 space and colours, as
/// Android's `widget_list_setup_art.xml`: a list card with one task ticked, a second card peeking
/// out behind it, two sparkles and a "+" bubble.
///
/// Compiled into both the app and the widget extension: the unconfigured List widget draws it, and
/// so does the sheet the app shows when that widget is tapped (`ListWidgetSetupSheet`).
struct ListWidgetSetupArt: View {
    /// Height over width of the drawing.
    static let aspectRatio: CGFloat = 100.0 / 120.0

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let dark = colorScheme == .dark
        Canvas { context, size in
            let scale = min(size.width / 120, size.height / 100)
            context.translateBy(x: (size.width - 120 * scale) / 2, y: (size.height - 100 * scale) / 2)
            context.scaleBy(x: scale, y: scale)

            // The card behind, tilted.
            var behind = context
            behind.translateBy(x: 60, y: 52)
            behind.rotate(by: .degrees(-9))
            behind.translateBy(x: -60, y: -52)
            behind.fill(
                Path(roundedRect: CGRect(x: 26, y: 20, width: 68, height: 70), cornerRadius: 10),
                with: .color(dark ? .setupArtBackCardDark : .setupArtBackCard)
            )

            // The card in front, and its title bar.
            let card = Path(roundedRect: CGRect(x: 28, y: 14, width: 68, height: 74), cornerRadius: 10)
            context.fill(card, with: .color(dark ? .setupArtCardDark : .setupArtCard))
            context.stroke(card, with: .color(dark ? .setupArtCardEdgeDark : .setupArtCardEdge), lineWidth: 1.2)
            let accent: Color = dark ? .setupArtAccentDark : .setupArtAccent
            context.fill(Path(roundedRect: CGRect(x: 37, y: 23, width: 28, height: 6), cornerRadius: 3), with: .color(accent))

            // Three rows: one ticked, two open rings, each beside a line.
            let line: Color = dark ? .setupArtLineDark : .setupArtLine
            context.fill(Path(ellipseIn: CGRect(x: 38, y: 38, width: 10, height: 10)), with: .color(dark ? .setupArtGreenDark : .setupArtGreen))
            var tick = Path()
            tick.move(to: CGPoint(x: 40.8, y: 43.2))
            tick.addLine(to: CGPoint(x: 42.6, y: 45))
            tick.addLine(to: CGPoint(x: 45.6, y: 41.6))
            context.stroke(tick, with: .color(.white), style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
            context.fill(Path(roundedRect: CGRect(x: 51, y: 41, width: 33, height: 4), cornerRadius: 2), with: .color(line))
            context.stroke(
                Path(ellipseIn: CGRect(x: 38.8, y: 53.8, width: 8.4, height: 8.4)),
                with: .color(dark ? .setupArtOrangeDark : .setupArtOrange),
                lineWidth: 1.6
            )
            context.fill(Path(roundedRect: CGRect(x: 51, y: 56, width: 27, height: 4), cornerRadius: 2), with: .color(line))
            context.stroke(Path(ellipseIn: CGRect(x: 38.8, y: 68.8, width: 8.4, height: 8.4)), with: .color(accent), lineWidth: 1.6)
            context.fill(Path(roundedRect: CGRect(x: 51, y: 71, width: 21, height: 4), cornerRadius: 2), with: .color(line))

            // The "+" bubble.
            context.fill(Path(ellipseIn: CGRect(x: 86, y: 69, width: 22, height: 22)), with: .color(dark ? .setupArtPinkDark : .setupArtPink))
            var plus = Path()
            plus.move(to: CGPoint(x: 97, y: 74.5))
            plus.addLine(to: CGPoint(x: 97, y: 85.5))
            plus.move(to: CGPoint(x: 91.5, y: 80))
            plus.addLine(to: CGPoint(x: 102.5, y: 80))
            context.stroke(plus, with: .color(.white), style: StrokeStyle(lineWidth: 2.2, lineCap: .round))

            // Sparkles.
            let sparkle: Color = dark ? .setupArtSparkleDark : .setupArtSparkle
            context.fill(Self.sparkle(center: CGPoint(x: 104, y: 22), armX: 7, armY: 8, pinch: 1), with: .color(sparkle))
            context.fill(Self.sparkle(center: CGPoint(x: 17, y: 45), armX: 5, armY: 5, pinch: 0.7), with: .color(sparkle))
            context.fill(
                Path(ellipseIn: CGRect(x: 19.8, y: 73.8, width: 4.4, height: 4.4)),
                with: .color(dark ? .setupArtSparkleSoftDark : .setupArtSparkleSoft)
            )
        }
    }

    /// A four-point star drawn as Android's quadratic path is: tip to tip, each curve pulled in
    /// through a control point `pinch` off the centre on both axes.
    private static func sparkle(center: CGPoint, armX: CGFloat, armY: CGFloat, pinch: CGFloat) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: center.x, y: center.y - armY))
        path.addQuadCurve(to: CGPoint(x: center.x + armX, y: center.y), control: CGPoint(x: center.x + pinch, y: center.y - pinch))
        path.addQuadCurve(to: CGPoint(x: center.x, y: center.y + armY), control: CGPoint(x: center.x + pinch, y: center.y + pinch))
        path.addQuadCurve(to: CGPoint(x: center.x - armX, y: center.y), control: CGPoint(x: center.x - pinch, y: center.y + pinch))
        path.addQuadCurve(to: CGPoint(x: center.x, y: center.y - armY), control: CGPoint(x: center.x - pinch, y: center.y - pinch))
        path.closeSubpath()
        return path
    }
}

/// Numerically identical to Android's `tday_widget_setup_art_*` colours (light / values-night).
private extension Color {
    static let setupArtBackCard = Color(red: 246.0 / 255.0, green: 217.0 / 255.0, blue: 231.0 / 255.0)
    static let setupArtBackCardDark = Color(red: 58.0 / 255.0, green: 42.0 / 255.0, blue: 54.0 / 255.0)
    static let setupArtCard = Color.white
    static let setupArtCardDark = Color(red: 35.0 / 255.0, green: 40.0 / 255.0, blue: 52.0 / 255.0)
    static let setupArtCardEdge = Color(red: 227.0 / 255.0, green: 232.0 / 255.0, blue: 242.0 / 255.0)
    static let setupArtCardEdgeDark = Color(red: 52.0 / 255.0, green: 59.0 / 255.0, blue: 75.0 / 255.0)
    static let setupArtAccent = Color(red: 110.0 / 255.0, green: 168.0 / 255.0, blue: 225.0 / 255.0)
    static let setupArtAccentDark = Color(red: 141.0 / 255.0, green: 195.0 / 255.0, blue: 243.0 / 255.0)
    static let setupArtGreen = Color(red: 77.0 / 255.0, green: 143.0 / 255.0, blue: 131.0 / 255.0)
    static let setupArtGreenDark = Color(red: 127.0 / 255.0, green: 199.0 / 255.0, blue: 185.0 / 255.0)
    static let setupArtOrange = Color(red: 243.0 / 255.0, green: 166.0 / 255.0, blue: 75.0 / 255.0)
    static let setupArtOrangeDark = Color(red: 1.0, green: 180.0 / 255.0, blue: 84.0 / 255.0)
    static let setupArtPink = Color(red: 224.0 / 255.0, green: 82.0 / 255.0, blue: 156.0 / 255.0)
    static let setupArtPinkDark = Color(red: 240.0 / 255.0, green: 111.0 / 255.0, blue: 176.0 / 255.0)
    static let setupArtLine = Color(red: 221.0 / 255.0, green: 227.0 / 255.0, blue: 238.0 / 255.0)
    static let setupArtLineDark = Color(red: 57.0 / 255.0, green: 65.0 / 255.0, blue: 79.0 / 255.0)
    static let setupArtSparkle = Color(red: 247.0 / 255.0, green: 201.0 / 255.0, blue: 72.0 / 255.0)
    static let setupArtSparkleDark = Color(red: 247.0 / 255.0, green: 212.0 / 255.0, blue: 107.0 / 255.0)
    static let setupArtSparkleSoft = Color(red: 185.0 / 255.0, green: 214.0 / 255.0, blue: 242.0 / 255.0)
    static let setupArtSparkleSoftDark = Color(red: 62.0 / 255.0, green: 90.0 / 255.0, blue: 120.0 / 255.0)
}
