import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import Backend from "i18next-http-backend";

import en from "../messages/en.json";

// The app ships English-only. Keeping a single-locale list means the locale
// router segment and any stray `/xx/...` URL resolve to English.
export const SUPPORTED_LOCALES = ["en"] as const;
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
  .use(initReactI18next)
  .init({
    resources: {
      en: {
        translation: en,
        ...createNamespacedResources(en),
      },
    },
    // English-only: pin the language and skip any locale detection so
    // localStorage/navigator/path can never switch the app away from English.
    lng: DEFAULT_LOCALE,
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
  });

export default i18n;
