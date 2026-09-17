import { CompletedTodoItemType } from "@/types";
import TodoCheckbox from "@/components/ui/TodoCheckbox";
import ListDot from "@/components/ListDot";
import { Check } from "lucide-react";
import clsx from "clsx";
import { useEffect, useRef, useState } from "react";
import {
  TASK_COMPLETION_CHECK_TO_STRIKE_MS,
  TASK_COMPLETION_REMOVING_TRANSITION,
  TASK_COMPLETION_STRIKE_TO_FADE_MS,
  TASK_COMPLETION_TOTAL_MS,
} from "@/lib/taskCompletionTiming";
import { usePrefersReducedMotion } from "@/lib/prefersReducedMotion";
import { useUnCompleteTodo } from "../query/uncomplete-completedTodo";

export const CompletedTodoItemContainer = ({
  completedTodoItem,
}: {
  completedTodoItem: CompletedTodoItemType;
}) => {
  const { title, description, listID, listName, listColor } = completedTodoItem;
  const { mutateUnComplete } = useUnCompleteTodo();

  // Un-completing is the check-off played backwards, and it is now played on the check-off's
  // own beats rather than on a third of its own:
  //   unchecked (empty circle) → unstruck (the rule lifts) → removing (ink out, box shut) → gone.
  // The constants are the task rows' — one task leaving one list is one motion, and which
  // direction it went is not a reason for it to take a different length of time.
  const [phase, setPhase] = useState<
    "unchecked" | "unstruck" | "removing" | null
  >(null);
  const timers = useRef<number[]>([]);
  const removing = phase === "removing";
  // Subscribed rather than read once: this decides what the row renders, so it has to follow a
  // preference that flips mid-session.
  const reduceMotion = usePrefersReducedMotion();

  useEffect(() => {
    return () => timers.current.forEach((id) => window.clearTimeout(id));
  }, []);

  const handleUncomplete = () => {
    if (phase) return;
    const removeAt = TASK_COMPLETION_CHECK_TO_STRIKE_MS + TASK_COMPLETION_STRIKE_TO_FADE_MS;
    setPhase("unchecked"); // 1. empty the circle + pop
    timers.current.push(
      window.setTimeout(() => setPhase("unstruck"), TASK_COMPLETION_CHECK_TO_STRIKE_MS), // 2.
      window.setTimeout(() => setPhase("removing"), removeAt), // 3. the ink leaves
      // 4. gone. The last leg waits for the box to shut, and with reduce-motion on there is no
      // box shutting — the cut the task rows make, argued in `taskCompletionTiming`.
      window.setTimeout(
        () => mutateUnComplete(completedTodoItem),
        reduceMotion ? removeAt : TASK_COMPLETION_TOTAL_MS,
      ),
    );
  };

  const struck = phase === null || phase === "unchecked";

  return (
    <div
      style={
        removing
          ? {
              opacity: 0,
              gridTemplateRows: "0fr",
              transition: reduceMotion ? undefined : TASK_COMPLETION_REMOVING_TRANSITION,
            }
          : undefined
      }
      // The 1fr track is the task rows' collapse (TodoItemCard), which argues the trick where
      // it lives: a height cannot be animated away from `auto`, so the box shuts by closing a
      // grid row. Without it this row faded out and left its gap for the list to jump through.
      //
      // `grid-cols-[minmax(0,1fr)]` is the other half of the wrapping fix, and it is about
      // COLUMNS the way the row track is about rows: this grid's only column was implicit, and
      // an implicit `auto` track is floored at its content's min-content width. A child that
      // could not shrink therefore grew the TRACK past this box, and the row painted outside
      // the app's border — `max-w-full` on a child caps the child, never the track it sits in.
      // Naming the column `minmax(0, 1fr)` drops that floor, so the border is the edge the row
      // wraps at rather than a line it runs through.
      className="relative grid max-w-full grid-cols-[minmax(0,1fr)] grid-rows-[1fr] overflow-hidden sm:overflow-visible"
    >
      <div
        // Lets the grid item shrink past its own content while the track closes.
        style={removing ? { overflow: "hidden", minHeight: 0 } : undefined}
        // `items-start`: the text column is always the tallest thing in this row, so
        // it is what sets the row's height either way — the only child this moves is
        // the trailing list mark, which is exactly the one that was floating.
        className="group flex max-w-full items-start justify-between gap-3 px-1 py-2.5 sm:rounded-lg sm:transition-colors sm:duration-quick sm:hover:bg-muted/40"
      >
        {/* Stacked from the TOP with the control in the title's own line box
            (`leading-5` = 20 px). The title truncates here, but the notes under it do
            not: a completed task with two lines of notes had its restore circle sitting
            beside the notes rather than beside the task. */}
        <div className="flex min-w-0 items-start gap-3">
          <div className="flex h-5 shrink-0 items-center">
            <TodoCheckbox
              icon={Check}
              onChange={handleUncomplete}
              complete={true}
              checked={phase === null}
            />
          </div>

          <div className="min-w-0">
            <p
              className={clsx(
                // The row has to wrap INSIDE the app's border, and this is the class that let it
                // run past it: `truncate` is `white-space: nowrap`, so the title's min-content
                // width was its entire text on one line, and both the flex row and the grid track
                // above it grew to honour that instead of letting anything shrink. Removing the
                // `nowrap` is the fix — measured, the row then fits at 1440, 1024, 768, 640, 390
                // and 320 — and the two classes that replace it bound the result either way:
                //
                //   `line-clamp-2` is how far it may divide. Without a cap a very long title is a
                //   wall of text (7 lines at 320px); two is what the native clients draw, Android
                //   by `maxLines = 2` on its completed title and iOS by its own note that
                //   "its titles wrap to two lines".
                //   `wrap-anywhere` is `overflow-wrap: anywhere`, and it is load-bearing rather
                //   than tidy: a title with one unbroken 150-character token still pinned the row
                //   to 1375px against a 938px column, because `break-words` does not reduce a
                //   word's min-content width and `anywhere` does.
                //
                // The clamp is in the shared half of the clsx on purpose: the struck and
                // unstruck halves are the same box, so the line count cannot change on the beat.
                "select-none line-clamp-2 wrap-anywhere text-[0.98rem] font-black leading-5 text-muted-foreground transition-colors duration-emphasis",
                // Bare `line-through` while the row is struck, because this row is BORN struck —
                // `.task-strike` is a reveal and would fade a rule in on every row in the list at
                // mount. `.task-unstrike` is the beat the user actually asked for.
                struck ? "line-through" : "task-unstrike",
              )}
            >
              {title}
            </p>
            {description && (
              // `wrap-anywhere` for the same reason the title has it. A note is a `<pre>` with
              // `pre-wrap`, so it breaks at spaces and newlines but not inside a word — and one
              // long URL in a note was enough to push the row past the border on its own, at
              // every width, with the title already wrapping. `w-48` caps the box on mobile and
              // `sm:w-full` makes it the text column's width above that, which is exactly the
              // width the unbroken run then had to be squeezed into.
              <pre className="w-48 whitespace-pre-wrap wrap-anywhere pt-0.5 text-xs font-extrabold leading-4 text-muted-foreground sm:w-full">
                {description}
              </pre>
            )}
          </div>
        </div>

        {listName && (
          // The list mark is an annotation on the task, so it reads with the title's
          // first line — the `h-5` box is the same one the restore circle gets at the
          // other end of the row.
          //
          // It is the list's own GLYPH, not a colour dot with the name beside it. The glyph
          // carries the colour as its tint, so it replaces the dot rather than sitting next to
          // it — which is what the pending rows draw, what Android's completed row draws from
          // `tdayListIconForList`, and what iOS's draws from `TdayListIcon`. The row used to
          // put the API's raw colour NAME into a CSS declaration, where five of the fifteen
          // values (DEEP_BLUE, ROSE, LIGHT_RED, BRICK, SLATE) are not CSS colours at all and
          // the dot rendered with no colour; the tint map has all fifteen.
          //
          // `listID` is what the completed record snapshotted, and `listName`/`listColor` go
          // beside it because the id is not always there to look up — see `ListDot`.
          <div className="flex h-5 shrink-0 items-center gap-2 pr-1">
            {/* Mobile: the glyph alone. Desktop: the glyph + list name pill. */}
            <ListDot
              id={listID}
              name={listName}
              color={listColor}
              className="h-4 w-4 sm:hidden"
            />
            <span className="hidden items-center gap-1 rounded-full border border-border/70 bg-muted/70 px-2 py-[0.2rem] text-xs font-black text-foreground/80 sm:flex">
              <ListDot
                id={listID}
                name={listName}
                color={listColor}
                className="shrink-0 text-sm"
              />
              {/* The name is capped at every width, and the `lg` step is a cap rather than the
                  `lg:max-w-none` this row used to carry: the pill is `shrink-0` and the backend
                  column is `varchar(255)`, so an uncapped name is a second way for the row to
                  run past the border, with nothing above it able to stop it. It ellipsizes. */}
              <span className="max-w-24 truncate md:max-w-52 lg:max-w-64">
                {listName}
              </span>
            </span>
          </div>
        )}
      </div>
    </div>
  );
};
