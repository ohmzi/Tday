import { useEffect, useState } from "react";
import {
  closestCenter,
  DndContext,
  type DragEndEvent,
  KeyboardSensor,
  MouseSensor,
  TouchSensor,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import {
  arrayMove,
  SortableContext,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { Check, GripVertical, Plus, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import type { TaskStepType } from "@/types";
import { cn } from "@/lib/utils";
import { GuideHelpLink } from "@/features/guide/GuideHelpLink";
import { SheetCard, SheetSectionTitle } from "@/components/ui/sheet-chrome";
import { useTaskSteps } from "@/features/taskSteps/query/use-task-steps";
import {
  useCreateTaskStep,
  useDeleteTaskStep,
  useReorderTaskSteps,
  useToggleTaskStep,
} from "@/features/taskSteps/query/use-task-step-mutations";

/**
 * Steps section for the task editor — a flat, reorderable checklist attached to
 * a persisted todo. Only rendered for existing todos (steps need a todo id to
 * attach to). Shows an "N/M" completed/total counter next to the title.
 */
export default function TaskStepsSection({ todoId }: { todoId: string }) {
  const { t: appDict } = useTranslation("app");
  const { data: steps } = useTaskSteps(todoId, true);
  const createStep = useCreateTaskStep();
  const toggleStep = useToggleTaskStep();
  const deleteStep = useDeleteTaskStep();
  const reorderSteps = useReorderTaskSteps();

  // Local mirror of the server list so drag reordering feels instant; it is
  // resynced whenever the fetched steps change.
  const [items, setItems] = useState<TaskStepType[]>(steps ?? []);
  const [newTitle, setNewTitle] = useState("");

  useEffect(() => {
    setItems(steps ?? []);
  }, [steps]);

  const sensors = useSensors(
    useSensor(KeyboardSensor),
    useSensor(MouseSensor),
    useSensor(TouchSensor, { activationConstraint: { delay: 250, tolerance: 5 } }),
  );

  const completed = items.filter((step) => step.completed).length;

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const oldIndex = items.findIndex((item) => item.id === active.id);
    const newIndex = items.findIndex((item) => item.id === over.id);
    if (oldIndex < 0 || newIndex < 0) return;
    const next = arrayMove(items, oldIndex, newIndex);
    setItems(next);
    reorderSteps.mutate({ todoId, orderedIds: next.map((item) => item.id) });
  }

  function handleAdd() {
    const title = newTitle.trim();
    if (!title) return;
    createStep.mutate({ todoId, title });
    setNewTitle("");
  }

  return (
    <>
      <div className="flex items-center gap-2 px-1">
        <SheetSectionTitle>{appDict("steps")}</SheetSectionTitle>
        {items.length > 0 && (
          <span className="text-sm font-black tabular-nums text-muted-foreground/70">
            {completed}/{items.length}
          </span>
        )}
        <GuideHelpLink topic="task-steps" className="ml-auto" />
      </div>
      <SheetCard>
        {items.length > 0 && (
          <DndContext
            sensors={sensors}
            collisionDetection={closestCenter}
            onDragEnd={handleDragEnd}
          >
            <SortableContext items={items} strategy={verticalListSortingStrategy}>
              {items.map((step) => (
                <SortableStepRow
                  key={step.id}
                  step={step}
                  onToggle={() =>
                    toggleStep.mutate({
                      id: step.id,
                      completed: !step.completed,
                      todoId,
                    })
                  }
                  onDelete={() => deleteStep.mutate({ id: step.id, todoId })}
                />
              ))}
            </SortableContext>
          </DndContext>
        )}
        <div className="flex items-center gap-3 px-4 py-2.5">
          <span className="flex h-[22px] w-[22px] shrink-0 items-center justify-center text-muted-foreground">
            <Plus className="h-5 w-5" />
          </span>
          <input
            value={newTitle}
            onChange={(e) => setNewTitle(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                handleAdd();
              }
            }}
            placeholder={appDict("addStep")}
            className="min-w-0 flex-1 bg-transparent text-base font-bold text-foreground placeholder:font-bold placeholder:text-muted-foreground/60 focus:outline-hidden"
          />
          <button
            type="button"
            onClick={handleAdd}
            disabled={!newTitle.trim()}
            className="shrink-0 rounded-full px-3 py-1 text-sm font-black text-accent transition-colors hover:bg-muted-foreground/5 disabled:opacity-40"
          >
            {appDict("add")}
          </button>
        </div>
      </SheetCard>
    </>
  );
}

function SortableStepRow({
  step,
  onToggle,
  onDelete,
}: {
  step: TaskStepType;
  onToggle: () => void;
  onDelete: () => void;
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id: step.id });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.6 : 1,
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      className="flex items-center gap-2.5 px-4 py-2.5"
    >
      <button
        type="button"
        aria-label="Reorder step"
        className="shrink-0 cursor-grab touch-none text-muted-foreground/50 active:cursor-grabbing"
        {...attributes}
        {...listeners}
      >
        <GripVertical className="h-4 w-4" />
      </button>
      <button
        type="button"
        role="checkbox"
        aria-checked={step.completed}
        onClick={onToggle}
        className={cn(
          "flex h-5 w-5 shrink-0 items-center justify-center rounded-full border-[2.23px] transition-colors",
          step.completed
            ? "border-accent-lime bg-accent-lime text-white"
            : "border-foreground text-transparent",
        )}
      >
        <Check className="h-3.5 w-3.5 stroke-[3]" />
      </button>
      <span
        className={cn(
          "min-w-0 flex-1 truncate text-base font-bold",
          step.completed
            ? "text-muted-foreground/60 line-through"
            : "text-foreground",
        )}
      >
        {step.title}
      </span>
      <button
        type="button"
        aria-label="Delete step"
        onClick={onDelete}
        className="shrink-0 rounded-full p-1 text-muted-foreground/60 transition-colors hover:text-foreground"
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  );
}
