import React from "react";
import clsx from "clsx";
import { Leaf, SquarePen, Trash } from "lucide-react";

/**
 * Canonical edit / delete action buttons used across the web app (Today,
 * Overdue, Completed, Floaters, Calendar) so every screen shares the same
 * affordance and behaviour.
 *
 * Why the explicit event suppression matters: these buttons live inside
 * dnd-kit draggable rows whose `MouseSensor`/`TouchSensor` activate on
 * `mousedown`/`touchstart` (not pointer events). Stopping only `onPointerDown`
 * left `mousedown` free to bubble to the draggable, so a click with a few
 * pixels of movement was interpreted as a drag-start and the click was
 * swallowed — making edit/delete take several attempts. We stop the actual
 * drag-activating events here, in one place, so the click always wins.
 */
const suppressDragActivation = (event: React.SyntheticEvent) => {
  event.stopPropagation();
};

type ActionButtonProps = {
  onActivate: () => void;
  label: string;
  className?: string;
};

const BASE_BUTTON_CLASS =
  "rounded-full bg-card/80 p-1.5 text-muted-foreground backdrop-blur transition-colors";

export function EditTaskButton({ onActivate, label, className }: ActionButtonProps) {
  return (
    <button
      type="button"
      aria-label={label}
      onPointerDown={suppressDragActivation}
      onMouseDown={suppressDragActivation}
      onTouchStart={suppressDragActivation}
      onClick={(event) => {
        event.stopPropagation();
        onActivate();
      }}
      className={clsx(BASE_BUTTON_CLASS, "hover:bg-muted hover:text-foreground", className)}
    >
      <SquarePen className="h-4 w-4" strokeWidth={1.8} />
    </button>
  );
}

export function DeleteTaskButton({ onActivate, label, className }: ActionButtonProps) {
  return (
    <button
      type="button"
      aria-label={label}
      onPointerDown={suppressDragActivation}
      onMouseDown={suppressDragActivation}
      onTouchStart={suppressDragActivation}
      onClick={(event) => {
        event.stopPropagation();
        onActivate();
      }}
      className={clsx(
        BASE_BUTTON_CLASS,
        "hover:bg-destructive/10 hover:text-destructive",
        className,
      )}
    >
      <Trash className="h-4 w-4" strokeWidth={1.8} />
    </button>
  );
}

/** "Let it float": demote a stale todo into an Anytime floater. */
export function FloatTaskButton({ onActivate, label, className }: ActionButtonProps) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onPointerDown={suppressDragActivation}
      onMouseDown={suppressDragActivation}
      onTouchStart={suppressDragActivation}
      onClick={(event) => {
        event.stopPropagation();
        onActivate();
      }}
      className={clsx(BASE_BUTTON_CLASS, "hover:bg-muted hover:text-foreground", className)}
    >
      <Leaf className="h-4 w-4" strokeWidth={1.8} />
    </button>
  );
}

type TaskActionButtonsProps = {
  onEdit: () => void;
  onDelete: () => void;
  editLabel?: string;
  deleteLabel?: string;
  className?: string;
};

export function TaskActionButtons({
  onEdit,
  onDelete,
  editLabel = "Edit task",
  deleteLabel = "Delete task",
  className,
}: TaskActionButtonsProps) {
  return (
    <div className={clsx("flex items-center gap-1", className)}>
      <EditTaskButton onActivate={onEdit} label={editLabel} />
      <DeleteTaskButton onActivate={onDelete} label={deleteLabel} />
    </div>
  );
}
