import { useEffect, useMemo, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Trash2 } from "lucide-react";
import { useTranslation } from "react-i18next";
import AppBottomSheet from "@/components/ui/AppBottomSheet";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  SheetCard,
  SheetSectionTitle,
} from "@/components/ui/sheet-chrome";
import { api } from "@/lib/api-client";
import { usePathname, useRouter } from "@/lib/navigation";
import { cn } from "@/lib/utils";
import { listColorMap } from "@/lib/listColorMap";
import {
  DEFAULT_LIST_ICON_KEY,
  getListIcon,
  listIconOptions,
  normalizeListIconKey,
} from "@/lib/listIcons";
import { useToast } from "@/hooks/use-toast";
import { useCreateList } from "@/components/Sidebar/List/query/create-list";
import type { ListColor, ListItemMetaType } from "@/types";

type EditableList = {
  id: string;
  name: string;
  color?: ListColor;
  iconKey?: string | null;
};

type ListFormSheetProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  list?: EditableList | null;
  initialName?: string;
  initialColor?: ListColor;
  initialIconKey?: string;
  onSaved?: (list?: ListItemMetaType) => void;
};

function normalizeListName(value: string) {
  return value.trim();
}

async function patchList({
  id,
  name,
  color,
  iconKey,
}: {
  id: string;
  name: string;
  color: ListColor;
  iconKey: string;
}) {
  await api.PATCH({
    url: "/api/list",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id, name, color, iconKey }),
  });
}

async function deleteList(id: string) {
  await api.DELETE({
    url: "/api/list",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id, ids: [id] }),
  });
}

export default function ListFormSheet({
  open,
  onOpenChange,
  list,
  initialName = "",
  initialColor = "BLUE",
  initialIconKey = DEFAULT_LIST_ICON_KEY,
  onSaved,
}: ListFormSheetProps) {
  const { t: appDict } = useTranslation("app");
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const router = useRouter();
  const pathname = usePathname();
  const { createMutateAsync, createLoading } = useCreateList();
  const isEditing = Boolean(list?.id);
  const [name, setName] = useState(initialName);
  const [color, setColor] = useState<ListColor>(list?.color ?? initialColor);
  const [iconKey, setIconKey] = useState(() =>
    normalizeListIconKey(list?.iconKey ?? initialIconKey),
  );
  const [error, setError] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  useEffect(() => {
    if (!open) return;
    setName(list?.name ?? initialName);
    setColor(list?.color ?? initialColor);
    setIconKey(normalizeListIconKey(list?.iconKey ?? initialIconKey));
    setError(null);
    setConfirmingDelete(false);
  }, [initialColor, initialIconKey, initialName, list, open]);

  const selectedColor = useMemo(
    () => listColorMap.find((option) => option.value === color) ?? listColorMap[4],
    [color],
  );
  const SelectedIcon = getListIcon(iconKey);
  const nameColorClass = selectedColor.tailwind.replace("bg-", "text-");

  const invalidateListQueries = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["listMetaData"] }),
      queryClient.invalidateQueries({ queryKey: ["todo"] }),
      queryClient.invalidateQueries({ queryKey: ["todoTimeline"] }),
      queryClient.invalidateQueries({ queryKey: ["overdueTodo"] }),
      queryClient.invalidateQueries({ queryKey: ["completedTodo"] }),
    ]);
  };

  const updateListMutation = useMutation({
    mutationFn: patchList,
    onSuccess: async () => {
      await invalidateListQueries();
      onSaved?.();
      onOpenChange(false);
    },
    onError: (mutationError) => {
      const message =
        mutationError instanceof Error ? mutationError.message : "Failed to update list";
      setError(message);
      toast({ description: message, variant: "destructive" });
    },
  });

  const deleteListMutation = useMutation({
    mutationFn: deleteList,
    onSuccess: async (_data, deletedId) => {
      await invalidateListQueries();
      onOpenChange(false);
      if (pathname.includes(`/app/list/${deletedId}`)) {
        router.push("/app/tday");
      }
    },
    onError: (mutationError) => {
      const message =
        mutationError instanceof Error ? mutationError.message : "Failed to delete list";
      setError(message);
      toast({ description: message, variant: "destructive" });
    },
  });

  const deleting = deleteListMutation.isPending;
  const saving = createLoading || updateListMutation.isPending;
  const canSubmit = Boolean(normalizeListName(name)) && !saving && !deleting;

  const handleSubmit = async () => {
    const normalizedName = normalizeListName(name);
    if (!normalizedName) {
      setError("List name cannot be empty");
      return;
    }

    setError(null);
    if (isEditing && list) {
      const currentIconKey = normalizeListIconKey(list.iconKey);
      if (
        normalizedName === normalizeListName(list.name) &&
        color === (list.color ?? "BLUE") &&
        iconKey === currentIconKey
      ) {
        onOpenChange(false);
        return;
      }
      updateListMutation.mutate({ id: list.id, name: normalizedName, color, iconKey });
      return;
    }

    try {
      const created = await createMutateAsync({ name: normalizedName, color, iconKey });
      onSaved?.(created);
      onOpenChange(false);
      setName("");
      setColor(initialColor);
      setIconKey(normalizeListIconKey(initialIconKey));
    } catch (createError) {
      const message =
        createError instanceof Error ? createError.message : "Failed to create list";
      setError(message);
    }
  };

  return (
    <AppBottomSheet
      variant="native"
      open={open}
      onOpenChange={onOpenChange}
      title={isEditing ? appDict("editList") : appDict("newList")}
      onClose={() => onOpenChange(false)}
      onConfirm={() => void handleSubmit()}
      confirmDisabled={!canSubmit}
      confirmLabel={appDict("save")}
      closeLabel={appDict("cancel")}
    >
      <div className="flex flex-col gap-3 pb-2">
        {/* Icon preview + name */}
        <SheetCard className="p-[18px]">
          <div className="flex flex-col items-center gap-4">
            <span
              className={cn(
                "flex h-20 w-20 shrink-0 items-center justify-center rounded-full text-white shadow-inner",
                selectedColor.tailwind,
              )}
            >
              <SelectedIcon className="h-9 w-9 stroke-[2.4]" />
            </span>
            <Input
              value={name}
              onChange={(event) => setName(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  event.preventDefault();
                  void handleSubmit();
                }
              }}
              placeholder={appDict("listName")}
              className={cn(
                "h-14 w-full rounded-2xl border-transparent bg-muted/60 text-center text-xl font-extrabold focus-visible:ring-0",
                nameColorClass,
              )}
              autoFocus
            />
          </div>
        </SheetCard>

        {/* Color */}
        <SheetSectionTitle>{appDict("color")}</SheetSectionTitle>
        <SheetCard className="p-3.5">
          <div className="flex gap-3 overflow-x-auto pb-1">
            {listColorMap.map((option) => (
              <button
                key={option.value}
                type="button"
                onClick={() => setColor(option.value)}
                className={cn(
                  "h-12 w-12 shrink-0 rounded-full transition-transform active:scale-95",
                  option.tailwind,
                  color === option.value && "ring-[3px] ring-foreground/30",
                )}
                aria-label={`Use ${option.name}`}
                aria-pressed={color === option.value}
              />
            ))}
          </div>
        </SheetCard>

        {/* Icon */}
        <SheetSectionTitle>{appDict("icon")}</SheetSectionTitle>
        <SheetCard className="p-3.5">
          <div className="flex gap-2.5 overflow-x-auto pb-1">
            {listIconOptions.map((option) => {
              const Icon = option.icon;
              const selected = iconKey === option.key;

              return (
                <button
                  key={option.key}
                  type="button"
                  onClick={() => setIconKey(option.key)}
                  className={cn(
                    "flex h-12 w-12 shrink-0 items-center justify-center rounded-full transition-transform active:scale-95",
                    selected
                      ? "bg-accent/15 text-accent ring-[2px] ring-accent/55"
                      : "bg-muted/60 text-muted-foreground hover:text-foreground",
                  )}
                  aria-label={`Use ${option.label} icon`}
                  aria-pressed={selected}
                >
                  <Icon className="h-5 w-5 stroke-[2.4]" />
                </button>
              );
            })}
          </div>
        </SheetCard>

        {error ? (
          <p className="px-1 text-sm font-extrabold text-destructive">{error}</p>
        ) : null}

        {isEditing && list ? (
          confirmingDelete ? (
            <div className="rounded-2xl border border-destructive/30 bg-destructive/5 p-4">
              <p className="text-sm font-extrabold text-destructive">
                Delete &ldquo;{normalizeListName(list.name) || list.name}&rdquo; and all of
                its tasks? This can&apos;t be undone.
              </p>
              <div className="mt-3 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setConfirmingDelete(false)}
                  disabled={deleting}
                  className="rounded-2xl border-border/70 bg-card px-5 font-black"
                >
                  Keep list
                </Button>
                <Button
                  type="button"
                  variant="destructive"
                  onClick={() => deleteListMutation.mutate(list.id)}
                  disabled={deleting}
                  className="rounded-2xl px-5 font-black"
                >
                  {deleting ? "Deleting..." : appDict("deleteList")}
                </Button>
              </div>
            </div>
          ) : (
            <button
              type="button"
              onClick={() => setConfirmingDelete(true)}
              className="flex w-full items-center justify-center gap-2 rounded-2xl border border-destructive/30 bg-destructive/5 px-5 py-2.5 text-sm font-black text-destructive transition-colors hover:bg-destructive/10 active:scale-[0.99]"
            >
              <Trash2 className="h-4 w-4 stroke-[2.4]" />
              {appDict("deleteList")}
            </button>
          )
        ) : null}
      </div>
    </AppBottomSheet>
  );
}
