import { CircleHelp } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Link } from "@/lib/navigation";
import { cn } from "@/lib/utils";

/**
 * A quiet "?" that deep-links from a feature surface into its guide topic
 * (`/:locale/app/guide/:topicId`). Reference topic ids from the shared GuideTopicIds.
 */
export function GuideHelpLink({
  topic,
  className,
  withLabel,
  label,
}: {
  topic: string;
  className?: string;
  /** Also render the guide title next to the icon (for menu rows). */
  withLabel?: boolean;
  /**
   * What the "?" is about, for a link that sits on one row rather than heading a card: "About
   * crash & problem reports" says more to a screen reader than the guide's own title does.
   */
  label?: string;
}) {
  const { t } = useTranslation("guide");
  const accessibleName = label ?? t("title");
  return (
    <Link
      href={`/app/guide/${topic}`}
      aria-label={accessibleName}
      title={accessibleName}
      className={cn(
        "inline-flex shrink-0 items-center justify-center gap-1.5 rounded-full p-1 text-muted-foreground transition-colors hover:text-foreground",
        className,
      )}
    >
      <CircleHelp className="h-[18px] w-[18px]" aria-hidden="true" />
      {withLabel && <span className="text-sm">{t("title")}</span>}
    </Link>
  );
}
