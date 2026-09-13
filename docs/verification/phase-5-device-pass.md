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
