import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { ChevronRight, CircleHelp, Search, Sparkles, X } from "lucide-react";
import { cn } from "@/lib/utils";
import NativePageHeader from "@/components/app/NativePageHeader";
import {
  tdaySearchCapsuleClass,
  tdaySearchCapsuleClearClass,
  tdaySearchCapsuleIconClass,
  tdaySearchCapsuleInputClass,
} from "@/components/app/RootFeedHeroHeader";
import { nativeScreenAccentColors } from "@/components/app/nativeScreenTheme";
import { SheetCard } from "@/components/ui/sheet-chrome";
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
  const rowRefs = useRef<Record<string, HTMLDivElement | null>>({});

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
    const node = rowRefs.current[focusTopicId];
    if (node) node.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [focusTopicId]);

  const whatsNew = useMemo(() => whatsNewTopics(), []);

  const renderRow = (topic: GuideTopicDef, index: number) => (
    <div key={topic.id}>
      {index > 0 ? <CardDivider /> : null}
      <TopicRow
        topic={topic}
        showNew={showNewBadges && isNew(topic)}
        expanded={expandedId === topic.id}
        onToggle={() => setExpandedId((cur) => (cur === topic.id ? null : topic.id))}
        onTryIt={(seg) => navigate(`/${locale}/app/${seg}`)}
        registerRef={(el) => (rowRefs.current[topic.id] = el)}
      />
    </div>
  );

  // Same page skeleton as Settings: the shared collapsing page header, then a
  // stack of SheetCards — the guide is a settings sub-screen, not its own site.
  return (
    <div className="w-full space-y-3 pb-10">
      <NativePageHeader
        title={t("guide.title")}
        subtitle={t("guide.subtitle")}
        accentColor={nativeScreenAccentColors.settings}
        icon={CircleHelp}
        className="mb-1"
      />

      {/* The app's search field, same chrome and same parts as the root feeds'
          open capsule — see `tdaySearchCapsuleClass`. The clear button is the
          one deliberate difference: the root feeds' X dismisses a field that
          folds back into a button, and this one has no folded state to return
          to, so it clears the text and only appears when there is text. */}
      <div className="space-y-1.5">
        <div className={cn(tdaySearchCapsuleClass, "w-full")} style={{ height: 56 }}>
          <Search className={tdaySearchCapsuleIconClass} aria-hidden="true" />
          {/* type=text, not search: the app's search fields use their own clear
              button and WebKit would stack a second one on top of it. */}
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t("guide.searchPlaceholder")}
            aria-label={t("guide.searchAria")}
            className={tdaySearchCapsuleInputClass}
          />
          {query && (
            <button
              type="button"
              onClick={() => setQuery("")}
              aria-label={t("guide.clearSearch")}
              className={tdaySearchCapsuleClearClass}
            >
              <X className="h-5 w-5 stroke-[2.6]" aria-hidden="true" />
            </button>
          )}
        </div>
        {trimmed && (
          <p className="px-1 text-sm font-black text-muted-foreground" aria-live="polite">
            {t("guide.results", { count: rankedIds.length })}
          </p>
        )}
      </div>

      {trimmed ? (
        rankedIds.length > 0 ? (
          <GuideCard>{rankedIds.map((id, i) => renderRow(byId[id], i))}</GuideCard>
        ) : (
          <GuideCard>
            <p className="py-6 text-center text-sm font-extrabold text-muted-foreground">
              {t("guide.noResults")}
            </p>
          </GuideCard>
        )
      ) : (
        <>
          {whatsNew.length > 0 && (
            <GuideCard
              title={t("guide.whatsNew")}
              titleIcon={<Sparkles className="size-4 shrink-0 text-accent" aria-hidden="true" />}
            >
              {whatsNew.map(renderRow)}
            </GuideCard>
          )}
          {GUIDE_SECTIONS.map((section) => {
            const topics = topicsInSection(section.id);
            if (topics.length === 0) return null;
            return (
              <GuideCard key={section.id} title={t(section.titleKey)}>
                {topics.map(renderRow)}
              </GuideCard>
            );
          })}
        </>
      )}
    </div>
  );
}

/** Settings-style section card: SheetCard shell, 1.4rem black heading, rows inside. */
function GuideCard({
  title,
  titleIcon,
  children,
}: {
  title?: string;
  titleIcon?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <SheetCard className="space-y-3 p-[18px]">
      {title ? (
        <h2 className="flex items-center gap-2 text-[1.4rem] font-black leading-tight text-foreground">
          {titleIcon}
          {title}
        </h2>
      ) : null}
      <div>{children}</div>
    </SheetCard>
  );
}

function CardDivider() {
  return <div className="h-px bg-border/60" />;
}

function TopicRow({
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
    <div ref={registerRef} className="scroll-mt-24">
      {/* data-no-press: the app-wide press ripple assumes a roughly square
          target — on this wide, short row it balloons into an ugly clipped
          band, so this row gets its own contained background highlight. */}
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={expanded}
        data-no-press
        className="-mx-2 flex w-[calc(100%+1rem)] items-center gap-3.5 rounded-2xl px-2 py-3 text-left transition-colors hover:bg-muted-foreground/5 active:bg-muted-foreground/10"
      >
        <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-muted/70">
          <GuideIcon name={topic.icon} className="size-5 stroke-[2.4] text-accent" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <span className="text-base font-black text-foreground">{t(topic.titleKey)}</span>
            <TopicBadges topic={topic} showNew={showNew} />
          </span>
          <span className="mt-0.5 block truncate text-sm font-extrabold text-muted-foreground">
            {t(topic.summaryKey)}
          </span>
        </span>
        <ChevronRight
          className={cn(
            "size-5 shrink-0 text-muted-foreground transition-transform",
            expanded && "rotate-90",
          )}
          aria-hidden="true"
        />
      </button>

      {expanded && (
        <div className="pb-4 pl-[50px]">
          <div className="flex flex-col gap-3 text-sm font-bold leading-relaxed text-foreground">
            {topic.body.map((block, i) => (
              <BodyBlock key={i} type={block.type} texts={block.keys.map((k) => t(k))} />
            ))}
          </div>
          {tryItSegment && (
            <button
              type="button"
              onClick={() => onTryIt(tryItSegment)}
              className="mt-4 inline-flex h-11 items-center gap-1.5 rounded-full bg-accent px-4 text-sm font-black text-accent-foreground transition active:opacity-80 hover:opacity-90"
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
      tone: "bg-muted/70 text-muted-foreground",
    });

  return (
    <>
      {badges.map((b) => (
        <span
          key={b.key}
          className={cn("rounded-full px-2 py-0.5 text-[10px] font-black uppercase tracking-wide", b.tone)}
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
        <ol className="flex list-none flex-col gap-2">
          {texts.map((step, i) => (
            <li key={i} className="flex gap-2.5">
              <span className="grid size-5 shrink-0 place-items-center rounded-full bg-accent/15 text-[11px] font-black text-accent">
                {i + 1}
              </span>
              <span>{step}</span>
            </li>
          ))}
        </ol>
      );
    case "TIP":
      return (
        <p className="rounded-2xl border-l-2 border-accent bg-accent/[0.06] px-3.5 py-2.5 text-sm font-bold text-muted-foreground">
          {text}
        </p>
      );
    case "KBD":
      return (
        <p>
          <kbd className="rounded-lg bg-muted/70 px-2.5 py-1.5 font-mono text-xs font-black text-foreground">
            {text}
          </kbd>
        </p>
      );
    case "EXAMPLE":
      return (
        <p className="rounded-2xl bg-muted/70 px-3.5 py-2.5 font-mono text-sm font-bold text-foreground">
          {text}
        </p>
      );
    default:
      return <p>{text}</p>;
  }
}
