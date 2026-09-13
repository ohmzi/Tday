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

- [ ] **PR 11 · android · Completion against deletion** — Today, 2+ pending tasks, system haptics on (Settings › Sound & vibration › Vibration & haptics), phone held rather than on a desk.
      Do:     tick the first task's circle, then swipe the second row open and tap Delete.
      Watch:  two haptics you could name blind — the tick is one rounded CONFIRM pulse, the delete a single heavier LONG_PRESS thud that lands harder than the tick did.
      Fails:  the two are indistinguishable (both are still the old CLOCK_TICK), or the delete is the lighter of the pair.

- [ ] **PR 11 · android · Edit and Delete on the same revealed row** — Today, one pending task, swiped open so Edit / Copy / Delete are showing.
      Do:     tap Edit, dismiss what opens, swipe the same row open again and tap Delete.
      Watch:  Edit gives the same light click as any other button in the app; Delete gives the heavier thud. The two buttons sit 16dp apart and a mis-tap should be felt before it is read.
      Fails:  Edit and Delete feel the same, or Edit is the heavy one.

- [ ] **PR 11 · android · Pick-up against drop** — Today with 2+ scheduled tasks, timeline visible, two tasks at different hours.
      Do:     long-press a task until it lifts, drag it onto a different hour, release.
      Watch:  two different haptics inside the one gesture — a DRAG_START lift at the moment the row comes loose, and a rounded CONFIRM when it lands. The lift is not the tick a tap gives.
      Fails:  only one haptic fires across the whole gesture, or the pick-up and the drop feel identical.

- [ ] **PR 11 · android · Tab change against dock expand** — root feed, dock collapsed, sitting on any tab.
      Do:     tap a different tab, then tap the tab you are now on to expand the dock.
      Watch:  the tab change is a single SEGMENT_TICK detent; the expand is the sharper CONTEXT_CLICK. Same finger, same target, two events.
      Fails:  both taps feel the same, or the expand fires nothing at all.

- [ ] **PR 11 · android · Toggle direction** — create-task sheet open, scrolled to the Schedule switch row.
      Do:     tap the Schedule row to turn it on, then tap it again to turn it off.
      Watch:  on and off differ from each other (TOGGLE_ON against TOGGLE_OFF), and neither is the plain click the Date / Time row above gives.
      Fails:  both directions feel identical, or the switch row feels the same as the Date row next to it.

- [ ] **PR 11 · android · Restoring is not completing** — Completed screen with at least one item in it.
      Do:     tap the filled circle on a completed task to restore it.
      Watch:  a TOGGLE_OFF, lighter than the CONFIRM the same circle gave when the task was finished — an undo, not an achievement.
      Fails:  restoring feels like a completion, or gives the old flat tick.

- [ ] **PR 11 · android · One event, one feel, on every screen** — a pending task visible in each of Today, the Scheduled-home Today card list, and Calendar's day list (recreate a task between deletes).
      Do:     swipe open and tap Delete on each of the three in turn.
      Watch:  all three deletes are the same heavier thud. Before this change Scheduled-home's swipe buttons fired SEGMENT_FREQUENT_TICK while Today's and Calendar's fired CLOCK_TICK, so this is the row that catches a screen left behind.
      Fails:  one of the three is lighter, sharper or shorter than the other two.
