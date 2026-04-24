import React, { useMemo, useState } from "react";
import clsx from "clsx";
import { Link } from "@/lib/navigation";
import { usePathname } from "@/lib/navigation";
import { useRouter } from "@/lib/navigation";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import { useMenu } from "@/providers/MenuProvider";
import { useListMetaData } from "./query/get-list-meta";
import ListDot from "@/components/ListDot";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { AlertTriangle, MoreVertical, Pencil, Trash2 } from "lucide-react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { listColorMap } from "@/lib/listColorMap";
import type { ListColor } from "@/types";
import { useToast } from "@/hooks/use-toast";

type ListSidebarSectionProps = {
  mode: "expanded" | "collapsed";
  onNavigate?: () => void;
};

const expandedItemBase =
  "group flex h-10 w-full min-w-0 items-center gap-0 overflow-hidden rounded-xl pl-0 pr-3 text-sm font-medium transition-colors duration-200";

const expandedItemIdle =
  "text-sidebar-foreground/70 transition-colors duration-200 hover:bg-sidebar-accent/50 hover:text-sidebar-foreground";

const expandedItemActive =
  "bg-sidebar-accent text-sidebar-accent-foreground";

const collapsedItemBase =
  "group flex h-10 min-h-10 w-10 items-center justify-center rounded-xl text-sidebar-foreground/70 transition-colors duration-200 hover:bg-sidebar-accent/50 hover:text-sidebar-foreground";

const listIconSlot = "flex h-10 w-10 shrink-0 items-center justify-center";
const listDotClass = "pr-0 text-base leading-none";

type SidebarListItem = {
  id: string;
  name: string;
  color?: ListColor;
  todoCount: number;
};

type DeleteListsResponse = {
  message?: string | null;
  deletedIds?: string[];
};

async function updateList({
  id,
  name,
  color,
}: {
  id: string;
  name: string;
  color?: ListColor;
}) {
  if (!id) {
    throw new Error("List id is missing");
  }

  const normalizedName = normalizeListName(name);
  if (!normalizedName) {
    throw new Error("List name cannot be empty");
  }

  await api.PATCH({
    url: "/api/list",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id, name: normalizedName, color }),
  });
}

async function deleteLists(ids: string[]) {
  const normalizedIds = ids.map(normalizeListName).filter(Boolean);
  if (normalizedIds.length === 0) {
    throw new Error("Select at least one list to delete");
  }

  return (await api.DELETE({
    url: "/api/list",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      id: normalizedIds.length === 1 ? normalizedIds[0] : undefined,
      ids: normalizedIds,
    }),
  })) as DeleteListsResponse | null;
}

export default function ListSidebarSection({
  mode,
  onNavigate,
}: ListSidebarSectionProps) {
  const pathname = usePathname();
  const router = useRouter();
  const { listMetaData } = useListMetaData();
  const { activeMenu, setActiveMenu } = useMenu();
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const [renameDialogOpen, setRenameDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [bulkDeleteDialogOpen, setBulkDeleteDialogOpen] = useState(false);
  const [selectedList, setSelectedList] = useState<SidebarListItem | null>(null);
  const [renameValue, setRenameValue] = useState("");
  const [renameColor, setRenameColor] = useState<ListColor>("BLUE");
  const [renameError, setRenameError] = useState<string | null>(null);
  const [bulkDeleteSelection, setBulkDeleteSelection] = useState<string[]>([]);

  const lists = useMemo(() => {
    return Object.entries(listMetaData)
      .filter(([, value]) => Boolean(value?.name?.trim()))
      .map(([id, value]) => ({
        id,
        name: value.name,
        color: value.color,
        todoCount: value.todoCount ?? 0,
      }));
  }, [listMetaData]);

  const activeListIdFromPath = React.useMemo(() => {
    const markers = ["/app/list/"];
    for (const marker of markers) {
      const index = pathname.indexOf(marker);
      if (index !== -1) {
        return pathname.slice(index + marker.length).split("/")[0] || null;
      }
    }
    return null;
  }, [pathname]);

  const selectedBulkLists = useMemo(() => {
    const selectedIds = new Set(bulkDeleteSelection);
    return lists.filter((list) => selectedIds.has(list.id));
  }, [bulkDeleteSelection, lists]);

  const bulkDeleteIncludesActiveList = Boolean(
    activeListIdFromPath && bulkDeleteSelection.includes(activeListIdFromPath),
  );

  React.useEffect(() => {
    const validIds = new Set(lists.map((list) => list.id));
    setBulkDeleteSelection((current) =>
      current.filter((listId) => validIds.has(listId)),
    );
  }, [lists]);

  const invalidateListQueries = React.useCallback(async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["listMetaData"] }),
      queryClient.invalidateQueries({ queryKey: ["todo"] }),
      queryClient.invalidateQueries({ queryKey: ["todoTimeline"] }),
      queryClient.invalidateQueries({ queryKey: ["overdueTodo"] }),
      queryClient.invalidateQueries({ queryKey: ["completedTodo"] }),
    ]);
  }, [queryClient]);

  const cancelListQueries = React.useCallback(
    async (listIds: string[]) => {
      await Promise.all(
        listIds.map((listId) =>
          queryClient.cancelQueries({ queryKey: ["list", listId] })
        ),
      );
    },
    [queryClient],
  );

  const handleDeletedListsSuccess = React.useCallback(
    async (deletedListIds: string[]) => {
      await invalidateListQueries();

      if (
        activeListIdFromPath &&
        deletedListIds.includes(activeListIdFromPath)
      ) {
        setActiveMenu({ name: "Todo" });
        router.push("/app/tday");
      }
    },
    [activeListIdFromPath, invalidateListQueries, router, setActiveMenu],
  );

  const updateListMutation = useMutation({
    mutationFn: updateList,
    onSuccess: () => {
      invalidateListQueries();
      setRenameDialogOpen(false);
      setSelectedList(null);
      setRenameValue("");
      setRenameColor("BLUE");
      setRenameError(null);
    },
    onError: (error) => {
      setRenameError(
        error instanceof Error ? error.message : "Failed to update list",
      );
    },
  });

  const deleteListMutation = useMutation({
    mutationFn: async (deletedListId: string) => {
      const response = await deleteLists([deletedListId]);
      return {
        requestedIds: [deletedListId],
        deletedIds: response?.deletedIds ?? [],
      };
    },
    onMutate: async (deletedListId) => {
      await cancelListQueries([deletedListId]);
    },
    onSuccess: async ({ requestedIds, deletedIds }) => {
      const resolvedDeletedIds =
        deletedIds.length > 0 ? deletedIds : requestedIds;
      await handleDeletedListsSuccess(resolvedDeletedIds);
      setDeleteDialogOpen(false);
      setSelectedList(null);
    },
    onError: (error) => {
      toast({
        description:
          error instanceof Error ? error.message : "Failed to delete list",
        variant: "destructive",
      });
    },
  });

  const bulkDeleteListsMutation = useMutation({
    mutationFn: async (listIds: string[]) => {
      const response = await deleteLists(listIds);
      return {
        requestedIds: listIds,
        deletedIds: response?.deletedIds ?? [],
        message: response?.message,
      };
    },
    onMutate: async (listIds) => {
      await cancelListQueries(listIds);
    },
    onSuccess: async ({ requestedIds, deletedIds, message }) => {
      const resolvedDeletedIds =
        deletedIds.length > 0 ? deletedIds : requestedIds;
      await handleDeletedListsSuccess(resolvedDeletedIds);
      setBulkDeleteDialogOpen(false);
      setBulkDeleteSelection([]);
      toast({
        description:
          message ??
          (resolvedDeletedIds.length === 1
            ? "List deleted"
            : `${resolvedDeletedIds.length} lists deleted`),
      });
    },
    onError: (error) => {
      toast({
        description:
          error instanceof Error ? error.message : "Failed to delete lists",
        variant: "destructive",
      });
    },
  });

  const handleRenameClick = (list: SidebarListItem) => {
    setSelectedList(list);
    setRenameValue(normalizeListName(list.name));
    setRenameColor(list.color ?? "BLUE");
    setRenameError(null);
    setRenameDialogOpen(true);
  };

  const handleDeleteClick = (list: SidebarListItem) => {
    setSelectedList(list);
    setDeleteDialogOpen(true);
  };

  const closeDeleteDialog = () => {
    setDeleteDialogOpen(false);
    setSelectedList(null);
  };

  const closeBulkDeleteDialog = () => {
    setBulkDeleteDialogOpen(false);
    setBulkDeleteSelection([]);
  };

  const toggleBulkDeleteSelection = (listId: string) => {
    setBulkDeleteSelection((current) =>
      current.includes(listId)
        ? current.filter((id) => id !== listId)
        : [...current, listId],
    );
  };

  const closeRenameDialog = () => {
    setRenameDialogOpen(false);
    setSelectedList(null);
    setRenameValue("");
    setRenameColor("BLUE");
    setRenameError(null);
  };

  const bulkDeleteSummary =
    selectedBulkLists.length === 1
      ? formatListName(selectedBulkLists[0]?.name ?? "")
      : `${selectedBulkLists.length} lists`;

  const handleRenameSubmit = () => {
    if (!selectedList) {
      return;
    }

    const normalizedName = normalizeListName(renameValue);
    if (!normalizedName) {
      setRenameError("List name cannot be empty");
      return;
    }

    if (
      normalizedName === normalizeListName(selectedList.name) &&
      renameColor === (selectedList.color ?? "BLUE")
    ) {
      closeRenameDialog();
      return;
    }

    setRenameError(null);
    updateListMutation.mutate({
      id: selectedList.id,
      name: normalizedName,
      color: renameColor,
    });
  };

  if (lists.length === 0) {
    return null;
  }

  if (mode === "collapsed") {
    return (
      <div className="space-y-1 overflow-x-hidden px-3 py-2">
        {lists.map((list) => {
          const listName = formatListName(list.name);
          const active =
            activeListIdFromPath === list.id ||
            (activeMenu.name === "List" && activeMenu.children?.name === list.id);

          return (
            <Tooltip key={list.id}>
              <TooltipTrigger asChild>
                <Link
                  href={`/app/list/${list.id}`}
                  onClick={() => {
                    setActiveMenu({
                      name: "List",
                      open: true,
                      children: { name: list.id },
                    });
                    onNavigate?.();
                  }}
                  className={cn(
                    collapsedItemBase,
                    active && expandedItemActive,
                  )}
                  aria-label={`List ${listName}`}
                >
                  <ListDot id={list.id} className={listDotClass} />
                </Link>
              </TooltipTrigger>
              <TooltipContent side="right" sideOffset={10}>
                <div className="flex items-center gap-2">
                  <span>{listName}</span>
                  <span className="text-xs opacity-60">({list.todoCount})</span>
                </div>
              </TooltipContent>
            </Tooltip>
          );
        })}
      </div>
    );
  }

  return (
    <>
      <div className="px-3 pb-1 pt-3">
        <div className="flex items-center justify-between px-1">
          <div className="flex items-center gap-2">
            <span className="text-xs font-medium uppercase tracking-wide text-sidebar-foreground/40">
              Lists
            </span>
            <span className="rounded-full bg-sidebar-accent/45 px-2 py-0.5 text-[11px] font-medium text-sidebar-foreground/50">
              {lists.length}
            </span>
          </div>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="h-7 rounded-lg px-2 text-xs text-sidebar-foreground/60 hover:bg-sidebar-accent/50 hover:text-sidebar-foreground"
            onClick={() => {
              setBulkDeleteSelection([]);
              setBulkDeleteDialogOpen(true);
            }}
          >
            Manage
          </Button>
        </div>
      </div>

      <div className="space-y-1 overflow-x-hidden px-3 pb-2 pt-1">
        {lists.map((list) => {
          const listName = formatListName(list.name);
          const active =
            activeListIdFromPath === list.id ||
            (activeMenu.name === "List" && activeMenu.children?.name === list.id);

          return (
            <div
              key={list.id}
              className={cn(
                expandedItemBase,
                active ? expandedItemActive : expandedItemIdle,
              )}
            >
              <Link
                href={`/app/list/${list.id}`}
                onClick={() => {
                  setActiveMenu({
                    name: "List",
                    open: true,
                    children: { name: list.id },
                  });
                  onNavigate?.();
                }}
                className="flex min-w-0 flex-1 items-center gap-0"
                aria-current={active ? "page" : undefined}
              >
                <span className={listIconSlot}>
                  <ListDot id={list.id} className={listDotClass} />
                </span>
                <span className="flex-1 truncate whitespace-nowrap">
                  {listName}
                </span>
              </Link>

              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button
                    variant="ghost"
                    size="icon"
                    className={cn(
                      "h-6 w-6 opacity-0 transition-opacity group-hover:opacity-100 data-[state=open]:opacity-100",
                      "text-sidebar-foreground/50 hover:text-sidebar-foreground",
                    )}
                  >
                    <MoreVertical className="h-3 w-3" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" side="right">
                  <DropdownMenuItem
                    onSelect={() => {
                      handleRenameClick(list);
                    }}
                  >
                    <Pencil className="h-4 w-4" />
                    <span>Rename list</span>
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem
                    onSelect={() => {
                      handleDeleteClick(list);
                    }}
                    className="text-destructive focus:bg-destructive/10 focus:text-destructive"
                  >
                    <Trash2 className="h-4 w-4" />
                    <span>Delete list</span>
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>

              <span
                className={clsx(
                  "text-xs font-medium",
                  active ? "text-sidebar-accent-foreground/80" : "text-sidebar-foreground/40",
                )}
              >
                {list.todoCount}
              </span>
            </div>
          );
        })}
      </div>

      <Dialog
        open={renameDialogOpen}
        onOpenChange={(open) => {
          if (!open) {
            closeRenameDialog();
            return;
          }
          setRenameDialogOpen(true);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Rename List</DialogTitle>
            <DialogDescription>Enter a new name for this list.</DialogDescription>
          </DialogHeader>
          <div className="space-y-2 py-2">
            <Input
              value={renameValue}
              onChange={(e) => {
                setRenameValue(e.target.value);
                if (renameError) {
                  setRenameError(null);
                }
              }}
              placeholder="List name"
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  handleRenameSubmit();
                }
              }}
              autoFocus
              className={cn(
                renameError &&
                  "border-destructive focus-visible:ring-destructive/30",
              )}
            />
            {renameError && (
              <p className="px-1 text-xs text-destructive">{renameError}</p>
            )}
            <div className="space-y-2 px-1 pt-2">
              <p className="text-xs font-medium text-muted-foreground">List color</p>
              <div className="flex flex-wrap gap-2">
                {listColorMap.map((colorOption) => (
                  <button
                    key={colorOption.value}
                    type="button"
                    title={colorOption.name}
                    aria-label={`Set color ${colorOption.name}`}
                    onClick={() => setRenameColor(colorOption.value)}
                    className={cn(
                      "h-6 w-6 rounded-full border transition-transform hover:scale-105",
                      colorOption.tailwind,
                      renameColor === colorOption.value
                        ? "ring-2 ring-accent ring-offset-2 ring-offset-background"
                        : "border-border/60",
                    )}
                  />
                ))}
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={closeRenameDialog}>
              Cancel
            </Button>
            <Button
              onClick={handleRenameSubmit}
              disabled={
                !normalizeListName(renameValue) || updateListMutation.isPending
              }
            >
              {updateListMutation.isPending ? "Saving..." : "Save"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={deleteDialogOpen}
        onOpenChange={(open) => {
          if (!open) {
            closeDeleteDialog();
            return;
          }
          setDeleteDialogOpen(true);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <div className="mb-2 flex items-center gap-3">
              <div className="rounded-full bg-destructive/10 p-2">
                <AlertTriangle className="h-5 w-5 text-destructive" />
              </div>
            <DialogTitle>Delete list</DialogTitle>
            </div>
            <DialogDescription>
              Delete{" "}
              <span className="font-semibold">
                {formatListName(selectedList?.name ?? "")}
              </span>
              ? This will remove it from all tasks and cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={closeDeleteDialog}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={() => {
                if (!selectedList) {
                  return;
                }
                deleteListMutation.mutate(selectedList.id);
              }}
              disabled={deleteListMutation.isPending || !selectedList}
            >
              {deleteListMutation.isPending ? "Deleting..." : "Delete"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={bulkDeleteDialogOpen}
        onOpenChange={(open) => {
          if (!open) {
            closeBulkDeleteDialog();
            return;
          }
          setBulkDeleteDialogOpen(true);
        }}
      >
        <DialogContent className="sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>Manage lists</DialogTitle>
            <DialogDescription>
              Select one or more lists to delete. Tasks will stay in T&apos;Day
              and simply lose their list assignment.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="flex flex-wrap items-center gap-2">
              <span className="rounded-full bg-muted px-2.5 py-1 text-xs font-medium text-muted-foreground">
                {selectedBulkLists.length === 0
                  ? "No lists selected"
                  : `${selectedBulkLists.length} selected`}
              </span>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="h-8 rounded-lg px-2 text-xs"
                onClick={() => {
                  setBulkDeleteSelection(lists.map((list) => list.id));
                }}
                disabled={
                  lists.length === 0 ||
                  selectedBulkLists.length === lists.length
                }
              >
                Select all
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="h-8 rounded-lg px-2 text-xs"
                onClick={() => {
                  setBulkDeleteSelection([]);
                }}
                disabled={selectedBulkLists.length === 0}
              >
                Clear
              </Button>
            </div>

            <div className="max-h-[320px] space-y-2 overflow-y-auto pr-1">
              {lists.map((list) => {
                const listName = formatListName(list.name);
                const isSelected = bulkDeleteSelection.includes(list.id);
                const isActive = activeListIdFromPath === list.id;

                return (
                  <div
                    key={list.id}
                    className={cn(
                      "flex items-center gap-3 rounded-2xl border px-3 py-3 transition-colors",
                      isSelected
                        ? "border-destructive/35 bg-destructive/5"
                        : "border-border/60 bg-background/80 hover:bg-accent/40",
                    )}
                  >
                    <Checkbox
                      checked={isSelected}
                      onCheckedChange={() => {
                        toggleBulkDeleteSelection(list.id);
                      }}
                      aria-label={`Select ${listName}`}
                      className={cn(
                        isSelected &&
                          "border-destructive data-[state=checked]:border-destructive data-[state=checked]:bg-destructive data-[state=checked]:text-white",
                      )}
                    />

                    <button
                      type="button"
                      className="flex min-w-0 flex-1 items-center gap-3 text-left"
                      onClick={() => {
                        toggleBulkDeleteSelection(list.id);
                      }}
                    >
                      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-sidebar-accent/35 text-sidebar-foreground/80">
                        <ListDot id={list.id} className="text-base leading-none" />
                      </span>

                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-medium text-foreground">
                          {listName}
                        </span>
                        <span className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                          <span>
                            {list.todoCount} {list.todoCount === 1 ? "task" : "tasks"}
                          </span>
                          {isActive && (
                            <span className="rounded-full bg-sidebar-accent px-2 py-0.5 font-medium text-sidebar-accent-foreground">
                              Open now
                            </span>
                          )}
                        </span>
                      </span>
                    </button>
                  </div>
                );
              })}
            </div>

            {selectedBulkLists.length > 0 && (
              <div className="rounded-2xl border border-destructive/20 bg-destructive/5 px-4 py-3">
                <div className="flex items-start gap-3">
                  <div className="rounded-full bg-destructive/10 p-2">
                    <AlertTriangle className="h-4 w-4 text-destructive" />
                  </div>
                  <div className="space-y-1">
                    <p className="text-sm font-medium text-foreground">
                      Delete{" "}
                      <span className="font-semibold">{bulkDeleteSummary}</span>
                      ?
                    </p>
                    <p className="text-sm text-muted-foreground">
                      The selected lists will disappear from your sidebar. Tasks
                      inside them will remain and lose their list assignment.
                      {bulkDeleteIncludesActiveList &&
                        " If one of them is open right now, you’ll be sent back to Today."}
                    </p>
                  </div>
                </div>
              </div>
            )}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={closeBulkDeleteDialog}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={() => {
                bulkDeleteListsMutation.mutate(bulkDeleteSelection);
              }}
              disabled={
                bulkDeleteListsMutation.isPending ||
                selectedBulkLists.length === 0
              }
            >
              {bulkDeleteListsMutation.isPending
                ? selectedBulkLists.length === 1
                  ? "Deleting list..."
                  : "Deleting lists..."
                : selectedBulkLists.length <= 1
                  ? "Delete selected"
                  : `Delete ${selectedBulkLists.length} lists`}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

function normalizeListName(name: string) {
  return name.trim();
}

function formatListName(name: string) {
  const normalized = normalizeListName(name);
  return normalized || "list";
}
