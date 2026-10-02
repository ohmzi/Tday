import { readFileSync, readdirSync, existsSync } from "fs";
import path from "path";
import { describe, it, expect } from "vitest";

// The code that decides whether a report may leave a device is pinned in
// `sentry-privacy.test.ts`. This file pins what gets that code in front of people: the DSNs and
// the upload token CI hands to each release build, and the words the consent card, the Settings
// row and the FAQ show in every language.

const ROOT = path.resolve(__dirname, "..", "..");
const MONO = path.resolve(ROOT, "..");

function readSource(filePath: string): string {
  return readFileSync(filePath, "utf-8");
}

function repoPath(file: string): string {
  return path.relative(MONO, file).split(path.sep).join("/");
}

// The text from `from` up to the next `to` after it. It throws when either anchor is missing, so a
// renamed anchor fails the test instead of leaving an empty slice to satisfy a `not` assertion.
function between(content: string, from: string, to: string): string {
  const start = content.indexOf(from);
  if (start === -1) throw new Error(`"${from}" not found`);
  const end = content.indexOf(to, start + from.length);
  if (end === -1) throw new Error(`"${to}" not found after "${from}"`);
  return content.slice(start, end);
}

// ─── CI and image build paths ──────────────────────────────────────
const WORKFLOWS = path.join(MONO, ".github", "workflows");
const releaseWorkflow = path.join(WORKFLOWS, "release.yml");
const iosWorkflow = path.join(WORKFLOWS, "ios-testflight.yml");
const dockerfile = path.join(MONO, "Dockerfile.backend");
const composeBuild = path.join(MONO, "docker-compose.build.yaml");
const rootEnvExample = path.join(MONO, ".env.example");
const fastfile = path.join(MONO, "ios-swiftUI", "fastlane", "Fastfile");
const backendAppConfig = path.join(
  MONO, "tday-backend", "src", "main", "kotlin", "com", "ohmz", "tday", "config", "AppConfig.kt",
);
const backendSecurityHeaders = path.join(
  MONO, "tday-backend", "src", "main", "kotlin", "com", "ohmz", "tday", "plugins", "SecurityHeaders.kt",
);

// ─── String and guide paths ────────────────────────────────────────
const ANDROID_RES = path.join(MONO, "android-compose", "app", "src", "main", "res");
const iosStrings = path.join(MONO, "ios-swiftUI", "Tday", "Resources", "Localizable.xcstrings");
const iosCard = path.join(MONO, "ios-swiftUI", "Tday", "Feature", "Telemetry", "TelemetryConsentCard.swift");
const iosSettings = path.join(MONO, "ios-swiftUI", "Tday", "Feature", "Settings", "SettingsScreen.swift");
const iosGuideDir = path.join(MONO, "ios-swiftUI", "Tday", "Resources", "Guide");
const MESSAGES = path.join(ROOT, "messages");
const GUIDE_DIR = path.join(MONO, "shared", "src", "commonMain", "kotlin", "com", "ohmz", "tday", "shared", "guide");
const guideCatalog = path.join(GUIDE_DIR, "GuideCatalog.kt");
const guideTopicIds = path.join(GUIDE_DIR, "GuideTopicIds.kt");
const guideStrings = path.join(GUIDE_DIR, "GuideStringsGenerated.kt");
const guideStructure = path.join(ROOT, "src", "generated", "guide-structure.json");
const guideIconsFixture = path.join(ROOT, "tests", "fixtures", "guide-icons.json");
const versionManifest = path.join(MONO, "version.json");

// ─── Workflow helpers ──────────────────────────────────────────────
function withoutYamlComments(source: string): string {
  return source.replace(/^\s*#.*$/gm, "");
}

function indentOf(line: string): number {
  return (line.match(/^\s*/) as RegExpMatchArray)[0].length;
}

type WorkflowStep = { name: string; text: string };

// The `- name:` steps of a workflow, comments removed. A step runs until the next line that is not
// indented deeper than its own `- name:` line (the next step, or the next job).
function workflowSteps(source: string): WorkflowStep[] {
  const steps: WorkflowStep[] = [];
  let name: string | null = null;
  let indent = 0;
  let body: string[] = [];
  for (const line of withoutYamlComments(source).split("\n")) {
    const header = line.match(/^(\s*)-\s+name:\s*(.+?)\s*$/);
    if (header || (name !== null && line.trim() !== "" && indentOf(line) <= indent)) {
      if (name !== null) steps.push({ name, text: body.join("\n") });
      name = header ? header[2] : null;
      indent = header ? header[1].length : 0;
      body = header ? [line] : [];
      continue;
    }
    if (name !== null) body.push(line);
  }
  if (name !== null) steps.push({ name, text: body.join("\n") });
  return steps;
}

// The `key:` line and every line indented deeper than it. Throws when the key is missing.
function yamlBlock(text: string, key: string): string {
  const lines = text.split("\n");
  const start = lines.findIndex((line) => new RegExp(`^\\s*${key}:`).test(line));
  if (start === -1) throw new Error(`"${key}:" not found`);
  const block = [lines[start]];
  for (const line of lines.slice(start + 1)) {
    if (line.trim() !== "" && indentOf(line) <= indentOf(lines[start])) break;
    block.push(line);
  }
  return block.join("\n");
}

function dockerStages(source: string): string[] {
  return source.split(/^FROM\s/m).slice(1);
}

describe("crash-report delivery: release workflow", () => {
  const release = withoutYamlComments(readSource(releaseWorkflow));
  const steps = workflowSteps(readSource(releaseWorkflow));
  const imageSteps = steps.filter((step) => /uses:\s*docker\/build-push-action@/.test(step.text));

  it("builds the web image twice, and both builds are checked below", () => {
    // The push step replays the build step from cache, so a third or a fourth image build that
    // skipped the inputs would ship an SPA without them.
    expect(imageSteps).toHaveLength(2);
  });

  it("passes the web DSN as a build arg to both image builds", () => {
    expect(release.match(/VITE_SENTRY_DSN=\$\{\{ secrets\.SENTRY_DSN_WEB \}\}/g)).toHaveLength(2);
    for (const step of imageSteps) {
      expect(yamlBlock(step.text, "build-args"), `${step.name} must pass the web DSN`).toContain(
        "VITE_SENTRY_DSN=${{ secrets.SENTRY_DSN_WEB }}",
      );
    }
  });

  it("passes the upload token to both image builds as a build secret, through secret-envs", () => {
    // `secret-envs` rather than `secrets`: the action rejects an empty `secrets` value, which
    // would fail the whole release whenever the repository secret is unset.
    expect(release.match(/sentry_auth_token=SENTRY_AUTH_TOKEN/g)).toHaveLength(2);
    for (const step of imageSteps) {
      expect(yamlBlock(step.text, "secret-envs"), `${step.name} must mount the token`).toContain(
        "sentry_auth_token=SENTRY_AUTH_TOKEN",
      );
      expect(step.text).toContain("SENTRY_AUTH_TOKEN: ${{ secrets.SENTRY_AUTH_TOKEN }}");
      expect(step.text, `${step.name} must not use the secrets input`).not.toMatch(/^\s+secrets:/m);
    }
  });

  it("never passes the upload token as a build arg", () => {
    // Build args land in `docker history`.
    for (const step of imageSteps) {
      expect(yamlBlock(step.text, "build-args")).not.toContain("SENTRY_AUTH_TOKEN");
    }
  });

  it("gives the Android release build its DSN and the upload token", () => {
    const apk = steps.find((step) => step.name === "Build Android APK");
    expect(apk, "the Android release step is missing").toBeDefined();
    expect(apk?.text).toContain("SENTRY_DSN: ${{ secrets.SENTRY_DSN_ANDROID }}");
    expect(apk?.text).toContain("SENTRY_AUTH_TOKEN: ${{ secrets.SENTRY_AUTH_TOKEN }}");
  });

  it("does not hand a DSN to any workflow that runs on pull requests", () => {
    // A DSN reaches a build only through a release. Anywhere else a pull request from a fork
    // could read it, and a build outside a release would carry it.
    const others = readdirSync(WORKFLOWS).filter(
      (file) => /\.ya?ml$/.test(file) && file !== "release.yml" && file !== "ios-testflight.yml",
    );
    expect(others.length).toBeGreaterThan(0);
    for (const file of others) {
      expect(readSource(path.join(WORKFLOWS, file)), `${file} must not use a Sentry secret`).not.toMatch(
        /secrets\.SENTRY_/,
      );
    }
  });
});

describe("crash-report delivery: iOS TestFlight workflow", () => {
  const source = readSource(iosWorkflow);
  const workflow = withoutYamlComments(source);
  const steps = workflowSteps(source);

  it("gives the archive step the iOS DSN in every mode", () => {
    expect(workflow).toContain("SENTRY_DSN: ${{ secrets.SENTRY_DSN_IOS }}");
  });

  it("gives the upload token only to release runs", () => {
    const withToken = steps.filter((step) => step.text.includes("secrets.SENTRY_AUTH_TOKEN"));
    expect(withToken.length).toBeGreaterThan(0);
    for (const step of withToken) {
      expect(step.text, `${step.name} must be limited to release mode`).toContain("== 'release'");
    }
  });

  it("installs a pinned sentry-cli and checks its SHA-256 before running it", () => {
    // The binary runs with the upload token in its environment: what executes must be exactly
    // the file named here.
    expect(workflow).toMatch(/SENTRY_CLI_VERSION:\s*"\d+\.\d+\.\d+"/);
    // The digest is a public checksum, but a bare 64-hex string beside the word "sentry" reads as
    // a Sentry access token to secret scanners, so it lives in its own file.
    expect(workflow).toMatch(/CLI_DIGEST_FILE:\s*\.github\/pinned\/cli-darwin-universal\.sha256/);
    expect(workflow).not.toMatch(/\b[0-9a-f]{64}\b/);
    const digest = readSource(path.join(MONO, ".github", "pinned", "cli-darwin-universal.sha256")).trim();
    expect(digest).toMatch(/^[0-9a-f]{64}$/);
    expect(digest.toLowerCase()).not.toContain("sentry");
    expect(workflow).toContain("shasum -a 256 --check");
    expect(workflow).not.toMatch(/SENTRY_CLI_VERSION:\s*"?latest/i);
    expect(workflow).not.toMatch(/sentry\.io\/get-cli|sentry-cli[^\n]*\|\s*(?:ba)?sh\b/);
  });
});

describe("crash-report delivery: Fastfile", () => {
  const fastlane = readSource(fastfile);

  it("checks the DSN's shape and writes it without a literal //", () => {
    // `//` starts a comment in an xcconfig, so `https://key@host/1` would be cut at `https:`.
    expect(fastlane).toMatch(/^SENTRY_DSN_SHAPE = /m);
    expect(fastlane).toContain('build_settings["SENTRY_DSN"] = sentry_dsn.sub("//", "/$()/")');
  });

  it("uploads the dSYMs to the iOS project before the TestFlight upload", () => {
    expect(fastlane).toContain('SENTRY_ORG = "tday-kb"');
    expect(fastlane).toContain('SENTRY_IOS_PROJECT = "tday-ios"');
    expect(fastlane).toContain('"sentry-cli", "debug-files", "upload"');
    // The call, not the definition and not a comment. App Store Connect spends the build number
    // on upload, so a failed dSYM upload has to cost a re-run and not a number.
    const dsymUpload = fastlane.search(/^\s*upload_dsyms_to_sentry\(/m);
    const testflightUpload = fastlane.search(/^\s*upload_to_testflight\(/m);
    expect(dsymUpload).toBeGreaterThan(-1);
    expect(testflightUpload).toBeGreaterThan(dsymUpload);
  });
});

describe("crash-report delivery: web image", () => {
  const docker = readSource(dockerfile);
  const stages = dockerStages(docker);

  it("never makes the upload token an ARG or ENV", () => {
    expect(docker).not.toMatch(/^\s*(?:ARG|ENV)\s+SENTRY_AUTH_TOKEN/m);
  });

  it("mounts the upload token as a build secret for the web build, and nowhere else", () => {
    expect(docker).toContain("--mount=type=secret,id=sentry_auth_token");
    const frontend = stages.find((stage) => stage.includes("npm run build"));
    expect(frontend).toContain("--mount=type=secret,id=sentry_auth_token");
    expect(stages.filter((stage) => stage.includes("type=secret"))).toHaveLength(1);
  });

  it("redeclares the DSN arg in the final stage and exposes it to the CSP builder", () => {
    // ARG is scoped to its stage: the frontend stage's value never reaches this one.
    expect(docker.match(/^ARG VITE_SENTRY_DSN\b/gm)).toHaveLength(2);
    const finalStage = stages[stages.length - 1];
    expect(finalStage).toMatch(/^ARG VITE_SENTRY_DSN=/m);
    expect(finalStage).toContain("ENV TDAY_CLIENT_SENTRY_DSN=$VITE_SENTRY_DSN");
  });

  it("is read by the backend under the same name, and always allowed in the CSP", () => {
    expect(readSource(backendAppConfig)).toContain('env("TDAY_CLIENT_SENTRY_DSN")');
    expect(readSource(backendSecurityHeaders)).toContain("parseSentryIngestOrigin(config.clientSentryDsn)");
  });

  it("local builds take the token as a secret and the DSN as a build arg", () => {
    const compose = readSource(composeBuild);
    const args = yamlBlock(compose, "args");
    expect(args).toContain("VITE_SENTRY_DSN:");
    expect(args).not.toContain("SENTRY_AUTH_TOKEN");
    expect(compose).toMatch(/secrets:\s*\n\s*-\s*sentry_auth_token/);
    expect(compose).toMatch(/sentry_auth_token:\s*\n\s*environment:\s*SENTRY_AUTH_TOKEN/);
  });

  it("no client trace-rate plumbing is left in the build", () => {
    for (const file of [dockerfile, composeBuild, rootEnvExample, path.join(ROOT, "vite.config.ts")]) {
      expect(readSource(file), `${repoPath(file)} must not mention it`).not.toContain(
        "VITE_SENTRY_TRACES_SAMPLE_RATE",
      );
    }
  });

  it("ships no DSN and no token in the env template", () => {
    const env = readSource(rootEnvExample);
    expect(env).toMatch(/^SENTRY_DSN=\s*$/m);
    expect(env).toMatch(/^VITE_SENTRY_DSN=\s*$/m);
    // Commented out: a real token in a copied template would be picked up by every build.
    expect(env).not.toMatch(/^SENTRY_AUTH_TOKEN=/m);
  });
});

// ─── Android strings ───────────────────────────────────────────────
const ANDROID_LOCALES = ["de", "es", "fr", "it", "ja", "ms", "pt", "ru", "zh"];
const ANDROID_FAMILY = /^(?:telemetry_|settings_crash_reports)/;
const ANDROID_REQUIRED_KEYS = [
  "telemetry_card_title",
  "telemetry_card_intro",
  "telemetry_card_sent_label",
  "telemetry_card_sent",
  "telemetry_card_never_sent_label",
  "telemetry_card_never_sent",
  "telemetry_card_footnote",
  "telemetry_card_share",
  "telemetry_card_not_now",
  "telemetry_card_read_faq",
  "settings_crash_reports",
  "settings_crash_reports_toggle",
  "settings_crash_reports_help",
];

function androidStrings(valuesDir: string): Map<string, string> {
  const xml = readSource(path.join(ANDROID_RES, valuesDir, "strings.xml"));
  const entries = new Map<string, string>();
  for (const match of xml.matchAll(/<string\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g)) {
    entries.set(match[1], match[2]);
  }
  return entries;
}

function androidFamily(strings: Map<string, string>): string[] {
  return [...strings.keys()].filter((key) => ANDROID_FAMILY.test(key)).sort();
}

describe("crash-report strings: Android", () => {
  const english = androidStrings("values");

  it("values/ has every key the card and the Settings row use", () => {
    for (const key of ANDROID_REQUIRED_KEYS) {
      expect(english.get(key)?.trim(), `values/strings.xml is missing ${key}`).toBeTruthy();
    }
  });

  it.each(ANDROID_LOCALES)("values-%s has exactly the keys values/ has, translated", (locale) => {
    const localized = androidStrings(`values-${locale}`);
    expect(androidFamily(localized), `values-${locale} should not add or remove keys`).toEqual(
      androidFamily(english),
    );
    for (const key of androidFamily(english)) {
      const value = localized.get(key)?.trim();
      expect(value, `values-${locale} has an empty ${key}`).toBeTruthy();
      expect(value, `values-${locale} still has the English text for ${key}`).not.toBe(english.get(key));
    }
  });

  it("every value is valid Android resource text in all ten files", () => {
    // aapt rejects an apostrophe that is not escaped, and a bare ampersand is not XML. Nothing
    // else on a Linux machine reads these nine translated files before a build does.
    for (const dir of ["values", ...ANDROID_LOCALES.map((locale) => `values-${locale}`)]) {
      const strings = androidStrings(dir);
      for (const key of androidFamily(strings)) {
        const value = strings.get(key) as string;
        expect(value, `${dir}/${key} has an unescaped apostrophe`).not.toMatch(/(?<!\\)'/);
        expect(value, `${dir}/${key} has a bare ampersand`).not.toMatch(
          /&(?!amp;|lt;|gt;|quot;|apos;|#\d+;)/,
        );
      }
    }
  });
});

// ─── iOS strings ───────────────────────────────────────────────────
const IOS_LOCALES = ["de", "es", "fr", "it", "ja", "ms", "pt", "ru", "zh"];
// English is the key. Each is also a literal in the Swift view that shows it.
const IOS_CARD_KEYS = [
  "Help fix crashes?",
  "T'Day can send a short technical report only when something goes wrong, such as a crash, a freeze or an unexpected error. It helps the developer reproduce the problem on a similar device.",
  "What's included",
  "App version, device model, OS version, what failed and where.",
  "Never included",
  "Your name or account, IP address, location, server address, or any task or list content.",
  "Off by default. Change it any time in Settings → Privacy.",
  "Share reports",
  "Not now",
  "Read the full FAQ",
];
const IOS_SETTINGS_KEYS = [
  "Crash & problem reports",
  "Send crash & problem reports when something fails",
  "About crash & problem reports",
];

type XcStringUnit = { stringUnit?: { state?: string; value?: string } };
type XcCatalog = { strings: Record<string, { localizations?: Record<string, XcStringUnit> }> };

describe("crash-report strings: iOS Localizable.xcstrings", () => {
  const catalog = JSON.parse(readSource(iosStrings)) as XcCatalog;
  const keys = [...IOS_CARD_KEYS, ...IOS_SETTINGS_KEYS];

  it.each(keys)("has %s in all nine non-English locales, translated", (key) => {
    const entry = catalog.strings[key];
    expect(entry, `Localizable.xcstrings is missing "${key}"`).toBeDefined();
    for (const locale of IOS_LOCALES) {
      const unit = entry?.localizations?.[locale]?.stringUnit;
      expect(unit?.state, `${locale} for "${key}" is not marked translated`).toBe("translated");
      expect(unit?.value?.trim(), `${locale} for "${key}" is empty`).toBeTruthy();
      expect(unit?.value, `${locale} for "${key}" still has the English text`).not.toBe(key);
    }
  });

  it("the card and the Settings row use these exact strings", () => {
    // A string that is reworded in the view but not in the catalog silently shows English.
    const card = readSource(iosCard);
    for (const key of IOS_CARD_KEYS) {
      expect(card, `TelemetryConsentCard.swift has no literal "${key}"`).toContain(key);
    }
    const settings = readSource(iosSettings);
    for (const key of IOS_SETTINGS_KEYS) {
      expect(settings, `SettingsScreen.swift has no literal "${key}"`).toContain(key);
    }
  });
});

// ─── Guide topic and web strings ───────────────────────────────────
const WEB_LOCALES = ["en", "es", "fr", "de", "it", "pt", "ru", "zh", "ja", "ms"];
const TOPIC_ID = "crash-reports";

type Messages = Record<string, unknown>;

function readMessages(locale: string): Messages {
  return JSON.parse(readSource(path.join(MESSAGES, `${locale}.json`))) as Messages;
}

function lookup(messages: Messages, dotted: string): unknown {
  return dotted.split(".").reduce<unknown>(
    (node, part) => (node && typeof node === "object" ? (node as Messages)[part] : undefined),
    messages,
  );
}

function asText(value: unknown): string {
  return Array.isArray(value) ? value.join(" ") : typeof value === "string" ? value : "";
}

type GuideStructureTopic = {
  id: string;
  section: string;
  icon: string;
  platforms: string[];
  titleKey: string;
  summaryKey: string;
  keywordsKey: string;
  body: { type: string; keys: string[] }[];
  deepLink: unknown;
  helpAnchors: string[];
  serverOnly: boolean;
  sinceVersion: string | null;
};

function semverCompare(a: string, b: string): number {
  const left = a.split(".").map(Number);
  const right = b.split(".").map(Number);
  for (let i = 0; i < 3; i += 1) {
    if (left[i] !== right[i]) return left[i] < right[i] ? -1 : 1;
  }
  return 0;
}

describe("crash-reports guide topic", () => {
  const catalog = readSource(guideCatalog);
  const release = JSON.parse(readSource(versionManifest)) as { version: string };

  it("has an id the Settings rows and the consent cards can link to", () => {
    expect(readSource(guideTopicIds)).toContain(`const val CRASH_REPORTS = "${TOPIC_ID}"`);
  });

  it("is declared in GuideCatalog: activity icon, every client, shown from 0.8.0", () => {
    const declaration = between(
      catalog,
      "GuideTopicIds.CRASH_REPORTS, GuideSectionId",
      'helpAnchors = listOf("settings-privacy")',
    );
    expect(declaration).toMatch(/GuideSectionId\.MODES_AND_SYNC,\s*"activity",/);
    expect(declaration).toContain("setOf(WEB, ANDROID, IOS)");
    expect(declaration).toContain('sinceVersion = "0.8.0"');
    // A deep link would need a route in routes.json, and the topic is reached from a Settings row.
    expect(declaration).not.toContain("deepLink");
    expect(declaration).not.toContain("serverOnly = true");
  });

  it("is not newer than the release in version.json", () => {
    // Not "equal to": `sinceVersion` is the release the topic shipped in, and every merge to
    // master after 0.8.0 bumps version.json past it. The topic then leaves "What's New" and
    // stays in the guide. A topic from the future would never have been shown.
    expect(semverCompare("0.8.0", release.version)).toBeLessThanOrEqual(0);
  });

  it("is in the generated structure the web and iOS read, matching the catalog", () => {
    const structure = JSON.parse(readSource(guideStructure)) as { topics: GuideStructureTopic[] };
    const topic = structure.topics.find((candidate) => candidate.id === TOPIC_ID);
    expect(topic, "run ./gradlew :shared:exportGuideContent").toBeDefined();
    expect(topic?.icon).toBe("activity");
    expect(topic?.section).toBe("MODES_AND_SYNC");
    expect(topic?.sinceVersion).toBe("0.8.0");
    expect([...(topic?.platforms ?? [])].sort()).toEqual(["ANDROID", "IOS", "WEB"]);
    expect(topic?.serverOnly).toBe(false);
    expect(topic?.deepLink).toBeNull();
    expect(topic?.helpAnchors).toEqual(["settings-privacy"]);
    expect(topic?.body.map((block) => block.type)).toEqual([
      "PARAGRAPH", "PARAGRAPH", "PARAGRAPH", "PARAGRAPH", "PARAGRAPH", "STEPS", "TIP",
    ]);
  });

  it("has an icon asset on every platform", () => {
    // guide-icons.test.ts checks that each glyph in the manifest has an Android drawable and an
    // iOS imageset. This checks that the manifest, generated from the catalog, lists this one.
    expect(JSON.parse(readSource(guideIconsFixture))).toContain("activity");
  });

  it.each(WEB_LOCALES)("has every string the topic shows in the %s messages", (locale) => {
    const structure = JSON.parse(readSource(guideStructure)) as { topics: GuideStructureTopic[] };
    const topic = structure.topics.find((candidate) => candidate.id === TOPIC_ID) as GuideStructureTopic;
    const english = readMessages("en");
    const messages = readMessages(locale);
    const keys = [
      topic.titleKey,
      topic.summaryKey,
      topic.keywordsKey,
      ...topic.body.flatMap((block) => block.keys),
    ];
    for (const key of keys) {
      const text = asText(lookup(messages, key)).trim();
      expect(text, `${locale}.json has no ${key}`).not.toBe("");
      const englishText = asText(lookup(english, key));
      if (locale !== "en" && englishText.length > 24) {
        expect(text, `${locale}.json still has the English text for ${key}`).not.toBe(englishText);
      }
    }
  });

  it("is in the generated Kotlin and iOS bundles, so no client shows a missing topic", () => {
    expect(readSource(guideStrings)).toContain(`"guide.topics.${TOPIC_ID}.body5"`);
    for (const locale of WEB_LOCALES) {
      const file = path.join(iosGuideDir, `guide.${locale}.json`);
      expect(existsSync(file), `${repoPath(file)} is missing`).toBe(true);
      const guide = JSON.parse(readSource(file)) as {
        topics: { id: string; title?: string; body?: { type: string; texts?: string[] }[] }[];
      };
      const topic = guide.topics.find((candidate) => candidate.id === TOPIC_ID);
      expect(topic?.title?.trim(), `${locale} iOS guide has no ${TOPIC_ID} title`).toBeTruthy();
      expect(topic?.body?.filter((block) => block.type === "PARAGRAPH")).toHaveLength(5);
      expect(topic?.body?.find((block) => block.type === "STEPS")?.texts).toHaveLength(3);
    }
  });
});

describe("crash-report strings: web", () => {
  // The Settings row, the consent card, the admin row and the privacy page. The guide topic is
  // above. i18n-parity.test.ts already requires every locale to have English's key set; this
  // requires English to have these keys at all, and every locale to have said something in them.
  const REQUIRED = [
    "crashReports.card.title",
    "crashReports.card.intro",
    "crashReports.card.sentLabel",
    "crashReports.card.sent",
    "crashReports.card.neverSentLabel",
    "crashReports.card.neverSent",
    "crashReports.card.footnote",
    "crashReports.card.share",
    "crashReports.card.notNow",
    "crashReports.card.readFaq",
    "settings.privacy.title",
    "settings.crashReports.title",
    "settings.crashReports.toggle",
    "settings.crashReports.helpLabel",
    "settings.serverTelemetry.title",
    "settings.serverTelemetry.toggle",
    "settings.serverTelemetry.description",
    "settings.serverTelemetry.updateFailed",
    "privacy.sections.9.title",
    "privacy.sections.9.content",
  ];

  it.each(WEB_LOCALES)("%s has each of them, translated", (locale) => {
    const english = readMessages("en");
    const messages = readMessages(locale);
    for (const key of REQUIRED) {
      const text = asText(lookup(messages, key)).trim();
      expect(text, `${locale}.json has no ${key}`).not.toBe("");
      const englishText = asText(lookup(english, key));
      if (locale !== "en" && englishText.length > 24) {
        expect(text, `${locale}.json still has the English text for ${key}`).not.toBe(englishText);
      }
    }
  });

  it("the privacy page renders every section the messages define", () => {
    const page = readSource(path.join(ROOT, "src", "pages", "PrivacyPage.tsx"));
    const sections = Object.keys((lookup(readMessages("en"), "privacy.sections") as Messages) ?? {});
    expect(page).toContain(`Array.from({ length: ${sections.length} }`);
  });
});
