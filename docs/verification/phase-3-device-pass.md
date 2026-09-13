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

> **Every PR 11 row below states a minimum Android version, and it is not decoration.**
> `minSdk = 26` (`android-compose/app/build.gradle.kts:125`), and `androidx.core`'s
> `HapticFeedbackConstantsCompat.getFeedbackConstantOrFallback` rewrites the newer constants
> down to older ones on older handsets. Nothing goes silent — but below API 34 the vocabulary
> partly *collapses*: `SEGMENT_TICK` and `TOGGLE_ON` both become `CONTEXT_CLICK`, `TOGGLE_OFF`
> and `SEGMENT_FREQUENT_TICK` both become `CLOCK_TICK`, and `DRAG_START` becomes `LONG_PRESS`
> — the same constant `destructive()` uses. Below API 30, `CONFIRM` additionally becomes
> `VIRTUAL_KEY` and `REJECT` becomes `LONG_PRESS`. On a handset under the stated version the
> two haptics a row asks you to compare are the *same constant*, so a `Fails` there is a
> property of the fallback table and proves nothing about this diff.

- [ ] **PR 11 · and · Completion against deletion** — Today, Android 11+ (API 30), 2+ pending tasks, system haptics on (Settings › Sound & vibration › Vibration & haptics), phone held rather than on a desk.
      Do:     tick the first task's circle, then swipe the second row open and tap Delete.
      Watch:  two haptics you could name blind — the tick is one rounded CONFIRM pulse, the delete a single heavier LONG_PRESS thud that lands harder than the tick did.
      Fails:  the two are indistinguishable (both are still the old CLOCK_TICK), or the delete is the lighter of the pair.

- [ ] **PR 11 · and · Edit and Delete on the same revealed row** — Today, Android 11+ (API 30), one pending task, swiped open so Edit / Copy / Delete are showing.
      Do:     tap Edit, dismiss what opens, swipe the same row open again and tap Delete.
      Watch:  Edit gives the same light click as any other button in the app; Delete gives the heavier thud. The two buttons sit 16dp apart and a mis-tap should be felt before it is read.
      Fails:  Edit and Delete feel the same, or Edit is the heavy one.

- [ ] **PR 11 · and · Pick-up against drop** — Today, Android 14+, 2+ scheduled tasks, timeline visible, two tasks at different hours.
      Do:     long-press a task until it lifts, drag it onto a different hour, release.
      Watch:  two different haptics inside the one gesture — a DRAG_START lift at the moment the row comes loose, and a rounded CONFIRM when it lands. The lift is not the tick a tap gives.
      Fails:  only one haptic fires across the whole gesture, or the pick-up and the drop feel identical.

- [ ] **PR 11 · and · Tab change against dock expand** — root feed, Android 14+, dock collapsed, sitting on any tab (collapsed, the other tabs are transparent and parked outside the dock, so the tab you are on is the only thing there is to press).
      Do:     tap the tab you are on to expand the dock, then tap a different tab.
      Watch:  the expand is the sharper CONTEXT_CLICK; the tab change that follows is a single SEGMENT_TICK detent. Same finger, same target, two events.
      Fails:  both taps feel the same, or the expand fires nothing at all.

- [ ] **PR 11 · and · Toggle direction** — create-task sheet open, Android 14+, scrolled to the Schedule switch row.
      Do:     tap the Schedule row to turn it on, then tap it again to turn it off.
      Watch:  on and off differ from each other (TOGGLE_ON against TOGGLE_OFF), and neither is the plain click the Date / Time row below it gives.
      Fails:  both directions feel identical, or the switch row feels the same as the Date / Time row below it.

- [ ] **PR 11 · and · Restoring is not completing** — Completed screen, Android 14+, at least one item in it.
      Do:     tap the filled circle on a completed task to restore it.
      Watch:  a TOGGLE_OFF, lighter than the CONFIRM the same circle gave when the task was finished — an undo, not an achievement.
      Fails:  restoring feels like a completion, or gives the old flat tick.

- [ ] **PR 11 · and · One event, one feel, on every screen** — Android 11+ (API 30), a pending task visible in each of Today, the Scheduled-home Today card list, and Calendar's day list (recreate a task between deletes).
      Do:     swipe open and tap Delete on each of the three in turn.
      Watch:  all three deletes are the same heavier thud. Before this change Scheduled-home's swipe buttons fired SEGMENT_FREQUENT_TICK while Today's and Calendar's fired CLOCK_TICK, so this is the row that catches a screen left behind.
      Fails:  one of the three is lighter, sharper or shorter than the other two.

- [ ] **PR 11 · and · One circle, two events** — Today, Android 14+, 2+ pending tasks, bulk-select entered from the Select (circle-check) button in the top bar — never a long-press, which is drag-to-reschedule.
      Do:     tap a task's circle to add it to the selection, tap the X in the header to leave bulk-select, then tap that same circle to complete the task.
      Watch:  the same pixel gives a light SEGMENT_TICK detent while it is moving a selection and the rounded CONFIRM when it finishes the task — the one control in the app whose haptic depends on the mode it is in.
      Fails:  both give the same pulse, or the bulk-select tap fires the completion CONFIRM, so selecting ten tasks reads as ten completions.

- [ ] **PR 11 · and · Asking is light, destroying is heavy** — Android 11+ (API 30), a list you can afford to delete, open on its detail screen with 2+ pending tasks in it.
      Do:     select two tasks and delete them through the bulk bar's Delete → confirm; then open the list's ⋯ menu → Delete → Cancel, and finally ⋯ → Delete → confirm.
      Watch:  every button that only opens a prompt is the plain light click, and the heavier LONG_PRESS thud lands exactly twice across the whole run — on the two confirms that actually destroyed something, and never on the prompt you cancelled.
      Fails:  the thud fires on the Delete that merely opens the prompt, so cancelling felt like a deletion; or the confirm that removes the two tasks, or the whole list, feels like any other button.

- [ ] **PR 15a · and · The create sheet plays its exit from every dismiss path** — Today, tap `+` to
      open the create sheet and type a word so the keyboard is up.
      Do:     tap the scrim well above the card (repeat from: the ✕ in the sheet header; the system
              Back gesture; and once more for the edit sheet opened from a task, and for the New List
              sheet on the lists row).
      Watch:  the card SLIDES down off the bottom edge while fading, over 320 ms, on every one of those
              paths — the dim scrim is still there behind it until the card has finished leaving, and
              the keyboard stays up until the card has gone, dropping after it rather than before it.
      Fails:  the sheet disappears on a single frame on any path, or one path slides and another cuts;
              or the scrim disappears first and the card is left sliding over the live screen; or the
              keyboard drops first and the card's top edge jumps down a third of the screen part-way
              through the slide (`and-create-sheet-ime-height-snap` getting into the exit).

- [ ] **PR 15a · and · The card is still readable the whole way out** — open the edit sheet on a task
      that has a title, notes, a due date and a repeat set, so the card is full.
      Do:     tap the ✕ and watch the card, not the screen behind it.
      Watch:  title, notes and the date rows stay drawn and legible for the whole 320 ms slide; they
              fade with the card, at the card's opacity, and are still there as it clears the edge.
      Fails:  the card empties, blanks or collapses on the frame the slide starts, so what slides away
              is an empty rectangle — the exit playing over nothing.

- [ ] **PR 15a · and · A scrim dismiss draws no ripple** — create sheet open over Today, dark theme,
      where a ripple against the dim scrim is easiest to see.
      Do:     press and HOLD a finger on the scrim a good distance above the card for a full second,
              then release.
      Watch:  nothing lights under the finger for the whole second it is held — a Material ripple
              reaches full radius in ~300 ms, so a second is three times over — the scrim stays one
              flat dim, and the sheet starts its 320 ms slide out on release.
      Fails:  a circular ripple spreads out from the finger across the whole window, or the scrim
              brightens as a full-screen button would while held.

- [ ] **PR 15a · and · The widget's create sheet leaves before its window does** — a Today widget on
      the home screen, no app task open. This is the only create surface whose host is an Activity
      rather than a composition flag, so its exit is two animations in sequence and nothing in CI
      can see either.
      Do:     tap the widget's `+`, type a title, then tap the scrim above the card (repeat once
              tapping the green Create instead, and once with the header X).
      Watch:  the card slides down off the bottom edge over 320 ms, and only once it is gone does
              the whole window fade out over ~90 ms. The dim behind the card does not outlive the
              card by more than a blink.
      Fails:  the card vanishes on one frame and only the window fade is left; or a bare dim scrim
              with no card in it sits on screen after the card has gone, which is the mirror image
              of the first row's failure — scrim outliving card instead of card outliving scrim.

- [ ] **PR 15a · and · A submit in flight refuses the dismiss instead of half-playing it** — the
      widget create sheet with a title typed, airplane mode ON so the submit hangs instead of
      returning.
      Do:     tap Create, then immediately tap the scrim (repeat with the header X).
      Watch:  nothing moves. The sheet stays fully drawn, does not start its 320 ms slide, and the
              tap is refused outright rather than queued.
      Fails:  the card slides away and leaves an empty scrim over a request that is still running;
              or nothing happens at the time and the sheet then leaves by itself once the request
              finally gives up, which is the same tap arriving late.
