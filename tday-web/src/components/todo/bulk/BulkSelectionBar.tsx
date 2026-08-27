import { useState } from "react";
import { useTranslation } from "react-i18next";
import { CheckCheck, Flag, List, Trash2, X } from "lucide-react";
import ListDot from "@/components/ListDot";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  CenteredSelectorOverlay,
  SelectorDivider,
  SelectorRow,
} from "@/components/ui/sheet-chrome/CenteredSelectorOverlay";
import { prioritySwatchClass } from "@/components/ui/sheet-chrome/swatches";
import {
  nativeAppContentClassName,
  nativeAppHorizontalPaddingClassName,
} from "@/components/app/nativeAppLayout";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import {
  priorityLabelKey,
  type Priority,
} from "@/components/todo/component/TodoForm/labels";
import { useTaskSelection } from "@/providers/TaskSelectionProvider";
import { useBulkTodoActions } from "@/hooks/use-bulk-todo-actions";
import {
  bulkActionRequiresConfirmation,
  distinctSourceListCount,
  effectiveBulkSelection,
} from "@/lib/bulk/bulk-selection-policy";
import { hapticConfirm, hapticTick } from "@/lib/haptics";
import { cn } from "@/lib/utils";
import type { TodoItemType } from "@/types";

const PRIORITIES: Priority[] = ["Low", "Medium", "High"];

/**
 * `recurrenceUnknown` is set by `useList` when the payload has no `rrule` key at
 * all (a backend older than the release that added `ListTodoDto.rrule`). Such a
 * row is treated as repeating so it stays out of delete / priority / move: the
 * cost of being wrong that way is one skipped task, the cost of the other way is
 * a destroyed series.
 */
const isRecurringRow = (row: TodoItemType) =>
  Boolean(row.rrule) || row.recurrenceUnknown === true;

/**
 * Only a materialised occurrence can be completed. See
 * `effectiveBulkSelection` — a recurring row without one writes a phantom
 * history entry and completes nothing.
 */
const hasInstanceDate = (row: TodoItemType) => Boolean(row.instanceDate);

/**
 * The selection action bar, plus the two pickers and the two confirmations the
 * four actions need.
 *
 * It takes over the dock slot rather than sitting beside it — same fixed
 * metrics as `RootDock` / `TaskFloatingActionButton`, which stand down while
 * selection mode is open (see `bulk-selection-signal.ts`). Rendered by
 * `TaskSelectionProvider`, so a screen only has to mount the provider.
 */
export default function BulkSelectionBar({
  scopeListId,
}: {
  scopeListId?: string | null;
}) {
  const { t } = useTranslation("app");
  const selection = useTaskSelection();
  const { listMetaData } = useListMetaData();
  const actions = useBulkTodoActions({ scopeListId });
  const [picker, setPicker] = useState<"priority" | "move" | null>(null);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [moveConfirmTarget, setMoveConfirmTarget] = useState<
    { listID: string | null } | null
  >(null);

  const selected = selection.selectedRows;
  const total = selected.length;

  // Complete addresses the occurrence it was shown as; the other three have no
  // per-occurrence route and would hit the whole series, so a recurring row is
  // never eligible for them (§4.1).
  const completeSet = effectiveBulkSelection(
    "complete",
    selected,
    isRecurringRow,
    hasInstanceDate,
  );
  const deleteSet = effectiveBulkSelection("delete", selected, isRecurringRow);
  const prioritySet = effectiveBulkSelection("priority", selected, isRecurringRow);
  const moveSet = effectiveBulkSelection("move", selected, isRecurringRow);

  if (!selection.selectionMode) return null;

  const closeAfterAction = () => {
    setPicker(null);
    setDeleteConfirmOpen(false);
    setMoveConfirmTarget(null);
    selection.exitSelection();
  };

  /**
   * "Applies to N of M — repeating tasks are skipped." Shown wherever an action
   * is about to touch fewer rows than the user selected, so the count in the
   * dialog is never a surprise.
   */
  const skippedNote = (effectiveCount: number) =>
    effectiveCount < total ? (
      <p className="px-5 pb-2 text-sm font-extrabold text-muted-foreground">
        {t("bulkAppliesTo", { count: effectiveCount, total })}
      </p>
    ) : null;

  const runMove = (listID: string | null) => {
    actions.moveSelectedToList(moveSet, listID);
    closeAfterAction();
  };

  const onPickMoveTarget = (listID: string | null) => {
    setPicker(null);
    // Only a selection that spans more than one source list needs asking: that
    // is the one a second bulk move cannot put back.
    const distinctSources = distinctSourceListCount(
      moveSet.map((row) => row.listID ?? scopeListId ?? null),
    );
    if (bulkActionRequiresConfirmation("move", distinctSources)) {
      setMoveConfirmTarget({ listID });
      return;
    }
    runMove(listID);
  };

  const countLabel = selection.atCap
    ? t("bulkSelectedCapped", { count: total })
    : t("bulkSelected", { count: total });

  const writableLists = Object.entries(listMetaData).filter(
    // Moving into a list you can only view is rejected by the backend with
    // "list not found", so those are never offered — same rule as the
    // create/edit sheet's picker.
    ([, meta]) => meta.myRole !== "VIEWER",
  );

  return (
    <>
      <div
        className={cn(
          "pointer-events-none fixed inset-x-0 bottom-[calc(18px+env(safe-area-inset-bottom))] z-40",
          nativeAppHorizontalPaddingClassName,
        )}
      >
        <div className={nativeAppContentClassName}>
          <div
            role="toolbar"
            aria-label={countLabel}
            className={cn(
              "pointer-events-auto flex flex-col gap-1 rounded-[25px] border border-white/70 bg-muted/80 p-1.5",
              "shadow-[0_18px_42px_-24px_hsl(var(--shadow)/0.65)] backdrop-blur-xl",
              "dark:border-white/10 dark:bg-muted/80",
            )}
          >
            <div className="flex items-center gap-1.5 px-1.5 pt-1">
              <span className="min-w-0 flex-1 truncate text-sm font-black text-foreground">
                {countLabel}
              </span>
              <button
                type="button"
                onClick={() => {
                  hapticTick();
                  if (selection.allSelected) selection.deselectAll();
                  else selection.selectAll();
                }}
                className="shrink-0 rounded-full px-2.5 py-1 text-xs font-black text-accent transition-colors hover:bg-card/60"
              >
                {selection.allSelected
                  ? t("bulkDeselectAll")
                  : t("bulkSelectAll")}
              </button>
              <button
                type="button"
                aria-label={t("cancel")}
                onClick={() => {
                  hapticTick();
                  selection.exitSelection();
                }}
                className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-muted-foreground transition-colors hover:bg-card/60 hover:text-foreground"
              >
                <X className="h-4 w-4 stroke-[2.6]" />
              </button>
            </div>

            <div className="flex items-stretch gap-1">
              <BulkActionButton
                icon={CheckCheck}
                label={t("bulkComplete")}
                disabled={completeSet.length === 0}
                onClick={() => {
                  actions.completeSelected(completeSet);
                  closeAfterAction();
                }}
              />
              <BulkActionButton
                icon={Flag}
                label={t("bulkPriority")}
                disabled={prioritySet.length === 0}
                onClick={() => setPicker("priority")}
              />
              <BulkActionButton
                icon={List}
                label={t("bulkMove")}
                disabled={moveSet.length === 0}
                onClick={() => setPicker("move")}
              />
              <BulkActionButton
                icon={Trash2}
                label={t("bulkDelete")}
                destructive
                disabled={deleteSet.length === 0}
                onClick={() => setDeleteConfirmOpen(true)}
              />
            </div>
          </div>
        </div>
      </div>

      {/* Priority — no confirmation and no toast: it is an edit, one tap to
          reverse, and the rows visibly change. */}
      <CenteredSelectorOverlay
        open={picker === "priority"}
        onOpenChange={(open) => !open && setPicker(null)}
        title={t("priority")}
      >
        {skippedNote(prioritySet.length)}
        {PRIORITIES.map((level, index) => (
          <div key={level}>
            {index > 0 ? <SelectorDivider /> : null}
            <SelectorRow
              label={t(priorityLabelKey[level])}
              swatchClass={prioritySwatchClass(level)}
              selected={false}
              onClick={() => {
                actions.setPriorityForSelected(prioritySet, level);
                closeAfterAction();
              }}
            />
          </div>
        ))}
      </CenteredSelectorOverlay>

      {/* Move — scheduled lists only. A floater list is a different silo and
          moving across it is promote/demote, a different operation entirely. */}
      <CenteredSelectorOverlay
        open={picker === "move"}
        onOpenChange={(open) => !open && setPicker(null)}
        title={t("list")}
      >
        {skippedNote(moveSet.length)}
        <SelectorRow
          label={t("noList")}
          selected={false}
          onClick={() => onPickMoveTarget(null)}
        />
        {writableLists.map(([id, meta]) => (
          <div key={id}>
            <SelectorDivider />
            <SelectorRow
              label={meta.name.trim()}
              swatchNode={<ListDot id={id} className="h-2.5 w-2.5" />}
              selected={false}
              onClick={() => onPickMoveTarget(id)}
            />
          </div>
        ))}
      </CenteredSelectorOverlay>

      {/* Layer 1 of the delete guard. Layer 2 is the undo toast the confirmed
          delete then stages behind — both, never one or the other. */}
      <Dialog
        open={deleteConfirmOpen}
        onOpenChange={(open) => !open && setDeleteConfirmOpen(false)}
      >
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>
              {t("bulkDeleteTitle", { count: deleteSet.length })}
            </DialogTitle>
            <DialogDescription>{t("bulkDeleteBody")}</DialogDescription>
          </DialogHeader>
          {deleteSet.length < total ? (
            <p className="text-sm font-semibold text-muted-foreground">
              {t("bulkAppliesTo", { count: deleteSet.length, total })}
            </p>
          ) : null}
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteConfirmOpen(false)}>
              {t("cancel")}
            </Button>
            <Button
              variant="destructive"
              disabled={deleteSet.length === 0}
              onClick={() => {
                hapticConfirm();
                actions.deleteSelected(deleteSet);
                closeAfterAction();
              }}
            >
              {t("bulkDeleteConfirm", { count: deleteSet.length })}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* A move out of several different lists is the one bulk action nothing
          can put back in one step, so it says so before it runs. */}
      <Dialog
        open={moveConfirmTarget !== null}
        onOpenChange={(open) => !open && setMoveConfirmTarget(null)}
      >
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>
              {t("bulkMoveTitle", { count: moveSet.length })}
            </DialogTitle>
            <DialogDescription>{t("bulkMoveBody")}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setMoveConfirmTarget(null)}>
              {t("cancel")}
            </Button>
            <Button
              onClick={() => {
                if (!moveConfirmTarget) return;
                hapticConfirm();
                runMove(moveConfirmTarget.listID);
              }}
            >
              {t("bulkMoveConfirm", { count: moveSet.length })}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

function BulkActionButton({
  icon: Icon,
  label,
  onClick,
  disabled,
  destructive = false,
}: {
  icon: typeof CheckCheck;
  label: string;
  onClick: () => void;
  disabled: boolean;
  destructive?: boolean;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => {
        hapticTick();
        onClick();
      }}
      className={cn(
        "flex flex-1 flex-col items-center justify-center gap-1 rounded-[20px] px-2 py-2.5",
        "text-[11px] font-black transition-colors",
        "disabled:cursor-not-allowed disabled:opacity-40",
        destructive
          ? "text-destructive hover:bg-destructive/10"
          : "text-foreground hover:bg-card/70",
      )}
    >
      <Icon className="h-5 w-5 stroke-[2.4]" />
      <span className="truncate">{label}</span>
    </button>
  );
}
