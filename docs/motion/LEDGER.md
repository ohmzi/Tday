# The motion programme ledger — 205 rows

## WHY THIS FILE EXISTS, AND WHY IT IS A COMMITTED FILE

The first attempt at this programme died three times mid-task and lost everything, because the
work lived in a context window and in `/tmp`. Both are volatile, so a death was total. This file
is the opposite of both: it is a tracked file on a branch, which means a dead session costs at
most one PR, and `git diff` on this file is the entire progress report.

It is also the only place these rows exist as one list. The audit that produced them and the
execution plan that sequenced them are working papers that live outside the repo; nothing in CI,
nothing in review and nobody reading `develop` can see them. So if a row is not written here, it
is not dropped loudly — it is dropped silently, which is how a motion audit becomes a motion
anecdote. **A truncated ledger is worse than no ledger, because it looks finished.** All rows
are present below; the counts in the next section are the assertion, and the `grep` at the end of
it is how you check.

Section references of the form `§2.2` point at the execution plan this file was seeded from. That
plan is not in the repo and is not a deliverable — everything load-bearing has been restated here,
so this file stands alone.

## THE RULE: A PR TICKS ITS OWN ROWS IN THE SAME COMMIT

Not in a follow-up commit. Not at the end of the phase. Not "I'll tidy the ledger later".

The programme's definition of done puts the tick third of five, after the code is committed and
its gate is green locally, and before the PR is opened against `develop`:

1. the code change is committed on a branch off `develop`;
2. its verification gate is green locally (or, for iOS-only changes, the change is textually
   complete and the guardrail covering it is green);
3. **its row here is ticked in the same commit;**
4. if it needs a device check, its line is appended to `docs/verification/phase-N-device-pass.md`
   in the same commit, unticked;
5. the PR is open against `develop`.

A unit is not done because the code looks right. It is done when the row is ticked on disk.

The same-commit part is mechanical, not ceremonial. Tick and fix in one commit and a revert takes
both, a cherry-pick takes both, and a bisect can never land on a tree where the ledger claims
behaviour the code does not have. Split them and the ledger starts describing a repo that does not
exist — which is the exact failure this file was created to prevent.

## HOW TO READ A ROW

```
  - [ ] `ledger-id` — what is actually wrong · platform · severity or impact · effort · Gate
```

- **Platform** — `and`, `ios`, `web`, `and+ios`, `and+web`, `ios+web`, `all`, or `ci`/`doc` for
  the rows that touch no client.
- **Severity / impact** — `Sev 1`–`Sev 4` for defect rows (4 = the user loses work or a control
  goes dead; 1 = hygiene). `Impact O1`–`Impact O4` for opportunity rows, which are not broken
  today; they are motion the app does not have yet.
- **Effort** — `XS` / `S` / `M` / `L`, sized in session-units. Every `L` is split into PRs that
  each fit one session.
- **Gate** — what proves it, and nothing weaker:

  | Gate | Means |
  |---|---|
  | `V` | vitest — `cd tday-web && npm run test` |
  | `G` | a guardrail test (vitest; reads any language, runs on Linux, no device) |
  | `J` | Gradle JVM test — `cd android-compose && ./gradlew :app:testDebugUnitTest` |
  | `X` | iOS xctest, CI only (there is no local iOS compile on this machine) |
  | `D` | a device or eye check, batched into that phase's `docs/verification/phase-N-device-pass.md` |
  | `TF` | a TestFlight cycle — expensive, batched into exactly three cycles for the whole programme |
  | `self` | the deliverable is its own proof (a workflow that runs, a scanner that reports) |
  | `doc` | a documentation-only correction |

Rows are grouped under the PR that carries them, and PRs under the phase that ships them. **The PR
heading is the unit of work**: one PR, one branch, one session.

### Three conventions you will hit immediately

**Rows split across several PRs.** Fourteen rows are one piece of work that has to land on more
than one client, or in more than one session. Those PRs carry a pointer line instead of a box:

```
  - ↳ part 2 of 6 of `motion-token-layer` — <what this PR does>. Box lives under **PR 8g…8n**.
```

The single checkbox sits under the **last** PR in the split, because that is the first commit at
which the row can be ticked honestly. The pointer lines are not optional bookkeeping — they are
how you find out, mid-phase, that the row you are about to tick is only two thirds landed.

**Duplicate rows.** Three rows were filed twice by two different passes of the audit, twice with
conflicting constants. Both filings keep their row (they are two of the 109) and both tick
together, under the same PR. The resolution — including which constant won and why — is written
into a sub-bullet under the second row, so nobody re-litigates it at 1 a.m.

**Lines that are not one of the 109.** Programme infrastructure (the CI gate and the eight
guardrail PRs), the two defects found *while building* the guardrails, and PR 8f's one-line value
change all have checkboxes but no ledger id, and are marked `*infra*`, `*new, not one of the 109*`
or `*no ledger row*`. They are real work and they are tracked; they are simply not audit findings,
so they are outside the count.

## COUNT RECONCILIATION — 128 SOURCE FINDINGS, 109 ROWS

The 19 that disappear do not disappear. They are all on the opportunity side, and they are
collapses, not deletions:

| Source | Count |
|---|---|
| Defects confirmed in source | 25 |
| New defects found while verifying those | 23 |
| `web:calendar-earlier-sheets` gap findings | 23 |
| Forward-looking opportunities | 57 |
| **Source total** | **128** |

| After processing | Count |
|---|---|
| Defect rows — 25 + 23 + 23, with two internal merges cancelled by two promotions | 71 |
| Opportunity rows — 57, of which 34 collapse into 15 cross-client rows (14 groups, one splitting in two) | 38 |
| **Ledger rows, first pass** | **109** |
| Less the 3 cross-ledger duplicates, which tick in pairs | −3 |
| **Distinct units of work** | **106** |
| Long-tail pass — audit findings re-derived in source | 163 |
| …already tracked above under a different id | −67 |
| …unresolved, needs a device | 1 |
| **…survived as genuinely new** | **95** |
| **Ledger rows below, total** | **205** |
| Plus programme infrastructure — the web CI gate (W0) and the eight guardrail PRs (G1–G8) | +9 PRs |

So the 19 are this: **34 client-specific opportunity findings became 15 rows that fix all three
clients at once** — a net −19. `semantic-haptic-vocabulary`, for instance, absorbed the Android
finding and the iOS finding, because shipping one client's haptic vocabulary is how the last five
divergences started. On the defect side nothing was lost either: two internal merges were offset
by two findings promoted out of the opportunity ledger, so 71 stayed 71.

The three duplicates are `and-swipe-row-lags-finger` ≡ `android-swipe-reveal-tracks-finger`,
`ios-settings-dayahead-overlay-snap` ≡ `ios-orphaned-transition-day-ahead`, and
`and-ios-root-feed-tab-swap-uncrossfaded` ≡ `root-feed-tab-switch-transition`. Each pair is one
code change, so 109 rows resolve to 106 things to actually do.

### Checking the count

Ledger rows are the only checkboxes whose text starts with a backticked id, so they can be counted
without counting the infrastructure lines:

```bash
grep -c '^- \[[ x]\] `' docs/motion/LEDGER.md   # 205 — must never shrink
grep -c '^- \[x\] `'    docs/motion/LEDGER.md   # rows closed so far
grep -c '^- ↳'          docs/motion/LEDGER.md   # 29 pointer lines into split rows
```

If the first number ever shrinks, a row was edited away and the programme has silently lost work.
Restore it from git history rather than adjusting the number.

---

## Phase 0 — correctness wearing a motion costume

### PR W0 — the missing web CI gate

- [x] *infra* — `web.yml` — run vitest + eslint + `tsc --noEmit` on PRs into develop · ci · S · Gate self

### PR 1 — staged completion survives an unmount

- [x] `web-staged-completion-dropped-on-unmount` — staged completion silently DROPPED if the row unmounts inside its 780 ms window · web · Sev 3 · M · Gate V

### PR 2 — the calendar form shell keeps what was typed across 640 px

- [x] `web-form-shell-swaps-on-resize` — crossing 640 px with a calendar form open discards everything typed · web · Sev 2 · S · Gate V

### PR 3 — calendar pager flags, Android and iOS

- [x] `and-calendar-pager-scrollrequest-stranded` — finger mid-slide cancels `animateScrollToPage`; both chevrons go dead · and · Sev 4 · XS · Gate J+D
- [x] `and-calendar-requestpage-guard-races` — pointless `coroutineScope.launch` around the `scrollRequest` write · and · Sev 1 · XS · Gate G
- [x] `ios-calendar-pager-flags-stranded` — no `scrollViewWillBeginDragging`; `isProgrammaticScroll` strands forever · ios · Sev 4 · S · Gate X
- [x] `ios-pager-early-return-leaves-flags-set` — early return at `:133` strands the flags on the `!animated` path · ios · Sev 3 · XS · Gate X

### PR 4 — the Today jump and the week-page settle

- [x] `and-calendar-today-jump-loses-selection` — cross-month Today jump moves the page but never selects today → `+` prefills the wrong date · and · Sev 4 · S · Gate J+D
- [x] `and-week-page-settle-declines-silently` — week page 0 can start in the previous month; `settlePage` declines silently · and · Sev 2 · S · Gate J+D

### PR 5 — pointer capture on the web calendar and row swipes

- [x] `web-calendar-swipe-lost-off-element` — no pointer capture, no `pointercancel`; release off-element leaves refs dirty · web · Sev 3 · XS · Gate V
- [x] `web-calendar-row-touch-cancel-freezes-swipe` — cancelled touch strands a row half-swiped with transitions off · web · Sev 3 · XS · Gate V

### PR 6 — Today drag targets survive an emptied section

- [x] `and-today-drag-targets-dropped-when-empty` — Morning/Afternoon/Tonight vanish mid-drag; an Earlier row has nowhere to drop · and · Sev 2 · XS · Gate J

## Phase 1 — guardrails

### PR G1 — the Kotlin source scanner and its canary, no rules yet

- [ ] *infra* — `KtSource.kt` scanner + canary (≥80 sites), no rules · and · S · Gate J

### PR G2 — Rule A, content empties as the exit starts

- [x] *infra* — Rule A (content empties as exit starts) **+ fix** `TdayToastHost.kt:140` · and · S · Gate G
  - Rules A–D landed as one vitest scanner, `tday-web/tests/guardrails/motion-reachability-android.test.ts`:
    that suite already statically reads Kotlin on Linux and runs on every PR, so these rows read `Gate G`
    rather than `Gate J`. The four rules and the canary are 9 tests, ~0.5 s.
- [x] `and-toast-exit-never-plays` — toast content lambda returns null the frame `visible` flips · and · Sev 3 · XS · Gate G+D
- [ ] `and-toast-drag-dismiss-has-no-threshold` — drag-dismiss commits on any downward movement (1 px twitch) · and · Sev 2 · S · Gate J+D

### PR G3 — Rule B, write-once visibility flag

- [x] *infra* — Rule B (write-once visibility flag) — deferred to PR 15a/PR 41b which carry its fixes · and · XS · Gate G
  - The rule ships enforcing, with `CreateTaskBottomSheet.kt` and `ScheduledTaskHomeScreen.kt` named in a
    `RULE_B_PENDING_FIX` list against `and-create-sheet-dismiss-cut`. The exemption is self-closing: a
    second test asserts each listed site still has the defect, so PR 15a's fix fails this suite until the
    name is deleted from the list.

### PR G4 — Rule C, composition-constant animation target

- [x] *infra* — Rule C (composition-constant target) **+ 2 deletions** · and · S · Gate G
- [x] `and-create-sheet-dead-keyboard-height-animation` — `keyboardSheetHeight`'s `animateDpAsState` can never run · and · Sev 2 · XS · Gate G
- [x] *new, not one of the 109* — `OnboardingWizardOverlay.kt:1679-1683` `wizardStepChipBorderWidth` animates a literal `1.dp` · and · Sev 2 · XS · Gate G

### PR G5 — Rule D, a guard implies `visible`

- [x] *infra* — Rule D (guard implies `visible`) **+ fix** — carries PR 13 · and · S · Gate G
- [x] `and-earlier-scene-enter-never-runs` — Earlier empty scene enters already visible; 34 % of screen snaps open · and · Sev 4 · S · Gate G+D
  - Fixed with a `MutableTransitionState` hoisted above the guard and **seeded from the live visibility
    value**, not from `false`: seeding false would animate the scene in on every cold entry into an
    already-finished Today, which is a behaviour change nobody asked for.

### PR G6 — the iOS reachability scanner

- [ ] *infra* — `scripts/ios-motion-reachability.mjs` + baseline + `ios-tests.yml` job · ios · S · Gate self

### PR G7 — the web exit-animation guardrail

- [ ] *infra* — `tests/guardrails/motion-exit-animations.test.ts` (5 rules) **+ 4 fixes** · web · S · Gate G
- ↳ part 1 of 2 of `web-dead-motion-code` — delete `.animate-scroll-left` (`globals.css:300`) + `.animate-task-complete` (`:529`). Box lives under **PR 18**.
- [x] `web-centered-selector-has-no-exit` — Radix unmounts overlay + card on the same frame · web · Sev 3 · XS · Gate G
- [x] `web-sheet-overlay-outruns-panel` — overlay has no duration → 0.15 s fallback vs the panel's Emphasis-in/Enter-out · web · Sev 2 · XS · Gate G
  - Row text corrected: it said "panel's 300/500 ms", which **PR 41a** retired. 41a put the panel on
    `Emphasis`-in/`Enter`-out and left the scrim on the library fallback, so the gap narrowed from
    350/150 ms to 170/50 ms and stayed the same defect. The scrim takes the panel's own two rungs
    rather than a pair of its own — it is that panel's backdrop, not a surface someone watches. That
    is the one place this declines the ladder's `Quick`-on-the-way-out reading, and declines what the
    native sheets do: `TdaySheetMotion.scrimOut()` is `Enter` against a card exiting on `Change`, so
    their scrim may leave first; this panel exits on `Enter` too, so a `Quick` scrim would hand the
    page back bright with the panel still crossing it. Both rungs are named utilities, so
    `web.durationUtility` does not move — the ceiling stays at the 47 41a lowered it to. This closes
    the sheet's own web leg, not the row above it: `dialog.tsx:21` is still a bare
    `animate-in`/`animate-out` scrim on that same 0.15 s fallback, 50 ms short of the `duration-enter`
    its own card names at `:44`, so **PR 41c** still inherits one untimed scrim on web.
- [x] *new, not one of the 109* — `InstallPromptBanner.tsx:8` — `return null` against `animate-in slide-in-from-bottom-4` at `:14` · web · Sev 2 · XS · Gate G

### PR G8 — pbxproj registration and the zero-test assertion

- [ ] *infra* — pbxproj-registration guardrail + `ios-tests.yml` zero-test assertion · ios · XS · Gate G

## Phase 2 — the one-line sweeps (iOS batch 1 + web presence)

### PR 16 — three iOS orphaned transitions, one line each

- [x] `ios-settings-dayahead-overlay-snap` — Day Ahead selector has a `.transition` with no `.animation(value:)` · ios · Sev 3 · XS · Gate G+X
- [x] `ios-orphaned-transition-day-ahead` — The Day Ahead selector declares a .transition with no animating ancestor, so it hard-cuts one row away from two siblings that spring · ios · Impact O3 · XS · Gate G+X
  - **Duplicate of `ios-settings-dayahead-overlay-snap` (§2.2).** No conflict to resolve — the two filings are a byte-identical one-line insert between `SettingsScreen.swift:274` and `:275`. Ticks with its twin.
- [x] `ios-onboarding-security-questions-cut` — neither security-questions flag is in the wizard's `.animation` list · ios · Sev 3 · XS · Gate G+X
  - Both flags added to the list at `OnboardingWizardOverlay.swift:119-126`: `isChoosingSecurityQuestions` on the panel spring, `isLoadingSecurityQuestions` on the loading ease. Guarded by `motion-reachability-ios.test.ts` rule B, which fails if either is dropped again.
- [x] `ios-security-questions-exits-animate-by-accident` — two of three exits animate only by coincidence · ios · Sev 2 · XS · Gate TF
  - The three exits are the three sites that clear `isChoosingSecurityQuestions`. The one inside
    the panel — "Back" (`OnboardingWizardOverlay.swift:624`) — flips the gate the card's own
    `.animation(_:value:)` names, so it is driven by a line keyed to it and pinned by rule B. The
    other two leave the flow by flipping a *different* flag — the account-mode toggle (`:525`)
    flips `isCreatingAccount`, "Change setup" (`:550`) sets `step` — and moved only because the
    card 400 lines up happens to list those two. Both now declare their own transaction with
    `withAnimation(.spring(response: 0.28, dampingFraction: 0.86))`, the spring that chain already
    applies to both flags, so the motion is unchanged today and survives the chain changing. TF1
    row in `docs/verification/phase-5-device-pass.md`.

### PR 44 — the iOS create-sheet selector overlay

- [x] `ios-selector-overlay-unanimated` — create-sheet selector overlay: no transition, no animation, six open sites · ios · Sev 3 · S · Gate G+TF
  - **The count in the row is wrong and the fix is bigger than it.** `CreateTaskSheet.swift`
    has *five* sites that open the overlay (`:239` and `:240` for date and time, `:259` list,
    `:276` priority, `:292` repeat) and *eight* that close it — seven picker dismissals plus
    the one where turning Schedule off pulls a schedule-only picker out from under the user.
    All thirteen were bare assignments and all thirteen now go through one
    `setActiveSelector(_:)`, which carries `TdayCenteredSelectorMotion` (`TdaySheetChrome.swift`,
    derived from the bottom sheet's own block). Guarded by `motion-reachability-ios.test.ts`
    rule A, which reports the overlay's `.transition` at `:182` the moment the funnel is
    bypassed.
- [x] `ios-selector-pops-while-card-slides` — card slides ~300 pt down as the unanimated selector pops over it · ios · Sev 2 · XS · Gate TF
  - The ~300 pt is the keyboard inset unwinding: opening a selector resigns first responder, so
    `TdayBottomSheetPresentationHost` animates the card back down over the keyboard's own
    ~0.25 s. That slide is correct and stays. What the selector takes from the spec is the
    *scrim* pair — `.easeOut(0.22)` in, `.easeIn(0.2)` out — rather than the card spring: it
    arrives where it already is, and 0.22 s of the same curve family lands with the settle
    instead of over it. Eye-check only; no gate can see two clocks agree.
- [x] `ios-sheet-dismiss-keyboard-lingers` — only scrim-tap resigns first responder; X and confirm leave the keyboard · ios · Sev 3 · XS · Gate TF
  - Fixed one level up from the row: the resign moved out of `dismissSheet()` into
    `TdayBottomSheetPresentationHost.animateOut()`, the funnel every dismissal of every sheet
    using this chrome reaches. Confirm also resigns before its `await` (`CreateTaskSheet.swift:499`),
    because waiting for the dismissal there leaves the keyboard up for the whole save.
- [x] `ios-uiscreen-main-keyboard-probe` — deprecated scene-unaware `UIScreen.main.bounds.maxY` · ios · Sev 1 · XS · Gate X
  - Citation `TdaySheetChrome.swift:500` was correct. Replaced by `TdayKeyboardFrameProbe` in
    the same file, which takes the bottom edge from the app's own window scene; the
    hidden-keyboard test is now a pure function with xctest coverage
    (`TdaySheetChromeMotionTests.swift`). The app's seven other `UIScreen.main` reads are
    sizing calls, not this row.

### PR 45 — iOS empty states that blank or cut

- [x] `ios-calendar-empty-state-blanked-by-isloading` — pull-to-refresh blanks the empty state for the whole sync · ios · Sev 3 · S · Gate G+TF
  - **The row's trigger is wrong; the gate it names is real.** Pull-to-refresh is not wired on this
    screen either — `pullRefreshEnabled` defaults to false and `AppRootView.swift:450` builds
    `CalendarScreen` without it — so the gesture cannot be what blanks anything. The defect is
    `CalendarScreen.swift:606`, `} else if !viewModel.isLoading {` in front of
    `calendarDayEmptyState`, and `isLoading` is raised only by `refresh()`: a force sync over a
    cache `CalendarViewModel.init` already hydrated from. Nothing on the screen loads on appear, so
    the flag never means "the answer is not known yet", only "a sync is in flight" — and the gate
    hid a correct scene for the length of it. Today `refresh()` is reachable from the error-retry
    button; it is reachable from the drag the moment that parameter is passed. Now a plain `else`.
- [x] `ios-completed-empty-state-no-removal-transition` — deleting a char out of a no-match search makes it vanish in one frame · ios · Sev 2 · XS · Gate G
  - Both scenes in `CompletedScreen`'s overlay take `completedEmptyStateTransition` —
    `.asymmetric(insertion: .identity, removal: .opacity)`, copied in shape and in reasoning from
    `TodoListScreen.emptyStateIllustrationTransition`. **The insertion leg is inert on purpose** and
    must stay that way: `TdayEmptyState` already rises itself over 0.52s from its own `onAppear`, so
    a second opacity leg would stack two fades on one arrival. The transaction the removal runs in
    is `.animation(.easeIn(duration: 0.22), value: showsCompletedEmptyState)` on the overlay's
    `ZStack` — the scene leaves on a keystroke written straight into `searchQuery`, which is inside
    no `withAnimation`, so without that modifier the transition above it was decoration.
- [x] `ios-completed-stale-pull-to-refresh-comment` — a comment justifying a gesture the screen does not have (cost a verifier pass) · ios · Sev 1 · XS · Gate review
  - Citation `CompletedScreen.swift:122-124` was accurate. **Fixed the comment, not the parameter**:
    which screens own a force-sync trigger is a product call (`AppRootView` hands
    `pullRefreshEnabled: !appViewModel.isLocalMode` to the two root feeds and to nothing else), and
    a motion PR is the wrong place to make it. The `.allowsHitTesting(false)` keeps its line on its
    real justification — the scene is decoration inside a full-screen overlay and would otherwise
    answer for the history's own scroll — and the comment now says the gesture is absent.
  - All three are latched by `tday-web/tests/guardrails/ios-empty-state-presence.test.ts`, which the
    iOS reachability scan cannot cover: its rules key off a view's `@State`, and every gate here is
    a computed `private var` over the view model. The pull-to-refresh row is asserted as an
    *agreement* between `AppRootView`'s call site and the prose, so wiring the parameter later is
    allowed and simply requires the comment back.

### PR 17a — closed inside G7

- *(no row, no work — the two exit defects this PR was cut for are fixed inside PR G7)*

### PR 17b — the web modal exit

- [x] `web-modal-has-no-exit` — `if (!isOpen) return null`; 8 call sites blink out · web · Sev 3 · M · Gate V
  - **Ticked by PR G7, which was one call site short.** G7 counted its eight as five confirmation
    dialogs plus three conditional mounts — but two of those three are the *containers* around
    `CreateModal` / `EditModal`, not `<Modal>` sites of their own. Eight files render `<Modal>`:
    the five confirmations, `CreateModal`, `EditModal`, and `DataTransferCard.tsx`, which is the
    one G7 never opened. Its portal was fixed for free by `Modal.tsx`; its body was not. The
    card's open flag is `pending !== null` and its body is `pending`'s dry-run preview, so
    answering it cleared both at once and the card spent its whole 200 ms exit offering to import
    zero items. Retained the last non-null bundle — the shape G7 gave `ConfirmRescheduleRecurring`
    for the same reason.
    Covered by `tests/unit/data-transfer-confirm-exit.test.tsx`, which asserts what is inside the
    portal while it leaves rather than that it is still there.
  - **Not this row:** `ConfirmDelete` / `ConfirmDeleteAll` gate on presence correctly, but they
    are rendered by `CalendarTaskRow` and their destructive buttons call `deleteMutate`, which
    prunes the `calendarTodo` cache on the spot — the row unmounts and takes the portal with it
    before a frame of the exit paints. Cancel animates; Delete still does not. The fix is to hoist
    them to `CalendarClient` beside `ConfirmRescheduleRecurring`, and the calendar delete dialog
    already belongs to **PR 25c**, so it is left there rather than half-done here.

### PR 51 — three web surfaces with no transition at all

- [x] `web-calendar-search-results-pop` — mobile search results panel is a bare conditional · web · Sev 1 · XS · Gate V
  - The panel is not in `CalendarClient` — it lives in the shared
    `components/ui/MobileSearchHeader.tsx`, which every screen's pinned bar uses.
    Calendar is simply the only caller that passes `onSelectResult` today, which
    is why the row reads as a calendar defect.
- [x] `web-bulk-selection-bar-has-no-transition` — fixed bottom bar with no enter and no exit · web · Sev 1 · XS · Gate V
- [x] `web-floater-row-dead-dnd-subscription` — every floater row calls `useSortable` with no `DndContext` anywhere · web · Sev 1 · XS · Gate V
  - Resolved by REMOVING the subscription, not by wiring a context: floater order
    is fixed (priority → most-recently-modified → id, shared with both native
    clients and the widgets) and drag-to-reorder is retired there, as
    `FloaterGroup`'s own doc comment already says.

### PR 18 — the rest of web’s dead motion code

- [x] `web-dead-motion-code` — Delete six dead motion artefacts that account for 5 of web's ~14 durations and 2 of its 10 easing curves · web · Impact O3 · S · Gate G — **final part (2 of 2)**; PR G7 carried the rest
  - citation corrected: the remaining artefacts were not in `globals.css`, which PR G7 left clean.
    They were eleven `.tsx` files no module imports — grouped as six components: the pre-dock
    sidebar (`SidebarContainer.tsx` + `ui/SidebarToggle.tsx`), `app/skeletons.tsx`,
    `ui/eyeToggle.tsx`, `calendar/CompleteButton.tsx`, the two `CreateTodoBtn.tsx` twins, and the
    four orphaned task-form dropdown menus. Rule E in `motion-reachability-web.test.ts` is what
    names them, and it fails on all eleven without the deletion.
  - the easing half of the claim holds exactly: web had 10 curves, PR G7 took `linear` with
    `.animate-scroll-left` and this takes `ease-in`, which lived only in `CompleteButton.tsx`.
    The duration half does not — `200`, `300` and `500` all survive at live sites. What is true
    is that no duration and no curve is spent in an unimported module any more, which is what
    Phase 4 needs before it tries to converge them.

## Phase 3 — what the user touches daily

### PR 19 — the Android swipe row tracks the finger

- [x] `and-swipe-row-lags-finger` — one `StiffnessLow` spring drives drag *and* settle; rows trail the thumb ~141 ms · and · Sev 4 · S · Gate J+D
- [x] `android-swipe-reveal-tracks-finger` — Swipe-to-reveal is spring-chased during the drag instead of tracking the finger 1:1, so every task row visibly trails the thumb · and · Impact O4 · S · Gate J+D
  - **Duplicate of `and-swipe-row-lags-finger` (§2.2).** Same file (`TaskSwipeRevealState.kt:96-100`), same fix, but the two filings disagreed on the release spring — `0.82f/340f` against `0.85f/StiffnessMedium` (1500f), a 4.4× stiffness gap. §2.2 settles it at `dampingRatio = 0.82f, stiffness = 340f`, because 340f is iOS's `SwipeActions.swift:353` release spec converted and `StiffnessMedium` is a Compose default nobody chose. Ticks with its twin.
- [x] `and-swipe-hint-stomps-live-drag` — `playHint()` yanks a row to zero under a live finger · and · Sev 2 · XS · Gate J

### PR 15a — the Android create sheet actually plays its exit

- [x] `and-create-sheet-dismiss-cut` — `sheetVisible` never set false; 320 ms exit is dead code, 7 call sites · and · Sev 4 · M · Gate G+D
  - Fixed in the component rather than at the call sites, so all of them route through one path:
    every dismiss affordance starts the exit, and the caller's `onDismiss` — which is what removes
    the host `Dialog` — is called only once the transition has settled. The seven call sites are the
    seven in screens; `WidgetCreateTaskActivity.kt:171` is an eighth the row does not count (its host
    is an Activity, not a composition flag) and it is carried by the same change, as is the create-LIST
    sheet named alongside it in `RULE_B_PENDING_FIX`, now empty.
  - Still cutting after this, and not part of this row: the CONFIRM path. Tapping Create or Save ran
    the caller's `onCreateTask`/`onUpdateTask`, and every one of them cleared the composition flag on
    the same frame — the create and edit lambdas in `CalendarScreen`, `ScheduledTaskHomeScreen`
    (including the create-LIST sheet's `onCreate`), `TodoListScreen` and `CompletedScreen`. This
    row's text is `sheetVisible` never set false, which is what was fixed; but confirming is the
    commonest way a user leaves this sheet, so the sheet still cut more often than it slid. It
    belonged to **PR 41b / `sheet-presentation-unification`**, which unifies the sheet chrome and is
    the place to route submit through `startDismiss` as well.
    **Discharged there** — see the confirm-path sub-bullet under **PR 41b**. The eight call sites
    named above are line-less here on purpose: PR 41b deleted the lines, so a reader chasing a
    number would be chasing a frame of the tree that no longer exists.
- [x] `and-sheet-scrim-ripple-on-dismiss` — full-screen Material ripple on a dismiss tap · and · Sev 2 · XS · Gate D

### PR 15b — the create sheet’s IME height stops leaping

- [x] `and-create-sheet-ime-height-snap` — boolean IME threshold swaps the whole modifier chain; leaps to 85 % of screen · and · Sev 3 · M · Gate J+D

### PR 11 — Android’s semantic haptic vocabulary

- ↳ part 1 of 2 of `semantic-haptic-vocabulary` — 55 of 64 Android haptics are the same `CLOCK_TICK`. Box lives under **PR 10**.

### PR 10 — iOS haptics and bar-button press depth

- [x] `semantic-haptic-vocabulary` — One semantic haptic vocabulary: 55 of 64 Android haptics are the same CLOCK_TICK, and four documented iOS haptics have zero call sites · and+ios · Impact O3 · S · Gate G + D + TF — **final part (2 of 2)**; PR 11 carried the rest
- ↳ part 1 of 3 of `press-affordance-unification` — deepen iOS bar buttons 0.985 → 0.94. Box lives under **PR 9a/9b**.

## Phase 4 — the motion token foundation

### PR 7/8a — the shared token source of truth

- ↳ part 1 of 6 of `motion-token-layer` — `docs/motion.md` + `shared/.../MotionTokens.kt` + exporter + Gradle tasks. Box lives under **PR 8g…8n**.

### PR 8b — the Android token file, no call sites

- ↳ part 2 of 6 of `motion-token-layer` — `core/ui/TdayMotionTokens.kt` + `TdayMotionTokensTest.kt`. No call sites. Box lives under **PR 8g…8n**.

### PR 8c — the iOS token file and its pbxproj entries

- ↳ part 3 of 6 of `motion-token-layer` — `Tday/UI/Theme/TdayMotion.swift` + 6 island re-exports + xctest + **8 pbxproj entries**. Box lives under **PR 8g…8n**.

### PR 8d — the web token file and its CSS custom properties

- ↳ part 4 of 6 of `motion-token-layer` — `src/lib/motion.ts` + `--ease-scene`/`--ease-gesture` in `@theme inline`. Box lives under **PR 8g…8n**.

### PR 8e — the parity guardrail and the seeded budget

- ↳ part 5 of 6 of `motion-token-layer` — `tests/guardrails/motion-parity.test.ts` + `motion-budget.json` seeded. Box lives under **PR 8g…8n**.

### PR 8f — the one-line default-duration change

- [ ] *no ledger row* — ⚠ value change: `--default-transition-duration: 190ms` — **DROPPED, not deferred**; the box stays open because the change was not made and will not be. Four committed places now forbid it: `docs/motion.md:350-357` makes "Do not rebind Tailwind's default transition duration" idiom rule 3, `docs/CODING_STANDARDS.md:209` says "never rebind", `tday-web/src/globals.css:210-213` argues it in place at the one declaration block that would have carried it (~159 bare `transition-*` sites riding an un-overridden 150 ms, which is exactly `Quick`, so they are on the vocabulary for free), and `motion-budget.json`'s `_excluded.notCountedOnPurpose` excludes those same utilities on those same grounds. The value no longer names anything either: `4c941b1c` moved `Enter` 190 → 200 because 190 matched 2 sites against 200's 57. Rule 3 landed in `bc521d0d`, inside Phase 4's own PR #204 — the same PR that would have carried 8f — so this is a decision taken at the time, not a lapse · web · XS · Gate n/a — dropped

## Phase 5 — web primitives, in dependency order

### PR 24c — hoist the reduced-motion helper; it lands first

- [x] `web-prefers-reduced-motion-helper` — hoist out of `useFadeUnmount.ts:8-11`; **land first, 2 rows block on it** · web · Sev 2 · S · Gate V
- [x] `web-earlier-handoff-ignores-reduced-motion` — reduced-motion users get 520 ms of static illustration then everything at once · web · Sev 3 · S · Gate V

### PR 22a — completed rows collapse their box

- [x] `web-completed-row-box-does-not-collapse` — row fades ink but holds full height to 780 ms · web · Sev 3 · M · Gate V+D

### PR 22b — FLIP placement for the web feed

- [x] `web-feed-rows-have-no-placement` — no FLIP anywhere in `tday-web`; every neighbour of a removed row teleports · web · Sev 3 · L · Gate V+D
- [x] `web-today-section-wrapper-drops-gap` — Today section wrapper unmounts with its gap (~70 px) · web · Sev 2 · XS · Gate V
- ↳ part 1 of 2 of `feed-item-motion-parity` — `src/lib/feedItemMotion.ts` mirroring `TdayFeedItemMotion.kt`. Box lives under **PR 47**.

### PR 23 — empty-state slots stop claiming their height in one frame

- [x] `web-empty-state-slot-claims-42vh-in-one-frame` — the 42vh slot is claimed the same frame the last row is pruned · web · Sev 3 · M · Gate V+D · four screens draw this scene inline; the three scoped feeds took their travel in Phase 5 and the custom list was the one that was missed, so the row closes in Phase 9 on `ListContainer`'s own `useRowPlacement` wrapper and the placement lead in front of its celebration
- [x] `web-floater-empty-arrival-displaces-tiles` — ~33 vh of uncued jump, on the confetti frame — largest in the set · web · Sev 4 · M · Gate V+D
- [x] `web-empty-state-anchor-citation-fix` — ledger hygiene: `EmptyState.tsx:163` did not exist when this row was written, and the anchors that replaced it have since drifted again as the file grew 161 → 180 lines — twice now, so cite by class name as well as by line. Against the tree: the slot is `min-h-[42vh]` at `:64`, inside the wrapper at `:62-67`; the scene's own 520 ms arrival is the inner wrapper at `:70-80`, `.tday-empty-enter` at `:72` and `.tday-empty-enter-celebrating` at `:73`; and the confetti that arrival sits above the wrapper to avoid fading with is `:175-177`, mounted at `:176` · web · Sev 1 · XS · Gate doc

### PR 24a — the Earlier hand-off animates height, not just paint

- [x] `web-earlier-handoff-height-jump` — the EXPAND hand-off animates paint only: the scene fades and sinks while its box holds all 42vh, which the page then takes back in the single frame Earlier's rows arrive in. The collapse tap's own two jumps 260 ms apart — this row's original wording — are a different mechanism and belong to `web-earlier-collapse-has-no-handoff` in PR 24b, which is where they are counted: a collapse takes no hand-off at all, so nothing here reaches it · web · Sev 4 · M · Gate V+D
- [x] `web-earlier-exit-520ms-dead-wait` — `TODAY_EARLIER_EXIT_MS` 520 → **200** (`Enter`), not the 220 the audit asked for: 220 is iOS's `EarlierIllustrationHandoff.exitDuration` and is not a rung, and the ladder has nothing between 200 and 260 on purpose — 320 and 260 are both ruled out because a departure must not outlast or match the 260 arrival it is making room for (the argument in full at `todayEarlierIllustration.ts:48-62`). Pinned at `tests/unit/today-earlier-illustration.test.ts:338`, with the invariant that chose the rung at `:347` — not `:165-172` as this row used to say · web · Sev 3 · S · Gate V

### PR 24b — the Earlier collapse gets the expand’s hand-off

- [x] `web-earlier-collapse-has-no-handoff` — expand is sequenced, collapse is not → two jumps per tap: the illustration remounts into an already-open track and claims its 42vh in one frame, while Earlier's rows hold their own height behind it for the `OVERDUE_ROWS_FADE_MS` `useFadeUnmount` keeps them mounted. That 260 ms gap is the one PR 24a's row used to be worded with; it is counted here · web · Sev 3 · M · Gate V
- [x] `web-illustration-pops-back-inside-celebrate-window` — illustration snaps back to full opacity over Earlier's rows, and stays. Paint only, and deliberately kept that way: the hand-off that starts inside the celebration window does not close the scene's slot, because the scene is still there when the beat ends (`earlierHandoffVacatesSlot`). Whatever replaces the snap must not reopen a track either · web · Sev 3 · M · Gate V
- [x] `web-celebrate-window-expiry-is-an-untimed-cut` — the 4 s window closes on an unrelated re-render, then hard-cuts · web · Sev 3 · M · Gate V

### PR 56 — the Earlier chevron says something during the wait

- [x] `web-earlier-header-no-feedback-during-handoff` — the tapped chevron is identical for the whole 520 ms wait · web · Sev 3 · S · Gate V
- ↳ part 1 of 2 of `disclosure-expand-collapse` — promote `AnimatedHeight` out of `OnboardingWizard.tsx:704-729`. Box lives under **PR 42b+42c**.

## Phase 6 — parity pairs (android + web together; iOS follows)

### PR 29 — the Android hero search morph

- [ ] `and-hero-search-morph-snaps` — capsule jumps pill→full width in one frame, 3 siblings blink to alpha 0 · and · Sev 4 · S · Gate D
- [ ] `and-hero-mark-clock-frozen` — sun/moon samples the hour once in a keyless `remember` · and · Sev 2 · S · Gate J+D

### PR 29w — the web hero search capsule

- [ ] `web-hero-search-capsule-snaps` — width + translateX written imperatively, no transition, no crossfade · web · Sev 3 · S · Gate V+D
- [ ] `web-hero-title-opacity-transition-fights-raf` — 200 ms CSS transition on the element the rAF rewrites every frame · web · Sev 2 · XS · Gate V
- [ ] `web-hero-capsule-relayout-one-frame-late` — input can paint clipped inside the old 56 px pill (PLAUSIBLE, not confirmed) · web · Sev 2 · XS · Gate D

### PR 27 — the root feed tab swap, Android and iOS

- [ ] `and-ios-root-feed-tab-swap-uncrossfaded` — root feed body swaps in one frame while the dock pill springs · and+ios · Sev 4 · S · Gate D+TF
- [ ] `root-feed-tab-switch-transition` — The root feed tab switch — the app's most-used interaction — is a hard cut on both native clients while the dock selector that triggered it springs across · and+ios · Impact O4 · S · Gate D+TF
  - **Duplicate of `and-ios-root-feed-tab-swap-uncrossfaded` (§2.2).** §2.2 dissolves a three-way tangle here: this row is the root-feed **body** inside a single route (`TdayApp.kt:1103-1126`), not the NavHost route transition (`TdayApp.kt:1942-1955`, wired at `:335-338`) that PR 31 retimes to 160/110. Different surfaces — give the tab swap its own spec and do not reuse `NAV_FADE_*`. Ticks with its twin.

### PR 28 — the Android onboarding blur and overlay

- ↳ part 1 of 2 of `and-onboarding-blur-and-overlay-snap` — blur 14 dp → 0 in one frame; wizard card pops; locked feed hard-swaps. Box lives under **PR 46**.

### PR 46 — the iOS half of the same overlay

- [ ] `and-onboarding-blur-and-overlay-snap` — Android: the onboarding blur goes 14dp→0dp in one frame, the wizard card pops, and the locked feed hard-swaps for the real one · and+ios · Sev 3 · M · Gate D + TF — **final part (2 of 2)**; PR 28 carried the rest

### PR 30 — the dock and FAB duck instead of vanishing

- [ ] `dock-fab-duck-not-vanish` — dock + FAB pop out of existence in one frame on all three clients · all · Impact O3 · S · Gate D+TF

### PR 58 — the Android FAB accent crossfade

- [ ] `android-fab-accent-crossfade` — FAB snaps accent while the dock 8 lines away crossfades at 180 ms · and · Impact O2 · XS · Gate D

### PR 12a — Android completion choreography

- ↳ part 1 of 3 of `completion-choreography` — nothing eases during the 780 ms; toggle, tint, title, strike all hard-cut. Box lives under **PR 12c**.

### PR 12b — web completion choreography

- ↳ part 2 of 3 of `completion-choreography` — 3 of 5 row types hardcode 280/620/960 against Today's 160/360/260. Box lives under **PR 12c**.
- [ ] `web-checkbox-pointer-and-spring` — most-tapped control fires its pop on `onMouseDown`; touch gets the weakest feedback · web · Impact O3 · S · Gate V
- [ ] `web-strike-notes-mismatch` — the comment claims one mechanism; the notes use a bare `line-through` · web · Impact O3 · XS · Gate V

### PR 12c — iOS completion choreography

- [ ] `completion-choreography` — One completion choreography across the three clients: Android flips three states instantly inside a 780ms wait, web runs three rogue rhythms, iOS Calendar fires the wrong haptic and no sound at all · all · Impact O4 · M · Gate J + D + V + X + TF — **final part (3 of 3)**; PR 12a, PR 12b carried the rest

### PR 47 — iOS feed item motion

- [ ] `feed-item-motion-parity` — Bring iOS and web onto Android's TdayFeedItemMotion — web has no list motion at all, iOS uses one symmetric 220ms curve for insert, move and remove · ios+web · Impact O4 · M · Gate V + X — **final part (2 of 2)**; PR 22b carried the rest

## Phase 7 — remaining web areas

### PR 21 — the web calendar swipe tracks the finger

- [x] `web-calendar-swipe-is-untracked` — pointerdown records x, pointerup jumps; nothing moves under the finger · web · Sev 3 · M · Gate V+D
  - The grid now carries the finger's translation, written straight onto the DOM rather than
    through React state, and the app's own clocks run only for what happens after the finger
    leaves. Android's Phase 3 rows state the rule this was fixed to: a finger and a clock of the
    app's own are two clocks, and only one of them belongs to the app.
  - A release that turns the page hands over to the slide the incoming page already arrives on —
    Emphasis, on the Gesture curve, which is why a release can hand over to it at all — and the
    grid's own offset is dropped with the element that carried it. A release that turns nothing
    glides home on Quick, the rung the floor's refusal answers on, so the two compose as one
    answer when a back swipe is declined at the floor.
  - Past the threshold the grid gives instead of tracking, and in a refused direction it gives
    from the first pixel and reaches half a threshold at most. Web keeps ONE page in the DOM —
    the thing that lets `AnimatedHeight` measure a single height for a card whose four pages are
    four heights — so past the threshold there is no neighbour to uncover, and a grid that
    travelled a third of the card would be promising one.
  - The drag is written to a CHILD of the element that slides, which is structural rather than
    tidy: a filling CSS animation outranks an inline style, so a page that arrived on
    `cal-native-slide-from-*` holds its own `transform` at `translateX(0)` for as long as it
    lives. Written there, the drag would have been ignored on every page but the first the card
    ever drew — the same "one element, one `animation`" rule the refusal wrapper above it is
    built on, read on the property next door.
  - Reduced motion keeps the tracking and loses the trip home. The preference is about motion the
    app plays, not about the movement a finger is making, and the fifth idiom rule is kept the
    usual way: the destination is drawn in the frame the finger leaves.
  - No literal is added: the return names `var(--tday-duration-quick)` and
    `var(--tday-ease-gesture)`, and every distance in the gesture is a multiple of the swipe
    threshold it already had. The three web ceilings are untouched at 58 / 3 / 10.
  - Two things fall out of the gesture rather than out of the row, and both are kept: the axis
    locks at the same 8px the calendar row's swipe locks at, because the grid sits directly above
    a scrolling task list, and a gesture that ends vertically now turns no page at all.
  - Pointer capture moved from the way down to the axis lock, which is what lets the gesture
    begin on a day cell. The pager used to refuse any press that landed on a button, and in month
    view the grid is seven columns of day-cell buttons with 8px between its rows and nothing
    between its columns — so the tracking existed almost nowhere a thumb goes. The reason it
    refused is real but narrower than the refusal was: capture retargets the click that follows a
    press, so it is taken at the instant the axis says "x" and not before. Before the lock the
    press is still a candidate tap; after it, the cell's click is one the user no longer means.
  - A second finger arriving mid-drag now takes the page over instead of ending the gesture
    under it. The button bail used to clear the tracking refs and return, after which the first
    finger's move, release and lost capture all failed the pointer-id guard and the grid stayed
    parked at its dragged offset for good — selecting a date does not re-key the pager, so
    nothing came along to replace the element holding it.
  - The weekday row is out of the pager and out of the track: AGENTS.md's Calendar UX Contract
    says in so many words that the month title and weekday row do not slide with the date grid,
    and the labels come from today and the locale rather than the selected date, so every page
    drew the same seven letters. Sliding them was pointless at 17px on a page turn; carrying
    them under the thumb for 96px made it a breach. It stays inside the height box, because the
    row comes and goes with the view and a row outside the measurement would take its height out
    of the card in one frame while the rest of the change eased.
  - What a release hands over is a cut, and it is argued at the call site rather than fixed.
    Web keeps one page in the DOM, so the content the drag has carried to -80px *is* the outgoing
    month; the arriving one must finish at 0, and a continuation from -80px could only reach 0 by
    travelling right — which is the movement an undecided release makes and the one a user reads
    as a refusal. Direction is the half that carries meaning, so the arrival keeps it and pays a
    frame for it. The device row gained a `Judge:` line for it rather than a `Fails:` one,
    because it is a trade to be looked at, not a bug to be spotted.

### PR 25a — the calendar grid height animates across month lengths

- [x] `web-calendar-grid-height-snaps-mid-slide` — 35-vs-42-day months and view switches change height in one frame · web · Sev 3 · M · Gate V+D

### PR 25b — calendar row removal and the highlight ring

- [x] `web-calendar-row-removal-height-snap` — ticking fades ink but holds height to t=960 ms · web · Sev 2 · S · Gate V+D
  - **Already closed on `develop` when this PR opened.** 867c7903 put all five web row types on
    `taskCompletionTiming` and gave the calendar row the same 1fr→0fr track the scheduled row
    closes; abfe8c3b pinned it with `calendar-row-complete-interaction.test.tsx`. The `t=960` in
    the row is the hand-written clock that commit deleted — the ink now leaves at 520 over 260 and
    the box shuts behind it over 320, so the prune is at 840. Nothing to redo in code. The `D` half
    of the gate was still outstanding, because neither of those commits wrote a device row; that is
    what this one adds.
- [x] `web-calendar-highlight-ring-cuts` — `box-shadow` missing from the inline transition whitelist · web · Sev 2 · XS · Gate V
  - The row is the whitelist and the whitelist is fixed. What the fix does **not** buy is the
    phone. Below `sm` the ring is the only way the mark is drawn, and below `sm` the row sits in an
    `overflow-hidden` wrapper whose clip box is exactly the ring element's border box — so a 2px
    outset ring is cut away everywhere but the four rounded corners, and what fades there is
    slivers. That is a second, pre-existing defect, in a wrapper this row does not own; it is filed
    under **PR 25d**. The two are independent: reverting this transition would leave even the
    corners cutting.

### PR 25c — three small calendar and dialog cuts

- [x] `web-calendar-back-swipe-at-floor-is-silent` — rejected swipe at the earliest month produces no feedback at all · web · Sev 2 · S · Gate V
  - The card resists 8px and comes back, on Quick with the Gesture curve the paging slide already
    uses — short enough that it cannot be read as a page turn that started and changed its mind.
    The answer is given at the refusal rather than at the gesture: `animateToDate` now reports
    whether the page turned, so the arrow keys get the same answer the swipe does and the floor
    rule stays in one place.
  - Reduced motion gets nothing, deliberately. A refusal ends where it began, so the fifth idiom
    rule's "keep the destination" has no destination to keep; the flag is not raised at all in that
    case. The still signal there is the previous chevron, `disabled` at the floor.
  - Android does not share this row: its `HorizontalPager` gets the platform's stretch overscroll
    at page 0. iOS does — `CalendarPagingScrollView.swift:37` sets `bounces = false` — and is not
    fixed here, there being no Swift toolchain on this machine. Filed under **PR 26**.
- [x] `web-calendar-drag-overlay-drops-with-no-animation` — `dropAnimation={null}`; card vanishes at release · web · Sev 2 · XS · Gate V+D
  - Emphasis on the Enter curve, only those two values named; dnd-kit keeps its own keyframes and
    the side effect that hides the row underneath.
  - What the library decides, and therefore what the fix does not reach: dnd-kit measures the
    draggable's node at the drop and declines to animate if it has gone. A card released over
    nothing or back on its own day flies home; one dropped on another day flies to where its row
    was, and if the optimistic update has already removed that row there is no landing to play.
    The first case is the one the old code punished hardest — changing your mind deleted the card
    you were holding.
  - The two sibling drag contexts (`TimelineDndContext`, `TodayBucketDnd`) carry the identical
    `dropAnimation={null}` and are left for their own rows, which is why the config sits in the
    calendar's `lib/`.
- [x] `web-delete-dialog-has-no-fallback` — first Delete tap renders literally nothing until the chunk lands · web · Sev 2 · XS · Gate V
  - Both boundaries render unconditionally, so the import starts at row mount rather than at the
    tap; what is left is a cold cache or a bad connection. The fallback is gated on the open flag —
    ungated it would flash a modal over the calendar on first paint, once per row.
  - Built from the Modal primitives, so the scrim, the card and the click-to-dismiss are the real
    dialog's. Known cost: a chunk that lands mid-enter makes the real dialog play that enter again
    from the start. Removing it means hoisting the modal shell out of the lazy chunk so only the
    body swaps — a change to both dialogs and their boundary, not a rider on a fallback.
  - `ModalPlaceholder` is still unimported: it is the edit form's shape, and PR 49 owns it.
    PR 49 has since deleted it rather than wiring it up — see there for why the edit form stopped
    needing a surface-shaped fallback at all. The known cost above is unchanged and still open:
    it is the same hoist, on the confirm dialogs, and nothing in PR 49 reached them.

### PR 25d — the highlight ring is clipped away by the row's own collapse wrapper

- [x] *new, not one of the 109* — below `sm` the ring is drawn outset on a child whose border box **is** the wrapper's clip box, so the mark a deep link leaves on a row is ~95 % invisible on a phone · web · Sev 2 · S · Gate V+D
  - Found while fixing `web-calendar-highlight-ring-cuts`, which now fades a ring almost nobody can
    see. Identical in all three row types carrying
    `highlighted && "rounded-lg ring-2 ring-accent/25 sm:bg-accent/5 sm:ring-0"` —
    `CalendarClient.tsx:880`, `TodoItemContainer.tsx:406`, `FloaterItemContainer.tsx:267` — because
    all three sit in the same `grid-rows-[1fr] overflow-hidden sm:overflow-visible` wrapper, which
    predates the programme (09225a04).
  - Not the one-line `inset-ring` swap it looks like, which is why it is `S`. The wrapper's clip is
    load-bearing — it is what lets the 1fr track actually close below `sm` — and `sm:ring-0` means
    whatever lands has to leave the desktop tint byte-identical. Three shapes were open: an inset
    ring, the ring moved onto the wrapper, or the clip applied only while `removing`, which is the
    trade `TodoItemContainer.tsx:359` already argues for the foreground child's own `overflow` and
    for exactly this reason. Whichever wins, it is one change in three files or it is a fourth way
    these rows differ from each other.
  - **The inset ring won**, spelled `inset-ring-2` / `inset-ring-accent/25` — its own utility in
    Tailwind 4, not v3's `ring-inset` modifier, and compiled against the installed 4.2.2 rather
    than read off a changelog. It stays a `box-shadow`, so it rides the whitelist leg PR 25b added
    and the clip keeps doing the two jobs that make it unmovable.
  - **The ring on the wrapper was rejected** because it escapes the clip by leaving the clock
    behind. `HIGHLIGHT_SETTLE` is written into the foreground child's inline `style` as part of
    `swipeTransition` (`useSwipeRow.ts:51`); the wrapper declares no transition at all, so the ring
    would arrive there as a cut — `web-calendar-highlight-ring-cuts` reopened by its own follow-up —
    unless the clock were duplicated onto a second node, which is a second place for it to drift.
  - **Clipping only while `removing` was rejected** for more than the row above credits. The clip
    is not only what lets the 1fr track close: it is also what contains the swipe. The foreground
    child is translated up to -210px under a finger, and `FloaterItemContainer.tsx:255` already
    records that this same clip is what the mobile `min-h-[54px]` exists to keep the swipe pills
    out of. Removing it outside `removing` would trade a clipped ring for a row painting over its
    neighbours.
  - The shape is a ternary, not a second `&&`, and that is the one piece of this that a diff reads
    as noise. Two things forced it. Tailwind emits `inset-ring-transparent` *after*
    `inset-ring-accent/25` in the utilities layer, so a row carrying both resolves to the invisible
    one; and the ring has to be declared on **both** sides of `highlighted`, for two reasons that
    are *not* "it would cut". It would not. An unmarked row of any of the three declares no
    `box-shadow` at all — nothing in its class string is a ring or shadow utility, and the only
    `box-shadow` rule in `globals.css` outside the drag keyframe is the `:active` press — so it
    computes to `none`, and CSS pads a `none` against the other list adopting its `inset` flags.
    Measured in headless Chromium against the installed 4.2.2 with `transition: box-shadow 1000ms
    linear`, `inset-ring-2` hung off `highlighted` interpolates: spread 0.27px at t+150ms, 1.17px
    at t+600ms, 2px at the end. The same comment already stands in this tree at `globals.css:1179`
    for the drag overlay. What is wrong with it is that *growing a ring is geometry*, and geometry
    is `Emphasis` by rule 2 while this mark rides the `Quick` leg of `swipeTransition` next to the
    desktop tint; a mark lighting up should move paint, which is what a fixed 2px ring changing
    only its alpha does. And declaring both sides is defensive against the real cut:
    `--tw-inset-ring-shadow` initialises **non**-inset, so the first `shadow-*`, ring or press rule
    to leave a composite `box-shadow` on the resting row makes the flags disagree and the property
    stops transitioning at all — the same measurement, with `ring-0` added to the from-state, holds
    2px flat from t+150ms. That is `web-calendar-highlight-ring-cuts` back with every gate green,
    one utility away. Only the colour moves.
  - Desktop is byte-identical by construction: `sm:inset-ring-transparent` replaces `sm:ring-0`,
    and a variant always sorts after the utility it varies. No new duration, curve or ms literal,
    so `motion-budget.json` is untouched.
  - Restores an existing mark rather than introducing a behaviour, so no `GuideTopic`,
    `sinceVersion`, locale strings or `:shared:exportGuideContent` re-run.
  - `calendar-row-highlight-ring.test.tsx` is PR 25b's and asserted the ring by the literal
    `ring-2`, which is a substring of `inset-ring-2` and so would have passed on the defect and on
    the fix alike. It now names the inset spelling, and its "no ring when unmarked" assertion is
    about the absent *colour*, the ring itself being unconditional. The new coverage is
    `row-highlight-ring-clip.test.tsx`, which holds all three rows against one string.

### PR 49 — the drawer placeholder matches the surface it precedes

- [x] `web-drawer-placeholder-wrong-shape-on-desktop` — bottom-sheet skeleton for a desktop modal; `ModalPlaceholder` has zero importers · web · Sev 3 · XS · Gate V
- [x] `web-drawer-placeholder-double-arrival` — static placeholder at final geometry, then vaul slides the real sheet into it · web · Sev 3 · S · Gate V+D
  - **Both rows, one cause: the code split sat around the surface.** `EditFormContainer` and
    `CreateFormContainer` each lazily imported a drawer AND a modal behind a single Suspense
    fallback, so the fallback had to *be* a surface. It could not be the right one — one
    `DrawerPlaceholder` answered for both branches, so a tap above 640px put a bottom sheet on
    screen for an arriving centred modal — and it could not hand over, because being replaced is
    the only thing a fallback does. A finished sheet removed while vaul slides an identical sheet
    into the same place is one tap and two arrivals.
  - So wiring `ModalPlaceholder` up fixes neither. It was written to be the desktop half of that
    pair and never imported — the missing half, not dead code, which `ConfirmPlaceholder`'s own
    doc comment already names as "the edit form" — but a matched pair of impersonations still has
    to get out of the way twice. The split moves **inside** the sheet instead: both shells are
    imported outright and only `CalendarTaskFormBody` is lazy, waited for from within the sheet
    that is already on screen. The surface the user sees is the real one, so it is the right shape
    on both breakpoints without anybody choosing, and it mounts once and slides in once while its
    contents are swapped underneath.
  - This is the shape `TaskFormSheet` already uses — an eager `AppBottomSheet` around a suspended
    `TodoFormContainer` — so it is one fewer way to spell this, not a new one. It also puts the
    boundary where the weight is: the shells are chrome plus a mutation hook and `rrule` was
    already eager in the containers through `useCalendarTaskFormState`, while the body's subtree
    is the chrono title field, the TipTap notes editor and the selector overlays. The build still
    emits `CalendarTaskFormBody` as its own chunk, with `NotesField`'s 311kB behind it.
  - `DrawerPlaceholder` and `ModalPlaceholder` are both gone, replaced by one `FormBodyPlaceholder`
    built from the `sheet-chrome` cards and rows the real body lands into — the reason
    `ConfirmPlaceholder` gives next door, that the radii and row heights should be the ones the
    content arrives at rather than a second set tuned to look like them.
  - **No transition on the swap, deliberately.** `lazy` renders an already-resolved module without
    suspending, so on every open after the first the placeholder never appears at all; a fade
    declared there would animate the body over a sheet that is itself still arriving — paying on
    the common path to smooth the rare one. No new motion literals either: the budget is unchanged.
  - The gate needed building, not just running. `calendar-form-shell-resize` waits for a shell and
    then reads the fields, which the old eager body satisfied exactly as well — so it is green on
    the defect and is evidence for neither row. `tests/unit/calendar-form-placeholder.test.tsx`
    is the one that can tell them apart: it holds the body's chunk open on a gate and asserts what
    the document contains while it is in flight (a modal shell at 900px with the one `aria-busy`
    node *inside* it, no drawer and no portal beside it), then opens the gate and asserts the shell
    node is the same object afterwards — identity being the only thing a second arrival cannot
    fake. All three cases fail on `bd0cefbb^` and pass on the fix.

### PR 50 — the nested confirm drawer’s double scrim

- [x] `web-nested-confirm-drawer-double-scrim` — two `black/80` scrims compose to ~96 % black, both close in one frame · web · Sev 2 · M · Gate D
  - **Two defects, one stack.** The calendar's confirm sheet is a SIBLING of the form sheet it
    covers — `EditDrawer` renders `ConfirmCancelEditDrawer` beside its own `Drawer` rather than
    inside it — so two vaul roots put two portals and two scrims over the same pixels, and opacity
    composes: `black/80` twice over resolves to 96 % black, darker than any surface in the app, so
    raising a confirm sheet read as the page changing colour scheme. A scrim that finds one already
    up now draws no dim of its own (`drawer.tsx:185-199`). The node stays, and stays catching
    pointers — it is what a tap outside the sheet lands on and what vaul releases a drag against.
    Only the dim is dropped.
  - **The registry is keyed on the caller's OPEN flag, not on the scrim being in the document**
    (`drawer.tsx:57`, `:77-83`, `:113`). This is the line that looks wrong in a diff — the document
    is right there and the flag is a second source of truth — and it is the fix. Radix's `Presence`
    holds a closed overlay until an `animationend`, so a drawer registered by MOUNT stays
    registered for the whole `DRAWER_EXIT_MS`. A second sheet opened inside that window — two
    calendar rows tapped in the same third of a second, or a sheet reopened off the one just
    dismissed — found a scrim "already up" that was on its way OUT, declined to dim, and then
    stayed undimmed for its whole life, because the answer below is taken once and never revisited.
    Registering on the flag drops the entry on the frame the drawer is told to close, while the
    scrim it belongs to is still fading, so a "yes" taken here is always about a sheet that is
    staying.
  - **Nestedness is decided once, in a layout effect, and never revised** (`drawer.tsx:165-178`) —
    the other line that reads as a bug. Recomputing when the sheet underneath leaves is the more
    principled rule and would look worse: two stacked sheets are normally dismissed together, so
    the nested scrim would turn from transparent to 80 % black for the last few frames of its own
    exit, and a flash on the way out is most of what this row exists to remove. A layout effect
    rather than render, because a double-invoked render asks twice and, more to the point, asks
    before the commit the answer belongs to; effect and state both land before paint, so the first
    frame is already the right one. And the question is "any drawer but mine" rather than "more
    than one drawer", because whether this scrim's own root has registered by the time it runs
    depends on which commit vaul mounts the portal in — asked this way it has the same answer
    either way.
  - **The second leg is the half that took both sheets away at once.** `useModalPresence` — 200 ms
    at the time, since moved to Quick by PR 41a — was gating a drawer whose exit travels out
    through the bottom edge, which is position changing, which is Emphasis by the geometry rule.
    The window ran out mid-slide: the calendar's form sheet was pulled halfway down, with the
    confirm sheet stacked on it going in the same frame. `DRAWER_EXIT_MS` (`drawer.tsx:24`) and
    `useDrawerPresence` (`:39`) are the drawer's own clock, and `CalendarClient.tsx:620` and
    `:1055` read it; `globals.css:1359-1383` plays vaul's own exit animation on that same rung,
    each selector carrying one attribute more than the injected rule it outranks, so no
    `!important` is needed. One number read twice cannot drift, which is the `MODAL_EXIT_MS`
    arrangement exactly. The enter deliberately keeps vaul's injected half-second: the identical
    0.5s is also written inline as the transition a released drag settles on, so retiming the way
    in without the way a drag lands would split one gesture in two.
  - The gate is `tests/unit/nested-drawer-scrim.test.tsx`, and it asserts the RULE rather than the
    shade — which is what let the shade move underneath it when PR 41a put every scrim on
    `--sheet-scrim` (0.40 light / 0.68 dark). The arithmetic is gentler now; doubling is still
    doubling. The awkward part is that jsdom computes no animation, so Radix drops every closed
    overlay on the spot and the hazard cannot occur there at all: the file spies on
    `getComputedStyle` to report vaul's own `fadeIn`/`fadeOut`, which is the only way to put a
    scrim through the moment the one beneath it is still leaving. Six cases cover the stack — only
    scrim, second scrim, the one beneath going away, the stack emptying and dimming again, a sheet
    opened while the last is still sliding out, and a drawer opened from its own `DrawerTrigger`
    with no flag at all. A seventh compares the exit's two halves, which live in different
    languages and cannot see each other: it reads the rung out of `globals.css` and checks it
    against `DRAWER_EXIT_MS` and the vocabulary, never against itself.

### PR 20 — velocity and rubber-banding on the web swipe

- [x] `web-swipe-velocity-rubberband` — position-only commit, hard clamp, browser-default `ease` · web · Impact O3 · M · Gate V+D
  - Three defects, one cause: every swipe on web read the last frame of a gesture as though it
    were the end of one. `src/lib/swipeGesture.ts` is the answer to all three, and it holds the
    gesture rather than any surface — it takes numbers and gives numbers back, so the four call
    sites keep their own thresholds and their own reasons, and the maths is argued in
    `tests/unit/swipe-gesture.test.ts` instead of inferred from what a jsdom row ended up at.
  - **A release is a projection, not a position.** `projectedRest` carries the surface on at the
    speed it was let go at and asks which resting place *that* lands nearer. One rule, not two:
    a 40px flick commits because it was still travelling, and a 120px drag already being walked
    back does not, because it was not. Both were wrong under the old rule, and wrong in the same
    way. The horizon is `Quick` rather than a constant of its own — that rung is the app
    answering a finger that is on it, and this is the same question one frame after it left.
  - **A limit gives.** The row's clamp was `Math.min(0, Math.max(-actionsWidth, …))`, so 100px of
    finger bought nothing at all: the app declining to admit the gesture happened. `rubberBand`
    is the pager's own curve from PR 21, hoisted rather than copied, and a row spends an eighth
    of its actions on it — about 26px. Half a threshold, which is what the pager grants a refused
    direction, would be 52px here and reads as a fourth action arriving rather than as a limit.
  - **The settle names rungs.** `transform 220ms ease` was the browser default curve on a number
    between two rungs, written out in all three row components. A row goes home on Quick and
    opens on Emphasis, both on the Gesture curve — the same pair the calendar's pager already
    answers a refused swipe and a turned page with, and the same intent Android and iOS spend the
    Gesture spring on at their own row releases. Geometry puts the arrival on Emphasis by the
    second idiom rule; the first rule makes the trip home the shorter of the two.
  - One hook for three rows. The scheduled, calendar and Anytime rows carried three copies of one
    gesture with the same 210px of actions and the same 8px lock, and being copies they had three
    different sets of bugs: only the calendar's had an exit for a touch the platform takes away.
    `useCalendarRowSwipe` is now `src/hooks/useSwipeRow`, and the other two rows get that exit,
    the projection and the give by moving onto it rather than by having them written out again.
  - The hook owns the whole `transition` whitelist for the same reason. Three rows drawing the
    same three properties had drifted into three lists, and only the calendar's named
    `box-shadow` — which is how Tailwind draws the deep-link ring under `sm`, so the other two
    faded the tint and cut the ring. `web-calendar-highlight-ring-cuts` was fixed in one row in
    PR 25b; this is the same fix reaching the two it named.
  - Reduced motion: the settle becomes `transform 0s` and the row is drawn at the resting place
    the release chose. The tracking stays — a finger is not a motion the app plays — which is the
    same cut PR 21 made on the pager.
  - `web.cssMsLiteral` drops 10 → 5: three `transform 220ms ease` and the two surviving
    `background-color 150ms ease` beside them. The other two web ceilings are untouched at
    58 / 3. Two numbers are added and neither is a rung: a 100ms velocity window (Android's
    `VelocityTracker` horizon) and a one-frame floor under it, both measurement constants rather
    than motions anybody watches, argued where they are declared and listed in `docs/motion.md`.

### PR 52 — web drag lift and drop

- ↳ part 1 of 2 of `drag-lift-and-drop` — `dropAnimation={null}` on both dnd contexts; static overlay. Box lives under **PR 53**.
  - The overlay was not only landing-less, it was `opacity-70`. That is the same 70 % the row left
    behind carries, so one word was doing two opposite jobs: the hole in the list and the card in
    the hand looked alike, and the card in the hand looked disabled. The card is opaque now and the
    row keeps the dim, because a hole is what that row actually is.
  - The row left behind kept its value and lost its cut, the same move PR 53 makes on Android: it
    blinked to 70 % on the frame the press fired while the card above it rose over Emphasis, which
    is two events for one gesture. `DRAG_VACATED_TRANSITION` is a `transition` shorthand rather
    than a `transition-opacity duration-emphasis` utility because both rows already hand the DOM
    an inline `style` — dnd-kit writes its own `transform` shorthand into the timeline row's for
    the whole drag, and an inline shorthand outranks any class the row could carry, so the utility
    would have declared a transition that never ran.
  - `.tday-drag-lift` (`globals.css`) declares the **lifted** state and the keyframe holds the
    resting one, which is what the fifth idiom rule buys here: a reduced-motion reader gets a card
    that is already up rather than one pinned to the first frame of a rise. Its `box-shadow: none`
    is a `from` with no `to`, so each of the three overlays keeps its own elevation and this only
    decides that the shadow arrives instead of being there.
  - The landing left `features/calendar/lib`. Two contexts had `dropAnimation={null}` and the third
    had a landing PR 25c gave it, which is exactly the shape a per-screen decision leaves; it is
    `src/lib/dragLiftMotion.ts` now, with the lift beside it, and the three overlays spend it.
  - No ceiling moves. The lift adds no duration, curve or `ms` literal on either client: it names
    `--tday-duration-emphasis` and `--tday-ease-enter`, and its scale is `calc(2 - var(--tday-press-card))`
    rather than a tenth press literal on top of the nine `docs/motion.md` counts.

### PR 53 — Android drag lift and drop

- [x] `drag-lift-and-drop` — A dragged row reads as disabled rather than held: Android only dims it to 70%, and both web drag contexts disable the drop animation outright · and+web · Impact O3 · M · Gate V + D — **final part (2 of 2)**; PR 52 carried the rest
  - **The row's `* 0.7f` is not the whole affordance, and it is not the lift.** Both Android screens
    already draw a preview card that follows the finger (`TimelineTaskDragPreview`,
    `CalendarTaskDragPreview`) while the row stays in the list; the row's dim is the hole and iOS
    says the same thing on the same row (`CalendarScreen.swift:581`). What was missing is the card:
    it was composed straight into 12dp and its final size, so a pick-up had no frame that said the
    app had taken the task — and it was drawn at `alpha = 0.88f`, which is the fade the brief is
    about, one element further in than the row.
  - So the fix is on the preview: opaque, rising from flat to 12dp and from the row's size to
    `2f - PressScales.Card`, on Emphasis over Enter. `TdayDragLift` holds that once for both
    screens and web derives the identical scale from the identical token, which is the two clients
    saying one thing rather than two.
  - The row's dim keeps its value and loses its cut: it now travels on the rise's own rung, so a
    long press is one event instead of a card appearing whole beside a row that blinked. Web's two
    rows say the same thing the same way under PR 52; iOS's row is still a cut and is not in this
    box's scope.
  - `0.7f` is three clients wide and is **not** promoted, because the vocabulary has no alpha
    family to promote it into — `MotionTokens` carries durations, delays, easings, springs and
    press scales. It is named at `TdayDragLift.VacatedAlpha` with the iOS site quoted beside it, so
    the next reader can see it was considered. Whether the vocabulary should grow that family is a
    question for a PR that may edit `MotionTokens.kt`; this one may not.
  - No Android ceiling moves: tween 29, spring 10, pressScale 31. The new spec reads
    `Durations.Emphasis` and `Easings.Enter` through the wrapper, and `1.03f` is never written.

### PR 54 — the web press affordance stops losing to `transition-colors`

- ↳ part 2 of 3 of `press-affordance-unification` — `:where()` at 0,0,0 loses to `transition-colors` at 0,1,0 on every shadcn Button. Box lives under **PR 9a/9b**.
  - **The specificity reading was the wrong diagnosis, and it matters because it points at the
    wrong fix.** Both rules are layered, and a layer outranks specificity outright: measured in
    Chromium, an 0,3,4 selector written inside `@layer base` still loses to `transition-colors` at
    0,1,0 in `@layer utilities`. Escalating would not have worked; the whole class of fix is dead.
  - What the defect actually looked like, before, on a shadcn Button: the squash was not missing —
    `scale: 0.985`, `translate: 0 1.5px` and the shadow all landed, because no utility competes for
    those — but the utility had replaced `transition-property`, so all three arrived in ONE FRAME
    and left in one, under a ripple that still took its 340 ms because no class can reach a pseudo.
    Half an affordance reads worse than none: a jolt under a slow bloom.
  - The same override took the reduced-motion floor with it. `@media (prefers-reduced-motion:
    reduce) { transition-duration: 0ms }` sat in `base`, so every element carrying any
    `transition-*` at all went on animating under it — a shadcn Button at 150 ms, a
    `transition-all duration-300` button at 300.
  - The fix is `@layer tday-press`, opened after `@import "tailwindcss"` so it sorts after
    `utilities`, holding the smallest set that has to win: which properties transition, what curve
    they take, the reduced-motion floor, and the pressed shadow — whose `!important` comes OUT,
    the layer doing that work now.
  - **What deliberately did not move up.** The pressed scale, so that the 17 call sites pressing to
    their own depth still win: the onboarding wizard's step chip at `[0.97]` (which is
    `--tday-press-card`), eight sheet buttons at `[0.99]`, and eight small round icon buttons — the
    task FAB among them — at `scale-95`. Verified in Chromium that all three depths still do. Six
    further sites say `[0.985]`, which is `--tday-press-row` written out rather than a depth of
    their own; retiring those six literals is a separate row. And the press LENGTH, so that the dozen
    pressables carrying a `duration-200` of their own keep it — bringing those onto the ladder is a
    call-site migration with its own row, not a silent retiming to be taken for free here. A site
    that says nothing still gets Quick from `base`; a bare `transition-*` rides Tailwind's
    un-overridden 150, which is the same rung, so both land in the same place.
  - **The property list displaces `transition-all` too, and no closed list is a superset of `all`.**
    It IS a superset of the two named utilities — `transition-colors` adds
    outline-color/text-decoration-color/fill/stroke, `transition-transform` adds transform/rotate,
    so displacing either costs the call site nothing. `transition-all` is on 19 class strings and
    the list has to be checked against them one at a time: on all but one the properties actually in
    flight (translate, scale, background-color, color, opacity, box-shadow) are already in it. The
    exception is the dock tab, `RootDock.tsx:185`, `sm:min-w-[104px]` when selected against
    `sm:min-w-12` when not — measured in Chromium against the compiled stylesheet, it went from
    easing over 200 ms to reaching 104 px in the first frame, beside an indicator pill that is a
    `pointer-events-none` div this selector does not match and so still glides for 300 ms. That is
    this unit's own defect shape relocated, and it would have taken the written argument at
    `RootDock.tsx:101` — the re-measure timed against "the tab width transition (200ms)" — with it.
    `min-width` is therefore in the list, and it has to be there rather than at the call site: a
    `transition-[min-width]` on the button is displaced by this same declaration, and the
    `!important` that would beat it is the escalation the layer exists to retire. `width` and
    `height` stay out — nothing pressable animates them, and `sm:min-w-*` appears on exactly one
    element in `src`. The tab's curve does change, from Tailwind's default ease to
    `--tday-ease-gesture`, which is the vocabulary's press curve and the same one its colours
    already took after this PR.
  - Two literals retired and the ceiling lowered with them: `web.cssMsLiteral` 5 → 3. The press ran
    on a hand-written 180 (now `Quick`: the app answering a finger, and the number every bare
    `transition-*` already rides) and the ripple on 340 (now `Emphasis`: it changes size, and 320 is
    that motion to within a frame). The 340–420 ms band in `docs/motion.md` loses its web member.
  - `tests/guardrails/press-affordance-cascade.test.ts` is the gate. It reads the stylesheet rather
    than a rendered className, for the reason `toast-action-specificity` gives next door: the class
    was present and correct the whole time. It fails on `ce071f30` on three counts — no layer, an
    `!important` in the affordance, and the pressed scale written out rather than read from the
    token — and a fourth case holds `min-width` in the property list, which is the one declaration
    here whose removal has no local symptom: the tab that needs it cannot ask for it from below.

### PR 55 — the web route hand-over

- ↳ part 1 of 2 of `route-change-handover` — `.tday-route-fade` 140 → 200 ms; adopt React Router `viewTransition`. Box lives under **PR 31**.
  - **The 140 came with a written argument and half of it survives, so the half that survives is
    restated at the call site rather than deleted.** `globals.css` said the route fade is short
    because "this sits between a tap and the screen the user asked for, and anything longer reads
    as lag", and against the long end — `Scene`, or Android's own 360 — that is still exactly
    right. What it could not defend was 140 in particular. It named no rung, so nothing downstream
    could tell a decision from a number somebody liked; and the lag it was written against is the
    wait in front of the destination, not the length of the fade — the arriving screen is laid out
    and hit-testable from its first frame, so this animation never sits between a tap and its
    answer. A thing arriving with no reason to be another length is `Enter`. `docs/motion.md`'s
    `Scene` bullet pointed at that argument and now says which rung it points AT.
  - **The view transition is not a second fade, it is the half `RouteFade` could not afford.**
    That component's standing argument is that only the arriving screen fades because fading the
    leaving one means holding its whole tree mounted — live queries, realtime subscriptions and
    focus effects running in duplicate for those milliseconds. A view transition hands the leaving
    screen back as a flat snapshot, which is the one thing that argument was missing, at none of
    its cost. So the two paths COMPOSE rather than switch: `.tday-route-fade` fades the arrival on
    every browser, and where `document.startViewTransition` exists the snapshot fades out over the
    top of it. Nothing detects which path it is on and there is no branch in the markup — a
    browser without view transitions sees exactly what it saw before, at 200 ms.
  - Composing is what shapes the three UA overrides, and none of them has a local symptom if it is
    deleted — the transition still runs and still looks like a route change. `new` must NOT animate:
    it renders the arriving screen live, so `.tday-route-fade` is already fading up inside it and
    the UA's own opacity curve on top of that is a fade of a fade. `old` is therefore the half that
    moves, so it needs `z-index`, because the UA paints `new` last and an opaque `new` hides the
    outgoing half completely. And the blend must be `normal`: `plus-lighter` is what keeps the UA's
    symmetric crossfade from dipping, and over two layers each opaque at one end it blows the screen
    out to white. What is left is a fade THROUGH the constant background — which is the fade
    `RouteFade` already describes, now with the outgoing screen in it.
  - The opt-in is asked by `src/lib/routeHandover.ts` and wired in `src/lib/navigation.tsx`, the
    app's one navigation chokepoint (39 importers). Three call sites inside the shell were routing
    around it and now go through `useRouter().push`: the guide's "try it" (`GuideScreen.tsx`) and
    the release toast (`ReleaseUpdateAnnouncer.tsx`), both of which were hand-building
    `/${locale}/app/...` that `localizePath` already builds. What is left on raw `react-router-dom`
    is outside the shell — the landing, 404, route-error and blog pages — plus ONE deliberate
    exception, argued at its call site: Settings' locale switch. That is the one pathname change
    here where the screen being left is not being left, and `changeLanguage` has already started
    re-rendering it, so the snapshot a transition would take is of a tree mid-swap.
  - The question it asks about the destination is the same one `RouteFade` asks — **pathname**, not
    the full location — because a view transition snapshots the whole document, so one started for a
    task-focus query param would crossfade a page with itself, which is the flash `RouteFade`
    already declines to draw. A `to` that is only a query string or only a fragment needs its own
    guard rather than falling out of that comparison: it splits to the EMPTY string, not to the path
    it was clicked on, so the comparison alone would call it a different page. `back()` gets no
    opt-in and cannot have one: `navigate(-1)` takes a delta rather than a destination, and the
    browser's own back button never comes through this module, so a POP keeps the path that exists
    everywhere.
  - **Reduced motion is answered in the opt-in, not only in the stylesheet, because the CSS lands
    too late to be the whole answer.** `globals.css` can pin the finished frame; it cannot stop
    React Router taking the opt-in's slower path, which does not commit the new route in the
    navigation at all — it parks it in `pendingState`, picks it up two effect passes later and
    applies it inside the `startViewTransition` callback. Left to CSS alone, a user who asked for
    less motion would pay that deferral to be shown nothing. The stylesheet override stays as
    defence in depth for a call site that passes `viewTransition` directly.
  - Reduced motion: `.tday-route-fade` is off as before, and the outgoing snapshot is taken off
    outright rather than merely un-animated — an un-animated `old` is opaque and on top, and would
    hold the screen the user just left for the frame the transition takes to end. The destination is
    the whole of the finished state at a route change.
  - One literal retired and the ceiling lowered with it: `web.cssMsLiteral` 3 → 2. The keyframe was
    renamed with it, `fade-in` → `tday-route-fade-in`, so the pair reads as a pair and matches the
    prefix every other keyframe in the file carries.
  - `tests/guardrails/route-handover.test.ts` is the gate, and it is in two halves because the two
    halves are testable in different ways. The CSS half reads the stylesheet rather than a render for
    a reason jsdom makes unavoidable: there are no view transitions to start there, so a rendered
    assertion could only ever see the path that was already present. The navigation half calls
    `startsRouteHandover` and asserts what it RETURNS — which is why the function sits in its own
    module instead of staying private to `navigation.tsx`. Grepping the source for the guards, which
    is what an earlier draft did, stayed green on an implementation with the empty-path guard moved
    below the `return` and on one whose split let `#anchor` through: both are the self-crossfade the
    unit exists to prevent, and both have their own case now. It fails on `6774e5c2` on the rung and
    on all three overrides being absent.

### PR 26 — two iOS feed cuts

- [x] `ios-today-block-removal-is-a-cut` — `.animation` inside the `if`; ~72 pt of layout vanishes in one frame · ios · Sev 3 · S · Gate G+TF
  - **The modifier was inside the thing it was for.** `ScheduledTaskHomeScreen.swift` wrote
    `.animation(.spring(0.34/0.9), value: viewModel.todayTodos.map(\.id))` on the `VStack` *inside*
    `if !viewModel.todayTodos.isEmpty { … }`. A modifier written inside a branch is part of that
    branch: the update that empties `todayTodos` takes the modifier out of the tree in the same pass
    it takes the rows out, so at the moment the removal is decided there is no transaction open. The
    block cuts, the rows' own `.transition` legs cut with it — a `.transition` outside a transaction
    is inert — and the ~72 pt the block held closes in one frame. It animated everything that
    happened *within* the block perfectly well, which is why it reads correct and why review has
    walked past it: it did half its job, and the half it did is the half anyone looks at.
  - The fix is position, not duration: a `Group` around the `if`, with the `.animation` on the
    `Group`. That is the only place that spans both states of the branch. The block then leaves on
    `TdayFeedItemMotion.row` — a feed's departure rung, because the block is what this feed adds and
    removes alongside its rows — while the board and the lists below travel up into its space on
    `placement`. Two beats, 150 then 320, in that order.
  - The rows joined `TdayFeedItemMotion` while the file was open, which is PR 47's split reaching its
    third feed: arrival on Enter, departure on Quick, travel on Emphasis, all on Standard. Reduced
    motion is refused at both mechanisms separately for the reason `row(reduceMotion:)` gives —
    `nil` to the `.animation(_:value:)` for the travel, `.identity` for the legs.
  - **Gate G is rule D of `motion-reachability-ios.test.ts`**, and it is a new rule rather than a case
    added to rule A. Rule A asks whether a transaction exists *somewhere in the type*; it could not
    ask where the modifier is written, and on both of these sites it saw a gate that appears in an
    `.animation(value:)` and said nothing. Rule D reads the branches enclosing each
    `.animation(_:value:)` line and fails when one of them is gated on the state that modifier
    animates. It has no allowlist and is not meant to grow one: a branch that should cut wants no
    animation at all. Verified red on `94097db4` on exactly these two sites and nothing else in the
    tree, green after.
- [x] `ios-calendar-day-swap-stacks-rows` — both days' rows play insert and removal over the same pixels · ios · Sev 3 · S · Gate G+TF
  - The calendar's day list had the same misplaced `.animation` as the row above and is fixed the same
    way, but that is not this row: hoisting it makes the swap animate, and a swap that animates as it
    stood is worse than one that cuts. `pendingItems` is keyed by task id, and two days share no task,
    so a day change is one `ForEach` diff in which every row of the outgoing day is removed and every
    row of the incoming day is inserted — concurrently, in one slot, over the same pixels. Both legs
    also carried `.move(edge: .top)`, so both days slid down from the top through each other.
  - **Identity first.** `.id(selectedDayStart)` on the day's `VStack` makes a day change one view
    replaced rather than N removals interleaved with M insertions. That is what gives the swap two
    things to sequence; without it there is nothing to hold an order between. The day is normalised
    to `startOfDay` because `isSelectedDay` already throws the time away, and an identity that did
    not would replace a day with itself.
  - **Then ordering.** `calendarDayListTransition()` is asymmetric and *sequential*, which is where it
    parts company with `TdayFeedItemMotion.row`: a row's arrival and departure are concurrent on
    purpose, because they happen to different rows in different places while the feed stays the same
    feed. A day swap is one list replacing another in the same slot. So the outgoing day leaves on
    `Durations.departure` and the incoming day is delayed by exactly that before it arrives on
    `Durations.arrival` — the delay is that constant and not a number of its own, because what it has
    to match is that leg. No `.move`: the rows are not travelling anywhere, the day under them
    changed. The card's height crosses on `placement`, the rung a box changing size answers to.
  - The transaction is keyed on `pendingDayAnimationKey`, which carries the day as well as the row
    ids. The ids alone would open it for every swap that exists today, but the `.id()` is the thing
    being animated and a transaction should be keyed to what it carries, not to a property of the
    data that happens to imply it.
  - The empty day gets the same ordered transition. A day with nothing on it is still a day arriving,
    and has no more business being drawn over the day it replaced than a populated one does.
  - **Rule D resolves one hop**, which is what puts this row behind gate G rather than behind TF
    alone. The rule was red here before the fix only because the site wrote `value:
    pendingItems.map(\.id)` inline; naming that expression `pendingDayAnimationKey` would have made
    the identical defect invisible to a purely textual match, and a gate that a rename can switch off
    is not a gate on this row at all. So the rule expands a bare identifier in `value:` through the
    type's own computed `var`s before asking what the expression reads — one hop, no transitive
    closure, because a key assembled out of the branch's state is the shape that exists and chasing
    further would pull half a screen's properties into every expression. Re-verified by putting the
    `.animation` back inside the `if` in the shape the screen now ships: red, naming the state and the
    property it travelled through.
  - iOS `spring` ceiling 108 → 104, closing the unit: the two screens each carried the same
    `0.34/0.9` spring, two counted literals apiece, and `Gesture` is 0.34/**0.82** — an orphan pair,
    not a near-miss of a token that a migration could have absorbed.

## Phase 8 — accessibility

### PR 33a — the web reduced-motion floor and its subscribing hook

- ↳ part 1 of 6 of `reduced-motion-coverage` — blanket `@media` floor after the Tailwind import + `useReducedMotion` subscribing hook + `src/lib/scroll.ts`. Box lives under **PR 35b**.
  - **The hook this row asks for already exists.** `usePrefersReducedMotion` (`src/lib/prefersReducedMotion.ts`) landed in Phase 5 and already subscribes, through `useSyncExternalStore`, so the preference is live at runtime. The row predates it; a `useReducedMotion` beside it would be a second answer to one question. What landed is the floor and the scroll module.
  - **A floor, not an off switch.** The blanket rule pins `animation-duration` to `1ms` with `animation-iteration-count: 1` rather than writing `animation: none`, because `none` takes the fill mode with it — an element whose resting CSS *is* its own start frame would be held there for good, which is the fifth idiom rule inverted. A floored animation still fills onto its last frame and still fires `animationend`, so the JS waiting on one advances. The delays go to `0s` for the other half of the same rule. Spinners and skeleton pulses are exempt: they are status with no finished state to draw, and one that has stopped says the work is done when it is not.
- [x] `web-scrollintoview-ignores-reduced-motion` — search-result jump always smooth-scrolls · web · Sev 2 · XS · Gate V
  - Eight sites, not one — `git grep -n 'behavior: "smooth"' -- tday-web/src` on the parent commit returns four `scrollIntoView` jumps, three `scrollTo`-to-top calls and the drawer's `scrollBy`, and zero on this one. `scrollIntoView({ behavior: "smooth" })` is documented to **override** the `scroll-behavior` property rather than defer to it, so the CSS floor above cannot reach a single one of them and the preference has to be read in JS. `src/lib/scroll.ts` is where it is read now. It downgrades only, and `GeneralLayout`'s route reset is routed through it as the ninth caller for exactly that reason: it asks for `"auto"` because the jump is the point, and a preference for less motion must not be able to give it more. `CommandPalette`'s cursor stays out — it names no behavior, so the spec already jumps, and routing it would hand it the module's smooth default.
- [x] `web-calendar-slide-ignores-reduced-motion` — `calendar-styles.css` has no reduced-motion block at all (29 lines, zero) · web · Sev 3 · XS · Gate G
  - **Already closed by PR 25a, and re-checked rather than re-done.** The file now carries two `prefers-reduced-motion` blocks covering both of the classes that animate — the page-turn slide and the floor refusal — and nothing in it is unguarded. What this row bought instead is the question being asked of every stylesheet under `src/` rather than of the animations somebody happened to list: `tests/guardrails/reduced-motion-floor.test.ts` rule C fails on any CSS file that declares motion and never mentions the preference.

### PR 33b — web sound and haptic preferences

- ↳ part 2 of 6 of `reduced-motion-coverage` — sound/haptic preference + Settings UI (`haptics.ts`, `TodoCheckbox.tsx:44-53`). Box lives under **PR 35b**.
  - **Web is the only client that needed this, and that is the row's argument.** Android's `TaskCompletionSound.play()` refuses unless the ringer is in `RINGER_MODE_NORMAL`, iOS's `SoundManager` runs on an `.ambient` session the silent switch silences, and every native haptic goes out through `performHapticFeedback` / `UIFeedbackGenerator`, which the OS mutes with its own touch-feedback setting. Three system switches, none of which a browser is handed: there is no media query for "this phone is on silent", and `navigator.vibrate()` buzzes whatever the system haptic setting says. So the two natives keep no preference of their own and web grows one — `src/lib/feedbackPreferences.ts`, device-local beside `isRestingFloatersEnabled` rather than on the account, because the question is about the machine in the room.
  - **One gate, not seventy-four.** The haptic preference is read inside `haptics.ts`'s private `vibrate()`, the single chokepoint its eight verbs already share, so all ~74 call sites across 18 files are covered and the next verb added to that file gets it for free. `hapticsSupported()` comes out of the same file for Settings to ask before drawing a switch over a vibrator that does not exist on any desktop or any iPhone — resolved per call rather than captured at module scope, for the reason `prefersReducedMotion.ts` already gives about `matchMedia`.
  - **The sound preference is read at the tap, not at mount.** `TodoCheckbox` builds its two `Audio` objects in an effect; deciding there would keep a muted browser popping until the row remounted. Both clips answer to one switch — somebody who muted the app did not ask to be muted only while finishing things.
  - **Sign-out does not undo it.** `PRESERVED_STORAGE_KEYS` grows from three to five. The list's existing rule already covers it (these belong to the browser, not to a server account), and an accessibility choice adds a second: an expiring token is not something the user did, and it must not hand back a buzz they turned off. `docs/security/SECURITY_CONTROLS.md`'s plaintext-`localStorage` inventory carries both new keys and the new count.
  - **No new motion literals.** Nothing here declares a duration, a curve or a spring, so the three budgets are untouched; `TodoCheckbox`'s own `DURATION_MS.quick` and `duration-quick` are as Phase 6 left them.
  - Guide topic `sound-and-vibration` (Gestures, `sinceVersion` 0.7.23, WEB-only), its strings in all ten locales, and the regenerated guide artifacts. It reuses the covered `waves` glyph rather than minting one: a new glyph would mean an Android vector drawable plus an iOS imageset for a topic neither platform shows.

### PR 34a — Android reads the system animator scale

- ↳ part 3 of 6 of `reduced-motion-coverage` — `ContentObserver` + `LocalTdayMotionScale` + `scaledDelay` through all 5 raw `delay()`. Box lives under **PR 35b**.

### PR 34b — the in-app Android “Reduce motion” toggle

- ↳ part 4 of 6 of `reduced-motion-coverage` — in-app "Reduce motion" toggle + persistence. Box lives under **PR 35b**.
  - **Android is the only client that needed this, and that is the row's argument.** Web is handed `prefers-reduced-motion` and iOS `UIAccessibility.isReduceMotionEnabled` — accessibility settings, per-user, discoverable, and already the thing 33a and 35a read. Android has no such signal: what it has is `ANIMATOR_DURATION_SCALE`, which is device-wide, sits behind developer options on many builds, and on a managed profile that locks them cannot be reached at all. So the two other clients keep no in-app switch and Android grows one. Adding the same switch to web or iOS would put a second answer in front of a user who already has a first one, and the failure mode of two switches is the app agreeing with neither.
  - **One-way composition, not a second opinion.** `effectiveMotionScale(systemScale, reduceInApp)` in `TdayMotion.kt` is the whole rule: the in-app switch resolves to 0 and otherwise defers. It can subtract motion and can never add any back, so a device at 0x stays at 0x with the switch off — the switch being off is "nothing extra", never "animations please". `TdayMotionTest` pins that direction, which for a rule about not re-enabling something is the half worth testing without a device.
  - **The preference is observed, not read once.** `ReduceMotionPreferenceStore.observeEnabled` registers an `OnSharedPreferenceChangeListener` alongside 34a's `ContentObserver`, both inside `ProvideTdayMotionScale`. Without it the switch would take effect at the next cold start and nowhere before it — the switch is in Settings and the motion it governs is on every other screen, held by a composition a preference write does not invalidate. The returned cancel is load-bearing rather than tidy: `SharedPreferences` holds listeners weakly, so dropping it lets the registration be collected with no symptom but a toggle that quietly stops working.
  - **The row says when it cannot act.** Where the device's own setting has already removed animations, the switch is drawn on and silenced through the existing `SettingsSilencedWhen` — the same treatment the reminder rows get while notifications are off — with a line naming Android as the one that decided. Drawing it off would claim the app is animating when it is not; drawing it on and live would be a control that springs back the instant it is touched. This is the one caller of `rememberSystemMotionScale()`, which exists so the two halves can be told apart in the one place that has to tell them apart.
  - **Its own "Motion" heading, not a second row under Appearance.** Appearance is what the app looks like standing still; this is how long it takes to get there. The heading is also the only place the card can hang a "?", since only the first row of a same-section run draws one.
  - **The two halves do not have the same reach, and the file says so.** The system scale is `MotionDurationScale` in the recomposer's coroutine context, which every `animate*AsState` obeys with no code written for it; that context belongs to the composition and a composable cannot substitute one for its own subtree, so the in-app switch reaches exactly the motion that *asks* — the ~13 files built on `rememberTdayMotionEnabled` / `rememberTdayMotionScale` / `scaledDelay`. The other ~191 animation sites keep animating at the device's scale until they are migrated, which is the `reduced-motion-coverage` box itself ("honoured in 2 of ~99 Android sites"), not a regression 34b introduces. The alternative — installing a custom `Recomposer` through `WindowRecomposerPolicy` — buys the whole app at the cost of an `@InternalComposeUiApi` in every `setContent` root, and is not a trade to make blind without a device.
  - **Never cleared on sign-out**, mirroring `RestingFloatersPreferenceStore` and 33b's call on web: an expiring token is not a request to start animating again.
  - **No new motion literals.** Nothing here declares a duration, a curve or a spring — `REDUCED` is a scale of zero, not a rung — so the three budgets are untouched.
  - Guide topic `reduce-motion` (Gestures, `sinceVersion` 0.7.23, ANDROID-only), its strings in all ten locales, three Android string resources in all ten `values*/strings.xml`, and the regenerated guide artifacts. It reuses the already-covered `activity` glyph — a line that moves, which is the thing being turned down — rather than minting one that would need both a drawable and an imageset for a topic only one platform shows.
  - **A wait runs on the clock of the motion it is covering, and there are two clocks now.** The bullet above says the in-app switch reaches only the motion that asks. For an animation that is a coverage gap; for a `scaledDelay` it is a correctness hazard, because until `effectiveMotionScale` existed the app's scale and Compose's `MotionDurationScale` were the same number by construction and every wait was in step with whatever it was covering. Handing the app's scale to a wait that covers an *ungated* animation removes the wait and keeps the motion — the fifth idiom rule broken from the side nobody watches — and the app tears a surface out from under a transition still running, which is the jump the wait existed to hide. So `rememberSystemMotionScale()` is no longer the Settings row's private accessor: it reads a second composition local published beside the first, is free per row, and is what a wait over ungated motion now takes. Triaged: the search-close hold in `ScheduledTaskHomeScreen` (waits out `TdayApp`'s `fadeIn` route handover), the two nav settles, the two flash holds and the flash's pulse gap in `TodoListScreen`, and the `Earlier` hand-off (waits out `animateItem`'s `FadeOut`) move to the system scale, each naming the ungated animation it waits for so migrating that animation also moves the wait back. `TaskSwipeRevealState.playHint`, `TdayConfetti` and `TdayEmptyState` were already safe — each returns before its own wait when the scale is 0, so the wait and the motion leave together.
  - **The five check-off runs went the other way: gate the motion, not the wait.** The third leg of every three-beat run waits out a row's fade, and that fade was a bare `tween` while the tint and the strike either side of it were already gated — so the switch would have pulled a row out of the list at full opacity, by a wait of nothing, while the fade meant to carry it off ran on. Gating it (`completionAlpha` / `completionOffsetY` / `rowAlpha` / `rowScale` / `rowOffsetY` in `TodoListScreen`, `ScheduledTaskHomeScreen`, `CalendarScreen` × 2 and `CompletedScreen`) keeps the run on one clock and makes `TASK_COMPLETION_CHECK_TO_STRIKE_MS`'s written argument true again, which the alternative — the wait on the device's clock — would not: it would leave the app's most-performed interaction half instant and half animated. No literal moved; each `tween` is now the first arm of an `if (motionEnabled)`, the same shape the tint beside it already had.
  - **The switch is disabled, not merely covered, where the device has already answered.** `SettingsSilencedWhen` blocks pointers with a transparent `clickable` overlay and nothing else, so a `Switch` under it kept its toggle action in the semantics tree: TalkBack activates that node, not the overlay. The row draws `checked = enabled || systemReduced`, so the write was also invisible — a preference the user never chose, waiting to take effect when Android's animations came back. `enabled = !systemReduced` removes the action rather than hiding it; the disabled colours restate the checked ones so the dimming stays the wrapper's single 0.45 and "on" stays legible, which is the whole of what the row has to say.
  - **The guide topic described the switch it was going to be, not the one it is.** The body shipped in all ten locales said rows, sheets and screens go straight to where they were heading; sheets and route handovers are ungated and keep animating, which the coverage bullet above says in the same commit. The copy now names what the switch actually reaches — the check-off, the swipe hint, the confetti, the empty-day scene, and the waits those were covering — and hands screens and sheets back to the phone's own animation setting, which is also where the tip already points. The coverage gap is `reduced-motion-coverage`'s to close; a promise that it already is closed was not.

### PR 35a — iOS reads the accessibility environment

- ↳ part 5 of 6 of `reduced-motion-coverage` — root `@Environment` read + custom `EnvironmentKey` + `tdayAnimation(_:)`. Box lives under **PR 35b**.
  - **iOS needed the plumbing and nothing else — that is the difference from 34a and 34b.** The setting is `accessibilityReduceMotion`, a per-user accessibility switch SwiftUI publishes into every environment and keeps live, so there was no signal to synthesise and (per 34b's argument) no in-app switch to offer. What was missing was a single place the answer becomes an `Animation?`: seven views each read the setting and each spelled `reduceMotion ? nil : x` in their own hand, which is seven places to forget and none to fix it once. `TdayMotionEnvironment.swift` is that place; the seven now read `\.tdayAnimation` and nothing outside that file reads the system key.
  - **`callAsFunction`, so the environment value and the verb are one token.** `tdayAnimation(TdayMotion.settle)` is the whole call site. A named method would read as a second thing to remember to call — which is exactly the thing that was being forgotten — and a closure in the environment would be diffed on the identity of whoever built it rather than on the answer. `TdayMotionResolution` is an `Equatable` struct over one `Bool` for that reason.
  - **`nil` is the mechanism, and it is the fifth idiom rule in one line.** `withAnimation(nil)` and `.animation(nil, value:)` still apply the state change; they only refuse to animate the trip. So refusing motion on iOS cannot hold a surface at the start of a fade and leaves no wait behind it — there is no iOS equivalent of Android's `scaledDelay` problem here, because nothing in these seven files waits out a motion it also gates.
  - **The value is composed, not stored, and both halves are load-bearing.** The custom key carries `Bool?` — `nil` meaning nobody upstream has answered — and the accessor falls back to `accessibilityReduceMotion` out of the same `EnvironmentValues`. The fallback covers what the root cannot reach: a hand-built `UIHostingController` starts a fresh environment and inherits none of the app's own values, while UIKit keeps feeding it the system keys from its trait environment — and `CalendarPagingScrollView` builds one per month page. The override is what makes the answer *live*: an accessor reading a key it never declared a dependency on is correct at first draw and says nothing about the flip, so `tdayResolvedMotion()` declares that dependency in the ordinary way and writes the answer down. The override direction is pinned in `TdayMotionEnvironmentTests`. The fallback's *reduce* direction is not, and cannot be: `EnvironmentValues.accessibilityReduceMotion` is declared get-only — the same fact that forced the custom key in the first place — so no test can stage a system answer for the fallback to carry. An earlier draft of that suite assigned to it and would have failed the whole bundle to build. That half is a device row instead, `docs/verification/phase-8-device-pass.md`'s calendar line, on the one surface built from hand-made hosting controllers and therefore reaching the fallback and nothing else.
  - **Installed at the scene root *and* in `tdayAppTheme`, and the pair is deliberate.** `tdayAppTheme` is the one wrapper both window roots go through — the main hierarchy and `AppLockWindowHost`, which re-applies the theme for the same reason it would have to re-apply this; the same call `ProvideTdayMotionScale` makes inside Android's `TdayTheme`. That alone was not enough, and the gap was at the worst possible place: `AppRootView` applies the theme to *its own body*, and `@Environment` resolves against the environment a view was placed in, so the theme's provider reached every descendant of `AppRootView` and none of `AppRootView`'s own three animations — the tab hand-over, the dock and create button, and the onboarding blur. Those ran on the accessor's fallback, which this PR's own file calls correct at first draw and silent about the flip, so the three sites nearest the root were the three the PR did not make live. `TdayApp`'s `WindowGroup` installs the provider above the root view now; the theme keeps its copy for the separate window, which inherits nothing. The launch splash is under the scene-root provider too and draws no motion at all, which was checked rather than assumed. `reduced-motion-floor`'s rule D fails if either install goes missing — it cannot see a call applied to the wrong thing, which is what the device row is for, but it can see one deleted.
  - **`TdayFeedItemMotion.row(reduceMotion:)` keeps its boolean.** A `.transition` cannot be switched off by a nil animation — it does not open the transaction it plays in — so five sites read `!tdayAnimation.isEnabled` rather than passing a resolution. One property with one meaning beat adding an `isReduced` beside it; the negative spelling belongs to that function's parameter, not to this one's answer.
  - **`TodoListViewModel` still reads `UIAccessibility.isReduceMotionEnabled` directly**, and correctly: it is not a `View` and has no environment to read. Its comment already says so. It is the one place that would disagree if the app's answer ever stopped being the system's.
  - **One literal off, none on.** Nothing here declares a duration, a curve or a spring; the two `easeInOut` literals in `TdayEmptyState` lost their `guard` and gained a `tdayAnimation(…)` wrapper, and both numbers are where Phase 6 left them. The one that moved is the root snackbar's `.snappy(duration: 0.3)`, which was the last `.animation` in `AppRootView` that Reduce Motion could not switch off — a toast still sliding the full height of its own arrival for a user who asked for less. `.snappy(duration: 0.3)` is SwiftUI's own preset, `spring(duration: 0.3, bounce: 0.15)`, and the Snappy token is `response: 0.28, dampingFraction: 0.86` — the same bounce and the same perceptual length to within a frame, so naming the token is not a retiming and what the site gains is the gate. `ios.easeDuration` drops 28 → 27 in the same commit.
  - **The comment that claimed the whole file was a comment worth one bullet of correction.** `AppRootView`'s gate said every `.animation` below passed through it while the snackbar's did not, which is the shape of overclaim 34b's guide copy was corrected for — a promise that a box is closed is not the box being closed. It is true now that the snackbar is gated, and the comment names the one thing still outside it: `AppSnackbar` is a separate view with its own environment, and its drag snap-back (`withAnimation(.snappy(duration: 0.25))`) still animates. That is owed to `reduced-motion-coverage`, not asserted away.

### PR 35b — the five iOS amplitude decisions

- [ ] `reduced-motion-coverage` — Reduced motion is honoured in 2 of ~99 Android sites, 2 of 20 iOS files and ~15% of web — and on every client the setting cannot be seen to change at runtime · all · Impact O4 · L · Gate V + J + D + X + TF — **final part (6 of 6)**; PR 33a, PR 33b, PR 34a, PR 34b, PR 35a carried the rest
  - **`nil` is not the accommodation; it is one of two.** 35a's file said the mechanism was `withAnimation(nil)`, and for the surfaces it reached that was right — a tint, a crossfaded feed body, an onboarding blur, all of them motions whose amplitude was already nothing. What the setting is actually about is amplitude, and the standard substitute for a large travel is a crossfade rather than an absence. Refusing everything fails the same user twice: a full-bleed modal that replaces the screen between two frames gives the eye nothing to follow to it, and a toast that blinks in and out over a feed reads as the app glitching. So the resolver answers in a third shape — `tdayAnimation(spec, reduced: substitute)` and a `.transition` twin — which returns a **non-optional** on purpose: a site reaching for it has already decided something plays, and a nil escaping there would be that decision reversed by whoever wrote the substitute. The overload is the reviewable form of the judgement, because it makes the call site name both halves.
  - **Sheet card — crossfade.** The longest travel in the app, a whole screen height, and the one surface that arrives over content the user was reading. Under Reduce Motion it is placed at its resting height and fades up with the scrim, on the scrim's own curve rather than a third number: a card that has stopped travelling is doing what the scrim is doing, and separate lengths would be the only thing making them read as two surfaces. The deferred teardown is untouched and that is checked rather than assumed — `scrimOut`'s 0.20 finishes inside `exitDuration`'s 0.24, so the wait still covers a running animation. Handing the card `nil` is what would have turned it into a wait in front of a destination already drawn, which is Android's `scaledDelay` hazard arriving on iOS by a different road.
  - **Calendar page turn — refused, and it took the completion with it.** A chevron slides a whole month grid the full width of the screen; the grid *is* the page, so there is nothing to crossfade that is not the thing being asked for, and this is the one of the five that comes out as an outright refusal. The bug is what the refusal exposed: `scrollViewDidEndScrollingAnimation` was carrying two things, the `isProgrammaticScroll` latch **and** the only notification the parent ever gets that a page turn finished. The un-animated path retracted the latch and said nothing, which cost nothing while that path was only ever a re-centring (`notifyParentIfNeeded` drops the centre index anyway) — and would have left a Reduce Motion user's calendar jumping one month and then freezing, both chevrons dead, `selection` never back at centre. It reports its own arrival now. Removing the trip must not remove what arriving told somebody. A swipe is untouched: the finger is carrying that one.
  - **Root dock — crossfade, and a token it was already spelling.** `.scale(scale: 0.82, anchor: .leading)` is an eighteen-percent size change out of the dock's leading edge; Reduce Motion keeps the opacity and drops the scale, because the two `if` arms are different controls at different widths and a cut would swap a 56 pt pill for a full segmented control in one frame, in the corner of the screen nobody is looking at. The spring beside it was `0.34 / 0.82` written out, which is `Gesture` exactly — a control carrying on after a finger has let go, which expand-on-tap and collapse-on-scroll both are. Naming it is not a retiming and is what put the gate there: `ios.spring` 104 → 102 in the same commit.
  - **Snackbar — crossfade, revising 35a rather than extending it.** 35a gated this site and refused the slide and the fade together; the comment it left said so plainly, which is why this is a correction and not a discovery. A toast is the one surface in the app with nothing around it to explain its arrival — no row closes over it, no scrim dims for it — and it carries an Undo the user has seconds to reach. The travel goes, a full toast height up from off the screen; the crossfade stays, on `Enter`. `AppSnackbar`'s own drag snap-back is still outside the gate and still named as owed, as 35a named it.
  - **Centred selector — kept, and that is the fifth decision.** `TdayCenteredSelectorMotion` fades and scales 3 % and travels nowhere: it is already a crossfade. Gating it removes no amplitude and leaves a picker that reads as pasted on rather than resolved into place, so it keeps what it has, with the argument written where the spec lives. `CompletedScreen`'s restoring row (0.985) and the three confirmation overlays (0.96) are the same call. A rule written for a card crossing a screen, applied to three hundredths of a scale, is the letter of the guidance against its point.
  - **The box stays open, and the remainder is named rather than waved at.** What this PR closes is the second half of the row's own claim — the setting is live at runtime on all three clients now, and no client reads it at mount and goes quiet. The first half is coverage, and coverage is not finished: 34b's own bullet puts ~191 Android animation sites outside `effectiveMotionScale` until they are migrated, and on iOS `AppSnackbar`'s drag snap-back reads its own environment. Ticking this here would be the overclaim 34b's guide copy was corrected for, one row further on. It is carried into Phase 9's call-site migration, where the sites it is waiting on are the sites being touched.
  - **`AnyTransition` has no test and cannot have one.** It is opaque and not `Equatable`, so nothing can ask a transition what it does; `TdayMotionEnvironmentTests` pins the `Animation` overload in both directions — the substitute really plays, and full motion never gets it, which is the wrong-way-round ternary that would have retimed four surfaces for everybody — and the transition half is checked with eyes, in `phase-8-device-pass.md`'s 35b row. That row runs every check twice on purpose: the two passes must look different without the second looking broken.

### PR 36 — the Undo toast respects “Time to take action”

- [x] `android-accessible-toast-timeout` — hard 8 s Undo regardless of "Time to take action" · and · Impact O3 · XS · Gate J+D
  - **The row is filed as an opportunity and is a Sev 4 in everything but its label.** The one toast in this app that carries a button is the Undo on a delete, and 8 s is the whole life of that offer. A switch scanner or a head pointer can spend most of it simply arriving at the control, so the window was shortest for the person slowest to reach it, and what they lose by missing it is a task. `getRecommendedTimeoutMillis` is asked instead of `ACCESSIBILITY_INTERACTIVE_UI_TIMEOUT_MS` because the framework does three things the raw setting does not: it returns max(ours, theirs), it answers separately for interactive and non-interactive content, and it folds in a timeout an accessibility service asked for on the user's behalf. Reading the setting directly reproduces two of those and misses the third — and the third is where a value meaning "never" comes from, since Settings' own ladder stops at two minutes.
  - **The commit window is derived, not re-declared, and that is the load-bearing half.** `COMMIT_DELAY_MS = 8_500L` was tuned against a `TOAST_AUTO_DISMISS_WITH_ACTION_MS = 8_000` in a different file with nothing holding the pair together — a coupling `docs/PLAN_UNDO_TOAST_DELETE.md` had already logged as a latent hazard. The moment one of the two started following a setting it became a live one: a 30 s toast over an 8.5 s commit is an Undo button that is still on screen, still tappable, and silently does nothing, which tells the user their delete was reversed when it was not. `undoCommitDelayMillis` measures off the resolved toast window, so the two cannot drift by construction; `docs/design/bulk-selection.md`, which forbade changing the old pair, now states the new invariant instead.
  - **"Never" is refused for the button and honoured for the text, and the asymmetry is the argument.** An informational toast resolves to `null` and stays until dismissed — it is only text, and text can sit there as long as the user wants. An action toast takes `INTERACTIVE_TIMEOUT_CEILING_MS` instead, because the Undo is an offer that holds only while the delete is staged, and a delete that never commits is a row that comes back on the next sync. The cost of the ceiling is named where the risk is weighed rather than left for a reader to discover: `UndoableDeleteCoordinator`'s accepted-edge-cases paragraph now says the staged window can be two minutes, not 8.5 s, which is a real fraction of `SyncManager.OFFLINE_RESYNC_INTERVAL_MS` and makes the resurrection guard stop being a formality.
  - **Longer is worthless if nobody is told the toast arrived, so the semantics block carries two things.** A toast appearing is a newly composed subtree — `TYPE_WINDOW_CONTENT_CHANGED`, which TalkBack does not speak — so before this the extra seconds only helped a user who happened to be exploring the bottom of the screen. `liveRegion = LiveRegionMode.Polite` is what makes the window reach them; Polite because the row they just deleted is still being read out and this is a report of it, not an interruption. Beside it, `dismiss { … }` publishes `ACTION_DISMISS`: the drag was the only early exit and a drag is reachable by exactly one kind of user, which matters most in the state where there is nothing to wait out. `mergeDescendants = true` is what makes both reachable rather than merely present — an un-merged container is not a stop, so focus would land on the message `Text` and the dismiss would sit on a node nobody visits. The Undo keeps its own stop, because `clickable` is itself a merging node and a merge does not reach through one.
  - **The dock asks the same question and is fixed in the same commit.** `RootFeedDock` opens on a tap and closes itself again on a hard `delay(2400)` — the shortest such window in the app, on the same screen as the toast, and a user who told Settings they need thirty seconds was watching the dock shut while still travelling to the tab. It goes through the same `interactiveTimeoutMillis`. Neither window is scaled by `scaledDelay`: this is time the user needs, not time they spend watching something move, so a device at 0x still gets all of it — the one place in Phase 8 where a delay deliberately stays outside the motion clock.
  - **No new motion literals.** The counted categories are `tween`/`spring`/`pressScale` on Android; nothing here declares a duration, a curve or a spring, and the dock's 2 400 ms moved from a bare `delay()` argument into a named base without becoming one. The three budgets are untouched.
  - **What the JVM gate can and cannot see.** `AccessibilityTimeoutTest` pins the arithmetic either side of the platform call — floor, ladder, ceiling, the "never" split, and the commit-outlives-toast invariant at every rung — because that is the half that can silently go back to being a constant and looks like a working app from the outside. The call itself reads a user-level setting through a system service and is Gate D's, along with whether TalkBack's dismiss gesture actually lands and whether the live region is spoken.

### PR 48 — iOS swipe-to-complete becomes reachable

- [x] `ios-swipe-to-complete-unreachable` — `standardModeContent` is dead; the only `.swipeActions` in the app is unreachable · ios · Impact O4 · M · Gate X
  - **Closed by removal, not by revival.** `isTodayMode` and `isMinimalTimelineMode` cover all seven `TodoListMode` cases, so `modeContent`'s fallback, `standardModeContent`, and `todoRow` behind it were deleted along with the `.swipeActions(edge: .leading)` nobody could reach. The live row (`minimalTimelineRow`) completes by tap and carries a hand-rolled pan-gesture trailing swipe, not SwiftUI's `.swipeActions` — so putting a leading Complete back is a new gesture on `TodoTrailingSwipeActionsModifier` and belongs in its own device-gated PR, not in a guardrail PR that cannot compile Swift. `motion-reachability-ios.test.ts` rule C now fails on any view reachable only through an enum-exhausted branch.
  - **The removal left the wrapper behind, and the feature went on existing only in the source.** Deleting `todoRow` took the `.swipeActions` call site with it and left `todoSwipeActions(_:)` — the `extension View` helper that holds the `.swipeActions` — standing in the file with every caller gone, along with the `TodoRowAction` it takes. That is precisely the gap rule C documents rather than covers: `private` is file-scoped, so a one-file reference search is a proof, and `internal` is module-wide, so it is not. But the module is a single source path — `project.yml` gives the app target `Tday` and nothing else — so the same proof is available one target wide, and the new rule takes it. `SwipeRevealHintModifier` came out beside it: its 0.26/0.78 nudge, 0.38/0.68 return and the 340 ms sleep between them are a second copy of what `TodoTrailingSwipeActionsModifier.revealHint()` writes out itself, and it had had no caller since the trailing swipe absorbed the hint. `ios.spring` drops 102 → 98 for the four literals that went with it, and the 340–420 ms non-token band in `docs/motion.md` drops from four cited values to three — the one it loses was that duplicated sleep, so the row's own "no two of them the same motion" is true now and was not before.
  - **The rule covers both halves of a modifier, because the second stranding was in the half nobody scans.** `todoSwipeActions` was found by an `extension View` sweep; `SwipeRevealHintModifier` would not have been, and initially was not — it came out by eye, and the rule as first written stayed green over a planted copy of it. A SwiftUI modifier is declared twice, as the `extension View` func and as the `struct … : ViewModifier` behind it, and either half can outlive its callers alone. Both are collected now and both go through the same one-target reference search, so a `.modifier(X())` with no call site fails exactly as a `.x()` with no call site does.
  - **Revival was reconsidered on the merits and refused for a different reason than the one first given.** The live feed is a `List`, so `.swipeActions` would not be inert there; "hand-rolled, therefore impossible" was the wrong reading and is not what decides this. What decides it is that `TodoTrailingSwipeActionsModifier` puts a `UIPanGestureRecognizer` on the enclosing scroll view which begins on horizontal velocity in *either* direction and recognizes simultaneously with everything, so a row carrying both would hand one drag to two recognizers — a device question, on a branch with no Swift compiler. The argument now lives on the declaration, together with the half PR 37 needs: a pan recognizer is invisible to the accessibility API, so a row whose only Edit / Copy / Delete is this modifier has no assistive affordance until those are published again as accessibility actions.
  - **Seven tests, one per `TodoListMode`, because the false claim was about every mode.** The cases are read out of the enum rather than typed into the test, so an eighth case arrives with its own assertion instead of arriving with seven of eight still covered; each one resolves `modeContent` for that case, walks the private view declarations it reaches, and requires a swipe application among them that no branch is wrapped around. Routing one mode away from the live row fails for exactly that mode and names it, which is the thing an argument in this file could not do.
  - **What xctest and a device still owe.** Every claim here is textual: that the deleted declarations had no callers is a reference search over the one target, and the four negative controls the rules were checked against were textual edits too. Only a build says the target still compiles with them gone, and only a device says the trailing swipe still reveals, still closes on the neighbouring row, and still hands the list its scrolling back.

### PR 37 — VoiceOver actions on iOS rows

- [x] `ios-accessibility-actions` — zero `accessibilityAction` in the target; VoiceOver cannot edit/copy/delete/reschedule · ios · Impact O4 · M · Gate X+D
  - **The actions go where the gesture is, and that is what makes them complete.** Four screens apply `todoTrailingSwipeActions` — the todo feed, the scheduled task home, Completed and the Calendar day list — and putting the accessibility actions at those call sites would have been four copies of the same list, each able to fall behind the pills on its own. They go inside `TodoTrailingSwipeActionsModifier` instead, over the same `content` the pan recognizer is attached to and out of the same four closures, so every row that has the swipe has the rotor by construction and a screen cannot acquire one without the other. `enabled:` already carries the row's answer to whether it can be acted on — a viewer's list, a row mid-completion, a selection sweep — so the block sits behind it rather than inventing a second condition.
  - **Parity of capability meant four actions, not three.** Edit, Copy and Delete are on every row; the fourth is the mode's own — Schedule on a floater, Float on an overdue row, Defer on a dated one — and it is the `reschedule` half of this row's title. It is published from the same `extraAction` the pill reads, so a mode that has no third action publishes three entries rather than a dead one. There is no context menu on a task row anywhere in the iOS target to model anything else on: the swipe was the whole set.
  - **The pills are hidden from the tree in the same commit, and that is not tidying.** They are in the row permanently, at `opacity(0)` and `allowsHitTesting(false)` until a pan reveals them — a state the accessibility API has no notion of. Left exposed they are three-to-four buttons an assistive user can focus and cannot meaningfully use, sitting beside the four real actions, so VoiceOver would offer Edit twice and only one of them would be the one that works. `.accessibilityHidden(true)` on the pill stack makes the row's one accessible path the actions, deterministically, instead of relying on SwiftUI's own treatment of a transparent view.
  - **The names say what the action does, except where the app already has a name for it.** `L("Edit task")`, `L("Copy task")` and `L("Delete task")` — the first two words the pill has room for plus the noun it acts on, and two of the three keys already existed in `Localizable.xcstrings` for the edit sheet's title and the delete confirmation, fully translated. Only `Copy task` is new, added in all nine locales. The mode's third action keeps `extraAction.title` verbatim: Schedule, Float and Defer are what the app teaches that button is called and they are already localised at the call site, so a fuller phrase invented here would give one button two names and split the vocabulary between the people who can see the pill and the people who cannot.
  - **The rule is what keeps this true, because nothing else on this branch can.** `motion-reachability-ios.test.ts` reads the modifier's action inputs — closure-typed parameters, plus a value whose type is an `Action` — and requires every one of them to be named inside an `.accessibilityActions` block in the same declaration, so a fifth pill added later lands as a fifth rotor entry or lands red. A second rule reads the same block out of the RAW source and asks two things of every name in it: that it goes through `L(…)` rather than being English on the screen of a user who chose otherwise, and that the key it names has a value in all nine locales — a missing key falls back silently to the English key, which is reachable and unreadable at once. All three were checked against negative controls: dropping the Copy action, unwrapping one label out of `L(…)`, and blanking two locales each fail with the site named. No motion literals are added or retired; the three budgets are untouched.
  - **What xctest and a device still owe.** There is no Swift compiler on this branch, so the compile itself is owed — `.accessibilityActions`, the `role: .destructive` overload and the `if let extraAction` inside a `@ViewBuilder` are all read rather than built. The load-bearing runtime question is narrower and is written into the device row: the actions are attached to a container holding several accessibility elements, and only a real VoiceOver pass says whether they are offered from every stop in the row or only from one. Whether `.accessibilityHidden(true)` on the pill stack removes stops that were really there, and whether the haptics fire on an action activated from the rotor, are the same kind of question.

## Phase 9 — adoption, polish, confetti

### PR 8g…8n — token call-site migration, one directory per PR

- [ ] `motion-token-layer` — No client has a motion token layer — durations and easings live in ~99/168/10-curve piles of literals, and all three theme files define colour and type but not time · all · Impact O4 · L · Gate J + X + V + G — **final part (6 of 6)**; PR 7/8a, PR 8b, PR 8c, PR 8d, PR 8e carried the rest
  - **8i — `tday-web/src/components/` is empty of `duration-<n>`, and the box stays open on purpose.** Twenty-eight utilities came off; the web ceiling is 19 and every survivor is in `src/features/` or `src/pages/`, which is 8j's directory. The box cannot be ticked here: it is the single checkbox for all eight of 8g…8n by the "rows split across several PRs" convention above, and 8j…8n have not landed. Ticking it at 8i would assert that Android's feeds and iOS's Snappy sites are migrated too.
  - **Twenty-three of the twenty-eight are a renaming and not a retiming, and the ratchet cannot tell them apart.** `duration-200` already IS the `Enter` rung, so spelling it `duration-enter` moves no pixel — but the counter drops by 23 all the same, which is the whole reason the budget note records what came off rather than only the number. What changes is legibility: a reviewer can now see which sites chose a length and which merely typed one.
  - **The dock's pill and its tabs were the one real defect in the set, and the rung alone did not fix it.** The pill ran on 300 and the tabs under it on 200; both are geometry under rule 2 and both are `Emphasis` now. But the thing the pairing times is not the arriving tab, which is what `sm:min-w-[104px]` looks like it should be: that tab's label makes it wider than the 104px floor — 128px for "Scheduled", within about five of the floor for "Floater", measured in Chromium against the built stylesheet — so the floor never binds on the way in and the tab has its width in the first frame at any duration. What runs for 320 ms is the DEPARTING tab collapsing to `sm:min-w-12` once its label is hidden, and the slide it gives every tab to its right. Re-measured in the same harness, matching the rungs closed about a third of the gap and left two thirds: the pill's target was sampled once at `rAF` and again on a hand-written 260 ms timer, so it set off for where the arriving tab stood BEFORE the collapse, 56px past its destination, and turned round when the timer fired. It now follows the tab's rect once a frame for the length of the rung, which turns that into one journey — the pill settles onto its mark from about five pixels past it, the way an ease-out chasing a moving target does, and is within a pixel of rest on the frame the tabs stop on. One duration covers everything the press layer animates on that tab, so its hover tint and press squash come up to Emphasis with the width; that is conceded at the call site and has a device row of its own. `globals.css` and `press-affordance-cascade.test.ts` each describe the old pairing in place and both still say "300 ms" for the pill; neither file is in this unit's scope.
  - **`MobileSearchHeader`'s bar is on a rung that times nothing, and says so.** It was the third site naming no rung, and the first reading of it — `justify-between` → `justify-stretch` redistributes every child, therefore rule 2, therefore `Emphasis` — is wrong: `justify-content` is a discrete property, a parent cannot animate where its children land, and in Chromium the bar's second child moves 778px on the first frame under a 320 ms `transition-all`. The 300 it replaced timed exactly as much. It is `Enter` now by the ladder's default clause rather than by a geometry argument it cannot support, and the comment says which — the declaration stays for the bar's own background, and animating the redistribution would mean a transition on each child, which is a larger change than a one-frame reflow is asking for.
  - **`BANNER_EXIT_MS` moved with its own class, for `MODAL_EXIT_MS`'s reason.** The install banner's exit is read twice — by `data-[state=closed]:duration-quick` and by `useFadeUnmount` — and a rung named on one side only half-plays it. `Quick` rather than `Enter` because an offer being declined is exactly the rung's case: something leaving that nobody is meant to watch go.
  - **8j — `tday-web/src` is empty of `duration-<n>`, and the web ceiling is 0.** Nineteen utilities came off `src/features/` and `src/pages/`, the two directories 8i left, and the counter is now a floor as well as a ceiling: there is no `duration-<n>` anywhere in the web tree outside comment prose, so the next one written is new by construction and turns the ratchet red without anyone having to work out what the number used to be. The box still cannot be ticked — it is the one checkbox for all of 8g…8n, and 8k…8n have not landed — but the web half of the row is finished here.
  - **The brief's arithmetic was stale, and the direction it was stale in matters.** It asked for 30 → 11 on the strength of eleven survivors in `src/components/ui/` — `sheet.tsx`, `dialog.tsx`, `Modal.tsx` and `sheet-chrome/CenteredSelectorOverlay.tsx` — assigned to PR 41a. 41a has since landed and took all eleven, and 8i then took the ceiling to 19. Counted rather than transcribed, the floor for this group is 0, and writing 11 would have left a ceiling with eleven slots of headroom in it that nothing is ever going to fill: a ratchet with slack is not a ratchet.
  - **Eighteen of the nineteen are a renaming; the nineteenth is the defect.** Fourteen `duration-200` became `duration-enter` byte-identically across the calendar, the two native dashboards, the Completed tab strip, the list header's edit button and the summary button. `BlogsPage`'s four 300s named no rung and are `Change`: nothing on that page changes position or size, which rules `Emphasis` out under rule 2, and `Change` rather than `Enter` because a hover reveal is the reader's own pointer played back where it already is and is meant to be watched finishing. The comment there is honest about which of the four actually toggle on hover — the excerpt's opacity and the footer link's colour — and which currently cover only a theme swap, which is the same in-place case and therefore the same rung.
  - **The calendar's view slider was running a control and the view it picks on two different clocks.** The thumb travels a full segment on every view change, so rule 2 puts it on `Emphasis` on geometry alone — but the reason it is worth a device row is that the tap does not only move the thumb: `changeView` sets `slideDirection` and bumps `animKey` in the same call, so the grid below plays `cal-native-slide-from-*`, and `calendar-styles.css` has run both of those keyframes on `--tday-duration-emphasis` since Phase 7. The 300 meant the switcher stopped 20 ms before the view it switched, in one gesture, every time — one tap arriving as two events, which is the same defect Phase 7 fixed between the slide and the height box and the same sentence its comment already carries. The height box was already on the rung; the thumb is the third half that was never brought over. `calendar-pager-height.test.tsx` now asserts the thumb's rung next to the slide's, and `CalendarViewSlider` is exported for that: a counter at zero forbids a literal and is satisfied by any rung name, so a rename back to `duration-enter` would re-open the 20ms gap with every guardrail green.
  - **Two thumbs that travel were deliberately left on `Enter`, and the split is a rule rather than a mood.** `CompletedContainer`'s tab thumb and `SettingsPage`'s two switchers are one segmented-control shape, all three already sitting exactly on 200. Rule 2 would move them, and this unit does not, because a site that already sits on a rung is renamed and a site that named no rung is adjudicated — that is 8i's line and holding it is what keeps a migration from becoming an unreviewed retiming. Retiming one of the three would give the app two segmented controls answering a tap at different lengths; retiming all three is a visible change to a control shape and needs its own argument and its own device row. The calendar's thumb is promoted not because it is a thumb but because it named no rung AND has a grid moving beside it. Conceded at the call site in `CompletedContainer.tsx`.

### PR 9a/9b — `Modifier.tdayPressable` and the 17 hand-rolled triplets

- [ ] `press-affordance-unification` — One press affordance: Android hand-rolls the scale/offset/elevation triplet 17 times at 7 different scales, and on web any `transition-*` utility silently deletes the global press squash · and+web · Impact O3 · M · Gate TF + D — **final part (3 of 3)**; PR 10, PR 54 carried the rest

### PR 31 — the Android route hand-over and predictive back

- [ ] `route-change-handover` — Route change: Android crossfades at 360/240ms, web at 140ms, and web's own comment says the long one reads as lag — pick the number once · and+web · Impact O3 · M · Gate V + D — **final part (2 of 2)**; PR 55 carried the rest
- [ ] `android-predictive-back-scrub` — edge drag scrubs a pure crossfade — communicates nothing · and · Impact O3 · S · Gate D

### PR 32 — iOS cold launch and zoom navigation

- [ ] `ios-cold-launch-fade` — splash → first screen is a hard cut, every launch · ios · Impact O4 · S · Gate TF
- [ ] `ios-zoom-navigation-transition` — six home tiles push with the stock slide; zero shared elements in the target · ios · Impact O4 · M · Gate X+TF

### PR 38 — the confetti spec, into the repo

- ↳ part 1 of 5 of `confetti-kinematics` — **extract `docs/confetti-spec.md` into the repo — highest session-death insurance**. Box lives under **PR 39d**.

### PR 39a — web confetti kinematics

- ↳ part 2 of 5 of `confetti-kinematics` — web `confetti-kinematics.ts` + I1–I6 vitest. Box lives under **PR 39d**.

### PR 39b — Android confetti kinematics

- ↳ part 3 of 5 of `confetti-kinematics` — Android `TdayConfettiKinematics.kt` + JVM I1–I6 + `MinSpin`/`MaxSpin` → `MinSpinRadians`/`SpinRadiansRange`, which is the pair `docs/confetti-spec.md` names and both halves of the rename, not just the misleading one. Box lives under **PR 39d**.

### PR 39c — iOS confetti kinematics

- ↳ part 4 of 5 of `confetti-kinematics` — iOS `TdayConfettiKinematics` + xctest + **pbxproj registration**. Box lives under **PR 39d**.

### PR 39d — confetti palette and fan-bounds parity

- [ ] `confetti-kinematics` — Land the recovered confetti physics — linear drag, flip split off rotation, smoothstep fade — on all three clients at once · all · Impact O3 · L · Gate self + V + J + X + G + D + TF — **final part (5 of 5)**; PR 38, PR 39a, PR 39b, PR 39c carried the rest

### PR 40a — the web task-row skeleton, its crossfade, and the route shell

- ↳ part 1 of 5 of `skeleton-loading-vocabulary` — web `TaskRowSkeleton` at `TodoItemContainer`'s own row classes; the `ui/TodoListLoading` re-export shim retired. Box lives under **PR 40b**.
- ↳ part 2 of 5 of `skeleton-loading-vocabulary` — web `.tday-skeleton-exit`/`.tday-content-enter` on `Enter` + `useSkeletonCrossfade`; the four hand-rolled `animate-pulse` spellings retired and the scheduled root feed given the placeholder its sibling always had. `TodoListLoading`'s four callers (`AllTasksTimelineContainer`, `ListContainer`, `CompletedTodoContainer`, `CompletedFloaterContainer`) stop gating its mount and hand the flag down, so its crossfade is not inert — those four get the fade-out half only, having no single content block to carry `.tday-content-enter`. Box lives under **PR 40b**.
- ↳ part 3 of 5 of `skeleton-loading-vocabulary` — web `AppShellSkeleton`, the Suspense fallback every lazy route and the home redirect show, drops its three `h-[62px] rounded-2xl` cards for `TaskRowSkeletonGroup` and routes its remaining blocks through the `Skeleton` primitive; the dock placeholder is left alone for **PR 176**, which removes it. No crossfade: React swaps a Suspense fallback without re-rendering the fallback, so `useSkeletonCrossfade` has no frame to hold. The user card this row was scoped with had already landed in part 2. Box lives under **PR 40b**.

### PR 40b/40c — the Android and iOS skeletons

- ↳ part 4 of 5 of `skeleton-loading-vocabulary` — iOS `TdayTaskRowSkeleton` + xctest + **pbxproj registration**, built at the two sets of row metrics the feeds actually draw (`TdayTaskRowMetrics` for Today, `TodoTimelineMetrics` for a task list and Completed) rather than at one pleasing set of grey blocks; the three feeds that drew nothing at all while loading — `ScheduledTaskHomeScreen`, `TodoListScreen`, `CompletedScreen` — now stand rows in the slot and crossfade to content, and sit flat and full-alpha under Reduce Motion. Box lives under **PR 40b**.
- ↳ part 5 of 5 of `skeleton-loading-vocabulary` — Android `TdayTaskRowSkeleton` + `TdayTaskRowMetrics` at `TodayTodoRow`'s own geometry; the timeline's `surfaceVariant` card and Completed's `displaySmall` word both retire; JVM test. Renumbered from `part 2 of 3` on the merge with `develop`: the web unit split into three parts of its own and iOS took a fourth, so this is the fifth and last. Box lives under **PR 40b**, directly below, and this is the part that ticks it.
  - **The row text said 190 and 190 is the wrong number.** It is `TdayFeedItemMotion.FadeInMillis`, a hand-written Android literal that `docs/motion.md` settled against the ladder in favour of 200, and that both other clients' feed-motion mirrors name in writing as the value which owes the move. A new site wired to it would have made that literal harder to retire while looking correct. The hand-over is `TdayMotionTokens.Durations.Enter`, on `Standard` — the curve, because a hand-over runs both halves off one clock and neither a decelerate nor an accelerate describes that.
  - **The hand-over is a paired fade and shrink, not a `Crossfade`.** Two lazy items are stacked, never layered, so the skeleton and the feed it gives way to can no more cross-fade over each other than two paragraphs can. A `Crossfade`'s `Box` holds the largest composed child for the whole transition, and on the timeline the skeleton sits ABOVE the rows: the feed would land, sit pushed down by three rows' worth of placeholder for 200 ms, and then jump. That is the pop this unit removes, moved later and made larger. `AnimatedVisibility` with `fadeOut` and `shrinkVertically` on one spec is what the Earlier scene in the same `LazyColumn` already does, and it is the answer here too. Both items are hidden by `visible` rather than removed by their guard — an item its guard has already taken out of the list has no exit left to play — and both are mounted for a WINDOW rather than unconditionally: `Arrangement.spacedBy` is charged per item and not per drawn pixel, so a placeholder left mounted for the sake of its exit goes on costing the flat feed `TimelineDateGroupSpacing` under its header for the whole of the loaded state. `rememberTdayTaskRowSkeletonMounted` closes the window one hand-over after `visible` drops, on `scaledDelay` so the wait leaves with the motion it covers.
  - **The rung owes an argument, and the argument is already in `docs/motion.md`.** A paired fade and shrink is geometry, and the second idiom rule reads geometry as `Emphasis` — but the doc writes the exception where it discusses the tree's one other slot hand-off: an exit that hands a slot to an arrival answers to that arrival's length, not to the length of the thing it undoes. Nothing here takes a new slot, and 320 would make the placeholder outlast the feed it is uncovering. Web's `TODAY_EARLIER_EXIT_MS = DURATION_MS.enter` is the same call reached by the same sentence, and it rules out 320 in as many words. `Enter` stands; what was missing was this paragraph.
  - **Compose does not freeze an infinite transition where it stopped it.** At animator duration scale 0 `InfiniteTransition` calls `skipToEnd()`, which assigns the `TargetBasedAnimation`'s TARGET — so a pulse running `RestingAlpha` down to `DimmedAlpha` sat pinned at 45 % for the whole load on exactly the devices that asked for less motion, which is the fifth idiom rule broken twice over. Turning the tween round only moves the bug: the INITIAL value is what a host that never delivers a frame holds, so one orientation is legible when frames stop and the other when the scale is 0. The ends stay bright-to-faint and `TdayTaskRowSkeleton.frozenAlpha` answers the scale-0 case by drawing a constant with no transition composed at all — the system scale and not the app's, because an `InfiniteTransition` obeys the recomposer's `MotionDurationScale` and the in-app switch cannot reach it.
  - **`label_loading` stays, with one reader instead of two.** Bars say nothing to TalkBack, so replacing the two words with shapes would have removed the only thing on either screen a screen reader could report. The group carries the string as one merged node with a polite live region, which is the announcement the `Text` used to make by being text. `EmptyCompletedState` went the other way and was deleted: the loading call was its only caller, and the real empty state next to it is `TdayEmptyState`.
- [x] `skeleton-loading-vocabulary` — one skeleton per client at real row geometry, handed over on the `Enter` rung · all · Impact O4 · M ea · Gate V/J/X — **final part (5 of 5)**; web (PR 40a, parts 1–3), iOS (PR 40c) and now Android (PR 40b, part 5) have all landed, so every part of this row is in the tree and the box ticks on the merge that brought the last of them together. The box was written to live under PR 40c as the last of the five; iOS landed ahead of Android, so it moved to PR 40b rather than ticking early. The row read `crossfading` until this merge and now reads `handed over`: web and iOS dissolve two layered views, and Android cannot — its placeholder and its feed are two stacked items in one `LazyColumn` — so it pairs a fade with a shrink on the same `Enter` spec instead, which is the argument under part 5. The three device rows this owes are all still open in `docs/verification/phase-9-device-pass.md`.

### PR 40d — the web infinite-scroll sentinel

- [x] `web-infinite-scroll-sentinel` — hardcoded English in a ten-locale app, no `aria-live` on the pager strip · web · Impact O2 · S · Gate V — the strip reads the locale file in all ten languages, and the reveal is spoken: a `role="status"` region mounts empty and gains "Showing n of total tasks" on the frame each page lands, because a region whose text does not change announces nothing (PR 40d)
- [ ] `web-infinite-scroll-skeleton` — the pager strip is 48 px of empty chrome above ten rows that arrive in one frame · web · Impact O2 · S · Gate V — split out of `web-infinite-scroll-sentinel` by PR 40d, which fixed the other two defects in that row: this one is the `TaskRowSkeletonGroup` swap. That component has since landed with PR 40a, so the blocker the row was written with is gone — what is left is the strip itself, which still renders 48 px of empty chrome instead of the group, and this box ticks when it does.

### PR 41a — the web sheet language

- ↳ part 1 of 3 of `sheet-presentation-unification` — one `--sheet-scrim` token (0.40 light / 0.68 dark, byte-for-byte what `TdayTheme.swift` and `TdaySheetChrome.kt` already draw) behind `drawer`, `dialog`, `sheet`, `Modal` and `CenteredSelectorOverlay`; the sheet's 500-in/300-out becomes Emphasis/Enter, `Modal` and the centred selector spell Enter-in/Quick-out, and the dialog spells Enter both ways — its single bare `duration-enter` covers both directions, as the bare `duration-200` it replaced did — `MODAL_EXIT_MS` moving to `DURATION_MS.quick` alongside its own class so the exit cannot half-play; `slide-in-from-bottom-[48%]` and `slide-*-bottom-8` dropped, because a centred card arrives where it already is. Eleven `duration-<n>` literals retired and the `web.durationUtility` ceiling lowered 58→47. Two corrections to this row as it was written: the scrim was **5 sites carrying 4 alphas** (0.80 twice, 0.65, 0.50, 0.45), not 7 spellings, and the 48% slide was in `dialog.tsx`, not `sheet.tsx`. PR 50's nested-scrim branch survives untouched — only the dimming half of it moved. Box lives under **PR 41c**.

### PR 41b — the Android sheet language

- ↳ part 2 of 3 of `sheet-presentation-unification` — `TdaySheetMotion` from iOS's 4 specs (card exit 320 → `Change`, both directions off `Standard`); the scrim fades with its card instead of with the `Dialog` window; both create-sheet constants retired; `TdayModalBottomSheet` named as the other mechanism. Also carries PR 15a's deferred confirm cut, per the note under **PR 15a**. Box lives under **PR 41c**.
  - **PR 15a's deferred confirm cut is discharged.** `submitTask()` OPENS on
    `sheetDismiss.start()` and returns if the exit was already claimed. The eight confirm lambdas
    stop clearing the flag that composes the `Dialog`: teardown belongs to `onDismiss` alone, which
    is the end of the exit, so the commonest way out of this sheet now leaves the same way the X
    does. The create-LIST sheet's `onCreate` goes the same way, and its draft reset moves to the
    host's `onDismiss` behind a `listCreated` flag, because blanking the name on the frame of the tap
    is now something the user watches happen: the field empties and Create greys out under their
    finger while the card is still sliding. A dismissal that created nothing keeps its draft, as
    before.
  - **The claim comes first because the payload must be refusable a second time.** Putting the
    confirm inside the slide is what makes the card readable on the way out, and it leaves Create
    lit and hit-testable for the whole 260 ms — deliberately, since greying a control the user is
    still looking at would be them watching it go dead under their finger. So `start()` answers now:
    true to the tap that claimed the exit, false to every one after it, and the confirm drops the
    tap it cannot claim. Without that a second tap mid-slide hands the caller a second payload, and
    `createTodo` mints a fresh local id per call — a duplicate task, queued as its own create and
    synced to every device. The old shape was immune to this only by accident, because the host tore
    the composition down on the frame of the tap. The same answer closes the older half of the same
    window, from PR 15a: after the X is tapped, Create used to stay live for the length of the exit
    and could still create the task the user had just cancelled.
  - **Two things had to move that the deferral did not name, and both are the same defect one level
    up.** The edit hosts resolve their target by looking an id up in the feed they are showing and
    compose the sheet inside `target?.let { … }`; that was safe only while the confirm tore the sheet
    down on the frame of the tap. Saving an edit that moves the task out of the feed underneath it —
    a due date pushed off today, a list changed — drops the row while the card is still sliding and
    the `let` stops composing, so the host cuts the sheet in place of the callback. `rememberEditSheetTarget`
    retains the row the sheet was opened on until the id itself goes null, which is the host's own
    teardown. And the widget create surface is an Activity, not a flag: its submit used to `finish()`
    in a `finally`, which would now cut the slide, while exiting on the slide alone would have
    cancelled a composition-scoped write and lost the task. So the write stops being
    composition-scoped — `WidgetCreateTaskSubmitter.submitDetached` runs it on the singleton's own
    process-lifetime scope, the way `WidgetRefresher` already runs its renders — and the card is the
    only clock that ends the activity. Holding the window for the write instead was the first shape
    tried, and it is worse than it sounds: `createTodo` does its local write and its widget repaint
    first and then AWAITS a forced sync, so the user got the slide and then a fully transparent,
    touch-swallowing window standing over their own home screen for as long as a connection probe
    takes, with the new task already painted into the widget behind it. Nothing is lost by letting
    the window go: the local write and its replayable `CREATE_TODO` mutation are committed within a
    frame or two of the tap, and a sync cut short is the case pending-mutation replay exists for.
  - **`dismissEnabled` is gone rather than bypassed.** It existed so the widget could refuse a
    dismissal while a submit was in flight, because its own `finish()` could drop one that the sheet
    had already latched. That host leaves with the card now, so there is nothing left to refuse — and
    the veto could never have covered a confirm anyway: `submitTask` reaches `start()` inside the
    same click that sets the host's flag, a frame before the refusal composes. A first confirm is
    not a gesture a host is entitled to refuse; the user has committed. A repeat confirm of a sheet
    that is already leaving is a different gesture, and the sheet refuses that one itself.
  - No literal is added or retired, so all eight budget counters are untouched. `RULE_B_PENDING_FIX`
    stays empty. The unit test is on `SheetDismissState` itself and pins two things the endpoints do
    not show: the gap the defect lives in — `dismissing` true while `gone` is still false — and the
    latch, by asserting on what `start()` ANSWERS, since a latch that did nothing would leave the
    same `dismissing` and the same `targetState` behind as one that works. It cannot see the wiring
    it exists for: there is no Compose test runtime on this module's JVM classpath, so that the
    confirm opens on `start()` and drops the tap it is refused, and that no host still clears its own
    flag, are read — and carry a device row.

### PR 41c — the iOS sheet language and drag-to-dismiss

- [ ] `sheet-presentation-unification` — Four overlay systems on web, two on iOS, two on Android — one sheet language, one scrim, one set of timings · all · Impact O3 · L · Gate V + J + D + TF — **final part (3 of 3)**; PR 41a, PR 41b carried the rest
- [ ] `ios-sheet-drag-to-dismiss` — the app's most-used sheet cannot be swiped down; no grabber · ios · Impact O4 · M · Gate TF

### PR 42a…42g — seven polish PRs, one surface each

- *(container row — the seven rows below are the work: Morning Sweep · Android Settings inline forms · iOS Help Guide · Android onboarding steps · Android calendar mode switch · iOS numeric counts · Android error card)*
- [ ] `android-morning-sweep-motion` — 241 lines, zero `animate*`, bare-`Text` finish · and · Impact O3 · S · Gate D
- [ ] `disclosure-expand-collapse` — Expand/collapse has no shared spec: Android slices glyphs mid-growth, iOS runs the app's fastest curve at 150ms, web snaps the height and swaps two chevron glyphs · all · Impact O3 · M · Gate V + D + TF — **final part (2 of 2)**; PR 56 carried the rest
- [ ] `android-onboarding-step-direction` — ordered steps crossfade on Compose's default spec · and · Impact O2 · S · Gate D
- [ ] `android-calendar-mode-content-cut` — content hard-cuts inside a container whose height is springing · and · Impact O3 · S · Gate D
- [ ] `ios-numeric-count-transition` — four plain `Text("\(count)")`, one at 34 pt; zero `contentTransition` in the codebase · ios · Impact O3 · S · Gate TF
- [ ] `android-error-card-feed-motion` — error card pops mid-feed, two directories from the spec that fixes it · and · Impact O2 · XS · Gate D

### PR 43a — the web radius scale becomes monotonic

- ↳ part 1 of 3 of `dimension-radius-token-adoption` — **correctness**: `rounded-xl` (12 px) renders smaller than `rounded-md` (14 px). Box lives under **PR 43c…43n**.

### PR 43b — the missing `TdayDimens` steps and the lint that holds them

- ↳ part 2 of 3 of `dimension-radius-token-adoption` — 3 missing `TdayDimens` steps + lint forbidding new raw `.dp` under `feature/`. Box lives under **PR 43c…43n**.

### PR 43c…43n — one screen file per session, behind the lint

- [ ] `dimension-radius-token-adoption` — The dimension scales are unused on Android (5 TdayDimens refs against 200 raw dp in one file) and non-monotonic on web (rounded-xl renders smaller corners than rounded-md) · and+web · Impact O3 · L · Gate V + J — **final part (3 of 3)**; PR 43a, PR 43b carried the rest

### PR 57a/57b — the web dock collapses on scroll

- [ ] `web-dock-scroll-collapse` — web dock never collapses; both native clients do past 44 dp · web · Impact O3 · L · Gate V+D

### PR 59 — compositor hints for the always-on blurs

- [ ] `web-compositor-hints-blur` — zero `will-change` in `src/`; 17 always-on backdrop blurs over a scrolling list · web · Impact O2 · S · Gate D

### PR 60 — one celebration ordering

- [ ] `celebration-ordering-decision` — **[CORRECTION]** see §4.5 — Android has *both* orderings; web is missing a leg, not on the opposite model · and+web · Impact O3 · S · Gate D
---

## Long-tail pass — 95 rows recovered from the killed audit

These come from the 2026-09-12 audit's Map phase, which produced findings its Verify phase never
reached. 163 of them were re-derived in source in a later pass: **67 turned out to be already tracked
above under a different id**, 1 could not be settled without a device, and **95 survived as genuinely
new**. None is severity 4 — the severity-4 tier had already been verified and is carried in the rows
above. 79 of the 163 were graded lower than originally filed, and 12 carried citations pointing at
lines that do not exist, so treat the original severities in any recovered document as inflated.

Rows are grouped by root cause, so one heading is one PR. Numbering continues from the existing plan.


### PR 120 — android dock spring chases spring

- [ ] `android:home-dock#6` — 6 · and · Sev 3 · M · Gate G

### PR 121 — completed restore phase never reset

- [ ] `ios:completed-settings-guide#completed-restore-stuck-invisible` — completed restore stuck invisible · ios · Sev 3 · M · Gate X

### PR 122 — create sheet keyboard layout

- [ ] `ios:sheets-swipe-pull#10` — 10 · ios · Sev 3 · M · Gate D

### PR 123 — restore phase never reset on failure

- [ ] `android:completed-toast-swipe#restore-stuck-on-failure` — restore stuck on failure · and · Sev 3 · S · Gate J

### PR 124 — search capsule morph

- [ ] `web:floater-dashboards#6` — 6 · web · Sev 3 · M · Gate V

### PR 125 — segmented selection multi speed

- [ ] `android:sheets#segmented-thumb-lag` — segmented thumb lag · and · Sev 3 · XS · Gate G

### PR 126 — selector overlay orphaned transition

- [ ] `ios:todo-list#1` — 1 · ios · Sev 3 · XS · Gate G

### PR 127 — sheet dismiss teardown not deferred

- [ ] `android:sheets#members-close-cut` — members close cut · and · Sev 3 · S · Gate G
  - citation corrected: /home/ohmz/StudioProjects/Tday/android-compose/app/src/main/java/com/ohmz/tday/compose/feature/todos/ManageMembersSheet.kt:224 (the finding's `file` drops `app/src/main/java/com/ohmz/tday/compose/`; line 220 opens the TdaySheetHeader block, the defect is on 224)

### PR 128 — android root search overlay handover

- [ ] `android:home-dock#7` — 7 · and · Sev 2 · M · Gate G

### PR 129 — auth panel swap outruns card

- [ ] `android:nav-settings#8` — 8 · and · Sev 2 · XS · Gate D

### PR 130 — auth validation message unanimated

- [ ] `ios:completed-settings-guide#auth-error-message-jump` — auth error message jump · ios · Sev 2 · S · Gate G

### PR 131 — calendar cell and chrome state animation

- [ ] `android:calendar#today-pill-label-pops-two-width-animators` — today pill label pops two width animators · and · Sev 2 · S · Gate D
- [ ] `android:calendar#week-cell-selection-snaps-month-cell-animates` — week cell selection snaps month cell animates · and · Sev 2 · S · Gate G
- [ ] `android:calendar#chevrons-blink-dim-on-every-page` — chevrons blink dim on every page · and · Sev 1 · XS · Gate D

### PR 132 — calendar day list feed motion

- [ ] `android:calendar#empty-scene-and-heading-swap-without-motion` — empty scene and heading swap without motion · and · Sev 2 · S · Gate G
- [ ] `android:calendar#rows-no-placement-and-off-clock-fades` — rows no placement and off clock fades · and · Sev 2 · XS · Gate G

### PR 133 — calendar empty branch swap cut

- [ ] `ios:calendar#tasks-to-empty-swap-snaps` — tasks to empty swap snaps · ios · Sev 2 · S · Gate G

### PR 134 — calendar today jump dropped mid page

- [ ] `ios:calendar#today-jump-dropped-while-paging` — today jump dropped while paging · ios · Sev 2 · S · Gate G

### PR 135 — car surface motion gaps

- [ ] `android:completed-toast-swipe#car-list-no-item-animation` — car list no item animation · and · Sev 2 · XS · Gate G
- [ ] `android:completed-toast-swipe#car-loading-content-cut` — car loading content cut · and · Sev 2 · S · Gate G
- [ ] `android:completed-toast-swipe#car-header-cuts` — car header cuts · and · Sev 1 · XS · Gate D

### PR 136 — celebration ordering decision

- [ ] `ios:todo-list#9` — 9 · ios · Sev 2 · S · Gate G

### PR 137 — completed row transition off transaction

- [ ] `ios:completed-settings-guide#completed-collapse-two-clocks` — completed collapse two clocks · ios · Sev 2 · XS · Gate G

### PR 138 — completion choreography

- [ ] `ios:todo-list#4` — 4 · ios · Sev 2 · S · Gate D

### PR 139 — completion row fade vocabulary

- [ ] `web:rows-completion-css#row-fade-no-lift` — row fade no lift · web · Sev 2 · S · Gate V

### PR 140 — dashboard list row placement

- [ ] `web:floater-dashboards#13` — 13 · web · Sev 2 · S · Gate V

### PR 141 — disclosure expand collapse

- [ ] `android:nav-settings#5` — 5 · and · Sev 2 · XS · Gate D
- [ ] `ios:todo-list#12` — 12 · ios · Sev 1 · XS · Gate G

### PR 142 — drag lift and drop

- [ ] `ios:todo-list#3` — 3 · ios · Sev 2 · S · Gate G

### PR 143 — drop placeholder off clock

- [ ] `android:todo-list#drop-placeholder-own-clock` — drop placeholder own clock · and · Sev 2 · XS · Gate J

### PR 144 — earlier celebrate window exit

- [ ] `android:todo-list#expanded-celebration-exit-rows-lag-header` — expanded celebration exit rows lag header · and · Sev 2 · S · Gate D

### PR 145 — earlier collapse has no handoff

- [ ] `android:todo-list#collapse-earlier-rows-fade-under-expanding-scene` — collapse earlier rows fade under expanding scene · and · Sev 2 · S · Gate J

### PR 146 — earlier handoff exit duration

- [ ] `android:todo-list#earlier-handoff-exit-150ms-vs-siblings` — earlier handoff exit 150ms vs siblings · and · Sev 2 · S · Gate J

### PR 147 — earlier scene celebration hold

- [ ] `android:todo-list#inline-celebration-hold-is-a-dead-gap` — inline celebration hold is a dead gap · and · Sev 2 · XS · Gate J

### PR 148 — feed section header add remove fade

- [ ] `android:todo-list#section-headers-cut-on-add-remove` — section headers cut on add remove · and · Sev 2 · S · Gate J

### PR 149 — feed section header fade parity

- [ ] `android:completed-toast-swipe#completed-header-pops` — completed header pops · and · Sev 2 · S · Gate G

### PR 150 — gate overlay arrival

- [ ] `android:nav-settings#2` — 2 · and · Sev 2 · S · Gate D

### PR 151 — guide search filter unanimated

- [ ] `ios:completed-settings-guide#guide-search-results-cut` — guide search results cut · ios · Sev 2 · S · Gate G

### PR 152 — in sheet confirm height swap

- [ ] `web:floater-dashboards#9` — 9 · web · Sev 2 · S · Gate V

### PR 153 — interruptible transitions not keyframes

- [ ] `web:rows-completion-css#enter-and-exit-race-on-nested-wrappers` — enter and exit race on nested wrappers · web · Sev 2 · M · Gate V
- [ ] `web:rows-completion-css#rows-fade-keyframes-pop-on-rapid-toggle` — rows fade keyframes pop on rapid toggle · web · Sev 2 · S · Gate G

### PR 154 — ios calendar cell state unanimated

- [ ] `ios:calendar#day-cell-highlight-snaps` — day cell highlight snaps · ios · Sev 2 · S · Gate G

### PR 155 — ios calendar completion phase reset ghost

- [ ] `ios:calendar#completion-phase-reset-ghosts-row` — completion phase reset ghosts row · ios · Sev 2 · XS · Gate G

### PR 156 — ios calendar mode layer identity churn

- [ ] `ios:calendar#mode-switch-outgoing-layer-is-a-fresh-pager` — mode switch outgoing layer is a fresh pager · ios · Sev 2 · M · Gate G

### PR 157 — ios header snap double motion

- [ ] `ios:sheets-swipe-pull#8` — 8 · ios · Sev 2 · M · Gate D

### PR 158 — ios highlight reclears collapsed sections

- [ ] `ios:todo-list#11` — 11 · ios · Sev 2 · XS · Gate G

### PR 159 — ios sheet state change unanimated

- [ ] `ios:sheets-swipe-pull#2` — 2 · ios · Sev 2 · XS · Gate G
- [ ] `ios:sheets-swipe-pull#3` — 3 · ios · Sev 2 · XS · Gate G
- [ ] `ios:sheets-swipe-pull#12` — 12 · ios · Sev 1 · XS · Gate G

### PR 160 — ios timeline untransacted writes

- [ ] `ios:todo-list#6` — 6 · ios · Sev 2 · S · Gate G
- [ ] `ios:todo-list#7` — 7 · ios · Sev 2 · XS · Gate G
- [ ] `ios:todo-list#10` — 10 · ios · Sev 1 · XS · Gate G

### PR 161 — press affordance unification

- [ ] `android:home-dock#4` — 4 · and · Sev 2 · S · Gate G
  - citation corrected: android-compose/app/src/main/java/com/ohmz/tday/compose/ui/component/RootFeedDock.kt:127-140
- [ ] `android:home-dock#5` — 5 · and · Sev 1 · S · Gate G

### PR 162 — pull refresh pill min visible

- [ ] `ios:sheets-swipe-pull#9` — 9 · ios · Sev 2 · S · Gate G

### PR 163 — route change handover

- [x] `web:rows-completion-css#root-view-transition-vs-route-fade` — root view transition vs route fade · web · Sev 2 · S · Gate G
- [ ] `android:nav-settings#10` — 10 · and · Sev 1 · XS · Gate G

### PR 164 — search bar morph snaps

- [ ] `android:nav-settings#3` — 3 · and · Sev 2 · M · Gate D

### PR 165 — search results overlay pop

- [ ] `ios:home-dock#3` — 3 · ios · Sev 2 · M · Gate G

### PR 166 — search swap items no feed motion

- [ ] `android:todo-list#search-swap-items-have-no-enter-exit` — search swap items have no enter exit · and · Sev 2 · S · Gate J
  - citation corrected: android-compose/app/src/main/java/com/ohmz/tday/compose/feature/todos/TodoListScreen.kt:1786 (the `if (scopedSearchHasNoResults)`; the cited 1787 is the `item(` on the next line). The second site, 1747, is exact.

### PR 167 — settings search filter unanimated

- [ ] `ios:completed-settings-guide#settings-search-filter-cuts` — settings search filter cuts · ios · Sev 2 · S · Gate G

### PR 168 — settings toggle reveal unanimated

- [ ] `ios:completed-settings-guide#settings-toggle-reveals-jump` — settings toggle reveals jump · ios · Sev 2 · S · Gate G

### PR 169 — sheet content swap unanimated

- [ ] `android:sheets#members-content-jump` — members content jump · and · Sev 2 · S · Gate D
  - citation corrected: /home/ohmz/StudioProjects/Tday/android-compose/app/src/main/java/com/ohmz/tday/compose/feature/todos/ManageMembersSheet.kt:235 (same malformed path as the sibling finding; the line itself is correct)

### PR 170 — sheet lifecycle sequencing

- [ ] `web:floater-dashboards#7` — 7 · web · Sev 2 · S · Gate V
- [ ] `web:floater-dashboards#8` — 8 · web · Sev 2 · S · Gate V

### PR 171 — sheet nested resize curve mismatch

- [ ] `android:sheets#create-schedule-row-race` — create schedule row race · and · Sev 2 · S · Gate D

### PR 172 — swipe reveal offset model

- [ ] `ios:sheets-swipe-pull#6` — 6 · ios · Sev 2 · M · Gate D
- [ ] `ios:sheets-swipe-pull#7` — 7 · ios · Sev 2 · S · Gate G

### PR 173 — theme mode not applied outside nav tree

- [ ] `android:nav-settings#12` — 12 · and · Sev 2 · S · Gate D

### PR 174 — toast host motion

- [ ] `android:completed-toast-swipe#toast-drag-two-stage-exit` — toast drag two stage exit · and · Sev 2 · S · Gate D
- [ ] `android:completed-toast-swipe#toast-enter-exit-asymmetric` — toast enter exit asymmetric · and · Sev 2 · S · Gate G
- [ ] `android:completed-toast-swipe#toast-replace-cut` — toast replace cut · and · Sev 2 · S · Gate G

### PR 175 — toggle dependent state snaps

- [ ] `android:nav-settings#7` — 7 · and · Sev 2 · XS · Gate D

### PR 176 — web app index skeleton double chrome

- [ ] `web:shell-sidebar-settings#4` — 4 · web · Sev 2 · S · Gate V
  - citation corrected: tday-web/src/components/app/AppShellSkeleton.tsx:36-39 (dock placeholder); tday-web/src/pages/AppHomeRedirectPage.tsx:24-26; tday-web/src/components/app/NativeAppShell.tsx:37-40; tday-web/src/components/app/RootDock.tsx:105

### PR 177 — web dock pill measure and first paint

- [ ] `web:shell-sidebar-settings#2` — 2 · web · Sev 2 · S · Gate G
  - citation corrected: tday-web/src/components/app/RootDock.tsx:81-92 (the measure effect), :122 (pill class), :159-163 (button transition + min-width)
- [ ] `web:shell-sidebar-settings#3` — 3 · web · Sev 2 · S · Gate G
  - citation corrected: tday-web/src/components/app/RootDock.tsx:65 (pillStyle init), :81-92 (post-paint measure), :122 (unconditional transition-all)

### PR 178 — web root feed search overlay cut

- [ ] `web:shell-sidebar-settings#9` — 9 · web · Sev 2 · XS · Gate V

### PR 179 — web secondary list mutations uncued

- [ ] `web:shell-sidebar-settings#12` — 12 · web · Sev 2 · M · Gate V
- [ ] `web:shell-sidebar-settings#8` — 8 · web · Sev 1 · M · Gate V

### PR 180 — wizard step chip state snaps

- [ ] `android:nav-settings#9` — 9 · and · Sev 2 · XS · Gate J

### PR 181 — calendar select date bypasses pager

- [ ] `android:calendar#adjacent-month-tap-snaps-grid` — adjacent month tap snaps grid · and · Sev 1 · M · Gate D

### PR 182 — centered selector overlay transition node

- [ ] `ios:completed-settings-guide#settings-overlay-scrim-scaled` — settings overlay scrim scaled · ios · Sev 1 · XS · Gate D

### PR 183 — completion timing 160 contract

- [ ] `ios:home-dock#9` — 9 · ios · Sev 1 · XS · Gate G

### PR 184 — dock collapse threshold hysteresis

- [ ] `android:home-dock#10` — 10 · and · Sev 1 · S · Gate G

### PR 185 — fab accent crossfade

- [ ] `ios:home-dock#6` — 6 · ios · Sev 1 · XS · Gate D

### PR 186 — hero search morph single spec

- [ ] `ios:home-dock#10` — 10 · ios · Sev 1 · XS · Gate G

### PR 187 — ios calendar pager page list churn

- [ ] `ios:calendar#page-rebuild-between-current-and-next-month` — page rebuild between current and next month · ios · Sev 1 · M · Gate G

### PR 188 — loading to content crossfade

- [ ] `android:nav-settings#11` — 11 · and · Sev 1 · XS · Gate D

### PR 189 — member roster row motion

- [ ] `web:floater-dashboards#10` — 10 · web · Sev 1 · M · Gate V

### PR 190 — numeric count transition

- [ ] `android:home-dock#9` — 9 · and · Sev 1 · XS · Gate G
- [ ] `web:floater-dashboards#15` — 15 · web · Sev 1 · S · Gate V

### PR 191 — search filter reflow unanimated

- [ ] `android:nav-settings#4` — 4 · and · Sev 1 · S · Gate D

### PR 192 — settings silenced dim uncrossfaded

- [ ] `ios:completed-settings-guide#settings-reminders-dim-snaps` — settings reminders dim snaps · ios · Sev 1 · XS · Gate G

### PR 193 — web segmented pill timing divergence

- [ ] `web:shell-sidebar-settings#13` — 13 · web · Sev 1 · XS · Gate G

### PR 194 — web settings inline editor affordance cut

- [ ] `web:shell-sidebar-settings#7` — 7 · web · Sev 1 · XS · Gate V

### Unresolved — needs a device, not work yet

- [ ] `ios:completed-settings-guide#settings-notifications-toggle-bounce` — settings notifications toggle bounce · ios · **unresolved** · Gate D
