# Motion

The normative home of T'Day's motion vocabulary: the durations, delays, easings,
springs and press scales the three clients animate with, what each one is for,
and what each one already stands on in the tree.

The hand-authored source of truth is
[`shared/src/commonMain/kotlin/com/ohmz/tday/shared/motion/MotionTokens.kt`](../shared/src/commonMain/kotlin/com/ohmz/tday/shared/motion/MotionTokens.kt).
Android, iOS and web each read a **generated** artifact produced from it; nobody
edits those by hand. This file is the prose half of that pair — it exists so a
reviewer can pick the right token **without looking at the number**.

## When a number belongs here

> A number that appears in two clients belongs in `MotionTokens`. A number that
> appears in one, with a written argument for why it is special, stays where it
> is and carries a `not a token — see docs/motion.md` comment.

That is the whole rule, and it cuts both ways. A token file that swallows the
best-reasoned specs in the codebase is a regression, not a cleanup: the argument
written next to a one-client value is worth more than the tidiness of having it
in a list. There is exactly one deliberate exception — `Easings.Gesture`, which
is web-only and says so in its own doc string.

## How the counts in this file were taken

Every count below was measured against the tree at `4c941b1c`, the commit that
introduced `MotionTokens.kt`. They are the evidence the vocabulary was chosen
on, not a live figure: later PRs migrate call sites onto the tokens, which moves
raw-literal counts down by design. `file:line` references point at the current
tree.

PRs 8g–8n are that sentence being cashed, and they are the last of it: the
call-site migration moved 104 literals off the counters in eight units — 38 iOS
spring parameters (8g, 8h), 47 web `duration-*` utilities (8i, 8j), 14 Android
tweens (8k, 8l, 8n) and 5 iOS `duration:` arguments (8m). Three of the eight
counters now say something a ceiling normally cannot. `web.durationUtility` is
at **zero**, so the next `duration-<n>` written anywhere in `tday-web/src` is
new by construction. `web.rawCubicBezier` has reached the state a ratchet is
aiming at without ever reaching zero: all three survivors are enumerated one by
one, each argued at its own call site and each with a row in the non-tokens
table below, so the ceiling and the list of sites are the same thing and a
fourth line is always a new decision. `android.pressScale`'s eight are
enumerated too, but only three of them are argued where they are written — the
swipe buttons' two composed factors and `TdayPressable`'s `FabScale`. The other
five are unclaimed rather than owned: an enter scale and a scroll-driven dock
reveal, which this counter's docstring admits it over-counts by design
(`TdayEmptyState`'s `EnterStartScale`, `TdayHeroTitleHeader`'s
`DockedTitleScaleFrom`), the dock label's text ramp, and the segmented slider's
press halo. A ninth line is still always new; five of the eight are still
waiting for whoever retimes those surfaces to say what they are. What
is left elsewhere is deliberate and owned — the dock's six springs, the iOS
springs that are off-token by damping rather than by choice, and the two iOS
counters whose remainder is a retiming with a device behind it rather than an
adoption. The live figures are in
`tday-web/tests/fixtures/motion-budget.json`, which is the only place they are
kept current; the ones in this file stay the census they were.

Android counts cover `android-compose/app/src/main/java` (tests excluded); iOS
counts cover `ios-swiftUI/` including the widget extension, and say so where the
two differ; web counts cover `tday-web/src` excluding `src/generated/`.

The counting rules are part of the evidence, because a count nobody can
reproduce is an assertion:

- **An Android tween** is one `tween(…)` construction whose duration resolves to
  the rung, following `const val` indirection — ten of `Emphasis`'s fifteen were
  spelled `CREATE_TASK_SHEET_MOTION_MS` or `CREATE_LIST_SHEET_MOTION_MS` at the
  census, not `320`. Four of those ten are `TdaySheetMotion.cardIn()` now, two
  are a `Durations.Emphasis` sheet resize written out, and four left the rung
  altogether with the card's exit. A number quoted inside a comment is not a
  call site.
- **A web transition utility** is a `transition` or `transition-<property>` class
  in a class string. It "names a duration" when a `duration-*` class appears in
  the same string literal. `transition-none` disables a transition rather than
  declaring one, and the three arbitrary-property `transition-[…]` forms are a
  different spelling; neither is in the census figure.
- **An iOS site** is one call — `.easeInOut(duration:)`, `.easeOut(duration:)`,
  `.spring(response:dampingFraction:)` — counted per call, not per file.

## Where a token is spelled on each client

The tables below give the raw value per platform; this is the name you actually
type.

| | Android | iOS | Web |
|---|---|---|---|
| Durations, delays | `TdayMotionTokens.Durations.*` / `.Delays.*` | `TdayMotion.Durations.*` / `.Delays.*` | `var(--tday-duration-*)` / `var(--tday-delay-*)` in CSS; `DURATION_MS` / `DELAY_MS` from `src/lib/motion.ts` when a timer needs the number |
| Easings | `TdayMotionTokens.Easings.*` | `TdayMotion.standard(duration:)`, `.enter(duration:)`, `.exit(duration:)`, `.scene(duration:)` | `ease-in-out` / `ease-out` / `ease-in` (Tailwind's own, byte-identical to `Standard` / `Enter` / `Exit`), plus `ease-scene` and `ease-gesture` mapped in `globals.css` |
| Springs | `TdayMotionTokens.Springs.snappy()` / `.gesture()` / `.settle()` | `TdayMotion.snappy` / `.gesture` / `.settle` | none — web has no spring runtime and uses the `Gesture` easing instead |
| Press scales | `TdayMotionTokens.PressScales.*` | `TdayMotion.PressScales.*` | `var(--tday-press-*)`, or `PRESS_SCALE` from `src/lib/motion.ts` |

The generated stylesheet declares `--tday-duration-*`, `--tday-delay-*`,
`--tday-ease-*` and `--tday-press-*` on `:root` and nothing else. Only the two
curves Tailwind does not already ship are lifted into `@theme inline` as
utilities: redeclaring `ease-in-out`, `ease-out` or `ease-in` would mint a second
name for a curve the existing utilities already resolve to, which is how a
vocabulary stops being one.

---

## Durations

| Token | Android | iOS | Web | Reach for it when |
|---|---|---|---|---|
| `Quick` | `150` | `0.15` | `var(--tday-duration-quick)` | The app is answering a finger that is on it, or something is leaving that nobody is meant to watch go |
| `Enter` | `200` | `0.2` | `var(--tday-duration-enter)` | One element arrives, or a control changes state under its own steam — and nothing argues for another length |
| `Change` | `260` | `0.26` | `var(--tday-duration-change)` | The user's own edit is replayed back to them **in place**; they are meant to watch it finish |
| `Emphasis` | `320` | `0.32` | `var(--tday-duration-emphasis)` | Position or size changes: a row takes a new slot, a sheet arrives, a strikethrough sweeps across |
| `Scene` | `520` | `0.52` | `var(--tday-duration-scene)` | A full-bleed illustration rises into an empty feed. The arrival, and not the way back out — see the bullet below |

Five rungs, deliberately. Rungs closer together than about two frames at 60 Hz
cannot be told apart by eye, which means a guardrail cannot tell a correct
choice from a lazy one — so the ladder is only as fine as it is enforceable.

**What each rung stands on**

- **`Quick` (150).** Android 5 tweens, iOS 1, web 2 explicit `duration-150`.
  The real weight is elsewhere: Tailwind's `--default-transition-duration` is an
  un-overridden 150 ms, and of the 183 `transition*` utilities in `tday-web/src`
  (159 property-specific `transition-<prop>`, 24 bare `transition`), **123 name
  no duration at all** and therefore already run on this rung.
  `tday-web/src/globals.css:242` quotes 159 — the property-specific count — as
  the blast radius of rebinding the default; 123 is the narrower figure that
  would actually move, being the utilities that name no duration of their own.
  The `iOS 1` is a census figure and the site it counted has since left the rung:
  the Guide's topic card expands on `Emphasis` now, because it grows a box and
  rule 2 below decides that by geometry. Nothing on iOS spells this rung as a
  number any more — every site reaches it through `TdayMotion.Durations.quick`,
  which is seven `withAnimation` / `.animation` transactions plus
  `RootFeedDock`'s reduced-motion substitute and `TdayFeedItemMotion.departure`,
  the rung under a second name for a row on its way out. The anchor below is
  named rather than numeric for that reason, and a `duration:` grep now finds
  none of the nine.
  Anchors: `android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TdayFeedItemMotion.kt:60`,
  `ios-swiftUI/Tday/Feature/App/AppRootView.swift:173`,
  `tday-web/src/globals.css:233`.
- **`Enter` (200).** 52 web `duration-200` utilities and 7 iOS sites (5 in the
  app, 2 in the widget extension); **zero** on Android at the census. The first
  two Android sites are the hand-built sheets' dim in both directions —
  `TdaySheetMotion.scrimIn()` and `scrimOut()`, one construction each — which is
  the rung being reached for rather than the census moving. This rung is 200 and
  not 190 because 190 matched two hand-written Android tweens
  (`android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TdayFeedItemMotion.kt:54`,
  and the Completed rows' own fade-in, which has since gone the other way and
  taken `TdayFeedItemMotion`'s spec wholesale, so the rung's rival is down to one
  name in one file, plus one conditional branch at
  `android-compose/app/src/main/java/com/ohmz/tday/compose/ui/component/TdaySegmentedSlider.kt:199`) against 200's 57 in the two
  apps proper (52 web plus the 5 iOS app sites, the widget extension's two
  aside). Anchors: `tday-web/src/components/settings/SettingsPage.tsx:295`,
  `ios-swiftUI/Tday/Feature/Onboarding/OnboardingWizardOverlay.swift:123`.
- **`Change` (260).** 3 Android tweens, 8 iOS `.easeInOut(duration: 0.26)`, and
  2 on web, both named constants — thirteen sites across all three clients, with
  the weight on iOS. It was fifteen until the calendar's two paging animations
  moved to `Emphasis`: a page turn moves the grid, and moving is geometry.
  If the thing changes *where* or
  *how big* it is, that is `Emphasis`, not this. One Android site used to
  disagree, running a *placement* on 260 — the timeline's drop placeholder, which
  this paragraph called a call site to settle rather than a reason to widen the
  rung. It is settled: a placeholder is an item in the feed, so it takes
  `TdayFeedItemMotion` entire like everything else in that feed, and nothing in
  the tree now runs a placement on this rung.
  Anchors:
  `android-compose/app/src/main/java/com/ohmz/tday/compose/feature/scheduledtaskhome/ScheduledTaskHomeScreen.kt:1520`,
  `ios-swiftUI/Tday/Feature/Todos/TodoListScreen.swift:3180`,
  `tday-web/src/lib/taskCompletionTiming.ts:36`.
- **`Emphasis` (320).** 15 Android tweens at the census (eleven of them reached
  through a named constant rather than written out), 5 iOS, 1 on web. Long
  enough to be followed with the eye. Ten of those eleven were the two
  create-sheet constants, and both are gone: `TdaySheetMotion` holds the
  hand-built sheets' four specs now, and it spends this rung on the card's
  *arrival* only — the card's exit answers to `Change`, which is the nearer rung
  to iOS's own 0.24 and the one that keeps an exit from outlasting its enter.
  The two content resizes those constants also drove stayed here, naming
  `Durations.Emphasis` directly: a sheet changing height is geometry, but it is
  not the sheet arriving, and tying it to the card's spec would retime it the
  next time the arrival moves. The eleventh is still
  `TdayFeedItemMotion.PlacementMillis`.
  Anchors:
  `android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TaskStrikethrough.kt:63`,
  `ios-swiftUI/Tday/Feature/Todos/TodoListScreen.swift:351`,
  `tday-web/src/globals.css:813`.
- **`Scene` (520).** 2 Android, 1 iOS, 2 web at the census. This rung is the
  empty-state illustration **arriving**: all three clients name their site for
  the enter (`EnterMillis`, `EmptyStateEnter.duration`, `.tday-empty-enter`).
  It is **not** the way back out. There is one exit that mirrors that arrival
  anywhere in the tree — web's, played whenever that scene gives its slot up to
  an "Earlier" bucket's rows — and it is deliberately not on this rung: an exit that hands a slot to an arrival answers
  to that arrival's length, not to the length of the scene it undoes, and
  `todayEarlierIllustration.ts` writes the argument out where the constant is.
  Nor is this rung for route or tab handovers: those are `Enter` on both
  clients that have them — `globals.css` argues in place for why anything longer
  there reads as a stall, and `TdayApp.kt`'s `navigationEnterTransition` says the
  same thing about the 360 it replaced.
  Anchors:
  `android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TdayEmptyState.kt:372`,
  `ios-swiftUI/Tday/Core/UI/TdayEmptyState.swift:284`,
  `tday-web/src/globals.css:961`.

## Delays

A delay is not a duration, and they live in separate generated namespaces for a
concrete reason: emitting them together would mint `duration-emphasis` and
`duration-celebration-lead` as interchangeable 320 ms utilities that no
guardrail could tell apart.

| Token | Android | iOS | Web | Reach for it when |
|---|---|---|---|---|
| `PlacementLead` | `320` | `0.32` | `var(--tday-delay-placement-lead)` | A feed must hold a celebration back until the rows that scene displaces have reached their new slots |
| `CelebrationLead` | `320` | `0.32` | `var(--tday-delay-celebration-lead)` | The confetti should have the screen to itself before the scene comes up behind it |

The two are sequential, not competing: the source calls them *added, never
traded*. `PlacementLead` is **derived** from `Emphasis` in `MotionTokens.kt`,
because it exists to match the placement tween exactly and has no freedom of its
own. `CelebrationLead` is an independent literal that happens to equal 320 today
— welding it to `Emphasis` would let a later PR that retimes row placement
silently retime the confetti on all three clients.

- **`PlacementLead`.** Three sites on two clients, all of them a feed that draws
  the empty state *inline* and therefore moves its own layout when that scene
  arrives. Android spends it inside the feed's motion spec
  (`android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TdayFeedItemMotion.kt:76`);
  web spends it at the two screens with the same shape, as `EmptyState`'s
  `celebrationStartDelayMs` —
  `tday-web/src/features/todayTodos/component/AllTasksTimelineContainer.tsx:362`
  and
  `tday-web/src/features/floater/component/NativeFloaterTaskHomeDashboard.tsx:238`.
  The overlay callers on both clients pass nothing, because nothing behind the
  overlay moves. See the open question below.
- **`CelebrationLead`.** One site per client:
  `android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TdayEmptyState.kt:391`,
  `ios-swiftUI/Tday/Core/UI/TdayConfetti.swift:520`,
  `tday-web/src/globals.css:976`. Web's is the one that shows the two delays
  adding: `.tday-empty-enter-celebrating` is
  `calc(var(--tday-celebration-start, 0s) + var(--tday-delay-celebration-lead))`,
  where the first term is whatever `PlacementLead` the host handed over. What the
  lead is buying time for has a normative file of its own,
  [`docs/confetti-spec.md`](confetti-spec.md): the flight, the fan and the physics
  are specified there, and the 320 ms in front of them stays here.

## Easings

Cubic-bezier control points, in CSS `(x1, y1, x2, y2)` order. Compose's
`CubicBezierEasing(a, b, c, d)` takes the same four numbers in the same order;
the three Compose constants named below are byte-identical to the tokens, proved
against `animation-core`'s bytecode rather than assumed.

| Token | Curve | Android | iOS | Web | Reach for it when |
|---|---|---|---|---|---|
| `Standard` | `(0.4, 0, 0.2, 1)` | `FastOutSlowInEasing` | `timingCurve` — **not** `.easeInOut` | `ease-in-out` | Unmarked. Both ends eased; the curve you use when nothing argues otherwise |
| `Enter` | `(0, 0, 0.2, 1)` | `LinearOutSlowInEasing` | `timingCurve` — **not** `.easeOut` | `ease-out` | Something arriving, which should settle rather than stop |
| `Exit` | `(0.4, 0, 1, 1)` | `FastOutLinearInEasing` | `timingCurve` — `.easeIn` is a near-match | `ease-in` | Something leaving, which should commit rather than drift off |
| `Scene` | `(0.05, 0.7, 0.1, 1)` | `CubicBezierEasing` | `timingCurve` | `ease-scene` | Pairs with the `Scene` duration on the empty-state illustration — and nothing else |
| `Gesture` | `(0.2, 0.8, 0.2, 1)` | — | — | `ease-gesture` | **Web only.** Press feedback and calendar paging |

**What each curve stands on**

- **`Standard`.** 58 `FastOutSlowInEasing` on Android — the busiest easing in
  the repo by a wide margin. On web it is the resolved value of Tailwind's own
  `--ease-in-out` *and* `--default-transition-timing-function`, so all 123
  default-timed utilities are already on it, plus one explicit `ease-in-out` and
  one declaration of its own, `.task-strike-fade` at
  `tday-web/src/globals.css:813`.
- **`Enter`.** 6 `LinearOutSlowInEasing` on Android; 8 `ease-out` utilities on
  web (e.g. `tday-web/src/components/settings/SettingsPage.tsx:295`).
- **`Exit`.** 7 `FastOutLinearInEasing` on Android; zero `ease-in` utilities on
  web today.
- **`Scene`.** `android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TdayEmptyState.kt:375`, and three
  declarations on web — `.tday-empty-enter` at `tday-web/src/globals.css:961`,
  `.tday-surface-enter` at `:1284` and `.tday-surface-exit` at `:1290`. iOS
  expresses the same arrival with `.easeOut` and is not on this curve yet.
- **`Gesture`.** Four sites, all web: `tday-web/src/globals.css:392` (the press
  ripple) and `:494` (the press itself, which is in `@layer tday-press`, opened
  at `:454`, so that a `transition-colors` on a shadcn Button cannot put the
  app's most common button back on Tailwind's default ease),
  `tday-web/src/features/calendar/style/calendar-styles.css:32` and `:36`
  (calendar paging). Android and iOS express the same intent with the
  **Gesture spring**, which is a different thing under a shared name.

> **Migrating an iOS easing site onto a token is a visible change.** SwiftUI's
> `.easeInOut` is `(0.42, 0, 0.58, 1)` and its `.easeOut` is `(0, 0, 0.58, 1)`.
> Neither is `Standard` or `Enter`; the tails are materially different. Only
> `.easeIn` at `(0.42, 0, 1, 1)` is a near-match for `Exit`.

## Springs

Carried as **both** platforms' parameters, because neither set can be derived at
read time. Compose fixes mass = 1, so its undamped natural frequency is
`sqrt(stiffness)`; SwiftUI's is `2π/response`. The two describe the same spring
when `stiffness == (2π/response)²`, and `MotionTokens.Spring.init` enforces that
within 2 % rather than leaving it to a test — a source of truth that can hold two
different springs under one name is not one. Compose's `dampingRatio` and
SwiftUI's `dampingFraction` are the same dimensionless quantity and cross
unchanged.

| Token | SwiftUI | Compose | Reach for it when |
|---|---|---|---|
| `Snappy` | `response 0.28`, `dampingFraction 0.86` | `stiffness 504`, `dampingRatio 0.86` | A confirmation dialog, a selector overlay, a control committing to a new state |
| `Gesture` | `response 0.34`, `dampingFraction 0.82` | `stiffness 340`, `dampingRatio 0.82` | A surface continuing under its own momentum after a finger lets go |
| `Settle` | `response 0.40`, `dampingFraction 0.86` | `stiffness 250`, `dampingRatio 0.86` | Something heavy coming to rest — a dock, a bar, a sheet finding its height |

- **`Snappy`.** The exact `response: 0.28, dampingFraction: 0.86` pair is
  hand-written at **19 sites across 8 iOS files** — by far the busiest spring in
  the repo. (`MotionTokens.kt`'s doc string says nine files; the tree has eight.
  The count of sites, which is what the rung rests on, is right.) No Android
  site matches it yet: Android's nearest is
  `Spring.StiffnessMediumLow` (400 f), which is a library default rather than a
  decision anybody made.
- **`Gesture`.** Looser than `Snappy` on purpose: a release should overshoot a
  little or it reads as a snap-back. The one token here that was already a
  working two-platform conversion before it had a name —
  `android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TaskSwipeRevealState.kt:65` converts
  `ios-swiftUI/Tday/UI/Component/SwipeActions.swift:838` explicitly, and
  `ios-swiftUI/Tday/Core/UI/RootFeedDock.swift:118` is the third site — it wrote
  0.34 / 0.82 out until Reduce Motion needed a gate there, and naming the token
  is what bought one. **Do not round 340 to the arithmetic 342**: it would break
  the Android site that already matches.
- **`Settle`.** One anchoring site today,
  `ios-swiftUI/Tday/UI/Component/TdaySheetChrome.swift:271` (`cardIn`). Android
  has no exact match — `Spring.StiffnessLow` is 200 f and `StiffnessMediumLow`
  is 400 f, and this rung sits between them on purpose.

## Press scales

How far a surface squashes under a finger, by surface class. Smaller surfaces
move further, because the same absolute travel reads as a bigger gesture on a
bar button than on a full-width row.

| Token | Value | Reach for it when |
|---|---|---|
| `Bar` | `0.94` | Bar buttons — round header buttons, the timeline top bar, the search capsule |
| `Card` | `0.97` | Cards and tiles |
| `Row` | `0.985` | Full-width rows, where more travel would read as the list moving |

- **`Bar`.** 10 Android press sites, all of them on
  `Modifier.tdayPressable` — the round buttons in the top bar of Guide,
  Calendar, Completed, Latest release and Settings, the calendar's "Today"
  pill beside one of them, the root feed's search/close pair, the list header's
  back chevron, the Today header button, and the sheet chrome's toolbar action,
  which is the one that passes a 1 dp sink instead of the default 2. On iOS it
  is one modifier, `TdayToolbarButtonEffectModifier`
  (`ios-swiftUI/Tday/Core/UI/TaskFloatingActionButton.swift:167`), worn by 3
  sites through `TdayToolbarButtonStyle`. The number is still what that
  modifier's own argument at `:162` made it: it sat at 0.95, which on a 56 pt
  circle is half a point of travel, close enough to nothing that the press read
  as a tap landing rather than as a button going down. This bullet said "one
  site" until Phase 9's 9b brought the Android half onto it: all ten had typed
  0.93 for themselves — a depth nothing on Android ever argued for — so every
  one of them is a retune of a hundredth as well as a migration.
- **`Card`.** 5 Android press sites; 1 on web
  (`tday-web/src/components/onboarding/OnboardingWizard.tsx:725`). It is also
  `Modifier.tdayPressable`'s default, so it is the depth a surface gets when its
  author has not decided which class it belongs to — the middle of the three
  rather than the deepest.
- **`Row`.** 6 Android press sites: 2 on `Modifier.tdayPressable`, and 4 that
  name the token without moving onto it — the root dock's selector and tab
  content and the segmented slider's selector and segment label, each of which
  is coupled to a spring or an offset that the shared modifier deliberately
  cannot take. 2 on iOS
  (`ios-swiftUI/Tday/Feature/Onboarding/OnboardingWizardOverlay.swift:1347` and
  `:1359`); 6 `active:scale-[0.985]` on web, plus the global press rule at
  `tday-web/src/globals.css:416`, which reads the token rather than writing
  0.985 out again and stays in `@layer base` on purpose — a call site pressing
  to its own depth has to be able to beat it.

These three are the narrowest part of the vocabulary and the tree is no longer
much messier than they are: 6 distinct press literals span 0.93–0.99, and
exactly one of them is Android's. This paragraph said nine spanning 0.92–0.992
before Phase 9's 9b. Only one of the three that went was a press depth: the
segmented slider's segment label, which pressed to 0.98 of its own and now
names `Row` so that it and the pill under it travel as one object. The other
two never were — 0.92 is the swipe buttons' composed reveal factor and the
slider's halo, and 0.992 is `AppRootView.swift:227`'s onboarding backdrop, both
of which the old sentence swept in for being pressed-shaped numbers. What is
left is the FAB disagreement in the open questions, and web's `[0.99]` and
`scale-95`, which are that same disagreement's other end.

---

## The idiom rules

Five rules, each one applicable in review without knowing the numbers.

### 1. An exit is never longer than the enter it undoes

An arrival is information; a departure is the absence of it, and an absence that
lingers reads as the app hesitating. When the two are asymmetric, the exit is
the shorter one.

`android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TdayFeedItemMotion.kt:54` and `:60` — an item
arrives in 190 ms and leaves in 150 ms, with the reason written at `:59`:
*"Shorter than the arrival: an absence should not linger."*

### 2. `Change` vs `Emphasis` is decided by geometry, not importance

If position or size changes, it is `Emphasis`. If the user's edit is replayed in
place and nothing moves, it is `Change`. "How important is this?" is not the
question and produces inconsistent answers; "does anything move?" produces the
same answer from every reviewer.

Both rungs, inside one motion — the staged check-off every task row in every
client plays. The row's content fades where it stands at 260 (`Change`):
`android-compose/app/src/main/java/com/ohmz/tday/compose/feature/scheduledtaskhome/ScheduledTaskHomeScreen.kt:1520`,
`ios-swiftUI/Tday/Feature/Todos/TodoListScreen.swift:3180`,
`tday-web/src/lib/taskCompletionTiming.ts:36`. The rule crossing out the title
grows across it, so it takes 320 (`Emphasis`):
`android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TaskStrikethrough.kt:63`,
`ios-swiftUI/Tday/Feature/Todos/TodoListScreen.swift:351`,
`tday-web/src/globals.css:813`.

The same beat played backwards — a completed task being restored — stays on
`Emphasis` rather than dropping to `Change`, because a rule that retracts is
still changing how big it is. Rule 1 caps it at the length of the enter it
undoes, and it sits exactly at that cap.

### 3. Do not rebind Tailwind's default transition duration

`--default-transition-duration` is an un-overridden 150 ms, which is exactly
`Quick`. Every bare `transition-*` in the app is on that rung for free.
Rebinding it is not a tidy-up — it is a visible change to the 123 utilities that
name no duration of their own. Web's `globals.css` declares only the two easings
Tailwind does not already ship, and says why at
`tday-web/src/globals.css:233`.

A bare utility already on `Quick`, with nothing to change:
`tday-web/src/components/Sidebar/List/ListSidebarSection.tsx:452`.

### 4. Two clients means a token; one client with an argument stays put

A value used by two clients belongs in `MotionTokens.kt`. A one-client value
whose call site explains itself stays where it is and gets a
`not a token — see docs/motion.md` comment, so the next reader knows it was
considered and kept rather than missed.

Both halves, side by side:

- **Promoted.** `android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TaskSwipeRevealState.kt:57` writes
  out the conversion from iOS's `interactiveSpring(response: 0.34,
  dampingFraction: 0.82)` — two clients releasing a row on the same curve, which
  is exactly what the `Gesture` spring names.
- **Kept.** `android-compose/app/src/main/java/com/ohmz/tday/compose/ui/component/TdayPullRefresh.kt:196` carries
  the marker in place: *"0.72 is looser than any spring in the vocabulary on
  purpose: the feed is being let go, not placed."*

### 5. A surface that cannot animate must still draw its FINISHED state

Reduced motion removes the trip, never the destination. A scene held at the
start of its fade looks half-drawn — which is worse than no animation at all,
because the user cannot tell it from a broken render. Android reads
`ANIMATOR_DURATION_SCALE` through
`android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TdayMotion.kt`;
web uses `prefers-reduced-motion`; iOS reads `accessibilityReduceMotion` through
`ios-swiftUI/Tday/UI/Theme/TdayMotionEnvironment.swift`.

Android's half of that file answers in two shapes, because the setting is a
**scale** and not a switch — the user is offered 0x, 0.5x, 1x, 2x, 5x and 10x.
`rememberTdayMotionEnabled()` is the boolean, for the call sites that can only
choose between a `tween` and a `snap`; `rememberTdayMotionScale()` is the number
underneath it, published live by `ProvideTdayMotionScale` in `TdayTheme` — one
`ContentObserver` for the whole app, so that a user who flips the setting from
the quick settings tile is not left looking at the answer this app read at
mount.

That number is also where Android's own **"Reduce motion"** switch lands
(Settings → Motion, persisted by `ReduceMotionPreferenceStore`). The two answers
compose in `effectiveMotionScale`, and the composition is one-way: the in-app
switch can subtract motion and can never add any back, so a device whose
animator scale is 0 stays at 0 whatever the switch says. Android needs the
switch because it is the one client with nothing better: web is handed
`prefers-reduced-motion` and iOS `UIAccessibility.isReduceMotionEnabled`, both of
them accessibility settings a user has already been taught to find, while
`ANIMATOR_DURATION_SCALE` is device-wide and lives behind developer options on
many builds. The Settings row reads the system half on its own
(`rememberSystemMotionScale()`) for one reason: when the device has already
removed animations the switch cannot change anything, and a control that cannot
act must say so rather than claim the app is still moving.

Having two answers means having two **clocks**, and a `scaledDelay` has to be
handed the right one. The device's scale is also Compose's own
`MotionDurationScale`, so every ungated `tween` obeys it whether or not anybody
wrote code for it; the in-app switch reaches only what asks. A wait therefore
runs on the clock of the motion it is covering — `rememberTdayMotionScale()`
where that motion is gated on the preference, `rememberSystemMotionScale()`
where it is a Compose animation nobody has gated yet. Getting that backwards
breaks the fifth idiom rule from the side nobody watches: the motion is kept and
the wait is removed, so the app tears a surface out from under a transition that
is still running. Where the choice is available, gating the covered animation is
the better half of the fix — the run then has one clock instead of two.

iOS has one clock and no switch of its own, so its file is about reach rather
than arithmetic. `\.tdayAnimation` in the environment answers in the two shapes
an ordinary call site needs — `tdayAnimation(TdayMotion.settle)` for anything
that takes an `Animation?`, and `tdayAnimation.isEnabled` for a `.transition`,
which cannot be handed a nil because it does not open the transaction it plays
in; a third shape, for the surfaces whose amplitude rather than whose existence
is the thing being refused, is below. The value is composed rather than stored:
an override written by `tdayResolvedMotion()` at the app root, falling back to
`accessibilityReduceMotion` from the same environment wherever nobody has
written one. The fallback is what covers the surfaces the root cannot reach — a
hand-built `UIHostingController`, of which the calendar's pager makes one per
month page, inherits none of the app's own environment while still resolving the
system keys from its traits. The override is what makes the answer
live: an accessor reading a key it never declared a dependency on is right at
first draw and silent afterwards, which is the runtime half of what this row was
filed for.

**Refusing an animation is not always the accommodation.** The rule above says a
surface must be drawn finished; it does not say the way to that surface must be a
cut. What the accessibility setting is actually about is *amplitude* — a card
crossing the screen, a grid paging sideways, a control jumping a fifth of its own
size — and the standard substitute for a large travel is a crossfade, not nothing.
Refusing everything fails the same user twice: a full-bleed modal that replaces
the screen between two frames gives the eye nothing to follow to it, and a toast
that blinks in and out over a feed reads as the app glitching rather than as the
app doing what it was asked. So iOS's resolver answers in a third shape,
`tdayAnimation(spec, reduced: substitute)` and its `.transition` twin, and the
five surfaces that had to make that judgement each carry it at the call site:

| Surface | Amplitude | Under Reduce Motion |
|---|---|---|
| `TdaySheetChrome`'s bottom-sheet card | a whole screen height | Crossfades in place, on the scrim's own curve — it is placed where it will stay |
| `CalendarPagingScrollView`'s chevron page turn | a whole screen width | Refused. The grid *is* the page, so there is nothing to crossfade that is not the thing being asked for |
| `RootFeedDock`'s collapse/expand swap | an 18 % anchored scale | Keeps the crossfade, drops the scale. The two arms are different controls at different widths |
| `AppRootView`'s snackbar | a full toast height | Keeps the crossfade on `Enter`, drops the slide |
| `TdayCenteredSelectorMotion` (and the 0.96 / 0.985 overlays that share its shape) | 3 % and travels nowhere | Kept as it is. This is already a crossfade; gating it removes no amplitude and leaves the picker pasted on |

The calendar row is the one worth reading before writing another of these. The
travel it refuses was carrying `scrollViewDidEndScrollingAnimation`, which is the
only notification the parent ever gets that a page turn finished — so removing the
motion removed the completion, and the fix is the un-animated path reporting its
own arrival. Removing the trip must not remove what arriving at the destination
told somebody.

- Android: `android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TdayEmptyState.kt:126` seeds the
  appearance `Animatable` at `1f` — fully arrived — when motion is off, rather
  than at `0f` with the animation skipped.
- iOS: `ios-swiftUI/Tday/Core/UI/TdayEmptyState.swift:124` sets `entered = true`
  and returns before the `withAnimation` block.
- Web: `tday-web/src/globals.css:1229` switches the scene's animations off and
  pins the sparkle to `opacity: 1`, with the reason in the block.
- Web, the JS half: CSS cannot see a `setTimeout`, so a sequence gated in
  JavaScript has to ask the same question. `tday-web/src/lib/prefersReducedMotion.ts`
  is the one place that asks it — `prefersReducedMotion()` for a timer at the
  instant it arms, `usePrefersReducedMotion()` for a component whose output
  depends on the preference and must follow it when it changes. Turning an
  animation off without telling the timer that was waiting for it removes the
  trip and keeps the wait, which is the rule broken from the other side:
  `useEarlierExpandHandoff` therefore takes its immediate branch rather than
  holding the finished state behind 520 ms of a scene that cannot animate.
- Android, the coroutine half: the same hole, one platform over. Compose already
  scales the animations themselves — an `animateTo` runs against a
  `MotionDurationScale` in its coroutine context — but `delay()` is outside that
  clock, so at 0x a choreography's gaps outlive the motion they were covering and
  at 2x they fire with it still half-played. `scaledDelay(millis, scale)` in
  `TdayMotion.kt` closes it. The test for whether a wait belongs to it is whether
  the wait would still make sense with the screen frozen; the ones that would not,
  and now go through it, are:
  - The staged check-off, and the restore that plays it backwards — the app's
    most-performed interaction, on all four screens that own a task row
    (`TodoListScreen.kt`, `CalendarScreen.kt` twice, `ScheduledTaskHomeScreen.kt`,
    `CompletedScreen.kt`). All three legs, not just the last: at 0x the tint is a
    `snap()`, `rememberTaskStrikeProgress` hands back its finished progress and the
    fade is a `tween` Compose collapses to a frame, so the row is finished on frame
    one and every millisecond of the 780 is a wait in front of a destination already
    drawn. Web cuts this shallower — `taskCompletionStaging.ts` keeps the first two
    legs — because `prefers-reduced-motion: reduce` is a request for less movement
    while an animator scale of 0 is the platform stating that animations land in one
    frame, and because a *scale* also has to be right at 2x, where a fixed 160 would
    start the strike over a tint still crossfading.
  - The swipe hint (`TaskSwipeRevealState.playHint`), which is two gaps between two
    springs. It takes the scale as a parameter rather than reading it, because it is
    not a composable; at 0x it does not play at all, since a gesture that ends where
    it started has no finished state to draw and a 42 dp flick inside one frame is a
    flicker rather than a suggestion.
  - The search-result path in `TodoListScreen.kt`: the flash hold, the gap between
    its two pulses, the settle before a result is scrolled to — and in
    `ScheduledTaskHomeScreen.kt` the wait before the search surface is torn down
    behind a push that is still running.
  - The hero header's focus hand-off in `RootFeedHeroHeader.kt`. Android's
    `EarlierExpandDeferMillis` — the twin of `useEarlierExpandHandoff` above — used to
    be named here as well, and is not any more: `TodoListScreen`'s empty-state scene
    moved below Earlier's rows, the scene's exit and the rows' entrance stopped
    contesting one slot, and the wait was retired rather than re-scaled. A wait
    deleted is the fifth idiom rule met the only way it stays met. Web and iOS keep
    theirs — but not for the reason recorded here when this was written, which said
    their scene is drawn over the list rather than in it. That is true of iOS (an
    `.overlay` with a reserved-top-height preference key) and false of web, whose
    scene is an inline sibling in the flow and now renders BELOW Earlier's block
    exactly as Android's does. Web keeps both its defers on a mechanism argument of
    its own: `.tday-empty-slot` is a grid track with a transition and no start value
    on mount, so the scene's 42vh lands in a single frame however the blocks are
    ordered, and neither direction of the hand-off can be given to the layout the way
    Compose's `animateItem` gives Android's. `useEarlierExpandHandoff`'s own doc
    carries the full argument, including what retiring the expand half would cost.
  - The celebration lead in `TdayEmptyState.kt` and `TdayConfetti.kt`, which is
    already dead at 0x behind a `motionEnabled` guard but was not stretching at 2x
    with the burst it is timed against.

  It is deliberately **not** for a wait that would still make sense with the screen
  frozen, and the ones left on a plain `delay()` are all of that kind: keystroke
  debounces, the pull-to-refresh spinner's minimum visible time, the undo window,
  a toast's reading time, network backoff, a clock ticking to the next minute, the
  root dock's dwell before it folds itself away. Scaling those takes away time the
  user needs rather than time they spend watching. One is a judgement rather than a
  class: `CREDENTIAL_PROMPT_SETTLE_DELAY_MS` in `OnboardingWizardOverlay.kt` waits
  out a *system* credential dialog, which Compose is not animating and this app
  cannot observe — scaling our side of a hand-off we do not own would be guessing.

---

## Deliberate non-tokens

These are not oversights. Sweeping any of them into the vocabulary would either
change pixels or destroy an argument that is worth more than the tidiness.

| Not a token | Where | Why it is excluded |
|---|---|---|
| The 340–420 ms band | `android-compose/app/src/main/java/com/ohmz/tday/compose/feature/todos/TodoListScreen.kt:6804` (420); `ios-swiftUI/Tday/Feature/Todos/TodoListScreen.swift:320` (0.42, iOS's own leg of the same flash); `ios-swiftUI/Tday/UI/Component/SwipeActions.swift:329` (340 ms hand-off sleep) | Two values, not the same motion, and nothing that would still be true if they were merged. This row said four until Phase 8's 48 and three until Phase 9's 31. The fourth was never a fourth motion: `SwipeRevealHintModifier` held a second copy of `revealHint()`'s own hint sequence, sleep included, and went out with the modifier — the same duplication the budget fixture records for that sequence's two springs. The web press ripple sat in the band at 340 ms too and is no longer in it: it grows from a third of its surface to nearly twice it, which rule 2 puts on `Emphasis`, and 320 is that same motion to within a frame. The third was the Android nav fade-in, and it left for the reason web's route fade left 140: a route change is a hand-over between two screens and not a motion of its own, so it takes the rung a thing arriving takes when it has no reason to be another length. What is left in the band is one flash and one sleep. A rung here would sit one frame from `Emphasis` and could not be told from it by eye — exactly the case the five-rung ladder exists to refuse |
| The 600–620 ms band | `android-compose/app/src/main/java/com/ohmz/tday/compose/feature/todos/TodoListScreen.kt:6808` (620); `ios-swiftUI/Tday/Feature/Todos/TodoListScreen.swift:145` (0.62 flash delay) and `:324` (the 0.62 leg that delay waits out) | Both are legs of the search-result reveal, timed against the legs either side of them rather than against a ladder. They are longer than `Scene`, which is the app's longest *motion* — these are waits |
| The iOS sub-frame sequencing constant | `ios-swiftUI/Tday/Feature/Todos/TodoListScreen.swift:142` (0.08 s pre-scroll delay) | Below the two-frame floor the ladder is built on. It orders events; it is not a motion anybody watches. `CompletedScreen.swift`'s 0.1 s was listed here and did not belong: it was the `.easeOut` on a row transition's removal leg, which is a motion somebody watches, and it is now `TdayFeedItemMotion.departure` — a departure that got 50 % longer, deliberately, because this row was the only thing claiming it was a sequencing constant |
| `cubic-bezier(0.3, 0, 0.4, 1)` | `tday-web/src/globals.css:1041` (`--tday-empty-sink-ease`, ridden by `.tday-empty-exit` and by the `.tday-empty-slot` track it closes) | The empty scene *sinking*. Deliberately not `Scene`'s curve read backwards — the exit is played only during an "Earlier" hand-off and is tuned against that hand-off's own timing. Named as a property rather than written twice: the ink and the slot under it have to leave on one curve or they read as two departures |
| `cubic-bezier(0.25, 1, 0.5, 1)` | `tday-web/src/components/app/RootDock.tsx:333` | The dock's sliding indicator pill. A hard-out curve with no counterpart on Android or iOS, which express the dock with springs |
| `cubic-bezier(0.22, 0.61, 0.36, 1)` | `tday-web/src/components/ui/AnimatedHeight.tsx:62` | The app's only height transition, declared once in the primitive that owns it — promoted out of the onboarding wizard, where it was written inline. One declaration, one client, and a height animation is the one place a curve's tail is load-bearing against layout. Its 280 ms did not survive the promotion: a box changing size is `Emphasis` by the second idiom rule, and that half was never argued |
| `SettleSpring` (0.9 damping, `StiffnessMediumLow`) | `android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TdayHeroTitleHeader.kt:210` | Pinned to the iOS UIView spring its doc comment names, not to the vocabulary's `Settle`. Moving it onto the token would undo the cross-platform match the comment argues for — the opposite of what a token layer is for |
| `TdayPullRefresh`'s specs | `android-compose/app/src/main/java/com/ohmz/tday/compose/ui/component/TdayPullRefresh.kt:196` (0.72 damping), `:245` (220 ms), `:261` (1050 ms wave) | A pull-to-refresh is driven by the finger, not by a clock: the release spring is looser than anything in the vocabulary on purpose, and the wave is a loop rather than a transition |
| The refresh pill's 0.9 / `StiffnessLow` | `android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/RootFeedHeroHeader.kt:525` | Neither half is the vocabulary's: 0.9 is damper than any spring in it, and `Spring.StiffnessLow` is a Compose library default rather than a chosen frequency. The same family as `TdayPullRefresh`'s 0.72 one row up — the pill is being let go by a finger rather than placed — and `Settle`, the rung for something heavy coming to rest, is the wrong description of a thing riding a thumb |
| The car selector's 0.86 with `StiffnessMediumLow` | `android-compose/app/src/main/java/com/ohmz/tday/compose/feature/car/CarTaskSurfaceScreen.kt:303` | The damping IS `Snappy`'s. The stiffness beside it is not: `StiffnessMediumLow` is 400 f against `Snappy`'s 504, a library default rather than a decision, so the pair as written is not the token and naming it would retime the selector. That retiming may well be right; it is a visible change on the one surface in the app reviewed from a car seat, and it belongs to a PR that admits to it |
| The two loops | `android-compose/app/src/main/java/com/ohmz/tday/compose/ui/component/TdayPullRefresh.kt:261` (1050 ms wave); `android-compose/app/src/main/java/com/ohmz/tday/compose/feature/onboarding/OnboardingWizardOverlay.kt:1548` (1300 ms spinner); `ios-swiftUI/Tday/Core/UI/TdayEmptyState.swift:54` (3 s float) and `:60` (1.4 s twinkle) | A loop has a period, not a duration. Every rung on the ladder names how long a change takes to *finish*, and none of these finishes — the longest rung is half a second, which would turn the empty scene's float into a flicker. Grouped here rather than given four rows because the argument is one argument |
| `.interactiveSpring`'s `Gesture` pair | `ios-swiftUI/Tday/UI/Component/SwipeActions.swift:838` | The only entry in this table whose numbers ARE a token: 0.34 / 0.82 is `Gesture` exactly, and this is the site `TaskSwipeRevealState.kt:65` converts to Compose by hand. What is not the token is the constructor. `TdayMotion.gesture` is a plain `.spring`; this settle has to survive a second pan landing on the row before it finishes, and `.interactiveSpring` re-aims at the new target instead of fighting the one in flight. A row that can be grabbed again mid-settle is exactly the case the two constructors differ on. It is wrapped in the row's resolved motion rather than applied bare, because this settle is also a CLOSE whenever the release lands under the detent — the drag-back close — and a `UIViewRepresentable.Coordinator` has no environment to read the gate from, so it is handed one. The numbers are untouched by that; what the gate decides is whether they run at all |
| The swipe sampler's two windows | `tday-web/src/lib/swipeGesture.ts` (100 ms velocity window, 16 ms floor under it) | Neither is a motion anybody watches: they are how long a release is measured over, and how little evidence is too little. The window is Android's `VelocityTracker` horizon, solved by people with far more device data than this repo has; the floor is one frame at 60 Hz, the same fact the five-rung ladder is built on, read for the other half of what it says — a finger cannot be observed to do anything inside one frame, so a distance divided by a sub-frame gap is the platform's event delivery and not a flick |
| `pressedScale * revealScale` | `android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TaskSwipeActionButton.kt:48`; `android-compose/app/src/main/java/com/ohmz/tday/compose/feature/calendar/CalendarScreen.kt:3302` | The 0.92 f here is **one factor of a composed transform**, multiplied by the reveal scale before it reaches the screen. The press-scale tokens are the whole scale a finger sees; this is not the same quantity and must not be given the same name |
| `PREDICTIVE_BACK_MIN_SCALE` (0.90) | `android-compose/app/src/main/java/com/ohmz/tday/compose/TdayApp.kt:148` | How far the screen a predictive-back drag has hold of recedes by the end of that drag. One client, one site, and not the quantity the press scales measure: those grade by surface class and grade it the *other way* — the table's own rule is that smaller surfaces move further — so extending `Row`'s 0.985 to a whole screen would give a recede of nothing at all. A press scale is a surface squashing under the finger on it; this is a screen being carried off the side, and the number's job is to report how far the drag has got. The same distinction the `pressedScale * revealScale` row above draws. A tenth because that is what the platform's own back gesture recedes an outgoing window by, and one edge of one screen should not answer two gestures at two depths. Worth reading twice because no counter can see it: `android.pressScale` greps for `[Ss]cale` beside a `0.9…f` literal and this constant spells it `SCALE`, so the marker comment and this row are the whole of the record |
| The sheet scrim's `0.40` / `0.68` | `ios-swiftUI/Tday/UI/Theme/TdayTheme.swift:76`; `android-compose/app/src/main/java/com/ohmz/tday/compose/ui/component/TdaySheetChrome.kt:110`; `tday-web/src/globals.css:582` and `:654` (`--sheet-scrim`) | Not a duration, a curve or a spring, so it has no home in the generated layer and is not going to get one — `MotionTokens.kt` is the source of truth for *time*, and an alpha would be the first thing in it that is not. It is written down here instead, because a value three clients agree on byte for byte with nothing in any build checking that they do is exactly what rots quietly: 0.40 light and 0.68 dark, written out natively on iOS and Android and carried on web as `--sheet-scrim` since PR 41a, which is when web stopped having four alphas of its own. Web's token is worth having and the native spellings are not missing one — the client with four overlay systems needs a name for the shade, and two clients with one sheet chrome each do not |

---

## Open questions this PR does not settle

**The FAB press scale disagrees with itself.** The FAB is 0.93 on Android and
on iOS (`ios-swiftUI/Tday/Core/UI/TaskFloatingActionButton.swift:224`), but
0.95 on web (`tday-web/src/components/app/TaskFloatingActionButton.tsx:103`,
via `active:scale-95`). Two clients agreeing to the hundredth against a third
is the whole of what is left: the Android side is now
`TdayPress.FabScale`
(`android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TdayPressable.kt:82`),
one constant read by both Android FABs rather than two literals with nothing
tying them together, and its doc argues the extra depth in place — the FAB is
the one control on screen with nothing beside it to be measured against, so it
can travel further before the travel reads as a glitch. Promoting 0.93 to a
fourth `PressScales` entry is the decision this still does not make, and it is
bigger than it looks: `MotionTokens.kt` generates into three clients, the
parity test asserts the whole table by value, and web would have to be argued
onto 0.93 or argued out of the family. The other half of this question —
"`Bar` rests on one site" — is answered: it carries ten Android sites and the
iOS toolbar modifier, and the Press scales section has the count.

**Migrating an iOS easing site onto `Standard` or `Enter` is not a zero-pixel
change.** There are 65 easing sites in `ios-swiftUI/` (46 `.easeInOut`, 14
`.easeOut`, 5 `.easeIn`). 60 of the 65 land on a curve that is materially
different from the token they would move to; only the 5 `.easeIn` sites are a
near-match. That is why this PR migrates none of them, and why whoever does must
treat it as a visual change with a visual review, not as a refactor.

**`PlacementLead` has no iOS caller.** It went in ahead of two of its three
clients, on the argument that the inline layout iOS and web share would need it
the day either one animated row placement. Web now does
(`useRowPlacement`), and spends it at both of its inline hosts — the Delays
section lists them — so half of that bet is settled. iOS is the other half: its
rows travel on SwiftUI's implicit layout animation, which nothing there can
currently ask the length of, so the inline empty state has no travel it can name
to wait for. Until it can, this token has two clients and not three. Overlay
callers on every client are not the gap: nothing behind an overlay moves, so
there is no placement to lead in the first place.

---

## How the three clients stay in sync

The mechanism is the repo's second cross-platform Gradle codegen, modelled on
the first (`GuideCatalog` → `exportGuideContent`).

```bash
./gradlew :shared:exportMotionTokens   # regenerate the four committed artifacts
./gradlew :shared:verifyMotionTokens   # fail if any of them is stale
```

`MotionTokens.kt` is the only file anyone edits. It generates four artifacts,
all committed:

| Client | Generated artifact | Ergonomic wrapper |
|---|---|---|
| Android | `android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TdayMotionTokensGenerated.kt` | `TdayMotionTokens.kt` |
| iOS | `ios-swiftUI/Tday/UI/Theme/TdayMotionGenerated.swift` | `TdayMotion.swift` |
| Web (CSS) | `tday-web/src/generated/motion-tokens.css` | imported by `globals.css`, which lifts only `ease-scene` and `ease-gesture` into `@theme inline`; it mints no `duration-<token>` utility — durations and press scales are read as `var(--tday-*)` |
| Web (TS) | `tday-web/src/generated/motion-tokens.ts` | `src/lib/motion.ts` |

`verifyMotionTokens` runs in CI in two places, both as a step of the shared
Gradle job:

- `.github/workflows/pr-gate.yml` — every PR.
- `.github/workflows/release.yml` — before a release is cut. The release job
  also runs `exportMotionTokens` and commits the artifacts alongside the guide
  ones. The motion artifacts take no input from `version.json`, so unlike the
  guide artifacts a version bump cannot stale them and that step is normally a
  no-op; it runs anyway so the two codegens are operated identically.

**When the gate goes red**, it is telling you that a committed artifact no
longer matches `MotionTokens.kt`. Do not hand-edit the artifact — the next
export would overwrite you, and the one after that would put the gate back where
it started. Run `./gradlew :shared:exportMotionTokens`, commit the regenerated
files with the change to `MotionTokens.kt` that caused them, and add the count
that justifies the new rung to this file.
