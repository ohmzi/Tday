import { useEffect, useMemo, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Trash2 } from "lucide-react";
import { useTranslation } from "react-i18next";
import AppBottomSheet from "@/components/ui/AppBottomSheet";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { SheetCard, SheetSectionTitle } from "@/components/ui/sheet-chrome";
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
import { useCreateFloaterList } from "@/features/floaterList/query/create-floater-list";
import type { FloaterListItemMetaType, ListColor } from "@/types";

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
}: FloaterListFormSheetProps) {
  const { t: appDict } = useTranslation("app");
  const queryClient = useQueryClient();
  const { toast } = useToast();
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

  const deleteListMutation = useMutation({
    mutationFn: deleteFloaterList,
    onSuccess: async (_data, deletedId) => {
      await invalidateFloaterListQueries();
      onOpenChange(false);
      if (pathname.includes(`/app/floater-list/${deletedId}`)) {
        router.push("/app/floater");
      }
    },
    onError: (mutationError) => {
      const message =
        mutationError instanceof Error
          ? mutationError.message
          : "Failed to delete floater list";
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
              autoFocus
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

        <SheetSectionTitle>{appDict("icon")}</SheetSectionTitle>
        <SheetCard className="p-3.5">
          <div className="grid grid-cols-5 gap-2 sm:grid-cols-8">
            {listIconOptions.map((option) => {
              const Icon = option.icon;
              return (
                <button
                  key={option.key}
                  type="button"
                  onClick={() => setIconKey(option.key)}
                  className={cn(
                    "flex h-11 items-center justify-center rounded-2xl text-muted-foreground transition-colors",
                    iconKey === option.key
                      ? "bg-muted text-foreground ring-2 ring-foreground/20"
                      : "hover:bg-muted/70 hover:text-foreground",
                  )}
                  aria-label={option.label}
                  aria-pressed={iconKey === option.key}
                >
                  <Icon className="h-5 w-5" />
                </button>
              );
            })}
          </div>
        </SheetCard>

        {error ? (
          <p className="px-1 text-sm font-bold text-destructive">{error}</p>
        ) : null}

        {isEditing && list ? (
          <div className="pt-2">
            {confirmingDelete ? (
              <div className="flex items-center gap-2">
                <Button
                  type="button"
                  variant="destructive"
                  className="h-12 flex-1 rounded-2xl font-black"
                  disabled={deleting}
                  onClick={() => deleteListMutation.mutate(list.id)}
                >
                  {appDict("deleteFloaterList")}
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  className="h-12 rounded-2xl font-black"
                  disabled={deleting}
                  onClick={() => setConfirmingDelete(false)}
                >
                  {appDict("cancel")}
                </Button>
              </div>
            ) : (
              <Button
                type="button"
                variant="ghost"
                className="h-12 w-full rounded-2xl text-destructive hover:bg-destructive/10 hover:text-destructive"
                onClick={() => setConfirmingDelete(true)}
              >
                <Trash2 className="mr-2 h-4 w-4" />
                {appDict("deleteFloaterList")}
              </Button>
            )}
          </div>
        ) : null}
      </div>
    </AppBottomSheet>
  );
}
