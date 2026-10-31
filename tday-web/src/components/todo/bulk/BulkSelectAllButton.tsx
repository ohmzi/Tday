import { useTranslation } from "react-i18next";
import { CheckCheck } from "lucide-react";
import { useTaskSelection } from "@/providers/TaskSelectionProvider";
import { hapticButtonTap } from "@/lib/haptics";
import { cn } from "@/lib/utils";

/**
 * One-tap Select all in the header cluster, beside `BulkSelectButton`. It opens
 * selection mode with every reachable row already selected, so selecting a whole
 * list no longer takes Select and then Select all in the bar. Uses the bar's own
 * Select all glyph (`check-check`) and label, and hides under the same gates as
 * `BulkSelectButton`: no provider, a read-only list, nothing to select, or
 * selection mode already open (the bar then owns Select all / Deselect all).
 */
export default function BulkSelectAllButton() {
  const { t } = useTranslation("app");
  const selection = useTaskSelection();

  if (!selection.available || selection.selectionMode) return null;

  return (
    <button
      type="button"
      aria-label={t("bulkSelectAll")}
      title={t("bulkSelectAll")}
      onClick={() => {
        hapticButtonTap();
        selection.enterSelectionWithAll();
      }}
      className={cn(
        "flex h-14 w-14 shrink-0 items-center justify-center rounded-full",
        "border border-white/70 bg-card/90 text-foreground shadow-[0_14px_30px_-16px_hsl(var(--shadow)/0.6)]",
        "transition-all duration-enter hover:-translate-y-0.5 hover:bg-card dark:border-white/10",
      )}
    >
      <CheckCheck className="h-6 w-6 stroke-[2.6]" />
    </button>
  );
}
