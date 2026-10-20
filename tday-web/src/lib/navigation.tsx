import {
  Link as RouterLink,
  useNavigate,
  useLocation,
  useParams,
} from "react-router-dom";
import { forwardRef, type ComponentProps } from "react";
import i18n, { DEFAULT_LOCALE, SUPPORTED_LOCALES, type SupportedLocale } from "@/i18n";
import { startsRouteHandover } from "@/lib/routeHandover";

function resolveLocale(params: Record<string, string | undefined>): string {
  const fromParams = params.locale;
  if (fromParams && (SUPPORTED_LOCALES as readonly string[]).includes(fromParams)) {
    return fromParams;
  }
  return i18n.language || DEFAULT_LOCALE;
}

/**
 * The one place a locale-relative path becomes a URL. Exported because the router's
 * locale-less entry (`/app/...`, see LocaleAppRedirectPage) has to produce exactly
 * what every Link/useRouter call site produces, and two implementations of that
 * rule would be two chances to drift.
 */
export function localizePath(path: string, locale: string): string {
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
