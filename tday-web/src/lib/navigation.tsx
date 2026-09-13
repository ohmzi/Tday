import {
  Link as RouterLink,
  useNavigate,
  useLocation,
  useParams,
} from "react-router-dom";
import { forwardRef, type ComponentProps } from "react";
import i18n, { DEFAULT_LOCALE, SUPPORTED_LOCALES, type SupportedLocale } from "@/i18n";

function resolveLocale(params: Record<string, string | undefined>): string {
  const fromParams = params.locale;
  if (fromParams && (SUPPORTED_LOCALES as readonly string[]).includes(fromParams)) {
    return fromParams;
  }
  return i18n.language || DEFAULT_LOCALE;
}

/**
 * Whether this navigation is one the route hand-over has anything to say about,
 * and one the browser can actually play.
 *
 * Two questions, and both have to be answered here rather than in `globals.css`,
 * because a view transition is started by the navigation and not by the
 * stylesheet.
 *
 * The first is the same question `RouteFade` answers when it keys its fade on
 * `pathname` and not on the full location: a query-string change — the task-focus
 * params the timeline pages push — is the same screen answering itself. A view
 * transition snapshots the whole document, so starting one there would crossfade a
 * page with itself, which is the flash `RouteFade` already refuses to draw.
 *
 * The second is a feature test React Router does not need — it falls back to a
 * plain state update on its own — but does warn about, once per app, on every
 * environment that cannot honour the opt-in. jsdom is one of those. Asking a
 * question we can answer for free is cheaper than a warning nobody can act on.
 *
 * Asked per call rather than captured at module scope, for the reason
 * `prefersReducedMotion` gives about `matchMedia`: a module-scope read binds to
 * whatever the document was at import time, which in a test is before anything has
 * had a chance to stub it.
 */
function startsRouteHandover(target: string, current: string): boolean {
  if (typeof document === "undefined") return false;
  if (typeof document.startViewTransition !== "function") return false;
  const targetPath = target.split(/[?#]/)[0];
  // A `to` that is only a query string or only a fragment resolves to the page it
  // was clicked on, and splits to the empty string rather than to that page's path —
  // the one way this comparison can say "different" about a destination that is not.
  if (targetPath === "") return false;
  return targetPath !== current;
}

function localizePath(path: string, locale: string): string {
  if (!path.startsWith("/")) return path;
  for (const l of SUPPORTED_LOCALES) {
    if (path === `/${l}` || path.startsWith(`/${l}/`)) return path;
  }
  return `/${locale}${path}`;
}

type LinkProps = Omit<ComponentProps<typeof RouterLink>, "to"> & {
  href?: string;
  to?: string;
  locale?: SupportedLocale;
  prefetch?: boolean;
};

export const Link = forwardRef<HTMLAnchorElement, LinkProps>(function Link(
  { href, to, locale, prefetch, ...rest },
  ref,
) {
  void prefetch;
  const params = useParams();
  const { pathname } = useLocation();
  const currentLocale = resolveLocale(params);
  const targetLocale = locale || currentLocale;
  const path = href ?? to ?? "";
  const localizedPath = localizePath(path, targetLocale);

  // Before the spread, so a call site that has a reason to decide for itself still
  // wins — this is the app's default answer, not a policy.
  return (
    <RouterLink
      ref={ref}
      to={localizedPath}
      viewTransition={startsRouteHandover(localizedPath, pathname)}
      {...rest}
    />
  );
});

export function useRouter() {
  const navigate = useNavigate();
  const params = useParams();
  const { pathname } = useLocation();
  const locale = resolveLocale(params);

  return {
    push(path: string) {
      const target = localizePath(path, locale);
      navigate(target, { viewTransition: startsRouteHandover(target, pathname) });
    },
    replace(path: string) {
      const target = localizePath(path, locale);
      navigate(target, {
        replace: true,
        viewTransition: startsRouteHandover(target, pathname),
      });
    },
    refresh() {
      window.location.reload();
    },
    // No opt-in here, and none is available: `navigate(-1)` takes a delta rather
    // than a destination, and the browser's own back button does not come through
    // this module at all. A POP therefore gets the path that exists everywhere —
    // the arriving screen fades up, with no snapshot of the one being left.
    back() {
      navigate(-1);
    },
  };
}

export function usePathname(): string {
  return useLocation().pathname;
}

export function useLocale(): string {
  const params = useParams();
  return resolveLocale(params);
}
