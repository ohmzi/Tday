import { useEffect, useMemo, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Share2, Trash2, Users } from "lucide-react";
import { useTranslation } from "react-i18next";
import AppBottomSheet from "@/components/ui/AppBottomSheet";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { SheetCard, SheetSectionTitle } from "@/components/ui/sheet-chrome";
import { api } from "@/lib/api-client";
import { usePathname, useRouter } from "@/lib/navigation";
import { cn } from "@/lib/utils";
import { hapticTick, hapticConfirm } from "@/lib/haptics";
import { listColorMap } from "@/lib/listColorMap";
import {
  DEFAULT_LIST_ICON_KEY,
  getListIcon,
  listIconOptions,
  normalizeListIconKey,
} from "@/lib/listIcons";
import { useToast } from "@/hooks/use-toast";
import { useUndoableDelete } from "@/hooks/use-undoable-delete";
import { useCreateFloaterList } from "@/features/floaterList/query/create-floater-list";
import type {
  FloaterListItemMetaMapType,
  FloaterListItemMetaType,
  ListColor,
} from "@/types";

type EditableFloaterList = {
  id: string;
  name: string;
  color?: ListColor;
  iconKey?: string | null;
};

type FloaterListFormSheetProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  list?: EditableFloaterList | null;
  initialName?: string;
  initialColor?: ListColor;
  initialIconKey?: string;
  onSaved?: (list?: FloaterListItemMetaType) => void;
  // Sharing section (edit mode only): manage members + share externally.
  onManageMembers?: () => void;
  onShareList?: () => void;
};

function normalizeListName(value: string) {
  return value.trim();
}

async function patchFloaterList({
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
    url: "/api/floaterList",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id, name, color, iconKey }),
  });
}

async function deleteFloaterList(id: string) {
  await api.DELETE({
    url: "/api/floaterList",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id, ids: [id] }),
  });
}

export default function FloaterListFormSheet({
  open,
  onOpenChange,
  list,
  initialName = "",
  initialColor = "TEAL",
  initialIconKey = DEFAULT_LIST_ICON_KEY,
  onSaved,
  onManageMembers,
  onShareList,
}: FloaterListFormSheetProps) {
  const { t: appDict } = useTranslation("app");
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const showUndoableDelete = useUndoableDelete();
  const router = useRouter();
  const pathname = usePathname();
  const { createMutateAsync, createLoading } = useCreateFloaterList();
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
    () => listColorMap.find((option) => option.value === color) ?? listColorMap[7],
    [color],
  );
  const SelectedIcon = getListIcon(iconKey);
  const nameColorClass = selectedColor.tailwind.replace("bg-", "text-");

  const invalidateFloaterListQueries = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["floaterListMeta"] }),
      queryClient.invalidateQueries({ queryKey: ["floater"] }),
      queryClient.invalidateQueries({ queryKey: ["floaterList"] }),
    ]);
  };

  const updateListMutation = useMutation({
    mutationFn: patchFloaterList,
    onSuccess: async () => {
      await invalidateFloaterListQueries();
      onSaved?.();
      onOpenChange(false);
    },
    onError: (mutationError) => {
      const message =
        mutationError instanceof Error
          ? mutationError.message
          : "Failed to update floater list";
      setError(message);
      toast({ description: message, variant: "destructive" });
    },
  });

  // Commit half of the delayed-commit delete: fires the real DELETE once the
  // undo toast has closed without undo. Runs from a toast callback, possibly
  // after this sheet unmounted, so it only touches the queryClient and the
  // imperative toast (both safe after unmount).
  const commitDeleteList = async (id: string) => {
    try {
      await deleteFloaterList(id);
    } catch (mutationError) {
      const message =
        mutationError instanceof Error
          ? mutationError.message
          : "Failed to delete floater list";
      toast({ description: message, variant: "destructive" });
    } finally {
      // Success: refresh caches around the cascade (list + its floaters).
      // Failure: the same refetch restores the staged pruning.
      await invalidateFloaterListQueries();
    }
  };

  // Stage: prune the list from the cached metadata, close the sheet and leave
  // the deleted list's page immediately — but DON'T send the DELETE yet; the
  // undo toast decides whether the request ever fires.
  const handleDeleteList = (id: string) => {
    void queryClient.cancelQueries({ queryKey: ["floaterListMeta"] });
    queryClient.setQueryData<FloaterListItemMetaMapType>(
      ["floaterListMeta"],
      (old = {}) => {
        const next = { ...old };
        delete next[id];
        return next;
      },
    );
    onOpenChange(false);
    if (pathname.includes(`/app/floater-list/${id}`)) {
      router.push("/app/floater");
    }
    showUndoableDelete({
      message: appDict("listDeleted"),
      commit: () => void commitDeleteList(id),
      // The server still has the list — a refetch restores the pruned cache.
      undo: () => void invalidateFloaterListQueries(),
    });
  };

  const saving = createLoading || updateListMutation.isPending;
  const canSubmit = Boolean(normalizeListName(name)) && !saving;

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
        color === (list.color ?? "TEAL") &&
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
        createError instanceof Error ? createError.message : "Failed to create floater list";
      setError(message);
    }
  };

  return (
    <AppBottomSheet
      variant="native"
      open={open}
      onOpenChange={onOpenChange}
      title={isEditing ? appDict("editFloaterList") : appDict("newFloaterList")}
      onClose={() => onOpenChange(false)}
      onConfirm={() => void handleSubmit()}
      confirmDisabled={!canSubmit}
      confirmLabel={appDict("save")}
      closeLabel={appDict("cancel")}
    >
      <div className="flex flex-col gap-3 pb-2">
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
              placeholder={appDict("floaterListName")}
              className={cn(
                "h-14 w-full rounded-2xl border-transparent bg-muted/60 text-center text-xl font-extrabold focus-visible:ring-0",
                nameColorClass,
              )}
            />
          </div>
        </SheetCard>

        <SheetSectionTitle>{appDict("color")}</SheetSectionTitle>
        <SheetCard className="p-3.5">
          <div className="flex gap-3 overflow-x-auto pb-1">
            {listColorMap.map((option) => (
              <button
                key={option.value}
                type="button"
                onClick={() => { hapticTick(); setColor(option.value); }}
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
                  onClick={() => { hapticTick(); setIconKey(option.key); }}
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

        {isEditing && list && (onManageMembers || onShareList) ? (
          <>
            <SheetSectionTitle>{appDict("sharing")}</SheetSectionTitle>
            <div className="grid grid-cols-2 gap-2.5">
              {onManageMembers ? (
                <button
                  type="button"
                  onClick={() => {
                    hapticTick();
                    onOpenChange(false);
                    onManageMembers();
                  }}
                  className="flex items-center justify-center gap-2 rounded-2xl border border-border/70 bg-muted/60 px-4 py-2.5 text-sm font-black text-foreground transition-colors hover:bg-muted active:scale-[0.99]"
                >
                  <Users className="h-4 w-4 stroke-[2.4]" />
                  {appDict("members")}
                </button>
              ) : null}
              {onShareList ? (
                <button
                  type="button"
                  onClick={() => {
                    hapticTick();
                    onOpenChange(false);
                    onShareList();
                  }}
                  className="flex items-center justify-center gap-2 rounded-2xl border border-border/70 bg-muted/60 px-4 py-2.5 text-sm font-black text-foreground transition-colors hover:bg-muted active:scale-[0.99]"
                >
                  <Share2 className="h-4 w-4 stroke-[2.4]" />
                  {appDict("share")}
                </button>
              ) : null}
            </div>
          </>
        ) : null}

        {isEditing && list ? (
          confirmingDelete ? (
            <div className="rounded-2xl border border-destructive/30 bg-destructive/5 p-4">
              <p className="text-sm font-extrabold text-destructive">
                Delete &ldquo;{normalizeListName(list.name) || list.name}&rdquo; and all of
                its floaters? This can&apos;t be undone.
              </p>
              <div className="mt-3 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => { hapticConfirm(); setConfirmingDelete(false); }}
                  className="rounded-2xl border-border/70 bg-card px-5 font-black"
                >
                  Keep list
                </Button>
                <Button
                  type="button"
                  variant="destructive"
                  onClick={() => handleDeleteList(list.id)}
                  className="rounded-2xl px-5 font-black"
                >
                  {appDict("deleteFloaterList")}
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
              {appDict("deleteFloaterList")}
            </button>
          )
        ) : null}
      </div>
    </AppBottomSheet>
  );
}
