import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import LanguageDetector from "i18next-browser-languagedetector";

import en from "../messages/en.json";
import es from "../messages/es.json";
import fr from "../messages/fr.json";
import de from "../messages/de.json";
import it from "../messages/it.json";
import pt from "../messages/pt.json";
import ru from "../messages/ru.json";
import zh from "../messages/zh.json";
import ja from "../messages/ja.json";
import ms from "../messages/ms.json";

export const SUPPORTED_LOCALES = [
  "en", "es", "fr", "de", "it", "pt", "ru", "zh", "ja", "ms",
] as const;
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number];
export const DEFAULT_LOCALE: SupportedLocale = "en";

/** localStorage key that persists a manual language override (Settings picker). */
export const LANGUAGE_STORAGE_KEY = "tday.language";

type TranslationResources = Record<string, unknown>;

const MESSAGES: Record<SupportedLocale, TranslationResources> = {
  en, es, fr, de, it, pt, ru, zh, ja, ms,
};

const TRANSLATION_NAMESPACES = Object.keys(en);

function createNamespacedResources(messages: TranslationResources) {
  return Object.fromEntries(
    TRANSLATION_NAMESPACES.map((namespace) => [namespace, messages[namespace] ?? {}]),
  );
}

// All locales are bundled into the JS build — fully offline, no HTTP fetch.
const resources = Object.fromEntries(
  SUPPORTED_LOCALES.map((loc) => [
    loc,
    { translation: MESSAGES[loc], ...createNamespacedResources(MESSAGES[loc]) },
  ]),
);

/**
 * Resolve the initial locale without a URL param: a persisted manual override
 * wins, else the browser/OS language, else English. Used by the root redirect
 * so first-time visitors land on their detected language.
 */
export function resolveInitialLocale(): SupportedLocale {
  const supported = SUPPORTED_LOCALES as readonly string[];
  try {
    const stored = localStorage.getItem(LANGUAGE_STORAGE_KEY);
    if (stored && supported.includes(stored)) return stored as SupportedLocale;
  } catch {
    /* localStorage unavailable (SSR / privacy mode) — fall through */
  }
  const navLangs =
    typeof navigator !== "undefined"
      ? navigator.languages ?? [navigator.language]
      : [];
  for (const lang of navLangs) {
    const base = (lang || "").toLowerCase().split("-")[0];
    if (supported.includes(base)) return base as SupportedLocale;
  }
  return DEFAULT_LOCALE;
}

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: DEFAULT_LOCALE,
    supportedLngs: SUPPORTED_LOCALES as unknown as string[],
    nonExplicitSupportedLngs: true,
    ns: ["translation", ...TRANSLATION_NAMESPACES],
    defaultNS: "translation",
    load: "languageOnly",
    interpolation: { escapeValue: false },
    detection: {
      // URL locale segment wins (keeps deep links consistent), then a persisted
      // manual choice, then the browser/OS language (Safari/Chrome/Firefox).
      order: ["path", "localStorage", "navigator"],
      lookupFromPathIndex: 0,
      lookupLocalStorage: LANGUAGE_STORAGE_KEY,
      // Writes are handled manually by the Settings picker so a "System default"
      // choice can clear the override and fall back to the browser language.
      caches: [],
    },
  });

export default i18n;
