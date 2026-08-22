import { useEffect, useRef, useState } from "react";
import { ImagePlus, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import type { AttachmentTaskType } from "@/types";
import { useToast } from "@/hooks/use-toast";
import { useIsLocalMode } from "@/hooks/useAppMode";
import { SheetCard, SheetSectionTitle } from "@/components/ui/sheet-chrome";
import {
  attachmentImageUrl,
  attachmentThumbnailUrl,
  useAttachments,
  useDeleteAttachment,
  useUploadAttachment,
} from "@/features/attachments/query/use-attachments";

/** Mirrors the server's limits so an upload that cannot succeed is refused before it starts. */
const MAX_PER_TASK = 6;
const MAX_BYTES = 10 * 1024 * 1024;
const ACCEPTED_TYPES = ["image/jpeg", "image/png"];

/**
 * Pictures attached to a task, for both feeds — pass `taskType` to say which one.
 *
 * Only rendered for a saved task: an attachment needs a task id to hang off, exactly like
 * steps. In Local Mode the section explains itself instead of appearing broken; the local
 * workspace is a single JSON document in browser storage and cannot hold photographs.
 */
export default function TaskAttachmentsSection({
  taskType,
  taskId,
}: {
  taskType: AttachmentTaskType;
  taskId: string;
}) {
  const { t: appDict } = useTranslation("app");
  const { toast } = useToast();
  const isLocalMode = useIsLocalMode();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [preview, setPreview] = useState<string | null>(null);

  const { data: attachments } = useAttachments(taskType, taskId, !isLocalMode);
  const uploadAttachment = useUploadAttachment();
  const deleteAttachment = useDeleteAttachment();

  const items = attachments ?? [];
  const isFull = items.length >= MAX_PER_TASK;

  // Escape closes the lightbox. Registered unconditionally (and inert while `preview` is null)
  // because hooks cannot sit behind the Local Mode early return below.
  useEffect(() => {
    if (!preview) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setPreview(null);
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [preview]);

  if (isLocalMode) {
    return (
      <>
        <SheetSectionTitle>{appDict("attachments")}</SheetSectionTitle>
        <SheetCard>
          <p className="px-[18px] py-3 text-sm font-bold text-muted-foreground">
            {appDict("attachmentsLocalModeHint")}
          </p>
        </SheetCard>
      </>
    );
  }

  function handleFiles(fileList: FileList | null) {
    const file = fileList?.[0];
    if (!file) return;

    if (!ACCEPTED_TYPES.includes(file.type)) {
      toast({ description: appDict("attachmentsUnsupportedType"), variant: "destructive" });
      return;
    }
    if (file.size > MAX_BYTES) {
      toast({ description: appDict("attachmentsTooLarge"), variant: "destructive" });
      return;
    }

    uploadAttachment.mutate(
      { taskType, taskId, file },
      {
        onError: () =>
          toast({ description: appDict("attachmentsUploadFailed"), variant: "destructive" }),
      },
    );
  }

  return (
    <>
      <SheetSectionTitle>
        {appDict("attachments")}
        {items.length > 0 ? ` ${items.length}/${MAX_PER_TASK}` : ""}
      </SheetSectionTitle>
      <SheetCard>
        <div className="flex flex-wrap gap-2 px-[18px] py-3">
          {items.map((attachment) => (
            <div key={attachment.id} className="group relative">
              <button
                type="button"
                onClick={() => setPreview(attachmentImageUrl(attachment.id))}
                aria-label={attachment.fileName}
                className="block h-20 w-20 overflow-hidden rounded-xl border border-border"
              >
                <img
                  src={attachmentThumbnailUrl(attachment.id)}
                  alt={attachment.fileName}
                  loading="lazy"
                  className="h-full w-full object-cover"
                />
              </button>
              <button
                type="button"
                aria-label={`${appDict("attachmentsRemove")}: ${attachment.fileName}`}
                onClick={() =>
                  deleteAttachment.mutate(
                    { id: attachment.id, taskType, taskId },
                    {
                      onError: () =>
                        toast({
                          description: appDict("attachmentsDeleteFailed"),
                          variant: "destructive",
                        }),
                    },
                  )
                }
                className="absolute -right-1.5 -top-1.5 rounded-full bg-background p-1 text-muted-foreground shadow ring-1 ring-border hover:text-foreground"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            </div>
          ))}

          {!isFull && (
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={uploadAttachment.isPending}
              aria-label={appDict("attachmentsAdd")}
              className="flex h-20 w-20 items-center justify-center rounded-xl border border-dashed border-border text-muted-foreground hover:text-foreground disabled:opacity-50"
            >
              <ImagePlus className="h-5 w-5" />
            </button>
          )}

          <input
            ref={fileInputRef}
            type="file"
            accept={ACCEPTED_TYPES.join(",")}
            className="hidden"
            onChange={(event) => {
              handleFiles(event.target.files);
              // Reset so picking the same file twice in a row still fires a change event.
              event.target.value = "";
            }}
          />
        </div>

        {items.length === 0 && !uploadAttachment.isPending && (
          <p className="px-[18px] pb-3 text-sm font-bold text-muted-foreground">
            {appDict("attachmentsEmpty")}
          </p>
        )}
      </SheetCard>

      {preview && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label={appDict("attachments")}
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4"
        >
          {/*
            A real button rather than a click handler on the backdrop div: tapping outside to
            dismiss then works from the keyboard too, and it keeps the dismiss affordance
            something a screen reader can announce.
          */}
          <button
            type="button"
            aria-label={appDict("attachmentsClosePreview")}
            onClick={() => setPreview(null)}
            className="absolute inset-0 cursor-default"
          />
          <img
            src={preview}
            alt={appDict("attachments")}
            className="pointer-events-none relative max-h-full max-w-full rounded-xl object-contain"
          />
          <button
            type="button"
            aria-label={appDict("attachmentsClosePreview")}
            onClick={() => setPreview(null)}
            className="absolute right-4 top-4 rounded-full bg-background/90 p-2"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
      )}
    </>
  );
}
