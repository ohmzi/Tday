import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import AppBottomSheet from "@/components/ui/AppBottomSheet";
import Spinner from "@/components/ui/spinner";
import { useAiCapability } from "@/features/summary/useAiCapability";
import { useSummary, type SummaryMode } from "@/features/summary/useSummary";

type SummarySheetProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  mode: SummaryMode;
  listId?: string;
};

export default function SummarySheet({
  open,
  onOpenChange,
  mode,
  listId,
}: SummarySheetProps) {
  const { t } = useTranslation("summary");
  const { data: capability } = useAiCapability();
  const { mutate, data, error, isPending, reset } = useSummary();

  // Trigger the request when the sheet opens; reset state when it closes so a
  // re-open starts fresh (loading) rather than flashing stale content.
  useEffect(() => {
    if (open) {
      mutate({ mode, listId });
    } else {
      reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, mode, listId]);

  const showSourceLabel = capability?.aiSummaryConfigured && data && !error;
  const sourceLabel =
    data?.source === "ai" ? t("sourceServer") : t("sourceLocal");

  return (
    <AppBottomSheet
      open={open}
      onOpenChange={onOpenChange}
      variant="native"
      title={t("title")}
    >
      <div className="flex flex-col gap-4 pt-2">
        {isPending ? (
          <div className="flex min-h-[120px] flex-col items-center justify-center gap-3 text-muted-foreground">
            <Spinner className="h-7 w-7" />
            <p className="text-sm font-extrabold">{t("loading")}</p>
          </div>
        ) : error ? (
          <p className="py-6 text-center text-base font-extrabold text-muted-foreground">
            {t("error")}
          </p>
        ) : data?.summary ? (
          <p className="whitespace-pre-line text-lg font-extrabold leading-relaxed text-foreground">
            {data.summary}
          </p>
        ) : (
          <p className="py-6 text-center text-base font-extrabold text-muted-foreground">
            {t("clearForNow")}
          </p>
        )}

        {showSourceLabel ? (
          <p className="pt-1 text-center text-xs font-black uppercase tracking-wide text-muted-foreground/70">
            {sourceLabel}
          </p>
        ) : null}
      </div>
    </AppBottomSheet>
  );
}
