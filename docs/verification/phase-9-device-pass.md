# Phase 9 device pass

Run against the build that closes Phase 9. See `README.md` in this directory for the row format
and for how the file is closed out.

Phase 9 is adoption and polish: most of its rows are an easing swapped at an unchanged duration,
or a surface that was cut and now moves. Both are cases the gates cannot see — a counter reads the
same number for either curve, and a guardrail that proves a spec exists proves nothing about
whether it plays. So the rows here lean hard on `Fails:`, because the wrong version of almost every
one of them still animates.

## Android

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
