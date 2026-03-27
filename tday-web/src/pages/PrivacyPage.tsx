import { useTranslation } from "react-i18next";

export default function PrivacyPage() {
  const { t } = useTranslation("privacy");

  const sections = Array.from({ length: 9 }, (_, i) => i + 1);

  return (
    <main className="min-h-screen px-6 py-16">
      <div className="mx-auto max-w-3xl space-y-8">
        <header className="space-y-2">
          <h1 className="text-3xl font-semibold">{t("title")}</h1>
          <p className="text-sm">{t("lastUpdated")}</p>
        </header>

        <p>{t("intro")}</p>

        {sections.map((n) => {
          const content = t(`sections.${n}.content`, { returnObjects: true });
          return (
            <section key={n}>
              <h2 className="mb-2 text-xl font-medium">
                {n}. {t(`sections.${n}.title`)}
              </h2>
              {Array.isArray(content) ? (
                <ul className="list-disc space-y-1 pl-5">
                  {(content as string[]).map((item, idx) => (
                    <li key={idx}>{item}</li>
                  ))}
                </ul>
              ) : (
                <p>{String(content)}</p>
              )}
            </section>
          );
        })}
      </div>
    </main>
  );
}
