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
 * `earlierHandoffPending` is genuinely true (i.e. only for Today, mid
 * requirement-3 hand-off — see `shouldShowTodayEmptyIllustration`'s own doc
 * comment); it is inert everywhere else.
 */
export default function TimelineEmptyState({
  icon,
  accentColor,
  isDayDone,
  celebrate,
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
      />
    </div>
  );
}
