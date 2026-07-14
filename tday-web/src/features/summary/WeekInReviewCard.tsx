import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { CalendarCheck, X } from "lucide-react";
import { useSummary } from "./useSummary";

// A short key for "this week" so a dismissal only hides the current week's card.
function currentWeekKey(): string {
  const now = new Date();
  // Anchor to the most recent Sunday.
  const sunday = new Date(now);
  sunday.setDate(now.getDate() - now.getDay());
  return `tday.weekInReview.dismissed.${sunday.toISOString().slice(0, 10)}`;
}

/**
 * "Week in Review" — a calm Sunday-only card recapping what you cleared over the past
 * week (counts, busiest day, oldest cleared), computed by the shared summary engine.
 * Dismissible for the week.
 */
export default function WeekInReviewCard() {
  const { t } = useTranslation("today");
  const summary = useSummary();
  const weekKey = useMemo(currentWeekKey, []);
  const isSunday = useMemo(() => new Date().getDay() === 0, []);
  const [dismissed, setDismissed] = useState(() => {
    if (typeof window === "undefined") return true;
    return localStorage.getItem(weekKey) === "1";
  });
  const [text, setText] = useState<string | null>(null);
  const [requested, setRequested] = useState(false);

  useEffect(() => {
    if (!isSunday || dismissed || requested) return;
    setRequested(true);
    summary.mutate(
      { mode: "week" },
      { onSuccess: (result) => setText(result.summary) },
    );
  }, [isSunday, dismissed, requested, summary]);

  if (!isSunday || dismissed || !text) return null;

  const dismiss = () => {
    try {
      localStorage.setItem(weekKey, "1");
    } catch {
      // best-effort
    }
    setDismissed(true);
  };

  return (
    <div className="mb-3 flex items-start gap-3 rounded-3xl border border-border/60 bg-accent/10 p-4">
      <CalendarCheck className="mt-0.5 h-5 w-5 shrink-0 text-accent" strokeWidth={2.4} />
      <div className="min-w-0 flex-1">
        <p className="text-sm font-black text-foreground">{t("weekInReview.title")}</p>
        <p className="mt-1 text-sm font-bold text-muted-foreground">{text}</p>
      </div>
      <button
        type="button"
        aria-label={t("weekInReview.dismiss")}
        onClick={dismiss}
        className="shrink-0 rounded-full p-1 text-muted-foreground transition active:opacity-60"
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  );
}
