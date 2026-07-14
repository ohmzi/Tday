import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Repeat, X } from "lucide-react";
import { RRule, type Options } from "rrule";
import { useCompletedTodo } from "@/features/completed/query/get-completedTodo";
import { normalizeForSuggestion, suggestRepeat } from "@/lib/todoNlp";
import {
  dismissRepeatSuggestion,
  isRepeatSuggestionDismissed,
} from "@/lib/repeatSuggestionDismissal";
import deriveRepeatType from "@/lib/deriveRepeatType";

// RRULE → i18n label key for the chip copy.
const RRULE_LABEL_KEY: Record<string, string> = {
  "RRULE:FREQ=DAILY;INTERVAL=1": "everyDay",
  "RRULE:FREQ=WEEKLY;INTERVAL=1": "everyWeek",
  "RRULE:FREQ=MONTHLY;INTERVAL=1": "everyMonth",
  "RRULE:FREQ=YEARLY;INTERVAL=1": "everyYear",
};

/**
 * "Make this repeat?" chip. Appears under the title when the completed history shows
 * the same task finished on a steady cadence and no repeat is set yet. Tapping it sets
 * the recurrence; the ✕ dismisses it (persisted per title).
 */
export default function RepeatSuggestionChip({
  title,
  rruleOptions,
  setRruleOptions,
}: {
  title: string;
  rruleOptions: Partial<Options> | null;
  setRruleOptions: (options: Partial<Options> | null) => void;
}) {
  const { t } = useTranslation("today");
  const { completedTodos } = useCompletedTodo();
  const [dismissTick, setDismissTick] = useState(0);

  const norm = normalizeForSuggestion(title);
  const alreadyRepeating = deriveRepeatType({ rruleOptions }) !== null;

  const suggestedRrule = useMemo(() => {
    if (!norm || alreadyRepeating || isRepeatSuggestionDismissed(norm)) return null;
    const completions = (completedTodos ?? []).map((c) => ({
      title: c.title,
      completedAtEpochMs: c.completedAt.getTime(),
    }));
    return suggestRepeat(title, completions);
    // dismissTick is a dep so a dismissal re-checks isRepeatSuggestionDismissed.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [title, norm, alreadyRepeating, completedTodos, dismissTick]);

  const accept = useCallback(() => {
    if (suggestedRrule) setRruleOptions(RRule.parseString(suggestedRrule));
  }, [suggestedRrule, setRruleOptions]);

  const dismiss = useCallback(() => {
    if (norm) {
      dismissRepeatSuggestion(norm);
      setDismissTick((n) => n + 1);
    }
  }, [norm]);

  if (!suggestedRrule) return null;
  const labelKey = RRULE_LABEL_KEY[suggestedRrule] ?? "everyWeek";

  return (
    <div className="flex items-center gap-2 px-1 pt-1">
      <button
        type="button"
        onClick={accept}
        className="inline-flex items-center gap-1.5 rounded-full bg-accent/15 px-3 py-1.5 text-xs font-black text-accent transition active:opacity-70"
      >
        <Repeat className="h-3.5 w-3.5" strokeWidth={2.6} />
        {t("repeatSuggestion.prompt", { cadence: t(`repeatSuggestion.${labelKey}`) })}
      </button>
      <button
        type="button"
        aria-label={t("repeatSuggestion.dismiss")}
        onClick={dismiss}
        className="inline-flex h-6 w-6 items-center justify-center rounded-full text-muted-foreground transition active:opacity-60"
      >
        <X className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}
