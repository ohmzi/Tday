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
spends TF2 — `phase-8-device-pass.md`, which those rows open. See `README.md`, "iOS: three
cycles, for the whole programme".

## Web

- [ ] **PR 21 · web · The month is under the thumb, not behind it** — the calendar on a phone,
      Month view, standing on any month but the current one, so both directions are live.
      Do:     start with the thumb ON a date, not in the gaps between the rows — a month grid is
              seven columns of date buttons, so that is where a thumb lands and it is the case
              that matters. Drag the grid sideways slowly and hold there; carry on past the 48px
              threshold and keep going, a whole card width if you can; then let go. Repeat,
              releasing well short of the threshold, and check the date you started on did not get
              selected. Tap a date on its own afterwards to be sure taps still land. Then drag,
              release short, and catch the grid again before it has finished coming home. Last, with
              one thumb holding a drag half-way, put a second thumb down on a date and lift both.
      Watch:  the grid is under the thumb from the first pixel, one for one, until the threshold.
              Past that it keeps answering but gives — a card width of thumb buys about 96px of
              grid and no more. Released past the threshold, the grid's own offset is dropped and
              the next month slides in over 320 ms: one movement, not a glide home followed by a
              page turn. Released short, it glides back over 150 ms and the month does not change.
              The month title, the chevrons and the S M T W T F S row stay still throughout — only
              the dates travel. The re-grab is the fourth thing to look for: the grid should carry
              on from exactly where your thumb caught it, never jump back out to where you let it
              go. After the two-thumb case the grid must end up square again, either way.
      Fails:  nothing moving until the release, which is the defect — the whole gesture was a
              measurement before it was a movement; and nothing moving only when the drag starts on
              a date, which is the same defect wearing the gesture's own clothes. Also a fail: the
              month title, the chevrons or the weekday row travelling with the dates; the date you
              began a swipe on ending up selected; the grid left sitting out of place after any
              release, the two-thumb one included; a release past the threshold playing both a
              glide home and a page turn; or the grid tracking sideways while you scroll the task
              list below it, which the axis lock is there to prevent — start a scroll from inside
              the grid to check that one.
      Judge:  the hand-off at the moment you let go is a cut, and a deliberate one. The grid is
              carrying up to 96px of your drag; the arriving month starts from the far side and
              moves the way your thumb was going, so the content crosses that offset in a single
              frame before the 320 ms plays. Web keeps one page in the DOM, so the alternative is
              an arrival that travels BACKWARDS to centre — the same movement a refused swipe
              makes, which would say the opposite of what happened. Call it if the jump reads as a
              glitch rather than as the page changing hands; the argument is at the `restHome`
              call site in `useCalendarPagerSwipe.ts`.
      Also:   with reduce-motion on, the tracking stays and the trip home goes: the grid still
              follows the thumb, and lands in the frame you lift it. The page turn itself is a cut
              under that preference, as it already was.

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
      Note:   since PR 21 the grid also gives under the thumb on the way there — about 24px at the
              very most, half of what it takes to turn a page, however far backwards you drag. That
              is the same refusal said twice, and the 8px answer is played on top of the grid coming
              home from it; the two are one movement to the eye. The keyboard gets the 8px alone,
              which is what it is for.
      Fails:  nothing happens at all, which is the defect. Also a fail: a resist long or far enough
              to read as a page beginning to turn; the grid tracking the backwards drag one for one
              the way a forward drag does; the whole card, title included, moving with it; or the
              grid landing anywhere but where it started. With reduce-motion on, nothing happening
              after the release is the correct answer — a refusal has no finished state to draw.

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
              card in when it lands. Tapping the scrim while it is still waiting closes it the way
              the real dialog closes — the card fades and settles downwards over 200 ms rather than
              being cut away under your finger.
      Fails:  nothing on screen until the dialog appears, which is the defect. Also a fail: the
              placeholder gone in the frame you tap the scrim, which would be the same silence at
              the other end of the same gesture. And record — it is known and accepted, not a
              regression — whether the real dialog visibly re-plays its arrival after the
              placeholder; a real fail if the placeholder's card is a different size from the
              dialog that replaces it.

- [ ] **PR 20 · web · A task row hears the flick, and gives at its ends** — any screen that lists
      tasks on a phone: Today, a scheduled list, the calendar's day list, and the Anytime feed.
      Check at least the Anytime feed and one dated list, because those two rows were copies of
      each other until this PR and are one hook now.
      Do:     flick a row left — a short, fast throw of about a thumb's width, released while it is
              still moving — and let go well before the actions are uncovered. Then drag a second
              row slowly past halfway, pause, and lift. Then drag a third slowly past halfway and,
              without lifting, walk it back a little before you let go. Then, with a row open, keep
              dragging left past the Delete pill; and with a row closed, drag it to the right.
      Watch:  the flick opens the row: it carries on to the full 210px and settles over 320 ms.
              The slow drag past halfway also opens — a pause before the lift is a decision not to
              flick, and position decides it. The one that was being walked back closes again over
              150 ms, which is visibly the shorter of the two. At both ends the row keeps answering
              the finger and stops answering it in proportion: about 26px at most, however hard you
              pull, and it returns the instant you let go.
      Fails:  the flick closing the row again, which is the defect — the row was well short of
              halfway and the old rule could only see that. Also a fail: a row that stops dead at
              either end while the finger keeps moving; a row leaving the rubber band and never
              coming back; the give being large enough to read as a fourth action arriving behind
              Delete; or the two settles being indistinguishable from one another, which would mean
              a row shuts as slowly as it opens.
      Also:   the highlight a deep link leaves on a row now fades on all three rows rather than on
              the calendar's alone. Open a task from a search result or a notification on the
              Anytime feed and check the ring arrives rather than cuts.
      Also:   with reduce-motion on, a released row is at its resting place in the frame you lift
              your finger — never part-way, and never travelling. It still follows the finger while
              the finger is down.

- [ ] **PR 20 · web · The calendar turns a page on a flick** — the calendar on a phone, Month view,
      standing on a month with pages live in both directions.
      Do:     flick the grid sideways — fast, and released after well under the 48px the threshold
              asks for. Then drag the grid a long way past the threshold, walk it back towards the
              middle without lifting, and let go. Then tap twenty-odd day cells at speed, the way
              a thumb picks a date — quick, careless taps, not careful ones.
      Watch:  the flick turns the page. The drag that was being walked back does not: the grid
              glides home over 150 ms and the month is unchanged. The taps select their dates and
              nothing else: the month stays put, and the card never shakes.
      Fails:  a flick that has to be dragged the full 48px before it counts, which is the defect on
              a surface a whole card wide. Also a fail: a drag you have already changed your mind
              about turning the page anyway; or a flick turning two pages. And a tap turning one —
              a projection is 150 ms of travel the finger never made, so a few pixels of contact
              jitter can clear the threshold arithmetically while being no gesture at all. Standing
              at the navigation floor the same press shows itself differently, as the refusal
              shake playing for a tap; both are the same fault and either one is a fail.

- [ ] **PR 52 · web · Picking a task up and putting it down** — a phone, with tasks on the
      calendar's day list, the timeline's date sections, and Today's Morning/Afternoon/Tonight
      buckets. All three, because until this PR the calendar had a landing and the other two did
      not, and the pick-up was missing from all three.
      Do:     press and hold a task until the card lifts, carry it a little without dropping it,
              then drop it on another day / date section / bucket. Repeat on each of the three
              screens.
      Watch:  the card GROWS out of the row over 320 ms as the shadow arrives under it — about 3 %
              bigger, which is the same travel a card sinks by when you press it. The card is
              opaque the whole time; the row it left is the only thing at 70 %. On release the
              card TRAVELS to where the task lands over 320 ms rather than disappearing where
              your finger was.
      Fails:  the card appearing at full size with its shadow already cast, which is the defect
              and is easiest to catch by picking up and immediately letting go. Also a fail: the
              card reading as greyed-out or half-there while you carry it; a drop that cuts on
              the frame you release; the three screens disagreeing with each other; or the card
              visibly jumping in size at the instant it lands, which would mean the lift outlived
              the landing.
      Also:   with reduce-motion on, the card is already big and already casting in the frame it
              appears, and a release removes it immediately. Never a card sitting flat in mid-air,
              which is the rise pinned at its first frame rather than its last.

- [ ] **PR 53 · android · The drag preview lifts instead of fading** — the timeline (a scheduled
      list with several dated tasks) and the calendar's day list, on a device.
      Do:     long-press a task until the preview card appears, hold still for a second, then move
              it and let go.
      Watch:  the preview rises as it appears — from flat to a real drop shadow, and from the
              row's size to about 3 % over it, across 320 ms. It is fully opaque. The row you
              pressed dims to 70 % over that same 320 ms, so the card leaving and the slot
              emptying are one movement.
      Fails:  the preview arriving whole — full shadow, full size, in one frame — which is the
              defect. Also a fail: a preview you can see the list through, which is what 88 %
              alpha looked like and reads as a task you may not have; the row snapping to 70 % on
              the frame the long press fires while the card rises behind it, which is one gesture
              read as two events; or the two screens lifting by different amounts.
      Also:   with Settings → Developer options → Animator duration scale set to Off, the preview
              appears already lifted and the row is already dimmed. A card drawn flat and small
              under the finger would be the fifth idiom rule broken the usual way round.

- [ ] **PR 49 · web · The calendar form arrives once, in its own shape** — the calendar, on a phone
      and on a desktop browser, with the network throttled (DevTools → Network → Slow 3G) and the
      cache disabled, because the placeholder this is about only exists on the first open of a
      session. Hard-reload before each attempt; the second open renders the body synchronously and
      shows nothing.
      Do:     tap an empty slot to open the new-task form, on a phone first and then on a desktop
              window comfortably wider than 640px. Then open an existing task for editing on both.
              Repeat each one once more without reloading, to see the warm path.
      Watch:  on desktop, a centred modal card — the real one, with the Cancel / New task / Save
              header already in it — and skeleton rows filling the body underneath while the chunk
              lands. On a phone, the bottom sheet, same story. The surface slides in ONCE and then
              stands still: the skeleton rows are replaced by the title field, the notes area and
              the Schedule / Details rows without the card moving, resizing or re-entering. The
              "Schedule" and "Details" titles are already the real words before the body arrives
              and do not change when it does. On the warm second open the form is simply there.
      Fails:  a bottom sheet sliding up from the bottom of a desktop window for a form that then
              appears as a centred modal — the first defect, and the most visible of the two. And
              the second: the sheet arriving, settling, then being swapped for a sheet that plays
              the same entrance again, which on Slow 3G reads as the form flickering or bouncing
              once before it is usable. Also a fail: the card visibly jumping in height as the
              skeleton rows give way to real ones, which would mean the placeholder is not built
              from the chrome the body lands into.
      Also:   with reduce-motion on, the surface is simply present rather than sliding, and the
              handover is still invisible — a finished form is the finished state either way.

- [ ] **PR 54 · web · Every button answers the finger the same way** — the app on a phone, anywhere
      with a shadcn `Button` and a hand-rolled one in the same view: a form sheet's Cancel / Save
      pair beside the round header buttons, or the calendar's Today pill beside its chevrons.
      Do:     press and HOLD each button in turn, a full second, and watch the moment the finger
              lands rather than the moment it lifts. Then lift, and watch the way back up. Do it
              on a Save button inside a sheet, on a round header button, and on the onboarding
              card, which presses deeper than the rest on purpose.
      Watch:  the surface goes down into the press — it should be possible to see it travel, not
              just find it already down. Every button travels the same way and on the same curve,
              whichever kind it is, and comes back the same way on the lift. The ripple blooms out
              from under the finger over the same beat it always did. The onboarding card still
              goes visibly deeper than a sheet button does.
      Fails:  a button that is already squashed on the frame the finger lands — the defect, and it
              is easiest to catch beside one that is not, which is why the two kinds have to be in
              the same view. A jolt down under a ripple that then blooms slowly is the exact
              before-state. Also a fail: the onboarding card pressing to the same shallow depth as
              everything else, which would mean the pressed scale was promoted out of reach of the
              call sites that set their own.
      Also:   with reduce-motion on, the pressed state is simply there on contact and gone on
              release, with no ripple at all — including on a Save button, which is the half that
              used to go on animating under the setting because its own `transition-colors`
              outranked the floor.
