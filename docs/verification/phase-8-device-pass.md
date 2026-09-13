# Phase 8 device pass — TF2, iOS with Reduce Motion on

Run against the release build that closes Phase 8. See `README.md` in this directory for the row
format and for how the file is closed out.

This file is opened by a Phase 7 PR, which is not a mistake. Cycles are spent at phase merges and
there are exactly three in the whole programme, so Phase 7's iOS batch (PR 26) has no cycle of its
own to be checked in; TF2 is the next one that exists and this is its file. The rows are written now,
while the diff is open, and read at the Phase 8 merge — see `README.md`, "iOS: three cycles, for the
whole programme".

Every iOS row here is checked twice, once normally and once with **Settings → Accessibility → Motion
→ Reduce Motion** on, which is the half TF2 exists for. Phase 6's parity work whose iOS third missed
the TF1 build, and Phase 8's own rows, land here alongside these.

## iOS

- [ ] **PR 26 · ios · The today block leaves instead of vanishing** — the Scheduled task home
      (the root feed's task tab), with exactly one task due today, so the block below the Today card
      is one row and emptying it empties the block.
      Do:     complete that task from the block itself, and watch the space it occupied rather than
              the row. Repeat with three tasks due today, completing them one at a time, so the last
              completion is the one that empties the block.
      Watch:  the last row fades and lifts away over 150 ms, and the Category board and the Lists
              section below it then TRAVEL up over 320 ms into the ~72 pt the block was holding.
              Two beats, in that order. On the earlier completions the block stays and only the row
              leaves, with the rows under it closing the gap on the same 320 ms.
      Fails:  the board and the lists are simply 72 pt higher on the next frame — the defect, and it
              is only visible on the completion that empties the block, which is why the setup says
              one task. Also a fail: the block fading for longer than the rows below take to arrive,
              which would mean the block is riding the travel instead of the departure rung.
      Also:   with Reduce Motion on, the block is there or it is not, and the layout below is at its
              new height immediately — no fade, no travel, and no intermediate frame where the space
              is half closed.

- [ ] **PR 26 · ios · One day at a time in the calendar** — the Calendar screen, Month view, with
      two ADJACENT days that both have tasks on them, three or more on at least one. Two populated
      days next to each other is the whole setup: a swap into an empty day cannot show this.
      Do:     tap the other day. Then tap back. Then run the same swap with the day chevrons rather
              than the grid.
      Watch:  one day on screen at a time. The day you are leaving fades out over 150 ms, and only
              then — after it is gone — the new day's rows fade up over 200 ms, with the card and
              everything below it resizing to the new day's height over 320 ms. Nothing slides down
              from the top; the rows do not travel, the day under them changed.
      Fails:  both days' rows drawn over each other mid-swap — a stack of half-transparent titles
              where one list should be, which is the defect. Also a fail: the outgoing day still
              fading while the incoming one is already legible, which would mean the two legs are
              running together rather than one after the other.
      Also:   with Reduce Motion on, the new day is simply there, at its own height, on the frame
              after the tap.

- [ ] **PR 26 · ios · A drop is not a day swap** — the same screen and the same setup, on the day
      with three or more tasks. The pair with the row above: that one checks the `.id()` fires on a
      day change, this one checks it stays out of an edit within the day. The drop deliberately does
      not follow the task — `commitPendingReschedule` leaves `selectedDate` where it is — so a
      reschedule changes which rows are in this day and never which day is shown.
      Do:     drag one task off this day onto another date in the grid.
      Watch:  the dragged row alone leaves, fading over 150 ms, and the rows under it close its gap
              over 320 ms. The day around them holds still: the header, the grid and the card's
              other content are not redrawn and do not fade.
      Fails:  the whole list fading out and back — the `.id()` swap playing on an edit that belongs
              to the row transition, which would also mean every completion in this list plays it.
              Also a fail: the view jumping to the drop target's day, which is a different bug in a
              different file but looks like this row passing.
      Also:   with Reduce Motion on, the row is gone and the gap is closed on the next frame, and
              the day is still this day.
