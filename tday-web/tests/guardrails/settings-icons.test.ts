import { existsSync, readFileSync } from "fs";
import path from "path";
import { describe, expect, it } from "vitest";
import icons from "../fixtures/settings-icons.json";

// Every Lucide glyph the native settings rows reference must have a real asset on
// the platform whose rows use it. iOS cannot be compiled on this machine, and a
// typo'd `Image("Lucide…")` name is a silent runtime no-op in SwiftUI — the row
// simply renders nothing — so this coverage check is the only automated guard
// that every glyph listed for a settings row has an asset to resolve to (it
// cannot catch a name typo'd in the Swift or Kotlin source itself). Unlike the
// guide manifest, the fixture is hand-maintained, and it is split per platform
// because a few settings rows exist on only one (docs/ICONS.md marks those with
// a dash): update it whenever a settings row's glyph changes.
const REPO = path.resolve(__dirname, "..", "..", "..");
const ANDROID_DRAWABLE = path.join(REPO, "android-compose/app/src/main/res/drawable");
const IOS_ASSETS = path.join(REPO, "ios-swiftUI/Tday/Assets.xcassets");

const toSnake = (glyph: string) => glyph.replace(/-/g, "_");
const toPascal = (glyph: string) =>
  glyph
    .split("-")
    .map((w) => w[0].toUpperCase() + w.slice(1))
    .join("");

describe("settings icon coverage", () => {
  it("has icons to check", () => {
    expect(icons.android.length).toBeGreaterThan(0);
    expect(icons.ios.length).toBeGreaterThan(0);
  });

  for (const glyph of icons.android) {
    it(`Android drawable exists for "${glyph}"`, () => {
      expect(
        existsSync(path.join(ANDROID_DRAWABLE, `ic_lucide_${toSnake(glyph)}.xml`)),
        `missing ic_lucide_${toSnake(glyph)}.xml — add it per docs/ICONS.md`,
      ).toBe(true);
    });
  }

  for (const glyph of icons.ios) {
    it(`iOS imageset exists for "${glyph}"`, () => {
      expect(
        existsSync(path.join(IOS_ASSETS, `Lucide${toPascal(glyph)}.imageset`, "Contents.json")),
        `missing Lucide${toPascal(glyph)}.imageset — add it per docs/ICONS.md`,
      ).toBe(true);
    });
  }
});

// The fixture above only proves the glyphs we *listed* have assets. These two read the
// settings screens themselves, so a name typo'd in the source is caught too — the failure
// mode the fixture cannot see, and the one that matters most on iOS where a bad asset name
// renders nothing at all instead of failing the build.
describe("settings icon references resolve", () => {
  it("every Lucide asset named in the iOS settings screen exists", () => {
    const source = readFileSync(
      path.join(REPO, "ios-swiftUI/Tday/Feature/Settings/SettingsScreen.swift"),
      "utf8",
    );
    const names = [...source.matchAll(/"(Lucide[A-Za-z0-9]+)"/g)].map((m) => m[1]);
    expect(names.length).toBeGreaterThan(0);

    const missing = [...new Set(names)].filter(
      (name) => !existsSync(path.join(IOS_ASSETS, `${name}.imageset`, "Contents.json")),
    );
    expect(missing, `no imageset for: ${missing.join(", ")} — add it per docs/ICONS.md`).toEqual(
      [],
    );
  });

  it("every Lucide drawable named in the Android settings screen exists", () => {
    const source = readFileSync(
      path.join(
        REPO,
        "android-compose/app/src/main/java/com/ohmz/tday/compose/feature/settings/SettingsScreen.kt",
      ),
      "utf8",
    );
    const names = [...source.matchAll(/R\.drawable\.(ic_lucide_[a-z0-9_]+)/g)].map((m) => m[1]);
    expect(names.length).toBeGreaterThan(0);

    const missing = [...new Set(names)].filter(
      (name) => !existsSync(path.join(ANDROID_DRAWABLE, `${name}.xml`)),
    );
    expect(missing, `no drawable for: ${missing.join(", ")} — add it per docs/ICONS.md`).toEqual(
      [],
    );
  });
});
