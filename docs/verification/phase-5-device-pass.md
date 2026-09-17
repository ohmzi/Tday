# Phase 5/6 device pass — TF1, the first look at iOS

Run against the release build that closes Phase 5/6. See `README.md` in this directory for the row
format and for how the file is closed out.

This is the file iOS rows land in long before Phase 5. There is no TestFlight cycle at a Phase 2
merge — cycles are spent at phase merges and there are exactly three in the whole programme — so an
iOS change that ships CI-green in Phase 2 waits here for the first cycle that exists. A row written
now and read in three phases' time is the point: it was written while the diff was open, by the only
person who knew what to look at.

Rows from Android and web PRs of Phases 5 and 6 belong in this file too, alongside the iOS backlog.

## iOS

- [ ] **PR 44 · ios · The create-sheet selector enters and leaves instead of popping** — any list,
      tap **+** for New task; do not touch a text field first, so the keyboard is down.
      Do:     tap the List row (repeat for Priority, Repeat, Due date and Due time; then close each
              one by picking a row, and once more by tapping the scrim).
      Watch:  the dimming and the picker fade up together over 220 ms, the card arriving through the
              last 3 % of its size; on the way out both fade over 200 ms.
      Fails:  the picker is simply present on one frame with the dimming already at full strength,
              or it disappears on one frame when a row is picked. Either is the overlay's
              `.transition` running with no transaction to start it, which is what this fixed.

- [ ] **PR 44 · ios · The picker arrives with the sheet, not over it** — New task with the Title
      field focused and the keyboard up, so the sheet card is riding ~300 pt above its resting
      place.
      Do:     tap the Priority row (repeat with Notes focused, which is the UITextView path).
      Watch:  one move. The keyboard drops, the card settles back down over the keyboard's own
              ~250 ms, and the picker fades up over 220 ms across that same window — they finish
              together.
      Fails:  the picker is fully opaque and still while the card is visibly still travelling under
              it. That is the defect: a finished picker sitting over a sliding card.

- [ ] **PR 44 · ios · Every way out of a sheet takes the keyboard with it** — New task with the
      **Notes** field focused. Notes is a UITextView behind a `UIViewRepresentable`, which is the
      case clearing `@FocusState` alone never covered.
      Do:     tap the header's X (repeat: type a title and tap the green confirm; then repeat both
              with the Title field focused instead; then once with the scrim, which always worked
              and is the control).
      Watch:  the keyboard starts down on the same frame the card does, and the two are gone
              together.
      Fails:  the card slides down in front of a keyboard still standing, or the keyboard is still
              up for a beat after the sheet has gone. On the confirm path, watch for the keyboard
              staying up for the whole save on a slow connection — that is the same defect with the
              `await` in front of it.

- [ ] **PR 16 · ios · The two create-account exits still swap at one speed** — onboarding wizard,
      past the server step, on the Sign in / Create account card.
      Do:     from Create account, tap "I already have an account" (repeat from the questions step:
              Back, then the same tap; and once more with "Change setup" from Create account).
      Watch:  the card's fields swap over the same ~0.28 s spring as a Mode or Server step change —
              First name and Confirm password grow and shrink the card with the rest of the panel.
      Fails:  the swap now runs at a different speed from a step change; or something that used to
              be still — the step chips, the hero tile, the card's height on the way back to Mode —
              slides or fades, which is the newly explicit transaction reaching further than the
              `.animation(_:value:)` chain it replaces did.

- [ ] **PR 10 · ios · Completion against deletion** — Today with 2+ pending tasks, System Haptics on
      (Settings › Sounds & Haptics), phone held rather than lying on a desk.
      Do:     tick the first task's circle, then swipe the second row open and tap Delete.
      Watch:  two haptics you could name blind — the tick is one rounded success pulse, the delete a
              single heavy thud that lands harder than the tick did.
      Fails:  the two are indistinguishable, or the delete is the lighter of the pair. Before this
              every one of these was a light impact at 0.6, so "both feel like a tap" is the old
              build.

- [ ] **PR 10 · ios · A reveal is not a tap** — any list with one pending task, its row closed.
      Do:     tap the row to bring out Edit / Copy / Delete, then tap Edit.
      Watch:  the reveal is a sharp, rigid click; Edit a second later is a soft light tap. Two taps
              a second apart on the same row, two different textures.
      Fails:  the two feel the same, or the reveal is the softer of the pair — it is the event, the
              action button is the cost of using the app.

- [ ] **PR 10 · ios · Pick-up against drop** — Today with 2+ scheduled tasks spread across the
      Morning / Afternoon / Tonight buckets.
      Do:     press and hold a task until it lifts, drag it into a different bucket, release.
      Watch:  two different haptics inside the one gesture — a medium impact at the moment the row
              comes loose, and a rounded success pulse when it lands.
      Fails:  one haptic across the whole gesture, or the lift and the landing feel identical. Both
              used to be the same light impact fired from a raw generator inside the screen.

- [ ] **PR 10 · ios · The tab change is a detent** — root feed, sitting on Today, header showing the
      ⊕ and ⋯ circles.
      Do:     tap the Anytime tab, then tap the ⊕ create-list circle in the header.
      Watch:  the tab change is one selection detent — the click a picker wheel gives; ⊕ is a soft
              light tap. Same bar, same finger, two events.
      Fails:  the two feel the same, which is the tab falling back to the plain control tap.

- [ ] **PR 10 · ios · Restoring is not completing** — Completed screen with at least one item in it,
      and a pending task on Today to compare against.
      Do:     tap the filled circle on a completed task to restore it, then go to Today and tick a
              pending task off.
      Watch:  the restore is a dull, soft impact — an undo; the completion is the rounded success
              pulse. The undo is plainly the duller of the two.
      Fails:  restoring feels like a completion, or gives the same light tap every other control in
              the app gives.

- [ ] **PR 10 · ios · The create sheet confirms once** — any list, tap **+** for New task, type a
      title.
      Do:     tap the green ✓ in the sheet header.
      Watch:  two beats in order — a light tap as the button goes down, then a single rounded
              success pulse when the task lands.
      Fails:  two success pulses in quick succession. That was the defect: the header button fired
              one on the press and `submit()` fired another when the save returned.

- [ ] **PR 10 · ios · The bar button goes down** — any root feed, header showing the ⊕ create-list
      circle (repeat on the Completed screen's ⌕, which wears the same lift).
      Do:     press and hold the circle without releasing.
      Watch:  the 56 pt circle visibly sinks while the finger is down — 0.94 of its size, about 3 pt
              off the width, plus a 1 pt drop — and comes back over 140 ms on release.
      Fails:  nothing moves under the finger, or the button only reacts once you let go. This is an
              absolute check, not a comparison: the depth went 0.95 → 0.94, which is half a point
              and is not what you are looking for — you are looking for a press that reads as a
              press at all.

- [ ] **PR 10 · ios · The list sheets confirm too** — root feed, header showing the ⊕ create-list
      circle; for the repeat path, a list you own, opened, with its ⋯ button in the top bar.
      Do:     tap ⊕, type a name, tap the green ✓ (repeat from ⋯ › List settings: change the name,
              tap the green ✓).
      Watch:  the same two beats the new-task sheet gives — a light tap as the ✓ goes down, then one
              rounded success pulse as the list appears (or as the renamed list comes back).
      Fails:  only the light tap, with no pulse behind it. Creating a list would then feel weaker
              than creating a task, which is the same event, and saving settings weaker than saving
              a task.

The rows from here on are Phase 6's, and they are a different kind of row: each one has a twin in
the Android or Web section below that is the same behaviour on another client. Run a pair together
rather than running this section and then that one — the pairs are why TF1 is the long cycle, and a
handover that is 150 ms on one client and half a second on another is a difference nobody can see
one screen at a time.

- [ ] **PR 27 · ios · The feed under the pill hands over instead of cutting** — root feed, on
      Today, with enough tasks on both tabs that the two bodies are plainly different.
      Do:     tap the Anytime tab in the dock, then tap back.
      Watch:  the arriving feed fades up over 150 ms while the leaving one fades out on the same
              clock, so the swap is finished before the segmented control's own indicator has
              stopped travelling. Nothing slides: both feeds are drawn in the slot the other had.
      Fails:  the body is the new feed on the frame after the tap while the pill is still moving —
              that is the defect. Also a fail: the body still resolving after the pill has landed,
              which would mean the swap is running longer than Quick.
      Superseded: by **PR 199 · ios** at the end of `phase-9-device-pass.md`. This row was written
              against a symmetric 150 ms crossfade on `Quick` and both its `Watch:` and its `Fails:`
              line encode that spec: the hand-over is now web's two-curve pairing on `Enter` (200 ms),
              so it deliberately does outlast the pill's spring and the second `Fails:` half now names
              correct behaviour as a failure. Run PR 199's pair instead. This row is kept as the record
              of what was asked at the time rather than as a row to run.

- [ ] **PR 46 · ios · Locking and unlocking is one event** — a server account, signed in, on the
      root feed. Sign out from Settings, then sign back in.
      Do:     sign out, and watch the whole screen rather than the wizard; then sign back in and
              watch it again.
      Watch:  one handover, 150 ms, everything inside it: the feed behind goes out of focus
              (a 6 pt blur, and a scale down of eight thousandths that reads as the blur's other
              half) while the wizard fades up over it. The dock and the create button do NOT
              travel — they are simply not there once the wizard is.
      Fails:  the blur arriving before or after the wizard does; or the dock and the create button
              flying up from under the bottom edge on the way back in, which is the duck below
              being driven by an event nothing else moves in.
      Known:  a COLD start straight into onboarding plays no enter at all — the wizard is drawn
              finished over a backdrop that was never in focus. That is deliberate: an insertion
              with nothing before it has nothing to hand over from. Only signing out and back in
              animates.

- [ ] **PR 30 · ios · The chrome ducks out rather than being taken away** — root feed, dock and
      create button both on screen.
      Do:     tap the search capsule in the header to expand it, then close the field again.
      Watch:  the dock and the create button travel straight down and out through the bottom edge,
              fading as they go, over about 0.4 s, and come back up the same way at the same
              length. They are gone rather than parked: nothing is left sitting in the home
              indicator strip.
      Fails:  either control ceasing to exist in one frame, leaving a hole where the chrome was;
              or the two running at different lengths; or a ghost of the dock still visible over
              the home indicator once the field is open.

- [ ] **PR 12c · ios · The check-off has a beat in the middle** — Today with 2+ pending tasks, and
      a Calendar day with at least one task on it. System Haptics on, ringer NOT silenced — the
      Calendar row is the one this PR fixed and it is the sound that was missing.
      Do:     tick a task's circle on Today; then tick one on a Calendar day.
      Watch:  the Calendar row answers exactly as the Today row does — one rounded success pulse
              AND the completion sound on the tap — and then plays the same four beats at the same
              lengths: the circle fills, the rule lands across title and notes 160 ms later while
              the title dims over 320 ms, the strike holds for 360 ms, and the row's ink fades over
              260 ms. Tick one on each screen in turn and the two rhythms are the same rhythm.
      Fails:  the Calendar tick landing in silence, or with the plain light tap every other control
              gives — that is the defect this row exists for. Also a fail: the Calendar row
              finishing visibly sooner or later than the Today row, or the rule arriving on the
              same frame as the tick with no gap in between.
      Known:  the rule itself does not sweep on iOS. `.strikethrough()` is a boolean SwiftUI will
              not tween, so what moves on that beat is the title's colour; Android sweeps the rule
              and web fades it in. Three mechanisms, one rhythm — the rhythm is what this row
              checks, and a reviewer running the pair side by side should not file the difference.

## Android

Android costs a cable rather than a cycle, so these are run in the same sitting as the iOS rows
above and against the signed APK the Phase 5/6 merge builds. Each row here whose twin is in the
iOS section says so; run the pair side by side.

Every row below is run with the system animator scale at its default 1x and the in-app motion
switch on. The motion-off half of each of them is Phase 8's business — that is the phase that
built the preference and the file that checks it.

- [ ] **PR 29 · and · The search capsule grows out of its button** — root feed, header at full
      height (scrolled to the top), so the capsule is the wide pill rather than the folded circle.
      Do:     tap the capsule to open the search field, then close it.
      Watch:  the capsule travels and grows to the full width of the row over 320 ms, and the three
              controls it is taking the row from — the time-of-day mark on the left and the two
              round buttons on the right — fade out over 150 ms while it does. The title fades on
              that same 150 ms.
      Fails:  the capsule at full width on the frame after the tap. Also a fail: a sibling blinking
              to alpha 0 rather than fading, or the mark and the buttons still fading after the
              capsule has arrived.

- [ ] **PR 29 · and · The sun is not still up at 9pm** — the root feed's task tab, whose hero mark
      is the time-of-day one. Getting to the check is the work: either leave the app open on the
      feed across the 06:00 or the 18:00 boundary, or park it on the feed and move the device clock
      across one of them in Settings.
      Do:     cross the boundary with the feed on screen and keep watching the mark for a minute.
      Watch:  the glyph turns over within a minute of the boundary — sun above 06:00, moon from
              18:00 — and it turns over without animating. It is a swap, not a crossfade.
      Fails:  the mark still showing the sun at 9pm in a session that was opened in the afternoon.
              That is the defect: the hour was sampled once per process, and a header that is never
              torn down never sampled it again.

- [ ] **PR 27 · and · The feed under the pill hands over instead of cutting** — root feed on the
      task tab, with enough on both tabs that the two bodies plainly differ. Twin of the PR 27 iOS
      row above; run them together.
      Do:     tap the other tab in the dock, then tap back.
      Watch:  the two feeds cross over 150 ms, finishing while the dock's selector pill is still
              springing across to the tab that was tapped. Nothing travels — both feeds are drawn
              in the slot the other had.
      Fails:  the body cutting to the new feed while the pill is still on its way. Also a fail: the
              body still resolving after the pill has landed.
      Superseded: by **PR 199 · and** at the end of `phase-9-device-pass.md`, twin of the iOS row
              above. "The two feeds cross over 150 ms" is no longer what ships: the swap is web's
              two-curve pairing over `Enter` (200 ms), the leaving feed is drawn above the arriving
              one, and the second `Fails:` half — the body resolving after the pill has landed — is
              now the intended read rather than a defect. Run PR 199's pair instead.

- [ ] **PR 28/46 · and · The unlock is one handover, not three** — a server account signed in on
      the root feed. Sign out from Settings, then sign back in.
      Do:     sign out, then sign back in, watching the whole screen rather than the wizard.
      Watch:  one 150 ms handover carrying all three surfaces: the feed behind goes from sharp to a
              14 dp blur, the wizard fades up over it, and the locked placeholder feed crosses to
              the real one underneath. They start together and they finish together.
      Fails:  the blur snapping to full strength on one frame; the wizard card popping in over a
              backdrop still resolving; or the placeholder hard-swapping for the real feed. Any one
              of the three landing on its own frame is the defect.
      Known:  a cold start straight into onboarding deliberately plays NO enter — the wizard is
              drawn finished over an unblurred backdrop, because an insertion with nothing before
              it has nothing to hand over from. Only the sign-out and the sign-back-in animate.

- [ ] **PR 30 · and · The chrome ducks out rather than being taken away** — root feed, dock and
      create button both on screen. Twin of the PR 30 iOS row above.
      Do:     tap the search capsule to expand the field, then close it.
      Watch:  both controls travel straight down and out through the bottom edge, fading as they
              go, and come back up the same way over the same length — about 0.4 s, coming to rest
              without a visible bounce. They are gone rather than parked below the gesture bar.
      Fails:  either control vanishing in one frame; the two running at different lengths; or a
              faded dock still sitting over the gesture-bar strip while the field is open.

- [ ] **PR 58 · and · The corner changes colour with the body** — root feed, dock on screen, with
      the create button in view.
      Do:     tap the other tab in the dock.
      Watch:  the create button's accent and the dock's own tab tint cross between blue and green
              together, over the same 150 ms the feed body crosses on. Three surfaces, one
              handover.
      Fails:  a blue-to-green jump in the corner while the body is still fading — the create button
              was the last surface still cutting. Also a fail: the dock tint finishing visibly
              before or after the button's, which is the 180 ms this retired.

- [ ] **PR 12a · and · The check-off has a beat in the middle** — Today with 2+ pending tasks. Twin
      of the PR 12c iOS row above and the PR 12b web row below; run all three together, because the
      claim is that one task ticked off on three clients finishes on one rhythm.
      Do:     tick a task's circle and watch the TITLE rather than the row.
      Watch:  the rule sweeps left to right across the title over 320 ms, starting 160 ms after the
              tick lands, one rule per line on a title that wraps; it holds for 360 ms; then the
              row's ink fades over 260 ms and the rows below close the gap.
      Fails:  the rule being simply present on the next frame — the beat the user is meant to watch
              was the only one in the sequence with no motion in it. Also a fail on a wrapped
              title: a single rule down the middle of the block, landing in the gap between two
              lines.

## Web

Run against the deployed image the Phase 5/6 merge pushes, at phone width — the dock, the create
button and the hero capsule are the native shell, and none of them is on screen on a desktop
viewport.

- [ ] **PR 29w · web · The search capsule grows out of its button** — the native app shell at phone
      width, root feed, scrolled to the top so the capsule is the wide pill. Twin of the PR 29
      Android row above.
      Do:     tap the capsule to open the search field, then close it.
      Watch:  the capsule travels and grows to the full width of the row over 320 ms; the title and
              the controls it takes the row from fade over 150 ms.
      Fails:  the capsule arriving at its new width in one frame. Also a fail: the capsule
              stuttering or lagging behind the finger while the header is FOLDING under a scroll —
              the scroll path is meant to stay instant, and a transition reaching it would put the
              whole fold a step behind.

- [ ] **PR 29w · web · The expanded field is never clipped inside the old pill** — the same header,
      and this one is worth a screen recording: it is one frame, and it is the frame the fix
      exists for. A mid-range phone rather than a desktop browser, since the defect is a scheduling
      race and a fast machine can hide it.
      Do:     open the search field and step back through the recording frame by frame from the
              tap.
      Watch:  every frame from the tap onward has the input inside a capsule at least as wide as
              the input's own painted content. The capsule's growth and the field's contents change
              on the same frame.
      Fails:  any single frame where the expanded field is painted clipped inside the 56 px folded
              pill — the text cut off at the pill's edge, or the placeholder overflowing it. This
              was filed as PLAUSIBLE and never confirmed on a device: the row is both the proof of
              the fix and the confirmation that the defect was real, so if it IS seen on this
              build, say so rather than re-filing it as new.

- [ ] **PR 30 · web · The chrome ducks out rather than being taken away** — the native shell at
      phone width, on a list with 3+ tasks, dock and create button on screen. Twin of the PR 30
      Android and iOS rows above.
      Do:     tap the **Select** (circle-check) button in the header's trailing cluster, beside
              Search, and watch the bottom of the screen (repeat with the bar's X to leave). NOT a
              long-press: on web that gesture lifts a drag-to-reschedule card, exactly as it does
              on the native clients, and never opens selection mode.
      Watch:  the dock and the create button travel down and out through the bottom edge over
              320 ms, fading as they go, while the bulk selection bar rises into the slot they are
              leaving. Leaving selection mode plays it backwards at the same length.
      Fails:  either control disappearing in one frame, or still being visible over the gesture
              area after the bar has arrived.

- [ ] **PR 30 · web · A tap aimed at the bar is not taken by the chrome leaving it** — the same
      setup, NOT yet in selection, and the check is the first 320 ms only: tap DURING the duck-out,
      not after it. The bar's four actions are quarters of the row and the chrome covers both ends
      of it at phone width: the create button, a 56 px circle pinned right, sits over **Delete**,
      and the dock, a ~112 px two-tab pill pinned left, sits over **Complete**. Nothing is selected
      yet, so both actions are greyed — that the tap does nothing at all IS the check.
      Do:     tap **Select** (circle-check) in the header cluster and, without waiting, tap Delete
              (repeat: leave with the bar's X, let the chrome settle back, tap Select again and
              aim at Complete instead).
      Watch:  nothing opens and nothing navigates — you are still on the list with the bar up. The
              dock and the create button are painted ABOVE the bar for the whole 320 ms exit, so
              this is the one client where the picture of a control can still take a tap meant for
              the bar underneath it.
      Fails:  the create-task sheet opening on the Delete tap, or the app navigating to Today on
              the Complete tap — the dock's left tab is what sits under Complete. Either means a
              leaving control is still hit-testable, which is a web-only hazard: Android and iOS
              duck their chrome for the search field, where nothing arrives in the vacated row to
              be tapped.

- [ ] **PR 12b · web · The check-off has a beat in the middle** — any list with a task that has
      NOTES on it, since the notes are half of what this row checks. Twin of the PR 12a Android and
      PR 12c iOS rows above.
      Do:     tick the task's checkbox and watch the title and the notes together.
      Watch:  160 ms after the tick, one rule fades in across the title and the notes on the same
              beat, over 320 ms; it holds for 360 ms; then the row's ink fades over 260 ms while
              its box shuts underneath the fade, so the rows below travel into the space rather
              than jumping into it.
      Fails:  the notes' rule snapping on under a title's rule that fades — two edits to one task,
              and the defect this PR names. Also a fail: the row being pruned while its box is
              still closing, which puts back the jump the sequence exists to remove.

PR 23's row below is not one of the pairs. It is the fourth leg of a Phase 5 row — the three
scoped feeds took their placement travel then and the custom list was the one that was missed — so
it lands in this file's Web section with the Phase 6 rows and has no twin to run it beside.

- [ ] **PR 23 · web · The custom list's Earlier bucket slides when the scene lands** — a phone, a
      custom list holding exactly one task due today and at least two overdue ones, with Earlier
      collapsed, so its header is the block sitting under the scene.
      Do:     tick that last current task off, and watch the Earlier header rather than the paper.
      Watch:  the header slides down over 320 ms into the place the 42vh scene pushes it to, and
              the first confetti appears only once it has arrived — travel, then burst, then the
              scene rising through it.
      Fails:  the header is already in its new place on the frame the row disappears, which is the
              defect this closes and is what the other three feeds looked like before Phase 5. Also
              a fail: the burst firing while the header is still moving, which is the placement lead
              not reaching `EmptyState`; or the header sliding at all with Reduce Motion on, where
              it must simply be drawn where it ends up.
