import { useTranslation } from "react-i18next";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { GuideHelpLink } from "@/features/guide/GuideHelpLink";
import { commandKeyLabel } from "@/lib/keyboard";

type ShortcutsOverlayProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
};

type ShortcutRow = {
  id: string;
  keys: string[];
  label: string;
};

/**
 * The `?` overview. Rows must stay in lockstep with useGlobalHotkeys and the
 * keyboard-shortcuts guide topic (which lists the same chords as kbd blocks).
 */
export default function ShortcutsOverlay({ open, onOpenChange }: ShortcutsOverlayProps) {
  const { t } = useTranslation("shortcuts");
  const command = commandKeyLabel();

  const globalRows: ShortcutRow[] = [
    { id: "new-task", keys: ["N"], label: t("global.newTask") },
    { id: "palette", keys: [command, "K"], label: t("global.openPalette") },
    { id: "overview", keys: ["?"], label: t("global.showShortcuts") },
  ];
  const todoRows: ShortcutRow[] = [
    { id: "quick-open", keys: ["Q"], label: t("todo.openForm") },
    { id: "submit", keys: ["Ctrl", "Enter"], label: t("todo.submit") },
  ];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md rounded-[24px]" aria-describedby={undefined}>
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <ShortcutSection label={t("global.title")} rows={globalRows} />
          <ShortcutSection label={t("todo.title")} rows={todoRows} />
        </div>
        <div className="flex items-center justify-end border-t border-border pt-3">
          <GuideHelpLink topic="keyboard-shortcuts" withLabel />
        </div>
      </DialogContent>
    </Dialog>
  );
}

function ShortcutSection({ label, rows }: { label: string; rows: ShortcutRow[] }) {
  return (
    <section>
      <p className="pb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        {label}
      </p>
      <ul className="space-y-2">
        {rows.map((row) => (
          <li key={row.id} className="flex items-center justify-between gap-4">
            <span className="text-sm text-foreground">{row.label}</span>
            <span className="flex shrink-0 items-center gap-1">
              {row.keys.map((key) => (
                <kbd
                  key={key}
                  className="rounded-md border border-border bg-card-muted px-2 py-1 font-mono text-xs text-card-foreground"
                >
                  {key}
                </kbd>
              ))}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
