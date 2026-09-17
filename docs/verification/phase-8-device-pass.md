# Phase 8 device pass — TF2, iOS with Reduce Motion on

Run against the release build that closes Phase 8. See `README.md` in this directory for the row
format and for how the file is closed out.

This file is opened by a Phase 7 PR, which is not a mistake. Cycles are spent at phase merges and
there are exactly three in the whole programme, so Phase 7's iOS batch (PR 26) has no cycle of its
own to be checked in; TF2 is the next one that exists and this is its file. The rows are written now,
while the diff is open, and read at the Phase 8 merge — see `README.md`, "iOS: three cycles, for the
whole programme".

Every iOS row here is checked twice, once normally and once with **Settings → Accessibility → Motion
→ Reduce Motion** on, which is the half TF2 exists for. Phase 6's parity work whose iOS third missed
the TF1 build, and Phase 8's own rows, land here alongside these.

The Android section below is not part of TF2 and costs a cable rather than a cycle, but it is run in
the same sitting for one reason: Phase 8 is the accessibility phase, and several of its Android rows
are checked against a platform setting rather than against a frame. Those rows each name the setting
to change first, and every one of them says what the default must still look like — a change that
only ever reads a setting can regress the people who never opened it, and that is the failure nobody
goes looking for.

## iOS

- [ ] **PR 26 · ios · The today block leaves instead of vanishing** — the Scheduled task home
      (the root feed's task tab), with exactly one task due today, so the block below the Today card
      is one row and emptying it empties the block.
      Do:     complete that task from the block itself, and watch the space it occupied rather than
              the row. Repeat with three tasks due today, completing them one at a time, so the last
              completion is the one that empties the block.
      Watch:  the last row fades and lifts away over 150 ms, and the Category board and the Lists
              section below it then TRAVEL up over 320 ms into the ~72 pt the block was holding.
              Two beats, in that order. On the earlier completions the block stays and only the row
              leaves, with the rows under it closing the gap on the same 320 ms.
      Fails:  the board and the lists are simply 72 pt higher on the next frame — the defect, and it
              is only visible on the completion that empties the block, which is why the setup says
              one task. Also a fail: the block fading for longer than the rows below take to arrive,
              which would mean the block is riding the travel instead of the departure rung.
      Also:   with Reduce Motion on, the block is there or it is not, and the layout below is at its
              new height immediately — no fade, no travel, and no intermediate frame where the space
              is half closed.

- [ ] **PR 26 · ios · One day at a time in the calendar** — the Calendar screen, Month view, with
      two ADJACENT days that both have tasks on them, three or more on at least one. Two populated
      days next to each other is the whole setup: a swap into an empty day cannot show this.
      Do:     tap the other day. Then tap back. Then run the same swap with the day chevrons rather
              than the grid.
      Watch:  one day on screen at a time. The day you are leaving fades out over 150 ms, and only
              then — after it is gone — the new day's rows fade up over 200 ms, with the card and
              everything below it resizing to the new day's height over 320 ms. Nothing slides down
              from the top; the rows do not travel, the day under them changed.
      Fails:  both days' rows drawn over each other mid-swap — a stack of half-transparent titles
              where one list should be, which is the defect. Also a fail: the outgoing day still
              fading while the incoming one is already legible, which would mean the two legs are
              running together rather than one after the other.
      Also:   with Reduce Motion on, the new day is simply there, at its own height, on the frame
              after the tap.

- [ ] **PR 26 · ios · A drop is not a day swap** — the same screen and the same setup, on the day
      with three or more tasks. The pair with the row above: that one checks the `.id()` fires on a
      day change, this one checks it stays out of an edit within the day. The drop deliberately does
      not follow the task — `commitPendingReschedule` leaves `selectedDate` where it is — so a
      reschedule changes which rows are in this day and never which day is shown.
      Do:     drag one task off this day onto another date in the grid.
      Watch:  the dragged row alone leaves, fading over 150 ms, and the rows under it close its gap
              over 320 ms. The day around them holds still: the header, the grid and the card's
              other content are not redrawn and do not fade.
      Fails:  the whole list fading out and back — the `.id()` swap playing on an edit that belongs
              to the row transition, which would also mean every completion in this list plays it.
              Also a fail: the view jumping to the drop target's day, which is a different bug in a
              different file but looks like this row passing.
      Also:   with Reduce Motion on, the row is gone and the gap is closed on the next frame, and
              the day is still this day.

- [ ] **PR 35a · ios · Reduce Motion lands without a relaunch** — any screen with a feed on it
      (the Scheduled task home, or a list), with at least three tasks so a completion has rows to
      move. The whole point of the row is that the app is never restarted: `tdayResolvedMotion()`
      exists to make the setting live, and a relaunch would pass even if it did nothing.
      Do:     with the app open and the feed on screen, pull down Control Centre or switch to
              Settings → Accessibility → Motion, turn **Reduce Motion** on, and come straight back.
              Complete a task. Then turn it off the same way, come back, and complete another.
      Watch:  the first completion has no travel and no fade — the row is gone and the gap is closed
              on the next frame. The second is the full choreography again. Neither needs the app
              killed, backgrounded past a relaunch, or navigated away from and back.
      Fails:  the first completion still animating, which is the defect this row exists for: the
              answer was read once at mount and the subtree was never invalidated. Also a fail: the
              feed animating again only after you navigate away and return.
      Also:   with Reduce Motion still on, exercise the three animations `AppRootView` owns itself
              rather than hands to a child: swap feeds on the root tab bar, scroll until the dock
              and create button hide and come back, and (on a signed-out or unreachable-server
              build) the onboarding blur. All three should cut. These are the sites a feed-only
              pass cannot see — they read the gate through `AppRootView`'s own property, which is
              why the provider had to move above the root view — so a fail here is the provider in
              the wrong place, not the flip going unnoticed.
      Also:   the same flip on the Calendar screen's month grid, which is the one surface built from
              hand-made `UIHostingController` pages. Swap to another day with Reduce Motion on: the
              new day is simply there. A hosted page inherits none of the app's environment, so this
              is the fallback being exercised rather than the override — and, since a get-only
              `accessibilityReduceMotion` keeps it out of `TdayMotionEnvironmentTests`, the only
              place that half of the accessor is checked at all.
      Also:   the snackbar: complete a task so the Undo toast appears with Reduce Motion on. It
              should not slide up from the bottom edge. 35b revised what it does instead — it
              crossfades rather than cutting, and the row below is where that is checked. Its drag
              snap-back is NOT covered — `AppSnackbar` reads its own environment — and still
              animates.

- [ ] **PR 35b · ios · Less motion, not no motion** — every check here is run twice, once with
      **Reduce Motion** off and once on, and the point of each is that the two look DIFFERENT
      without the second one looking broken. Refusing a large travel outright was the other
      candidate at four of these five sites and is what this row is looking for the symptoms of.
      Do:     (a) open any create-task sheet. (b) On the Calendar screen, Month view, turn a page
              with the chevrons — then turn one with a swipe. (c) Scroll a root feed down until the
              dock collapses to its icon, tap it, and let it time out. (d) Complete a task so the
              Undo toast appears. (e) In the create sheet, open the List picker.
      Watch:  with Reduce Motion ON — (a) the sheet card is already at its resting height and fades
              up with the scrim, one surface, no rise from the bottom edge. (b) The next month is
              simply drawn, with no sideways travel; the chevrons stay live and a SECOND tap works,
              and a swipe still follows the finger exactly as before. (c) The pill and the
              segmented control cross over in place at the size each of them is, over ~150 ms. (d)
              The toast fades in and out over ~200 ms without rising from the bottom edge. (e) The
              picker is unchanged — it still fades and settles its 3 %, which is deliberate.
      Fails:  (b) is the one to spend time on: a single chevron tap that moves the grid one month
              and then leaves BOTH chevrons dead is the defect — the page turn's completion used to
              ride the scroll animation, and refusing the animation is what would strand it. Also a
              fail: (a) the card appearing between two frames with no fade at all, (c) the dock
              swapping with a hard cut so a 56 pt pill is replaced by a full control in one frame,
              (d) the toast blinking in and out, (e) the picker cutting in.
      Also:   with Reduce Motion OFF, all five must be exactly what they were before this PR: the
              card rises and settles, the grid slides a full width, the dock scales out of its
              leading edge, the toast springs up. A retiming here means the gate was written the
              wrong way round and every user got the substitute.

- [ ] **PR 37 · ios · VoiceOver can act on a task row** — **Settings → Accessibility → VoiceOver**
      on. Needs four feeds: a scheduled list holding a dated non-recurring task, the floater feed,
      an overdue row, and the Completed screen. Also needs a list you are only a viewer of, which
      is the half that checks the gate rather than the actions.
      Do:     focus a task row, swipe up or down to reach the Actions rotor, and run each entry in
              turn — Edit, Copy, the mode's own third action, Delete — returning to the feed
              between them. The third action is Defer on a dated row, Schedule on a floater and
              Float on an overdue one, so this is four rows, not one. Repeat on the Completed
              screen and on the Calendar day list. Then focus a row in the viewer list, and start
              a multi-select sweep in a normal one and focus a row inside it.
      Watch:  the row says "Actions available" and the rotor holds four entries on a dated row,
              three on Completed and on Calendar. Each one is read in the device language — switch
              the app language to one you can recognise and hear it change. Each one does on
              activation exactly what the matching pill does on a swipe, haptic included: Edit
              opens the edit sheet, Copy puts the task on the clipboard, Delete removes the row
              and raises the Undo toast. In the viewer list and mid-sweep there are NO actions on
              the row at all.
      Fails:  the row reading as title and date with nothing else — no "Actions available" — which
              is the defect the whole row exists for, and the state the app shipped in. Also a
              fail: the actions present on the row's text but absent when focus is on the complete
              toggle, which would mean they are attached to one child element rather than to the
              row; a Delete offered in the viewer list, which is an action the app then refuses;
              or a rotor entry whose name is English while the rest of the screen is not.
      Also:   explore the row left-to-right with VoiceOver and count the stops. There must be no
              Edit, Copy or Delete BUTTON anywhere in it — those are the swipe pills, which sit in
              the row permanently at zero opacity, and hearing one is the tree exposing something
              the eye cannot see and the finger cannot reach. Then turn VoiceOver off and swipe a
              row open by hand: the pills must still reveal, still stagger, and still fire.

## Android

- [ ] **PR 36 · and · The Undo waits as long as the user asked it to** — Settings →
      Accessibility → **Time to take action** set to **30 seconds**, then back in the app on
      any feed with at least one task. The setting is the whole setup: at its default this
      row is indistinguishable from the old behaviour and passes by doing nothing.
      Do:     delete a task, start counting, and leave the toast alone until ~25 s — then
              tap **Undo** at ~28 s.
      Watch:  the toast is still on screen at 25 s, and the tap at 28 s actually puts the
              row back in the feed. Then pull to refresh: the row stays. Repeat the whole
              thing with the setting at **2 minutes** — the toast is still up at ~1:50 and
              Undo still restores.
      Fails:  the toast leaving at ~8 s, which is the platform call returning our own
              number rather than the user's — the defect, and the only check in the
              programme that can see it. Also a fail, and the worse one: the toast still up
              but Undo doing nothing, or appearing to work and the row gone again after a
              refresh. That is the commit having fired underneath a button still offering
              to undo it, and it means the window and the commit came apart.
      Also:   at the default setting, delete a task and let the toast time out without
              touching it. It must still go at ~8 s and the delete must stick. Everyone
              who has asked for nothing gets exactly what they had.

- [ ] **PR 36 · and · TalkBack is told the toast is there, and can put it away** — TalkBack
      on, **Time to take action** at 30 s, same feed.
      Do:     delete a task without moving focus, listen, then swipe to the toast and use
              TalkBack's dismiss gesture (swipe up-then-left) on it.
      Watch:  the message is SPOKEN when the toast arrives, without having to go looking for
              it, and politely — it waits for what TalkBack was already saying about the
              deleted row rather than cutting across it. The toast is then ONE focus stop
              that reads the message, the dismiss gesture closes it, and the **Undo** button
              is still its own separate stop that activates.
      Fails:  silence on arrival, which is the whole point of the row: the extra seconds are
              worth nothing if nobody is told there is something to reach. Also a fail: the
              dismiss gesture doing nothing; the message and the Undo landing on the same
              stop so the button cannot be activated on its own; or focus stopping on the
              message text as a separate node from the card.

- [ ] **PR 36 · and · The dock stops closing on people** — **Time to take action** at 30 s,
      a root feed scrolled far enough down that the dock has collapsed to its icon.
      Do:     tap the collapsed dock to open it, then wait — hands off — for 25 s.
      Watch:  it is still open at 25 s, and a tab tap then still switches feeds. At the
              default setting it must still close itself at ~2.4 s, unchanged.
      Fails:  it shutting at ~2.4 s with the setting at 30 s. Also a fail: it staying open
              for good at the default, which would be the base and the resolved window the
              wrong way round.

- [ ] **PR 34c · ios · The in-app Reduce Motion switch moves the app, without a relaunch** — any
      screen with motion on it, plus Settings. This is PR 34b's Android row on the other platform,
      and the half that has no local proof: there is no Swift toolchain in this repo, so nothing in
      this PR has been compiled, and `reduced-motion-floor` can see the shape of the composition
      (`systemReduceMotion || reduceMotion`) without being able to see that SwiftUI honours it.
      Do:     Settings -> Appearance -> Behavior -> **Reduce motion**, turn it on. Then complete a
              task on the Scheduled task home and watch the row leave. Come back and turn it off,
              and complete another.
      Watch:  with it on, the row leaves without travel or fade and the gap closes on the next
              frame; the title's strikethrough does not sweep; the Today card's scroll-to-top jumps
              rather than scrolls; opening Settings' search does not spring. With it off, all of
              those are the full choreography again. Nothing needs the app killed, and the switch is
              where it was left after a relaunch.
      Fails:  a switch that flips and changes nothing on screen. That is the whole feature, and it is
              the failure `MotionPreferenceStore` being `@Observable` exists to prevent — a value
              read once at launch would leave every animation running while the row said otherwise.
      Also:   turn **Settings -> Accessibility -> Motion -> Reduce Motion** ON as well, with the app
              open. The app's switch should draw itself ON and go untappable, with the caption naming
              iOS, and the motion should be off. Then turn the phone's setting back off without
              touching the app: the row goes live again holding whatever the app's own value was —
              the two are composed, not merged.
      Also:   with the phone's setting ON and the app's switch OFF, the motion is still OFF. This is
              the direction the composition can get wrong silently: an `&&` where the `||` is would
              leave the app animating for a user whose phone has asked it not to.
      Also:   the lock window. With Face ID required, lock and unlock: the cover's own animations
              follow the same switch. `AppLockWindowHost` renders into a separate window and is
              handed the preference rather than reading it, so this is where a forwarding mistake
              shows up as "everything is quiet except the lock screen".
      Why:    no Swift toolchain here. What is verified locally is the wiring read as text —
              `reduced-motion-floor`'s block D (one reader of the accessibility setting, both install
              sites, and the subtract-only composition pinned by name), `settings-icons` (the
              `LucideActivity` imageset this row names), and `ios-target-membership` (the new file is
              registered in the pbxproj). Whether the switch actually moves an animation is this row
              and nothing else.
