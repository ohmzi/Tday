docs/verification/README.md:

```markdown
# Device verification

Everything else in this repository is proved by something that runs without a human in front of
it: `npm run test`, `:app:testDebugUnitTest`, the guardrail suite under `tday-web/tests/guardrails/`,
the workflows in `.github/workflows/`. Motion is the part that is not, and this directory is where
that gap is written down instead of remembered.

The gap is not an oversight, it is what the tooling is. Compose motion has no Robolectric and no
screenshot harness here; `android.yml:176` runs `:app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`,
so `app/src/androidTest/` is compiled and never executed — those tests need an emulator that CI does
not have. jsdom computes no layout and implements no `Element.animate`, so a vitest run can prove a
class is applied and a timer fires, and can prove nothing about whether a row collapsed or teleported.
iOS `xctest` proves a Swift file compiles and a state machine settles; it has no opinion about whether
a curve reads as a slide or as a cut. A change can be green on every gate the repo owns and still be
the exact defect it was meant to fix.

So there is a class of change that is correct or wrong only in someone's eye. A file in this directory
is that eye-check, written down.

## Why the row is written by the PR that creates the need, not by the person running the pass

The obvious alternative — assemble a verification list at the phase merge from `git log` — fails for a
boring reason: the only person who knows what to look at is the person who just changed it, and they
know it for about a day. Reconstructed a week later, the check reads "check the toast exit". That costs
three minutes of poking at a toast and proves nothing, because nobody remembers which of the three
dismiss paths was the broken one. Written while the diff is still open, the same check names the screen,
the gesture, the thing to watch and the specific failure it is hunting, and it costs fifteen seconds.

Hence the rule, which is item 4 of the plan's definition of "done": a PR that needs a device check
appends its row to `docs/verification/phase-N-device-pass.md` **in the same commit as the code change**,
unticked. Not in a follow-up commit, not in the PR description, not in an issue. A PR that needs a check
and does not carry its row is not finished, however green CI is — the check has already been lost by then,
it just has not been noticed yet.

## The row format

Four fields, because four fields are what make a check take fifteen seconds instead of three minutes:

```
- [ ] **PR 22b · web · Feed placement** — Today, 3+ tasks.
      Do:     tick the middle task.
      Watch:  rows below SLIDE up over 320 ms; they do not jump.
      Fails:  the gap closes on one frame, or the row fades while its space is still open.
```

The header names the PR, the client and the behaviour, so a failure can be traced to a diff without
reading the file. The clause after the em dash is the **setup** — the state the app has to be in before
the check means anything. It is there because half the real cost of a device pass is getting to the right
screen with the right data, and "Today, 3+ tasks" is the difference between a check that gets run and one
that gets skipped.

`Do:` is one gesture. If it takes two sentences, it is two rows, or it is a row with the paths folded into
the single `Do:` line — the Phase 1 Earlier-scene row names four entry paths for one observation rather
than becoming four near-identical rows.

`Watch:` is the positive statement, **with its number**. "Rows slide up over 320 ms" is checkable;
"rows animate" is not, because every wrong version of this animates too. The number is what lets an
observer separate "fast" from "instant", which is the distinction almost every row in this programme
turns on.

`Fails:` is the field people leave out and the field that does the work. A check with no stated failure
mode passes by default: a reviewer at the end of a long pass sees something move and ticks the box.
Naming the wrong outcome — *the gap closes on one frame* — turns the check from a vibe into a question
with an answer. If a row cannot state what failure looks like, the change probably did not need a device
check at all.

Rows use continuation lines indented under the bullet so each check stays one Markdown list item and the
checkbox still renders.

## What actually requires a device check

A device check is **required** when the change alters what moves on screen and no gate can observe it:
any Compose motion change, for the reasons above; any web layout-collapse or FLIP change, because jsdom
computes no layout; any gesture-feel change — swipe tracking, drag lift, rubber-band, drop settle; any
easing swap at a constant duration, where the gate sees an identical number and the eye sees a different
motion; and anything driven by two clocks at once, such as a keyboard and a sheet, or a burst and a scene.

A device check is **not** required for a constant rename at an identical value; for a pure-function
extraction that lands with a JVM or vitest test; for the deletion of provably unreachable code; or for a
class-string or prop edit whose presence a guardrail asserts and whose effect is binary — an exit exists
or it does not. Those are cases where something in CI can already tell the two outcomes apart, and a
device row for them is pure cost.

The asymmetry is deliberate and worth defending. Writing a row costs a minute; running one costs fifteen
seconds; the entire programme budgets about 105 minutes of eye-time across all its passes. Rows are cheap.
A phase pass that swells to an hour is not, because a pass that long stops being run, and an unrun pass is
worse than no pass — it is an unrun pass with a tick next to it. If a gate can see the difference, the gate
owns it.

Phase 0 has no file in this directory at all, and that is the correct answer rather than an omission: every
one of its eleven rows is a state machine with a JVM or vitest gate, so the phase ships on unit tests alone.

## iOS: three cycles, for the whole programme

An Android eye-check costs a cable. An iOS eye-check does not. There is no local iOS compile on the
development machine, a Debug simulator build proves nothing about Release configuration, and getting a
build onto a phone costs a `develop → master` merge, a version bump, a tag, a GitHub release and a
TestFlight build. That is the phase-merge machinery — which means one iOS cycle per phase merge, and
never, under any circumstances, a cycle spent on a single PR.

iOS visual checks are therefore batched into exactly three cycles for the entire programme, and the
device-check batches name which ones:

**Phase 5/6 (TF1)** — the first time anyone looks at iOS at all. Twelve iOS rows have shipped by then on
CI-green-plus-guardrail alone: Phase 2's batch-1 sweeps (PRs 16, 44 and 45) and Phase 3's semantic haptics
and press depth (PR 10). This is the pass that runs the parity pairs side by side across all three clients,
and it is the longest in the programme at ~30 minutes.

**Phase 8 (TF2)** — Phase 6's parity work whose iOS third did not make the TF1 build, plus Phase 7's iOS
batch 2 (PR 26), checked with Reduce Motion and VoiceOver switched on.

**Phase 9 (TF3)** — Phase 8's five iOS amplitude decisions (PR 35b), and Phase 9's confetti, skeletons,
sheet drag-to-dismiss and cold-launch work.

This has a consequence for where rows get filed. A PR whose only outstanding check is an iOS eye-check
does not open a file for its own phase, because no cycle is spent at its own phase merge. Its row goes into
the device-pass file of the phase whose merge spends the cycle: PR 44's rows are written during Phase 2,
into `phase-5-device-pass.md`. The row is still written while the code is fresh — it simply waits in the
file that will actually be opened. The ledger's `Gate` column carries the cycle label (`TF1`, `TF2`, `TF3`)
so the owning cycle is visible from the ledger without reading this directory; where an iOS third misses its
merge window it trails to the next cycle rather than earning one of its own.

An iOS change that is merged to `develop` and CI-green is not "unscheduled follow-up". It is **unverified**,
which is a different and much cheaper state, and a row sitting unticked three phases ahead is that state
made visible instead of forgotten.

One thing that looks like a shortcut and is not: `gh workflow run ios-testflight.yml` on any branch gives a
Release-configuration archive, sign and export with no upload possible (`ios-testflight.yml:36-71`, `:471`).
That is the only thing in the repo that compiles iOS the way it ships, and it is worth running once mid-phase
on `develop` — but it answers "does the Release archive build", not "does the sheet slide". It is not a cycle
and it discharges no row here.

## One file per pass

Files are named `phase-N-device-pass.md`. Phases 5 and 6 are run as a single sitting — the batch is headed
"Phase 5/6" for that reason — so rows from both phases go into `phase-5-device-pass.md` and there is no
`phase-6-device-pass.md`. Phases with no pass (0, and 2, whose iOS rows wait for TF1) have no file.

## How a file is closed out

A `phase-N-device-pass.md` is created by the first PR of the phase that needs a check, grows one row at a
time as the phase lands, and is closed at the phase merge — the `develop → master` PR that ends the phase.
That merge bumps the version, tags, builds the signed APK, cuts the GitHub release, pushes the image and
fires `ios-testflight.yml`. **That release is the build the pass is run against.** Not a local debug build,
not a branch build: the thing being verified has to be the thing that shipped, or the pass is verifying
something nobody will ever install.

Closing out, in order:

1. Install the phase's release build on each client the file names — the signed APK, the TestFlight build,
   the deployed web image.
2. Work the file top to bottom. Read the setup, do the `Do:`, watch for the `Watch:`, and change `[ ]` to
   `[x]` only after actually observing it. If the setup cannot be reproduced, the row is not ticked and not
   guessed at — it is rewritten with a setup that works, which is itself a useful finding about the row.
3. A row that fails is neither ticked nor deleted. It becomes a new row in `docs/motion/LEDGER.md` against
   the phase that will fix it, worded as the failure was actually observed, and its device row moves to that
   phase's file. A failed check producing a tracked row is the whole point of running the pass; a failed
   check quietly dropped is worse than never having written it.
4. When every row is ticked, add a closing line at the top of the file recording the release tag it was run
   against, the date, and the devices used — for example `Passed against v1.14.0 on Pixel 7 (API 34),
   iPhone 13 (iOS 18.4) and Chrome 131, 2026-10-02.` The file then stays in the repository unchanged. It is
   the record of what was looked at, on which build, on which hardware, and it is the only thing that makes
   "this was verified" a claim anyone can check afterwards.
5. The phase is not finished while an unticked row remains. The only way a row leaves this file unticked is
   by becoming a ledger row under step 3.
```