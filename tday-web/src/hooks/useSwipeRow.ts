import { useCallback, useRef, useState, type TouchEvent as ReactTouchEvent } from "react";
import { hapticReveal } from "@/lib/haptics";
import { usePrefersReducedMotion } from "@/lib/prefersReducedMotion";
import {
  AXIS_SLOP_PX,
  createSwipeSampler,
  projectedRest,
  rubberBand,
  SWIPE_SETTLE_HOME,
  SWIPE_SETTLE_INSTANT,
  SWIPE_SETTLE_OPEN,
  type SwipeSampler,
} from "@/lib/swipeGesture";

/** What one gesture has to remember about itself. */
type RowGesture = {
  /** Where the finger went down, in client coordinates. */
  x: number;
  y: number;
  /** Where the row was resting when this gesture began — 0, or -actionsWidth. */
  startX: number;
  /** Locked once the finger has moved far enough to say what it meant. */
  axis: "x" | "y" | null;
  /** The last client x this gesture saw, for a `touchend` that carries none. */
  lastX: number;
  /**
   * Where this gesture last put the row — the same number `swipeX` holds, kept
   * here because the release has to read it outside a state updater. React may
   * call a functional update twice, and an updater that buzzed would buzz twice.
   */
  offsetX: number;
  /**
   * Whether this open-cycle has already spent its reveal haptic, seeded from the
   * offset the gesture began at. See the detent paragraph on [useSwipeRow].
   */
  hasFiredReveal: boolean;
  /** This gesture's readings, and only this gesture's. */
  sampler: SwipeSampler;
};

/**
 * How much further a row can be pulled at a limit, as a fraction of the actions
 * behind it.
 *
 * An eighth — about 26 px at the 210 px all three rows use. The pager answers a
 * refused direction with half of its own threshold, and half here would be 52 px:
 * a whole action pill's worth of travel, which stops reading as a limit and
 * starts reading as a fourth action about to appear. A row's give has to be
 * legible and unmistakably smaller than the reveal it is not.
 */
const LIMIT_GIVE = 1 / 8;

/**
 * The signals a row draws that are not its swipe, on the rung they share.
 *
 * Both are the app acknowledging a jump the user just made somewhere else — a
 * deep link or a search result landing on this row — drawn as a ring below `sm`
 * (which Tailwind spells as a `box-shadow`) and as a tint above it. A transition
 * list is a whitelist, so naming one and not the other made the same arrival fade
 * on a desktop and cut in one frame on a phone. Two spellings of one signal that
 * land differently are two signals.
 */
const HIGHLIGHT_SETTLE =
  "background-color var(--tday-duration-quick) var(--tday-ease-standard), " +
  "box-shadow var(--tday-duration-quick) var(--tday-ease-standard)";

/**
 * Where the row actually sits for a finger that has pulled it to `x`.
 *
 * One to one between the two resting places, because every pixel in there is the
 * user uncovering an action and a row that lagged would be the app arguing with
 * a finger it should be following. Outside them the pull is spent against a
 * limit it approaches and never reaches — there is no fourth action to the left
 * and nothing at all to the right, and both of those are facts the surface can
 * state by giving rather than by stopping dead.
 */
function rowFollow(x: number, actionsWidth: number): number {
  const give = actionsWidth * LIMIT_GIVE;
  if (x > 0) return hundredths(rubberBand(x, give));
  if (x < -actionsWidth) return hundredths(-actionsWidth - rubberBand(-actionsWidth - x, give));
  return x;
}

/**
 * Whether a row let go of here, at this speed, ends up open.
 *
 * One function and three callers, on purpose. The release has always asked this;
 * the detent now asks the same question with the velocity term zeroed ("if the
 * finger lifted *right now*"), and the start of a gesture asks it of the offset
 * the row was already resting at. Written once so the buzz cannot come to
 * disagree with the outcome it is announcing — a detent that fires where the row
 * would not actually open is a lie the hand cannot argue with.
 *
 * Half of `actionsWidth` rather than the 0.32 of it the two natives commit at.
 * That divergence is real and predates this: web decides on a projected rest
 * against halfway, the natives on a position against 32%. Adopting their fraction
 * here to make three numbers match would buzz at 67 px on the 210 px row every
 * client uses, and then close on release at anything under 105 px — a reveal
 * announced on every slow drag that provably does not happen. The three clients
 * agree on the sentence and not on the pixel; reconciling the pixel is a felt
 * change to the commit itself and belongs to its own argument.
 */
function commitsOpen(position: number, velocity: number, actionsWidth: number): boolean {
  return projectedRest(position, velocity) < -actionsWidth / 2;
}

/**
 * Hundredths, because the resistance curve returns a full double and no screen
 * can spend the rest of it: what the extra digits buy is a transform string
 * three times as long, rewritten sixty times a second. The pager rounds its own
 * for the same reason.
 */
function hundredths(value: number): number {
  return Math.round(value * 100) / 100;
}

/**
 * The task row's swipe-to-reveal: Edit, Copy and Delete uncovered by dragging the
 * row aside, on every screen that lists tasks.
 *
 * One hook for three rows. The scheduled row, the calendar row and the Anytime
 * row had three copies of this gesture with the same 210 px of actions, the same
 * 8 px axis lock and the same arithmetic — and, being copies, three different
 * sets of bugs: only the calendar's had an exit for a touch the platform takes
 * away, and all three decided a release on where the finger stopped rather than
 * on where it was going.
 *
 * **A release is a projection, not a position.** `projectedRest` carries the row
 * on at the speed it was let go at and asks which resting place *that* lands
 * nearer. A flick of 30 px commits, because it was still travelling; a slow drag
 * to 120 px that was already easing back does not, because it was not. The old
 * rule — is the row past halfway right now — got both of those backwards, and
 * felt mechanical for exactly that reason: a finger's last pixel is the one piece
 * of a gesture that says least about it.
 *
 * **A cancelled gesture is an abandoned one, not a quiet commit.** `swiping` is
 * what switches the row's transition off so it can track the finger, so something
 * has to switch it back on; a `touchend` is not that something, because a touch
 * the platform claims (a scroll, an edge back-swipe, an incoming call, the tab
 * hiding) fires `touchcancel` and no `touchend` at all. Without this exit the row
 * stayed parked at whatever offset the finger reached with its transition still
 * off — frozen half-open, not slow — until the user happened to touch it again.
 * `cancelSwipe` clears `swiping` *first*, which is the load-bearing half: the
 * transition has to be back on before `swipeX` moves or the row teleports home
 * instead of gliding there. Then it puts the row back where this gesture found
 * it, rather than settling it to the nearer edge the way a real lift does — the
 * user never finished choosing.
 *
 * **The reveal buzzes once, at the moment the row is committed to opening.** The
 * ask was for a vibration when the row is "slid left to show the buttons behind",
 * and that is the detent under the finger — the actions catching under the thumb —
 * not the app reporting an animation after the hand has already gone. So the
 * first arm lives in `touchmove`, on the single update where [commitsOpen] flips
 * false to true at zero velocity. The second arm lives in the release, because
 * web folds velocity into one projection: a flick can commit from 30 px without
 * the position ever having crossed halfway, and an arm A on its own would make
 * the fastest, most deliberate swipe in the app the only silent one. Two arms,
 * one event, and whichever of them arrives first is the only one that fires.
 *
 * The once is a flag on the gesture and not a second threshold under the first.
 * A hysteresis band exists to stop a single comparison strobing on its own
 * boundary; a flag that cannot re-arm inside a gesture *at all* is strictly
 * stronger — a finger parked exactly on the mark cannot repeat, and crossing,
 * coming back and crossing again cannot fire twice — and it costs no number
 * nobody can justify. It re-arms for free at the next `touchstart`, seeded from
 * the offset the row is resting at, which is why no close path has to remember
 * it: every close, including the one another row forces on this one, leaves
 * `swipeX` at 0. Closing itself buzzes nothing. Every action pill fires its own
 * haptic and then shuts the row, so a close buzz would double each of them, and
 * a row shut from under a finger that is nowhere near it has not been closed by
 * anybody.
 *
 * The one honest cost, said here rather than left for a device to find: cross the
 * detent, drag back, release closed, and you have felt a reveal that did not
 * happen. That is what a detent on a physical control does, and the alternative —
 * silence until the row settles — costs the feature its point.
 *
 * `reduceMotion` is read two lines from the buzz and is deliberately not consulted
 * by it. Reduced motion removes the trip and keeps the destination; the row still
 * opens, `SWIPE_SETTLE_INSTANT` is literally `transform 0s`, and there is still a
 * reveal to report. Gating feedback on a motion preference would silently delete
 * this for exactly the people most reliant on something other than an animation to
 * tell them what happened. The preference that *does* gate it is the app's haptic
 * switch, read inside `vibrate` at the one chokepoint in `lib/haptics`, which is
 * why there is no gate at this call site and must not be one.
 *
 * No pointer capture here, unlike the pager. Touch events are implicitly captured
 * by the spec — every `touchmove`, `touchend` and `touchcancel` goes to the
 * element the `touchstart` hit, whether or not the finger is still over it — so a
 * row never loses the end of its own gesture the way a pointer-driven one can.
 * `pointercancel` is wired to the same reset because the two event families report
 * the same interruption and a device may deliver either; the reset is idempotent,
 * so receiving both costs nothing.
 *
 * @param actionsWidth - How wide the actions behind the row are, which is both
 *   the open resting place and the scale this gesture is measured in.
 * @param onOpen - Called when the gesture locks horizontal, so the screen can
 *   close whichever other row was open. Fired at the lock rather than at the
 *   commit: one row is claimed by the finger, not by the outcome.
 * @param disabled - Whether the row will take a swipe at all. A row being picked
 *   in a multi-select, or one rendered read-only, has no actions to uncover.
 * @returns The row's offset, the `transition` its style should carry, a way to
 *   shut it, and the handlers.
 */
export function useSwipeRow({
  actionsWidth,
  onOpen,
  disabled = false,
}: {
  actionsWidth: number;
  onOpen: () => void;
  disabled?: boolean;
}) {
  const [swipeX, setSwipeX] = useState(0);
  const [swiping, setSwiping] = useState(false);
  const gestureRef = useRef<RowGesture | null>(null);
  // Subscribed rather than read once: this decides what the row renders, so it
  // has to follow a preference that flips mid-session.
  const reduceMotion = usePrefersReducedMotion();

  const closeSwipe = useCallback(() => setSwipeX(0), []);

  const onTouchStart = useCallback(
    (event: ReactTouchEvent) => {
      if (disabled) return;
      const touch = event.touches[0];
      if (!touch) return;
      const sampler = createSwipeSampler();
      sampler.sample(touch.clientX, event.timeStamp);
      gestureRef.current = {
        x: touch.clientX,
        y: touch.clientY,
        startX: swipeX,
        axis: null,
        lastX: touch.clientX,
        offsetX: swipeX,
        // Already committed open, so this gesture owes nothing: dragging an open
        // row further open, or part of the way back and out again, is not a
        // reveal. A row at rest closed begins here at 0 and is therefore armed,
        // which is the whole of the re-arm — no close path has to know about it.
        hasFiredReveal: commitsOpen(swipeX, 0, actionsWidth),
        sampler,
      };
      setSwiping(true);
    },
    [actionsWidth, disabled, swipeX],
  );

  const onTouchMove = useCallback(
    (event: ReactTouchEvent) => {
      const gesture = gestureRef.current;
      if (!gesture) return;
      const touch = event.touches[0];
      if (!touch) return;
      const dx = touch.clientX - gesture.x;
      const dy = touch.clientY - gesture.y;
      if (gesture.axis === null) {
        if (Math.abs(dx) <= AXIS_SLOP_PX && Math.abs(dy) <= AXIS_SLOP_PX) return;
        gesture.axis = Math.abs(dx) > Math.abs(dy) ? "x" : "y";
        if (gesture.axis === "x") onOpen();
      }
      // A vertical drag belongs to the feed's scroller. The gesture is kept open
      // rather than ended so the lift that follows is still recognisably this
      // one, and is refused a commit of its own.
      if (gesture.axis === "y") return;
      gesture.lastX = touch.clientX;
      gesture.sampler.sample(touch.clientX, event.timeStamp);
      const next = rowFollow(gesture.startX + dx, actionsWidth);
      // The detent, measured after the limit give rather than before it: what the
      // release will decide on is where the row actually is, so this has to ask
      // about the same number. Zero velocity because the question is what happens
      // if the finger stops existing on this frame — feeling this means "let go
      // now and the actions stay".
      if (!gesture.hasFiredReveal && commitsOpen(next, 0, actionsWidth)) {
        gesture.hasFiredReveal = true;
        hapticReveal();
      }
      gesture.offsetX = next;
      setSwipeX(next);
    },
    [actionsWidth, onOpen],
  );

  const onTouchEnd = useCallback(
    (event: ReactTouchEvent) => {
      const gesture = gestureRef.current;
      gestureRef.current = null;
      setSwiping(false);
      if (gesture?.axis !== "x") return;
      // `changedTouches` is where a lift reports the finger it lifted; a synthetic
      // end that carries none is measured against the last move instead, which is
      // the same reading one frame earlier.
      const lift = event.changedTouches[0];
      const velocity = gesture.sampler.release(lift?.clientX ?? gesture.lastX, event.timeStamp);
      // The second arm: this release opens the row and the detent never came
      // round, which is every flick short of halfway. Asked of the offset the
      // gesture recorded rather than of the one below, because the decision is
      // made once and a state updater is not promised to be.
      if (!gesture.hasFiredReveal && commitsOpen(gesture.offsetX, velocity, actionsWidth)) {
        hapticReveal();
      }
      setSwipeX((current) => (commitsOpen(current, velocity, actionsWidth) ? -actionsWidth : 0));
    },
    [actionsWidth],
  );

  const cancelSwipe = useCallback(() => {
    const gesture = gestureRef.current;
    gestureRef.current = null;
    // Order matters: `swiping` false restores the row's transition, so the return
    // below is animated rather than a jump cut.
    setSwiping(false);
    if (!gesture) return;
    setSwipeX(gesture.startX);
  }, []);

  /**
   * The row's whole `transition` whitelist, chosen by where the gesture left it.
   *
   * The settle is read off the offset the release just committed to, which is the
   * only place it can be read from: at this point `swipeX` *is* the destination,
   * so 0 means a row going home and anything else means one finishing the trip
   * the finger paid for.
   */
  const settle = reduceMotion
    ? SWIPE_SETTLE_INSTANT
    : swipeX === 0
      ? SWIPE_SETTLE_HOME
      : SWIPE_SETTLE_OPEN;
  const transition = swiping ? "none" : `${settle}, ${HIGHLIGHT_SETTLE}`;

  return {
    swipeX,
    swiping,
    transition,
    closeSwipe,
    swipeHandlers: {
      onTouchStart,
      onTouchMove,
      onTouchEnd,
      onTouchCancel: cancelSwipe,
      onPointerCancel: cancelSwipe,
    },
  };
}
