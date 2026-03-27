import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import LanguageDetector from "i18next-browser-languagedetector";

import en from "../messages/en.json";
import zh from "../messages/zh.json";
import de from "../messages/de.json";
import ja from "../messages/ja.json";
import ar from "../messages/ar.json";
import ru from "../messages/ru.json";
import es from "../messages/es.json";
import fr from "../messages/fr.json";
import ms from "../messages/ms.json";
import it from "../messages/it.json";
import pt from "../messages/pt.json";

export const SUPPORTED_LOCALES = [
  "en", "zh", "de", "ja", "ar", "ru", "es", "fr", "ms", "it", "pt",
] as const;
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number];
export const DEFAULT_LOCALE: SupportedLocale = "en";

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: { en, zh, de, ja, ar, ru, es, fr, ms, it, pt },
    fallbackLng: DEFAULT_LOCALE,
    supportedLngs: SUPPORTED_LOCALES as unknown as string[],
    interpolation: { escapeValue: false },
    detection: {
      order: ["path", "localStorage", "navigator"],
      lookupFromPathIndex: 0,
    },
  });

export default i18n;
