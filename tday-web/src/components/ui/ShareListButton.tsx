import { Share2 } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { useToast } from "@/hooks/use-toast";
import { buildListShareText, type ShareableTodo } from "@/lib/listShareText";

// Shares a list as plain text through the native share sheet when available
// (mobile browsers), otherwise copies it to the clipboard.
const ShareListButton = ({
  listName,
  todos,
  className,
}: {
  listName: string;
  todos: ShareableTodo[];
  className?: string;
}) => {
  const { t: appDict, i18n } = useTranslation("app");
  const { toast } = useToast();

  const handleShare = async () => {
    const text = buildListShareText({
      listName,
      todos,
      lang: i18n.language,
      t: appDict,
    });
    if (navigator.share) {
      try {
        await navigator.share({ title: listName, text });
      } catch {
        // Cancelled by the user — nothing to do.
      }
      return;
    }
    await navigator.clipboard.writeText(text);
    toast({ description: appDict("shareListCopied") });
  };

  return (
    <Button
      type="button"
      variant="ghost"
      size="icon"
      className={className}
      onClick={handleShare}
      aria-label={appDict("shareList")}
    >
      <Share2 className="h-5 w-5" />
    </Button>
  );
};

export default ShareListButton;
