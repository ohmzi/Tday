import { CircleHelp } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Link } from "@/lib/navigation";
import { cn } from "@/lib/utils";

/**
 * A quiet "?" that deep-links from a feature surface into its guide topic
 * (`/:locale/guide/:topicId`). Reference topic ids from the shared GuideTopicIds.
 */
export function GuideHelpLink({
  topic,
  className,
}: {
  topic: string;
  className?: string;
}) {
  const { t } = useTranslation("guide");
  return (
    <Link
      href={`/guide/${topic}`}
      aria-label={t("title")}
      title={t("title")}
      className={cn(
        "inline-flex shrink-0 items-center justify-center rounded-full p-1 text-muted-foreground transition-colors hover:text-foreground",
        className,
      )}
    >
      <CircleHelp className="h-[18px] w-[18px]" aria-hidden="true" />
    </Link>
  );
}
