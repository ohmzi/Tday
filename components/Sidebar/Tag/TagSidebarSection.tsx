"use client";

import React, { useMemo, useState } from "react";
import clsx from "clsx";
import { Link } from "@/i18n/navigation";
import { usePathname } from "next/navigation";
import { useRouter } from "@/i18n/navigation";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import { useMenu } from "@/providers/MenuProvider";
import { useProjectMetaData } from "../Project/query/get-project-meta";
import ProjectTag from "@/components/ProjectTag";
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
import type { ProjectColor } from "@prisma/client";
import { projectColorMap } from "@/lib/projectColorMap";

type TagSidebarSectionProps = {
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

const tagIconSlot = "flex h-10 w-10 shrink-0 items-center justify-center";
const tagHashClass = "pr-0 text-base leading-none";

type SidebarTagItem = {
  id: string;
  name: string;
  color?: ProjectColor;
  todoCount: number;
};

async function updateTag({
  id,
  name,
  color,
}: {
  id: string;
  name: string;
  color?: ProjectColor;
}) {
  if (!id) {
    throw new Error("Tag id is missing");
  }

  const normalizedName = normalizeTagName(name);
  if (!normalizedName) {
    throw new Error("Tag name cannot be empty");
  }

  await api.PATCH({
    url: `/api/project/${id}`,
    body: JSON.stringify({ name: normalizedName, color }),
  });
}

async function deleteTag(id: string) {
  if (!id) {
    throw new Error("Tag id is missing");
  }

  await api.DELETE({ url: `/api/project/${id}` });
}

export default function TagSidebarSection({
  mode,
  onNavigate,
}: TagSidebarSectionProps) {
  const pathname = usePathname();
  const router = useRouter();
  const { projectMetaData } = useProjectMetaData();
  const { activeMenu, setActiveMenu } = useMenu();
  const queryClient = useQueryClient();

  const [renameDialogOpen, setRenameDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [selectedTag, setSelectedTag] = useState<SidebarTagItem | null>(null);
  const [renameValue, setRenameValue] = useState("");
  const [renameColor, setRenameColor] = useState<ProjectColor>("BLUE");
  const [renameError, setRenameError] = useState<string | null>(null);

  const tags = useMemo(() => {
    return Object.entries(projectMetaData)
      .filter(([, value]) => Boolean(value?.name?.trim()))
      .map(([id, value]) => ({
        id,
        name: value.name,
        color: value.color,
        todoCount: value.todoCount ?? 0,
      }));
  }, [projectMetaData]);

  const activeTagIdFromPath = React.useMemo(() => {
    const marker = "/app/tag/";
    const index = pathname.indexOf(marker);
    if (index === -1) {
      return null;
    }
    return pathname.slice(index + marker.length).split("/")[0] || null;
  }, [pathname]);

  const updateTagMutation = useMutation({
    mutationFn: updateTag,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["projectMetaData"] });
      queryClient.invalidateQueries({ queryKey: ["todo"] });
      queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
      queryClient.invalidateQueries({ queryKey: ["overdueTodo"] });
      queryClient.invalidateQueries({ queryKey: ["completedTodo"] });
      setRenameDialogOpen(false);
      setSelectedTag(null);
      setRenameValue("");
      setRenameColor("BLUE");
      setRenameError(null);
    },
    onError: (error) => {
      setRenameError(
        error instanceof Error ? error.message : "Failed to update tag",
      );
    },
  });

  const deleteTagMutation = useMutation({
    mutationFn: deleteTag,
    onSuccess: (_, deletedTagId) => {
      queryClient.invalidateQueries({ queryKey: ["projectMetaData"] });
      queryClient.invalidateQueries({ queryKey: ["todo"] });
      queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
      queryClient.invalidateQueries({ queryKey: ["overdueTodo"] });
      queryClient.invalidateQueries({ queryKey: ["completedTodo"] });

      if (activeTagIdFromPath === deletedTagId) {
        router.push("/app/tday");
      }

      setDeleteDialogOpen(false);
      setSelectedTag(null);
    },
  });

  const handleRenameClick = (tag: SidebarTagItem) => {
    setSelectedTag(tag);
    setRenameValue(normalizeTagName(tag.name));
    setRenameColor(tag.color ?? "BLUE");
    setRenameError(null);
    setRenameDialogOpen(true);
  };

  const handleDeleteClick = (tag: SidebarTagItem) => {
    setSelectedTag(tag);
    setDeleteDialogOpen(true);
  };

  const closeRenameDialog = () => {
    setRenameDialogOpen(false);
    setSelectedTag(null);
    setRenameValue("");
    setRenameColor("BLUE");
    setRenameError(null);
  };

  const handleRenameSubmit = () => {
    if (!selectedTag) {
      return;
    }

    const normalizedName = normalizeTagName(renameValue);
    if (!normalizedName) {
      setRenameError("Tag name cannot be empty");
      return;
    }

    if (
      normalizedName === normalizeTagName(selectedTag.name) &&
      renameColor === (selectedTag.color ?? "BLUE")
    ) {
      closeRenameDialog();
      return;
    }

    setRenameError(null);
    updateTagMutation.mutate({
      id: selectedTag.id,
      name: normalizedName,
      color: renameColor,
    });
  };

  if (tags.length === 0) {
    return null;
  }

  if (mode === "collapsed") {
    return (
      <div className="space-y-1 overflow-x-hidden px-3 py-2">
        {tags.map((tag) => {
          const tagName = formatTagName(tag.name);
          const active =
            activeTagIdFromPath === tag.id ||
            (activeMenu.name === "Tag" && activeMenu.children?.name === tag.id);

          return (
            <Tooltip key={tag.id}>
              <TooltipTrigger asChild>
                <Link
                  href={`/app/tag/${tag.id}`}
                  onClick={() => {
                    setActiveMenu({
                      name: "Tag",
                      open: true,
                      children: { name: tag.id },
                    });
                    onNavigate?.();
                  }}
                  className={cn(
                    collapsedItemBase,
                    active && expandedItemActive,
                  )}
                  aria-label={`# ${tagName}`}
                >
                  <ProjectTag id={tag.id} className={tagHashClass} />
                </Link>
              </TooltipTrigger>
              <TooltipContent side="right" sideOffset={10}>
                <div className="flex items-center gap-2">
                  <span>#{tagName}</span>
                  <span className="text-xs opacity-60">({tag.todoCount})</span>
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
        {tags.map((tag) => {
        const tagName = formatTagName(tag.name);
          const active =
            activeTagIdFromPath === tag.id ||
            (activeMenu.name === "Tag" && activeMenu.children?.name === tag.id);

          return (
            <div
              key={tag.id}
              className={cn(
                expandedItemBase,
                active ? expandedItemActive : expandedItemIdle,
              )}
            >
              <Link
                href={`/app/tag/${tag.id}`}
                onClick={() => {
                  setActiveMenu({
                    name: "Tag",
                    open: true,
                    children: { name: tag.id },
                  });
                  onNavigate?.();
                }}
                className="flex min-w-0 flex-1 items-center gap-0"
                aria-current={active ? "page" : undefined}
              >
                <span className={tagIconSlot}>
                  <ProjectTag id={tag.id} className={tagHashClass} />
                </span>
                <span className="flex-1 truncate whitespace-nowrap">
                  {tagName}
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
                      handleRenameClick(tag);
                    }}
                  >
                    <Pencil className="h-4 w-4" />
                    <span>Rename tag</span>
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem
                    onSelect={() => {
                      handleDeleteClick(tag);
                    }}
                    className="text-destructive focus:bg-destructive/10 focus:text-destructive"
                  >
                    <Trash2 className="h-4 w-4" />
                    <span>Delete tag</span>
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>

              <span
                className={clsx(
                  "text-xs font-medium",
                  active ? "text-sidebar-accent-foreground/80" : "text-sidebar-foreground/40",
                )}
              >
                {tag.todoCount}
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
            <DialogTitle>Rename Tag</DialogTitle>
            <DialogDescription>Enter a new name for this tag.</DialogDescription>
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
              placeholder="Tag name"
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
              <p className="text-xs font-medium text-muted-foreground">Tag color</p>
              <div className="flex flex-wrap gap-2">
                {projectColorMap.map((colorOption) => (
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
                !normalizeTagName(renameValue) || updateTagMutation.isPending
              }
            >
              {updateTagMutation.isPending ? "Saving..." : "Save"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={deleteDialogOpen}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteDialogOpen(false);
            setSelectedTag(null);
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
              <DialogTitle>Delete tag</DialogTitle>
            </div>
            <DialogDescription>
              Delete <span className="font-semibold">{selectedTag?.name}</span>? This
              will remove it from all tasks and cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setDeleteDialogOpen(false);
                setSelectedTag(null);
              }}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={() => {
                if (!selectedTag) {
                  return;
                }
                deleteTagMutation.mutate(selectedTag.id);
              }}
              disabled={deleteTagMutation.isPending || !selectedTag}
            >
              {deleteTagMutation.isPending ? "Deleting..." : "Delete"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

function normalizeTagName(name: string) {
  return name.replace(/^#+\s*/, "").trim();
}

function formatTagName(name: string) {
  const normalized = normalizeTagName(name);
  return normalized || "tag";
}
