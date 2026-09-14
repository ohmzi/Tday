import React, { useCallback, useEffect, useState } from "react";
import TodoCheckbox from "@/components/ui/TodoCheckbox";
import { Checkbox } from "@/components/ui/checkbox";
import clsx from "clsx";
import { DRAG_VACATED_TRANSITION } from "@/lib/dragLiftMotion";
import { TASK_COMPLETION_REMOVING_TRANSITION } from "@/lib/taskCompletionTiming";
import { usePrefersReducedMotion } from "@/lib/prefersReducedMotion";
import { scrollIntoView } from "@/lib/scroll";
import {
  stageTaskCompletion,
  useTaskCompletionPhase,
} from "@/lib/taskCompletionStaging";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { TodoItemType } from "@/types";
import { Check, Copy, Flag, SquarePen, Trash } from "lucide-react";
import { getDisplayTime } from "@/lib/date/displayDate";
import { useLocale } from "@/lib/navigation";
import { useTodoMutation } from "@/providers/TodoMutationProvider";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import ListDot from "@/components/ListDot";
import { getPriorityFlag } from "@/lib/priority";
import { useUserTimezone } from "@/features/user/query/get-timezone";
import { getTodoFocusElementId } from "@/lib/todoToastNavigation";
import TaskFormSheet from "@/components/todo/component/TodoForm/TaskFormSheet";
import { FloatTaskButton, TaskActionButtons } from "@/components/ui/TaskActionButtons";
import { useDemoteTodo } from "@/features/todayTodos/query/demote-todo";
import { DeferTodoMenu } from "@/components/todo/component/DeferTodoMenu";
import { SWIPE_COPY_COLOR, SWIPE_DELETE_COLOR, SWIPE_EDIT_COLOR } from "@/lib/swipeActionColors";
import { useTaskSelection } from "@/providers/TaskSelectionProvider";
import { hapticTick } from "@/lib/haptics";
import { useTranslation } from "react-i18next";
import { useToast } from "@/hooks/use-toast";
import { useSwipeRow } from "@/hooks/useSwipeRow";
import { buildTaskShareText } from "@/lib/listShareText";


type TodoItemContainerProps = {
  todoItem: TodoItemType,
  overdue?: boolean,
  perTaskOverdue?: boolean,
  highlighted?: boolean,
  showOverdueTag?: boolean,
}

type TodoItemCardProps = TodoItemContainerProps & {
  containerProps?: React.HTMLAttributes<HTMLDivElement> & Record<string, unknown>;
  dragging?: boolean;
  style?: React.CSSProperties;
  setDragNodeRef?: (node: HTMLDivElement | null) => void;
}

export const TodoItemCard = ({
  todoItem,
  overdue,
  perTaskOverdue,
  highlighted = false,
  showOverdueTag = true,
  containerProps,
  dragging = false,
  style,
  setDragNodeRef,
}: TodoItemCardProps) => {
  const { listMetaData } = useListMetaData();
  // Inert on any screen without a TaskSelectionProvider (the home feed's Today
  // preview, the calendar, completed history), so those rows behave exactly as
  // they always have.
  const selection = useTaskSelection();
  const selecting = selection.selectionMode;
  const selected = selecting && selection.isSelected(todoItem.id);
  const { useCompleteTodo, useDeleteTodo, readOnly = false } = useTodoMutation();
  const { completeMutateFn } = useCompleteTodo();
  const { deleteMutateFn } = useDeleteTodo();
  const { demoteMutateFn } = useDemoteTodo();
  const { t: appDict, i18n } = useTranslation("app");
  const { toast } = useToast();
  const locale = useLocale();
  const userTimeZone = useUserTimezone();
  const [itemElement, setItemElement] = useState<HTMLDivElement | null>(null);
  const { title, description, completed, priority, rrule } = todoItem;
  const isOverdue = overdue || (perTaskOverdue && !completed && todoItem.due < new Date());
  const itemListID = todoItem.listID;
  const priorityFlag = getPriorityFlag(priority);
  const [displayForm, setDisplayForm] = useState(false);
  const [editInstanceOnly, setEditInstanceOnly] = useState(false);
  const [showHandle, setShowHandle] = useState(false);
  // Staged completion, on the native rows' beats (Android/iOS use 160/360):
  //   checked (green tick) → struck (title and notes, one rule fading in) → removing (ink out,
  //   box shut) → gone.
  // Title and notes are named together because they are one beat and one class: `.task-strike`
  // fades `text-decoration-color` up on both. Android and iOS sweep the rule across the text
  // instead — a mechanism difference between a `text-decoration` and a drawn line, on the same
  // rung either way.
  // The whole sequence runs on its own timers — it is not gated on the undo toast, which lives
  // for 5s independently.
  //
  // The phase is read from `taskCompletionStaging`, not held here, because the row has no right
  // to that window: a filter change or a re-keyed list unmounts it mid-sequence, and when the
  // timers were component state the unmount cleanup threw the user's completion away with them.
  // Keyed by task id, so a row that leaves and comes back rejoins its own sequence.
  const completePhase = useTaskCompletionPhase(todoItem.id);
  const completing = completePhase !== null;
  const removing = completePhase === "removing";
  // Subscribed rather than read once: this decides what the row renders, so a reader who turns
  // reduce-motion on mid-session must not be stuck with the answer given at mount.
  const reduceMotion = usePrefersReducedMotion();

  // Mobile swipe-to-reveal (mirrors the native slide-to-edit/copy/delete). The
  // row foreground translates left to expose Edit + Copy + Delete. A quick
  // horizontal swipe doesn't start a drag because the DnD sensors require a
  // ~250ms press with <5px movement, which a swipe exceeds; vertical
  // scroll/drag is preserved via axis-locking and touch-action: pan-y.
  const ACTIONS_WIDTH = 210;
  const announceSwipeOpen = useCallback(() => {
    // Claim the row: tell any other open row to close so only one is open.
    window.dispatchEvent(new CustomEvent("tday-swipe-open", { detail: todoItem.id }));
  }, [todoItem.id]);
  const { swipeX, transition: swipeTransition, closeSwipe, swipeHandlers } = useSwipeRow({
    actionsWidth: ACTIONS_WIDTH,
    onOpen: announceSwipeOpen,
    // While selecting, the row's only gesture is the tap that picks it — the
    // swipe would otherwise reveal Edit/Delete for a single task in the middle
    // of choosing several.
    disabled: readOnly || selecting,
  });

  const setCombinedRef = (node: HTMLDivElement | null) => {
    setItemElement(node);
    setDragNodeRef?.(node);
  };

  const handleToggleComplete = () => {
    if (readOnly) return;
    if (completed) {
      completeMutateFn(todoItem);
      return;
    }
    if (completing) return;
    // Green tick + pop now; strike, fade and the actual completion are scheduled by the staging
    // module so that none of them depend on this row still being on screen when they come due.
    stageTaskCompletion(todoItem.id, () => completeMutateFn(todoItem));
  };

  /** Copies the task's title/notes/due/priority to the clipboard as plain text. */
  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(
        buildTaskShareText({
          todo: { title, description, due: todoItem.due, priority },
          lang: i18n.language,
          t: appDict,
        }),
      );
      toast({ description: appDict("taskCopied") });
    } catch {
      toast({ description: appDict("taskCopyFailed"), variant: "destructive" });
    }
  };

  // Entering selection mode closes any row left open by a swipe, so the mode
  // never starts with a stray Edit/Delete pair showing.
  useEffect(() => {
    if (selecting) {
      closeSwipe();
      setShowHandle(false);
    }
  }, [closeSwipe, selecting]);

  // Close this row's swipe actions when another row is swiped open.
  useEffect(() => {
    const onOpen = (e: Event) => {
      const id = (e as CustomEvent<string>).detail;
      if (id !== todoItem.id) closeSwipe();
    };
    window.addEventListener("tday-swipe-open", onOpen as EventListener);
    return () => window.removeEventListener("tday-swipe-open", onOpen as EventListener);
  }, [closeSwipe, todoItem.id]);

  useEffect(() => {
    if (!displayForm) {
      setShowHandle(false);
    }
  }, [displayForm]);

  useEffect(() => {
    if (!highlighted || !itemElement) {
      return;
    }

    scrollIntoView(itemElement, { block: "center" });
  }, [highlighted, itemElement]);

  return (
    <>
      <div
        id={getTodoFocusElementId(todoItem.id)}
        ref={setCombinedRef}
        style={
          removing
            ? {
                ...style,
                opacity: 0,
                // Closing the track is the whole height animation. Under reduced motion the row
                // is handed the same finished frame with nothing to carry it there: an empty box
                // and no ink, which is the destination without the trip.
                gridTemplateRows: "0fr",
                transition: reduceMotion ? undefined : TASK_COMPLETION_REMOVING_TRANSITION,
              }
            : {
                ...style,
                // The vacated dim below travels rather than cuts, and it has to be
                // composed onto dnd-kit's own transition instead of added as a
                // `transition-opacity` utility: dnd-kit puts a `transform` shorthand
                // in this same inline style for the whole drag, and an inline
                // shorthand outranks any class the row could carry, so the utility
                // would silently never run. Reduced motion drops the trip and keeps
                // the 70 %, which is the hole itself.
                transition: reduceMotion
                  ? style?.transition
                  : [style?.transition, DRAG_VACATED_TRANSITION].filter(Boolean).join(", "),
              }
        }
        {...containerProps}
        className={clsx(
          // No per-row divider — the date group owns a single divider after its
          // last task (see TodoGroup / TimelineSectionDroppable).
          //
          // A grid with one 1fr row so the box has something interpolable to collapse: `height`
          // cannot be animated away from `auto`, and a measured pixel height would have to be
          // re-measured every time the title rewraps. Same trick the settings editors' `Collapse`
          // uses. The swipe actions sit out of flow and so never size the track.
          "group relative grid max-w-full grid-rows-[1fr] overflow-hidden sm:overflow-visible",
          // The hole the card came out of. Value and reasoning in
          // `dragLiftMotion.ts`, which owns both halves of the pick-up; the clock
          // that carries it there is on the style above.
          dragging && "opacity-70",
        )}
      >
        {/* Mobile: Edit + Delete revealed behind the row by a left swipe — native
            pill style (blue edit, red delete), white icon, label beneath. Fade in
            with the swipe so they're invisible when closed (lets the row stay
            transparent, so the screen watermark shows through). */}
        <div
          className={clsx(
            "absolute inset-y-0 right-0 z-0 flex items-center gap-3 pr-3 sm:hidden",
            // Unreachable while selecting anyway (the swipe is off), but not
            // rendering them keeps a single-task Delete out of the tree during
            // a multi-select entirely.
            selecting && "hidden",
          )}
          style={{ opacity: Math.min(1, Math.abs(swipeX) / ACTIONS_WIDTH) }}
        >
          <button
            type="button"
            aria-label="Edit task"
            onPointerDown={(e) => e.stopPropagation()}
            onMouseDown={(e) => e.stopPropagation()}
            onTouchStart={(e) => e.stopPropagation()}
            onClick={() => {
              setDisplayForm(true);
              closeSwipe();
            }}
            className="flex flex-col items-center gap-1"
          >
            <span
              className="flex h-[34px] w-14 items-center justify-center rounded-[17px]"
              style={{ backgroundColor: SWIPE_EDIT_COLOR }}
            >
              <SquarePen className="h-5 w-5 text-white" strokeWidth={2.2} />
            </span>
            <span className="text-[11px] font-bold text-muted-foreground">Edit</span>
          </button>
          <button
            type="button"
            aria-label="Copy task"
            onPointerDown={(e) => e.stopPropagation()}
            onMouseDown={(e) => e.stopPropagation()}
            onTouchStart={(e) => e.stopPropagation()}
            onClick={() => {
              closeSwipe();
              void handleCopy();
            }}
            className="flex flex-col items-center gap-1"
          >
            <span
              className="flex h-[34px] w-14 items-center justify-center rounded-[17px]"
              style={{ backgroundColor: SWIPE_COPY_COLOR }}
            >
              <Copy className="h-5 w-5 text-white" strokeWidth={2.2} />
            </span>
            <span className="text-[11px] font-bold text-muted-foreground">Copy</span>
          </button>
          <button
            type="button"
            aria-label="Delete task"
            onPointerDown={(e) => e.stopPropagation()}
            onMouseDown={(e) => e.stopPropagation()}
            onTouchStart={(e) => e.stopPropagation()}
            onClick={() => {
              deleteMutateFn(todoItem);
              closeSwipe();
            }}
            className="flex flex-col items-center gap-1"
          >
            <span
              className="flex h-[34px] w-14 items-center justify-center rounded-[17px]"
              style={{ backgroundColor: SWIPE_DELETE_COLOR }}
            >
              <Trash className="h-5 w-5 text-white" strokeWidth={2.2} />
            </span>
            <span className="text-[11px] font-bold text-muted-foreground">Delete</span>
          </button>
        </div>

        {/* Foreground row — slides left on swipe to reveal the actions. */}
        <div
          // The row itself is deliberately NOT interactive: editing is the Edit
          // button (desktop hover / mobile swipe) and completing is the
          // checkbox. It used to carry dnd-kit's drag listeners plus a
          // double-click-to-edit handler, which made it feel pressable while
          // doing nothing on a single press — drag-to-reorder is retired on web
          // (see TodoGroup), so those affordances no longer mean anything.
          onMouseOver={() => {
            if (selecting) return;
            setShowHandle(true);
          }}
          onMouseOut={() => setShowHandle(false)}
          // The one place the row becomes pressable: while selecting, a tap
          // anywhere on it picks it up or puts it down.
          role={selecting ? "button" : undefined}
          tabIndex={selecting ? 0 : undefined}
          aria-pressed={selecting ? selected : undefined}
          onKeyDown={
            selecting
              ? (event) => {
                  if (event.key !== "Enter" && event.key !== " ") return;
                  event.preventDefault();
                  hapticTick();
                  selection.toggle(todoItem.id);
                }
              : undefined
          }
          onClick={() => {
            if (selecting) {
              hapticTick();
              selection.toggle(todoItem.id);
              return;
            }
            if (swipeX !== 0) closeSwipe();
          }}
          {...swipeHandlers}
          style={{
            transform: `translateX(${swipeX}px)`,
            // Settle, tint and ring all come from `useSwipeRow`: three rows drawing the same
            // three properties wrote out three lists that had drifted apart, and only one of
            // them named the ring at all.
            transition: swipeTransition,
            touchAction: "pan-y",
            // A grid item's automatic minimum size is its own content, so the track above can
            // only close once this one is allowed to be smaller than the row it holds. Applied
            // while removing rather than always: clipping a row that is staying would cost it
            // the focus ring and the hover actions that sit proud of its box.
            ...(removing ? { overflow: "hidden", minHeight: 0 } : null),
          }}
          className={clsx(
            // Flat native-style row; transparent so the screen watermark shows
            // through. Swipe actions are hidden via opacity when the row is closed.
            "relative z-10 flex items-center justify-between gap-3 px-1 py-2.5",
            "sm:rounded-lg",
            // The row is keyboard-reachable while selecting, so it keeps a
            // visible focus ring rather than the bare browser outline.
            selecting &&
              "cursor-pointer rounded-lg focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-accent/70",
            selected && "bg-accent/10",
            // The mark a deep link or a search result leaves on the row it lands on, drawn INSIDE
            // the box. Below `sm` this element's border box IS the clip box of the
            // `overflow-hidden` wrapper it is the sole grid item of, so an outset `ring-2` was
            // erased everywhere except the four rounded corners — the mark PR 25b taught to fade
            // was almost entirely invisible on a phone. Moving the ring up onto the wrapper would
            // have escaped the clip and lost the clock: the whitelist that fades it is written
            // into this element's inline `transition`, and the wrapper declares none.
            //
            // Always declared, switched by colour, and load-bearing rather than tidy — though not
            // because the conditional shape would cut. It would not: an unmarked row declares no
            // `box-shadow` at all, and CSS pads a `none` against the other list adopting its
            // `inset` flags, so `inset-ring-2` hung off `highlighted` still transitions — measured
            // against the installed 4.2.2, by growing the ring from 0px to 2px. That growth is the
            // objection. Size changing is `Emphasis` by the rung rule, and this mark shares the
            // `Quick` leg of `swipeTransition` with the desktop tint, which is paint. A ring that
            // is lighting up rather than arriving holds its geometry and moves alpha.
            //
            // It is also the only shape that stays safe. `--tw-inset-ring-shadow` sits at a
            // NON-inset `0 0 #0000` initial, so the first `shadow-*`, ring or press rule to leave
            // a composite `box-shadow` on this element at rest makes the flags disagree and stops
            // the property transitioning altogether — `web-calendar-highlight-ring-cuts` back with
            // every gate green, one utility away. Declaring the inset ring on both sides spends
            // nothing and closes that off.
            //
            // Never both colours at once, hence a ternary and not two `&&`s: Tailwind emits
            // `inset-ring-transparent` after `inset-ring-accent/25`, so the two in one string
            // would resolve to the invisible one. The `sm:` reset is safe for the mirror reason —
            // a variant always sorts after the utility it varies, which is what keeps the desktop
            // mark the flat tint it has always been.
            "inset-ring-2",
            highlighted
              ? "rounded-lg inset-ring-accent/25 sm:bg-accent/5 sm:inset-ring-transparent"
              : "inset-ring-transparent",
          )}
        >
      <div className="flex min-w-0 items-start gap-3">
        <div className="shrink-0">
          {selecting ? (
            // The square selection checkbox replaces the round complete toggle
            // outright, so a tap can never finish a task while picking several.
            <Checkbox
              checked={selected}
              // The row's own click handler owns the toggle; this is the state
              // it reflects, not a second control that could disagree with it.
              tabIndex={-1}
              aria-hidden
              className="pointer-events-none h-5 w-5 rounded-[6px]"
            />
          ) : (
            <TodoCheckbox
              icon={Check}
              complete={completed}
              onChange={handleToggleComplete}
              checked={completed || completing}
              variant={rrule ? "repeat" : "outline-solid"}
            />
          )}
        </div>

        <div className="max-w-full">
          <div className="mb-1.5 flex items-center gap-1.5">
            <p
              className={clsx(
                "select-none text-[0.98rem] font-black leading-5 text-foreground transition-colors duration-emphasis",
                (completePhase === "struck" || removing) &&
                  "task-strike text-muted-foreground",
              )}
            >
              {title}
            </p>
          </div>
          {description && (
            <pre
              className={clsx(
                "w-48 whitespace-pre-wrap pb-2 text-xs font-extrabold leading-4 text-muted-foreground transition-colors duration-emphasis sm:w-full",
                // `task-strike`, not Tailwind's `line-through`: the notes are struck on the
                // same beat as the title an inch above them, and a rule that snaps on under
                // one that fades in reads as two edits to one task.
                (completePhase === "struck" || removing) && "task-strike",
              )}
            >
              {description}
            </pre>
          )}
          <div className="flex flex-wrap items-center justify-start gap-2 text-xs font-black">
            {/* The date header already shows the day, so the row shows just the
                time ("Due 1:00 AM") — same on mobile and desktop, matching native. */}
            <p className={clsx("font-bold", isOverdue ? "text-red" : "text-muted-foreground")}>
              {`Due ${getDisplayTime(todoItem.due, locale, userTimeZone?.timeZone)}`}
            </p>
            {isOverdue && showOverdueTag && (
              <p className='rounded-full border border-red/30 bg-red/10 px-2 py-[0.2rem] text-red font-medium'>
                overdue
              </p>
            )}
          </div>
        </div>
      </div>

        <div className="relative flex shrink-0 items-center gap-2 pr-1 sm:pr-0">
          {/* Priority flag + list, right-aligned on the title line (native layout).
              Mobile shows just the list icon; desktop shows the full name pill.
              On desktop the meta fades out on hover to reveal the edit/delete actions. */}
          <div
            className={clsx(
              "flex items-center gap-2 transition-opacity",
              showHandle && "sm:opacity-0",
            )}
          >
            {itemListID && (
              <>
                <ListDot id={itemListID} className="h-4 w-4 sm:hidden" />
                <span className="hidden items-center gap-1 rounded-full border border-border/70 bg-muted/70 px-2 py-[0.2rem] text-xs font-black text-foreground/80 sm:flex">
                  <ListDot id={itemListID} className="shrink-0 text-sm" />
                  <span className="max-w-24 truncate md:max-w-52 lg:max-w-none">
                    {listMetaData[itemListID]?.name}
                  </span>
                </span>
              </>
            )}
            {priorityFlag && (
              <Flag
                className={clsx("h-4 w-4 shrink-0 sm:h-3.5 sm:w-3.5", priorityFlag.className)}
                aria-label={priorityFlag.label}
              />
            )}
          </div>

          {/* Desktop hover edit/delete actions, overlaid at the right edge.
              Stood down while selecting for the same reason as the swipe pair:
              single-task verbs have no place inside a multi-select. */}
          {!readOnly && !selecting && (
            <div
              className={clsx(
                "absolute right-0 top-1/2 hidden -translate-y-1/2 transition-opacity sm:block",
                showHandle ? "sm:opacity-100" : "sm:pointer-events-none sm:opacity-0",
              )}
            >
              <div className="flex items-center gap-1">
                {/* Quick Defer: recurring todos defer per-occurrence via the
                    edit flow instead, so the one-tap menu hides for them. */}
                {!rrule && !completed && <DeferTodoMenu todo={todoItem} />}
                {/* "Let it float" only makes sense on carried-over tasks, and
                    the backend rejects recurring ones (their series would be
                    silently destroyed). */}
                {isOverdue && !rrule && (
                  <FloatTaskButton
                    onActivate={() => demoteMutateFn(todoItem)}
                    label="Let it float"
                  />
                )}
                <TaskActionButtons
                  onEdit={() => setDisplayForm(true)}
                  onCopy={() => void handleCopy()}
                  onDelete={() => deleteMutateFn(todoItem)}
                  editLabel="Edit task"
                  copyLabel="Copy task"
                  deleteLabel="Delete task"
                />
              </div>
            </div>
          )}
        </div>
        </div>
      </div>
      <TaskFormSheet
        open={displayForm}
        onOpenChange={setDisplayForm}
        todo={todoItem}
        editInstanceOnly={editInstanceOnly}
        setEditInstanceOnly={setEditInstanceOnly}
      />
    </>
  );
};

export const TodoItemContainer = ({
  todoItem,
  overdue,
  perTaskOverdue,
  highlighted = false,
  showOverdueTag = true,
}: TodoItemContainerProps) => {
  //dnd kit setups
  // attributes/listeners are intentionally not destructured: drag-to-reorder is
  // retired on web (see TodoGroup), so the row takes only the node ref and the
  // transform, never the drag handlers.
  const { setNodeRef, transform, transition, isDragging } =
    useSortable({ id: todoItem.id });
  const style = {
    transform: CSS.Translate.toString(transform),
    transition,
  };
  return (
    <TodoItemCard
      todoItem={todoItem}
      overdue={overdue}
      perTaskOverdue={perTaskOverdue}
      highlighted={highlighted}
      showOverdueTag={showOverdueTag}
      // No {...attributes, ...listeners}: spreading dnd-kit's drag props made
      // the row a pointer-capturing, role="button" target for a drag that is
      // retired, which is what made pressing a task feel broken.
      containerProps={{}}
      dragging={isDragging}
      style={style}
      setDragNodeRef={setNodeRef}
    />
  );

};
