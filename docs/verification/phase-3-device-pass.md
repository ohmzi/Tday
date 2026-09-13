# Phase 3 device pass — what the user touches daily

Run against the release build that closes Phase 3. See `README.md` in this directory for the row
format and for how the file is closed out.

Phase 3 is the phase whose changes a user meets every day — the swipe under the thumb, the create
sheet, the haptic that fires on a completion. Almost none of it can be proved by a gate: a JVM test
can assert that a drag offset equals the finger's translation and say nothing about whether the row
feels welded to the thumb, and no gate on this machine can feel a haptic at all. So this file is
longer than Phase 1's, and it is the phase where the eye is the gate rather than a second opinion.

iOS rows from this phase are not checked here. There is no TestFlight cycle at the Phase 3 merge —
cycles are spent at phase merges and there are exactly three in the whole programme — so PR 10's
iOS haptics and bar-button press depth write their rows into `phase-5-device-pass.md`, the first
cycle that exists. See `README.md`, "iOS: three cycles, for the whole programme".

## Android
