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
