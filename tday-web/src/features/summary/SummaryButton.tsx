import { useState } from "react";
import { Sparkles } from "lucide-react";
import { useTranslation } from "react-i18next";
import { cn } from "@/lib/utils";
import { useUserPreferences } from "@/providers/UserPreferencesProvider";
import SummarySheet from "@/features/summary/SummarySheet";
import type { SummaryMode } from "@/features/summary/useSummary";

// Mirrors the round top-right button used across the native-style screens.
const defaultButtonClass =
  "flex h-14 w-14 items-center justify-center rounded-full border border-white/70 bg-card/90 text-foreground shadow-[0_12px_28px_-22px_hsl(var(--shadow)/0.55)] transition-all duration-200 hover:-translate-y-0.5 hover:bg-card active:translate-y-0 dark:border-white/10";

type SummaryButtonProps = {
  mode: SummaryMode;
  listId?: string;
  className?: string;
};

export default function SummaryButton({
  mode,
  listId,
  className,
}: SummaryButtonProps) {
  const { t } = useTranslation("summary");
  const { preferences } = useUserPreferences();
  const [open, setOpen] = useState(false);

  // Hidden entirely when the user has turned the feature off (default ON).
  if (preferences?.aiSummaryEnabled === false) {
    return null;
  }

  return (
    <>
      <button
        type="button"
        className={cn(defaultButtonClass, className)}
        onClick={() => setOpen(true)}
        aria-label={t("title")}
      >
        <Sparkles className="h-6 w-6 stroke-[2.6]" />
      </button>
      <SummarySheet
        open={open}
        onOpenChange={setOpen}
        mode={mode}
        listId={listId}
      />
    </>
  );
}
