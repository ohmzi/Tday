# Design note: a task row's controls sit on the title's first line

Status: accepted, shipped.
Scope: web (React), Android (Compose), iOS (SwiftUI). Backend: **no change**.

The rule, in one sentence: **in a task row, the leading check control and any trailing
annotation are centred on the title's FIRST LINE, not on the middle of the text column.**

This note exists because the defect it fixes is invisible until it is not. Every feed in
the app draws the same shape — a round toggle, a title that may wrap, a list or priority
mark at the far end — and every one of them originally stacked that shape with a
centre-vertical alignment. That is indistinguishable from correct for as long as the
title is one line. The moment it is two, the toggle settles on the text column's
*middle*, which on a wrapped title is the **gap between line one and line two**. The
mark that is supposed to be the line's bullet ends up floating beside nothing, and the
trailing flag floats with it.

Eleven rows across three clients had it. A fix applied to the screen it was reported on
would have left it in ten, which is why this is a note and not a commit message.

---

## 1. What "inline with the first line" means, precisely

The control's **vertical centre** is on the first line box's **vertical centre**.

It does **not** mean top-aligning the control against the text. Top-aligning a 48 dp tap
target against a 24 sp line box puts the glyph's centre 12 dp below the line's, which
reads *low* rather than aligned — and the error is not even constant, because one
measurement is in dp and the other in sp. On Android it moves with the user's font
scale, and it moves **non-linearly**: since Android 14, sp above 1× converts through a
curve, so a 24 sp line is 28 dp at `fontScale = 1.5` and not the 36 dp anybody deriving
it by hand would have written. Any fix expressed as a constant is a fix that is correct
on one device at one setting.

## 2. What must not change, and did not

- **A single-line row is unmoved, by construction.** With one line, the text column's
  centre *is* its first line's centre, so centring the control on one is exactly what
  centring it on the other was already doing.
- **The tap target is untouched.** Nothing shrinks a control's box. Where the control is
  taller than the line (Android's 48 dp target, Completed's 28 dp toggle), the *text*
  takes the offset; where it is shorter (the car surface's 10 dp priority dot, web's
  20 px circle), the control takes it. Both directions are the same formula — and on
  Android every leading control asks for its own `topInsetFor` whether or not it needs
  one today, because which of the two is taller is not a property of the row. It is a
  property of the row *at the reader's font scale*: Completed's 28 dp toggle is the
  taller element at 1× and the shorter one past about 1.5×, where a 24 sp line box has
  grown past 28 dp. The call returns 0 dp in the common case, so nothing moves there.
- **Rows with a floor or a fixed height keep their content centred in it.** Several rows
  are given a height larger than a one-line row's content — Android's
  `CompletedSwipeRowHeight` (56 dp fixed), `TaskRowMinHeight` (58 dp), web's floater
  `min-h-[54px]`. Stacking those from the top alone would have lifted every short row
  off its own centre: the common case paying for the fix to the wrapped one. Each of
  them therefore keeps the *block* centred and does the top-stacking inside it —
  `Modifier.wrapContentHeight(Alignment.CenterVertically)` on Android, a content wrapper
  inside an `items-center` row on web.

## 3. What moves and what stays, at the trailing end

The user named the checkbox. The flag is the same claim and moves with it — leaving it
centred would have looked like a half-done fix, because it is one.

| Trailing element | Verdict | Why |
|---|---|---|
| List indicator, priority flag, completed-at mark | **moves to the first line** | An annotation **on the title**. It reads as part of the sentence, so it reads with the sentence's first line. |
| Web's desktop hover toolbar (edit/copy/delete) | **stays centred on the row** | A menu for the whole task, not a mark on its title. It is 28 px against a 20 px line, so putting it on the first line would stand it proud of a single-line row's box. It stays centred by being positioned against a `self-stretch` box. |
| `TodayTodoRow`'s delete `IconButton` (Android) | **stays centred on the row** | Same argument: a full-size action affordance for the row. |
| The car surface's trailing check glyph | **stays centred on the row** | It is what the whole card *does* when pressed, drawn at the card's scale for a driving surface. |
| The timeline drag preview's marks | **stays centred** | Its title is `maxLines = 1` and cannot wrap, so there is no second line to float between. |

The split is: **marks that belong to the title follow the title; affordances that belong
to the row follow the row.**

## 4. The three idioms

They are not a divergence. Each is the client's own existing answer to the question, and
every one of them was already in the tree before this change — the work was extending
each idiom to the rows that had not adopted it.

### iOS — `.firstTextBaseline` plus an alignment guide

```swift
HStack(alignment: .firstTextBaseline, spacing: TodoTimelineMetrics.minimalRowContentSpacing) {
    Button { … } label: { … }
        .alignmentGuide(.firstTextBaseline) { dimension in
            dimension[VerticalAlignment.center] + TodoTimelineMetrics.minimalRowBaselineNudge
        }
```

A `Button` holds no text, so it reports no text baseline and SwiftUI would align it by
its **bottom edge** — a good half-line low. The guide hands back its centre instead,
nudged by the distance from a line's centre to that line's baseline. `TodoListScreen`
has shipped this since the timeline learned to wrap; `CompletedScreen`, `CalendarScreen`
and `ScheduledTaskHomeScreen` now do the same, and so does the widget (which arrived at
it independently).

The nudge is spelled twice — `TodoTimelineMetrics.minimalRowBaselineNudge` for the
feature rows and `TdayTaskRowMetrics.checkBaselineNudge` for `Core/UI`, because a core
metric may not import a screen's constant — and `TdayTaskRowSkeletonTests` pins the two
equal so they cannot drift into two nudges for one gesture.

### Web — a line-height box

```tsx
<div className="flex h-5 shrink-0 items-center">{/* the control */}</div>
…
<p className="… leading-5 …">{title}</p>
```

`h-5` is `leading-5` written as a height. The pairing is the whole technique, so both
halves are asserted together: a row that kept the box and let its title drift to
`leading-6` would be aligned to a line it no longer draws. `TaskRowSkeleton` was already
built this way and is why 20 px is the number both halves read.

Bare `items-start` is **not** the technique and is the trap worth naming. It happens to
be right while the control is exactly 20 px — and the recurring-task variant is 21.6 px,
which top-aligned hangs 1.6 px below the line it is meant to sit on.

### Android — a derivation

Compose can express neither directly, so it derives. `taskRowFirstLineAlignment(density,
titleStyle, controlHeight)` in `core/ui/TdayTaskRowSkeleton.kt` returns the first line's
centre and an inset for anything of a known height; rows stack `Alignment.Top` and drop
each element onto that centre.

At `fontScale = 1` with the shipped `titleMedium` (24 sp) and a 48 dp toggle it returns
exactly the **12 dp `TodoListScreen` had hand-written** under the comment "top pad
centres the first title line against the (taller) toggle". That is the evidence the
derivation is the existing decision rather than a new one: the one row that was already
right does not move by a pixel, and becomes right at every other font scale as well.

Deriving rather than declaring is also what satisfies `FeatureDimensBudgetTest`, which
holds every file under `feature/` at zero anonymous `.dp`. A value computed from a text
style is neither a rung nor a named constant, and is better than both.

## 5. Where it is enforced

| Claim | Test |
|---|---|
| The arithmetic, swept across seven font scales and six control sizes | `TaskRowFirstLineAlignmentTest` (Android, JVM) |
| The set of Android files that derive a first line, **how many rows each declares**, and that every one of them stacks `Alignment.Top` | `TaskRowFirstLineAlignmentTest` (source walk) |
| Each Android row insets the control it derived against, and every trailing mark in it carries a first-line inset | `TaskRowFirstLineAlignmentTest` (per-row, structural) |
| iOS alignment + guide on every row, and no centred task-row `HStack` left | `tests/guardrails/task-row-first-line-alignment.test.ts` |
| Web's `h-5` box paired with `leading-5`, `self-stretch` on the trailing box, the hover toolbar still centred | same file |
| iOS's two spellings of the baseline nudge agree; every skeleton set carries the alignment *and* the guide | `TdayTaskRowSkeletonTests` |

None of these measures a pixel, and none can: jsdom lays nothing out, a JVM test has no
layout pass, and there is no emulator or simulator in the gate. They assert the two
things that decide the layout — which alignment each row asks for, and what the box it
puts its control in is derived from.

## 6. Known non-goals

- **Android's Completed row is still a fixed 56 dp** with `maxLines = 2`, so a
  two-line title still squeezes the completed-at line. That is a pre-existing
  constraint, unrelated to alignment, and changing a row's height is a larger visual
  change than was asked for.
- **`TdayTaskRowSkeleton` is faithful to one row, not to every feed that draws it.**
  The placeholder is built against `TodayTodoRow`, and top-stacking grows it from 63 dp
  to 69 dp per row (the 12 dp title inset joins the row's height: `max(48, 12+24+18)` is
  54, not 48). A `TodayTodoRow` with a subtitle grows by the same 6 dp and still matches
  exactly; Completed's rows cannot follow, because `CompletedSwipeRowHeight` fixes them
  at 56 dp. That handoff was already 7 dp out before this change — the skeleton group
  draws a hairline and 6 dp of spacing under every row, and the Completed feed draws
  neither between rows of the same day — so no inset makes one placeholder exact against
  three different rows, and parameterising it per feed would not close the gap it is
  blamed for.
- **Both widgets were already correct** and are untouched: the Android Glance row
  top-aligns with a 1 dp nudge against a 14 dp ring, and the iOS widget already used
  `.firstTextBaseline` with the same 5 pt guide. Glance has no `TextStyle` or `Density`
  to derive from, and `feature/widget/` is deliberately outside the `TdayDimens` scale.
