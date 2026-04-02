import { execFileSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";

const REPOSITORY = process.env.GITHUB_REPOSITORY ?? "ohmzi/Tday";
const DEFAULT_CURRENT_OUTPUT = "tday-web/public/release/current-release.json";
const DEFAULT_LATEST_OUTPUT = "tday-web/public/release/latest-changes.json";
const DEFAULT_BODY_OUTPUT = "body.md";

/** Parses CLI flags passed in `--key value` form. */
function parseArgs(argv) {
  const parsed = {};

  for (let index = 0; index < argv.length; index += 1) {
    const current = argv[index];
    if (!current.startsWith("--")) continue;

    const key = current.slice(2);
    const next = argv[index + 1];
    if (!next || next.startsWith("--")) {
      parsed[key] = "true";
      continue;
    }

    parsed[key] = next;
    index += 1;
  }

  return parsed;
}

/** Normalizes a release version by trimming it and removing a leading `v`. */
function normalizeVersion(raw) {
  if (!raw) return null;
  const value = String(raw).trim();
  if (!value) return null;
  return value.replace(/^[vV]/, "");
}

/** Runs a git command and returns its trimmed stdout. */
function runGit(args) {
  return execFileSync("git", args, { encoding: "utf8" }).trim();
}

/** Runs a git command and returns an empty string when the command fails. */
function safeRunGit(args) {
  try {
    return runGit(args);
  } catch {
    return "";
  }
}

/** Counts the files changed by a commit so larger changes rank a little higher. */
function getChangedFileCount(hash) {
  const files = safeRunGit(["show", "--name-only", "--format=", hash])
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);

  return files.length;
}

/** Strips release-noise prefixes and suffixes from commit subjects. */
function cleanSubject(subject) {
  return subject
    .replace(/^[a-z]+(\([^)]+\))?!?:\s*/i, "")
    .replace(/\s+and bump version to\s+[\d.]+$/i, "")
    .replace(/\(#\d+\)\s*$/i, "")
    .trim()
    .replace(/\.$/, "");
}

/** Scores commit subjects so feature-facing changes bubble up into release notes. */
function scoreCommit(subject, hash) {
  const commitType = subject.match(/^([a-z]+)/i)?.[1]?.toLowerCase() ?? "";
  const typeWeight = {
    feat: 60,
    fix: 50,
    refactor: 40,
    style: 30,
    docs: 20,
    chore: 10,
  }[commitType] ?? 25;

  return typeWeight * 100 + getChangedFileCount(hash);
}

/** Lists release-note candidates between the previous tag and the current ref. */
function listCommitCandidates(previousTag, toRef) {
  const range = previousTag ? `${previousTag}..${toRef}` : toRef;
  const raw = safeRunGit(["log", "--no-merges", "--format=%H%x1f%s%x1e", range]);

  return raw
    .split("\x1e")
    .map((entry) => entry.trim())
    .filter(Boolean)
    .map((entry) => {
      const [hash, subject] = entry.split("\x1f");
      return {
        hash: hash?.trim() ?? "",
        subject: subject?.trim() ?? "",
      };
    })
    .filter((entry) => entry.hash && entry.subject)
    .filter((entry) => !/^chore\(release\):/i.test(entry.subject))
    .filter((entry) => !/^merge /i.test(entry.subject))
    .map((entry) => ({
      ...entry,
      cleaned: cleanSubject(entry.subject),
      score: scoreCommit(entry.subject, entry.hash),
    }))
    .filter((entry) => entry.cleaned)
    .sort((left, right) => right.score - left.score);
}

/** Builds the top three release notes for the current release range. */
function buildTopNotes(previousTag, toRef) {
  const seen = new Set();
  const notes = [];

  for (const entry of listCommitCandidates(previousTag, toRef)) {
    const key = entry.cleaned.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    notes.push(entry.cleaned[0].toUpperCase() + entry.cleaned.slice(1));
    if (notes.length === 3) break;
  }

  if (notes.length > 0) {
    return notes;
  }

  return ["Maintenance improvements across the app."];
}

/** Creates the parent directory for an output file when it does not exist yet. */
function ensureDirectory(filePath) {
  mkdirSync(path.dirname(filePath), { recursive: true });
}

/** Writes formatted JSON output to disk. */
function writeJson(filePath, value) {
  ensureDirectory(filePath);
  writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

/** Writes the GitHub release body that accompanies the generated metadata files. */
function writeReleaseBody(filePath, metadata) {
  const compareLine = metadata.compareUrl
    ? `**Full Changelog**: ${metadata.compareUrl}`
    : `**Release**: ${metadata.releaseUrl}`;

  const body = [
    "## What's Changed",
    "",
    ...metadata.notes.map((note) => `- ${note}`),
    "",
    compareLine,
    "",
    "---",
    "",
    "## Downloads",
    "",
    "### Android",
    "",
    "Download the APK from the assets below and install on your device.",
    "",
    "### Docker Compose",
    "",
    "```bash",
    "docker compose pull && docker compose up -d",
    "```",
    "",
    "### Docker",
    "",
    "```bash",
    "docker pull ghcr.io/ohmzi/tday:latest",
    "",
    "docker rm -f tday 2>/dev/null || true",
    "",
    "docker run -d \\",
    "  --name tday \\",
    "  -p 2525:8080 \\",
    "  --env-file .env.docker \\",
    "  --restart unless-stopped \\",
    "  ghcr.io/ohmzi/tday:latest",
    "```",
    "",
    "### Portainer",
    "",
    "1. In Portainer: **Containers** -> select **tday**",
    "2. Click **Recreate**",
    "3. Enable **Re-pull image**",
    "4. Click **Recreate**",
    "",
  ].join("\n");

  writeFileSync(filePath, body, "utf8");
}

/** Generates local release metadata, the latest metadata snapshot, and the release body. */
function main() {
  const args = parseArgs(process.argv.slice(2));
  const version = normalizeVersion(args.version);

  if (!version) {
    throw new Error("Missing --version");
  }

  const versionTag = `v${version}`;
  const toRef = args["to-ref"] ?? "HEAD";
  const previousTag = args.previous?.trim() || "";
  const publishedAt = args["published-at"] ?? safeRunGit(["log", "-1", "--format=%cI", toRef]);
  const currentOutput = path.resolve(args["current-output"] ?? DEFAULT_CURRENT_OUTPUT);
  const latestOutput = path.resolve(args["latest-output"] ?? DEFAULT_LATEST_OUTPUT);
  const bodyOutput = path.resolve(args["body-output"] ?? DEFAULT_BODY_OUTPUT);
  const repository = args.repository ?? REPOSITORY;
  const releaseUrl = `https://github.com/${repository}/releases/tag/${versionTag}`;
  const compareUrl = previousTag
    ? `https://github.com/${repository}/compare/${previousTag}..${versionTag}`
    : null;
  const notes = buildTopNotes(previousTag, toRef);

  const metadata = {
    version,
    publishedAt: publishedAt || null,
    notes,
    releaseUrl,
    compareUrl,
  };

  writeJson(currentOutput, metadata);
  writeJson(latestOutput, metadata);
  writeReleaseBody(bodyOutput, metadata);
}

main();
