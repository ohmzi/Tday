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

- [ ] **PR G7 · web · The page dims with the panel, not ahead of it** — a phone, light AND dark
      (the scrim's two alphas differ, and the early-finish is easier to catch on the lighter one).
      Do:     open the right-hand `Sheet` from the More tab, watch only the page BEHIND the panel,
              then close it and watch the same thing. Repeat once at 1/4 speed if the browser's
              animation inspector offers it — the whole margin here is about a tenth of a second.
      Watch:  the page reaches its full dim at the moment the panel stops moving, and starts coming
              back only as the panel leaves — the two halves finish together in both directions.
      Fails:  the page is fully dark while the panel is still sliding in — that is the 0.15 s
              library fallback still winning, which means the utility is not reaching tw-animate's
              `animation-duration`; or the page is bright again with the panel still on screen.

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

- [ ] **PR 8i · web · The dock selects one object, and the pill takes the direct route** — a
      DESKTOP window, or any viewport at `sm` and wider: below that no tab changes width and there
      is nothing to see. The dock with the More tab visible, standing on any tab but the one you
      are about to tap.
      Do:     tap a different tab and watch the tab you are moving AWAY from, and the pill. Once at
              full speed and once with the browser's animation inspector at 1/4, if it offers one.
      Watch:  the tab you left narrows to its icon over about a third of a second, 320 ms, sliding
              the tabs beside it along with it, and the pill travels and stretches alongside them
              and stops when they do. One journey: at 1/4 speed the pill settles onto its mark
              from about five pixels past it, which is momentum, not a return trip.
      Fails:  the pill setting off past the arriving tab and coming back from the far side — 56px
              out and back, unmistakable even at full speed. That is the follower in `RootDock`
              not running, and it is what the 260 ms sample this row retired looked like. Also a
              fail: the tab you left snapping to its icon in one frame, which is `min-width`
              having fallen out of the press layer's `transition-property` list in `globals.css`
              — the call site cannot restore it.
      Note:   the arriving tab is NOT what to watch. Its label makes it wider than the 104px floor,
              so it reaches its width in the first frame at any duration — that is measured, not
              a defect. jsdom computes no layout, so the suite can prove both elements spell
              `duration-emphasis` and can say nothing about whether they arrive together.

- [ ] **PR 8i · web · The dock tab still answers a finger** — the same dock, any width; a touch
      screen if there is one, since this is the press and not the hover.
      Do:     press and hold a dock tab, then release, without changing tabs.
      Watch:  it squashes and dips under the finger and comes back on release. It now takes 320 ms
              rather than 200 — one duration covers every property the press layer animates on
              this element, and the tab's `min-width` had to come up to Emphasis to pair with the
              pill.
      Fails:  the squash reading as the tab thinking about it rather than answering — a press that
              is still arriving when the finger has gone. If it does, the pairing is not worth its
              price and the tab needs a press duration the pill does not share.

- [ ] **PR 8i · web · The install banner leaves faster than it arrived** — iOS Safari, or any
      browser where the PWA install prompt fires; the banner has to be on screen, which on Chrome
      means a site not already installed.
      Do:     let the banner slide up, then tap its X and watch the banner, not the page.
      Watch:  it rises over about a third of a second and is gone in about half that — 320 ms in,
              150 ms out — and it is fully gone before its space is reclaimed, never cut away
              mid-fade.
      Fails:  the banner vanishing between frames with no slide-out at all, which would mean
              `BANNER_EXIT_MS` and the closed-state class have drifted apart again and the node is
              being unmounted before its animation runs; or an exit that takes as long as the
              arrival, which is the class not reaching tw-animate's `animation-duration`.

- [ ] **PR 8j · web · The calendar's view switcher and the view it switched arrive together** — any
      viewport; the Calendar screen, standing on Month.
      Do:     tap Week, then Day, then back to Month, watching the WHITE THUMB behind the labels and
              the grid below it at the same time. Once at full speed and once at 1/4 in the
              browser's animation inspector if it offers one.
      Watch:  the thumb slides a segment and the grid slides in from the side, and both stop on the
              same frame — about a third of a second, 320 ms. The card's height settles with them;
              it was already on that rung.
      Fails:  the thumb arriving first and the grid still travelling under a switcher that has
              already finished — one tap reading as two events. That is the 300 ms this row
              retired, and 20 ms is at the edge of what the eye catches at full speed, which is why
              the 1/4 pass is part of the row rather than a nicety.
      Note:   jsdom computes no layout, so the suite can prove the thumb spells `duration-emphasis`
              and the keyframes spell `--tday-duration-emphasis`, and can say nothing about whether
              the two play as one. Not a fail: the OTHER two segmented controls in the app — the
              Completed tab strip and Settings' two switchers — still move their thumbs over 200 ms.
              That is deliberate and argued at the call site; they have no grid moving beside them.

- [ ] **PR 57a · web · The dock folds down to the tab you are on, and holds still at the fold** — a
      phone-width viewport; Today, and again on the Anytime feed, each with enough tasks to scroll.
      Do:     scroll down slowly past the first 44 px and keep going; scroll back to the top; then
              park a finger just past the fold without moving it and leave it there; then, with the
              dock folded, tap the dock once and wait without touching anything else. Finally
              scroll a long Settings page and the Guide.
      Watch:  as the feed passes 44 px the tabs you are not on are swallowed into the capsule from
              their trailing edge and the capsule closes around the tab you are on, over about a
              third of a second — 320 ms, the rung its pill and its tabs already share. The icons
              are clipped, not squeezed. A finger resting at the fold leaves the dock exactly where
              it is. Scrolling back up opens it again well before the top of the feed arrives. One
              tap on the folded dock opens it where it stands — no navigation, no jump to the top —
              and it closes itself again about 2.4 s later if nothing is chosen.
      Fails:  the capsule reaching its folded width in a single frame instead of gliding (the press
              layer deletes a `width` transition declared on a button, which is why the collapse is
              on a wrapper); an icon squashing on the way out; a stub of a closed tab left inside
              the capsule — folded, the dock is the one tab and the capsule's own 7 px either side
              of it, 62 px on a phone, and nothing else; the dock flickering between its two
              shapes while a finger rests near the fold, which is the whole reason there are two
              thresholds; a tap on the folded dock jumping the feed to the top or switching tab;
              the dock folding on Settings or the Guide, where it is the way out rather than in
              the way.
      Note:   jsdom computes no layout, so the suite can prove the fold's four answers and which
              classes each tab carries, and can say nothing about whether the capsule glides. The
              collapse is a `1fr` → `0fr` grid track — the same interpolable spelling of "as wide
              as what is inside it" the collapsing task rows use on the other axis — and it has
              never been seen playing.
      Pill:   the white pill under the active tab makes the trip with it, and the Anytime feed is
              where that is worth standing on — there the tab that closes is to the LEFT of the
              one you are on, so the active tab slides 52 px inwards while the capsule shuts around
              it. The pill is re-measured once a frame for the length of the fold rather than
              sprung to a computed slot the way Android's selector is, so what to watch for is a
              pill that arrives after the tab has stopped or overshoots and comes back — not one
              parked in the open dock's slot, which is what the suite now pins.

- [ ] **PR 57b · web · The folded dock is folded, not half-folded** — the same phone-width
      viewport and the same two feeds, with reduce-motion on (Chromium: DevTools → Rendering →
      **Emulate CSS prefers-reduced-motion: reduce**; or the OS setting, which is the one a real
      user has). The Anytime feed is where to spend the time: there the tab that closes is to the
      LEFT of the one you are on, so the fold moves the active tab 52 px without changing which
      tab is active.
      Do:     scroll past 44 px, then back to the top, then past it again. At a desktop width,
              also switch tabs with the dock open.
      Watch:  the dock is its folded shape on the next frame — no glide, no half-closed capsule —
              and the white pill is UNDER the one tab that is left, flush inside the 62 px
              capsule. The same in reverse: the open dock arrives already open with the pill under
              the active tab. Nothing travels at any point, and nothing needs a second gesture to
              settle.
      Fails:  the pill parked to the RIGHT of the tab it marks, or clipped to a sliver against the
              capsule's border — the defect this PR is for. With motion off there is no follower
              re-reading the tab's rect, so the only thing that can correct the measurement taken
              as the fold committed is the transition's own completion; a pill in the wrong slot
              means that never arrived. Also a fail: anything gliding, which would mean the 1 ms
              floor is not reaching this subtree, and a pill that snaps to the right slot only
              once you touch the dock again.
      Note:   `globals.css` floors transitions to 1 ms rather than to zero precisely so the
              completion still reports itself, and this row is the only place that bargain is
              checked by eye. An engine that decided not to fire `transitionend` for a 1 ms
              transition would show exactly the failure above with every automated gate green.

- [ ] **PR 59 · web · The hero header stays crisp through a long scroll** — a mid-range Android
      phone in Chrome, not a desktop emulating one: this row is about what a real GPU does with a
      layer it rasterises once and reuses, and a laptop has the headroom to hide it. Both root
      feeds, Today and Anytime, each with enough tasks to flick through several screens.
      Do:     drag the top 78 px of the feed slowly up and down so the hero mark, title and search
              capsule morph continuously for several seconds without a pause, then flick hard and
              let it settle. Repeat with the phone's own text size turned up, where the title is
              largest and a bad raster is easiest to see. Then stop scrolling, wait two seconds,
              and start again — that second pass is the one that runs after the hints have been
              dropped, and it has to look like the first.
      Watch:  the three pieces morph smoothly throughout, and the text in the title is as crisp at
              the end of a long scroll as at the start of it and as it is at rest.
      Fails:  the hero title going soft, fuzzy or fringed while scrolling and snapping back crisp
              when the scroll stops, which is a layer rasterised once at the wrong scale and
              reused — the specific cost of these hints, and the reason they are dropped 200 ms
              after the last scroll frame rather than held. Also a stutter on the first frame of
              the second pass, which would mean the drop is too eager.
      Note:   there is no automated gate for any of this and there cannot be. `will-change` changes
              no pixel by definition — it changes when the compositor allocates, which is a
              property of a GPU and a driver. Rule F of `motion-reachability-web.test.ts` proves
              only that this is the one hint in the app and that it clears what it sets; it is set
              in JavaScript, where even that cannot watch it happen. This row is the whole of the
              evidence that the change did not cost anything.
              Nothing to check on the dock, the bulk bar or the search panel: the nine stylesheet
              hints that would have made those a device question were removed before this shipped,
              and the ledger row argues why.

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

- [ ] **PR 42a · and · Morning Sweep deals the next card instead of swapping it** — Morning Sweep,
      with at least three carried-over overdue tasks waiting, so there are cards to deal and a
      finish line to reach.
      Do:     tap **Tomorrow**, twice. Then triage the rest until the last card is gone.
      Watch:  the card you have dealt with slides about a quarter of the screen LEFT and fades as it
              goes, over 150 ms, while the next one comes in from the right over 200 ms and settles.
              The whole stack travels as one thing — the card, the five action rows and the
              Skip / Sweep-all row arrive together, not six times. After the last card the finish
              line fades up into the space rather than appearing on the frame the card left.
      Fails:  the next stack drawn complete in one frame with no travel at all, which is the state
              this screen shipped in. Also a fail: the outgoing card taking as long as the incoming
              one or longer, which reads as the app hesitating over a decision already made; the
              tally on the leaving card ticking down while it leaves; and "all swept" flashing up
              when you OPEN Morning Sweep with cards waiting, which is the finish line playing
              before the deck has been read.
      Also:   with Settings → **Reduce motion** on (or the system's "Remove animations"), tap
              Tomorrow again. The next card is simply there, at its own height, on the next frame —
              no slide, no fade, and no frame where the panel is caught mid-resize. The finish line
              is the same: there, or not there.

- [ ] **PR 42d · and · The onboarding wizard says which way it moved** — a fresh install, or the
      app signed out so the wizard comes up at the Mode step.
      Do:     tap **Self-hosted** to go to Server, then **Change setup** to come back to Mode, then
              Self-hosted again. Connect to a server, and from the sign-in panel tap
              **Change setup** once more.
      Watch:  going forward, the arriving panel comes in from the RIGHT over 200 ms while the one
              it replaces leaves to the LEFT over 150 ms; coming back, both directions reverse. The
              panel that is leaving is always the quicker of the two. The three chips above do not
              slide with it — they stay put and only their own fill changes.
      Fails:  both panels simply dissolving into each other with no travel, which is the default
              spec this screen shipped on; the two directions looking identical, so Back and
              Continue are indistinguishable; or a taller panel clipped square across the bottom
              while it travels, which is the size transform clipping.
      Also:   the connect spinner and the "signing you in" panel must NOT slide. Watch the hop from
              Server to sign-in specifically — it goes Server → connecting → sign-in, and both of
              those hops are a crossfade in place with no sideways movement at all. A slide there,
              and especially a BACKWARD slide as the spinner goes away, is the wizard claiming the
              user moved a step when they did not.
      Also:   with Settings → **Reduce motion** on (or the system's "Remove animations"), walk Mode
              → Server → Mode again. Each panel is simply there on the next frame at its own full
              height — no slide, no fade, and no frame where the card is caught mid-resize.

- [ ] **PR G2 · and · A toast leaves when it is asked to, and not before** — any screen that puts a
      toast up with an Undo on it: delete a task from a list, which is the toast with the most to
      lose. Five gestures, one toast each; work quickly, the auto-dismiss window is the clock.
      Do:     (1) press the middle of the card and let go without meaning to move. (2) Take it down
              about a centimetre — a third of the card's own height — and let go slowly. (3) From
              rest, flick it down hard and let go at once, without taking it far. (4) Take it down
              a centimetre and then flick it back UP before letting go. (5) Swipe straight across
              the card, sideways.
      Watch:  (1) and (4) leave the card on screen and spring it back to where it sat — the same
              return a half-opened task row makes when you let go of it, on the same spring
              (0.82 / 340), carrying whatever speed it had. (2) and (3) throw it off the bottom in
              160 ms and the Undo goes with it. (5) does not move the card at all. Under the finger
              the card still fades towards 45 % and shrinks 3 % over the first 96 dp, exactly as it
              did before.
      Fails:  the card leaving on (1) — that is the old "any downward pixel commits", which is the
              whole point of this row. Also a fail: (4) dismissing, which means an upward flick is
              being read as distance already given up. Also a fail: a refused card arriving back at
              rest in one frame instead of springing, or snapping home and then springing from
              there — that is the hand-off between the finger and the spring going through zero.
              Also a fail: (5) dragging the card sideways-and-down, or eating a swipe meant for the
              screen underneath.
      Known:  (3)'s flick leaves on the same fixed 160 ms accelerating exit as (2)'s slow drag, so
              a hard throw hangs for a frame or two at lift-off before the card goes. The speed is
              carried into the refusal spring only; making the exit answer it is
              `toast-drag-two-stage-exit`, and it is not a fail here. A toast that is already past
              the threshold when something else claims the pointer still commits. A cancelled drag reaches the app as a release with no velocity, so it
              is judged on distance like any other release; that is argued at `TdayToastDismissState`
              and pinned by a test, and is not what this row is looking for.

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

- [ ] **PR 41c · ios · The create sheet can be pulled down, and says so** — the root feed, on a
      phone. Both sheets on the custom mechanism: the create-task sheet (the + button) and the
      create-list sheet, which is the one whose contents scroll.
      Do:     open the create-task sheet and pull it down slowly until it goes. Open it again and
              flick it down 40 pt or so, fast. A third time, drag it two thirds of the way down and
              then walk it most of the way back up before letting go. Then open the create-list
              sheet and try to scroll its colour and icon rows.
      Watch:  a 36 × 5 bar at the top of the card, the same one iOS draws on its own sheets. The
              card tracks the finger exactly while it is down, and a released drag that commits
              carries on and leaves on the same curve the scrim tap leaves on — the two must be
              indistinguishable, because they are the same code path. A quarter of the card's own
              height is where a slow pull commits; the flick commits long before that. The walked-
              back drag springs home on the Gesture spring, with a little overshoot rather than a
              snap. On the create-list sheet the scrolling regions still scroll and the header and
              the margins still drag the card.
      Fails:  the grabber reading as a second header — too dark, too far down, or crowding the
              title and the two round buttons under it, which start 14 pt below the top edge. Also
              a fail: the card oscillating or stuttering under the finger (that is the drag being
              measured in local space, which the global coordinate space here exists to prevent);
              a committed drag that snaps the card home first and then plays the exit; the
              walked-back drag dismissing anyway; and the create-list sheet refusing to scroll
              because the card's drag has taken the gesture.
      Keyboard: open the create-task sheet, tap into the title field so the keyboard lifts the card,
              and drag from there. The keyboard must go down as the drag begins, not at the release
              — and the card must not jump or fight the inset collapsing under it. This is the one
              interaction source cannot answer: the inset is animated on the keyboard's own
              ~0.25 s curve while the finger is still moving the card.
      Selector: on the create-task sheet, open List, then Priority, then Due date. The grabber must
              fade out as the picker's dim comes up and fade back as it goes — never sit lit on top
              of the dim — and while the picker is open a downward drag anywhere on the dim must do
              nothing at all. A fail: the drag dismissing the whole sheet and losing what was typed,
              where a tap on those same pixels only closes the picker. The picker's own rows and its
              tap-to-close must still work throughout; this stands the card's drag down, not the
              layer over it.
      Also:   with **Settings → Accessibility → Motion → Reduce Motion** on, repeat the first three.
              The card still follows the finger — a surface under a thumb is direct manipulation and
              is not what the setting turns off — and a refused drag is simply home on the next
              frame rather than springing. A committed drag still dismisses, and the card crossfades
              out where the full-motion build slides it. The grabber still fades under a picker,
              on both settings: that is a dim arriving over it rather than anything travelling, and
              it has to leave at the same rate the dim comes up.
