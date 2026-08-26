import { useEffect, useRef } from "react";

/**
 * The burst that plays when the user ticks off the last thing they had left.
 *
 * Deliberately not a package and not a sprite: a few dozen rounded rectangles on
 * one canvas, thrown from a single point and pulled back down, is the whole
 * effect. Pieces flip as they fly — the width is scaled by the cosine of their
 * own spin — which is what reads as paper rather than as coloured dots.
 *
 * The twin of the Compose `TdayConfetti` and the iOS `TdayConfetti` view; the
 * three share piece count, fan, timing and palette so finishing a list feels the
 * same wherever the user does it.
 *
 * Sits in an absolutely positioned, non-clipping layer over its parent so the
 * pieces can cross the copy under the illustration.
 *
 * @param accentColor the screen's own accent, mixed into the palette so the
 *   celebration still belongs to the list it happened on. Arrives as anything
 *   CSS accepts (a hex, or an `hsl(var(--x))`), so it is handed to the canvas as
 *   a fill string rather than parsed.
 */
export default function Confetti({ accentColor }: { accentColor: string }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

    const context = canvas.getContext("2d");
    if (!context) return;

    const palette = [...PALETTE, accentColor];
    const pieces = fan();
    const start = performance.now();
    let frame = 0;

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
      context.clearRect(0, 0, box.width, box.height);
      if (t >= 1) return;

      // Everything is thrown in fractions of the box's WIDTH — not of its
      // longest side, which on a narrow screen is the height and throws every
      // piece clean off the sides before it can be seen.
      const span = box.width;
      const originX = box.width * ORIGIN_X;
      const originY = box.height * ORIGIN_Y;

      for (const piece of pieces) {
        // Staggered launches: one salvo of forty pieces reads as a single
        // expanding ring rather than as confetti.
        const local = (t - piece.delay) / (1 - piece.delay);
        if (local <= 0) continue;

        const travelled = piece.speed * local;
        const x = originX + Math.cos(piece.angle) * travelled * span;
        const y =
          originY +
          Math.sin(piece.angle) * travelled * span +
          GRAVITY * local * local * span;

        // Full opacity for the first half of the flight, then out — pieces that
        // vanish at the apex look like a dropped frame.
        const alpha =
          local < FADE_START ? 1 : 1 - (local - FADE_START) / (1 - FADE_START);

        const spin = piece.spinPhase + piece.spin * local;
        // |cos| of the spin is the piece turning edge-on to the viewer; the
        // floor keeps it from disappearing completely on the way round.
        const flip = MIN_FLIP + (1 - MIN_FLIP) * Math.abs(Math.cos(spin));
        const width = piece.width * flip;
        const height = piece.height;

        context.save();
        context.globalAlpha = alpha;
        context.translate(x, y);
        context.rotate(spin);
        context.fillStyle = palette[piece.colorIndex % palette.length];
        context.beginPath();
        context.roundRect(-width / 2, -height / 2, width, height, width * 0.4);
        context.fill();
        context.restore();
      }

      frame = requestAnimationFrame(draw);
    };

    frame = requestAnimationFrame(draw);

    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
    };
  }, [accentColor]);

  return (
    <canvas
      ref={canvasRef}
      aria-hidden
      className="pointer-events-none absolute inset-0 h-full w-full"
    />
  );
}

type Piece = {
  angle: number;
  speed: number;
  spin: number;
  spinPhase: number;
  width: number;
  height: number;
  colorIndex: number;
  delay: number;
};

/**
 * The fan, rolled from a fixed seed: the burst is the same every time, which is
 * what makes it read as a designed celebration rather than a random one.
 */
function fan(): Piece[] {
  const random = seeded(0x7da9102b);
  return Array.from({ length: PIECE_COUNT }, (_, index) => {
    // Fanned up and out rather than in a full circle. A ring throws half its
    // pieces straight down through the copy, where they read as a glitch.
    const step = (index + random()) / PIECE_COUNT;
    return {
      angle: FAN_START + FAN_SWEEP * step,
      speed: 0.3 + random() * 0.48,
      spin: (random() < 0.5 ? 1 : -1) * (3.5 + random() * 9),
      spinPhase: random() * Math.PI * 2,
      width: 5 + random() * 4,
      height: 8 + random() * 5,
      colorIndex: Math.floor(random() * (PALETTE.length + 1)),
      delay: random() * 0.16,
    };
  });
}

/** Mulberry32: three lines, and the same fan on every machine. */
function seeded(seed: number) {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const PIECE_COUNT = 46;
const FLIGHT_MS = 1800;

/** Where the burst is thrown from, as a fraction of the box: the scene's heart. */
const ORIGIN_X = 0.5;
const ORIGIN_Y = 0.28;

/** Up and out: 200°..340°, measured with y growing downward. */
const FAN_START = (200 * Math.PI) / 180;
const FAN_SWEEP = (140 * Math.PI) / 180;

const GRAVITY = 0.95;
const MIN_FLIP = 0.25;
const FADE_START = 0.55;

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
