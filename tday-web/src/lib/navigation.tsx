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
  { href, to, locale, prefetch: _, ...rest },
  ref,
) {
  const params = useParams();
  const currentLocale = resolveLocale(params);
  const targetLocale = locale || currentLocale;
  const path = href ?? to ?? "";
  const localizedPath = localizePath(path, targetLocale);

  return <RouterLink ref={ref} to={localizedPath} {...(rest as any)} />;
});

export function useRouter() {
  const navigate = useNavigate();
  const params = useParams();
  const locale = resolveLocale(params);

  return {
    push(path: string) {
      navigate(localizePath(path, locale));
    },
    replace(path: string) {
      navigate(localizePath(path, locale), { replace: true });
    },
    refresh() {
      window.location.reload();
    },
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
