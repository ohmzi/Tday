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

  it("is mirrored on web", () => {
    const source = read("tday-web/src/lib/floater/mainScreen.ts");
    expect(source).toContain("listId == null || !hiddenListIds.has(listId)");
  });
});
