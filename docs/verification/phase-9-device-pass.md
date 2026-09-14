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
seen moving. A burst is forty-six independent objects on a two-second clock, and the whole question
about it is whether it reads as paper. Unit tests can prove a piece's path is concave and that its
fall settles rather than accelerating — `confetti-kinematics.test.ts`, the JVM I1–I6 suite and
`TdayConfettiKinematicsTests` each do — and nothing in this repository can say whether the result
looks like anything at all. That gap is what these rows are.

The migrations are the narrower gap and the same kind of gap. Most of them are an easing swapped
at an unchanged duration, or a surface that was cut and now moves: a counter reads the same number
for either curve, and a guardrail that proves a spec exists proves nothing about whether it plays.
So the rows here lean hard on `Fails:`, because the wrong version of almost every one of them still
animates.

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

- [ ] **PR 40d · web · The pager speaks when the rows land** — the Overdue screen (or Today) on an
      account with more than twenty tasks in that scope, so the feed pages at least twice. A screen
      reader is the whole point of the row: VoiceOver on Safari, or NVDA on Firefox.
      Do:     open the screen with the reader running and listen through the first frame without
              scrolling. Then scroll until the strip is on screen and the next ten rows are
              revealed, and keep going to the end of the feed. Then complete or delete a task and
              listen again. Then switch the app's language to French and to Japanese in Settings
              and repeat the scroll.
      Watch:  on the frame the ten rows land, the reader speaks a count — "Showing 20 of 25 tasks"
              — politely, so it waits its turn rather than cutting the row the reader is on, and
              in the app's language. The last page is spoken too, on the true total, which is how
              the reader learns the feed is complete. The strip itself reads "Scroll for more" in
              the same language: there is no request behind it, so it must not claim to be
              loading.
      Fails:  silence while ten rows are inserted above the strip, which is the defect this row
              exists for — and the one a passing `tests/unit/timeline-sentinel.test.tsx` cannot
              rule out on its own, because whether a reader voices a change inside a `role="status"`
              node is a browser/reader question and not a DOM one. Also a fail: a count spoken at
              mount, before anything has been revealed; a count spoken after completing or
              deleting a task, when nothing was revealed and the toast has already said what
              happened; and the sentence cutting into the row the reader was on, which would mean
              the region is not polite.
      Note:   the unit test pages the real container through its own observer and asserts the
              region's text CHANGED across the page-in, so the one thing that cannot be faked
              locally is whether that change is voiced. This row is that half.
      Left:   the strip is still 48 px of empty chrome above ten rows arriving in one frame. That
              is now its own ledger row (`web-infinite-scroll-skeleton`). PR 40a has since landed,
              so its `TaskRowSkeletonGroup` is in the tree — the strip simply does not draw it yet,
              so there is still nothing to check here, and the row above is not asking about it.

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

- [ ] **PR 40b · and · The feed hands over from a skeleton instead of from the word "Loading"** —
      a cold start on the root feed's flat modes (Anytime, a list feed, Priority — anything that
      is not the sectioned Today timeline) and on Completed. Force-stop the app first, and do one
      pass in airplane mode so the placeholder is on screen long enough to look at.
      Do:     open the screen and watch the first paint, then watch the frame the tasks arrive on.
              Repeat both on Completed. Then turn Settings → "Reduce motion" on (or the system's
              "Remove animations") and do it again.
      Watch:  three grey rows in the feed's own shape — a 48 dp circle, two bars where the title
              and the subtitle go, a hairline under each — breathing gently between full strength
              and faint. When the data lands the placeholder fades AND retracts together over
              200 ms while the real rows take the space: the feed should slide up into the slot,
              not appear and then jump. TalkBack should announce "Loading…" once when the
              placeholder appears.
      Fails:  the placeholder sitting at full height while it fades and the feed then jumping up
              by three rows — that is the fade running without the shrink. Also a fail: the rows
              landing at a different height than the bars they replaced, which means the skeleton
              and `TodayTodoRow` have come apart on geometry. Also a fail: the placeholder
              flickering rather than breathing (a pulse restarting instead of reversing). Also a
              fail, and the one only the Reduce-motion pass can see: the placeholder sitting at
              45% opacity instead of full strength, or the skeleton being cut away instead of the
              feed simply being there. Also a fail, and the one that outlives the load: a strip of
              dead space left under the header once the rows have settled — about one row-gap
              wide, and still there a minute later. That is the placeholder's lazy item left
              mounted after its exit finished, and a spaced `LazyColumn` charges for it whether or
              not it draws anything.

- [ ] **PR 41b · and · The sheet scrim leaves with its card** — the root feed, with the create-task
      sheet open and the keyboard dismissed (tap the sheet body once so the IME is down; the
      keyboard's own unwind is a second clock and this row is not about it).
      Do:     dismiss the sheet by tapping the dim area above it. Then repeat on the create-LIST
              sheet — root feed → the Lists section's add control — which is a second copy of the
              same chrome and the one most likely to be left behind by a later edit.
      Watch:  the dim FADES as the card travels, rather than holding full strength and blinking
              out with the window. It finishes first, by design: 200 ms of dim under 260 ms of
              card, which is iOS's own 0.20 scrimOut under its 0.24 cardOut. So the last ~60 ms
              of the slide plays over an undimmed background, with the card itself nearly faded
              out by then — it should read as the sheet leaving, not as the background coming
              back early. Opening is the mirror: the dim fades up over 200 ms under a card that
              takes 320 ms to arrive.
      Fails:  the dim CUTTING instead of fading — full strength to nothing on a single frame.
              Also a fail, and the defect this row exists for: the dim still standing after the
              card has reached the bottom edge. That is what the tree did before, because the
              scrim was drawn and undrawn with the `Dialog` window — it arrived ahead of the
              card and could not leave until the host tore the window down, which waits for the
              card's exit to settle. Also a fail: the card taking visibly LONGER to leave than
              it took to arrive.

- [ ] **PR 41b · and · The create sheets arrive and leave on different curves** — same two sheets,
      keyboard down.
      Do:     open a sheet, watch it arrive, dismiss it by tapping the dim, and watch it leave.
      Watch:  the arrival covers most of its travel early and settles into the bottom edge
              (decelerate, 320 ms); the departure starts slowly and is moving fastest as it goes
              off screen (accelerate, 260 ms). Two different shapes, and the exit is the shorter
              of the two.
      Fails:  both directions read the same — a symmetric ease at both ends, which is what the tree
              did before and is invisible to every gate the repo has. Also a fail: the departure
              floating away and easing to a stop at the edge, which is the arrival's curve running
              backwards.

- [ ] **PR 41b · and · Create and Save leave the sheet the way the X does** — the root feed, the
      calendar day list, a list feed and Completed, both sheets each time.
      Do:     open the create sheet, type a title, tap Create. Then open a task's edit sheet and tap
              Save. Then do the Save that MOVES the task out of the feed under it: on the root feed,
              edit a task due today to be due tomorrow; on a list feed, change the task's list.
              Then the create-LIST sheet — root feed → the Lists section's add control — with a name
              typed, and tap Create.
      Watch:  the card slides down and the dim fades with it, exactly as it does from the X. The
              relocating Save is the one this row exists for: the row vanishes from the feed behind
              the sheet, and the sheet still finishes its slide over the feed it left. On the
              create-LIST sheet the name stays in the field and Create stays lit for the whole
              slide.
      Fails:  the sheet DISAPPEARING on the tap, with or without the dim — that is the cut, and it
              is what every confirm did before. Also a fail, and the harder one to catch: the slide
              starting and then being cut part-way, which is the host tearing the composition down
              mid-exit. Also a fail: the create-LIST name blanking out or Create greying while the
              card is still on screen.

- [ ] **PR 41b · and · A confirm that is already leaving cannot be confirmed again** — the root feed
      and the create-LIST sheet; also worth one pass on a widget's "+".
      Do:     type a title, then DOUBLE-TAP Create as fast as the device will take it, aiming at
              where the button is rather than where it has slid to. Repeat on the create-LIST sheet
              with a name typed. Then the other order: tap the X and, while the card is still on
              screen, tap where Create was. Count what the feed has afterwards each time — and check
              a second device on the same account, since a duplicate is queued as its own create and
              syncs.
      Watch:  one task per double-tap, one list per double-tap, and nothing at all created by the
              tap that follows an X. Create stays LIT through the slide on purpose; it is refused,
              not greyed.
      Fails:  two tasks, or two lists of the same name, from one double-tap. Also a fail: the task
              the user just cancelled with the X appearing anyway. Also a fail in the other
              direction — Create going grey or the card stopping under the finger, which would mean
              the refusal is being drawn instead of just applied.

- [ ] **PR 41b · and · The widget create sheet does not lose the task it is animating out** — a
      Today widget and a Floater widget on the home screen.
      Do:     tap the widget's "+", type a title, tap Create. Repeat on a cold start, with the app's
              process killed first (Settings → force stop), so the write is as slow as it gets.
              Then repeat with the device in airplane mode.
      Watch:  the sheet slides out, the activity leaves with its own exit, and the task is in the
              widget when it repaints. The activity must go with the card and not with the write:
              the moment the card has gone, the home screen underneath must take a tap — try one
              immediately, on another widget or an app icon, in airplane mode where the sync has the
              longest to run.
      Fails:  the task missing from the widget and from the app afterwards — that is the write being
              lost with the window. Also a fail: the sheet cut on the tap (exiting on the write
              alone). Also a fail, and the one that shows nothing: a tap landing on nothing after
              the card has gone, which is the activity's own transparent window still standing there
              waiting for a sync.

- [ ] **PR 9b · and · The hero circle buttons travel instead of blinking** — the root feed, for the
      "Create list" and "More" circles in the header and then the close circle inside the expanded
      search capsule, and any list-detail screen for the back chevron.
      Do:     press and HOLD each of the four, watching the circle rather than the ripple, then let
              go. Repeat with Settings → "Reduce motion" on (or the system's "Remove animations").
      Watch:  the circle shrinks and sinks about 2 dp while the finger is down, and comes back when
              it lifts — a short trip each way rather than a state change, and one that no longer
              finishes before the ripple has started. These are bar buttons, so the squash is 0.94
              where they used to type 0.93 for themselves; that difference is a hair and the travel
              is the point.
      Fails:  the squash landing in one frame and leaving in one, which is the old `if (pressed)`
              inside `graphicsLayer` — it reads as a blink under a ripple still fading. Also a fail:
              the circle sinking without shrinking, or shrinking about a corner rather than its own
              centre, which is the offset and the layer applied in the wrong order. Also a fail, and
              only the Reduce-motion pass can see it: no press showing at all, which would be the
              destination thrown away along with the trip, or a press that still takes time to
              arrive.
      Known:  the compact close button inside the search capsule is transparent and unelevated by
              design, so its press is the squash and the sink with no shadow moving behind it.
              The search capsule itself — the wide button sitting between the title and the two
              circles — is not one of the four. It is still on plain `Card` elevation with no press
              scale, so it ripples and flattens and does not travel. Expected: no Phase 9 unit
              migrates it.

- [ ] **PR 9b · and · The create button answers a finger, and it does not jump at sign-in** — both
      root feeds (scheduled and Anytime), then the car surface, then the onboarding wizard.
      Do:     press and HOLD the blue "+" circle in the bottom-right of each root feed, watching the
              circle rather than the ripple, and let go. Repeat on the car surface, where the same
              button is drawn at a forced size in a row of controls. Then sign out far enough to
              reach the onboarding wizard, look at the blurred feed behind it, and sign back in
              watching the bottom-right corner through the hand-over.
      Watch:  the circle shrinks and sinks about 2 dp under the finger and comes back when it lifts
              — the same trip the header circles make, a little deeper: a FAB presses to 0.93 where
              a bar button presses to 0.94. Behind the wizard the backdrop is the app's own layout:
              the dock in the bottom-left AND one "+" circle in the bottom-right, both inert. Signing
              in changes the feed under that circle while the circle itself stays put.
      Fails:  the button not moving at all, which is the bug this row closes — it was handed an
              interaction source and never read it. Also a fail: the button jumping sideways or up
              as it presses, or the navigation-bar gap under it changing, which would be the press
              applied inside the padding instead of outside it. On the car surface, the circle
              changing the spacing of the controls beside it. Behind the wizard, a bottom-right
              corner with NO "+" circle in it. And at sign-in, the circle popping, sliding or
              flashing twice as the two feeds cross-fade — that would be the backdrop's button
              sitting at different padding from the live one.
      Known:  the two root feeds draw the same button from the same place, so they cannot disagree;
              if one presses and the other does not, the fault is above this button. On the car
              surface the button carries a forced `FabSize`, so the press has to move the drawing
              inside that fixed slot and nothing else. The backdrop's circle is a real
              `RootCreateTaskButton` and will press under a finger even though it opens nothing;
              that is cosmetic and not a fail.

- [ ] **PR 9b · and · The leaf screens' bar buttons travel, and a disabled sheet action still does
      not** — Guide, Calendar, Completed, Latest release and Settings for the round buttons in the
      top bar; then any sheet with a confirm in its header, and the "Manage members" sheet for the
      row-shaped action.
      Do:     press and HOLD each round button in the top bar of Guide, Calendar, Completed, Latest
              release and Settings in turn, watching the circle rather than the ripple, and let go.
              On Calendar also hold the purple "Today" pill twice: once with the title down, where
              it shows the word, and once after scrolling the title up, where it has shrunk to a
              circle. Then open a sheet and hold its confirm action with the sheet in a state that
              allows confirming, and hold it again with that action greyed out. Finally open
              "Manage members" and hold one of the wide action rows. Repeat the round buttons with
              Settings → "Reduce motion" on.
      Watch:  the circle shrinks and sinks about 2 dp while the finger is down and comes back when
              it lifts — a short trip each way rather than a state change. The "Today" pill makes
              the same trip without changing width, in either of its two shapes. The sheet's
              confirm travels 1 dp rather than 2 — it is a bordered square inside the sheet's own
              chrome, and iOS sinks its toolbar buttons a point for the same reason — and its
              shadow softens as it goes. The members row squashes and does NOT sink.
      Fails:  a button that does not move at all, or one that shrinks without sinking, or one
              whose drop gets smaller as it squashes — that last is the sink drawn inside the
              scale layer rather than outside it. Also a fail: the greyed-out confirm moving at
              all, which is the `enabled` gate gone; it must stay exactly where it is however hard
              it is held, while the enabled action beside it travels. Also a fail: the "Today"
              pill's label jumping or its width snapping as the finger lands, which would be the
              press applied outside `animateContentSize` rather than inside it. Also a fail: the
              members row dropping 2 dp, which would be the default sink arriving at a site that
              never had one. And under Reduce motion, a circle that shows no press at all, or one
              that still takes time to arrive.
      Known:  the buttons press a hair less deeply than they used to — 0.94 where each of these
              screens typed 0.93 for itself — so they now match the hero circles migrated ahead of
              them instead of sitting a hundredth deeper for no reason. That difference is not
              something to look for; the travel is. The sheet action's elevation still steps
              2/8/5 dp across pressed, enabled and disabled, which is its own animation and not
              this one.

- [ ] **PR 9b · and · Four classes of surface press to four different depths** — this is the row
      that says whether the press vocabulary was worth having. One device, one sitting, all five
      surfaces in a row so the depths are compared against each other rather than each against a
      memory.
      Do:     press and HOLD, in this order, letting go between each: a category card on the root
              feed; a list row under it on the Floater home; the round create button at the bottom
              of a task list; a "Members" or "Share" tile in a list's settings sheet, and the
              "Delete list" bar under them; then each end of a segmented slider — first the segment
              already selected, then the one that is not. Repeat the lot with Settings →
              "Reduce motion" on.
      Watch:  four depths, and they must be TELLABLE APART held side by side. The create button
              travels furthest and is the only thing on screen with nothing beside it to be
              measured against. The category card and the settings tiles sink less. The list row
              and the slider barely move at all — a full-width row that travels reads as the list
              shifting. The card and the row both drop about 2 dp as they shrink; the two settings
              tiles and the delete bar squash WITHOUT sinking.
      Fails:  four surfaces that all look like one number, which is the defect this closes coming
              back. A settings tile dropping while the tile beside it holds still — the pair must
              stay aligned, so neither sinks. A card or a row whose shadow no longer softens as it
              goes down: the lift is `cardElevation`'s two ends now rather than a hand-animated
              third leg, and if it has stopped moving, that hand-off is what to look at. On the
              slider, the selected segment's label sliding against the pill under it as the finger
              lands — label and pill are on one depth now and must travel as one object — and the
              UNSELECTED segment's halo, which grows INTO view and is deliberately not a press;
              it should still bloom outwards, not sink.
      Known:  the slider's segment label presses a half-hundredth shallower than it did, onto the
              selector's own depth. Not something to look for on its own; the label-against-pill
              question above is. The slider is also the one surface here that reads no motion
              preference at all: both its press scales keep an explicit spring because each is
              coupled to the offset that slides the selector, so with "Reduce motion" on the label
              and the pill still take that spring's time to get down. Expected, not a fail. The
              card's and the row's shadow is `cardElevation`'s now, which Material animates on a
              spec of its own that the preference does not reach, so the lift keeps travelling
              while the scale and the sink snap.
      Also:   with **Reduce motion** on, the four surfaces on the shared press modifier — the card,
              the row, the create button, and the settings tiles with the delete bar — arrive
              pressed on the next frame and leave on the next frame: no travel, and exactly as far
              down as before. One that shows no press at all is a fail; so is one that still takes
              time to get there. The slider is not one of the four — see Known, and do not log it
              against this line.

- [ ] **PR 42b · and · The account forms grow instead of being cut open** — Settings → Account, on a
      signed-in account that has security questions configured, so all three forms have something
      to draw.
      Do:     open Change name, close it; then Change password, close it; then Change security
              questions, close it. Watch the form's FIRST field rather than the button that opened
              it, then do it again watching the bottom edge.
      Watch:  the first field is pinned under the header from the first frame and does not travel —
              the form grows DOWNWARDS out of its header over 320 ms, and the edge that moves is the
              bottom one. The alpha rises WITH the growth, so the revealed edge is never a hard cut.
              Closing folds it back up into the header over 150 ms — shorter than the open, and it
              should not be worth watching.
      Fails:  the form's LAST row (Save/Cancel) arriving first and the first field's label sliding
              down into place last — that is the bottom anchor this closes, and it is the one thing
              to look for. Growth with no alpha under it, so a hard edge travels down the outline
              of a field: the expand running without the fade. Alpha with no growth — the form at
              full height and only then fading. Or a close that takes as long as the open.
      Known:  `expandVertically` clips, and it still does: the form is revealed through a cut, not
              drawn overflowing. So the bottom edge does pass across whatever is at it, and at some
              instant that is a field outline or a line of text. The fade is what keeps that from
              reading as a saw — it is the moving edge, at low alpha, arriving. A glyph momentarily
              incomplete at the BOTTOM edge is expected; one sliced at the TOP, under the header,
              is the fail above.

- [ ] **PR 42e · and · The calendar's mode switch crosses instead of cutting** — Calendar, with
      several tasks plotted across the month so the month card and the week strip are visibly
      different heights.
      Do:     swipe the month grid one month forward FIRST, so the visible month is not the selected
              date's, then tap Week, then Month, then Week again, watching the middle of the card
              rather than the tabs above it. Then turn the app's Settings → "Reduce motion" on (or
              the system's "Remove animations") and do the same four gestures.
      Watch:  the grid crosses into the strip over 200 ms — for that moment both are on screen at
              partial alpha — while the card around them settles to its new height on the Settle
              spring, which is the slower of the two. At no point is there a frame with the new
              content already solid at the old height.
      Fails:  a one-frame swap of the content inside a card that is still travelling, which is the
              defect this closes; the outgoing grid changing MONTH as it fades — the dates and the
              month title above them re-drawing to the selected date's month while the grid is
              still opaque, which is why the swipe is the first step; the outgoing grid cut off by
              a fast edge of its own on the way out, which would mean the container started
              animating its own size again; or the card bouncing as it lands, which Settle does not
              do.
      Also:   with Reduce motion on, the mode that was tapped is simply there on the next frame at
              its own height — no cross, and no wait where the cross would have been.

- [ ] **PR 42g · and · The error card joins the feed it lands in** — needs a load that actually
      fails, so: airplane mode ON before opening the screen, then off again for the retry. Four
      surfaces, and all four: a task list (Todos), Calendar, Completed, and the root feed's task
      tab.
      Do:     with airplane mode on, open each of the four so the retry card appears at the bottom
              of the feed. Pick a short or empty list on each, so the BOTTOM OF THE FEED is on
              screen when the error lands — the card is appended last, and Compose runs no
              appearance animation for an item that arrives below the fold, so scrolling down
              afterwards shows nothing either way. Then turn airplane mode off and tap Retry.
      Watch:  the card fades up over ~190 ms rather than arriving in one frame, and on Retry fades
              out over ~150 ms rather than being cut. Its placement only shows when something
              ABOVE it changes, so add one case on Calendar and Completed: with the card up, make
              a mutation fail so the empty-state or skeleton item leaves from above it — the card
              should glide down into the freed slot rather than jump.
      Fails:  the card appearing or vanishing in one frame; or the card landing in the wrong slot
              and then sliding to the right one, which would mean the key is colliding with
              something else in the list. Nothing below the card should move — the only thing
              under it is an invisible spacer, and it is not animated.
      Note:   the root feed's task tab fades on the same ~190/~150 as the other three since 8k,
              but still takes its PLACEMENT from a spring rather than the 320 ms tween, so its
              glide is allowed to read slightly softer. What must be true on all four is that the
              card never jumps.
      Also:   with Reduce motion on, repeat all four. The card is simply there, at the bottom of
              the feed, on the frame the error arrives, and gone on the frame the retry succeeds —
              no fade, and no wait where the fade would have been.

- [ ] **PR 8k · and · The calendar's day list moves at the same speed as every other feed** —
      Calendar, on a day that already has three or four tasks on it, so a row leaving has
      neighbours to be seen against. The change is ten milliseconds on each leg, which is under
      what the eye can time on its own — so this row is comparative, not absolute.
      Do:     tick a task off the day list and watch it go; then add one to the same day (or
              re-open it from Completed) and watch it arrive. Then do exactly the same thing on
              Completed, on the same device, within a few seconds — that feed has been on these
              numbers all along and is the reference.
      Watch:  the two FADES read as the same feed — a row arrives a touch more slowly than it
              leaves on both, and neither one feels brisker than the other.
      Fails:  the calendar reading noticeably crisper than Completed, which would mean the old
              180/140 is still in the tree somewhere. Also a fail, and the one worth looking for
              because it is what a swapped pair would look like: a row LINGERING on its way out —
              the departure must stay the shorter of the two.
      Note:   the two feeds differ on PLACEMENT by design, so compare the fade legs and nothing
              else. Completed glides the rows below a departure into the freed gap over 320 ms;
              the calendar passes `placementSpec = null`, so its rows take their new slots in one
              frame and only its error card glides. Both are the tree as it stands — neither is a
              fail on this row.
      Also:   these two fades are `Modifier.animateItem`'s, which Compose times for itself, so the
              app's Settings → "Reduce motion" cannot reach them and leaving it on proves nothing
              here. Use the system's "Remove animations" (or Developer options' animation scales
              at 0): a ticked row is then simply gone on the next frame and an added one simply
              there — no fade, and no pause where the fade was. With the system at 1x and only the
              app switch on the fades still play; that gap is the `reduced-motion-coverage` box's,
              not this row's.

- [ ] **PR 8k · and · The root feed's tiles settle with the row that displaced them** — the root
      feed's task tab, on the Today card with at least two tasks under it and the category grid
      and a list row or two visible below.
      Do:     tick a task off the Today card and watch the grid and the list rows underneath,
              not the row you ticked. Then undo it, or add one back, and watch the same blocks.
      Watch:  the row fades out and the blocks below follow it up on a spring — that spring is
              this feed's own and has not changed. What changed is the row's own fade, which is
              now the 150 ms every other feed leaves on; the departure should still finish before
              the blocks below it have stopped moving.
      Fails:  the row still on screen after the grid has settled — that would be the fade
              outlasting the placement, which is the ordering `TdayFeedItemMotion` is built to
              rule out. Also a fail: the blocks jumping a row height in one frame, which is the
              keys coming off rather than anything to do with these timings.

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
