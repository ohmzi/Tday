# Phase 7 device pass — the web calendar under a thumb

Run against the release build that closes Phase 7. See `README.md` in this directory for the row
format and for how the file is closed out.

Phase 7 is the web phase, and nearly every row in it is a height or a gesture: a card resizing
around a page that is sliding, a row's box shutting under its own fade, a month dragged across
under a finger. That is exactly the class jsdom cannot see. A vitest run proves the class is
applied, the inline style is written and the timer fires; it computes no layout and implements no
`Element.animate`, so it cannot tell a height that travels from a height that is simply the new
number. Hence the `V+D` on these rows — the `V` half says the instruction was issued, and only the
`D` half says anything moved.

Phase 7's iOS batch (PR 26) is not checked here. Cycles are spent at phase merges and there are
exactly three in the whole programme, so those rows write into the file for the phase whose merge
spends TF2 — see `README.md`, "iOS: three cycles, for the whole programme".

## Web

- [ ] **PR 25a · web · The card resizes with the page instead of under it** — the calendar on a
      phone, Month view, sitting on a month whose neighbour has a different number of weeks (a
      35-day month next to a 42-day one — February against March in most years).
      Do:     swipe to the next month (repeat: the header chevrons; and the view control, Month →
              Week → Day and back, which is the larger height change of the two).
      Watch:  one move. The grid slides across over 320 ms and the card's bottom edge travels to
              its new height over the same 320 ms, the two landing on the same frame, and whatever
              sits below the card moves once.
      Fails:  the card is already at its new height while the grid is still travelling over it —
              the defect, and most obvious going 42 → 35, where the box jumps up and leaves the
              outgoing page hanging. Also a fail: anything that legitimately paints outside the
              pager being cut off by the clip box this fix introduced — drag a task over a day cell
              and watch its 4px ring on all four sides; check the last column's day pill below
              about 390 px wide; check the glow under a selected day on the bottom row.

- [ ] **PR 25b · web · The calendar row's box shuts under its own fade** — the calendar on a phone,
      a day carrying 3+ tasks so there are rows below the one you tick.
      Do:     tick the first task's circle.
      Watch:  the tick lands, the strike runs at 160 ms, and at 520 ms the ink fades over 260 ms
              while the box closes underneath it over 320 ms — so the rows below have *travelled*
              up into the space by the time the row is pruned at 840 ms.
      Fails:  the row holds its full height while its ink fades and the gap closes on one frame at
              the prune, the rows below jumping. That is the defect. Also a fail the other way: the
              box shutting before the ink has gone, which reads as the row being yanked rather than
              finishing.

- [ ] **PR 25c · web · The floor answers a back swipe it cannot honour** — the calendar on a phone,
      sitting on the current month (the state it opens in), Month view.
      Do:     swipe backwards, past the 48px threshold, and let go. Repeat with the keyboard's left
              arrow on a desktop, which is the same refusal through a different input.
      Watch:  the grid resists — about 8px to the right and back over 150 ms, ending exactly where
              it started, with the month title and the chevrons not moving at all. Then swipe
              forward and back once to feel the difference: a real page turn is more than twice as
              long and actually changes the month.
      Fails:  nothing happens at all, which is the defect. Also a fail: a resist long or far enough
              to read as a page beginning to turn; the whole card, title included, moving with it;
              or the grid landing anywhere but where it started. With reduce-motion on, nothing
              happening is the correct answer — a refusal has no finished state to draw.

- [ ] **PR 25c · web · The dragged card lands instead of vanishing** — the calendar on a phone,
      Month view, a day with at least one task so there is a row to pick up.
      Do:     long-press a task row in the list below the grid, drag it up over the month grid, and
              release it over nothing — the header, or the gap beside the grid.
      Watch:  the card travels back to the row it came from over 320 ms, decelerating into place,
              and the row underneath reappears as it arrives.
      Fails:  the card disappears in the frame you lift your finger — the defect. Also worth a
              glance in the case the library does not always animate: drop onto a *different* day
              and check the card either flies to where its row was or simply goes, but never flies
              to the top-left corner of the screen.

- [ ] **PR 25c · web · The Delete tap is answered before its dialog exists** — the calendar on a
      phone, any task, with the network throttled to Slow 3G in devtools and the cache disabled, so
      the lazy chunk actually takes time.
      Do:     reload the calendar and tap a task's Delete immediately.
      Watch:  a scrim and a dialog-shaped card with two skeleton lines and two skeleton buttons
              arrive at once, in the place the real dialog will be, and the real dialog fills that
              card in when it lands. Tapping the scrim while it is still waiting closes it.
      Fails:  nothing on screen until the dialog appears, which is the defect. Also record — it is
              known and accepted, not a regression — whether the real dialog visibly re-plays its
              arrival after the placeholder; and a real fail if the placeholder's card is a
              different size from the dialog that replaces it.
