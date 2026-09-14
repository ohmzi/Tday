# Phase 9 device pass — adoption, polish, confetti

Run against the release build that closes Phase 9. See `README.md` in this directory for the row
format and for how the file is closed out.


Phase 9 is the adoption phase, and most of what it changes is what the user sees in the frames
either side of an arrival — a skeleton handing over to content, an overlay taking a screen. Those
are the frames no gate here owns: jsdom computes no layout, so a vitest run can prove a skeleton
spells the row's classes and prove nothing about whether the page held still when the rows landed.
The rows below are the half that has to be looked at.

Phase 9 finishes adopting the vocabulary the earlier phases built, so most of its rows are
migrations whose whole claim is that nothing changed by eye. The exception is the confetti, and it
is the largest gap in the programme: the burst's physics were reconstructed on paper in
`docs/confetti-spec.md`, checked twice by arithmetic and by invariant tests, and have never been
seen moving. Unit tests can prove a piece's path is concave and that its fall settles rather than
accelerating; nothing in this repository can say whether the result reads as paper.

## Web

- [ ] **PR 40a · web · The feed does not move when the tasks arrive** — a phone, a list with 3+
      tasks on it, on a cold load (hard refresh, or throttle the network to Slow 3G so the
      skeleton is on screen long enough to look at).
      Do:     open the list and watch the point where the grey bars become real rows.
      Watch:  nothing below the header moves sideways or vertically at the handover. The bars sit
              flush against each other, at the same 20 px circle, the same left edge and the same
              row height the real rows land at, so the only change is grey becoming text.
      Fails:  the rows step down or up as they land; a gap between the placeholders closes; the
              text starts further right than the bars did; or the placeholder is a bordered card
              with a fill and the rows that replace it are flat.
      Known:  on Today and All only, the block ABOVE the rows is the page title drawn a second
              time, and the section label that replaces it ("Morning", a date) sits 18 px
              higher — a measured, pre-existing difference between `TodoListLoading`'s heading
              block and the sections' own. Expected here, and not what this row is asking
              about: the check is the ROWS holding still, so read the step above them as
              out of scope rather than as a failure.

- [ ] **PR 40a · web · The handover is a crossfade, not a swap** — a phone, the Anytime feed and a
      shared list, on a cold load with the network throttled to Slow 3G.
      Do:     watch the moment the grey bars become rows, on all eight surfaces the crossfade
              reaches: the Anytime root feed (`NativeFloaterTaskHomeDashboard`), an Anytime list
              (`FloaterListContainer`), the scheduled root feed, the Members sheet of a shared
              list, and then the four `TodoListLoading` screens — Today, All tasks, a scheduled
              list, and both Completed tabs.
      Watch:  the bars fade out over the rows fading in — both halves on one 200 ms clock — and
              the rows are in their final position from the first frame of it. The bars sit ON
              TOP of the arriving rows while they fade, never behind them.
      Fails:  the bars disappear in one frame; the page holds a block of empty space open for the
              length of the fade and then snaps shut; the rows are printed over the top of the
              grey bars instead of under them; anything below the feed steps when the last of the
              placeholder goes.
      Known:  the scheduled root feed had no loading state at all before this row — a placeholder
              appearing there where nothing used to be drawn is the fix, not a regression.
      Known:  the four `TodoListLoading` screens get the fade-OUT half only. What arrives on them
              is not one block — a timeline, three drop targets, an empty state and a pager are
              siblings there, and there is nothing to hang `.tday-content-enter` on that is not a
              wrapper invented for it. The rows appear at full opacity, in their final position,
              under bars that fade off them. A step under the rows as the last of the grey goes
              is still a failure; the rows not fading in is not.

- [ ] **PR 40a · web · Reduce Motion gets the content, not the wait** — the same eight surfaces
      with the OS "Reduce Motion" setting ON.
      Do:     cold-load each one and watch the same moment.
      Watch:  the rows replace the bars immediately, with no fade and no pause in front of them.
              The bars themselves still pulse while they are on screen — a busy indicator that
              has stopped reads as work that has finished.
      Fails:  a beat of nothing between the bars going and the rows arriving (the wait kept after
              the motion was removed); or the placeholder frozen mid-pulse while it waits.

- [ ] **PR 40a · web · The sidebar's account placeholder moves** — desktop, sidebar expanded and
      again collapsed to the rail, on a cold load.
      Do:     watch the two account blocks at the bottom of the sidebar before the user resolves.
      Watch:  both blocks pulse, at the same rhythm as every other skeleton in the app.
      Fails:  they sit perfectly still — which is how a control that has rendered empty looks, not
              how one still loading does.

- [ ] **PR 40a · web · The cold-start shell draws the row the route draws** — a phone, network
      throttled to Slow 3G so the lazy chunk takes a visible moment, on a hard refresh of
      `/app/tday` (and again of `/app`, which holds on the same shell while it decides where to
      send you).
      Do:     watch the three grey task-row bars at the point the shell becomes the real feed.
      Watch:  the bars are flat and flush — no card, no border, no fill, no gap between them —
              and the block of them is the height the three rows that replace it are. Three
              bars of grey, three rows of text, same block.
      Fails:  the placeholder is a bordered tinted card and the rows replacing it are flat; or
              the block of rows closes ~20 px as the spacing between three cards goes.
      Known:  the whole column still steps down at the swap, and this row is not asking about
              that. One shell is the fallback for every lazy route, so it stands a single 56 px
              header in for whichever one resolves; the root feed's own is a 64 px toolbar over
              a 78 px hero block under the safe-area inset — roughly 100 px lower — and its
              gutter is 4 px narrower on a phone (`px-4` against the shell's `px-5`). That is
              pre-existing shell geometry this unit did not touch and could not fix without a
              shell per route. Read the step as expected; judge the bars.

- [ ] **PR 41a · web · Every overlay dims the page by the same amount** — a phone, in light mode
      and again in dark, against the native apps on the same two screens if one is to hand.
      Do:     open, one after another, a bottom sheet (tap a task to edit it), a confirm dialog
              (delete a task), the right-hand `Sheet` (the list members panel), a `Modal`
              (the calendar's delete confirm) and a centred selector (the repeat picker inside
              an open sheet).
      Watch:  the page behind them goes exactly as dark each time, and as dark as iOS and Android
              go — 40% in light, 68% in dark. Five overlays, one shade.
      Fails:  one of them is visibly heavier or lighter than the others; the dialog and the sheet
              disagree; or dark mode looks unchanged from before, which would mean the token is
              resolving to nothing and the scrim is drawing transparent.
      Known:  the centred selector opens ON TOP of the sheet's scrim and the two compound, so the
              page under a selector is darker than under anything else. That was true before this
              row and is not what it is asking about — the vaul nested-scrim registry does not
              reach a Radix dialog. Judge the five on their own.

- [ ] **PR 41a · web · A sheet arrives and leaves on the ladder** — a phone, light or dark.
      Do:     open and close the right-hand `Sheet`, then the delete dialog, then the calendar's
              `Modal`, then the repeat selector.
      Watch:  the side panel slides in over about a third of a second and leaves faster than it
              arrived. The three centred surfaces fade and scale into place WITHOUT rising from
              below — they arrive where they already are. The `Modal` and the selector leave
              quicker than they came; the delete dialog leaves in the same time it arrived, which
              is the one rung it declares covering both its directions.
      Fails:  a centred dialog still travels up from the bottom on open; an exit takes longer
              than its own enter; or the calendar `Modal` fades out only part-way before the card
              is cut away, which is the JavaScript half of its exit having drifted from the CSS.

- [ ] **PR 39a · web · The burst is paper, and the apex is not cut** — any list with exactly one
      task left on it, so completing it empties the list and celebrates. Run it twice: once in a
      tall window, and once with the browser window short enough that the empty state is at its
      `min-h-[42vh]` floor (roughly under 640 px tall) — that second case is the one the canvas
      bleed exists for, and the only one where clipping shows.
      Do:     tick the last task and watch the space above the illustration rather than the row.
      Watch:  the pieces leave a thumb-sized patch at the centre rather than a single point; the
              cloud has all but stopped spreading by ~300 ms and the highest pieces turn over about
              a fifth of the card's WIDTH above the origin at ~415 ms, wholly inside the canvas;
              after the scene lands at ~840 ms everything left is falling at a steady speed rather
              than gathering pace, drifting ±7–15 px sideways at about 1 Hz and turning edge-on on
              a rhythm unrelated to how fast it is spinning; nothing is drawn after 2000 ms.
      Fails:  a piece is sliced along a straight horizontal line near the top of the card while
              still fully opaque (the bleed is not reaching, or the origin was not moved back down
              by it); the whole burst is thrown ~40 px too high (the bleed is in the canvas but not
              in the origin); pieces accelerate out of the bottom instead of settling; a piece goes
              flat exactly when it is side-on to its own travel, which is the flip locked back onto
              the rotation; or the burst ends on an edge you can point at instead of fading out.

## Android

- [ ] **PR 39b · android · The burst is paper, and it still fits the celebrate window** — any list
      with exactly one task left on it, so ticking that task empties the list and celebrates. Run
      it twice: once on a list-detail screen, where the empty scene is an overlay and the burst is
      thrown at once, and once on the floater home feed, where the scene is drawn inline and the
      burst is held 320 ms while the rows below it settle into their new places.
      Do:     tick the last task and watch the space above the illustration rather than the row.
      Watch:  the pieces leave a thumb-sized patch at the centre rather than a single point; the
              cloud has all but stopped spreading by ~300 ms and the highest pieces turn over about
              a fifth of the box's WIDTH above the origin at ~415 ms; the scene rises at 320 ms
              with paper already in the air above it; from ~840 ms everything left is falling at a
              steady speed rather than gathering pace, drifting ±7–15 px sideways at about 1 Hz and
              turning edge-on on a rhythm unrelated to how fast it is spinning; the last piece is
              gone 2000 ms after the throw, well inside `CompletionCelebrationWindowMs`.
      Fails:  pieces accelerate off the bottom of the screen instead of settling, which is the old
              parabola; a piece goes flat exactly when it is side-on to its own travel, which is
              the flip locked back onto the rotation; the burst ends on an edge you can point at
              instead of fading out; the fan clears the sides of the box, which would mean the
              speeds are being read as distances again; on the floater feed paper thrown across a
              Completed tile that is still sliding; or the window closing on paper still in the
              air, which is the 1800 → 2000 ms flight not fitting after all.
