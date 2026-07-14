import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { List as ListIcon, Plus, Waves } from "lucide-react";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { nativeRoutes } from "@/components/app/nativeRouteConfig";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import { useFloaterListMetaData } from "@/features/floaterList/query/get-floater-list-meta";
import { commandKeyLabel } from "@/lib/keyboard";
import { useRouter } from "@/lib/navigation";
import { useCreateTask } from "@/providers/CreateTaskProvider";
import { cn } from "@/lib/utils";

type PaletteEntry = {
  id: string;
  section: "actions" | "navigation" | "lists";
  label: string;
  // Matches nativeRouteConfig's icon typing (lucide components qualify).
  icon: React.ElementType;
  accentClass?: string;
  hint?: string;
  run: () => void;
};

type CommandPaletteProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
};

/**
 * Cmd/Ctrl+K palette: jump to any view or list by name, or start a new task.
 * Entries reuse the nativeRoutes nav table (labels match the dock/nav sheet)
 * plus the cached list metadata, so it needs no extra fetches.
 */
export default function CommandPalette({ open, onOpenChange }: CommandPaletteProps) {
  const { t } = useTranslation("palette");
  const router = useRouter();
  const { openCreateTask } = useCreateTask();
  const { listMetaData } = useListMetaData();
  const { floaterListMetaData } = useFloaterListMetaData();
  const [query, setQuery] = useState("");
  const [selectedIndex, setSelectedIndex] = useState(0);
  const listRef = useRef<HTMLDivElement>(null);

  const entries = useMemo<PaletteEntry[]>(() => {
    const close = () => onOpenChange(false);
    const newTask: PaletteEntry = {
      id: "action-new-task",
      section: "actions",
      label: t("newTask"),
      icon: Plus,
      accentClass: "text-accent",
      hint: "N",
      run: () => {
        close();
        openCreateTask();
      },
    };
    const routes: PaletteEntry[] = nativeRoutes.map((route) => ({
      id: `route-${route.id}`,
      section: "navigation" as const,
      label: route.label,
      icon: route.icon,
      accentClass: route.accentClass,
      run: () => {
        close();
        router.push(route.path);
      },
    }));
    const lists: PaletteEntry[] = Object.entries(listMetaData).map(([id, meta]) => ({
      id: `list-${id}`,
      section: "lists" as const,
      label: meta.name,
      icon: ListIcon,
      accentClass: "text-muted-foreground",
      run: () => {
        close();
        router.push(`/app/list/${id}`);
      },
    }));
    const floaterLists: PaletteEntry[] = Object.entries(floaterListMetaData).map(
      ([id, meta]) => ({
        id: `floater-list-${id}`,
        section: "lists" as const,
        label: meta.name,
        icon: Waves,
        accentClass: "text-accent-teal",
        run: () => {
          close();
          router.push(`/app/floater-list/${id}`);
        },
      }),
    );
    return [newTask, ...routes, ...lists, ...floaterLists];
  }, [t, onOpenChange, openCreateTask, router, listMetaData, floaterListMetaData]);

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return entries;
    return entries.filter((entry) => entry.label.toLowerCase().includes(needle));
  }, [entries, query]);

  useEffect(() => {
    setSelectedIndex(0);
  }, [query, open]);

  useEffect(() => {
    if (!open) setQuery("");
  }, [open]);

  useEffect(() => {
    const selected = listRef.current?.querySelector('[data-selected="true"]');
    selected?.scrollIntoView({ block: "nearest" });
  }, [selectedIndex, filtered.length]);

  const handleInputKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setSelectedIndex((index) => Math.min(index + 1, filtered.length - 1));
      return;
    }
    if (event.key === "ArrowUp") {
      event.preventDefault();
      setSelectedIndex((index) => Math.max(index - 1, 0));
      return;
    }
    if (event.key === "Enter") {
      event.preventDefault();
      filtered[selectedIndex]?.run();
    }
  };

  const sections: Array<{ id: PaletteEntry["section"]; label: string }> = [
    { id: "actions", label: t("actions") },
    { id: "navigation", label: t("navigation") },
    { id: "lists", label: t("lists") },
  ];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="top-[18%] max-w-lg translate-y-0 gap-0 overflow-hidden rounded-[24px] p-0"
        aria-describedby={undefined}
      >
        <DialogTitle className="sr-only">{t("title")}</DialogTitle>
        <div className="flex items-center gap-2 border-b border-border px-4">
          <input
            // eslint-disable-next-line jsx-a11y/no-autofocus
            autoFocus
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={handleInputKeyDown}
            placeholder={t("placeholder")}
            className="h-12 w-full bg-transparent text-sm text-foreground outline-none placeholder:text-muted-foreground"
            role="combobox"
            aria-expanded="true"
            aria-controls="command-palette-results"
            aria-label={t("title")}
          />
          <kbd className="shrink-0 rounded-md border border-border bg-card-muted px-2 py-1 font-mono text-xs text-muted-foreground">
            {commandKeyLabel()} K
          </kbd>
        </div>
        <div
          id="command-palette-results"
          ref={listRef}
          role="listbox"
          className="max-h-[320px] overflow-y-auto p-2"
        >
          {filtered.length === 0 && (
            <p className="px-3 py-6 text-center text-sm text-muted-foreground">
              {t("empty")}
            </p>
          )}
          {sections.map(({ id: sectionId, label: sectionLabel }) => {
            const sectionEntries = filtered.filter((entry) => entry.section === sectionId);
            if (sectionEntries.length === 0) return null;
            return (
              <div key={sectionId} className="pb-1">
                <p className="px-3 pb-1 pt-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                  {sectionLabel}
                </p>
                {sectionEntries.map((entry) => {
                  const index = filtered.indexOf(entry);
                  const isSelected = index === selectedIndex;
                  const Icon = entry.icon;
                  return (
                    <button
                      key={entry.id}
                      type="button"
                      role="option"
                      aria-selected={isSelected}
                      data-selected={isSelected}
                      onMouseEnter={() => setSelectedIndex(index)}
                      onClick={() => entry.run()}
                      className={cn(
                        "flex w-full items-center gap-3 rounded-xl px-3 py-2 text-left text-sm text-foreground",
                        isSelected && "bg-card-muted",
                      )}
                    >
                      <Icon className={cn("h-4 w-4 shrink-0", entry.accentClass)} strokeWidth={1.8} />
                      <span className="min-w-0 flex-1 truncate">{entry.label}</span>
                      {entry.hint && (
                        <kbd className="rounded-md border border-border bg-card-muted px-1.5 py-0.5 font-mono text-[10px] text-muted-foreground">
                          {entry.hint}
                        </kbd>
                      )}
                    </button>
                  );
                })}
              </div>
            );
          })}
        </div>
      </DialogContent>
    </Dialog>
  );
}
