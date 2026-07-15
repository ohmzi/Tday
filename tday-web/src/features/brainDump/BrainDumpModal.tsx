import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { Brain, CalendarClock, Loader2, Waves } from "lucide-react";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import { cn } from "@/lib/utils";
import { useBrainDump, type BrainDumpCandidate } from "./useBrainDump";

type Reviewable = BrainDumpCandidate & { selected: boolean };

/**
 * "Brain Dump": paste a blob of thoughts, T'Day splits it into candidate tasks for
 * review, then creates the confirmed ones — dated → scheduled tasks, undated → Anytime
 * floaters. Nothing is created until you confirm.
 */
export default function BrainDumpModal({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useTranslation("today");
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const brainDump = useBrainDump();

  const [text, setText] = useState("");
  const [items, setItems] = useState<Reviewable[] | null>(null);
  const [creating, setCreating] = useState(false);

  const reset = () => {
    setText("");
    setItems(null);
  };

  const parse = () => {
    if (!text.trim()) return;
    brainDump.mutate(
      { text },
      {
        onSuccess: (candidates) =>
          setItems(candidates.map((c) => ({ ...c, selected: true }))),
        onError: () =>
          toast({ description: t("brainDump.parseFailed"), variant: "destructive" }),
      },
    );
  };

  const create = async () => {
    const chosen = (items ?? []).filter((i) => i.selected);
    if (chosen.length === 0) return;
    setCreating(true);
    let created = 0;
    for (const item of chosen) {
      try {
        if (item.dueEpochMs != null) {
          await api.POST({
            url: "/api/todo",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              title: item.title,
              due: new Date(item.dueEpochMs).toISOString(),
              priority: item.priority ?? "Low",
              rrule: item.rrule ?? null,
            }),
          });
        } else {
          await api.POST({
            url: "/api/floater",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              title: item.title,
              priority: item.priority ?? "Low",
            }),
          });
        }
        created += 1;
      } catch {
        // Skip a failed item; keep going with the rest.
      }
    }
    queryClient.invalidateQueries({ queryKey: ["todo"] });
    queryClient.invalidateQueries({ queryKey: ["floater"] });
    queryClient.invalidateQueries({ queryKey: ["floaterListMeta"] });
    setCreating(false);
    toast({ description: t("brainDump.created", { count: created }) });
    reset();
    onOpenChange(false);
  };

  const selectedCount = (items ?? []).filter((i) => i.selected).length;

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) reset();
        onOpenChange(next);
      }}
    >
      <DialogContent className="max-w-lg gap-0 p-0">
        <DialogTitle className="flex items-center gap-2 px-5 pt-5 text-lg font-black">
          <Brain className="h-5 w-5 text-accent" />
          {t("brainDump.title")}
        </DialogTitle>

        {items === null ? (
          <div className="space-y-3 p-5">
            <p className="text-sm font-bold text-muted-foreground">
              {t("brainDump.blurb")}
            </p>
            <textarea
              value={text}
              onChange={(e) => setText(e.target.value)}
              rows={7}
              placeholder={t("brainDump.placeholder")}
              className="w-full resize-none rounded-2xl border border-border/60 bg-muted/40 p-3 text-sm font-semibold text-foreground focus:outline-hidden"
            />
            <Button
              type="button"
              disabled={brainDump.isPending || !text.trim()}
              onClick={parse}
              className="h-11 w-full rounded-2xl font-black"
            >
              {brainDump.isPending ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : null}
              {t("brainDump.parse")}
            </Button>
          </div>
        ) : (
          <div className="flex max-h-[60vh] flex-col p-5">
            <p className="mb-2 text-sm font-extrabold text-muted-foreground">
              {t("brainDump.reviewHint")}
            </p>
            <div className="min-h-0 flex-1 space-y-1.5 overflow-y-auto">
              {items.length === 0 ? (
                <p className="py-6 text-center text-sm font-bold text-muted-foreground">
                  {t("brainDump.empty")}
                </p>
              ) : (
                items.map((item, index) => (
                  <button
                    key={index}
                    type="button"
                    onClick={() =>
                      setItems((prev) =>
                        (prev ?? []).map((it, i) =>
                          i === index ? { ...it, selected: !it.selected } : it,
                        ),
                      )
                    }
                    className={cn(
                      "flex w-full items-center gap-2.5 rounded-2xl border p-2.5 text-left transition",
                      item.selected
                        ? "border-accent/50 bg-accent/10"
                        : "border-border/50 bg-muted/30 opacity-60",
                    )}
                  >
                    <span
                      className={cn(
                        "flex h-5 w-5 shrink-0 items-center justify-center rounded-md border-2",
                        item.selected ? "border-accent bg-accent" : "border-muted-foreground/40",
                      )}
                    >
                      {item.selected ? (
                        <span className="text-[0.7rem] font-black text-accent-foreground">✓</span>
                      ) : null}
                    </span>
                    <span className="min-w-0 flex-1 truncate text-sm font-black text-foreground">
                      {item.title}
                    </span>
                    {item.dueEpochMs != null ? (
                      <CalendarClock className="h-4 w-4 shrink-0 text-accent" />
                    ) : (
                      <Waves className="h-4 w-4 shrink-0 text-muted-foreground" />
                    )}
                  </button>
                ))
              )}
            </div>
            <div className="mt-3 flex gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={reset}
                className="h-11 flex-1 rounded-2xl font-black"
              >
                {t("brainDump.back")}
              </Button>
              <Button
                type="button"
                disabled={creating || selectedCount === 0}
                onClick={create}
                className="h-11 flex-1 rounded-2xl font-black"
              >
                {creating ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
                {t("brainDump.add", { count: selectedCount })}
              </Button>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
