import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import Backend from "i18next-http-backend";

import en from "../messages/en.json";

export const SUPPORTED_LOCALES = [
  "en", "zh", "de", "ja", "ar", "ru", "es", "fr", "ms", "it", "pt",
] as const;
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number];
export const DEFAULT_LOCALE: SupportedLocale = "en";

type TranslationResources = Record<string, unknown>;

const TRANSLATION_NAMESPACES = Object.keys(en);

function createNamespacedResources(messages: TranslationResources) {
  return Object.fromEntries(
    TRANSLATION_NAMESPACES.map((namespace) => [namespace, messages[namespace] ?? {}]),
  );
}

function parseTranslationNamespaces(
  data: string,
  namespaces?: string | string[],
) {
  const messages = JSON.parse(data) as TranslationResources;
  const requestedNamespaces = Array.isArray(namespaces)
    ? namespaces
    : namespaces
      ? [namespaces]
      : ["translation"];

  if (requestedNamespaces.length === 1) {
    const [requestedNamespace] = requestedNamespaces;

    if (requestedNamespace === "translation") {
      return messages;
    }

    return (messages[requestedNamespace] as TranslationResources | undefined) ?? {};
  }

  return Object.fromEntries(
    requestedNamespaces.map((namespace) => [
      namespace,
      namespace === "translation" ? messages : messages[namespace] ?? {},
    ]),
  );
}

i18n
  .use(Backend)
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: {
        translation: en,
        ...createNamespacedResources(en),
      },
    },
    partialBundledLanguages: true,
    fallbackLng: DEFAULT_LOCALE,
    supportedLngs: SUPPORTED_LOCALES as unknown as string[],
    ns: ["translation", ...TRANSLATION_NAMESPACES],
    defaultNS: "translation",
    load: "languageOnly",
    backend: {
      loadPath: "/locales/{{lng}}/translation.json",
      parse: (
        data: string,
        _languages?: string | string[],
        namespaces?: string | string[],
      ) => parseTranslationNamespaces(data, namespaces),
    },
    interpolation: { escapeValue: false },
    detection: {
      order: ["path", "localStorage", "navigator"],
      lookupFromPathIndex: 0,
    },
  });

export default i18n;
