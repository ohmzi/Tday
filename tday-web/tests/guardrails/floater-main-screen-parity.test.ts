import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * ONE VISIBILITY RULE, WRITTEN THREE TIMES
 *
 * Whether a floater reaches the main screen is decided by shared Kotlin, which web and iOS cannot
 * link, so each keeps a mirror. A drifted mirror means two clients disagree about the same list.
 * The rule is small enough to pin by its shape: no list is shown, and only membership in the
 * hidden ids hides a task.
 */
const root = resolve(__dirname, "../../..");
const read = (path: string) => readFileSync(resolve(root, path), "utf8");

describe("floater main-screen rule", () => {
  it("is the shared Kotlin rule", () => {
    const source = read("shared/src/commonMain/kotlin/com/ohmz/tday/shared/floater/FloaterMainScreen.kt");
    expect(source).toContain("listId == null || listId !in hiddenListIds");
  });

  it("reaches the Android feed on the normal path, not only while searching or dragging", () => {
    const source = read(
      "android-compose/app/src/main/java/com/ohmz/tday/compose/feature/todos/TodoListScreen.kt",
    );
    // `scopeSections` is the unfiltered build. Reusing it on the Floater home screen would put
    // hidden tasks straight back in the feed, and no JVM test can see a composable's branch.
    expect(source).toContain(
      "val timelineSections = if (!scopedSearchActive && !timelineDragActive && !isFloaterTaskHomeScreen) {",
    );
  });

  it("is mirrored on iOS", () => {
    const source = read("ios-swiftUI/Tday/Core/Model/FloaterMainScreen.swift");
    expect(source).toContain("guard let listId else { return true }");
    expect(source).toContain("return !hiddenListIds.contains(listId)");
  });

  it("reaches the iOS feed", () => {
    const source = read("ios-swiftUI/Tday/Feature/Todos/TodoListScreen.swift");
    expect(source).toContain("FloaterMainScreen.feedItems(timelineItems, lists: viewModel.lists)");
  });

  it("takes every iOS create-list closure at the sheet's six parameters", () => {
    const source = read("ios-swiftUI/Tday/Feature/ScheduledTaskHome/ScheduledTaskHomeScreen.swift");
    // iOS is not compiled on Linux hosts, so a trailing closure left at the old arity would only
    // surface on macOS CI.
    expect(source).toContain("CreateListSheet { name, color, iconKey, _, _, defaultPriority in");
  });

  it("is mirrored on web", () => {
    const source = read("tday-web/src/lib/floater/mainScreen.ts");
    expect(source).toContain("listId == null || !hiddenListIds.has(listId)");
  });
});
