"use client";

import React, { useMemo, useState } from "react";
import clsx from "clsx";
import { Link } from "@/i18n/navigation";
import { usePathname } from "next/navigation";
import { useRouter } from "@/i18n/navigation";
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
import { Input } from "@/components/ui/input";
import { AlertTriangle, MoreVertical, Pencil, Trash2 } from "lucide-react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { listColorMap } from "@/lib/listColorMap";
import type { ListColor } from "@/types";

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
    url: `/api/list/${id}`,
    body: JSON.stringify({ name: normalizedName, color }),
  });
}

async function deleteList(id: string) {
  if (!id) {
    throw new Error("List id is missing");
  }

  await api.DELETE({ url: `/api/list/${id}` });
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

  const [renameDialogOpen, setRenameDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [selectedList, setSelectedList] = useState<SidebarListItem | null>(null);
  const [renameValue, setRenameValue] = useState("");
  const [renameColor, setRenameColor] = useState<ListColor>("BLUE");
  const [renameError, setRenameError] = useState<string | null>(null);

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

  const updateListMutation = useMutation({
    mutationFn: updateList,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["listMetaData"] });
      queryClient.invalidateQueries({ queryKey: ["todo"] });
      queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
      queryClient.invalidateQueries({ queryKey: ["overdueTodo"] });
      queryClient.invalidateQueries({ queryKey: ["completedTodo"] });
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
    mutationFn: deleteList,
    onSuccess: (_, deletedListId) => {
      queryClient.invalidateQueries({ queryKey: ["listMetaData"] });
      queryClient.invalidateQueries({ queryKey: ["todo"] });
      queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
      queryClient.invalidateQueries({ queryKey: ["overdueTodo"] });
      queryClient.invalidateQueries({ queryKey: ["completedTodo"] });

      if (activeListIdFromPath === deletedListId) {
        router.push("/app/tday");
      }

      setDeleteDialogOpen(false);
      setSelectedList(null);
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

  const closeRenameDialog = () => {
    setRenameDialogOpen(false);
    setSelectedList(null);
    setRenameValue("");
    setRenameColor("BLUE");
    setRenameError(null);
  };

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
      <div className="space-y-1 overflow-x-hidden px-3 py-2">
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
            setDeleteDialogOpen(false);
            setSelectedList(null);
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
              Delete <span className="font-semibold">{selectedList?.name}</span>? This
              will remove it from all tasks and cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setDeleteDialogOpen(false);
                setSelectedList(null);
              }}
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
