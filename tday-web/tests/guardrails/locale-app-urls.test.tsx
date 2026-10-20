// @vitest-environment jsdom
import { readdirSync, readFileSync } from "node:fs";
import { join, relative, resolve } from "node:path";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render } from "@testing-library/react";
import {
  createMemoryRouter,
  matchRoutes,
  RouterProvider,
  type RouteObject,
} from "react-router-dom";
import { router } from "@/router";
import { localizePath } from "@/lib/navigation";
import {
  DEFAULT_LOCALE,
  LANGUAGE_STORAGE_KEY,
  resolveInitialLocale,
  SUPPORTED_LOCALES,
  type SupportedLocale,
} from "@/i18n";

// The auth provider is stubbed to a stable "still bootstrapping" state for one reason:
// `ProtectedRoute` is the first thing a landing app URL renders, and a real provider
// would either throw (no context in this harness) or navigate on to `/fr/login`, which
// would hide the destination under test behind a second redirect. The ROUTE TABLE and
// the redirect element driven below are the real ones; only the screen behind the gate
// is fixed. Hoisted above the `@/router` import by vitest, so the table is built with it.
vi.mock("@/providers/AuthProvider", () => ({
  useAuth: () => ({ user: null, authState: "loading", isAuthenticated: false }),
}));

/**
 * A LOCALE-LESS `/app/...` URL IS AN ENTRY POINT, NOT A TYPO
 *
 * Every app route is nested under `/:locale` (src/router.tsx), so a URL that omits
 * that segment binds `locale` to whatever the first segment happens to be. `/app/tday`
 * is two segments, so it binds `locale = "app"`, finds no child named `tday` under it,
 * and falls through to the `*` catch-all — the app's own `Page not found`.
 *
 * That is invisible in a browser, because you enter at `/` and the root route redirects
 * to `/${resolveInitialLocale()}` before anything else runs. It is the *installed PWA*
 * that cold-starts at the manifest's `start_url`, and that URL is `/app/tday`. So the
 * app opened on a 404 whose own "Go home" button was the first correct URL in the
 * sequence — the report this file exists to stop recurring.
 *
 * WHAT IS DRIVEN HERE, AND WHY IT IS DRIVEN RATHER THAN INSPECTED
 *
 * `matchRoutes` alone answers "does this URL reach the catch-all", and that is a weaker
 * question than the one that matters in two directions a previous version of this file
 * got wrong:
 *
 *   1. `matchRoutes` says nothing about the ELEMENT on the branch. A `/app/*` route that
 *      matches and renders a dead page (`element: <div />`) reaches no catch-all, so
 *      every structural assertion stayed green while the installed PWA opened blank.
 *   2. `resolveInitialLocale()` in jsdom returns `"en"` (navigator is `en-US`), and the
 *      old behavioural cases mounted `LocaleAppRedirectPage` on a synthetic route and
 *      compared against a target recomputed in the test — so hard-coding `"en"` inside
 *      the component was indistinguishable from resolving at runtime.
 *
 * Both are closed the same way: the REAL table (`router.routes`, the same array the
 * browser router is built from) is handed to `createMemoryRouter`, RENDERED through
 * `<RouterProvider>`, and the SETTLED location is read back and compared to a literal
 * destination under a NON-DEFAULT seeded locale. A dead element never moves the
 * location; a hard-coded locale moves it to the wrong one. Either fails here.
 *
 * THE ONE THING THAT SHIPPED IT, AND THE MANY THAT COULD
 *
 * Roughly thirty `/app/...` literals live in `src/`. They are all SAFE, and the reason
 * is a convention rather than a type: each one is handed to `Link`/`useRouter` from
 * `@/lib/navigation`, whose `localizePath` prepends the resolved locale. Swap a single
 * import to `react-router-dom` in any of those files and the literal becomes a 404 with
 * no test failure anywhere in the suite. That is the class.
 *
 * The manifest is the one emitter the convention cannot reach — the OS issues that
 * navigation, no component ever sees it — and it is why the first block below resolves
 * every URL in the manifest against the REAL route table instead of against a list of
 * paths somebody has to remember to update. A list is how this test would rot into one
 * that always passes; rendering `router.routes` cannot.
 *
 * Read as a route table rather than as text for the manifest half, and as text for the
 * emitter half, which is the only way to see an import that is about to go wrong.
 *
 * A KNOWN BOUNDARY, STATED SO NOBODY RE-DISCOVERS IT: a locale-less `/app/<name>` whose
 * `<name>` is also a static child of `/:locale` (`guide`, `login`, `privacy`, `terms`,
 * `blogs`) does NOT reach the redirect. React Router ranks branches by summed segment
 * score, and `/:locale/guide` (dynamic + static) outranks `/app/*` (static + splat), so
 * `/app/guide` lands on `GuideRedirectPage` as `locale = "app"` instead. None of those
 * names is in the manifest and no component emits one unlocalized — SettingsPage's
 * `/app/guide` href goes through `@/lib/navigation` and becomes `/en/app/guide` — so
 * they are left alone rather than chased with an ever-longer list of top-level routes.
 * A manifest shortcut that collides would fail the loop below loudly, which is the
 * right alarm: it is the manifest that cannot be fixed with a locale.
 */
describe("a locale-less app URL resolves to the app, not to the 404", () => {
  const MANIFEST = resolve(__dirname, "../../public/manifest.webmanifest");

  const ROUTES = router.routes as unknown as RouteObject[];

  /**
   * The locale every expectation here is written against. Deliberately NOT the jsdom
   * default: `resolveInitialLocale()` would return `"en"` on its own, which is what made
   * a hard-coded `"en"` invisible. Seeding a non-default locale means the component has
   * to actually consult the override for these to pass.
   */
  const SEED: SupportedLocale = "fr";

  /**
   * The branch a URL takes through the real table. Fails loudly if it matches nothing.
   */
  function branchFor(url: string) {
    const matches = matchRoutes(ROUTES, url);
    expect(matches, `${url} matches no route in the table at all`).not.toBeNull();
    return matches!;
  }

  /** True when the URL terminates on the `*` catch-all nested under `/:locale`. */
  function is404(url: string): boolean {
    return branchFor(url).some((match) => match.route.path === "*");
  }

  /**
   * THE HARNESS THAT CAN FAIL. Seeds the locale override, builds a memory router over
   * the REAL `router.routes` — elements included, so a route that matches but does not
   * redirect is caught — renders it, and waits for the location to stop moving before
   * reporting it. The wait is a stability poll rather than a `waitFor(expect(...))`
   * because lazy route modules resolve a tick after `render`, and a condition that is
   * true on the first tick would report the pre-redirect location as the answer.
   */
  const SETTLE_TICK_MS = 10;
  const SETTLE_STABLE_TICKS = 3;
  const SETTLE_MAX_TICKS = 300;

  async function settle(entry: string, seed: SupportedLocale): Promise<string> {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, seed);
    cleanup();
    const memory = createMemoryRouter(ROUTES, { initialEntries: [entry] });
    render(<RouterProvider router={memory} />);

    const at = () => {
      const { pathname, search, hash } = memory.state.location;
      return `${pathname}${search}${hash}`;
    };

    let previous = at();
    let stable = 0;
    for (let tick = 0; tick < SETTLE_MAX_TICKS; tick++) {
      await new Promise((tickDone) => setTimeout(tickDone, SETTLE_TICK_MS));
      const current = at();
      if (current !== previous) {
        previous = current;
        stable = 0;
      } else if (++stable >= SETTLE_STABLE_TICKS) {
        break;
      }
    }
    return previous;
  }

  type Manifest = {
    start_url: string;
    shortcuts?: { url: string }[];
    share_target?: { action?: string };
  };

  const manifest = JSON.parse(readFileSync(MANIFEST, "utf8")) as Manifest;

  /**
   * Every URL in the manifest that a browser or OS navigates to on the app's behalf.
   * `share_target` is in here because it is the same contract — the entry URL is issued
   * outside the router — and it is the one that already got this right.
   */
  const manifestUrls = [
    manifest.start_url,
    ...(manifest.shortcuts ?? []).map((shortcut) => shortcut.url),
    ...(manifest.share_target?.action ? [manifest.share_target.action] : []),
  ];

  /**
   * The subset the locale-less `/app/*` route claims. `share_target.action` is in the
   * list above because it is the same kind of external entry point, but it is NOT one of
   * these: `/share` is a real top-level route that builds its own locale-prefixed target,
   * so it is out of scope for the redirect and for the loop argument below.
   */
  const appUrls = manifestUrls.filter(
    (url) => url === "/app" || url.startsWith("/app/"),
  );

  afterEach(cleanup);

  it("seeds a locale that is not the one jsdom would pick on its own", () => {
    // THE CANARY. Every destination below is written against `SEED`, and this is what
    // makes that a real assertion: if the override ever stopped working (storage blocked,
    // key renamed, navigator changed), `resolveInitialLocale()` would fall back to `"en"`
    // and a component that had started hard-coding `"en"` would pass the whole file.
    localStorage.setItem(LANGUAGE_STORAGE_KEY, SEED);
    expect(resolveInitialLocale()).toBe(SEED);
    expect(resolveInitialLocale()).not.toBe(DEFAULT_LOCALE);
  });

  it("has a start_url and shortcuts to check", () => {
    // A rename that silently empties the list above is the one way this block can stop
    // asserting anything, so the shape it reads is pinned rather than assumed.
    expect(manifest.start_url).toBeTruthy();
    expect((manifest.shortcuts ?? []).length).toBeGreaterThan(0);
    expect(appUrls.length).toBeGreaterThan(0);
  });

  it("resolves every URL the manifest sends the OS to", () => {
    // THE ASSERTION THE BUG FAILED. `/app/tday`, `/app/floater` and `/app/settings` all
    // reach the catch-all on the tree this was written against.
    for (const url of manifestUrls) {
      expect(is404(url), `${url} falls through to the Page-not-found catch-all`).toBe(
        false,
      );
    }
  });

  it("lands every app URL the manifest sends the OS on, locale and all", async () => {
    // THE ASSERTION THAT REPLACES "is not the catch-all". Matching a route is not the
    // same as arriving somewhere: this reads the location the REAL table and the REAL
    // element settled on, so `/app/*` rendering anything that does not redirect — or
    // redirecting to a hard-coded locale — fails here rather than at a user's cold start.
    for (const url of appUrls) {
      expect(await settle(url, SEED), `${url} does not land on the app`).toBe(
        `/${SEED}${url}`,
      );
    }
  });

  it("keeps every manifest URL locale-less, because one file serves ten locales", () => {
    // The tempting "fix" is to write `/en/app/tday` into the manifest. It is one static
    // file (vite.config.ts sets `manifest: false`), so that would hand nine locales a
    // cold start in English. The locale is resolved at runtime, in the router.
    for (const url of manifestUrls) {
      const first = url.split("/")[1];
      expect(
        (SUPPORTED_LOCALES as readonly string[]).includes(first),
        `${url} hard-codes the "${first}" locale into a file every locale is served`,
      ).toBe(false);
    }
  });

  it("carries the paths the app actually navigates to, including the deep link", async () => {
    // Path, query and fragment, each one a way to lose part of the target: a bare `/app`
    // (one segment shorter, and it matched `/:locale`'s index child as `locale = "app"`
    // before this route existed), a nested path, the deep link whose QUERY is the
    // destination, and a fragment.
    for (const url of [
      "/app",
      "/app/tday",
      "/app/floater-list/abc",
      "/app/completed?scope=floater",
      "/app/tday#focus",
    ]) {
      expect(await settle(url, SEED), `${url} is not resolvable`).toBe(
        `/${SEED}${url}`,
      );
    }
  });

  it("resolves the locale when it renders, rather than baking one in", async () => {
    // THE BLIND SPOT, IN ONE TEST. The same entry under two different seeds must land in
    // two different locales. A `localizePath(pathname, "en")` — or any constant — passes
    // one of these and fails the other; only reaching for the resolver passes both.
    expect(await settle("/app/tday", "fr")).toBe("/fr/app/tday");
    expect(await settle("/app/tday", "ja")).toBe("/ja/app/tday");
  });

  it("still sends a genuinely unknown URL to the 404, and leaves it there", async () => {
    // The other direction, and the one a lazy fix breaks: widening the redirect to "any
    // first segment that is not a supported locale" would swallow both of these and make
    // the catch-all near-unreachable. `/nope` is not here because it already renders the
    // LandingPage with `locale = "nope"`, which is its own deliberate behaviour. The
    // settled location is asserted too, so a redirect that quietly rescued a real 404
    // fails even if it kept a `*` somewhere in its branch.
    for (const url of ["/en/nope", "/en/app/garbage", "/en/app/nope"]) {
      expect(is404(url), `${url} should still be a 404`).toBe(true);
      expect(await settle(url, SEED), `${url} was rescued from the 404`).toBe(url);
    }
  });

  it("cannot bounce back into the locale-less route it came from", async () => {
    // Loop safety, stated as a property of where it LANDS rather than trusted to the
    // implementation: a redirect whose target also carried a locale-less `/app` would
    // redirect again, forever. `/app/*` can never re-match a destination that starts with
    // a real locale segment and `app` is not one of them — and a loop would never settle
    // on a location, so the harness reports it as a failure rather than hanging.
    expect(appUrls.length, "no locale-less app URL is left to check").toBeGreaterThan(0);
    for (const url of appUrls) {
      const landed = await settle(url, SEED);
      expect(landed.split("/")[1], `${url} redirects to ${landed}`).toBe(SEED);
      expect(is404(landed), `${url} redirects into the 404: ${landed}`).toBe(false);
    }
  });
});

/**
 * THE MECHANISM THE WHOLE CONVENTION RESTS ON
 *
 * `localizePath` is six lines and, until this file, had no test at all: a repo-wide
 * search found no assertion on a localized `href` and none on the function itself. It is
 * the only thing standing between roughly thirty locale-less `/app/...` literals and the
 * same 404 the manifest shipped, and a "simplification" of it would break in-app
 * navigation exactly the way the manifest is broken with all 1515 other tests green.
 *
 * The router's own locale-less entry routes through the same function rather than
 * rebuilding `/${locale}${path}`, so these cases cover both callers at once.
 */
describe("the localizer every in-app /app literal relies on", () => {
  it("prefixes a locale-less app path", () => {
    expect(localizePath("/app/tday", "fr")).toBe("/fr/app/tday");
    expect(localizePath("/app", "ja")).toBe("/ja/app");
  });

  it("keeps whatever follows the path, because a deep link is not decoration", () => {
    expect(localizePath("/app/completed?scope=floater", "en")).toBe(
      "/en/app/completed?scope=floater",
    );
  });

  it("leaves an already-localized path alone instead of double-prefixing", () => {
    for (const locale of SUPPORTED_LOCALES) {
      expect(localizePath(`/${locale}/app/today`, "en")).toBe(`/${locale}/app/today`);
    }
  });

  it("does not mistake a segment that merely starts with a locale for one", () => {
    // The prefix test is on the whole segment, not on its first characters: `/app-…`
    // and `/english/…` are not locale segments and must still be prefixed.
    expect(localizePath("/app-ish/x", "en")).toBe("/en/app-ish/x");
    expect(localizePath("/english/x", "en")).toBe("/en/english/x");
  });
});

/**
 * AND THE EMITTERS DETECTION CANNOT SEE
 *
 * Comments are stripped first, deliberately: a doc comment that shows the wrong way to
 * write a target (`// never router.push("/app/tday")`) is documentation, and a test that
 * fails on it is a test somebody deletes. What is left is code, and what is looked for in
 * it is an EMITTED URL — a `to=`/`href=`/`router.push(`/`navigate(` literal, or a
 * `window.location` assignment — never a route MATCHING test like
 * `pathname.includes("/app/list/")`. Matchers are correct either way, because a localized
 * pathname still contains the substring, and folding them into this scan as an allowlist
 * is exactly how the scan would rot into one that always passes.
 *
 * On the tree this was written against the scan reports zero offenders. That is the
 * correct starting state: it pins the convention, it does not fix a bug.
 */
describe("nothing emits a locale-less app URL the localizer cannot reach", () => {
  const SRC = resolve(__dirname, "../../src");

  /**
   * TypeScript with its comments blanked, so the scan below reads code and not prose.
   * Quote-aware, because `href="https://…"` is full of characters that look like the
   * start of a comment.
   */
  function stripComments(source: string): string {
    let out = "";
    let quote: string | null = null;
    for (let i = 0; i < source.length; i++) {
      const char = source[i];
      const next = source[i + 1];
      if (quote) {
        out += char;
        if (char === "\\") {
          out += next ?? "";
          i++;
        } else if (char === quote) {
          quote = null;
        }
        continue;
      }
      if (char === '"' || char === "'" || char === "`") {
        quote = char;
        out += char;
        continue;
      }
      if (char === "/" && next === "*") {
        const end = source.indexOf("*/", i + 2);
        const skipped = source.slice(i, end < 0 ? source.length : end + 2);
        out += skipped.replace(/[^\n]/g, " ");
        i += skipped.length - 1;
        continue;
      }
      if (char === "/" && next === "/") {
        const end = source.indexOf("\n", i);
        i = (end < 0 ? source.length : end) - 1;
        continue;
      }
      out += char;
    }
    return out;
  }

  function sourceFiles(dir: string, out: string[] = []): string[] {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const full = join(dir, entry.name);
      if (entry.isDirectory()) sourceFiles(full, out);
      else if (/\.tsx?$/.test(entry.name)) out.push(full);
    }
    return out;
  }

  /**
   * A LOCALE-LESS APP URL IN AN EMITTING POSITION. `["'`]` covers the template-literal
   * form, which is how half of these are written (`router.push(\`/app/list/${id}\`)`).
   */
  const EMITS_LOCALE_LESS_APP =
    /\b(?:to|href)\s*=\s*["'`]\/app(?:\/|["'`])|\b(?:router\.(?:push|replace)|navigate)\(\s*["'`]\/app(?:\/|["'`])|\blocation\.(?:href\s*=|assign\(|replace\()\s*["'`]?\/app\//;

  /** The two shapes `@/lib/navigation` cannot fix by being imported: a raw anchor or a raw location write. */
  const BYPASSES_THE_WRAPPER =
    /<a\s[^>]*href\s*=\s*["'`]\/app|\blocation\.(?:href\s*=|assign\(|replace\()\s*["'`]?\/app\//;

  function offenders(pattern: RegExp): string[] {
    const found: string[] = [];
    for (const file of sourceFiles(SRC)) {
      const code = stripComments(readFileSync(file, "utf8"));
      const match = code.match(pattern);
      if (match) found.push(`${relative(SRC, file)}: ${match[0].trim()}`);
    }
    return found;
  }

  it("sends every emitted /app/... literal through the localizer", () => {
    // The convention, pinned: a file that emits one of these must import `Link` or
    // `useRouter` from `@/lib/navigation`, never from `react-router-dom`. Swapping that
    // import is a one-line diff that reintroduces the reported bug silently — which is
    // the whole reason this file exists rather than a list of the paths that were broken.
    const unreachable = offenders(EMITS_LOCALE_LESS_APP).filter((hit) => {
      const file = resolve(SRC, hit.slice(0, hit.indexOf(": ")));
      return !/from\s+["']@\/lib\/navigation["']/.test(
        stripComments(readFileSync(file, "utf8")),
      );
    });
    expect(
      unreachable,
      "these files emit a locale-less /app URL but do not import @/lib/navigation",
    ).toEqual([]);
  });

  it("has no raw anchor or location write pointing at a locale-less app path", () => {
    // A file can import the localizer for something else and still emit through an `<a>`
    // or `window.location`, neither of which the wrapper can intercept.
    expect(
      offenders(BYPASSES_THE_WRAPPER),
      "these bypass @/lib/navigation, which is the only thing that localizes a target",
    ).toEqual([]);
  });
});
