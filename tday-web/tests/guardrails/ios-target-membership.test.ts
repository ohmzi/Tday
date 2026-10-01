import { existsSync, readFileSync, readdirSync } from "node:fs";
import { basename, join, relative, resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * iOS target membership — nothing on disk is silently unbuilt, nothing registered is silently gone.
 *
 * `ios-swiftUI/project.yml` says it in its own first line: XcodeGen does not generate this
 * project, `TdayApp.xcodeproj/project.pbxproj` is the source of truth, and it is maintained by
 * hand. There is no Xcode-16 `fileSystemSynchronized` group to fall back on either — the pbxproj
 * contains none — so a `.swift` file joins the build only once someone has written three things
 * into it: a `PBXFileReference`, a `PBXBuildFile` pointing at that reference, and the build
 * file's id inside a `PBXSourcesBuildPhase`.
 *
 * Every machine this repository's work happens on, other than a reviewer's Mac, has no Xcode. So
 * a Swift file added anywhere else arrives unregistered by default, and unregistered does not
 * look like a broken build — it looks like nothing. A *test* file that lands in two of the three
 * places compiles into no target at all while `ios-tests.yml` reports green: the precise failure
 * that workflow exists to prevent, arriving through the one door it cannot see.
 * `docs/motion/LEDGER.md` has been booking "pbxproj registration" as a manual step with nothing
 * behind it; this is the something.
 *
 * The rules resolve the graph rather than grepping the `in Sources` comment. That comment appears
 * twice for every registered file — once on the `PBXBuildFile` declaration, once in a phase's
 * `files` list — and only the second decides whether anything is compiled. A build file declared
 * and never listed is exactly the half-registration being looked for, and a grep counts it as a
 * pass.
 *
 * Membership *per target* is deliberately not asserted. Which of the six native targets
 * (TdayShareExtension, TdayTests, Tday, TdayWidget, TdayWatchWidget, TdayWatch) a file belongs to
 * is a decision the pbxproj already records; restating it here would be a second copy to keep in
 * step every time a file moves, and a file in the wrong target fails the build loudly. "Registered
 * somewhere" is the check that catches the silent one.
 */

const MONO = resolve(__dirname, "..", "..", "..");
const IOS_ROOT = resolve(MONO, "ios-swiftUI");
const PBXPROJ = resolve(IOS_ROOT, "TdayApp.xcodeproj", "project.pbxproj");

/**
 * `ios-swiftUI/Package.swift` is an SPM manifest, not a compilation unit: SwiftPM reads it, the
 * Xcode target graph never does. It is excluded by exact path rather than by a `Package*` glob so
 * that a future `Packages/…/PackageDefaults.swift` — an ordinary source file — cannot slip out of
 * the walk on a name match.
 */
const NOT_A_BUILD_SOURCE = new Set([resolve(IOS_ROOT, "Package.swift")]);

/**
 * The directories `ios-swiftUI/.gitignore` excludes, read from that file rather than listed here.
 *
 * Build output is not project source, and the walk used to descend into it. `ios-swiftUI/build/`
 * is where a default-location Xcode build lands, and it carries the resolved SPM checkouts with
 * it — 648 vendored `.swift` files on this machine, none of them ours and none of them in the
 * pbxproj. Every one of them read as "compiles into no target", so both rules below were red on
 * any Mac where somebody had ever built in the default place, and they stayed red until that
 * directory was deleted. A test that a normal build turns red is a test people learn to ignore.
 *
 * The rules come out of the ignore file instead of being written here because that file is
 * already the place this repository says what is output: `build/`, `DerivedData/`, `xcuserdata/`
 * and fastlane's two result directories, plus the dotted ones the walk was skipping for its own
 * reasons. Hard-coding "build" would have to be kept in step with it by hand, which is the
 * failure mode this suite exists to catch in the pbxproj.
 *
 * Deliberately NOT a gitignore implementation. Only two shapes are honoured — a bare directory
 * name, matched at any depth the way git matches it, and a slash-bearing directory path, matched
 * against the walk's position under `ios-swiftUI/`. Negations, globs and file patterns are
 * skipped: a file pattern cannot hide a directory, and a `!` rule that re-admitted build output
 * is not a thing this ignore file has ever had. Anything unparsed simply stays in the walk, so
 * the failure direction is a rule that fires too rarely, never one that hides a real source.
 */
function ignoredDirectoryRules(): { names: Set<string>; paths: Set<string> } {
  const names = new Set<string>();
  const paths = new Set<string>();
  const ignoreFile = resolve(IOS_ROOT, ".gitignore");
  if (!existsSync(ignoreFile)) return { names, paths };

  for (const raw of readFileSync(ignoreFile, "utf8").split("\n")) {
    const line = raw.trim();
    if (line === "" || line.startsWith("#") || line.startsWith("!")) continue;
    if (!line.endsWith("/")) continue;
    if (line.includes("*") || line.includes("?") || line.includes("[")) continue;
    const entry = line.replace(/^\/+/, "").replace(/\/+$/, "");
    if (entry === "") continue;
    if (entry.includes("/")) paths.add(entry);
    else names.add(entry);
  }

  return { names, paths };
}

const IGNORED = ignoredDirectoryRules();

/** A pbxproj object id: 24 uppercase hex characters. */
const OBJECT_ID = "[0-9A-F]{24}";

function walkSwift(dir: string): string[] {
  const found: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    // `.spm-cache/` is where `ios-tests.yml` clones resolved packages; on a CI checkout it holds
    // thousands of third-party sources that no target of ours registers. Nothing Xcode builds
    // from this repository lives in a dotted directory, so skipping the whole class is safe and
    // survives the next tool that invents one. The ignore file names three of these too; this
    // rule stays because it is the broader one, and because it does not depend on that file
    // still being there.
    if (entry.name.startsWith(".")) continue;
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      // Build output, per `ios-swiftUI/.gitignore` — see `ignoredDirectoryRules`. Checked on the
      // directory rather than on each file so the walk never descends, which is also what keeps
      // a default-location build's SPM checkouts from costing this suite a recursive read of
      // several thousand entries.
      if (IGNORED.names.has(entry.name)) continue;
      if (IGNORED.paths.has(relative(IOS_ROOT, full))) continue;
      found.push(...walkSwift(full));
    } else if (entry.name.endsWith(".swift") && !NOT_A_BUILD_SOURCE.has(full)) {
      found.push(full);
    }
  }
  return found;
}

interface RegisteredSource {
  /** The build-file id as it appears in a phase's `files` list. */
  buildFileId: string;
  /** The `PBXFileReference` it points at, or null when no such object is declared. */
  fileRefId: string | null;
  /** That reference's `path`, or null when the reference is missing or pathless. */
  path: string | null;
}

interface Project {
  /** One entry per build-file id listed by a `PBXSourcesBuildPhase`, across all six phases. */
  sources: RegisteredSource[];
  /** Every file-reference id that some `PBXGroup` claims as a child. */
  grouped: Set<string>;
  phaseCount: number;
}

function parseProject(pbxproj: string): Project {
  const buildFiles = new Map<string, string>();
  for (const m of pbxproj.matchAll(
    new RegExp(`^\\s*(${OBJECT_ID})\\s*/\\*.*?\\*/\\s*=\\s*\\{isa = PBXBuildFile;\\s*fileRef = (${OBJECT_ID})`, "gm"),
  )) {
    buildFiles.set(m[1], m[2]);
  }

  const fileRefPaths = new Map<string, string | null>();
  for (const m of pbxproj.matchAll(
    new RegExp(`^\\s*(${OBJECT_ID})\\s*/\\*.*?\\*/\\s*=\\s*\\{isa = PBXFileReference;(.*?)\\};\\s*$`, "gm"),
  )) {
    const path = /\bpath = ("?)([^";]+)\1;/.exec(m[2]);
    fileRefPaths.set(m[1], path ? path[2] : null);
  }

  const sources: RegisteredSource[] = [];
  let phaseCount = 0;
  for (const phase of pbxproj.matchAll(/isa = PBXSourcesBuildPhase;[\s\S]*?files = \(([\s\S]*?)\);/g)) {
    phaseCount += 1;
    for (const entry of phase[1].matchAll(new RegExp(OBJECT_ID, "g"))) {
      const buildFileId = entry[0];
      const fileRefId = buildFiles.get(buildFileId) ?? null;
      sources.push({
        buildFileId,
        fileRefId,
        path: fileRefId === null ? null : (fileRefPaths.get(fileRefId) ?? null),
      });
    }
  }

  // `PBXVariantGroup` parents localised resources; it is in here so that a `.swift` file wrongly
  // parented under one is reported as grouped rather than as an orphan, which would be a
  // confusing way to describe a real but different mistake.
  const grouped = new Set<string>();
  for (const group of pbxproj.matchAll(/isa = PBX(?:Group|VariantGroup);[\s\S]*?children = \(([\s\S]*?)\);/g)) {
    for (const child of group[1].matchAll(new RegExp(OBJECT_ID, "g"))) grouped.add(child[0]);
  }

  return { sources, grouped, phaseCount };
}

const IOS_PRESENT = existsSync(IOS_ROOT) && existsSync(PBXPROJ);
const describeIOS = IOS_PRESENT ? describe : describe.skip;

const DISK: string[] = IOS_PRESENT ? walkSwift(IOS_ROOT).sort() : [];
const PROJECT: Project = IOS_PRESENT
  ? parseProject(readFileSync(PBXPROJ, "utf8"))
  : { sources: [], grouped: new Set(), phaseCount: 0 };

/**
 * Both directions compare basenames, because a `PBXFileReference` carries only the last path
 * component — its directory comes from the group tree above it, which is a second traversal for
 * no extra defect caught. The first rule below is what makes that key trustworthy: the moment two
 * `Foo.swift` exist in different directories, a basename stops identifying a file and this suite
 * has to be told so rather than quietly matching the wrong one.
 */
const REGISTERED_SWIFT = PROJECT.sources.filter((s) => s.path?.endsWith(".swift"));

const rel = (file: string) => relative(MONO, file);

describeIOS("iOS target membership", () => {
  it("identifies a Swift file by its basename unambiguously", () => {
    const byName = new Map<string, string[]>();
    for (const file of DISK) {
      const name = basename(file);
      byName.set(name, [...(byName.get(name) ?? []), rel(file)]);
    }
    const collisions = [...byName.entries()]
      .filter(([, files]) => files.length > 1)
      .map(([name, files]) => `${name} → ${files.join(", ")}`);
    expect(collisions).toEqual([]);
  });

  it("compiles every Swift file that exists on disk", () => {
    const registered = new Set(REGISTERED_SWIFT.map((s) => basename(s.path as string)));
    const unbuilt = DISK.filter((file) => !registered.has(basename(file))).map(
      (file) =>
        `${rel(file)} → no PBXSourcesBuildPhase lists it, so it compiles into no target. Add a ` +
        `PBXFileReference, a PBXBuildFile pointing at it, a group entry, and the build file's id ` +
        `to the Sources phase of the target it belongs to.`,
    );
    expect(unbuilt).toEqual([]);
  });

  it("does not name a Swift file that no longer exists on disk", () => {
    const onDisk = new Set(DISK.map((file) => basename(file)));
    const missing = [...new Set(REGISTERED_SWIFT.map((s) => basename(s.path as string)))]
      .filter((name) => !onDisk.has(name))
      .map(
        (name) =>
          `${name} → a Sources phase compiles it, but no such file is in ios-swiftUI/. Xcode ` +
          `fails the build on a missing source; delete the PBXBuildFile, the PBXFileReference ` +
          `and the group entry.`,
      );
    expect(missing).toEqual([]);
  });

  it("resolves every build file a Sources phase lists", () => {
    // The half-registration with no symptom on disk: a phase names an id whose PBXBuildFile or
    // PBXFileReference was never written, or was deleted from under it. Xcode reports this as a
    // damaged project rather than as a missing file, which sends the reader looking in the wrong
    // place entirely.
    const dangling = PROJECT.sources
      .filter((s) => s.path === null)
      .map(
        (s) =>
          `${s.buildFileId} → listed in a Sources phase, but ` +
          (s.fileRefId === null
            ? `no PBXBuildFile declares it.`
            : `its fileRef ${s.fileRefId} has no PBXFileReference with a path.`),
      );
    expect(dangling).toEqual([]);
  });

  it("keeps every compiled Swift file in the project navigator", () => {
    // A source with no group entry still compiles, so this is not about the build — it is about
    // whether the next person can find the file to edit it. Xcode shows an ungrouped reference
    // nowhere, and a file nobody can open is a file that drifts.
    const orphans = REGISTERED_SWIFT.filter((s) => s.fileRefId !== null && !PROJECT.grouped.has(s.fileRefId))
      .map((s) => `${s.path} → its PBXFileReference is in no PBXGroup, so Xcode does not show it.`);
    expect(orphans).toEqual([]);
  });
});

// ---------------------------------------------------------------------------
// The canary
//
// Every rule above passes vacuously on an empty set — a moved directory, a renamed project, a
// pbxproj format Apple changes out from under the regexes. A suite that passes on nothing is
// worse than no suite, because it reports green. These are floors, not counts.
// ---------------------------------------------------------------------------

describeIOS("the scanner is actually reading the project", () => {
  it("walks the iOS source tree", () => {
    expect(DISK.length).toBeGreaterThan(100);
  });

  it("read the ignore file it prunes build output with", () => {
    // Without this, a renamed or moved `ios-swiftUI/.gitignore` does not fail here — it fails as
    // several hundred "compiles into no target" lines pointing at vendored SPM sources, which
    // reads as a catastrophically broken project rather than as one missing file. Naming `build`
    // specifically because that is the directory a default-location Xcode build lands in, and it
    // is the only one of the rules whose absence is reproducible on a developer's Mac.
    expect(IGNORED.names.has("build")).toBe(true);
    expect(IGNORED.names.size + IGNORED.paths.size).toBeGreaterThan(1);
  });

  it("still sees the source tree the ignore rules sit beside", () => {
    // The other direction: a rule broad enough to prune real sources. `Tday/` is the app target's
    // own directory and is the largest thing in the walk — if a future ignore entry ever swallows
    // it, `DISK.length` alone could still clear 100 on the other targets.
    const appSources = DISK.filter((file) => relative(IOS_ROOT, file).startsWith("Tday/"));
    expect(appSources.length).toBeGreaterThan(100);
  });

  it("parses the pbxproj it is asserting about", () => {
    expect(REGISTERED_SWIFT.length).toBeGreaterThan(100);
    // Six native targets, six Sources phases. Finding fewer means the parse lost one, and a lost
    // phase makes every file in it look unregistered — or, if it is the app's, makes this suite
    // fail in a way that reads as a hundred missing files rather than one bad regex.
    expect(PROJECT.phaseCount).toBe(6);
  });
});
