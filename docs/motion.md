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

Android counts cover `android-compose/app/src/main/java` (tests excluded); iOS
counts cover `ios-swiftUI/` including the widget extension, and say so where the
two differ; web counts cover `tday-web/src` excluding `src/generated/`.

The counting rules are part of the evidence, because a count nobody can
reproduce is an assertion:

- **An Android tween** is one `tween(…)` construction whose duration resolves to
  the rung, following `const val` indirection — most of `Emphasis`'s fifteen are
  spelled `CREATE_TASK_SHEET_MOTION_MS`, not `320`. A number quoted inside a
  comment is not a call site.
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
  `tday-web/src/globals.css:149` quotes 159 — the property-specific count — as
  the blast radius of rebinding the default; 123 is the narrower figure that
  would actually move, being the utilities that name no duration of their own.
  Anchors: `android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TdayFeedItemMotion.kt:60`,
  `ios-swiftUI/Tday/Feature/Guide/HelpGuideScreen.swift:225`,
  `tday-web/src/globals.css:146`.
- **`Enter` (200).** 52 web `duration-200` utilities and 7 iOS sites (5 in the
  app, 2 in the widget extension); **zero** on Android. This rung is 200 and not
  190 because 190 matches two hand-written Android tweens
  (`android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TdayFeedItemMotion.kt:54`,
  `android-compose/app/src/main/java/com/ohmz/tday/compose/feature/completed/CompletedScreen.kt:290`, plus one conditional branch at
  `android-compose/app/src/main/java/com/ohmz/tday/compose/ui/component/TdaySegmentedSlider.kt:181`) against 200's 57 across two
  clients. Anchors: `tday-web/src/components/settings/SettingsPage.tsx:287`,
  `ios-swiftUI/Tday/Feature/Onboarding/OnboardingWizardOverlay.swift:123`.
- **`Change` (260).** 3 Android tweens, 8 iOS `.easeInOut(duration: 0.26)`, and
  4 on web (two named constants and two CSS animations) — fifteen sites across
  all three clients, with the weight on iOS. If the thing changes *where* or
  *how big* it is, that is `Emphasis`, not this; one Android site already
  disagrees, running a *placement* on 260 at
  `android-compose/app/src/main/java/com/ohmz/tday/compose/feature/todos/TodoListScreen.kt:3159`,
  which is a call site to settle rather than a reason to widen the rung.
  Anchors:
  `android-compose/app/src/main/java/com/ohmz/tday/compose/feature/scheduledtaskhome/ScheduledTaskHomeScreen.kt:1569`,
  `ios-swiftUI/Tday/Feature/Todos/TodoListScreen.swift:3044`,
  `tday-web/src/lib/taskCompletionTiming.ts:36`.
- **`Emphasis` (320).** 15 Android tweens (eleven of them reached through the
  two create-sheet constants and `TdayFeedItemMotion.PlacementMillis` rather
  than written out), 5 iOS, 1 on web. Long enough to be followed with the eye.
  Anchors:
  `android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TaskStrikethrough.kt:63`,
  `ios-swiftUI/Tday/Feature/Todos/TodoListScreen.swift:286`,
  `tday-web/src/globals.css:579`.
- **`Scene` (520).** 2 Android, 1 iOS, 2 web at the census. This rung is the
  empty-state illustration **arriving**: all three clients name their site for
  the enter (`EnterMillis`, `EmptyStateEnter.duration`, `.tday-empty-enter`).
  It is **not** the way back out. There is one exit that mirrors that arrival
  anywhere in the tree — web's, played whenever that scene gives its slot up to
  an "Earlier" bucket's rows — and it is deliberately not on this rung: an exit that hands a slot to an arrival answers
  to that arrival's length, not to the length of the scene it undoes, and
  `todayEarlierIllustration.ts` writes the argument out where the constant is.
  Nor is this rung for route or tab handovers: `globals.css` argues in place for
  why anything longer there reads as a stall. Anchors:
  `android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TdayEmptyState.kt:372`,
  `ios-swiftUI/Tday/Core/UI/TdayEmptyState.swift:272`,
  `tday-web/src/globals.css:608`.

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
  `tday-web/src/features/todayTodos/component/AllTasksTimelineContainer.tsx:313`
  and
  `tday-web/src/features/floater/component/NativeFloaterTaskHomeDashboard.tsx:218`.
  The overlay callers on both clients pass nothing, because nothing behind the
  overlay moves. See the open question below.
- **`CelebrationLead`.** One site per client:
  `android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TdayEmptyState.kt:388`,
  `ios-swiftUI/Tday/Core/UI/TdayConfetti.swift:160`,
  `tday-web/src/globals.css:623`. Web's is the one that shows the two delays
  adding: `.tday-empty-enter-celebrating` is
  `calc(var(--tday-celebration-start, 0s) + var(--tday-delay-celebration-lead))`,
  where the first term is whatever `PlacementLead` the host handed over.

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
  `tday-web/src/globals.css:579`.
- **`Enter`.** 6 `LinearOutSlowInEasing` on Android; 8 `ease-out` utilities on
  web (e.g. `tday-web/src/components/settings/SettingsPage.tsx:287`).
- **`Exit`.** 7 `FastOutLinearInEasing` on Android; zero `ease-in` utilities on
  web today.
- **`Scene`.** `android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TdayEmptyState.kt:375`, and three
  declarations on web — `.tday-empty-enter` at `tday-web/src/globals.css:608`,
  `.tday-surface-enter` at `:789` and `.tday-surface-exit` at `:795`. iOS
  expresses the same arrival with `.easeOut` and is not on this curve yet.
- **`Gesture`.** Four sites, all web: `tday-web/src/globals.css:249` and `:271`
  (press feedback), `tday-web/src/features/calendar/style/calendar-styles.css:32`
  and `:36` (calendar paging). Android and iOS express the same intent with the
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
  `android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TaskSwipeRevealState.kt:49` converts
  `ios-swiftUI/Tday/UI/Component/SwipeActions.swift:368` explicitly, and
  `ios-swiftUI/Tday/Core/UI/RootFeedDock.swift:92` is the third site. **Do not
  round 340 to the arithmetic 342**: it would break the Android site that
  already matches.
- **`Settle`.** One anchoring site today,
  `ios-swiftUI/Tday/UI/Component/TdaySheetChrome.swift:222` (`cardIn`). Android
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

- **`Bar`.** One site, and it carries its own argument:
  `ios-swiftUI/Tday/Core/UI/TaskFloatingActionButton.swift:167`, argued at
  `:162` — it sat at 0.95, which on a 56 pt circle is half a point of travel,
  close enough to nothing that the press read as a tap landing rather than as a
  button going down.
- **`Card`.** 5 Android press sites; 1 on web
  (`tday-web/src/components/onboarding/OnboardingWizard.tsx:725`).
- **`Row`.** 2 Android press sites; 2 on iOS
  (`ios-swiftUI/Tday/Feature/Onboarding/OnboardingWizardOverlay.swift:1347` and
  `:1359`); 6 `active:scale-[0.985]` on web, plus the global press rule at
  `tday-web/src/globals.css:286`.

These three are the narrowest part of the vocabulary and the tree is messier
than they are — nine distinct press literals span 0.92–0.992. See the open
questions.

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
`android-compose/app/src/main/java/com/ohmz/tday/compose/feature/scheduledtaskhome/ScheduledTaskHomeScreen.kt:1569`,
`ios-swiftUI/Tday/Feature/Todos/TodoListScreen.swift:3044`,
`tday-web/src/lib/taskCompletionTiming.ts:36`. The rule crossing out the title
grows across it, so it takes 320 (`Emphasis`):
`android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TaskStrikethrough.kt:63`,
`ios-swiftUI/Tday/Feature/Todos/TodoListScreen.swift:286`,
`tday-web/src/globals.css:579`.

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
`tday-web/src/globals.css:146`.

A bare utility already on `Quick`, with nothing to change:
`tday-web/src/components/Sidebar/List/ListSidebarSection.tsx:452`.

### 4. Two clients means a token; one client with an argument stays put

A value used by two clients belongs in `MotionTokens.kt`. A one-client value
whose call site explains itself stays where it is and gets a
`not a token — see docs/motion.md` comment, so the next reader knows it was
considered and kept rather than missed.

Both halves, side by side:

- **Promoted.** `android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TaskSwipeRevealState.kt:41` writes
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
`ANIMATOR_DURATION_SCALE` through `rememberTdayMotionEnabled()`
(`android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TdayMotion.kt:19`); web uses
`prefers-reduced-motion`.

- Android: `android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TdayEmptyState.kt:126` seeds the
  appearance `Animatable` at `1f` — fully arrived — when motion is off, rather
  than at `0f` with the animation skipped.
- iOS: `ios-swiftUI/Tday/Core/UI/TdayEmptyState.swift:112` sets `entered = true`
  and returns before the `withAnimation` block.
- Web: `tday-web/src/globals.css:744` switches the scene's animations off and
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

---

## Deliberate non-tokens

These are not oversights. Sweeping any of them into the vocabulary would either
change pixels or destroy an argument that is worth more than the tidiness.

| Not a token | Where | Why it is excluded |
|---|---|---|
| The 340–420 ms band | `android-compose/app/src/main/java/com/ohmz/tday/compose/TdayApp.kt:119` (360, nav fade-in); `android-compose/app/src/main/java/com/ohmz/tday/compose/feature/todos/TodoListScreen.kt:5757` (420); `ios-swiftUI/Tday/UI/Component/SwipeActions.swift:214` and `:452` (340 ms hand-off sleeps); `tday-web/src/globals.css:249` (340 ms ripple) | Five values, no two of them the same motion, and nothing that would still be true if they were merged. A rung here would sit one frame from `Emphasis` and could not be told from it by eye — exactly the case the five-rung ladder exists to refuse |
| The 600–620 ms band | `android-compose/app/src/main/java/com/ohmz/tday/compose/feature/todos/TodoListScreen.kt:5761` (620); `ios-swiftUI/Tday/Feature/Todos/TodoListScreen.swift:136` (0.62 flash delay) | Both are legs of the search-result reveal, timed against the legs either side of them rather than against a ladder. They are longer than `Scene`, which is the app's longest *motion* — these are waits |
| The iOS sub-frame sequencing constant | `ios-swiftUI/Tday/Feature/Todos/TodoListScreen.swift:133` (0.08 s pre-scroll delay) | Below the two-frame floor the ladder is built on. It orders events; it is not a motion anybody watches. `CompletedScreen.swift`'s 0.1 s was listed here and did not belong: it was the `.easeOut` on a row transition's removal leg, which is a motion somebody watches, and it is now `TdayFeedItemMotion.departure` — a departure that got 50 % longer, deliberately, because this row was the only thing claiming it was a sequencing constant |
| `cubic-bezier(0.3, 0, 0.4, 1)` | `tday-web/src/globals.css:683` (`--tday-empty-sink-ease`, ridden by `.tday-empty-exit` and by the `.tday-empty-slot` track it closes) | The empty scene *sinking*. Deliberately not `Scene`'s curve read backwards — the exit is played only during an "Earlier" hand-off and is tuned against that hand-off's own timing. Named as a property rather than written twice: the ink and the slot under it have to leave on one curve or they read as two departures |
| `cubic-bezier(0.25, 1, 0.5, 1)` | `tday-web/src/components/app/RootDock.tsx:122` | The dock's sliding indicator pill. A hard-out curve with no counterpart on Android or iOS, which express the dock with springs |
| `cubic-bezier(0.22, 0.61, 0.36, 1)` | `tday-web/src/components/ui/AnimatedHeight.tsx:57` | The app's only height transition, declared once in the primitive that owns it — promoted out of the onboarding wizard, where it was written inline. One declaration, one client, and a height animation is the one place a curve's tail is load-bearing against layout. Its 280 ms did not survive the promotion: a box changing size is `Emphasis` by the second idiom rule, and that half was never argued |
| `SettleSpring` (0.9 damping, `StiffnessMediumLow`) | `android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TdayHeroTitleHeader.kt:210` | Pinned to the iOS UIView spring its doc comment names, not to the vocabulary's `Settle`. Moving it onto the token would undo the cross-platform match the comment argues for — the opposite of what a token layer is for |
| `TdayPullRefresh`'s specs | `android-compose/app/src/main/java/com/ohmz/tday/compose/ui/component/TdayPullRefresh.kt:196` (0.72 damping), `:241` (220 ms), `:253` (1050 ms wave) | A pull-to-refresh is driven by the finger, not by a clock: the release spring is looser than anything in the vocabulary on purpose, and the wave is a loop rather than a transition |
| The swipe sampler's two windows | `tday-web/src/lib/swipeGesture.ts` (100 ms velocity window, 16 ms floor under it) | Neither is a motion anybody watches: they are how long a release is measured over, and how little evidence is too little. The window is Android's `VelocityTracker` horizon, solved by people with far more device data than this repo has; the floor is one frame at 60 Hz, the same fact the five-rung ladder is built on, read for the other half of what it says — a finger cannot be observed to do anything inside one frame, so a distance divided by a sub-frame gap is the platform's event delivery and not a flick |
| `pressedScale * revealScale` | `android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TaskSwipeActionButton.kt:48`; `android-compose/app/src/main/java/com/ohmz/tday/compose/feature/calendar/CalendarScreen.kt:2840` | The 0.92 f here is **one factor of a composed transform**, multiplied by the reveal scale before it reaches the screen. The press-scale tokens are the whole scale a finger sees; this is not the same quantity and must not be given the same name |

---

## Open questions this PR does not settle

**The FAB press scale disagrees with itself, and `Bar` rests on one site.**
The FAB is 0.93 on Android
(`android-compose/app/src/main/java/com/ohmz/tday/compose/feature/todos/TodoListScreen.kt:1422`) and on iOS
(`ios-swiftUI/Tday/Core/UI/TaskFloatingActionButton.swift:224`), but 0.95 on web
(`tday-web/src/components/app/TaskFloatingActionButton.tsx:81`, via
`active:scale-95`). Meanwhile the `Bar` token is 0.94 and stands on a single
call site that carries its own written argument. Nine distinct press literals
span 0.92–0.992 across the three clients. The press-scale PRs settle that
spread; this one records it.

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
