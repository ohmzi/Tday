import type { Locale } from "date-fns";
import {
  enUS,
  es,
  fr,
  de,
  it,
  pt,
  ru,
  zhCN,
  ja,
  ms,
} from "date-fns/locale";

// Maps an i18n language code (the app's `i18n.language`, e.g. "en", "zh") to the
// matching date-fns Locale so calendar/date `format(...)` output honors the
// active language. Mirrors SUPPORTED_LOCALES in src/i18n.ts.
const DATE_FNS_LOCALES: Record<string, Locale> = {
  en: enUS,
  es,
  fr,
  de,
  it,
  pt,
  ru,
  zh: zhCN,
  ja,
  ms,
};

/**
 * Returns the date-fns Locale for the given i18n language code. Accepts full
 * tags too (e.g. "en-US", "zh-CN") by falling back to the base subtag.
 * Defaults to English (enUS) when no match is found.
 */
export function getDateFnsLocale(lang: string | undefined | null): Locale {
  if (!lang) return enUS;
  const exact = DATE_FNS_LOCALES[lang];
  if (exact) return exact;
  const base = lang.toLowerCase().split("-")[0];
  return DATE_FNS_LOCALES[base] ?? enUS;
}
