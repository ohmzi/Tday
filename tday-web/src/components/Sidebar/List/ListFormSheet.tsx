import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import AppBottomSheet from "@/components/ui/AppBottomSheet";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { api } from "@/lib/api-client";
import { cn } from "@/lib/utils";
import { listColorMap } from "@/lib/listColorMap";
import { useToast } from "@/hooks/use-toast";
import { useCreateList } from "@/components/Sidebar/List/query/create-list";
import type { ListColor, ListItemMetaType } from "@/types";

type EditableList = {
  id: string;
  name: string;
  color?: ListColor;
};

type ListFormSheetProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  list?: EditableList | null;
  initialName?: string;
  initialColor?: ListColor;
  onSaved?: (list?: ListItemMetaType) => void;
};

function normalizeListName(value: string) {
  return value.trim();
}

async function patchList({
  id,
  name,
  color,
}: {
  id: string;
  name: string;
  color: ListColor;
}) {
  await api.PATCH({
    url: "/api/list",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id, name, color }),
  });
}

export default function ListFormSheet({
  open,
  onOpenChange,
  list,
  initialName = "",
  initialColor = "BLUE",
  onSaved,
}: ListFormSheetProps) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const { createMutateAsync, createLoading } = useCreateList();
  const isEditing = Boolean(list?.id);
  const [name, setName] = useState(initialName);
  const [color, setColor] = useState<ListColor>(list?.color ?? initialColor);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setName(list?.name ?? initialName);
    setColor(list?.color ?? initialColor);
    setError(null);
  }, [initialColor, initialName, list, open]);

  const selectedColor = useMemo(
    () => listColorMap.find((option) => option.value === color) ?? listColorMap[4],
    [color],
  );

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

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const normalizedName = normalizeListName(name);
    if (!normalizedName) {
      setError("List name cannot be empty");
      return;
    }

    setError(null);
    if (isEditing && list) {
      if (normalizedName === normalizeListName(list.name) && color === (list.color ?? "BLUE")) {
        onOpenChange(false);
        return;
      }
      updateListMutation.mutate({ id: list.id, name: normalizedName, color });
      return;
    }

    try {
      const created = await createMutateAsync({ name: normalizedName, color });
      onSaved?.(created);
      onOpenChange(false);
      setName("");
      setColor(initialColor);
    } catch (createError) {
      const message =
        createError instanceof Error ? createError.message : "Failed to create list";
      setError(message);
    }
  };

  const saving = createLoading || updateListMutation.isPending;

  return (
    <AppBottomSheet
      open={open}
      onOpenChange={onOpenChange}
      title={isEditing ? "Edit list" : "New list"}
      description="Name the list and choose the color shown across task views."
    >
      <form className="space-y-5" onSubmit={handleSubmit}>
        <div className="rounded-[24px] border border-white/70 bg-card/92 p-4 dark:border-white/10">
          <div className="flex items-center gap-3">
            <span
              className={cn(
                "h-12 w-12 rounded-2xl border border-white/70 shadow-inner dark:border-white/10",
                selectedColor.tailwind,
              )}
            />
            <Input
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="List name"
              className="h-12 rounded-2xl border-border/70 bg-background text-base font-extrabold"
              autoFocus
            />
          </div>
        </div>

        <div>
          <p className="mb-2 text-sm font-black text-muted-foreground">Color</p>
          <div className="grid grid-cols-5 gap-2 sm:grid-cols-8">
            {listColorMap.map((option) => (
              <button
                key={option.value}
                type="button"
                onClick={() => setColor(option.value)}
                className={cn(
                  "flex h-12 items-center justify-center rounded-2xl border transition-transform active:scale-95",
                  color === option.value
                    ? "border-foreground/50 bg-card shadow-sm"
                    : "border-border/70 bg-card/65",
                )}
                aria-label={`Use ${option.name}`}
              >
                <span className={cn("h-7 w-7 rounded-full", option.tailwind)} />
              </button>
            ))}
          </div>
        </div>

        {error ? (
          <p className="text-sm font-extrabold text-destructive">{error}</p>
        ) : null}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
            className="rounded-2xl border-border/70 bg-card px-5 font-black"
          >
            Cancel
          </Button>
          <Button
            type="submit"
            disabled={saving}
            className="rounded-2xl bg-accent px-5 font-black text-accent-foreground hover:bg-accent/90"
          >
            {saving ? "Saving..." : isEditing ? "Save" : "Create"}
          </Button>
        </div>
      </form>
    </AppBottomSheet>
  );
}
