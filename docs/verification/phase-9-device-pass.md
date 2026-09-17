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

- [ ] **PR 54 · web · The press is a trip, and a reduced-motion press is not a wait** — the last
      part of `press-affordance-unification` with no row anywhere, appended by 9b5 as it closes the
      box. A phone or a narrow desktop window. Needs a shadcn Button (any sheet or dialog confirm),
      the onboarding wizard's step chip, and the dock at phone width.
      Do:     press and HOLD a shadcn confirm and let go; then the wizard's step chip; then tap
              between two dock tabs. Repeat the lot with the OS "Reduce motion" setting on.
      Watch:  the squash and the 1.5 px drop EASE in and ease back over about 150 ms on the press
              curve — a short trip each way. The ripple under them takes longer, 320 ms, and that
              is fine; what must not happen is the geometry finishing before the ripple has
              started. The chip presses deeper than the confirm — it says 0.97 for
              itself and is obeyed. The dock tab's width eases open beside the indicator pill
              rather than reaching its new width ahead of it. Under Reduce motion every one of
              them arrives pressed on the next frame and leaves on the next frame: no travel, and
              exactly as far down as before.
      Fails:  the geometry landing in one frame and leaving in one under a ripple that still takes
              its time — half an affordance, a jolt under a slow bloom, which is the defect the
              layer exists to remove. Also a fail: any pressable still easing under Reduce motion,
              which is the floor being overridden again; the chip pressing to the same depth as the
              confirm, which would be the scale promoted out of `base`; and the dock tab snapping
              104 px → 48 px beside a pill that glides, which is `min-width` gone from the list.
      Why:    jsdom applies no cascade layers and computes no style, so a vitest run can prove the
              layer is declared and where it is declared — `press-affordance-cascade.test.ts` does
              exactly that — and can prove nothing about which declaration won in a browser. Every
              wrong version of this still presses.

- [ ] **PR 41c · web · The swipe row's reveal has a detent in it** — an ANDROID PHONE in
      Chrome, with the app's own Sound & vibration haptics switch left on and the phone off silent.
      Nothing else can perform this row: `navigator.vibrate` exists in Android Chrome and nowhere
      else, so an iPhone, a desktop and every other browser will feel nothing and prove nothing.
      Any list with a few tasks on it; the actions behind a row are 210 px wide and the detent is
      at 105 px.
      Do:     (1) drag a row left slowly and stop the moment you feel something, then look at how
              far it has gone and whether the pills are visibly on their way; (2) hold still there
              for a second and jiggle either side of it; (3) let go and then tap the checkbox on
              another row, so the reveal and the tick land a second apart; (4) from closed, flick a
              row left hard and short — 40 px and the finger is gone — so it opens on speed alone;
              (5) drag a row that is already open further left, walk it halfway back, and pull it
              out again.
      Watch:  (1) the buzz arrives at 105 px, half the row's actions, and reads as the actions
              catching under the thumb rather than as a notification landing. This is the question
              the row exists to ask: half the width is much further out than the natives' 32%, and
              it is that far out because web commits on a projected rest rather than on a position
              — so the honest question is whether a detent the hand meets halfway is late, and
              whether the commit itself is what wants moving. (2) one buzz and then nothing,
              however long you hold and however much you jiggle. (3) the reveal (25 ms) and the
              checkbox's own tick (15 ms) must be two different things in the hand — the reveal
              noticeably the fuller of the two; if they read as the same buzz, 25 is too close to
              the acknowledgement band and the gap wants widening rather than the verb reusing.
              (4) the flick's buzz lands at lift-off instead of mid-drag, and the two should still
              read as the same event happening at the only moment each of them can. (5) silence
              throughout — there is nothing left to uncover.
      Fails:  a buzz that repeats or rattles while the finger rests at the detent; two buzzes in
              one drag from crossing, coming back and crossing again; any buzz on the way closed,
              or on a row shut from under you by opening a different one; a second buzz as the row
              springs open after release; and a flick arm that feels heavier or lighter than the
              detent arm.
      Known:  cross the detent, drag back and release closed, and you have felt a reveal that did
              not happen. That is what a detent on a physical control does, it is pinned by a test
              that says so, and it is not a fail here — the alternative is silence until the row
              settles, which costs the feature its point. Also not a fail: the natives buzz at 32%
              of their reveal and web at 50% of its own. Each client fires on ITS OWN commit rule
              so the buzz can never announce an open that does not happen; the three clients agree
              on the sentence and not on the pixel, and reconciling the pixel means moving a
              shipped commit threshold, which is its own argument.
      Why:    no gate in this repository can feel a haptic and no machine here has a vibrator.
              `swipe-row-reveal-haptic.test.tsx` pins the decision — one buzz per crossing, none
              while hovering, none on a closed release, one at the flick that never crossed, none
              on an already-open row — and `feedback-preferences.test.tsx` pins that the new verb
              inherits the app's haptic switch at the chokepoint. What none of it can say is
              whether 105 px is where the catch belongs, or whether 25 ms and 15 ms are still two
              events in a hand.

- [ ] **PR 41d · web · An open swipe row goes away when you touch anything else** — a REAL PHONE,
      and both of them if you have both: iOS Safari and Android Chrome arbitrate a touch-scroll
      differently and this row is about the arbitration. Any feed long enough to scroll, one row
      swiped fully open, plus the same pass on the calendar day list and the Anytime feed, which
      are two other event buses behind the same hook.
      Do:     (1) with a row open, tap a DIFFERENT row's checkbox, and watch both the row you shut
              and the box you ticked; (2) tap the dock, then repeat and tap the FAB; (3) with a row
              open, tap one of its OWN pills — Edit, then Copy, then Delete — and confirm each pill
              actually fires; (4) with a row open, start a slow scroll with the finger landing ON
              the open row, and separately two rows below it; (5) with a row open at the very top
              of the feed, pull DOWN into the rubber-band overscroll without really scrolling, and
              on iOS also scroll just far enough to make Safari's URL bar collapse; (6) with a row
              open, drag it back to the right slowly and stop halfway, hold for a second, then
              finish the drag — and separately, drag it further LEFT past the limit and hold there.
      Watch:  (1) the row closes and the checkbox ticks, in one touch. Both, not either. (2) the dock
              switches tab and the FAB opens its sheet, with the row gone behind whatever happened.
              (3) every pill does its own thing. This is the subtree guard doing its whole job: the
              pills are `absolute inset-y-0 right-0` and stand still while the foreground slides
              over them, so a dismissal fired on the pointer-DOWN would slide the foreground back
              across the pill before the finger lifted, the up-target would no longer be the button,
              and the browser would send `click` to the common ancestor instead — a pill that looks
              pressed and does nothing. (4) the row is closed by the time the list has visibly moved,
              both times; the one starting on the row is the case the tap listener cannot see and
              the `scroll` listener exists for. (6) nothing dismisses it: the finger owns the row in
              both directions, and the release decides.
      Fails:  (1) a first tap that only closes the row and leaves the box unticked — a consumed
              touch, which is the outcome this design rules out by construction and the one that
              turns into a trap with a screen reader on. (3) any pill that needs a second tap, or
              that does nothing at all. (5) is the real risk and is a fail if the row closes on an
              overscroll bounce or on the URL bar collapsing: both are `scroll` events that no
              finger asked for, and neither is a list moving under a Delete pill. (6) the row
              jumping home mid-drag, or snapping back OPEN on the next move after something
              dismissed it — the second is the gesture re-seed, which is pinned by a test but has
              never run on a touchscreen.
      Known:  closing on scroll START is a deliberate divergence from the search capsule, which
              ignores scrolls on purpose. The field is chrome and stays put; a row is content and
              travels, and one left open puts an armed Delete pill under a thumb now aimed at a
              different task. The question a device can settle is whether it reads as a dismissal
              the user caused or as the row being snatched.
      Also:   with VoiceOver or TalkBack on, walk a CLOSED row and count what it offers. Below the
              `sm` breakpoint the three pills are drawn at `opacity: 0`, and CSS opacity removes
              nothing from the accessibility tree and disables no hit testing — so "Edit task",
              "Copy task" and "Delete task" are very probably announced on every closed row in the
              feed, with no gesture at all. Whether they actually are is the device question. This
              is NOT a defect to fix here, and `aria-hidden` / `inert` on those pills is the wrong
              instinct and must be refused in this PR: it is currently the only route an assistive
              web user has to those three actions, because web has no custom-action fallback the
              way iOS's rotor actions are. Hiding them without first publishing an equivalent route
              deletes functionality. It is the web half of `ios-accessibility-actions` and it wants
              its own PR.
      Why:    the decision is two pure functions with a truth table
              (`shouldCloseSwipeRow`, `canDismissMidGesture`, in `swipe-gesture.test.ts`) and the
              wiring is pinned in jsdom by `swipe-row-outside-dismiss.test.tsx` — which listener
              exists when, that the `scroll` one is capture-phase because `scroll` does not bubble,
              that the dismissing event still reaches its own target with `defaultPrevented` false,
              and that a closed row holds no document listener at all. What jsdom has none of is
              gesture arbitration: it does not fling, does not rubber-band, does not collapse a URL
              bar, and fires whatever event the test asks it to at the moment the test asks.

- [ ] **PR 43 · web · Undo takes the paper with the row it brings back** — a custom list with
      exactly one task left, run twice. Once where that task is dated today or later, so undoing it
      puts a CURRENT row back. Once where the one remaining task is itself **overdue**, so undoing
      it puts an OVERDUE row back — that is the screenshot and the setup that matters. The two are
      different screens, not two runs of one: `hasNonEarlierListTodos` excludes the Overdue bucket
      on purpose, so a restored current row makes it false and the scene leaves, while a restored
      overdue row leaves it exactly where it was. A list holding one current task and one overdue
      task is the FIRST case — ticking the current one and undoing it restores a current row.
      Do:     tick the last task, and while the paper is still in the air press **Undo** in the
              toast. Aim for the first half of the flight — inside about a second — so there is a
              burst left to interrupt.
      Watch:  the pieces keep FLYING as they go — still travelling, still spinning, still flipping
              — and fade out over Quick while they do. On the plain setup the scene goes with them
              on the same rung, its 42vh track closing under the fade rather than dropping in the
              frame the node leaves, so the restored row arrives without the page jumping under
              it. On the overdue setup the scene STAYS exactly where it is under the Overdue
              header, which is the designed v0.7.25 presentation and not a bug — only the confetti
              leaves, and the restored row is back in the section above it.
      Also do: the same plain undo on the **Anytime** tab's own feed and inside one **Anytime
              list**. Those two screens reached the celebration by a different road until this PR
              and had neither half of the fix; an Anytime task has no date, so there is no overdue
              variant to run there — every undo is the plain case, and the scene leaves over its
              own track every time.
      Fails:  the burst carrying on over the restored row and expiring on its own a second or two
              later, which is the report. Also a fail, and the reason this is not a one-line
              change: the paper vanishing between two frames on the press, which is the same
              complaint one layer down; the pieces FREEZING and then dissolving in place, which
              would mean the envelope is being applied to a stopped clock rather than multiplied
              into a running one; the burst restarting from the launch patch, which would mean the
              draw effect re-ran and re-rolled the fan; and on the overdue setup the scene's own
              rise visibly jumping, which would mean the celebrating class came off an arrival
              that was still playing.
      Also:   a second completion straight after an undo must celebrate normally — tick it off
              again and the full burst plays, because the newer stamp re-opens the window. And in
              two tabs or on a second device: empty the list from the other end (the burst plays
              here), then undo it there. The paper must go on this tab too, without the scene
              flickering back. Typing a new task into a list while the paper is up must end it the
              same way — the list is genuinely not finished any more.
      Reduce: with the OS "reduce motion" preference on, repeat the plain undo. There was never any
              paper to take away — `Confetti`'s effect returns before its first frame — so there
              must be nothing at all: no pause, no held frame, no Quick of anything. The row comes
              back and the scene goes on the same frame.
      Why:    jsdom computes no layout, applies no stylesheet and paints no canvas. `vitest` pins
              the decision — `undo-cancels-celebration` covers the overdue case a transition-shaped
              fix misses, `confetti-cancel-fade` pins that the canvas outlives `play` and that what
              it paints is fading, and `confetti-kinematics` pins the envelope term — and none of
              them can say whether forty-six pieces look like paper leaving or whether the 42vh
              under them came back smoothly. That is the whole of what this row is for.

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

- [ ] **PR 8l · and · The drop placeholder opens at the speed of the feed it opens in** — a
      timeline list (Today or All) with at least two dated sections and three or four tasks under
      the one you drag into, so the gap has neighbours to be seen against. This is a retiming of
      two legs, not three: the placeholder's fades were 150 in and 120 out of its own and are the
      feed's 190 and 150 now. Its placement leg took the feed's 320 in the same change and is the
      leg you cannot see here — nothing displaces the gap while it is up — so what is on the
      screen is those two fades against rows that were already gliding on 320.
      Do:     long-press a task, drag it over another day's section and hold there without
              dropping. Watch the gap fade in while the rows below it move down. Then drag back
              out of that section and watch it fade out as they close over it. Do it again, and
              this time watch only the gap.
      Watch:  the gap fades in at the pace of a row arriving — it is the same 190 — and fades out
              at the pace of a row leaving. The rows under it glide on their own 320 and settle
              after the gap has finished filling; that lag is what every arriving row in this
              feed does, and it is what passing looks like here.
      Fails:  the gap blinking in or out, a fade too quick to follow, which is the placeholder
              back on its own 150/120; or the placeholder still drawn after the rows have closed
              over the space it had.
      Note:   the section header's bottom spacing moved onto the same rung in this unit. Do not
              go looking for it: the only two values it takes are 2 dp and 4 dp, so the whole
              travel is 2 dp and no duration is legible in it. Naming Emphasis there is for the
              next size that gets animated off that line, not for this one.

- [ ] **PR 31 · android · Every screen hands over at the same length** — a device, anywhere
      with depth: Today → a list → a task, Settings → Account, the Guide, the Latest release
      screen. Include the cold launch, which is the splash handing over to the first real
      screen, and include at least one Settings sub-screen, because those routes each used to
      name a transition of their own and now inherit the graph's.
      Do:     walk in three or four screens deep and back out again with the system back
              gesture, watching the toolbar row rather than the body. Then kill the app and
              cold-launch it, watching the splash give way. Then sign out and back in, which is
              the auth routes. Do the whole walk once more at normal reading speed rather than
              at test speed.
      Watch:  every hand-over is the same length — 200 ms in and 200 ms out, one fade in place,
              nothing sliding. Going in should feel like it decelerates into the new screen and
              coming out like it leaves promptly; that is the only difference between the two
              directions and it is a curve, not a length. The splash and the Settings
              sub-screens are indistinguishable from an ordinary push.
      Fails:  a route that is visibly longer or shorter than the ones either side of it — the
              defect this row exists for, and the splash is where it was worst at 300 against
              360. Also a fail: the back chevron or the action cluster appearing to travel
              sideways, which would mean a slide has come back; the outgoing screen still
              legible under the arriving one, which at 200 ms should not read as a crossfade
              with two screens in it; or a hand-over that now feels rushed enough that the
              screen appears before you have finished the gesture — that is the one judgement
              call here, and the argument against it is at `navigationEnterTransition`.
      Also:   with Settings → Motion → Reduce motion ON — the in-app switch, NOT the developer
              options animator scale — every route change is a cut: the destination is fully
              drawn on the first frame, on the way in and on the way out, including the cold
              launch. This is the half that did not work before this unit, so it is the half
              worth the most attention. A screen caught half-faded, or a fade that still plays
              at any length, is a fail. Then check the animator scale at Off as well, which
              worked before and must still.
      Also:   still with the in-app switch ON, tap a search result from the scheduled home and
              from a root feed search {D} two waits existed only to cover the fade this unit now
              cuts, and they move with it. The search surface must come down at once rather
              than sitting over the task for a quarter of a second swallowing taps, and the
              highlighted row must be scrolled to and flashed immediately rather than after a
              third of a second of a destination that is already fully drawn. Either pause
              surviving is the wait kept with the motion removed, which is the failure the
              gate was supposed to end.

- [ ] **PR 31b · android · The back gesture says how far you have pulled** — a device on Android 13
      or later with the system back set to GESTURE navigation (Settings → System → Gestures), not the
      three-button bar. Any screen with depth under it: Today → a list → a task, or Settings → Account.
      Do:     drag slowly from the left edge and HOLD your thumb still partway across. Move it back
              towards the edge without lifting, then out again. Then let go past the threshold, and
              on a second run let go early so it snaps back. Do the whole thing again from the right
              edge.
      Watch:  the screen you are dragging is the only thing that moves. It slides right and shrinks
              as you pull, tracking your thumb both ways — holding still holds the screen still, and
              pulling back un-does the travel. At the far end it has gone about a quarter of the
              width across and is about a tenth smaller. Letting go past the threshold carries it the
              rest of the way; letting go early returns it. The screen arriving underneath does NOT
              travel: it fades up in place, and the back chevron and the action cluster stay exactly
              where they are throughout.
      Fails:  the drag showing nothing but two screens crossfading, which is the defect and is what
              every build before this one did. Also a fail: the arriving screen sliding too (the
              toolbar would visibly travel, which is the thing the NavHost comment refuses); the
              dragged screen not tracking backwards when you pull back towards the edge; or the
              recede being so slight you cannot tell a 30 % pull from a 70 % one.
      Depth:  the one judgement call in this row, and the reason it exists. 0.90 was chosen to match
              the recede the SYSTEM plays when back leaves the app, so compare them back to back:
              drag back from the home screen's own app-to-home gesture, then drag back inside the
              app, and say whether the two read as one gesture. If the in-app one is visibly
              shallower or deeper, the number is wrong and `PREDICTIVE_BACK_MIN_SCALE` is the one
              place to change it.
      Button: tap the toolbar's back chevron rather than dragging, on the same screens. The exit is
              the same 200 ms slide-and-recede a released scrub finishes on — one back, not two.
              A chevron that still plain-fades while the drag slides means the wiring reached the
              gesture and not the slot.
      Also:   with **Settings → Motion → Reduce motion** ON — the in-app switch, not the developer
              options animator scale — drag from the edge again. There is no recede and no travel at
              any point of the drag; the destination is simply drawn finished. A screen caught part
              way off the side, or one that scrubs and then cuts, is a fail.

- [ ] **PR 184a · and · The dock folds once instead of flickering at the fold point** — both root
      feeds (the scheduled home and the Anytime/list feed), each with enough tasks to scroll well
      past the header.
      Do:     scroll down slowly until the dock folds to its pill and hold the finger still there;
              then lift, let the list settle under its own fling, and scroll back up in small
              increments, watching where it opens.
      Watch:  one crossing each way. It folds about 44 dp in and stays folded with a finger parked
              at that distance, and while the list rocks a pixel either way as a fling settles. On
              the way back up it opens about 20 dp higher than it folded, so the two events are
              visibly at different heights rather than at the same one.
      Fails:  the pill and the capsule alternating under a held finger, or on the last frames of a
              fling — that is the single-threshold behaviour this row exists to catch; and, the
              other way, a deliberate scroll all the way to the top arriving with the dock still
              folded, which would mean the release edge sits too low to be reached.
      Also:   a feed too short to scroll must never fold the dock, including while an overscroll
              bounces it past the top — check with a list of one or two tasks. And scroll hard on
              both feeds with a frame-rate overlay up: the fold point is read off the scroll
              outside composition now, so a fling must not be costing the screen a recomposition
              per frame.

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

- [ ] **PR 8g…8n · android · The wizard's step chips still keep up with the step they mark** —
      onboarding, first run or a reset account, walking forward and then back through the wizard's
      steps so the chip strip has to hand the highlight over in both directions.
      Do:     watch the chip strip rather than the panel, and change step quickly — back-to-back,
              not one step every few seconds.
      Watch:  the swell is the only thing on the strip with a clock. Colour, border and elevation
              are a plain `if (highlighted)` on a non-clickable `Card` and land in the frame of
              the tap; only the 1.02 scale eases, by a fiftieth of the chip's size. It should read
              as the chip settling into a step the colour has already claimed. Note which way you
              are going: stepping forward, the chip you leave stays coloured — it is completed —
              and only shrinks, so backwards is the one direction where a chip loses its colour.
      Fails:  the swell reads as a bounce, or as a second event after the colour snap rather than
              as the tail of it; or a fast back-and-forth leaves a chip visibly mid-swell when the
              next step takes over. The scale ran on a hand-written 180 ms and is `Durations.Enter`
              (200) now, which is twenty milliseconds longer — the whole question this row asks is
              whether that is twenty milliseconds nobody can see, which is what the migration
              claims.

- [ ] **PR 41a · android · The swipe row's reveal has a detent in it** — Today, Todos, Calendar or
      Completed, a list with several tasks, system haptics on and the phone not in a case that eats
      them. Do the same row four ways.
      Do:     (1) drag a row left slowly and stop the moment you feel something, then look at how
              far it has actually travelled; (2) hold the finger still right there for a second or
              two, and jiggle it a few px either side of that point; (3) let go, tap a pill, and pay
              attention to the two buzzes back to back; (4) from closed, flick the row left hard and
              short — 20 px and gone — so it opens on speed without ever reaching the detent; (5)
              with one row open, swipe a DIFFERENT row open so the first is shut from under you,
              and catch that first row with a thumb while it is still travelling home — then drag
              it back out.
      Watch:  (1) the buzz should arrive at roughly 56 dp of travel (0.32 of the 176 dp reveal), and
              it should read as the actions catching rather than as a notification: the question
              this row exists to ask is whether 56 dp is where the hand expects the catch, or
              whether it comes too early to mean anything / too late to be a detent at all. (2) one
              buzz and then nothing, however long you hold or however much you jiggle. (3) the
              reveal (CONTEXT_CLICK) and the pill's own tap (CLOCK_TICK) must be two different
              things in the hand, arriving a moment apart — the reveal sharper, the tap lighter.
              (4) the flick's buzz lands at lift-off instead of mid-drag, and the two arms should
              still feel like the same event happening at the only moment each of them can.
      Fails:  a buzz that repeats, ticks or rattles while the finger rests at the detent; two buzzes
              in one drag from crossing, coming back and crossing again; any buzz on the way closed,
              or on a row shut from under you by opening a different row; a second buzz one frame
              after the first as the row springs open; a buzz on a plain tap (the tap plays the
              42 dp hint, which is under the 56 dp detent by design, and must stay silent); and the
              fling arm feeling like a different, heavier event than the detent arm. (5) is its own
              fail: a buzz on the frame the thumb lands on a row that is still open and still
              closing, or on the way back out from there. Nothing is being revealed — the actions
              are already out and under the thumb — and the open-cycle does not end until the row
              is actually home.
      Known:  cross the detent, drag back and release closed, and you have felt a reveal that did
              not happen. That is what a detent on a physical control does and it is not a fail
              here; the alternative is silence until the row settles, which costs the feature its
              point. Also not a fail: iOS buzzes on the tap-then-hint path and Android does not —
              a real cross-platform divergence, named rather than closed by this change.

- [ ] **PR 41b · android · An open swipe row goes away when you touch anything else** — Today,
      Todos, Calendar or Completed, a list long enough to scroll, and one row swiped fully open.
      Four questions, and every one of them is about a gesture rather than about a frame.
      Do:     (1) with a row open, put a thumb on the content 176 dp in from the right edge — which
              is where the open row's own body now sits — and drag it back to the right in one
              movement, including starting from the very edge of the screen; (2) from an open row,
              flick rightward sloppily, at an angle, fast; (3) with a row open and TalkBack ON,
              double-tap a different row, then the header, then the FAB; (4) with a row open, start
              a slow scroll with the finger landing ON the open row itself, and separately with it
              landing on a row two below; (5) on the two ROOT tabs — the scheduled home feed and
              Anytime — open a row and tap the dock, then re-open and tap the create button;
              (6) on the timeline feed and on Calendar, open one row and then long-press a
              DIFFERENT row until it lifts for a drag-to-reschedule.
      Watch:  (1) the row follows the thumb back and settles closed, and the system's predictive-back
              affordance does not take the gesture instead. The content being translated ~176 dp left
              is what makes this worth asking: the natural place to grab an open row is inside the
              edge zone the system watches. (2) the row still takes the drag rather than the
              LazyColumn taking it as a scroll — `draggable(Orientation.Horizontal)` and the list
              are racing for the same slop, and the loser of that race is invisible in code.
              (3) each double-tap closes the row AND does its own job — the other row plays its
              42 dp hint, the FAB opens the sheet. The interceptor watches the INITIAL pointer pass
              and never consumes, and whether it sees anything at all while explore-by-touch owns
              the touch stream is the one thing no gate here can answer. (4) the row is closed by
              the time the list has moved a few dp, both times. (5) both close the row AND do their
              own job in the same touch — the dock switches tab, the button opens the create sheet.
              These two are drawn OUTSIDE the feed's Scaffold, as siblings of the crossfade that
              holds it, so they are reached by an interceptor installed one level up in
              `RootFeedContent` rather than by the screens' own; that is a different code path from
              everything in (3) and is the reason it is asked separately. (6) the first row's
              actions are gone the instant the drag picks up, not when it is dropped.
      Fails:  (1) the screen pops or the back affordance appears instead of the row closing; (2) the
              list scrolls sideways-ish, or the row jumps to the finger instead of following from
              where it was; (3) any double-tap that silently does nothing — a consumed first touch
              is a trap with a screen reader on, and it is the one outcome this design rules out by
              construction; (4) the row staying open through a scroll, or closing a beat late, at
              the END of the fling, with an armed Delete pill riding past under the thumb; (5) the
              row surviving a dock or create-button tap, which is the whole defect, or either
              control failing to do its own job now that a second observer sits above it; (6) the
              open row keeping its Delete pill for the length of the drag and only shutting when
              the task is dropped — right outcome, wrong moment, and for the wrong reason.
      Known:  (4) asks a design question as much as a correctness one — whether closing on scroll
              START reads as a dismissal the user caused or as the row being snatched. The
              alternative is worse and is why it was chosen: the row is content, it travels with the
              list, and one left open puts Delete under a thumb now aimed at a different task.
      Why:    `TaskSwipeDismissPolicyTest` pins the decision — the revoke, one-open-at-a-time, the
              row that never closes itself out from under its own finger, and the narrow disclaim
              that (6) must NOT be routed through — and `TaskSwipeRevealStateTest`'s drag-back round
              trip pins the arithmetic of (1). None of it can drive a pointer: there is no
              Robolectric and no Compose harness on this source set, so every gesture-arbitration
              question above is only answerable in a hand. (5) is the one that was asserted here
              before it was true, which is the argument for asking it on the device rather than
              from the diff: an interceptor on the wrong composable compiles, reads correctly, and
              silently sees nothing.

- [ ] **PR 41c · android · Reduce Motion takes the close's spring away and nothing else** — any of
      the four feeds, the app's own Reduce Motion switch ON (Settings, not the system slider).
      Do:     swipe a row open, then close it four ways: tap its own body, tap a different row, tap
              the header, and scroll. Then turn the switch off and do it again.
      Watch:  with the switch on, the row is simply closed on the next frame — no ~340 ms travel and
              no wait of any kind. The reveal itself still buzzes at the detent on the way open:
              reduce motion silences animation, not feedback.
      Fails:  the row still springs home with the switch on; the actions blink or the row flashes
              through an intermediate position on the way; a close that snaps but leaves the next
              swipe of that same row unable to buzz (the snap has to re-arm the detent exactly as
              the spring's last frame did); or the haptic disappearing along with the animation.
      Why:    Compose's own `MotionDurationScale` covers the SYSTEM animator setting and is blind to
              the app's switch, which is why this was a ~340 ms spring for a user who had asked for
              none. Nothing on this source set can run a Compose animation, so the branch is a
              reading of the code until somebody watches it.

- [ ] **PR 43 · android · Undo takes the paper with the row it brings back** — THE CLIENT THE BUG
      WAS REPORTED ON, and the screenshot came from this one. A custom list with exactly one task
      left, run twice. Once where that task is dated today or later, so undoing it puts a CURRENT
      row back. Once where the one remaining task is itself **overdue**, so undoing it puts an
      OVERDUE row back — that second one is the screenshot. The two are different screens rather
      than two runs of one: `nonEarlierSectionsEmpty` excludes the Overdue/Earlier bucket on
      purpose (finishing today's work while overdue tasks wait still earns the payoff), so a
      restored current row moves it and a restored overdue row does not. A list holding one current
      task and one overdue task is the FIRST case — ticking the current one restores a current row.
      Then run the plain undo once more on the **Anytime** tab's own feed, whose inline scene had no
      exit at all until this PR.
      Do:     tick the last task, and while the paper is still in the air tap **Undo** in the toast.
              Aim for the first half of the flight — inside about a second — so there is a burst
              left to interrupt.
      Watch:  the pieces keep FLYING as they go — still travelling, still spinning, still flipping —
              and fade out over `Quick` while they do. On the plain setup the full-screen scene
              fades out on that same rung, so paper and scene leave as one thing rather than the
              scene cutting from over paper that is still in the air. On the Anytime feed the scene
              fades AND closes its ~42% gap on the same `Quick`, so the Completed tile and the list
              rows under it take that space back over the fade instead of jumping up into it.
      Also:   on the OVERDUE setup, expect to see two "no tasks" scenes for about 150 ms and do not
              file it. Ticking the only overdue task leaves the scope with no Earlier items at all,
              so the celebration is drawn by the FULL-SCREEN overlay; undoing it puts the Overdue
              section back, which fades that overlay out over `Quick` while the INLINE scene expands
              in under the Overdue header on its own 190 ms. That hand-off is the v0.7.25
              presentation meeting the new exit, and the restored row is in the Overdue section
              above both of them. What would be a real failure is the inline scene arriving with
              confetti of its own — the cancel is what stops that, and the burst must not restart.
      Also:   a second completion straight after an undo must celebrate normally — tick it off again
              and the full burst plays, because the newer stamp re-opens the window. Typing a new
              task while the paper is up must end it the same way. And with a collaborator or a
              second device: empty the list from the other end (the burst plays here), then undo it
              there — the paper must go on this device too, without the scene flickering back.
      Reduce: with the app's own motion preference off (Settings → Motion), repeat the plain undo on
              both the list screen and the Anytime feed. There was never any paper to take away, so
              there must be nothing at all: no pause, no held frame, no `Quick` of anything, and the
              scene gone on the frame the row comes back.
      Fails:  the burst carrying on over the restored row and expiring on its own a second or two
              later, which is the report. Also a fail, and the reason this is not a one-line change:
              the paper vanishing between two frames on the tap, which is the same complaint one
              layer down; the pieces FREEZING and then dissolving in place, which would mean the
              envelope is being applied to a stopped clock rather than multiplied into a running
              one; the burst restarting from the launch fan, which would mean the draw loop was
              re-armed; and on the Anytime feed the gap snapping shut after the fade rather than
              closing under it.
      Why:    there is no device on the machine this was written on. JVM tests pin the decisions —
              `ShouldCelebrateEmptyStateTest` covers the overdue case a transition-shaped fix
              misses, `PendingRowArrivedTest` the arrival that writes the cancel,
              `FloaterEmptySceneTest` that the Anytime item outlives the frame its scene stops being
              visible, and `TdayConfettiKinematicsTest` the envelope's curve and its `Quick` rung —
              and not one of them can say whether forty-six pieces look like paper leaving or
              whether the gap under them closed smoothly. That is the whole of what this row is for.

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

- [ ] **PR 8g · ios · Eleven springs changed their spelling and not their shape** — a build of the
      app with a signed-out start, so the onboarding wizard is reachable. No throttling and no
      settings to change; this row is the one in the file whose whole claim is that it looks
      identical to the last build.
      Do:     step forward and back through the wizard (mode → server → login, then into create
              account and into security questions), and take both flow exits out of it — "Back" out
              of the security-questions step and "Change setup". Then open and close the search bar
              on Settings and on the Guide, twice each. Then from the sign-in card open "Forgot
              password" and move between its steps.
      Watch:  nothing new. Every one of those is the same short, barely-bouncing spring it has
              always been — the panel swaps under about a third of a second with a single soft
              settle at the end, the search bar snaps open at that same weight, and the
              forgot-password card changes step the same way the wizard's does. The eleven sites now
              read the numbers out of the token rather than writing them down, so a difference of
              any kind is the bug.
      Fails:  anything that reads slower, looser or bouncier than it did — that would mean a call
              site picked up a spring that is not Snappy. A step that hard-cuts instead of springing
              is the other half of the same failure: a `withAnimation` that stopped opening a
              transaction at all. Both of these are per-site, so name the screen.
      Why:    iOS does not compile on the machine this was written on, so the only gates it passed
              are textual — the literal counter fell by exactly the twenty-two it should have — and
              xctest in CI. Neither of those can see a spring play.

- [ ] **PR 8h · ios · The four feed screens' search bars now name the spring they always used** — a
      signed-in build with enough history that Completed and the calendar have rows to filter. Same
      shape of row as 8g's above: its whole claim is that nothing looks different.
      Do:     open and close the search bar twice on each of the four feeds — Todos (both of them:
              the list's own search and the floater task home's), Completed, Calendar, and the
              scheduled-task home. Type into one and close it from the X as well as from the back
              gesture, so the close path runs with a populated field and with an empty one.
      Watch:  the bar expands and collapses at exactly the weight it did before — a short spring,
              one soft settle, no overshoot worth naming — and the feed under it reflows at the same
              moment it always did. Query text still clears on close, which is the behaviour the
              comment next to each of these functions promises and is untouched here.
      Fails:  any of the eight reading slower, looser or bouncier than its siblings, which would
              mean a call site picked up one of the 0.24/0.9 or 0.26/0.9 springs these same files
              still carry. A bar that hard-cuts open instead of springing is the other failure: a
              `withAnimation` that stopped opening a transaction. Both are per-site, so name the
              feed and say open or close.
      Why:    no Swift toolchain here, so the gates this passed are textual — `ios.spring` fell by
              exactly the sixteen literals that came off, and `TdayMotion.snappy` reads the same
              0.28/0.86 out of `TdayMotionGenerated` — plus xctest in CI. Neither can watch a search
              bar open.

- [ ] **PR 8m · ios · Two rungs stopped being spelled as numbers, and nothing retimed** — any build,
      signed in or not; the guide is reachable without an account and the wizard is what a fresh
      install opens on. The narrowest row in this batch: five `duration:` numbers became
      `TdayMotion.Durations.quick` and `.enter`, which hold the same 0.15 and 0.2, so every frame
      should be the frame it was.
      Do:     open Help & Guide and expand and collapse four or five topic cards, including one
              long enough to push the cards below it well down the screen. Then delete and
              reinstall, or sign out, and walk the onboarding wizard through a connect, a sign-in
              with a wrong password, and a security-question step, so each of the four loading
              flags actually flips.
      Watch:  a topic card opens and closes in about a sixth of a second, eased at both ends, with
              the cards below it sliding rather than jumping. In the wizard, the spinner and the
              content it replaces cross in about a fifth of a second on each of the four
              transitions, at the same weight as the step change beside them.
      Fails:  a loading swap that reads noticeably faster or slower — the only way that happens is
              a site picking up the wrong rung. Motion that leaves a longer tail than it did is the
              more likely shape of the mistake: it would mean a site moved onto
              `TdayMotion.standard(duration:)` instead of keeping SwiftUI's `.easeInOut`, which
              this unit deliberately did not do.
      Note:   the Guide half of this row has since been overtaken. PR 42c retimed the topic card to
              320 ms on the Standard curve on purpose, so a card that no longer opens in a sixth of
              a second is 42c working, not this unit failing — check the card against 42c's row
              below and this row against the wizard only.
      Why:    no Swift toolchain here, so this unit's gates are textual — `ios.easeDuration` fell by
              exactly the five literals that came off, and both constants read out of
              `TdayMotionGenerated` at the values the call sites had typed — plus xctest in CI.
              Neither can watch a card expand.

- [ ] **PR 42c · ios · The Guide's topic card stops opening at the speed of a button press** — any
      build; the guide is reachable without an account. Best on a topic long enough that opening it
      pushes the cards under it well down the screen — "What's new" entries are usually the longest.
      Do:     open Help & Guide, expand a long topic, then expand a second one so the first collapses
              in the same transaction. Do it once more watching only the chevron.
      Watch:  the card grows over about a third of a second, not the sixth it used to take, and the
              cards below it travel with it rather than being shoved. The chevron turns on that same
              clock — one transaction drives both, so they start and stop together. The curve has a
              shorter tail than the one it replaces, so the longer motion should not also read as a
              slower one: it should settle rather than coast.
      Fails:  a card that still snaps open — that is the old 150 ms, and it means the transaction did
              not pick up the new spec. A chevron that turns on a different clock from the box, which
              would mean the rotation left the transaction. And a motion that reads sluggish at the
              end rather than settling: that is `.easeInOut`'s longer tail, so the curve did not move
              with the rung.
      Also:   with **Settings → Accessibility → Motion → Reduce Motion** on, expand and collapse the
              same topics. The body is simply there, fully drawn, on the frame of the tap, and the
              chevron is already turned — no growth, and no wait where the growth would have been.
              Collapsing is the same in reverse: the card is at its closed height immediately.
      Why:    there is no Swift toolchain on the machine this was written on, so nothing here was
              built. The guardrails prove the transaction still exists and that the budget did not
              move; only a device can say whether 320 ms on Standard is the right length for this
              card, which is the whole of the `disclosure-expand-collapse` claim on iOS.

- [ ] **PR 42f · ios · The counts roll their digits instead of swapping them** — any build with a
      handful of scheduled tasks and at least two lists that have tasks on them, plus one Anytime
      list on the Todos screen. Checked twice, the second time with Reduce Motion on.
      Do:     on the root feed task tab, create a task first. Creating rewrites the cache and the
              dashboard summary at once, so the big date-card count rolls up on the spot, along
              with the tile and list-row counts for whatever the new task lands under — put one
              under a category tile (Today, Priority, Overdue) and one on a named list, so a 26 pt
              tile count and a 22 pt list-row count each move too. Then complete a task, and keep
              the screen up while you do: completing stages the row out of the list immediately
              but does not rewrite the summary those three counts read, so the number does not
              move on the tick. It rolls down about eight and a half seconds later, when the Undo
              snackbar's window closes and the completion commits. Then the Todos screen and a
              floater list card, which is the one exception — that count is tallied off the rows
              held in memory, so it drops on the frame the row leaves.
      Watch:  each count's digits roll over about a quarter of a second — the old glyph travelling
              out as the new one travels in, in the same slot — rather than one number replacing
              another between two frames. The 34 pt one on the date card is the one to judge; the
              others are the same motion at a size where it is easy to miss. Nothing beside a
              count moves while it rolls: the date label, the tile title and the list name hold
              still, and neither card nor row changes height.
      Fails:  a count that hard-swaps at the moment it changes — that is the modifier not reaching
              the label. Silence after a tick on the three summary-backed counts is not that: the
              new number is not due yet, and the swap to watch for there is the one that lands
              when the Undo window closes. A whole
              number cross-dissolving as one blurry block instead of per-digit, which would mean
              `.numericText` is not what is playing. A count that rolls noticeably longer than the
              row's own check-off fade beside it, which would mean it picked up `Emphasis` rather
              than `Change`. And a two-digit count where only one digit moves and the layout
              jitters sideways as the width changes.
      Also:   with **Settings → Accessibility → Motion → Reduce Motion** on, create and complete
              again on all four surfaces, on the same clock as above. The new number is simply
              there, whole, on the frame the count changed — no roll, and no pause where the roll
              would have been.
      Why:    there is no Swift toolchain on the machine this was written on, so none of it was
              built. `motion-reachability-ios` pins the pair textually — each of the four labels
              carries `.contentTransition(.numericText(`, an `.animation(_:value:)` keyed on
              `count`, and a spec that opens on `tdayAnimation` — and the budget did not move. No
              static rule can see the roll itself: whether SwiftUI plays it per-digit, whether 260
              is the right length for it and whether it reads at 34 pt are the whole of the claim,
              and only a screen answers them.

- [ ] **PR 32a · ios · The splash hands over to the first screen instead of being cut out** — a
      cold launch, which means force-quitting the app between every run: this is the one motion
      here that only plays on a launch that had no process to return to. Checked twice, the second
      time with Reduce Motion on, and worth doing once on a device slow enough (or a network poor
      enough) that the splash is up for more than a blink.
      Do:     swipe the app out of the app switcher, wait a beat, and launch it from the home
              screen. Watch the moment the splash stops being on screen — not the launch itself.
              Repeat it signed in with a workspace, and again in local mode, which reaches the
              same boundary by a different bootstrap. Then launch once more and hold a finger down
              on the splash while it is up: that pins it (`isLaunchSplashHeld`), so the bootstrap
              can finish underneath and the hand-over plays when the finger lifts instead.
      Watch:  the splash fades away while the first screen fades up in its place, over about a
              fifth of a second, and the two halves overlap — at no point is the screen empty or
              showing both at full strength. The first screen is complete when it appears: the
              feed, the dock and the create button are all where they belong rather than arriving
              after it. The held-finger launch does the same thing on release, once, not twice.
      Fails:  the splash disappearing between two frames with the feed simply there — that is the
              transaction not reaching the arms, and it is exactly what this row exists to catch.
              A fade that is visibly longer than a route change inside the app, which would mean
              the rung drifted. A splash that dims out and leaves the screen blank before the app
              arrives, which would mean the two halves are running one after the other rather than
              across each other. And the held-finger case playing the fade twice, or playing it on
              the press rather than on the release, which would mean the boundary is keyed on the
              two properties separately rather than on `showsLaunchSplash`.
      Also:   watch the TAGLINE across the whole splash, on the same launches. It is drawn by two
              different view instances — `TdayApp`'s while `AppContainer` builds, `AppRootView`'s
              until the bootstrap finishes — and `launchTagline` is a process-wide global so both
              draw the same one. It must not change at any point while the splash is up; a line
              that swaps part-way through is that global having gone back to being per-view state,
              and it puts a hard cut on the one boundary above this that has nothing to fade it.
              A different line on the NEXT launch is correct and expected.
      Also:   with **Settings → Accessibility → Motion → Reduce Motion** on, cold launch again. The
              first screen is simply there, whole and finished, on the frame the bootstrap
              completes — no fade, and no pause where the fade would have been. A launch that
              takes measurably longer with the setting on is the fifth idiom rule broken from the
              side nobody watches.
      Why:    there is no Swift toolchain on the machine this was written on, so none of it was
              built. `launch-handover.test.ts` pins the shape textually — a `.transition` on each
              arm naming its curve, the `Group`'s `.animation(_:value:)` keyed on
              `showsLaunchSplash`, both resolving through `tdayAnimation`, and no numeric duration
              in the block — and the budget did not move. What no static rule can see is whether
              SwiftUI actually plays a `Group`'s two arms across each other on a cold launch, when
              the first frame of the app is also the first frame of a bootstrap that has just
              finished. That, and whether 200 ms is the right length for the one motion every user
              sees, are the whole of the claim.

- [ ] **PR 32b · ios · The six home tiles zoom into the screens they open** — an iOS **18** device
      or simulator, on the scheduled home. Then the same build on an iOS **17** one, which is the
      half nothing here can check: the deployment target is 17.0 and both APIs are 18.0, so the
      whole feature is behind an `#available` branch that no machine in this repo can execute.
      Do:     tap each of the six category tiles in turn — Scheduled, Priority, Overdue, All,
              Completed, Calendar — and watch the push, then swipe back from the left edge and
              watch the return. Then open **All** a second way: type into the home screen's search
              field and tap a result, which pushes the same All screen with a highlight id.
      Watch:  the pressed tile grows into the screen it opens, from its own rectangle and its own
              corner radius, and the back swipe shrinks it home to the same tile. The other five
              tiles stay where they are. The search result does NOT zoom — it pushes with the
              stock slide, because nothing on screen was pressed to reach it.
      Fails:  a stock slide on any of the six, which means the source and the destination did not
              agree on an id and SwiftUI fell back without saying so — the one failure mode of
              this unit that reports nothing anywhere. A screen growing out of the WRONG tile,
              which is `zoomRoute` and `action` disagreeing at a construction. The interactive
              back swipe losing the zoom and dropping to a slide only on the way back. And the
              search-result arrival zooming out of the All tile, which is the animation claiming
              the user pressed something they did not.
      Also:   on an **iOS 17** device, run the same six taps. Every one of them is the stock push,
              the screens are correct, and nothing is missing or misdrawn — the availability
              branch is the one thing in this unit that compiles nowhere if it is wrong and is
              checked by nothing on the machine this was written on.
      Also:   with **Settings → Accessibility → Motion → Reduce Motion** on, tap three of the six.
              Each is the stock push: the platform's own substitute for a large-amplitude travel,
              which still puts the finished screen in front of the user and adds no wait
              (`docs/motion.md`'s fifth idiom rule). A tap that is slower with the setting on, or
              one that still zooms, is the gate not reaching one of the two halves.
      Why:    there is no Swift toolchain here, so none of this was built. `ZoomNavigationTests`
              pins the id table in CI — six routes, six distinct ids, and none for a highlighted
              All arrival — and `launch-handover.test.ts` pins the wiring textually: both APIs
              under `#available(iOS 18.0, *)`, each tile publishing the id of the route its own
              closure pushes, one namespace, one destination site. What none of it can see is
              whether SwiftUI actually finds the source rectangle for a tile that lives three
              levels inside a `ScrollView` in a private struct two files from the destination —
              which is the entire feature.

- [ ] **PR 184b · ios · The dock folds once instead of flickering at the fold point** — both root
      feeds (the scheduled home and the Anytime home), each with enough tasks to scroll well past
      the hero header. The iOS twin of PR 184a's Android row, and the same defect: one comparison
      with no memory, on a fold point all three clients share.
      Do:     scroll down slowly until the dock folds to its pill and hold the finger still there;
              then lift, let the feed settle out of its own deceleration, and scroll back up in
              small steps, watching where it opens. Then switch feeds with the dock folded, and
              switch back.
      Watch:  one crossing each way. It folds about 44 pt in and stays folded with a finger parked
              at that distance, and while the feed rocks a point either way as it settles. On the
              way back up it opens about 20 pt higher than it folded, so the two events are
              visibly at different heights. Then the other feed: the two share one dock and the
              one in front of you owns it, so the dock matches the feed you are looking at and
              not the one you left.
      Fails:  the pill and the capsule alternating under a held finger, or on the last frames of a
              settle — the single-threshold behaviour this row exists to catch; and, the other
              way, a deliberate scroll all the way to the top arriving with the dock still folded,
              which would mean the release edge is too low to reach. Also a fail: switching feeds
              leaving the dock in the other feed's state.
      Also:   the hero header itself must be untouched — the title docking into the bar, the mark
              fading, the pull-to-refresh pill. The observer that publishes the offset those run
              on is the one that stopped taking a threshold, and a mistake there shows up in the
              header rather than in the dock.
      Why:    there is no Swift toolchain on the machine this was written on, so none of it was
              built here. `RootFeedDockCollapseTests` pins the fold itself in CI — the four-step
              fold through 40, 46, 30 and 20, and a bounce above the top — and
              `ios-target-membership.test.ts` pins that the test is registered in the pbxproj
              rather than sitting on disk unbuilt. What none of that can see is whether the two
              feeds still reach the function at all, which is the whole of what this row is for.

- [ ] **PR 10 · ios · The bar buttons go down instead of landing** — the other part of
      `press-affordance-unification` with no row anywhere, appended by 9b5 as it closes the box.
      Any build. The round buttons in the top bar that wear `TdayToolbarButtonStyle`: the root
      feed hero header's pair and the list screen's.
      Do:     press and HOLD each in turn, watching the circle rather than the ripple, and let go.
              Then press and hold the create button in the same sitting, so the two depths are
              compared against each other rather than each against a memory.
      Watch:  the circle shrinks and sinks about a point while the finger is down — a short trip
              each way rather than a state change — and the create button beside it travels
              visibly further and drops twice as far.
      Fails:  a bar button whose squash cannot be seen at all, which is the 0.95 this replaced:
              on a 56 pt circle that is half a point of travel and reads as a tap landing rather
              than a button going down. Also a fail: a bar button dropping 2 pt, which would be
              the create button's modifier reaching a surface it is not for, and the two depths
              becoming indistinguishable when pressed one after the other.
      Why:    there is no Swift toolchain on the machine this was written on. The number is pinned
              by `motion-parity.test.ts`, which asserts `Bar` is 0.94 in all three generated
              artifacts, and nothing in the repository can say whether the button wearing it moves.

- [ ] **PR 41b · ios · The swipe row's reveal has a detent in it** — Today, Todos, Calendar or
      Completed, a list with several tasks, Settings ▸ Sounds & Haptics ▸ System Haptics on, and
      the phone out of a case thick enough to eat them. Two rows are wanted: an ordinary one with
      three pills (Edit/Copy/Delete, 228 pt of reveal, detent at 73 pt) and one that carries the
      mode's fourth pill — Schedule on a floater, Float on an overdue task — at 304 pt of reveal
      and a detent at 97 pt.
      Do:     (1) drag the three-pill row left slowly and stop the instant you feel something,
              then look at how far it has actually gone and whether the pills are visibly on
              their way; (2) hold still right there for a second or two and jiggle a few points
              either side of it; (3) let go, then tap a pill and pay attention to the two buzzes
              back to back; (4) from closed, flick the row left hard and short — 20 pt and the
              finger is gone — so it opens on speed alone; (5) do (1) again on the four-pill row.
      Watch:  (1) the buzz arrives around 73 pt of travel and reads as the actions catching under
              the thumb rather than as a notification landing — the question this row exists to
              ask is whether 32% is where the hand expects the catch, or whether it comes too
              early to mean anything. (2) one buzz and then nothing, however long you hold and
              however much you jiggle. (3) the reveal (`.rigid` at 0.7) and the pill's own tap
              (`.light` at 0.6) must be two different things in the hand a second apart — the
              first sharp, the second soft; if they are the same buzz, the pair
              `HapticManager.reveal` is documented against has collapsed. (4) the flick's buzz
              lands at lift-off instead of mid-drag, and the two arms should still read as the
              same event happening at the only moment each of them can. (5) the four-pill row's
              detent is 24 pt further out, since the fraction is of a wider reveal: the second
              half of the same question, which is whether one fraction can serve two widths or
              whether the detent should be a distance.
      Fails:  a buzz that repeats, ticks or rattles while the finger rests at the detent; two
              buzzes in one drag from crossing, coming back and crossing again; any buzz on the
              way closed, or on a row shut from under you by opening a different one; a second
              buzz as the row springs open after release; a buzz while dragging a row that is
              already open; and the flick arm feeling heavier or different from the detent arm.
      Known:  cross the detent, drag back and release closed, and you have felt a reveal that did
              not happen. That is what a detent on a physical control does, it is pinned by a
              test that says so, and it is not a fail here — the alternative is silence until the
              row settles, which costs the feature its point. Also not a fail: a plain tap on the
              row buzzes as it plays its 28 pt hint. That is the pre-existing tap path, Android
              has no equivalent, and closing that divergence either way is a felt change to a
              shipped gesture that was not asked for here.
      Why:    there is no Swift toolchain on the machine this was written on and no gate in the
              repository can feel a haptic. `TaskSwipeRevealDetentTests` pins the decision — one
              buzz per crossing, none while hovering, none on a closed release, one at the flick
              that never crossed — and `ios-target-membership.test.ts` pins that the test is
              registered in the pbxproj rather than sitting on disk unbuilt. What none of it can
              say is whether 73 pt is where the catch belongs, or whether the two generators are
              still telling two events apart in a hand.

- [ ] **PR 41c · ios · An open swipe row goes away when you touch anything else** — Today, Todos,
      Calendar or Completed, a list long enough to scroll, one row swiped fully open, and for the
      last two rows Settings ▸ Accessibility ▸ Voice Control and VoiceOver to hand.
      Do:     (1) with a row open, tap the dock, then the FAB, then the header's search capsule,
              then the gap between two rows, then a different row's checkbox — one at a time,
              re-opening the row between each; (2) with a row open, start a slow scroll with the
              finger landing ON the open row itself, and separately two rows below it; (3) with a
              row open, drag it back to the right in one movement, including starting from the
              very left edge of the screen; (4) open a row, push to another screen and come back;
              (5) with Voice Control on, say "swipe left" at a row; (6) with VoiceOver on and a
              row somehow open, do the two-finger scrub.
      Watch:  (1) every one of them closes the row AND does its own job in the same touch — the
              dock switches tab, the FAB opens its sheet, the capsule takes focus, the other
              row's checkbox ticks. A tap that closes the row and nothing else is the fail, and
              it is the one this design refused on purpose. (2) the row closes as the list starts
              moving, not when it stops, and the scroll itself is not swallowed or stuttered.
              (3) the row follows the thumb the whole way and settles closed, and the system's
              interactive pop does not take the gesture instead — these two travel in the same
              direction over the same pixels, which is the reason iOS does not intercept back
              here. (4) the row is closed on return. (5) the reveal actually opens. (6) the row
              closes.
      Fails:  any of (1) closing the row while the thing under the finger does nothing; the row
              surviving a scroll, or closing while the finger is still dragging it in either
              direction; a sheet, the dock or a swipe-back behaving differently from before, in
              which case the window recognizer's `cancelsTouchesInView = false` is not doing what
              it says; the row still open on return in (4); and in (5) nothing happening at all.
      Known:  (5) is the most important row here and the one with the least behind it.
              `gestureRecognizerShouldBegin` gates the reveal on `horizontalVelocity > 45`, and
              whether Voice Control's synthesised pan clears that gate decides whether an
              assistive user can open the reveal AT ALL. If it does not, (6) is unreachable in
              practice and the `.escape` action added here is insurance rather than a route —
              which is worth knowing either way, and is a finding rather than a fail for this PR.
      Also:   the close now honours Reduce Motion — with it on, the row is drawn home rather than
              springing there, including on the existing pill closes. Gated in two places and
              two only: `closeActions`, which every dismissal funnels through, and the pan's own
              `.ended` settle, which is the close that runs when a thumb drags an open row back
              and lets go under the detent and which is in a UIKit coordinator with no
              environment to read. Both are handed the same resolution, so the row cannot shut
              at two different speeds depending on who shut it. Confirm BOTH: the pill path and
              the drag-back in (3) must feel the same kind of instant, not one instant and one
              springing for a third of a second.
      Why:    there is no Swift toolchain on the machine this was written on.
              `TaskSwipeDismissPolicyTests` pins the decision and `TaskSwipeRevealDetentTests`
              pins the drag-back round trip from a nonzero resting offset — the assertion that
              would catch a `.changed` seeding from zero, which closes the row correctly and
              jumps a full reveal width doing it. What none of it can say is whether a
              window-level tap recognizer leaves the rest of the app's hit testing alone, which
              is what (1) is really asking.

- [ ] **PR 43 · ios · Undo takes the paper with the row it brings back** — a list with exactly one
      task left, run twice. Once where that task is dated today or later, so undoing it puts a
      CURRENT row back. Once where the one remaining task is itself **overdue**, so undoing it puts
      an OVERDUE row back — that is the screenshot, it is the setup that matters, and the two are
      genuinely different screens rather than two runs of one. Every scope's "is this finished"
      predicate excludes the Overdue/Earlier bucket on purpose (finishing today's work while
      overdue tasks wait still earns the payoff), so a restored CURRENT row moves it and the scene
      leaves, while a restored OVERDUE row moves it not at all and the scene stays. A setup with
      one current task and one overdue task waiting is the FIRST case, not the second: ticking the
      current one and undoing it restores a current row.
      Do:     tick the last task, and while the paper is still in the air tap **Undo** in the toast.
              Aim for the first half of the flight — inside about a second — so there is a burst
              left to interrupt. Then run the overdue setup and watch the Overdue header.
      Watch:  the pieces keep FLYING as they go — still travelling, still spinning, still flipping —
              and fade out over about 150 ms while they do. On the plain setup the scene goes with
              them and lands after: the paper finishes leaving at 0.15 s, the illustration at 0.32 s,
              in that order. On the overdue setup the scene STAYS exactly where it is under the
              Overdue header, which is the designed v0.7.25 presentation and not a bug — only the
              confetti leaves, and the restored row is back in the Overdue section above it.
      Fails:  the burst carrying on over the restored row and expiring on its own a second or two
              later, which is the report. Also a fail, and the reason this is not a one-line change:
              the paper vanishing between two frames on the tap, which is the same complaint one
              layer down; the pieces FREEZING and then dissolving in place, which would mean the
              envelope is being applied to a stopped clock rather than multiplied into a running
              one; and the scene on the plain setup cutting out from over paper that is still
              fading, which would mean the host left before its overlay did.
      Also:   a second completion straight after an undo must celebrate normally — tick it off
              again and the full burst plays, because the newer stamp re-opens the window. And with
              a collaborator or a second device: empty the list from the other end (the burst plays
              here), then undo it there. The paper must go on this device too, without the scene
              flickering back.
      Reduce: with **Settings → Accessibility → Motion → Reduce Motion** on, repeat the plain undo.
              There was never any paper to take away, so there must be nothing at all: no pause, no
              held frame, no 150 ms of anything. The row comes back on the next frame.
      Why:    there is no Swift toolchain on the machine this was written on. `xctest` pins the
              decision — `ShouldCelebrateEmptyStateTests` covers the overdue case the naive fix
              misses, and `TdayConfettiKinematicsTests` pins the envelope's curve and its Quick rung
              — and neither can say whether forty-six pieces fade while still in flight or whether
              the scene above them waited. That is the whole of what this row is for.

- [ ] **PR 32c · ios · The other four tiles grow into their screens too** — an iOS **18** device or
      simulator, on the scheduled home and on the Anytime feed. `PR 32b` checked the six category
      tiles; this checks the four beside them, and one of the four is the first shared element in
      this app to live in a `List` **cell** rather than a `LazyVStack` sibling — the two Anytime
      cards are `Section` rows in a `.listStyle(.plain)` List, and whether SwiftUI finds a source
      rectangle there is the whole question this row exists for. Nothing here can be read off
      source and there is no Swift toolchain in the repo.
      Do:     tap the **Today** card, a **custom list row** on the scheduled board, the **Completed**
              card on the Anytime feed, and a **Floater list** card — one at a time. After each,
              swipe back from the left edge. Then do the same four on an iOS **17** device.
      Watch:  each screen grows out of the rectangle of the tile just pressed — its corner radius and
              its position — and the back swipe shrinks it home to that same tile. Today grows from
              the Today card, not from the board behind it.
      Fails:  a stock slide on any of the four, which means the source and the destination did not
              agree on an id and SwiftUI fell back without saying so. A screen growing out of the
              WRONG rectangle — most likely the **Floater Completed card growing out of the Scheduled
              board's Completed tile**, which is exactly the collision the two distinct ids
              (`home-tile.completed` / `floater-tile.completed`) were introduced to remove, and the
              one to look hardest for because both ids resolve while the two root feeds are
              crossfading.
      Also:   **do not press a tile at all** and open the same four destinations the other ways — a
              list from the sidebar, a list from the home screen's search results, and a deep link
              (`tday://todos/list/<id>/<name>`). Every one of those must be the stock push: nothing
              on screen was pressed to reach them, and a zoom there is the animation claiming the
              user did something they did not.
      Also:   tap the Floater Completed card **immediately after a root-feed tab swap**, while both
              feeds are still mounted in the `ZStack`. It must grow from the Anytime feed's own card.
              Growing out of the scheduled board's tile is the failure this row was written for.
      Also:   on an **iOS 17** device, all ten tiles. Every one is the stock push, the screens are
              correct, and nothing is missing or misdrawn — the availability branch is the one thing
              here that compiles nowhere if it is wrong.
      Also:   with Reduce Motion on, tap all ten. Each is the stock push: the platform's own
              substitute for a large-amplitude travel, still putting the finished screen in front of
              the user and adding no wait (`docs/motion.md`'s fifth idiom rule). A tap that is slower
              with the setting on, or one that still zooms, is the gate not reaching one of the two
              halves.
      Why:    no Swift toolchain here. What is verified locally is the wiring read as text:
              `ZoomNavigationTests` pins the id table in CI — ten distinct ids, six of them
              unchanged, and none for a non-tile arrival — and `launch-handover.test.ts` pins that
              each of the three source sites publishes the id of the route its own closure pushes,
              with the four new rules mutation-tested red. What none of it can see is whether SwiftUI
              finds the source rectangle for a tile three levels inside a `List` cell — which is this
              unit.

- [ ] **PR 32d · android · The ten Android tiles grow into their screens** — any Android device or
      emulator with animations on, then again with the device's animator scale at 0 and with the
      in-app **Reduce motion** switch on. This is Android's first shared-element transition and
      there is no prior art in this codebase to compare it against, so every one of these taps is
      new behaviour rather than a regression check.
      Do:     tap each of the ten in turn — Scheduled, Priority, Overdue, All, Completed, Calendar,
              the **Today** card, a **custom list** row, the Anytime feed's **Completed** tile, and a
              **Floater list** row. Then press back from each (the button AND, where the device has
              it, the gesture).
      Watch:  the pressed tile grows into the screen it opens — its rectangle, its corner radius, and
              no content stretching on the way — and back returns it to that same tile. Rows further
              down the feed shift rather than jump.
      Fails:  a crossfade on any of the ten, which means the key never matched and both halves went
              inert without saying so. A screen growing out of the WRONG tile. A destination that
              arrives already full-size with the tile still visible underneath.
      Also:   **arrive without pressing a tile** and confirm nothing zooms: a list from the Android
              launcher shortcut, a notification tap, a widget row (`tday://todos/create?target=today`
              and the widget's own create route), and the create flow's push onto Today. These set no
              origin, so all of them must be the ordinary hand-over. An origin flag that leaked would
              zoom out of a tile the user never touched.
      Also:   **Reduce motion ON** (the in-app switch), and with the device's animator scale at 0:
              every tap is the short fade, no zoom, and the destination is drawn and readable — not
              blank, not half-faded, and not slower than the tap. Then check the **search-close** on
              the scheduled home with the in-app switch on and the device scale at 1x: the wait before
              the results close must still cover the hand-over rather than firing instantly over an
              animation still playing.
      Also:   **predictive back** — start a back gesture and hold it. The dragged screen should recede
              and travel with the finger and finish where the back button would have; with Reduce
              motion on it should be a plain fade with nothing parked mid-recede.
      Why:    `TileTransitionKeyTest` pins the key table in CI and the web guardrails pin the wiring,
              but nothing here can see whether Compose finds a source rectangle for a tile inside a
              `LazyColumn` — which is this unit — and the predictive-back slot is scrubbed by a
              `SeekableTransitionState`, the one path a static spec cannot describe.

- [ ] **PR 32e · android · The tile zoom reads like iOS** — a device with animations on, then again
      with the in-app Reduce motion switch and with the device animator scale at 0. This rebuilds the
      transition so a surface grows and the content fades, instead of the tile's own icon and label
      scaling up into the screen; the open is checkable against the list below, and the CLOSE is the
      item that could not be settled from source at all.
      Do:     tap a category tile, then the Today card, then a custom list row, then the Anytime
              feed's Completed tile and one of its list cards. Then close each one, both with the back
              button and with the gesture. Repeat with Reduce Motion on.
      Watch:  (a) the SURFACE for the first frames past the tile should be the tile's colour at the
              tile's radius, squaring off as it reaches the screen — never a stretched picture of the
              tile's icon or label. (b) NOTHING scales: the tile's icon, label and count fade where
              they sit at their own size, and the screen's toolbar and back chevron are at final size
              and position from the first frame they appear. Failure is a toolbar arriving at about
              half scale and growing — the defect this rebuild exists for.
      Also:   (c) the corners travel: the shape leaving the tile carries the tile's radius and the
              shape arriving is square. A hard-edged rectangle over a rounded tile on the first
              frames, or a rounded rectangle held over the full screen that snaps square at the end,
              are both failures — the second is the bug this rebuild removed.
      Also:   **(d) the close, which is the one thing this unit could not check.** Expect the surface
              to shrink back into the tile while the screen fades out in place and the home feed fades
              in — the open, reversed. What to watch specifically is whether the SCREEN SLIDES
              SIDEWAYS as it goes: it is now a plain child of the destination, so it takes the
              NavHost's pop exit, whose quarter-width travel and recede the shared element used to
              cover by drawing nothing in place. A sideways slide against a shrinking surface is the
              failure. The fix would be a per-destination `popExitTransition` on the nine
              `composable(...)` blocks in `TdayApp.kt`, which trades against the predictive-back
              argument `route-handover.test.ts` documents — so it wants a decision, not a patch.
      Also:   (e) with Reduce Motion on: no surface and no zoom at all, the ordinary short fade, and
              in particular no stray full-screen background left behind the screen.
      Also:   (f) arrive at a tile route WITHOUT pressing a tile — a deep link, a notification, a
              widget row, the launcher shortcut. The screen must not grow out of a tile nobody
              pressed, and no stray background may appear behind it.
      Why:    `:app:compileDebugKotlin` and `:app:testDebugUnitTest` are green and the guardrails pin
              the key table and the origin gate, but nothing on this machine can render a frame. The
              animation's quality — and the close in particular — is only visible on a device.

- [ ] **PR 195 · ios · The calendar's docked title** — a phone, Calendar, scrolled until the bar has
      collapsed. Run it at the default text size and again at a large Dynamic Type size.
      Do:     scroll up until the title docks, then scroll back down to the top, slowly.
      Watch:  (a) the word arrives WHOLE and at full size — "Calendar" keeps all eight letters and is
              the same 32pt as the block's own copy it is handing off from, never smaller and never
              "Cale…".
              (b) over the handoff, roughly the last quarter of the scroll, the docked copy sits
              about 32pt to the LEFT of the expanded one. That is the accepted cost of the reserve
              and not a failure: the reserve falls through to per-side rather than shrink the word,
              and per-side gives up the bar-centring the mirrored branch exists for.
      Fails:  the title ellipsises at any scroll position; or the docked copy is drawn at a
              different SIZE from the expanded one while both are on screen — the failure this row
              exists for, and the one the old `minimumScaleFactor` produced.
      Also:   open the search field and close it; the title must come back whole rather than arriving
              mid-reserve. Then rotate to landscape and back.
      Why:    `TdayBarTitleReserveTests` proves the arithmetic — 93pt before, 157pt after, against a
              137.5pt word — and proves nothing about how a 32pt lateral shift reads while two copies
              of the same word cross-fade. That is the half only an eye can settle. iOS 17 and up.
