# The confetti burst

The normative file for the celebration burst: its physics, its constants, its seeded
draws, its one haptic, and the tests that hold all three clients to the same numbers.
`docs/motion.md` owns the vocabulary the burst sits inside — the 320 ms
`CelebrationLead` that gives it the screen before the scene rises is that file's, and
stays there. Everything from the throw to the last faded piece is this file's.

## Where this came from, and what that means for trusting it

This is the reconstructed output of a design panel — `confetti-design-panel`, run
2026-09-12 — whose synthesis agent was killed by usage limits three times in a row.
The panel itself finished: three full proposals and three full judgements were
recovered. The winner was **unanimous**: Proposal 1, *"Paper in air: drag-limited
throw, terminal fall, sway-and-rock tumble, one pop haptic"* — judge totals **motion
49 / product 50 / engineer 47**, against P3's 48/48/46 and P2's 43/43/43. What follows
is that proposal with every graft and cut the three judges named applied.

Three things a reader should hold in mind:

- **The arithmetic has been checked twice and the code has never run.** Every number
  below was re-derived by hand at recovery and again when this file was written:
  `vt = 5.1/6.0 = 0.85`; reach `1.10/6.6 = 0.1667` to `1.95/5.4 = 0.3611`; drag time
  constant `1/6` flight = 333 ms; throw spent `1 − e^(−0.45) = 36 %` at 150 ms;
  `dx(1/6) = 1.95·(1−e⁻¹)/6 = 0.205439`; apex `−0.1953` at `tau 0.2074`; worst
  `dx −0.40581`, worst `dy 0.6904`; `smoothstep(0.5) = 0.5`, so alpha 0.5 at tau 0.80.
  It is internally consistent. **It has never been compiled and never been seen on a
  device.** Treat the invariants as the thing that proves it, not the prose.
- **Until this file existed there was one copy, in a plans directory outside the
  repository.** That is why it lands before any code: the insurance is the point.
- **The work it describes is `docs/motion/LEDGER.md` rows PR 38 (this file), PR 39a
  (web), PR 39b (Android), PR 39c (iOS) and PR 39d (palette and fan-bounds parity,
  which carries the single `confetti-kinematics` box for all five).**

A fourth thing, added when this file was written rather than recovered: the tree moved
under the spec while it sat outside the repository. Phase 8's reduced-motion plumbing
landed on all three clients after the panel ran, and it changes the haptic section's
snippets materially. Every anchor below has been re-checked against the tree at
`089ce679` and corrected in place; the structural collisions are collected in
[What changed under this spec](#what-changed-under-this-spec) at the end, and the four
implementing PRs must read that section before touching the haptic code.

## Intent

Today's burst is a diagram: outward travel is linear (no drag), the fall accelerates
forever (no terminal velocity), the origin is a single pixel, and the in-plane spin and
the edge-on flip are **the same angle** — so every piece is a propeller and a coin at
once. One coherent model, **linear (Stokes) drag**, gives every attribute a closed
form. Piece count (46) and the one-salvo choreography are unchanged; flight goes
1800 → 2000 ms to buy the terminal-fall phase. As `Drag → 0` the fall term collapses
back to today's `G·tau²`, so this is a strict superset of the current physics.

## Kinematics

`t` is the flight clock, 0..1. Positions are fractions of **span = box WIDTH**, from the
origin; the draw layer does `x = ox + dx*span`, `y = oy + dy*span`. Angles are in
radians, y is down.

Cached once in `fan()` — not in the composable or the view, because the tests must be
able to see them:

```
vx0 = cos(angle)*speed    vy0 = sin(angle)*speed
k   = Drag * dragScale    vt  = Gravity / k        // this piece's terminal speed
mx  = muzzle*cos(angle)   my  = muzzle*sin(angle)  // where on the source patch it leaves
```

Per frame:

```
 1  tau = (t - delay) / (1 - delay)
 2  if (tau <= 0 || tau >= 1) return null        // the tau>=1 exit moves INSIDE frame()
 3  E = exp(-k*tau)                              // fraction of launch velocity left
 4  D = (1 - E) / k                              // distance a unit launch speed has covered
 5  S = 1 - E                                    // fraction of throw spent = flutter envelope
 6  theta = swayRate*tau + swayPhase
 7  dx = mx + vx0*D + swayAmp*sin(theta)*S
 8  dy = my + vy0*D + vt*(tau - D)
 9  rot = spinPhase + S*(spin + Rock*sin(theta))          // radians
10  flipAngle = flipPhase + flipRate*tau                  // its OWN axis (new)
11  widthScale = MinFlip + (1 - MinFlip)*abs(cos(flipAngle));  heightScale = 1
12  u = (tau - FadeStart)/(1 - FadeStart)
    alpha = tau < FadeStart ? 1 : 1 - u*u*(3 - 2*u)       // smoothstep tail
13  colour = palette[colorIndex % palette.size], solid, alpha from 12
14  rounded rect widthScale*width × height, corner = DRAWN width * 0.4, rotated by rot about centre
```

**No clamping is needed anywhere**: `k >= 5.4`, `1 - delay >= 0.84`, and `u` is only ever
evaluated on `[FadeStart, 1)`.

## Verified numbers

With Drag 6.0, Gravity 5.1, dragScale 0.9–1.1, speed 1.10–1.95:

- drag time constant **333 ms** of a 2000 ms flight;
- throw spent 36 % @150 ms, 59 % @300, 78 % @500, 91 % @800, 95 % @1000;
- reach = `speed/k` = **0.167–0.361 span**;
- `vt` = **0.773–0.944 span/flight** (131–161 px/s on a 340 px box);
- apex **−0.1952 span at tau 0.207 (~415 ms)** — today it is −0.16 at ~740 ms;
- worst `|dx|` **0.4058** — today the fast half exits the sides at tau ≈ 0.7;
- worst `dy` **0.6907**;
- alpha 1.0 @0.60, 0.844 @0.70, **0.500 @0.80**, 0.156 @0.90, 0 @1.0 with zero slope.

## Constants

Android `TdayConfetti*.kt` · iOS `TdayConfettiMetrics.x` · web `confetti-kinematics.ts`.

| name | value | change |
|---|---|---|
| PieceCount | 46 | KEPT (drag keeps the fast third in frame, so the cloud is already denser) |
| FlightMillis / flightSeconds / FLIGHT_MS | **2000** | was 1800 — the extra 200 ms is the terminal-fall payoff. Worst case 320 + 2000 = 2320 ms, inside the 4 s celebrate window |
| OriginX / OriginY | 0.5 / 0.28 | KEPT |
| FanStart / FanSweep | 200° / 140° | KEPT — the fan is the identity |
| **Drag** | **6.0** /flight | NEW |
| Gravity | **5.1** span/flight² | was 0.95 — *not comparable*, that was g/2 with no drag. What the eye sees is vt = 0.85 |
| **Rock** | **0.5** rad | NEW — ±29° lean in phase with sway position; zero it here if it reads busy |
| MinFlip | 0.25 | KEPT |
| FadeStart | **0.60** | was 0.55 — opaque to 1200 ms |
| MaxLaunchDelay | 0.16 | KEPT (0–320 ms). If the pop reads smeared, 0.12 is the *only* knob to try |
| MinSpeed / MaxSpeed | **1.10 / 1.95** | was 0.30 / 0.78; these are now *launch speeds*, not distances. 1.10 (not P1's 0.85) so min reach 0.167 span → slow pieces don't clump on the illustration; 1.95 caps apex at −0.195 so the web canvas doesn't clip |
| MinSpinRadians / SpinRadiansRange | **1.5 / 3.0** (±U[1.5,4.5]) | was a *rate* 3.5–12.5 rad/flight; now a total in-plane turn. Renamed because today's `MaxSpin` is a range width, not a maximum. **There is no iOS/Android spin parity bug** — every proposal that claimed one inherited a misread from the brief; spin is 3.5–12.5 on all three today. Say so in the comment. |
| sizes 5–9 × 8–13, palette (7 + accent), corner 0.4, sceneLead 320 ms / 0.32 s | | KEPT verbatim |

## The sixteen seeded draws

Exactly this order on all three clients — Android `Random(PieceCount*31L)`, iOS LCG
`0x7DA9102B`, web mulberry32 `0x7da9102b`. **Parity is in the distributions and the draw
order, never in the values**: those are three different streams and always will be. Write
the order into every `fan()` doc comment.

1. angle jitter U[0,1) — stratified: `FanStart + FanSweep*(i+r)/N`
2. speed U[1.10,1.95] — **CHANGED**
3. spin sign coin
4. spin magnitude U[1.5,4.5] rad — **CHANGED**
5. spinPhase U[0,2π)
6. **flipRate U[6,12] rad/flight — NEW** (1.9–3.8 edge-on passes, undamped, decoupled from spin; that lock is today's mechanical wobble)
7. **flipPhase U[0,2π) — NEW**
8. width U[5,9]
9. height U[8,13]
10. colorIndex int U[0,7]
11. delay U[0,0.16)
12. **dragScale U[0.90,1.10] — NEW** (narrowed from P1's 0.85–1.15 on the engineer judge's note, to widen the apex margin)
13. **muzzle U[0,0.03] span — NEW** (thumb-sized source patch, offset *along the throw angle*)
14. **swayAmp U[0.018,0.04] span — NEW** (±7–15 px)
15. **swayRate U[9,15] rad/flight — NEW** (0.7–1.2 Hz, per-piece so 46 pieces don't flutter in unison)
16. **swayPhase U[0,2π) — NEW**

## Rejected deliberately — do not re-litigate

A second/echo salvo (the per-piece `vt` spread already gives the second-wave feel); an
emerge scale-in (reads as scaling, and it is 87 % done by 40 ms anyway); a second flip
axis (makes coins); shading the colour when edge-on (the web accent arrives as an opaque
CSS string); depth cues via size or alpha; more pieces.

## Timing

46 pieces, one salvo, 2000 ms, **linear clock** — an eased clock makes the apex look like
buffering. Launch delays uniform 0–320 ms. Scene lead unchanged (Android
`CelebrationLeadMillis = 320`, iOS `sceneLead = 0.32`, web
`.tday-empty-enter-celebrating` 320 ms). Android `startDelayMillis`
(= `TdayFeedItemMotion.CelebrationStartDelayMillis` = 320) shifts burst *and* haptic
together.

Overlay timeline:

| at | what |
|---|---|
| **0 ms** | pop + throw |
| **320 ms** | last launcher leaves, scene rises |
| **~415 ms** | apex |
| **~840 ms** | scene landed, 91 % of throw spent |
| **1200 ms** | fade begins |
| **1600 ms** | alpha 0.5 |
| **2000 ms** | nothing drawn |

`FlightMillis` lives in **three** places that must move together — iOS's `.task` sleep
reads the same constant, so a flight that grows without it grows a gap where the burst
is over and the view is still ticking.

## Interruption

A burst says "this list is finished". The moment that stops being true — an undo of the
completion that started it, a task arriving from a collaborator or a sync, a task the user
types while the paper is still up — the celebration is over, and **the burst leaves over a
fade rather than between two frames**. Cutting 46 pieces out of mid-air to cancel them is
the same complaint as leaving them flying over a restored row, one layer down.

Normative, all three clients:

| | |
|---|---|
| envelope | a SECOND alpha term, `1 → 0` over **Quick** (150 ms / 0.15 s) on **Exit**, multiplied into each piece's existing `alpha(tau)` at the single draw site |
| flight clock | **untouched.** Pieces keep travelling, spinning and flipping while they fade. Freezing the flight and dissolving a still frame is not this |
| where it lives | the pure kinematics module — Android `envelopedAlpha` in `TdayConfettiKinematics.kt`, and the twins in `TdayConfetti.swift`'s `TdayConfettiKinematics` enum and `confetti-kinematics.ts` — with a case in each kinematics test: envelope 1 ⇒ unchanged alpha, envelope 0 ⇒ 0, monotonic between |
| unmount | when the envelope reaches zero, which is also what makes the next celebration a fresh run |
| reduced motion | **instant.** Nothing was ever painted (Android returns before the `Canvas`, iOS draws `Color.clear`, web returns before the rAF loop), so there is no finished state to draw and no wait to survive: the view leaves on the cancel frame, with no animation and no timer. `docs/motion.md`'s fifth idiom rule |
| the host | where the scene the burst sits in leaves on the same event, it must stay mounted for at least the envelope, or the fade is cut by the unmount above it. **Every** host that draws a celebrating scene, not just the one each client noticed first: Android wraps both its full-screen scene and the Anytime home's inline lazy item in `AnimatedVisibility` on the same Quick/Exit rung (the item needs a mount guard wider than its own visibility — `shouldMountFloaterEmptyScene` — since an item its guard has removed has no exit left); web holds all four of its scenes with `useFadeUnmount(…, DURATION_MS.quick)` and draws the departure with `.tday-empty-cancel-exit`; iOS already lingers on its removal transition at Emphasis 0.32, which outlasts the envelope, and needs nothing. A host that closes a TRACK as well as fading ink — anything drawn inline, where the feed below is waiting for the space — closes it on that same one duration and curve, or the scene's ink and its box read as two things happening rather than one leaving |

Name the token, never the number: `TdayMotionTokens.Durations.Quick` + `Easings.Exit`,
`TdayMotion.exit(duration: TdayMotion.Durations.quick)`, `DURATION_MS.quick` + `EASE.exit`.
A digit at any of these call sites moves a motion-budget counter with no headroom.

The envelope is a new term, **not a retune**: `FlightMillis`/`flightSeconds`/`FLIGHT_MS`,
`FadeStart` 0.60 and the 320 ms scene lead are pinned above and asserted by three
kinematics test files. An uncancelled burst multiplies by 1 for its whole flight and is
byte-for-byte the flight this spec already describes.

**What cancels, and what does not.** Cancellation is an ARRIVAL — a pending row landing on
the screen, counted across **all** buckets including Earlier/overdue — and never a re-read
of the "is this scope finished" predicate, which every client deliberately writes to
exclude Earlier. An undone OVERDUE task leaves that predicate exactly as it was: nothing
transitions, and only the count moves. A remote DELETE of the last task still
false-celebrates on all three clients for the reason each client's own doc comment already
gives — the cache-change signal carries no reason — and cancellation neither fixes that nor
is meant to.

There is no burst haptic on any client today, so a cancel fires and suppresses nothing. If
the section below ever lands, a cancelled burst must not fire a second one on the way out.

## Haptics

**One firm pop, at the throw, exactly once per burst, on every celebrating list, on all
three platforms, *including under reduce-motion*.** A finished list is still finished;
haptics are not motion. It replaces Today's scene haptic whenever a burst will play. The
row's checkbox tick is a separate, earlier event and is untouched.

> No Phase 9 ledger row implements this section. PRs 39a–39d are the kinematics; the
> haptic is specified here so that it is not re-derived from scratch when its own row is
> written, and so that the kinematics PRs know what shape the files have to be left in.

**Android** (`core/ui/TdayConfetti.kt`) — restructure so `if (!play) return` is the only
early exit and the **Canvas**, not the effect, is what motion gates:

```kotlin
if (!play) return
val view = LocalView.current
val motionEnabled = rememberTdayMotionEnabled()
LaunchedEffect(runKey) {
    progress.snapTo(0f)
    if (startDelayMillis > 0L) delay(startDelayMillis)
    ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CONFIRM)  // the throw
    if (!motionEnabled) return@LaunchedEffect
    progress.animateTo(1f, tween(FlightMillis, easing = LinearEasing))
}
if (motionEnabled) { Canvas(modifier = modifier) { /* ... */ } }
```

`CONFIRM`, not `LONG_PRESS` — it is what this app already means by "accepted/completed"
and is the exact sensation Today's scene haptic gave. (See
[What changed under this spec](#what-changed-under-this-spec): the delay line and the
raw `performHapticFeedback` call both have to be re-expressed against what Phase 8 left
behind, and the prerequisite this snippet was written against is no longer true.)

**iOS** — new `HapticManager.listFinished()`:

```swift
static func listFinished() {
    let generator = UIImpactFeedbackGenerator(style: .medium)
    generator.prepare()
    generator.impactOccurred(intensity: 0.9)
}
```

P1 proposed `.heavy` @0.8; two of three judges called that the loudest haptic the app
would own. `.medium` @0.9 is the decision.

The `.onAppear` **must sit on a persistent `ZStack`, never a `Group`** around the
if/else. All three judges independently caught that a `Group` distributes the modifier
and re-fires it when `.task` flips `landed` at 2 s — a second pop exactly as the burst
ends:

```swift
ZStack {
    if !reduceMotion && !landed { TimelineView(.animation) { … }.task { … landed = true } }
}
.allowsHitTesting(false).accessibilityHidden(true)
.onAppear {
    guard startedAt == nil else { return }   // one mount = one burst = one pop
    startedAt = .now
    HapticManager.listFinished()
}
```

`startedAt` is `@State`, so a SwiftUI re-init mid-flight neither restarts the flight nor
re-buzzes; the reduce-motion branch renders nothing but still receives `onAppear`.

**Web** — `hapticListFinished() { vibrate(40) }` in `src/lib/haptics.ts`, plus a
ref-guarded effect in `Confetti.tsx` with its own `[]` deps, placed **above** the drawing
effect (so it fires before that effect's reduced-motion early return) and kept out of the
drawing effect's dependency list:

```tsx
const firedRef = useRef(false);
useEffect(() => { if (firedRef.current) return; firedRef.current = true; hapticListFinished(); }, []);
```

**No vibrate queue.** P1 added module-level `patternEndsAt` + `setTimeout` because the
checkbox fires `hapticSuccess()` — which vibrates `[30,40,60]` = 130 ms — and
`navigator.vibrate` is single-channel. Measured and re-verified: `TASK_COMPLETION_TOTAL_MS`
(`tday-web/src/lib/taskCompletionTiming.ts:65`) is **840 ms** — 160 + 360 + 320, the last
term being `TASK_COMPLETION_COLLAPSE_MS = DURATION_MS.emphasis` — and it is the mutation at
that point which empties the list and mounts `<EmptyState celebrate>`. 840 ≫ 130, so the
tick has long finished. Drop the queue: it is uncleaned module state with a dangling
timer. *(The recovered proposals wrote the checkbox call as `hapticSuccess([30,40,60])`;
the real signature takes no argument — the pattern is inside the helper, and the caller is
`tday-web/src/components/ui/TodoCheckbox.tsx:62`. The recovered total was 780 ms, computed
against a 260 ms fade; the third term is the 320 ms collapse. Neither correction changes
the conclusion.)*

### Dedupe — the scene haptic yields whenever a burst will play

- **Android** `feature/todos/TodoListScreen.kt:805-809`: `LaunchedEffect(isDayDone)`
  calls `TdayHaptics.completion(view)`; it becomes
  `if (isDayDone && !celebrateEmptyState)`. **Key on `isDayDone` only** — adding
  `celebrateEmptyState` to the key means the 4 s window closing flips it true→false
  while `isDayDone` stays true, the effect re-runs, and a stray CONFIRM fires at 4 s.
  (`celebrateEmptyState` is computed at `:710` from the same `uiState` snapshot as
  `isDayDone` at `:799`, so the two always agree within a composition, on both the
  own-tap and the remote path.)
- **iOS** `Feature/Todos/TodoListScreen.swift:1771-1783`: the day-done `.onAppear`
  becomes `if !celebratesEmptyState { HapticManager.completion() }`, and
  `SoundManager.taskCompleted()` at `:1782` stays **unconditional** behind the existing
  return-suppression guard. The burst's own `.onAppear` is a child of the same overlay,
  so sound and pop still land in the same frame.
- **Web**: nothing to dedupe — there is no scene haptic on web.
- **Decided, because the product judge demanded a resolution: the iOS Overdue/Earlier
  hand-off return.** `suppressDayDoneFeedbackOnReturn` (set at `:3372`, consumed at
  `:1777`) suppresses the sound and the scene haptic on that return, but the re-mounted
  `TdayConfetti` replays the burst today, and under this spec the pop rides with it.
  **Accept and document** — a thrown burst is never silent. Do **not** thread the flag
  into `celebrate`: it is consumed and reset inside that same `.onAppear`, so gating on
  it would re-enable the burst one render later and produce a *delayed* burst. Comment at
  `:3372` and at the `celebrate:` call site (`:1769`); check Android's `TdayEmptyState`
  remount equivalent and comment it the same way.

## The pure-kinematics contract

Shared `ConfettiFrame { dx, dy, rot (radians), widthScale, alpha }`. `frame()` returns
null for `tau <= 0` **or `tau >= 1`**; the draw layer keeps its own `t >= 1` exit; export
a separate pure `alpha(tau)` so that `alpha(1) == 0` is testable without a frame.

- **Android** → new `core/ui/TdayConfettiKinematics.kt` (`ConfettiPiece` with its cached
  derivations, `confettiFan(random)`, `frame()`/`alpha()`, all constants), test at
  `app/src/test/java/com/ohmz/tday/compose/core/ui/TdayConfettiKinematicsTest.kt` — plain
  JUnit, no Robolectric; `TdayFeedItemMotionTest.kt` is the precedent.
- **iOS** → `enum TdayConfettiKinematics` in `TdayConfetti.swift`; drop `private` from
  `ConfettiPiece`/`fan()`; test at
  `ios-swiftUI/Tests/TdayCoreTests/TdayConfettiKinematicsTests.swift` — **and it must be
  added to `TdayApp.xcodeproj/project.pbxproj` or it silently never runs.**
- **Web** → new `tday-web/src/components/app/confetti-kinematics.ts`; `Confetti.tsx`
  becomes draw-only; test at **`tday-web/tests/unit/confetti-kinematics.test.ts`, NOT
  beside the source** — `vitest.config.ts` has `include: ["tests/**/*.test.{ts,tsx}"]`,
  so a test under `src/` is silently never run.

Those are the two traps, and they are the same trap: a test registered in the wrong place
compiles into nothing and CI stays green.

## Tolerances

Double (iOS, web): step 0.01, tol 1e-9. Float (Android): step **0.05**, tol 1e-4.
Monotonicity and concavity use a **strict `<` with no epsilon** on both — at step 0.05 the
tail differences are ~5e-5, and an absolute 1e-4 epsilon would make the check vacuous.
(The engineer judge's correction to P1.)

## The invariants

- **I1 — nothing before the throw, and it leaves from the muzzle patch.** For every piece
  and `t ∈ {0, delay/2, delay}`: `frame()` null. At `t = delay + 1e-4*(1-delay)`:
  non-null, `hypot(dx,dy) <= 0.031`, `hypot(dx-mx, dy-my) <= 1e-3`. `frame(p, 1.0)` null
  for every p.
- **I2 — drag: outward travel concave, reach finite.** Hand-built piece (angle 0, speed
  1.95, dragScale 1, muzzle 0, swayAmp 0, delay 0): `dx` strictly increasing, forward
  differences strictly decreasing. Pins: `dx(1/6) == 0.2054392`;
  `dx(0.999) ∈ [0.32175, 0.325]`.
- **I3 — terminal velocity approached from both sides, never crossed.** `vt == 0.85`.
  Piece A (straight up) and piece B (straight down, hand-built): A has `v₀ < 0`, strictly
  increasing, `<= vt+tol`, `v_last >= 0.985·vt` (analytically 0.9912); B has `v₀ > vt`,
  strictly decreasing, `>= vt−tol`, `v_last <= 1.015·vt` (analytically 1.0035).
- **I4 — fade schedule.** `alpha(0.599) == 1`; `alpha(0.601) < 1` — **assert at
  FadeStart ± 1e-3, never at FadeStart itself**, because `(t-delay)/(1-delay)` rounds past
  0.60 on Float; `|alpha(0.80) − 0.5| <= tol`; `alpha(1.0) == 0`; non-increasing at step
  0.005. Assert `alpha >= 1−1e-6` rather than `== 1`.
- **I5 — stays in the box, and the fan is choreography.** Every piece, every
  `t ∈ {0, 0.005, …, 1}` with a non-null frame: `−0.44 <= dx <= 0.44` (analytic 0.4058),
  `−0.22 <= dy <= 0.72` (analytic −0.1952 / 0.6907). **Derive the bound from the parameter
  ranges, never from one seed** — P1's original 0.72 was wrong for its own wider ranges
  (true 0.7485), and that is exactly the trap. Plus `MinFlip <= widthScale <= 1`,
  `0 <= alpha <= 1`; `fan()` twice → element-wise identical; count 46; angle ∈ [200°,340°)
  non-decreasing; speed ∈ [1.10,1.95]; `|spin| ∈ [1.5,4.5]`; dragScale ∈ [0.90,1.10];
  delay ∈ [0,0.16).
- **I6 (nice-to-have) — the flip really is its own axis.** With `flipRate = 0`,
  `flipPhase = π/2`, `spin = 4.5`: `widthScale == MinFlip` at every tau, while
  `rot(0.5) − rot(0.1) > 1.5` rad (analytically ~2.25).

## The web canvas geometry fix

This was the product judge's blocking risk. The web `<canvas>` is `absolute inset-0` and
clips to its own bitmap, unlike the iOS overlay and the Android `Box`; the container is
`min-h-[42vh] w-full max-w-sm`, so on a short viewport (42vh < 268 px) the apex clips the
topmost pieces at full alpha. Give it top bleed and add the bleed back into the origin so
the geometry still matches the other two:

- class `pointer-events-none absolute -top-10 bottom-0 left-0 right-0 w-full`
- `const originY = (box.height - WEB_CANVAS_TOP_BLEED) * ORIGIN_Y + WEB_CANVAS_TOP_BLEED`
  with `WEB_CANVAS_TOP_BLEED = 40`
- `span = box.width` unchanged.

## The doc comments all three files have to lose

All three long file comments currently say pieces are "thrown from a single point and
pulled back down" (`TdayConfetti.kt:26`, `TdayConfetti.swift:6`, `Confetti.tsx:8`) and two
of them say the width is scaled by "the cosine of their own spin"
(`TdayConfetti.swift:8`, `Confetti.tsx:10`). Under this spec **both become false**: there
is a muzzle patch, there is a terminal velocity, and the flip has its own axis. Rewriting
them is part of each client's PR, not a follow-up.

## Verification

Android: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest --tests '*Confetti*'`.
Web: `npm run lint && npm run test && npm run build`.

**iOS cannot be built on Linux.** Self-review for: `Double` vs `CGFloat` at the
`GraphicsContext` boundary; `GraphicsContext` is `inout` in the draw helper;
`TimelineView(.animation)` only ticks while on screen; `@State` identity (`startedAt` and
`landed` must not re-init); `.task` cancellation when the branch disappears;
`Path(roundedRect:cornerRadius:)` argument labels; `impactOccurred(intensity:)` is
iOS 13+; and the pbxproj registration.

**No golden or screenshot test of the burst exists** — verified: nothing under
`app/src/test`, `Tests/TdayCoreTests` or `tday-web/tests` renders the confetti — so the
changed fan breaks nothing. `ShouldCelebrateEmptyStateTest.kt` and the Earlier/empty-state
gating tests are logic-only and unaffected.

## Parity checklist

Tick by reading the three side by side: same 16 seeded draws in the same order · 46 pieces
and 2000 ms in all three · constants row-for-row per the table · `vx0`/`vy0`/`k`/`vt`/`mx`/`my`
computed in `fan()` · `frame()` null at `tau <= 0` and `tau >= 1` · smoothstep written out,
never via `pow` · radians everywhere, degrees only at Android's draw site · exactly one
haptic per burst, fired *before* any motion gate · haptic fires under reduce-motion and
animator-scale-off · scene haptic yields when `celebrate` (Android `:805`, iOS `:1771`)
with `SoundManager` still unconditional · scene lead still 320/0.32/320 · all invariants in
all three test files, with the box bounds derived from the parameter ranges · a cancelled
burst fades on Quick/Exit as a second alpha term with the flight clock untouched, unmounts
instantly under reduced motion, and its host keeps the layer alive for at least the
envelope.

## What it feels like

**0 ms** — the pop lands on the same frame the first pieces leave a thumb-sized patch;
over a third of the throw is spent in 150 ms, so it reads as a snap, not a bloom.
**~300 ms** — the cloud has nearly stopped growing, the highest pieces turn over at apex,
spin is dying, the first sway starts, and the scene begins rising at 320 ms as the last
piece leaves. **~900 ms** — the scene has landed, everything left is falling at its own
terminal speed, heavier-feeling pieces ahead, swaying ±7–15 px at ~1 Hz, leaning into each
sway, turning edge-on at an unrelated rhythm. This is the paper moment, and it happens
*over* the finished illustration. **~1800 ms** — three-quarters faded, the lowest pieces
drifting across the copy line; gone at 2000 without an edge you could point to.

## What changed under this spec

Everything above is the panel's, carried over with its anchors re-checked. This section is
not: it is what the tree did between the panel running and this file landing, written down
so the four implementing PRs do not discover it one at a time. Phase 8 put a reduced-motion
resolver on all three clients, and the haptic snippets above predate it.

- **The Android prerequisite the snippet was written against is now false.** The panel
  verified that `TdayEmptyState` calls `TdayConfetti(play = celebrate, …)` unconditionally
  — still true, at `TdayEmptyState.kt:290-294` — and concluded the effect is therefore
  reached under animator-scale-off. It is not: `TdayConfetti` now opens with
  `if (!play || !motionEnabled) return`, so the whole composable leaves before any effect
  exists. That is precisely the restructure the snippet prescribes, and it is now load-bearing
  rather than tidy: without it the pop cannot fire at all for a user at 0x.
- **The Android lead is on the motion clock and the pop has to follow it there.** The
  snippet writes `delay(startDelayMillis)`; the tree writes
  `scaledDelay(startDelayMillis, motionScale)`, and `motionScale` is what the user's setting
  moves. Keep `scaledDelay`. At 0x the lead collapses to nothing and the pop lands at once,
  which is right and is the fifth idiom rule rather than an exception to it — there is no
  scene rise for the pop to wait behind, and a wait with nothing moving in it is the defect
  Phase 8 exists to remove.
- **Android already has a haptic vocabulary and the snippet reaches past it.**
  `ViewCompat.performHapticFeedback(view, …CONFIRM)` is what the scene haptic used to be
  written as; it is `TdayHaptics.completion(view)` now, and
  `tday-web/tests/guardrails/haptic-vocabulary.test.ts` is what holds that. Call the
  vocabulary, not the platform. The decision the panel made — CONFIRM, not LONG_PRESS — is
  unchanged, because `completion()` is where CONFIRM lives.
- **The iOS snippet reads the preference directly and must not.** `if !reduceMotion` is a
  raw environment read; `TdayConfetti` now asks `tdayAnimation.isEnabled`, the single
  resolver. The `ZStack`-not-`Group` rule survives intact and is, if anything, more
  necessary: the body today is a bare `if/else` returning `Color.clear` on one arm, the
  `.onAppear` sits on the *animating* arm only (so a reduce-motion user would get no pop at
  all), and `landed` swaps the arms at 2 s with nothing persistent above them. `guard
  startedAt == nil` is also missing today — `.onAppear { startedAt = .now }` is unguarded.
- **`listFinished()` is a ninth name for an event the vocabulary already has, and the
  vocabulary is a guarded cross-client contract.** `haptic-vocabulary-ios.test.ts` pins
  eight events *in declaration order* and asserts the same list against Android's
  `TdayHaptics.kt`, so a name added to one client alone fails the parity half on the spot.
  And the event is already there: `HapticManager.completion()` is
  `UINotificationFeedbackGenerator(.success)`, its doc string names "the day's last task
  cleared" among its cases, and Android's twin is `CONFIRM` — which is the pulse the panel
  chose. The `.medium` @0.9 decision was an argument about loudness against P1's `.heavy`
  @0.8, and `.success` settles that argument the same way without a ninth entry. So the
  recommendation this file makes to the haptic row is **reuse `completion()` on all three
  clients** and leave the vocabulary at eight; if a burst genuinely needs a pulse of its
  own, that is a vocabulary change, and it moves `haptic-vocabulary-ios.test.ts`,
  `haptic-vocabulary.test.ts` and both clients in one commit. (The member the panel spelled
  `taskCompleted()` never existed on `HapticManager` at all — that is `SoundManager`'s.)
- **The web haptic effect fires at mount, which is not always the throw.** `Confetti`
  takes `startDelayMs` and starts the flight at `performance.now() + startDelayMs`;
  `EmptyState.tsx:176` hands it `celebrationStartDelayMs`, which is 320 on the two screens
  that pass one (`AllTasksTimelineContainer.tsx:320`,
  `NativeFloaterTaskHomeDashboard.tsx:218`) and 0 on the overlay callers. An effect with
  `[]` deps therefore pops up to 320 ms before the first piece leaves on exactly the
  screens the spec's own timeline is written about. Either the effect waits out
  `startDelayMs` on a cleaned-up timer, or the haptic moves to where the flight starts —
  the row that implements the haptic owns that call, and it is the one place where the web
  snippet above is not simply transcribable.
