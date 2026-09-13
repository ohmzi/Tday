import { useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { cn } from "@/lib/utils";

/**
 * A box that grows and shrinks to whatever height its content currently needs,
 * instead of the content's new size arriving in one frame.
 *
 * Height is the one dimension the stylesheet cannot do on its own here. The
 * natural height of a changing body is `auto`, `auto` is not an animatable
 * value, and there is no selector that can name a number nobody has measured —
 * so this measures the content and writes the pixel height the transition
 * actually runs on. The measurement is a `ResizeObserver` rather than a read
 * per render because every way the body can change size has to move the box:
 * a swapped child, a line that wraps, a font that finally loads. Only the
 * observer sees all three.
 *
 * `Emphasis`, by the second idiom rule in `docs/motion.md`: the box changes
 * size, and geometry rather than importance is what picks that rung.
 */
export default function AnimatedHeight({
  children,
  className,
}: {
  children: ReactNode;
  /** Applied to the animated box, which is the element in the layout. */
  className?: string;
}) {
  const content = useRef<HTMLDivElement>(null);
  const [height, setHeight] = useState<number | undefined>(undefined);

  useLayoutEffect(() => {
    const el = content.current;
    if (!el) return;
    // No observer, no measurement — the box stays `auto`, draws the right
    // height and simply does not animate. Taking one measurement and then
    // never updating it would be worse than not animating at all: the box
    // clips (`overflow-hidden`) the next thing the content does.
    if (typeof ResizeObserver === "undefined") return;

    const measure = () => setHeight(el.scrollHeight);
    measure();
    // The content, never the box. Observing the box would feed the animation
    // its own frames back as new measurements.
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  return (
    <div
      className={cn(
        "overflow-hidden transition-[height] duration-emphasis",
        // Not a token — see docs/motion.md. The curve's tail is what stops a
        // growing box overshooting the content it is uncovering, which is
        // load-bearing against layout in a way no other web motion is; it is
        // the one height transition the app has, and it stays its own curve.
        "ease-[cubic-bezier(0.22,0.61,0.36,1)]",
        // Reduced motion removes the trip, not the destination: the measured
        // height is still written, it just arrives in the frame it is asked
        // for (`docs/motion.md`'s fifth idiom rule).
        "motion-reduce:transition-none",
        className,
      )}
      style={{ height: height != null ? `${height}px` : undefined }}
    >
      <div ref={content}>{children}</div>
    </div>
  );
}
