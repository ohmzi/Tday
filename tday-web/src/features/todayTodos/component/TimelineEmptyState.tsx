import type { ElementType } from "react";
import { CheckCheck } from "lucide-react";
import EmptyState from "@/components/app/EmptyState";
import { cn } from "@/lib/utils";
import { TODAY_EARLIER_EXIT_MS } from "../lib/todayEarlierIllustration";

/**
 * The native-style centered empty message `AllTasksTimelineContainer` shows
 * once a scope has zero tasks (Day Done: "finished everything" earns a calm
 * payoff state instead of the generic no-tasks message).
 *
 * The wrapper div only ever carries the exit animation while
 * `earlierHandoffPending` is genuinely true — mid requirement-3 hand-off, on
 * whichever scope's own Earlier bucket is mid-exit right now (see
 * `shouldShowTodayEmptyIllustration`'s own doc comment); it is inert
 * everywhere else.
 */
export default function TimelineEmptyState({
  icon,
  accentColor,
  isDayDone,
  celebrate,
  celebrationStartDelayMs = 0,
  earlierHandoffPending,
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
   * (see `EmptyState`'s own doc). `AllTasksTimelineContainer` passes the travel its
   * own children take to reach their new slots, because this scene claims its 42vh
   * out of the page they sit in; `ListContainer` omits it, because nothing on a list
   * screen moves when this mounts.
   */
  celebrationStartDelayMs?: number;
  /** Requirement 3's two-phase hand-off is mid-exit (see `useEarlierExpandHandoff`). */
  earlierHandoffPending: boolean;
  locale: string;
  emptyTitle: string;
  emptyBody: string;
  appDict: (key: string) => string;
}) {
  return (
    <div
      className={cn(earlierHandoffPending && "tday-empty-exit")}
      style={
        earlierHandoffPending
          ? { animationDuration: `${TODAY_EARLIER_EXIT_MS}ms` }
          : undefined
      }
    >
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
  );
}
