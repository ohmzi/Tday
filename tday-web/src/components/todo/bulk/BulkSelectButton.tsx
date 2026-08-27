import { useTranslation } from "react-i18next";
import { CircleCheckBig } from "lucide-react";
import { useTaskSelection } from "@/providers/TaskSelectionProvider";
import { hapticButtonTap } from "@/lib/haptics";
import { cn } from "@/lib/utils";

/**
 * Enters selection mode. Belongs in the `trailingAction` cluster the task-list
 * screens already hand to `MobileSearchHeader`, beside Search and Summary.
 *
 * An explicit button and not a long-press: long-press already starts
 * drag-to-reschedule on five of the seven list modes on Android and iOS, and
 * parity means the entry point is the same gesture everywhere. `circle-check-big`
 * is the shared Lucide glyph, present on all three platforms.
 *
 * Renders nothing without a `TaskSelectionProvider`, on a read-only (VIEWER)
 * list, or on a screen with nothing to select.
 */
export default function BulkSelectButton() {
  const { t } = useTranslation("app");
  const selection = useTaskSelection();

  if (!selection.available || selection.selectionMode) return null;

  return (
    <button
      type="button"
      aria-label={t("bulkSelect")}
      title={t("bulkSelect")}
      onClick={() => {
        hapticButtonTap();
        selection.enterSelection();
      }}
      className={cn(
        "flex h-14 w-14 shrink-0 items-center justify-center rounded-full",
        "border border-white/70 bg-card/90 text-foreground shadow-[0_14px_30px_-16px_hsl(var(--shadow)/0.6)]",
        "transition-all duration-200 hover:-translate-y-0.5 hover:bg-card dark:border-white/10",
      )}
    >
      <CircleCheckBig className="h-6 w-6 stroke-[2.6]" />
    </button>
  );
}
