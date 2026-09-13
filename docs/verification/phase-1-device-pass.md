# Phase 1 device pass — the guardrail phase

Run against the release build that closes Phase 1. See `README.md` in this directory for the row
format and for how the file is closed out.

Phase 1 is the guardrail phase, so most of its rows are proved by the scanners themselves: a rule
that fails on `develop` and passes after the fix is the proof, and the deletion of provably
unreachable code needs no eye. What lands here is the short list where a scanner can prove the
composition is now correct but only an eye can say the motion reads right.

## Android

- [ ] **PR G2 · and · The toast exit plays over a card, not over nothing** — any list with a task on it.
      Do:     complete a task so the toast appears, then let it time out on its own (repeat from: tap
              Undo; flick the toast downwards; tap the toast body if it navigates).
      Watch:  the card and its text fade together over 140 ms while the whole card slides down about a
              quarter of its own height over 180 ms — the words stay readable the entire way out.
      Fails:  the card disappears on a single frame with nothing to see; or an empty pill slides away
              after the text has already gone, which is the old bug with the content emptied first.

- [ ] **PR G5 · and · The Earlier empty scene enters instead of snapping** — Today with every task in
      scope done but Earlier still holding overdue tasks, Earlier collapsed.
      Do:     complete the last remaining task in scope (repeat the same observation from Scheduled,
              Priority, All and a List scope; and once more with Earlier already expanded, which is the
              celebration path).
      Watch:  the illustration fades in over 190 ms while its slot grows under it, so Earlier's header
              is pushed down progressively over those same 190 ms.
      Fails:  the ~34 % gap opens in one jump and only the artwork fades on top of the jump — the header
              is already in its final place before the fade starts.

- [ ] **PR G5 · and · Cold entry into an already-finished Today does NOT animate** — Today already empty
      in scope with Earlier collapsed and overdue tasks in it; leave the app, then come back.
      Do:     cold-launch into Today (repeat by switching to a different mode and back).
      Watch:  the scene is simply present on the first frame, at full size and full opacity.
      Fails:  the scene fades and expands itself in on arrival. That is the transition state being
              seeded `false` instead of from the live visibility value, and it is a behaviour change
              nobody asked for.
