import type { ElementType } from "react";
import { CheckCheck } from "lucide-react";
import EmptyState from "@/components/app/EmptyState";
import { cn } from "@/lib/utils";
import { emptySceneIsLeaving } from "../lib/todayEarlierIllustration";
import type { EarlierHandoff } from "../lib/useEarlierExpandHandoff";

/**
 * The native-style centered empty message `AllTasksTimelineContainer` shows
 * once a scope has zero tasks (Day Done: "finished everything" earns a calm
 * payoff state instead of the generic no-tasks message).
 *
 * The wrapper is the scene's SLOT and not just a box around it: the hand-off
 * closes its track as well as fading its ink, so the 42vh this claims is given
 * back over that beat rather than in the frame that ends it (see
 * `.tday-empty-slot` in globals.css). Two class names and one question
 * (`emptySceneIsLeaving`) — the stylesheet wants them apart because the ink is
 * an animation and the track is a transition on a grid, but they go on
 * together or not at all. Outside a departure neither is on, the track is open,
 * and there is nothing for the transition to run on.
 */
export default function TimelineEmptyState({
  icon,
  accentColor,
  isDayDone,
  celebrate,
  celebrationStartDelayMs = 0,
  earlierHandoff,
  locale,
  emptyTitle,
  emptyBody,
  appDict,
}: {
  icon: ElementType;
  accentColor: string;
  /** Scope is Today and the day's pending tasks were just finished. */
  isDayDone: boolean;
  /** A completion (this tab's or a remote one) just emptied the scope. */
  celebrate: boolean;
  /**
   * How long the celebration waits for the page to settle before any of it plays
   * (see `EmptyState`'s own doc). Both callers pass the travel their own children
   * take to reach their new slots, because this scene claims its 42vh out of a page
   * those children sit in — the list screen included, where Earlier's block goes on
   * rendering underneath it for as long as the list holds overdue tasks. The zero
   * default is `EmptyState`'s own, kept here so the two contracts stay the same
   * shape; every caller that comes through this component spends the lead.
   */
  celebrationStartDelayMs?: number;
  /** Which half of the swap is mid-exit, if either (see `useEarlierExpandHandoff`). */
  earlierHandoff: EarlierHandoff;
  locale: string;
  emptyTitle: string;
  emptyBody: string;
  appDict: (key: string) => string;
}) {
  // One question, asked once and spent on both class names — see this
  // component's own doc comment for why the stylesheet still wants two.
  const leaving = emptySceneIsLeaving({ earlierHandoff });

  return (
    <div
      className={cn(
        "tday-empty-slot",
        leaving && "tday-empty-exit",
        leaving && "tday-empty-slot-closing",
      )}
    >
      {/* The track the grid above closes. It is the one that takes the clip
          while the track is closing — a grid item that is not a scroll container
          keeps its own content height as the track's floor (see globals.css) —
          so it has to be a box of this component's own rather than the
          scene's. */}
      <div>
        <EmptyState
          // Day Done keeps its own glyph and its date line: it is a payoff,
          // not an absence, and the scope's own icon would undersell it.
          icon={isDayDone ? CheckCheck : icon}
          accentColor={accentColor}
          title={isDayDone ? appDict("allDoneToday") : appDict(emptyTitle)}
          description={
            isDayDone
              ? new Intl.DateTimeFormat(locale, {
                  weekday: "long",
                  day: "numeric",
                  month: "long",
                }).format(new Date())
              : appDict(emptyBody)
          }
          celebrate={celebrate}
          celebrationStartDelayMs={celebrationStartDelayMs}
        />
      </div>
    </div>
  );
}
