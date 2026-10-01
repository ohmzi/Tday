import SwiftUI

/// The zoom that joins a home tile to the screen it opens.
///
/// iOS 18 ships a navigation transition that grows the pushed screen out of the
/// view the user actually pressed (`.navigationTransition(.zoom(sourceID:in:))`,
/// paired with `.matchedTransitionSource(id:in:)` on the source). It is the one
/// place in this app where the system already knows the answer the vocabulary
/// keeps arriving at by hand: a surface that came from somewhere should be seen
/// coming from there. The surfaces in the tree that qualify are the large, distinct
/// rectangles on the two home feeds whose destination fills the screen with the same
/// list they were counting: the six category tiles and the Today card on the
/// scheduled board, that board's custom list rows, and the Anytime feed's list cards
/// and Completed entry.
///
/// Three things make this file necessary rather than two modifiers written at the
/// call sites.
///
/// The first is the deployment target. It is iOS 17.0 and is not moving, so both
/// APIs have to sit behind `#available(iOS 18.0, *)` and iOS 17 has to come out
/// the far side with the stock push and no trace of the branch.
///
/// The second is distance. The tiles are built inside private structs in
/// `ScheduledTaskHomeScreen.swift` and `TodoListScreen.swift`; the destinations are
/// built by `AppRootView.destinationView(for:)` two files away, behind a single
/// `.navigationDestination(for: AppRoute.self)`. A `Namespace.ID` cannot be
/// threaded between those without a parameter chain through several private types,
/// so it travels in the environment — the same call `TdayMotionEnvironment.swift`
/// made for the motion gate, for the same reason.
///
/// The third is that the two halves have to agree on an id, and the only thing
/// both ends hold is the `AppRoute`. That is what `zoomSourceID` below is: one
/// table, read by the tile that pushes and by the destination that arrives, so a
/// tile re-pointed at a different route cannot end up zooming out of a rectangle
/// it never came from.
extension AppRoute {

    /// The shared-element id this route zooms out of, or `nil` where nothing the
    /// user pressed is on screen to zoom out of.
    ///
    /// Exhaustive rather than a `default:`, and that is the point of writing it out:
    /// a renamed or removed case has to be answered here, at compile time, instead
    /// of quietly dropping back to the stock push in a TestFlight build. `deepLinkPath`
    /// already mints a stable string per route and could have been reused, but it is
    /// a URL contract with its own reasons to change, and an id that moves when a deep
    /// link is re-spelled is a broken transition nobody would think to look for.
    ///
    /// Two kinds of argument reach this table, and they say the same thing.
    ///
    /// `.allTodos` is the one whose argument was already there for another purpose: a
    /// highlight id means the arrival came from the home screen's own search results or
    /// from a deep link — not from the All tile — and the All tile is on screen either
    /// way. Zooming out of it would be the animation claiming the user pressed something
    /// they did not press, which is worse than no animation: a transition's whole job is
    /// to say where a screen came from.
    ///
    /// `todayTodos`, `listTodos`, `floaterListTodos` and `completed` say the same thing
    /// through an explicit `HomeTileOrigin?`, added for it. A non-nil origin says the
    /// arrival was that feed's own tile, so the id is the one the tile publishes; `nil`
    /// says it came from the sidebar, the search results or a deep link — none of which
    /// has a rectangle to grow out of — so the answer is `nil` and the push is the stock
    /// slide. `completed` is the one with two tiles rather than one, and the two ids
    /// differ by origin for the reason `HomeTileOrigin` gives: both root feeds are
    /// mounted together during a tab crossfade, so the Scheduled board's Completed tile
    /// and the Anytime feed's cannot share an id.
    var zoomSourceID: String? {
        switch self {
        case .scheduledTodos:
            return "home-tile.scheduled"
        case .priorityTodos:
            return "home-tile.priority"
        case .overdueTodos:
            return "home-tile.overdue"
        case let .allTodos(highlightTodoId):
            return highlightTodoId == nil ? "home-tile.all" : nil
        case let .completed(origin):
            guard let origin else { return nil }
            switch origin {
            case .scheduledBoard:
                return "home-tile.completed"
            case .floaterFeed:
                return "floater-tile.completed"
            }
        case .calendar:
            return "home-tile.calendar"
        case let .todayTodos(origin):
            return origin == nil ? nil : "home-tile.today"
        case let .listTodos(listId, _, origin):
            return origin == nil ? nil : "home-tile.list.\(listId)"
        case let .floaterListTodos(listId, _, origin):
            return origin == nil ? nil : "floater-tile.list.\(listId)"
        case .scheduledTaskHome,
             .createTodayTodo,
             .createFloaterTodo,
             .floaterTaskHome,
             .settings,
             .latestRelease,
             .helpGuide,
             .morningSweep,
             .forgotPassword:
            return nil
        }
    }
}

/// Which home feed's tile an arrival was pressed on.
///
/// Four routes are reachable both from a tile that has a rectangle and from a surface
/// that does not — the sidebar, the home screen's search results, a deep link. The route
/// is the only thing both ends of the zoom hold, so the difference has to travel on it:
/// an `AppRoute` built by a tile carries the tile's origin, and every other construction
/// site passes `nil`. `zoomSourceID` reads it and answers `nil` for the arrivals with
/// nothing to zoom out of.
///
/// `completed` is the case that makes the two values distinct rather than merely
/// informative: the Scheduled board and the Anytime feed each have a Completed tile, and
/// `AppRootView` draws both root feeds through one `ZStack` — during a tab crossfade both
/// trees are mounted, so a single id shared by the two tiles would be two views publishing
/// one id in one namespace, which is a match the user did not ask for. The origin splits
/// it into "home-tile.completed" and "floater-tile.completed".
enum HomeTileOrigin: Hashable {
    case scheduledBoard
    case floaterFeed
}

/// The namespace the two halves of the zoom are matched in.
///
/// `Namespace.ID` has no public initialiser, so there is no value to default this
/// to and the absence has to be spelled as `nil` — which is also the honest answer
/// for any subtree drawn outside the app root. `CalendarPagingScrollView` hosts its
/// month pages in hand-made `UIHostingController`s, and a surface that escapes the
/// provider that way must get the stock push rather than a namespace from nowhere.
private struct TdayZoomNamespaceKey: EnvironmentKey {
    static let defaultValue: Namespace.ID? = nil
}

extension EnvironmentValues {

    /// The `@Namespace` `AppRootView` owns, for the tiles and destinations below it.
    var tdayZoomNamespace: Namespace.ID? {
        get { self[TdayZoomNamespaceKey.self] }
        set { self[TdayZoomNamespaceKey.self] = newValue }
    }
}

/// The four conditions both halves share, and why each one is load-bearing.
///
/// `#available` is the deployment target: neither API exists on iOS 17, so this is
/// the only one of the four that is a compile requirement rather than a judgement.
///
/// The namespace and the id are the pairing itself. A `.matchedTransitionSource`
/// with no destination naming the same id is inert, and a `.navigationTransition`
/// whose source is not on screen falls back to the stock push — so either half
/// alone is harmless, and requiring both to resolve the same way is what keeps the
/// two ends from drifting into a half-wired transition that reads correctly in both
/// files.
///
/// The gate is `docs/motion.md`'s fifth idiom rule. A zoom is a large-amplitude
/// travel — a tile growing to fill the screen — which is exactly the class Apple's
/// own guidance names, and under Reduce Motion the substitute is the platform's
/// own: the stock push, which still puts the finished screen in front of the user
/// and adds no wait. Nothing here reads `accessibilityReduceMotion`; the answer
/// comes from the one resolver, as it does everywhere else.
private struct TdayZoomSourceModifier: ViewModifier {
    let route: AppRoute

    @Environment(\.tdayZoomNamespace) private var zoomNamespace
    @Environment(\.tdayAnimation) private var tdayAnimation

    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(iOS 18.0, *) {
            if let zoomNamespace, let sourceID = route.zoomSourceID, tdayAnimation.isEnabled {
                // The source's SHAPE, which the transition engine does not read off the
                // view it is attached to. Called with no configuration the engine clips
                // the travelling source to a plain rectangle, so a tile whose fill is
                // drawn with 26pt corners leaves its corner square on the first frame and
                // stays square all the way out — and all the way back, on the interactive
                // dismiss — while the fill underneath is round. That is the reported
                // defect, and this closure is the documented place to answer it: the
                // corner the source travels with is the corner the fill is drawn with,
                // read from the one rung both of them name.
                //
                // Nothing else about the transition moves. The closure sits INSIDE the
                // existing availability branch and the existing gate, so it adds no third
                // `if #available(iOS 18.0, *)` block and no second condition, and not one
                // call site is touched: the five surfaces keep `.tdayZoomSource(route)`
                // immediately after their own button style, which is what
                // `tests/guardrails/launch-handover.test.ts` asserts. The fix is written
                // here, on the modifier, precisely so that chain stays intact.
                //
                // The DESTINATION is still a full screen, so the surface squares off as it
                // lands. That is by construction — `matchedTransitionSource` carries no
                // destination shape, and this closure has nowhere to put a moving one: it
                // configures a static source clip, so whatever corner is named here is the
                // corner the source travels with for the whole push. Android, which owns
                // both ends, can do better and does: its `TdayTileCornerClip` re-derives
                // the radius on every draw, 26dp at the tile and 0 at the screen. This is
                // deliberately not part of the fix, and whether the corner snaps square as
                // the transition lands is on the device row rather than asserted here.
                //
                // (Android's row calls a radius HELD over the full screen and then popped
                // square the bug its rebuild removed, which is why that side animates it.
                // This side cannot, and does not claim to.)
                content.matchedTransitionSource(id: sourceID, in: zoomNamespace) { source in
                    source.clipShape(
                        RoundedRectangle(cornerRadius: TdayRadius.card, style: .continuous)
                    )
                }
            } else {
                content
            }
        } else {
            content
        }
    }
}

/// The arriving half. Applied once, over every route, at `AppRootView`'s single
/// `.navigationDestination` — routes with no source id fall through to the stock
/// push here rather than needing a list of their own.
private struct TdayZoomDestinationModifier: ViewModifier {
    let route: AppRoute

    @Environment(\.tdayZoomNamespace) private var zoomNamespace
    @Environment(\.tdayAnimation) private var tdayAnimation

    /// The alpha the arriving screen starts at, and why it is not zero.
    ///
    /// Measured on iOS 18.6 rather than chosen: starting at `0` does NOT give a
    /// see-through rect. UIKit composites the growing destination over an opaque
    /// backing for the first part of the flight, so a fully transparent screen reads as
    /// a blank WHITE card growing out of the tile — which is worse than the opaque
    /// screen it replaced, because at least that one had content in it. A partial alpha
    /// composites the way the request assumes: at this value the pressed tile, the
    /// neighbouring tiles and the dock all read through the arriving screen.
    ///
    /// Not a motion token. It is an opacity, not a duration, delay, easing or spring, so
    /// it is outside the vocabulary `MotionTokens.kt` carries — see `docs/motion.md`.
    private enum TdayZoomArrival {
        static let startingOpacity: Double = 0.35
    }

    /// False for the screen's first frame, flipped once on appear.
    ///
    /// A CLOCK, BECAUSE THERE IS NOTHING ELSE TO READ. Nothing in this view can see the
    /// push's progress: UIKit drives it on the hosting view's layer, SwiftUI reports the
    /// destination at its SETTLED position for the whole animation, and the pushed view is
    /// clipped to its own bounds mid-flight — so `GeometryReader`, `visualEffect` and
    /// preferences are all equally blind, and there is no transaction for a `.transition`
    /// to ride. `onAppear` therefore fires at layout rather than at the transition's first
    /// frame, which is why the wait below is short: it has to be over before the rect is
    /// large, or the hold stops showing the tile through the arriving screen and starts
    /// showing the whole home feed through it — the dock and the create button included.
    @State private var arrived = false

    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(iOS 18.0, *) {
            if let zoomNamespace, let sourceID = route.zoomSourceID, tdayAnimation.isEnabled {
                // The zoom is a transition, not a dismissal, and `navigationZoomDismissRefusal()`
                // below is the whole of that distinction. Setting `preferredTransition = .zoom(…)` — which
                // is what this modifier lowers to — also makes UIKit install a
                // `_UIDismissInteraction` on the pushed controller, and that interaction ships
                // three ways out: a downward content swipe, a pinch, and a leading-edge pan.
                // Only the third is a back gesture anyone asked for. The other two close a list
                // screen on a gesture the rest of this app spends on scrolling, and the
                // reported defect is exactly that.
                //
                // The refusal sits INSIDE this branch on purpose. It cannot be reached on
                // iOS 17, where there is no zoom and no interaction to refuse, and it cannot
                // be reached under Reduce Motion, where `tdayAnimation.isEnabled` is false and
                // `preferredTransition` is never set — so it needs no availability or gate of
                // its own, and it adds no second `#available` block for
                // `tests/guardrails/launch-handover.test.ts` to count.
                //
                // It is applied BEFORE `.navigationTransition` so the configurator is part of
                // the content the transition is attached to, and so this call site keeps the
                // one-line-after-the-branch shape the corner fix on the source half assumes.
                content
                    .opacity(arrived ? 1 : TdayZoomArrival.startingOpacity)
                    .animation(
                        tdayAnimation(
                            TdayMotion.enter(duration: TdayMotion.Durations.quick)
                                // How long the tile keeps the rect to itself before the screen
                                // starts arriving in it. Not a token — see `docs/motion.md`: it
                                // is a wait, so `Delays` is where it would live, and it is
                                // spelled as `Durations.quick` here only until the shape is
                                // settled and the rung can be promoted with Android's twin.
                                .delay(TdayMotion.Durations.quick)
                        ),
                        value: arrived
                    )
                    .navigationZoomDismissRefusal()
                    .navigationTransition(.zoom(sourceID: sourceID, in: zoomNamespace))
                    .onAppear { arrived = true }
            } else {
                content
            }
        } else {
            content
        }
    }
}

extension View {

    /// Marks this view as the thing `route` grows out of.
    func tdayZoomSource(_ route: AppRoute) -> some View {
        modifier(TdayZoomSourceModifier(route: route))
    }

    /// Marks this view as the screen `route` pushed, to be grown from its source.
    func tdayZoomDestination(_ route: AppRoute) -> some View {
        modifier(TdayZoomDestinationModifier(route: route))
    }
}
