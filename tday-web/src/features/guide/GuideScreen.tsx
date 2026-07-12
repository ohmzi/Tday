import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { ArrowLeft, ChevronRight, Search, Sparkles, X } from "lucide-react";
import { cn } from "@/lib/utils";
import { GuideIcon } from "./GuideIcon";
import {
  GUIDE_CURRENT_VERSION,
  GUIDE_SECTIONS,
  GUIDE_TOPICS,
  type GuideTopicDef,
  markGuideSeen,
  readLastSeenGuideVersion,
  topicsInSection,
  whatsNewTopics,
} from "./guideContent";
import { buildDoc, rank } from "./guideSearch";

function isNew(topic: GuideTopicDef): boolean {
  return topic.sinceVersion === GUIDE_CURRENT_VERSION;
}

export default function GuideScreen() {
  const { t } = useTranslation();
  const { locale, topicId: topicIdParam } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const focusTopicId = topicIdParam ?? searchParams.get("topic");
  const [query, setQuery] = useState("");
  const [expandedId, setExpandedId] = useState<string | null>(focusTopicId ?? null);
  const cardRefs = useRef<Record<string, HTMLDivElement | null>>({});

  // NEW badges show until the guide has been opened in this release: read the
  // persisted last-seen version once, then mark the running release as seen.
  const [lastSeenVersion] = useState(readLastSeenGuideVersion);
  const showNewBadges = lastSeenVersion !== GUIDE_CURRENT_VERSION;
  useEffect(() => {
    markGuideSeen();
  }, []);

  // Build search docs exactly as the exporter does, so ranking matches the
  // shared Kotlin engine (verified by tests/unit/guide-search.test.ts).
  const docs = useMemo(
    () =>
      GUIDE_TOPICS.map((topic) =>
        buildDoc(
          topic.id,
          t(topic.titleKey),
          t(topic.keywordsKey),
          [t(topic.summaryKey), ...topic.body.flatMap((b) => b.keys.map((k) => t(k)))].join(" "),
        ),
      ),
    [t],
  );

  const trimmed = query.trim();
  const rankedIds = useMemo(() => (trimmed ? rank(query, docs) : []), [query, docs, trimmed]);
  const byId = useMemo(() => Object.fromEntries(GUIDE_TOPICS.map((tp) => [tp.id, tp])), []);

  useEffect(() => {
    if (!focusTopicId) return;
    setExpandedId(focusTopicId);
    const node = cardRefs.current[focusTopicId];
    if (node) node.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [focusTopicId]);

  const whatsNew = useMemo(() => whatsNewTopics(), []);

  const renderCard = (topic: GuideTopicDef) => (
    <TopicCard
      key={topic.id}
      topic={topic}
      showNew={showNewBadges && isNew(topic)}
      expanded={expandedId === topic.id}
      onToggle={() => setExpandedId((cur) => (cur === topic.id ? null : topic.id))}
      onTryIt={(seg) => navigate(`/${locale}/app/${seg}`)}
      registerRef={(el) => (cardRefs.current[topic.id] = el)}
    />
  );

  return (
    <div className="min-h-dvh bg-background text-foreground">
      <div className="mx-auto w-full max-w-3xl px-4 pb-24 pt-6 sm:px-6">
        <header className="mb-5">
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="mb-4 inline-flex items-center gap-1.5 rounded-lg px-2 py-1 text-sm text-card-foreground-muted transition-colors hover:text-foreground focus-visible:outline-2 focus-visible:outline-accent"
          >
            <ArrowLeft className="size-4" aria-hidden="true" />
            {t("settings.title", "Settings")}
          </button>
          <h1 className="text-2xl font-black tracking-tight sm:text-3xl">{t("guide.title")}</h1>
          <p className="mt-1 max-w-prose text-sm text-card-foreground-muted">{t("guide.subtitle")}</p>
        </header>

        {/* Search */}
        <div className="sticky top-2 z-10 mb-5">
          <div className="relative">
            <Search
              className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-card-foreground-muted"
              aria-hidden="true"
            />
            <input
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={t("guide.searchPlaceholder")}
              aria-label={t("guide.searchAria")}
              className="w-full rounded-xl border border-border bg-card py-3 pl-10 pr-10 text-sm text-card-foreground shadow-sm outline-none transition-colors focus:border-accent"
            />
            {query && (
              <button
                type="button"
                onClick={() => setQuery("")}
                aria-label={t("guide.clearSearch")}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded-md p-1.5 text-card-foreground-muted transition-colors hover:text-foreground focus-visible:outline-2 focus-visible:outline-accent"
              >
                <X className="size-4" aria-hidden="true" />
              </button>
            )}
          </div>
          {trimmed && (
            <p className="mt-2 px-1 text-xs text-card-foreground-muted" aria-live="polite">
              {t("guide.results", { count: rankedIds.length })}
            </p>
          )}
        </div>

        {/* Results */}
        {trimmed ? (
          rankedIds.length > 0 ? (
            <div className="flex flex-col gap-2.5">{rankedIds.map((id) => renderCard(byId[id]))}</div>
          ) : (
            <p className="rounded-xl border border-dashed border-border px-4 py-10 text-center text-sm text-card-foreground-muted">
              {t("guide.noResults")}
            </p>
          )
        ) : (
          <div className="flex flex-col gap-8">
            {whatsNew.length > 0 && (
              <section>
                <SectionHeading icon>{t("guide.whatsNew")}</SectionHeading>
                <div className="flex flex-col gap-2.5">{whatsNew.map(renderCard)}</div>
              </section>
            )}
            {GUIDE_SECTIONS.map((section) => {
              const topics = topicsInSection(section.id);
              if (topics.length === 0) return null;
              return (
                <section key={section.id}>
                  <SectionHeading>{t(section.titleKey)}</SectionHeading>
                  <div className="flex flex-col gap-2.5">{topics.map(renderCard)}</div>
                </section>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

function SectionHeading({ children, icon }: { children: React.ReactNode; icon?: boolean }) {
  return (
    <h2 className="mb-2.5 flex items-center gap-1.5 px-1 text-xs font-bold uppercase tracking-wider text-card-foreground-muted">
      {icon && <Sparkles className="size-3.5 text-accent" aria-hidden="true" />}
      {children}
    </h2>
  );
}

function TopicCard({
  topic,
  showNew,
  expanded,
  onToggle,
  onTryIt,
  registerRef,
}: {
  topic: GuideTopicDef;
  showNew: boolean;
  expanded: boolean;
  onToggle: () => void;
  onTryIt: (segment: string) => void;
  registerRef: (el: HTMLDivElement | null) => void;
}) {
  const { t } = useTranslation();
  // Web is server-mode-only (no Local Mode concept), so serverOnly topics never
  // hide their Try-it button here; Android/iOS gate theirs behind !isLocalMode.
  const tryItSegment = topic.deepLink?.web ?? null;

  return (
    <div
      ref={registerRef}
      className="overflow-hidden rounded-xl border border-border bg-card scroll-mt-20"
    >
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={expanded}
        className="flex w-full items-center gap-3 px-4 py-3.5 text-left transition-colors hover:bg-card-muted focus-visible:outline-2 focus-visible:outline-accent"
      >
        <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-card-muted text-accent">
          <GuideIcon name={topic.icon} className="size-[18px]" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <span className="font-semibold text-card-foreground">{t(topic.titleKey)}</span>
            <TopicBadges topic={topic} showNew={showNew} />
          </span>
          <span className="mt-0.5 block truncate text-[13px] text-card-foreground-muted">
            {t(topic.summaryKey)}
          </span>
        </span>
        <ChevronRight
          className={cn(
            "size-4 shrink-0 text-card-foreground-muted transition-transform",
            expanded && "rotate-90",
          )}
          aria-hidden="true"
        />
      </button>

      {expanded && (
        <div className="border-t border-border px-4 py-4">
          <div className="flex flex-col gap-3 text-sm leading-relaxed text-card-foreground">
            {topic.body.map((block, i) => (
              <BodyBlock key={i} type={block.type} texts={block.keys.map((k) => t(k))} />
            ))}
          </div>
          {tryItSegment && (
            <button
              type="button"
              onClick={() => onTryIt(tryItSegment)}
              className="mt-4 inline-flex items-center gap-1.5 rounded-lg bg-accent px-3.5 py-2 text-sm font-semibold text-accent-foreground transition-opacity hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
            >
              {t("guide.tryIt")}
              <ChevronRight className="size-4" aria-hidden="true" />
            </button>
          )}
        </div>
      )}
    </div>
  );
}

function TopicBadges({ topic, showNew }: { topic: GuideTopicDef; showNew: boolean }) {
  const { t } = useTranslation();
  const badges: Array<{ key: string; label: string; tone: string }> = [];
  if (showNew)
    badges.push({ key: "new", label: t("guide.badges.new"), tone: "bg-accent/15 text-accent" });
  if (topic.badge === "HIDDEN_GEM")
    badges.push({
      key: "gem",
      label: t("guide.badges.hiddenGem"),
      tone: "bg-accent-purple/15 text-accent-purple",
    });
  if (topic.badge === "PRO_TIP")
    badges.push({
      key: "tip",
      label: t("guide.badges.proTip"),
      tone: "bg-accent-teal/15 text-accent-teal",
    });
  if (topic.serverOnly)
    badges.push({
      key: "server",
      label: t("guide.badges.server"),
      tone: "bg-card-muted text-card-foreground-muted",
    });

  return (
    <>
      {badges.map((b) => (
        <span
          key={b.key}
          className={cn(
            "rounded-md px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide",
            b.tone,
          )}
        >
          {b.label}
        </span>
      ))}
    </>
  );
}

function BodyBlock({ type, texts }: { type: string; texts: string[] }) {
  const text = texts[0] ?? "";
  switch (type) {
    case "STEPS":
      return (
        <ol className="ml-1 flex list-none flex-col gap-2">
          {texts.map((step, i) => (
            <li key={i} className="flex gap-2.5">
              <span className="grid size-5 shrink-0 place-items-center rounded-full bg-accent/15 text-[11px] font-bold text-accent">
                {i + 1}
              </span>
              <span>{step}</span>
            </li>
          ))}
        </ol>
      );
    case "TIP":
      return (
        <p className="rounded-lg border-l-2 border-accent bg-accent/[0.06] px-3 py-2 text-[13px] text-card-foreground-muted">
          {text}
        </p>
      );
    case "KBD":
      return (
        <p>
          <kbd className="rounded-md border border-border bg-card-muted px-2 py-1 font-mono text-xs text-card-foreground">
            {text}
          </kbd>
        </p>
      );
    case "EXAMPLE":
      return (
        <p className="rounded-lg bg-card-muted px-3 py-2 font-mono text-[13px] text-card-foreground">
          {text}
        </p>
      );
    default:
      return <p>{text}</p>;
  }
}
