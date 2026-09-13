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

- [ ] *infra* — Rule A (content empties as exit starts) **+ fix** `TdayToastHost.kt:140` · and · S · Gate J
- [ ] `and-toast-exit-never-plays` — toast content lambda returns null the frame `visible` flips · and · Sev 3 · XS · Gate J+D
- [ ] `and-toast-drag-dismiss-has-no-threshold` — drag-dismiss commits on any downward movement (1 px twitch) · and · Sev 2 · S · Gate J+D

### PR G3 — Rule B, write-once visibility flag

- [ ] *infra* — Rule B (write-once visibility flag) — deferred to PR 15a/PR 41b which carry its fixes · and · XS · Gate J

### PR G4 — Rule C, composition-constant animation target

- [ ] *infra* — Rule C (composition-constant target) **+ 2 deletions** · and · S · Gate J
- [ ] `and-create-sheet-dead-keyboard-height-animation` — `keyboardSheetHeight`'s `animateDpAsState` can never run · and · Sev 2 · XS · Gate J
- [ ] *new, not one of the 109* — `OnboardingWizardOverlay.kt:1679-1683` `wizardStepChipBorderWidth` animates a literal `1.dp` · and · Sev 2 · XS · Gate J

### PR G5 — Rule D, a guard implies `visible`

- [ ] *infra* — Rule D (guard implies `visible`) **+ fix** — carries PR 13 · and · S · Gate J
- [ ] `and-earlier-scene-enter-never-runs` — Earlier empty scene enters already visible; 34 % of screen snaps open · and · Sev 4 · S · Gate J+D

### PR G6 — the iOS reachability scanner

- [ ] *infra* — `scripts/ios-motion-reachability.mjs` + baseline + `ios-tests.yml` job · ios · S · Gate self

### PR G7 — the web exit-animation guardrail

- [ ] *infra* — `tests/guardrails/motion-exit-animations.test.ts` (5 rules) **+ 4 fixes** · web · S · Gate G
- ↳ part 1 of 2 of `web-dead-motion-code` — delete `.animate-scroll-left` (`globals.css:300`) + `.animate-task-complete` (`:529`). Box lives under **PR 18**.
- [ ] `web-centered-selector-has-no-exit` — Radix unmounts overlay + card on the same frame · web · Sev 3 · XS · Gate G
- [ ] `web-sheet-overlay-outruns-panel` — overlay has no duration → 0.15 s fallback vs panel's 300/500 ms · web · Sev 2 · XS · Gate G
- [ ] *new, not one of the 109* — `InstallPromptBanner.tsx:8` — `return null` against `animate-in slide-in-from-bottom-4` at `:14` · web · Sev 2 · XS · Gate G

### PR G8 — pbxproj registration and the zero-test assertion

- [ ] *infra* — pbxproj-registration guardrail + `ios-tests.yml` zero-test assertion · ios · XS · Gate G

## Phase 2 — the one-line sweeps (iOS batch 1 + web presence)

### PR 16 — three iOS orphaned transitions, one line each

- [ ] `ios-settings-dayahead-overlay-snap` — Day Ahead selector has a `.transition` with no `.animation(value:)` · ios · Sev 3 · XS · Gate G+X
- [ ] `ios-orphaned-transition-day-ahead` — The Day Ahead selector declares a .transition with no animating ancestor, so it hard-cuts one row away from two siblings that spring · ios · Impact O3 · XS · Gate G+X
  - **Duplicate of `ios-settings-dayahead-overlay-snap` (§2.2).** No conflict to resolve — the two filings are a byte-identical one-line insert between `SettingsScreen.swift:274` and `:275`. Ticks with its twin.
- [ ] `ios-onboarding-security-questions-cut` — neither security-questions flag is in the wizard's `.animation` list · ios · Sev 3 · XS · Gate G+X
- [ ] `ios-security-questions-exits-animate-by-accident` — two of three exits animate only by coincidence · ios · Sev 2 · XS · Gate TF

### PR 44 — the iOS create-sheet selector overlay

- [ ] `ios-selector-overlay-unanimated` — create-sheet selector overlay: no transition, no animation, six open sites · ios · Sev 3 · S · Gate G+TF
- [ ] `ios-selector-pops-while-card-slides` — card slides ~300 pt down as the unanimated selector pops over it · ios · Sev 2 · XS · Gate TF
- [ ] `ios-sheet-dismiss-keyboard-lingers` — only scrim-tap resigns first responder; X and confirm leave the keyboard · ios · Sev 3 · XS · Gate TF
- [ ] `ios-uiscreen-main-keyboard-probe` — deprecated scene-unaware `UIScreen.main.bounds.maxY` · ios · Sev 1 · XS · Gate X

### PR 45 — iOS empty states that blank or cut

- [ ] `ios-calendar-empty-state-blanked-by-isloading` — pull-to-refresh blanks the empty state for the whole sync · ios · Sev 3 · S · Gate G+TF
- [ ] `ios-completed-empty-state-no-removal-transition` — deleting a char out of a no-match search makes it vanish in one frame · ios · Sev 2 · XS · Gate G
- [ ] `ios-completed-stale-pull-to-refresh-comment` — a comment justifying a gesture the screen does not have (cost a verifier pass) · ios · Sev 1 · XS · Gate review

### PR 17a — closed inside G7

- *(no row, no work — the two exit defects this PR was cut for are fixed inside PR G7)*

### PR 17b — the web modal exit

- [ ] `web-modal-has-no-exit` — `if (!isOpen) return null`; 8 call sites blink out · web · Sev 3 · M · Gate V

### PR 51 — three web surfaces with no transition at all

- [ ] `web-calendar-search-results-pop` — mobile search results panel is a bare conditional · web · Sev 1 · XS · Gate V
- [ ] `web-bulk-selection-bar-has-no-transition` — fixed bottom bar with no enter and no exit · web · Sev 1 · XS · Gate V
- [ ] `web-floater-row-dead-dnd-subscription` — every floater row calls `useSortable` with no `DndContext` anywhere · web · Sev 1 · XS · Gate V

### PR 18 — the rest of web’s dead motion code

- [ ] `web-dead-motion-code` — Delete six dead motion artefacts that account for 5 of web's ~14 durations and 2 of its 10 easing curves · web · Impact O3 · S · Gate G — **final part (2 of 2)**; PR G7 carried the rest

## Phase 3 — what the user touches daily

### PR 19 — the Android swipe row tracks the finger

- [ ] `and-swipe-row-lags-finger` — one `StiffnessLow` spring drives drag *and* settle; rows trail the thumb ~141 ms · and · Sev 4 · S · Gate J+D
- [ ] `android-swipe-reveal-tracks-finger` — Swipe-to-reveal is spring-chased during the drag instead of tracking the finger 1:1, so every task row visibly trails the thumb · and · Impact O4 · S · Gate J+D
  - **Duplicate of `and-swipe-row-lags-finger` (§2.2).** Same file (`TaskSwipeRevealState.kt:96-100`), same fix, but the two filings disagreed on the release spring — `0.82f/340f` against `0.85f/StiffnessMedium` (1500f), a 4.4× stiffness gap. §2.2 settles it at `dampingRatio = 0.82f, stiffness = 340f`, because 340f is iOS's `SwipeActions.swift:368` release spec converted and `StiffnessMedium` is a Compose default nobody chose. Ticks with its twin.
- [ ] `and-swipe-hint-stomps-live-drag` — `playHint()` yanks a row to zero under a live finger · and · Sev 2 · XS · Gate J

### PR 15a — the Android create sheet actually plays its exit

- [ ] `and-create-sheet-dismiss-cut` — `sheetVisible` never set false; 320 ms exit is dead code, 7 call sites · and · Sev 4 · M · Gate G+D
- [ ] `and-sheet-scrim-ripple-on-dismiss` — full-screen Material ripple on a dismiss tap · and · Sev 2 · XS · Gate D

### PR 15b — the create sheet’s IME height stops leaping

- [ ] `and-create-sheet-ime-height-snap` — boolean IME threshold swaps the whole modifier chain; leaps to 85 % of screen · and · Sev 3 · M · Gate J+D

### PR 11 — Android’s semantic haptic vocabulary

- ↳ part 1 of 2 of `semantic-haptic-vocabulary` — 55 of 64 Android haptics are the same `CLOCK_TICK`. Box lives under **PR 10**.

### PR 10 — iOS haptics and bar-button press depth

- [ ] `semantic-haptic-vocabulary` — One semantic haptic vocabulary: 55 of 64 Android haptics are the same CLOCK_TICK, and four documented iOS haptics have zero call sites · and+ios · Impact O3 · S · Gate G + D + TF — **final part (2 of 2)**; PR 11 carried the rest
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

- [ ] *no ledger row* — ⚠ value change: `--default-transition-duration: 190ms` — **one line, its own PR** · web · XS · Gate D

## Phase 5 — web primitives, in dependency order

### PR 24c — hoist the reduced-motion helper; it lands first

- [ ] `web-prefers-reduced-motion-helper` — hoist out of `useFadeUnmount.ts:8-11`; **land first, 2 rows block on it** · web · Sev 2 · S · Gate V
- [ ] `web-earlier-handoff-ignores-reduced-motion` — reduced-motion users get 520 ms of static illustration then everything at once · web · Sev 3 · S · Gate V

### PR 22a — completed rows collapse their box

- [ ] `web-completed-row-box-does-not-collapse` — row fades ink but holds full height to 780 ms · web · Sev 3 · M · Gate V+D

### PR 22b — FLIP placement for the web feed

- [ ] `web-feed-rows-have-no-placement` — no FLIP anywhere in `tday-web`; every neighbour of a removed row teleports · web · Sev 3 · L · Gate V+D
- [ ] `web-today-section-wrapper-drops-gap` — Today section wrapper unmounts with its gap (~70 px) · web · Sev 2 · XS · Gate V
- ↳ part 1 of 2 of `feed-item-motion-parity` — `src/lib/feedItemMotion.ts` mirroring `TdayFeedItemMotion.kt`. Box lives under **PR 47**.

### PR 23 — empty-state slots stop claiming their height in one frame

- [ ] `web-empty-state-slot-claims-42vh-in-one-frame` — the 42vh slot is claimed the same frame the last row is pruned · web · Sev 3 · M · Gate V+D
- [ ] `web-floater-empty-arrival-displaces-tiles` — ~33 vh of uncued jump, on the confetti frame — largest in the set · web · Sev 4 · M · Gate V+D
- [ ] `web-empty-state-anchor-citation-fix` — ledger hygiene: `EmptyState.tsx:163` does not exist; real anchors `:54`/`:60-65` · web · Sev 1 · XS · Gate doc

### PR 24a — the Earlier hand-off animates height, not just paint

- [ ] `web-earlier-handoff-height-jump` — hand-off animates paint only; the collapse path jumps twice 260 ms apart · web · Sev 4 · M · Gate V+D
- [ ] `web-earlier-exit-520ms-dead-wait` — `TODAY_EARLIER_EXIT_MS` 520 → **220**; update `today-earlier-illustration.test.ts:165-172` same commit · web · Sev 3 · S · Gate V

### PR 24b — the Earlier collapse gets the expand’s hand-off

- [ ] `web-earlier-collapse-has-no-handoff` — expand is sequenced, collapse is not → two jumps per tap · web · Sev 3 · M · Gate V
- [ ] `web-illustration-pops-back-inside-celebrate-window` — illustration snaps back to full opacity over Earlier's rows, and stays · web · Sev 3 · M · Gate V
- [ ] `web-celebrate-window-expiry-is-an-untimed-cut` — the 4 s window closes on an unrelated re-render, then hard-cuts · web · Sev 3 · M · Gate V

### PR 56 — the Earlier chevron says something during the wait

- [ ] `web-earlier-header-no-feedback-during-handoff` — the tapped chevron is identical for the whole 520 ms wait · web · Sev 3 · S · Gate V
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

- [ ] `web-calendar-swipe-is-untracked` — pointerdown records x, pointerup jumps; nothing moves under the finger · web · Sev 3 · M · Gate V+D

### PR 25a — the calendar grid height animates across month lengths

- [ ] `web-calendar-grid-height-snaps-mid-slide` — 35-vs-42-day months and view switches change height in one frame · web · Sev 3 · M · Gate V+D

### PR 25b — calendar row removal and the highlight ring

- [ ] `web-calendar-row-removal-height-snap` — ticking fades ink but holds height to t=960 ms · web · Sev 2 · S · Gate V+D
- [ ] `web-calendar-highlight-ring-cuts` — `box-shadow` missing from the inline transition whitelist · web · Sev 2 · XS · Gate V

### PR 25c — three small calendar and dialog cuts

- [ ] `web-calendar-back-swipe-at-floor-is-silent` — rejected swipe at the earliest month produces no feedback at all · web · Sev 2 · S · Gate V
- [ ] `web-calendar-drag-overlay-drops-with-no-animation` — `dropAnimation={null}`; card vanishes at release · web · Sev 2 · XS · Gate V+D
- [ ] `web-delete-dialog-has-no-fallback` — first Delete tap renders literally nothing until the chunk lands · web · Sev 2 · XS · Gate V

### PR 49 — the drawer placeholder matches the surface it precedes

- [ ] `web-drawer-placeholder-wrong-shape-on-desktop` — bottom-sheet skeleton for a desktop modal; `ModalPlaceholder` has zero importers · web · Sev 3 · XS · Gate V
- [ ] `web-drawer-placeholder-double-arrival` — static placeholder at final geometry, then vaul slides the real sheet into it · web · Sev 3 · S · Gate V+D

### PR 50 — the nested confirm drawer’s double scrim

- [ ] `web-nested-confirm-drawer-double-scrim` — two `black/80` scrims compose to ~96 % black, both close in one frame · web · Sev 2 · M · Gate D

### PR 20 — velocity and rubber-banding on the web swipe

- [ ] `web-swipe-velocity-rubberband` — position-only commit, hard clamp, browser-default `ease` · web · Impact O3 · M · Gate V+D

### PR 52 — web drag lift and drop

- ↳ part 1 of 2 of `drag-lift-and-drop` — `dropAnimation={null}` on both dnd contexts; static overlay. Box lives under **PR 53**.

### PR 53 — Android drag lift and drop

- [ ] `drag-lift-and-drop` — A dragged row reads as disabled rather than held: Android only dims it to 70%, and both web drag contexts disable the drop animation outright · and+web · Impact O3 · M · Gate V + D — **final part (2 of 2)**; PR 52 carried the rest

### PR 54 — the web press affordance stops losing to `transition-colors`

- ↳ part 2 of 3 of `press-affordance-unification` — `:where()` at 0,0,0 loses to `transition-colors` at 0,1,0 on every shadcn Button. Box lives under **PR 9a/9b**.

### PR 55 — the web route hand-over

- ↳ part 1 of 2 of `route-change-handover` — `.tday-route-fade` 140 → 200 ms; adopt React Router `viewTransition`. Box lives under **PR 31**.

### PR 26 — two iOS feed cuts

- [ ] `ios-today-block-removal-is-a-cut` — `.animation` inside the `if`; ~72 pt of layout vanishes in one frame · ios · Sev 3 · S · Gate G+TF
- [ ] `ios-calendar-day-swap-stacks-rows` — both days' rows play insert and removal over the same pixels · ios · Sev 3 · S · Gate TF

## Phase 8 — accessibility

### PR 33a — the web reduced-motion floor and its subscribing hook

- ↳ part 1 of 6 of `reduced-motion-coverage` — blanket `@media` floor after the Tailwind import + `useReducedMotion` subscribing hook + `src/lib/scroll.ts`. Box lives under **PR 35b**.
- [ ] `web-scrollintoview-ignores-reduced-motion` — search-result jump always smooth-scrolls · web · Sev 2 · XS · Gate V
- [ ] `web-calendar-slide-ignores-reduced-motion` — `calendar-styles.css` has no reduced-motion block at all (29 lines, zero) · web · Sev 3 · XS · Gate G

### PR 33b — web sound and haptic preferences

- ↳ part 2 of 6 of `reduced-motion-coverage` — sound/haptic preference + Settings UI (`haptics.ts`, `TodoCheckbox.tsx:44-53`). Box lives under **PR 35b**.

### PR 34a — Android reads the system animator scale

- ↳ part 3 of 6 of `reduced-motion-coverage` — `ContentObserver` + `LocalTdayMotionScale` + `scaledDelay` through all 5 raw `delay()`. Box lives under **PR 35b**.

### PR 34b — the in-app Android “Reduce motion” toggle

- ↳ part 4 of 6 of `reduced-motion-coverage` — in-app "Reduce motion" toggle + persistence. Box lives under **PR 35b**.

### PR 35a — iOS reads the accessibility environment

- ↳ part 5 of 6 of `reduced-motion-coverage` — root `@Environment` read + custom `EnvironmentKey` + `tdayAnimation(_:)`. Box lives under **PR 35b**.

### PR 35b — the five iOS amplitude decisions

- [ ] `reduced-motion-coverage` — Reduced motion is honoured in 2 of ~99 Android sites, 2 of 20 iOS files and ~15% of web — and on every client the setting cannot be seen to change at runtime · all · Impact O4 · L · Gate V + J + D + X + TF — **final part (6 of 6)**; PR 33a, PR 33b, PR 34a, PR 34b, PR 35a carried the rest

### PR 36 — the Undo toast respects “Time to take action”

- [ ] `android-accessible-toast-timeout` — hard 8 s Undo regardless of "Time to take action" · and · Impact O3 · XS · Gate J+D

### PR 48 — iOS swipe-to-complete becomes reachable

- [ ] `ios-swipe-to-complete-unreachable` — `standardModeContent` is dead; the only `.swipeActions` in the app is unreachable · ios · Impact O4 · M · Gate X

### PR 37 — VoiceOver actions on iOS rows

- [ ] `ios-accessibility-actions` — zero `accessibilityAction` in the target; VoiceOver cannot edit/copy/delete/reschedule · ios · Impact O4 · M · Gate X+D

## Phase 9 — adoption, polish, confetti

### PR 8g…8n — token call-site migration, one directory per PR

- [ ] `motion-token-layer` — No client has a motion token layer — durations and easings live in ~99/168/10-curve piles of literals, and all three theme files define colour and type but not time · all · Impact O4 · L · Gate J + X + V + G — **final part (6 of 6)**; PR 7/8a, PR 8b, PR 8c, PR 8d, PR 8e carried the rest

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

- ↳ part 3 of 5 of `confetti-kinematics` — Android `TdayConfettiKinematics.kt` + JVM I1–I6 + `MaxSpin`→`SpinRange`. Box lives under **PR 39d**.

### PR 39c — iOS confetti kinematics

- ↳ part 4 of 5 of `confetti-kinematics` — iOS `TdayConfettiKinematics` + xctest + **pbxproj registration**. Box lives under **PR 39d**.

### PR 39d — confetti palette and fan-bounds parity

- [ ] `confetti-kinematics` — Land the recovered confetti physics — linear drag, flip split off rotation, smoothstep fade — on all three clients at once · all · Impact O3 · L · Gate self + V + J + X + G + D + TF — **final part (5 of 5)**; PR 38, PR 39a, PR 39b, PR 39c carried the rest

### PR 40a/b/c — one skeleton per client

- [ ] `skeleton-loading-vocabulary` — one skeleton per client at real row geometry, 190 ms crossfade · all · Impact O4 · M ea · Gate V/J/X
- [ ] `web-infinite-scroll-sentinel` — hardcoded English in a ten-locale app, no `aria-live`, 48 px growth per page · web · Impact O2 · S · Gate V

### PR 41a — the web sheet language

- ↳ part 1 of 3 of `sheet-presentation-unification` — one scrim token across 7 spellings; `sheet.tsx` 500→320; drop `slide-in-from-bottom-[48%]`. Box lives under **PR 41c**.

### PR 41b — the Android sheet language

- ↳ part 2 of 3 of `sheet-presentation-unification` — `TdaySheetMotion` from iOS's 4 specs; animate the scrim; two sheet mechanisms. Box lives under **PR 41c**.

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

- [ ] `web:rows-completion-css#root-view-transition-vs-route-fade` — root view transition vs route fade · web · Sev 2 · S · Gate G
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
