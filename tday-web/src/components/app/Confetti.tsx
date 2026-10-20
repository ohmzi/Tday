import { useEffect, useRef, useState } from "react";
import { prefersReducedMotion } from "@/lib/prefersReducedMotion";
import { DURATION_MS, EASE_CURVE, easingSampler } from "@/lib/motion";
import {
  envelopedAlpha,
  fan,
  frame,
  FLIGHT_MS,
  ORIGIN_X,
  ORIGIN_Y,
} from "@/components/app/confetti-kinematics";

/**
 * The burst that plays when the user ticks off the last thing they had left.
 *
 * Deliberately not a package and not a sprite: a few dozen rounded rectangles on one
 * canvas is the whole effect. This file draws them and owns nothing else — where a
 * piece is, how far round it has turned and how solid it still is all come from
 * `confetti-kinematics.ts`, which is the half that can be tested.
 *
 * What the throw actually does, because the shape of it is what reads as paper: the
 * outward travel is limited by drag, so each piece has a finite reach rather than
 * sliding off the sides; the fall settles to that piece's own terminal speed instead
 * of accelerating forever, which is why the cloud spreads out as it comes down; and
 * the edge-on flip runs on its own axis at its own rate, unrelated to the in-plane
 * spin, so nothing is a propeller and a coin at the same time.
 *
 * The twin of the Compose `TdayConfetti` and the iOS `TdayConfetti` view; the three
 * share piece count, fan, timing and palette so finishing a list feels the same
 * wherever the user does it. `docs/confetti-spec.md` is what holds them together.
 *
 * Sits in an absolutely positioned layer over its parent, bled above it so the apex
 * is not cut: a canvas clips to its own bitmap, which the Compose `Box` and the iOS
 * overlay do not.
 *
 * @param accentColor the screen's own accent, mixed into the palette so the
 *   celebration still belongs to the list it happened on. Arrives as anything
 *   CSS accepts (a hex, or an `hsl(var(--x))`), so it is handed to the canvas as
 *   a fill string rather than parsed.
 * @param startDelayMs how long the burst is held back after it is mounted. Zero
 *   where this plays over a page that is standing still; a feed that draws the
 *   empty state inline hands over the time its own rows take to reach their new
 *   slots, so the paper is never thrown across a screen that is still sliding.
 *   Held here rather than by mounting the canvas late: the pieces are rolled and
 *   the canvas is sized while the feed travels, so the first frame of the burst
 *   is a frame of confetti rather than a frame of layout.
 * @param play whether the celebration is still true. Flipping it to `false`
 *   mid-flight does NOT cut the burst: the pieces keep travelling on their own
 *   clock while a second alpha term — the envelope, `Quick` on the `Exit` curve —
 *   takes the paint away under them, and the loop stops when that reaches zero.
 *   Cutting forty-six pieces out of mid-air to cancel them is the same complaint
 *   as leaving them flying over a restored row, one layer down. The host is what
 *   actually unmounts this, and has to stay in the tree for at least the envelope
 *   (`EmptyState` holds it there with `useFadeUnmount`); under reduced motion
 *   nothing was ever painted, so it goes on the cancel frame instead.
 */
export default function Confetti({
  accentColor,
  startDelayMs = 0,
  play = true,
}: {
  accentColor: string;
  startDelayMs?: number;
  play?: boolean;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  // WHEN the celebration stopped being true, or null while it holds. A ref and
  // not state: the draw loop below reads it every frame and must not be
  // restarted to see it — `play` is deliberately absent from that effect's
  // dependencies, because re-running it re-rolls `fan()` and re-stamps `start`,
  // which teleports every piece to the beginning of a flight it is halfway
  // through. The whole point is that the burst keeps flying while it fades.
  const cancelledAtRef = useRef<number | null>(null);
  // Which run the pieces below belong to. Bumped only when a NEW celebration
  // starts while the last one is still fading — the host keeps this mounted for
  // the length of the envelope, so an undo immediately followed by another
  // completion would otherwise adopt a flight that is already half spent and
  // throw no paper at all. Never keyed on `play` itself, which is the thing the
  // envelope exists to survive. The same latch Android's `TdayConfetti` keeps.
  const [runGeneration, setRunGeneration] = useState(0);
  const playingRef = useRef(play);

  useEffect(() => {
    const wasPlaying = playingRef.current;
    playingRef.current = play;
    if (!play) {
      // `performance.now()`, the same clock `start` below is stamped from, so
      // the envelope and the flight are measured against one timeline.
      cancelledAtRef.current = performance.now();
      return;
    }
    cancelledAtRef.current = null;
    if (!wasPlaying) setRunGeneration((generation) => generation + 1);
  }, [play]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    // The burst is the whole effect — there is no finished state to pin, so
    // reduced motion means never starting rather than jumping to the end.
    if (prefersReducedMotion()) return;

    const context = canvas.getContext("2d");
    if (!context) return;

    const palette = [...PALETTE, accentColor];
    const pieces = fan();
    const start = performance.now() + startDelayMs;
    let rafHandle = 0;

    const resize = () => {
      const ratio = window.devicePixelRatio || 1;
      const { width, height } = canvas.getBoundingClientRect();
      canvas.width = Math.round(width * ratio);
      canvas.height = Math.round(height * ratio);
      context.setTransform(ratio, 0, 0, ratio, 0, 0);
      return { width, height };
    };

    let box = resize();
    const observer = new ResizeObserver(() => {
      box = resize();
    });
    observer.observe(canvas);

    const draw = (now: number) => {
      const t = (now - start) / FLIGHT_MS;
      // Still waiting for the feed to settle. Nothing is cleared because nothing
      // has been drawn yet, and the canvas is transparent until it has.
      if (t < 0) {
        rafHandle = requestAnimationFrame(draw);
        return;
      }
      context.clearRect(0, 0, box.width, box.height);
      // The envelope: 1 while the celebration holds, and once it does not, a
      // `Quick` slide to nothing on the `Exit` curve — paint leaving, and nobody
      // is meant to watch it go. It multiplies the piece's own fade rather than
      // replacing it (`envelopedAlpha`), and touches the flight clock above not
      // at all.
      const cancelledAt = cancelledAtRef.current;
      const envelope =
        cancelledAt === null
          ? 1
          : 1 - exitEase(Math.min(1, (now - cancelledAt) / DURATION_MS.quick));
      // Flight over, or the envelope spent: either way the canvas has just been
      // cleared and there is nothing left to ask for a frame for. The host takes
      // the element away on its own clock, which is the same rung.
      if (t >= 1 || envelope <= 0) return;

      // Everything is thrown in fractions of the box's WIDTH — not of its
      // longest side, which on a narrow screen is the height and throws every
      // piece clean off the sides before it can be seen.
      const span = box.width;
      const originX = box.width * ORIGIN_X;
      // The bleed is added back in so the origin lands on the same pixel of the
      // *container* it did before the canvas grew upwards. Without this term the
      // whole burst would be thrown forty pixels high, which on the shortest
      // viewport is a fifth of the illustration.
      const originY =
        (box.height - WEB_CANVAS_TOP_BLEED) * ORIGIN_Y + WEB_CANVAS_TOP_BLEED;

      for (const piece of pieces) {
        const state = frame(piece, t);
        // Not launched yet, or already landed: one salvo of forty-six pieces
        // leaving together reads as an expanding ring rather than as confetti.
        if (!state) continue;

        const width = state.widthScale * piece.width;

        context.save();
        context.globalAlpha = envelopedAlpha(state.alpha, envelope);
        context.translate(originX + state.dx * span, originY + state.dy * span);
        context.rotate(state.rot);
        context.fillStyle = palette[piece.colorIndex % palette.length];
        context.beginPath();
        // Radius off the DRAWN width, so a piece turning edge-on stays a sliver
        // with rounded ends rather than becoming a capsule.
        context.roundRect(-width / 2, -piece.height / 2, width, piece.height, width * 0.4);
        context.fill();
        context.restore();
      }

      rafHandle = requestAnimationFrame(draw);
    };

    rafHandle = requestAnimationFrame(draw);

    return () => {
      cancelAnimationFrame(rafHandle);
      observer.disconnect();
    };
  }, [accentColor, startDelayMs, runGeneration]);

  return (
    <canvas
      ref={canvasRef}
      aria-hidden
      className="pointer-events-none absolute -top-10 bottom-0 left-0 right-0 w-full"
    />
  );
}

/**
 * The cancel envelope's curve, folded once at module scope rather than per frame.
 *
 * `EASE_CURVE.exit` and not `EASE.exit`: the string form is for a style property, and
 * there is no element here — the envelope is multiplied into a `globalAlpha` inside a
 * canvas draw call. Same token either way, read from the same generated table.
 */
const exitEase = easingSampler(EASE_CURVE.exit);

/**
 * How far the canvas reaches above its container, in CSS pixels — the `-top-10` on
 * the element below, in a number the origin can be computed against.
 *
 * A `<canvas>` clips to its own bitmap, which is the one way this client differs from
 * the other two: Compose draws into a `Box` and iOS into an overlay, neither of which
 * cuts anything. The container is `min-h-[42vh]`, so on a short viewport the apex —
 * 0.195 of the WIDTH above the origin — lands outside the element at full opacity and
 * the topmost pieces are sliced off mid-flight rather than fading out.
 */
const WEB_CANVAS_TOP_BLEED = 40;

/**
 * A festive subset of the list palette rather than a new set of colours, so the
 * burst is made of shades the app already uses.
 */
const PALETTE = [
  "#E05299", // PINK
  "#E8A530", // GOLD
  "#3C9ADD", // DEEP_BLUE
  "#2EB8AC", // TEAL
  "#46B963", // LIME
  "#7D67B6", // PURPLE
  "#E6664C", // CORAL
];
