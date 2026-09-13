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
