import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * The root feeds' action cluster: two buttons, each with one destination, and the Completed
 * archive reached from the feed rather than from the bar.
 *
 * iOS had drifted on both counts. Its ellipsis became a `Menu` on the Anytime feed so that
 * Completed could hang off it, which made the one bar control that looked identical to its
 * neighbour behave unlike it (a `Menu` never sees the press, so no haptic and no sink), named it
 * "More" for VoiceOver while it had two destinations and kept that name after, and left the
 * Anytime feed as the only floater surface across the three clients with no Completed tile in it.
 *
 * Nothing caught any of that, and nothing would catch it coming back: there is no local iOS
 * compile here and CI's `xctest` has no opinion about which container a destination lives in.
 * What a static read *can* see is exactly what these defects were — a construct present that
 * should not be (`Menu`), a construct absent that should be (the tile) — plus one piece of
 * arithmetic that decides the shape and can be done here in full.
 *
 * That arithmetic is the reason the fix is a feed tile and not a third bar button, and it is the
 * part worth a function rather than a grep. `searchTrailingInset` reserves room for a fixed
 * *number* of 56pt circles; the search capsule's trailing edge is pinned to it. Render one more
 * button than the inset was solved for and the capsule slides under it while the docked title,
 * which is `.lineLimit(1).fixedSize()` with no ellipsis fallback, clamps at half size and runs on
 * underneath. `dockedTitleRoom` below is that reserve, and the cluster count is asserted against
 * the constant instead of against a number typed twice.
 */

const MONO = resolve(__dirname, "..", "..", "..");

const IOS_HEADER = resolve(MONO, "ios-swiftUI/Tday/Core/UI/RootFeedHeroHeader.swift");
const IOS_TODO_LIST = resolve(MONO, "ios-swiftUI/Tday/Feature/Todos/TodoListScreen.swift");
const ANDROID_HEADER = resolve(
  MONO,
  "android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/RootFeedHeroHeader.kt",
);
const ANDROID_TODO_LIST = resolve(
  MONO,
  "android-compose/app/src/main/java/com/ohmz/tday/compose/feature/todos/TodoListScreen.kt",
);
const WEB_HEADER = resolve(MONO, "tday-web/src/components/app/RootFeedHeroHeader.tsx");
const WEB_FLOATER_DASHBOARD = resolve(
  MONO,
  "tday-web/src/features/floater/component/NativeFloaterTaskHomeDashboard.tsx",
);

/**
 * Reads a source file with its comments removed.
 *
 * These files argue at length and the arguments quote the constructs asserted about — the comment
 * above the ellipsis says the word `Menu` three times explaining why there no longer is one.
 * Stripping first means every assertion below is about code.
 */
function readCode(file: string): string {
  return readFileSync(file, "utf8")
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .split("\n")
    .filter((line) => {
      const trimmed = line.trimStart();
      return !trimmed.startsWith("//") && !trimmed.startsWith("*");
    })
    .join("\n");
}

/** The brace-delimited block opening after `anchor`, anchor included. */
function blockAfter(source: string, anchor: string): string {
  const anchorAt = source.indexOf(anchor);
  if (anchorAt < 0) return "";
  const open = source.indexOf("{", anchorAt);
  if (open < 0) return "";

  let depth = 0;
  for (let i = open; i < source.length; i++) {
    if (source[i] === "{") depth += 1;
    else if (source[i] === "}") {
      depth -= 1;
      if (depth === 0) return source.slice(anchorAt, i + 1);
    }
  }
  return "";
}

/** A `static let name: CGFloat = 42` out of `RootFeedHeroHeaderMetrics`. */
function iosMetric(source: string, name: string): number {
  const match = source.match(new RegExp(`static let ${name}: CGFloat = (-?[\\d.]+)`));
  if (!match) throw new Error(`RootFeedHeroHeaderMetrics.${name} not found`);
  return Number(match[1]);
}

interface HeaderMetrics {
  horizontalPadding: number;
  barButtonSize: number;
  barButtonSpacing: number;
  compactSunBox: number;
  titleGap: number;
  /** How many 56pt circles `searchTrailingInset` was solved for. */
  clusterButtons: number;
}

function readHeaderMetrics(source: string): HeaderMetrics {
  const horizontalPadding = iosMetric(source, "horizontalPadding");
  const inset = source.match(
    /static let searchTrailingInset: CGFloat =\s*horizontalPadding \+ \(barButtonSize \* (\d+)\) \+ \(barButtonSpacing \* \d+\)/,
  );
  if (!inset) throw new Error("searchTrailingInset is no longer the padding + N buttons + N gaps form");

  return {
    horizontalPadding,
    barButtonSize: iosMetric(source, "barButtonSize"),
    barButtonSpacing: iosMetric(source, "barButtonSpacing"),
    compactSunBox: iosMetric(source, "compactSunBox"),
    titleGap: iosMetric(source, "titleGap"),
    clusterButtons: Number(inset[1]),
  };
}

/**
 * Points the docked (scrolled-down) title has to itself, at a given screen width and a given
 * number of buttons in the trailing cluster.
 *
 * This is `RootFeedHeroHeaderMetrics.titleScales`' `compactRoom` term, with the cluster size lifted
 * out as a parameter so the cost of one more button can be priced rather than guessed:
 *
 *   room = width
 *        − searchTrailingInset(n)   the reserve the search capsule's trailing edge is pinned to
 *        − barButtonSize            the folded capsule itself, which sits inside that reserve
 *        − (sunLeading + compactSunBox + titleGap)   the docked leaf/sun to the title's left
 *        − titleGap                 breathing room on the title's right
 *
 * `sunLeading` is `horizontalPadding + 2`, spelled out here because it is a derived constant and
 * this function takes only the primitives.
 */
export function dockedTitleRoom(m: HeaderMetrics, width: number, buttons: number): number {
  const searchTrailingInset =
    m.horizontalPadding + m.barButtonSize * buttons + m.barButtonSpacing * buttons;
  const sunLeading = m.horizontalPadding + 2;
  return (
    width -
    searchTrailingInset -
    m.barButtonSize -
    (sunLeading + m.compactSunBox + m.titleGap) -
    m.titleGap
  );
}

describe("root feed header — the trailing cluster", () => {
  const ios = readCode(IOS_HEADER);
  const metrics = readHeaderMetrics(ios);
  const trailingActions = blockAfter(ios, "private func trailingActions(width: CGFloat)");

  it("renders exactly the number of buttons searchTrailingInset reserves room for", () => {
    const rendered = trailingActions.match(/RootFeedHeaderCircleButton\(/g) ?? [];

    // Not "two" typed twice: the inset is the reserve and the cluster is what sits in it, so the
    // invariant is that they agree. Adding a third control to this bar without widening the inset
    // is the regression, and widening it is what the arithmetic below refuses.
    expect(rendered).toHaveLength(metrics.clusterButtons);
    expect(metrics.clusterButtons).toBe(2);
  });

  it("prices a third bar control, which is why Completed is a feed tile instead", () => {
    // At rest the docked title is `L("Floater")` at 40pt SF Rounded Heavy — on the order of 150pt
    // wide, and wider in it/ru ("Fluttuante", "Плавающие"). Two buttons already leave it under that
    // at the narrowest supported width, which is what `minTitleScale` is for. Three do not leave it
    // room to be a title at all.
    expect(dockedTitleRoom(metrics, 360, 2)).toBe(92);
    expect(dockedTitleRoom(metrics, 390, 2)).toBe(122);

    expect(dockedTitleRoom(metrics, 360, 3)).toBe(28);
    expect(dockedTitleRoom(metrics, 390, 3)).toBe(58);

    // A third button costs 64pt — one circle and its gap — and at 360pt that is more than two
    // thirds of everything the title has.
    expect(dockedTitleRoom(metrics, 360, 2) - dockedTitleRoom(metrics, 360, 3)).toBe(
      metrics.barButtonSize + metrics.barButtonSpacing,
    );
  });

  it("gives the ellipsis one destination on iOS, with the press feedback its twin has", () => {
    // The defect this file exists for: `Menu` in this header made the ellipsis a chooser, and a
    // `Menu` label never receives the press, so that one control answered neither the haptic nor
    // the 0.94 sink `TdayToolbarButtonStyle` gives the button beside it.
    expect(ios).not.toMatch(/\bMenu\b/);

    const ellipsis = trailingActions.slice(trailingActions.indexOf('RootFeedHeaderCircleButton(icon: "NavEllipsis"'));
    expect(ellipsis).toContain("HapticManager.buttonPress()");
    expect(ellipsis).toContain("onOpenSettings()");
    expect(ios).toContain(".buttonStyle(TdayToolbarButtonStyle())");
  });

  it("names the ellipsis for where it goes, on all three clients", () => {
    // "More" over a one-destination control describes the glyph, not the outcome, and the label is
    // the whole of what a VoiceOver/TalkBack user gets.
    expect(trailingActions).toContain('.accessibilityLabel("Settings")');
    expect(trailingActions).toContain('.accessibilityLabel("Create list")');

    const androidCluster = readCode(ANDROID_HEADER);
    expect(androidCluster).toContain("contentDescription = stringResource(R.string.settings_title)");
    expect(androidCluster).not.toContain("R.string.action_more)");

    expect(readCode(WEB_HEADER)).toContain("aria-label={settingsAriaLabel}");
  });
});

describe("the Anytime feed's Completed entry", () => {
  const ios = readCode(IOS_TODO_LIST);

  it("sits between the inline empty scene and My Lists, as it does on Android", () => {
    const tileAt = ios.indexOf("FloaterTaskHomeCompletedCard(onTap: onOpenCompleted)");
    const emptySceneAt = ios.indexOf("if showInlineFloaterTaskHomeEmpty {");
    const myListsAt = ios.indexOf("if !floaterTaskHomeListRows.isEmpty {");

    expect(tileAt).toBeGreaterThan(-1);
    expect(emptySceneAt).toBeGreaterThan(-1);
    expect(tileAt).toBeGreaterThan(emptySceneAt);
    expect(tileAt).toBeLessThan(myListsAt);

    // Android's order, from the same feed builder, asserted rather than trusted.
    const android = readCode(ANDROID_TODO_LIST);
    const androidTileAt = android.indexOf('key = "floater-completed-entry"');
    const androidSceneAt = android.indexOf("emptyScene?.invoke(this)");
    const androidListsAt = android.indexOf('key = "floater-my-lists-header"');
    expect(androidTileAt).toBeGreaterThan(androidSceneAt);
    expect(androidTileAt).toBeLessThan(androidListsAt);
  });

  it("is always present on the Anytime home, not gated on anything having been completed", () => {
    // An archive you can only reach once it is non-empty is an archive you cannot learn exists.
    // Android gates its tile on `isFloaterTaskHomeScreen` alone; so does this.
    const gate = ios.slice(0, ios.indexOf("FloaterTaskHomeCompletedCard(onTap: onOpenCompleted)"));
    expect(gate.lastIndexOf("if isFloaterTaskHomeScreen {")).toBeGreaterThan(
      gate.lastIndexOf("if showInlineFloaterTaskHomeEmpty {"),
    );
  });

  it("carries no count, matching Android's call site", () => {
    const card = blockAfter(ios, "private struct FloaterTaskHomeCompletedCard: View");
    expect(card).not.toContain("count");

    // The rule that would otherwise apply: `motion-reachability-ios.test.ts` requires any feed
    // count in this file to roll rather than swap. A countless tile owes it nothing — and this
    // assertion is what keeps a later "just add the number" from owing it silently.
    expect(card).not.toContain(".contentTransition(");

    const androidTile = blockAfter(readCode(ANDROID_TODO_LIST), 'key = "floater-completed-entry"');
    expect(androidTile).not.toMatch(/\bcount = /);
  });

  it("matches the shared tile geometry and the accent the other clients pin", () => {
    const card = blockAfter(ios, "private struct FloaterTaskHomeCompletedCard: View");

    // 70pt tall, 26pt radius — `FloaterTaskHomeListCard` beside it, Android's `CategoryCard`, and
    // web's `h-[70px] rounded-[26px]` all agree, and the tile sits in that same single column.
    expect(card).toContain("RoundedRectangle(cornerRadius: 26, style: .continuous)");
    expect(card).toContain("minHeight: 70, maxHeight: 70");

    // 0x719F84, spelled in sRGB components because `Color(hex:)` is a private extension in each of
    // the two files that declare one.
    expect(card).toContain("red: 113.0 / 255.0");
    expect(card).toContain("green: 159.0 / 255.0");
    expect(card).toContain("blue: 132.0 / 255.0");
    expect(readCode(ANDROID_TODO_LIST)).toContain("color = TdayCompletedTileAccent");

    // No zoom source: `ZoomNavigation` mints one shared id per route and the Scheduled board's
    // Completed tile already claims `.completed`, while `AppRootView` keeps both root feeds mounted
    // through a tab crossfade.
    expect(card).not.toContain(".tdayZoomSource(");
  });

  it("leaves every client's floater feed with a Completed entry", () => {
    expect(readCode(WEB_FLOATER_DASHBOARD)).toContain('href="/app/completed?scope=floater"');
    expect(readCode(ANDROID_TODO_LIST)).toContain('key = "floater-completed-entry"');
    expect(ios).toContain("FloaterTaskHomeCompletedCard(onTap: onOpenCompleted)");
  });
});
