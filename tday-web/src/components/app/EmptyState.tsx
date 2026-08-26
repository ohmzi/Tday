import type { ElementType, ReactNode } from "react";
import Confetti from "@/components/app/Confetti";
import { cn } from "@/lib/utils";

/**
 * What a screen shows when it has nothing to show.
 *
 * Every one of these used to be a single line of grey text in the middle of an
 * otherwise blank screen, which reads as a page that failed to load rather than
 * as one that is simply empty. This gives the state something to look at: a
 * little stack of cards with the screen's own glyph on top, tinted with that
 * screen's accent so the empty view still tells you where you are.
 *
 * The illustration is built out of divs rather than one SVG on purpose — it has
 * to take an arbitrary accent, and `currentColor` cannot carry four different
 * alphas of it at once.
 *
 * It never cuts in. The scene rises and fades up over half a second, because
 * the frame before it is the row the user just ticked off leaving the screen,
 * and a state that simply appears in that gap reads as the list breaking rather
 * than as the list being finished.
 *
 * @param accentColor the screen's accent. Interpolated through `color-mix`,
 *   which is the one form that accepts both a bare hex and an `hsl(var(--x))`,
 *   and both turn up here depending on whether the screen or the user's list
 *   picked the colour.
 * @param celebrate the list emptied because the user finished it, rather than
 *   because there was never anything in it: confetti flies first and the scene
 *   comes up through it a beat later.
 */
export default function EmptyState({
  icon: Icon,
  accentColor,
  title,
  description,
  action,
  className,
  celebrate = false,
}: {
  icon: ElementType;
  accentColor: string;
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
  celebrate?: boolean;
}) {
  const tint = (percent: number) =>
    `color-mix(in srgb, ${accentColor} ${percent}%, transparent)`;

  return (
    <div
      className={cn(
        "relative mx-auto flex min-h-[42vh] w-full max-w-sm flex-col items-center justify-center px-6 text-center",
        className,
      )}
    >
      {/* The arrival is on this wrapper rather than on the box above, so the
          confetti below is not faded up along with the scene it flies over. */}
      <div
        className={cn(
          "tday-empty-enter flex w-full flex-col items-center",
          celebrate && "tday-empty-enter-celebrating",
        )}
      >
        <div aria-hidden className="tday-empty-scene relative mb-7 h-[136px] w-[172px]">
          {/* Two cards behind, fanned out. Tinted rather than `bg-card`, so they
              read as depth instead of as two more empty rows. */}
          <div
            className="absolute left-3 top-4 h-[86px] w-[128px] -rotate-[9deg] rounded-[18px]"
            style={{ backgroundColor: tint(14) }}
          />
          <div
            className="absolute left-10 top-3 h-[86px] w-[128px] rotate-[7deg] rounded-[18px]"
            style={{ backgroundColor: tint(22) }}
          />

          {/* The card in front, holding three task rows. The first is ticked. */}
          <div className="absolute left-6 top-6 flex h-[88px] w-[130px] flex-col justify-center gap-[9px] rounded-[18px] border border-border/60 bg-card px-4 shadow-[0_18px_34px_-24px_hsl(var(--shadow)/0.6)]">
            {[0, 1, 2].map((row) => (
              <div key={row} className="flex items-center gap-2.5">
                <span
                  className="flex h-3 w-3 shrink-0 items-center justify-center rounded-full"
                  style={
                    row === 0
                      ? { backgroundColor: accentColor }
                      : { border: `1.5px solid ${tint(45)}` }
                  }
                >
                  {row === 0 ? (
                    <svg viewBox="0 0 12 12" className="h-2 w-2">
                      <path
                        d="M3 6.2 5 8.2 9 3.9"
                        fill="none"
                        stroke="white"
                        strokeWidth="2"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                    </svg>
                  ) : null}
                </span>
                <span
                  className="h-[5px] rounded-full"
                  style={{
                    width: row === 0 ? 44 : row === 1 ? 62 : 38,
                    backgroundColor: tint(row === 0 ? 30 : 52),
                  }}
                />
              </div>
            ))}
          </div>

          {/* The screen's own glyph, sitting on the corner of the stack. */}
          <div
            className="absolute -right-1 bottom-1 flex h-[52px] w-[52px] items-center justify-center rounded-full text-white shadow-[0_14px_26px_-14px_hsl(var(--shadow)/0.7)] ring-4 ring-background"
            style={{ backgroundColor: accentColor }}
          >
            <Icon className="h-6 w-6" strokeWidth={2.6} />
          </div>

          {/* Three sparkles on their own staggered twinkle. */}
          {[
            { top: 2, left: 6, size: 13, delay: "0s" },
            { top: 96, left: 0, size: 9, delay: "0.7s" },
            { top: 12, left: 150, size: 11, delay: "1.4s" },
          ].map((spark) => (
            <svg
              key={`${spark.top}-${spark.left}`}
              viewBox="0 0 24 24"
              className="tday-empty-sparkle absolute"
              style={{
                top: spark.top,
                left: spark.left,
                width: spark.size,
                height: spark.size,
                color: tint(70),
                animationDelay: spark.delay,
              }}
            >
              <path
                d="M12 0c.6 6.2 5.2 10.8 12 12-6.8 1.2-11.4 5.8-12 12-.6-6.2-5.2-10.8-12-12C6.8 10.8 11.4 6.2 12 0Z"
                fill="currentColor"
              />
            </svg>
          ))}
        </div>

        <p className="text-2xl font-black leading-tight text-foreground">{title}</p>
        {description ? (
          <p className="mt-2 text-sm font-semibold leading-relaxed text-muted-foreground">
            {description}
          </p>
        ) : null}
        {action ? <div className="mt-6">{action}</div> : null}
      </div>

      {celebrate ? <Confetti accentColor={accentColor} /> : null}
    </div>
  );
}
