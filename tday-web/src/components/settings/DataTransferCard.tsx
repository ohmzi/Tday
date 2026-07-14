import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { Download, Loader2, Upload } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SheetCard } from "@/components/ui/sheet-chrome";
import {
  Modal,
  ModalOverlay,
  ModalContent,
  ModalHeader,
  ModalTitle,
  ModalDescription,
  ModalFooter,
} from "@/components/ui/Modal";
import { GuideHelpLink } from "@/features/guide/GuideHelpLink";
import { useToast } from "@/hooks/use-toast";
import { api } from "@/lib/api-client";
import { getErrorMessage } from "@/lib/error-message";
import { downloadJson, fileTimestamp, readJsonFile } from "@/lib/fileTransfer";
import { useTodo } from "@/features/todayTodos/query/get-todo";
import { useFloater } from "@/features/floater/query/get-floater";
import { useCompletedTodo } from "@/features/completed/query/get-completedTodo";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";

type ImportCounts = {
  lists: number;
  floaterLists: number;
  todos: number;
  floaters: number;
  todoInstances: number;
  completedTodos: number;
  completedFloaters: number;
  remappedIds: number;
  preferencesApplied: boolean;
};

type ImportResponse = {
  dryRun: boolean;
  imported: ImportCounts;
  message?: string;
};

// Query keys any import can touch — invalidated after a real (non-dry-run) run
// so every feed refetches the merged data.
const IMPORT_TOUCHED_KEYS = [
  ["todo"],
  ["floater"],
  ["completedTodo"],
  ["listMetaData"],
  ["list"],
  ["floaterListMeta"],
  ["floaterList"],
  ["calendarTodo"],
  ["todoTimeline"],
  ["overdueTodo"],
];

/**
 * "Your data" trust card: shows what lives in this account, downloads it as one
 * portable JSON file, and imports a bundle back (with an additive-merge preview
 * the user confirms first). Server-mode only, so no local/last-sync concept.
 */
export default function DataTransferCard() {
  const { t } = useTranslation("settings");
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const { todos } = useTodo();
  const { floaters } = useFloater();
  const { completedTodos } = useCompletedTodo();
  const { listMetaData } = useListMetaData();

  const [exporting, setExporting] = useState(false);
  const [importing, setImporting] = useState(false);
  // Holds the parsed bundle + its dry-run preview until the user confirms.
  const [pending, setPending] = useState<{ bundle: unknown; preview: ImportCounts } | null>(null);

  const taskCount = (todos?.length ?? 0) + (floaters?.length ?? 0);
  const listCount = Object.keys(listMetaData ?? {}).length;
  const completedCount = completedTodos?.length ?? 0;

  async function handleExport() {
    setExporting(true);
    try {
      const bundle = await api.GET({ url: "/api/export" });
      downloadJson(`tday-export-${fileTimestamp()}.json`, bundle);
      toast({ description: t("data.exportDone") });
    } catch (err) {
      toast({ description: getErrorMessage(err, t("data.exportFailed")), variant: "destructive" });
    } finally {
      setExporting(false);
    }
  }

  async function handleFilePicked(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    // Reset so picking the same file again re-fires onChange.
    event.target.value = "";
    if (!file) return;

    setImporting(true);
    try {
      const bundle = await readJsonFile(file);
      const result = (await api.POST({
        url: "/api/import",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ export: bundle, dryRun: true }),
      })) as ImportResponse;
      setPending({ bundle, preview: result.imported });
    } catch (err) {
      toast({ description: getErrorMessage(err, t("data.importInvalid")), variant: "destructive" });
    } finally {
      setImporting(false);
    }
  }

  async function handleConfirmImport() {
    if (!pending) return;
    setImporting(true);
    try {
      const result = (await api.POST({
        url: "/api/import",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ export: pending.bundle, dryRun: false }),
      })) as ImportResponse;
      setPending(null);
      IMPORT_TOUCHED_KEYS.forEach((queryKey) => queryClient.invalidateQueries({ queryKey }));
      const added = importedItemTotal(result.imported);
      toast({ description: t("data.importDone", { count: added }) });
    } catch (err) {
      toast({ description: getErrorMessage(err, t("data.importFailed")), variant: "destructive" });
    } finally {
      setImporting(false);
    }
  }

  const previewTotal = pending ? importedItemTotal(pending.preview) : 0;

  return (
    <SheetCard className="space-y-4 p-[18px]">
      <div className="space-y-1">
        <div className="flex items-center justify-between gap-2">
          <h2 className="text-[1.4rem] font-black leading-tight text-foreground">{t("data.title")}</h2>
          <GuideHelpLink topic="export-your-data" />
        </div>
        <p className="text-sm font-extrabold text-muted-foreground">
          {t("data.summary", { tasks: taskCount, lists: listCount, completed: completedCount })}
        </p>
      </div>

      <div className="flex flex-col gap-2 sm:flex-row">
        <Button
          type="button"
          variant="default"
          disabled={exporting}
          onClick={handleExport}
          className="h-11 flex-1 rounded-2xl font-black"
        >
          {exporting ? (
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          ) : (
            <Download className="mr-2 h-4 w-4" />
          )}
          {t("data.download")}
        </Button>
        <Button
          type="button"
          variant="outline"
          disabled={importing}
          onClick={() => fileInputRef.current?.click()}
          className="h-11 flex-1 rounded-2xl font-black"
        >
          {importing && !pending ? (
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          ) : (
            <Upload className="mr-2 h-4 w-4" />
          )}
          {t("data.import")}
        </Button>
        <input
          ref={fileInputRef}
          type="file"
          accept="application/json,.json"
          className="hidden"
          onChange={handleFilePicked}
        />
      </div>

      <Modal open={pending !== null} onOpenChange={(open) => !open && setPending(null)}>
        <ModalOverlay>
          <ModalContent>
            <ModalHeader>
              <ModalTitle>{t("data.confirmTitle")}</ModalTitle>
              <ModalDescription>
                {t("data.confirmBody", { count: previewTotal })}
                {pending && pending.preview.remappedIds > 0
                  ? " " + t("data.confirmRemapped", { count: pending.preview.remappedIds })
                  : ""}
              </ModalDescription>
            </ModalHeader>
            <ModalFooter className="mt-4">
              <Button
                variant="outline"
                className="bg-popover w-full sm:w-auto"
                disabled={importing}
                onClick={() => setPending(null)}
              >
                {t("data.confirmCancel")}
              </Button>
              <Button
                variant="default"
                className="w-full sm:w-auto"
                disabled={importing}
                onClick={handleConfirmImport}
              >
                {importing ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
                {t("data.confirmImport")}
              </Button>
            </ModalFooter>
          </ModalContent>
        </ModalOverlay>
      </Modal>
    </SheetCard>
  );
}

function importedItemTotal(counts: ImportCounts): number {
  return (
    counts.lists +
    counts.floaterLists +
    counts.todos +
    counts.floaters +
    counts.completedTodos +
    counts.completedFloaters
  );
}
