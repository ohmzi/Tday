import { useCallback } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import ClickableToast from "@/hooks/ClickableToast";
import { useVersionGate } from "@/hooks/useVersionGate";

/**
 * App-wide stale-build guard. Detects new deploys and either reloads silently
 * (idle tab, not editing) or offers a non-blocking update toast. Renders nothing.
 *
 * The offer goes through [ClickableToast] rather than drawing its own box, which
 * is what every other toast in the app does. It used to hand-roll a bordered,
 * backdrop-blurred card with a `DownloadCloud` badge, and a custom toast is
 * `data-styled="false"`: sonner's own `<li>` is what supplies the frosted pill,
 * so the hand-rolled box sat next to the undo toasts looking like a different
 * component from a different app — larger, lighter, and off the shared radius.
 * The two strings were literal English in all ten locales; they are keys now.
 */
export default function VersionGate() {
  const { t: appDict } = useTranslation("app");

  const onPromptUpdate = useCallback(
    (reload: () => void) => {
      toast.custom(
        (id) => {
          const accept = () => {
            toast.dismiss(id);
            reload();
          };
          return (
            <ClickableToast
              title={appDict("versionAvailableTitle")}
              description={appDict("versionAvailableBody")}
              // Body and action are separate targets that accept the same offer:
              // the reader who taps the words and the reader who looks for the
              // named control both get the update, and neither has to guess that
              // an unlabelled card was a button.
              onClick={accept}
              action={{ label: appDict("versionAvailableReload"), onClick: accept }}
            />
          );
        },
        // Pinned rather than timed out: the prompt is the only way a reader on an
        // idle tab learns the build moved under them, and it is dismissed by
        // acting on it. See the note on `duration` in `useVersionGate`.
        { duration: Infinity },
      );
    },
    [appDict],
  );

  useVersionGate({ onPromptUpdate });
  return null;
}
