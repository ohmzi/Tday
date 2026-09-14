# Phase 9 device pass — the last one

Run against the release build that closes Phase 9. See `README.md` in this directory for the row
format and for how the file is closed out.

Phase 9 is adoption, polish and the confetti, so a larger share of its changes than usual are the
kind no gate can see: a burst is forty-six independent objects on a two-second clock, and the whole
question about it is whether it reads as paper. `xctest` can prove the model underneath is the one
that was designed — `TdayConfettiKinematicsTests` does — and it cannot prove the result looks like
anything at all. That gap is what these rows are.

## iOS

- [ ] **PR 39c · ios · The burst is paper, not a diagram** — any list with exactly one task left on
      it, so completing it empties the list and raises the celebrating empty state. Worth doing on a
      short screen (a small phone, or the Calendar day card) as well as a tall one: the pieces fly
      above the scene and the overlay is not clipped, so a short box is where an apex that is too
      high would show.
      Do:     complete that last task and watch the paper rather than the illustration. Then do it
              again and watch only the first 300 ms; then a third time and watch only the last
              500 ms.
      Watch:  three beats. The pieces leave a patch about a thumb wide — not one point — and over a
              third of the throw is spent in the first 150 ms, so it reads as a snap. The cloud has
              nearly stopped growing by ~300 ms, and the highest pieces turn over at their apex
              around 415 ms, by which time the empty-day scene has started rising underneath at
              320 ms. From ~900 ms everything left is falling at its own steady speed, heavier
              pieces ahead, each swaying about a centimetre either way roughly once a second and
              leaning into the swing. The fade starts at 1.2 s and there is nothing at 2 s.
      Fails:  pieces travelling outward at a constant speed and leaving the sides of the box — the
              starburst diagram this replaces, and the single thing the change is for. Also a fail:
              the fall visibly accelerating all the way down, so the last pieces shoot off the
              bottom; forty-six pieces swaying in unison, which is one sway rate rather than
              forty-six; the pieces turning edge-on in lockstep with their own rotation, which is
              the old mechanical wobble; and a hard edge at the end where the whole cloud switches
              off on one frame instead of thinning out.
      Also:   with **Settings → Accessibility → Motion → Reduce Motion** on, complete the last task
              again. There is no burst at all, and the empty-day scene is simply there at its
              resting position — no lead, no wait where the burst would have been. The list is still
              finished, and the screen must say so on the next frame.

- [ ] **PR 40c · ios · The feeds load into rows, not a blank frame** — needs a genuinely cold feed,
      so the request is slow enough to see: airplane mode off but a throttled connection, or the
      dev server paused for a second. Three screens, and all three: Today (the scheduled home), a
      task list, and Completed.
      Do:     force-quit, reopen, and watch each feed from the first frame. Then pull-to-refresh a
              feed that already has rows on it.
      Watch:  three grey rows in the slot the real rows are about to take, breathing. When the data
              lands they dissolve while the rows come up — one hand-over, both halves on one clock.
              A pull-to-refresh over existing rows shows the pill and keeps the rows; it must never
              replace them with grey bars.
      Fails:  the pulse reading as a flicker rather than as breathing — it is a 260 ms tween that
              autoreverses, so a round trip is about half a second, and this row is the only thing
              that can say whether that is right; a seam in the middle of the hand-over, where the
              bars are gone before the rows are solid or still showing behind them; and grey bars
              appearing under a search query the user is typing.
      Height: the placeholder is built at the metrics of the row it stands in for, and there are two
              such rows — Today's, and the timeline row a task list and Completed share, which is
              8 pt of vertical padding to Today's 10 and hangs its toggle off the title's first
              baseline. So on each of the three feeds, at the moment the content lands, the rows
              must come up at the height the bars were and nothing may resize by a line. Worth a
              second pass at the largest Dynamic Type, where the bars are sized from the live font
              and any remaining mismatch is at its widest.
      Slot:   on Today the bars are stacked over the rows, so **nothing above or below them moves**
              — the category board under the feed must be perfectly still through the whole
              hand-over. A task list and Completed cannot be stacked the same way: their placeholder
              is a `Section` above the row sections, and whether the feed reflows as they swap
              depends on whether `List` resolves the change as a SwiftUI removal (which holds the
              bars' height for the length of the dissolve, so everything below is shoved down and
              snaps back) or as a UIKit batch update (which animates both halves into their final
              places at once). Source cannot settle that and this row is where it gets settled: if
              the two List feeds shove and snap, say so — the fix is to stop removing the section
              and start collapsing it, and it is a follow-up, not a tweak.
      Also:   with **Settings → Accessibility → Motion → Reduce Motion** on, cold-open each feed
              again. The placeholder is there, fully drawn and perfectly still — never parked at the
              faded end of its own pulse — and when the data lands the rows are simply there on the
              next frame. No fade, and no wait where the fade would have been.
