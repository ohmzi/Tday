import { useTranslation } from "react-i18next";
import { useToast } from "@/hooks/use-toast";
import { buildListShareText, type ShareableTodo } from "@/lib/listShareText";

// Shares a list as plain text through the native share sheet when available
// (mobile browsers), otherwise copies it to the clipboard. Exposed as a hook
// so the action can live inside the list form sheet's Sharing section.
export function useShareListAsText({
  listName,
  todos,
}: {
  listName: string;
  todos: ShareableTodo[];
}) {
  const { t: appDict, i18n } = useTranslation("app");
  const { toast } = useToast();

  return async () => {
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
}
