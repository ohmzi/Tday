import { useEffect, useRef, useState } from "react";
import { EditorContent, useEditor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import Underline from "@tiptap/extension-underline";
import Placeholder from "@tiptap/extension-placeholder";
import { Eraser } from "lucide-react";
import { useTranslation } from "react-i18next";
import { cn } from "@/lib/utils";
import {
  decodeNotesToHtml,
  encodeNotes,
  htmlHasFormatting,
  htmlToPlainText,
  sanitizeHtml,
} from "@/lib/richNotes";
import "./NotesField.css";

type NotesFieldProps = {
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
  // Enter submits the form when true; otherwise it just blurs (nothing to
  // save yet) — mirrors the plain-Enter-submits convention used by every
  // other sheet text field (see isSubmitEnter). Shift+Enter always inserts a
  // line break instead of submitting.
  onSubmit?: () => void;
  canSubmit?: boolean;
  className?: string;
};

// Rich-text notes field: retains bold/italic/underline/strikethrough and
// bulleted/numbered lists pasted in from elsewhere, discards everything else
// (font size/color/family, links, images, headings, …) by construction —
// those marks/nodes simply aren't part of the editor's schema below, so
// ProseMirror's paste parser drops them automatically. Shows a "clear
// formatting" button only once real formatting is present.
export default function NotesField({
  value,
  onChange,
  placeholder,
  onSubmit,
  canSubmit = true,
  className,
}: NotesFieldProps) {
  const { t: appDict } = useTranslation("app");
  const lastEmittedRef = useRef(value);
  const onSubmitRef = useRef(onSubmit);
  const canSubmitRef = useRef(canSubmit);
  const [hasFormatting, setHasFormatting] = useState(() =>
    htmlHasFormatting(decodeNotesToHtml(value)),
  );

  useEffect(() => {
    onSubmitRef.current = onSubmit;
  }, [onSubmit]);
  useEffect(() => {
    canSubmitRef.current = canSubmit;
  }, [canSubmit]);

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: false,
        codeBlock: false,
        blockquote: false,
        horizontalRule: false,
        code: false,
        dropcursor: false,
        gapcursor: false,
      }),
      Underline,
      Placeholder.configure({ placeholder }),
    ],
    content: decodeNotesToHtml(value),
    editorProps: {
      attributes: {
        class:
          "notes-field-prose w-full bg-transparent text-base font-bold text-foreground focus:outline-hidden",
        "data-testid": "notes-editor",
      },
      handleKeyDown: (_view, event) => {
        if (
          event.key !== "Enter" ||
          event.shiftKey ||
          event.ctrlKey ||
          event.metaKey ||
          event.altKey ||
          event.isComposing
        ) {
          return false;
        }
        event.preventDefault();
        if (canSubmitRef.current) {
          onSubmitRef.current?.();
        } else {
          (event.target as HTMLElement | null)?.blur();
        }
        return true;
      },
    },
    onUpdate: ({ editor: liveEditor }) => {
      const html = liveEditor.getHTML();
      setHasFormatting(htmlHasFormatting(html));
      const encoded = encodeNotes(html);
      lastEmittedRef.current = encoded;
      onChange(encoded);
    },
  });

  // Sync content set from outside (switching which task is being edited) —
  // guarded so it never fires as an echo of this field's own onChange.
  useEffect(() => {
    if (!editor) return;
    if (value === lastEmittedRef.current) return;
    lastEmittedRef.current = value;
    const html = decodeNotesToHtml(value);
    editor.commands.setContent(html);
    setHasFormatting(htmlHasFormatting(html));
  }, [value, editor]);

  function handleClearFormatting() {
    if (!editor) return;
    // Tiptap's own getText({blockSeparator}) double-counts the <li><p>
    // nesting StarterKit's list items use (an extra blank line per item), so
    // this reuses the same block-line extraction the read-only previews use
    // instead — it also keeps list items recognizable as "• "/"1. " lines.
    const plainText = htmlToPlainText(sanitizeHtml(editor.getHTML()));
    editor.commands.setContent(decodeNotesToHtml(plainText), true);
    editor.commands.focus();
    setHasFormatting(false);
  }

  return (
    <div className={cn("relative w-full", className)}>
      <EditorContent
        editor={editor}
        className={cn("w-full px-[18px] py-3", hasFormatting && "pr-11")}
      />
      {hasFormatting && (
        <button
          type="button"
          onClick={handleClearFormatting}
          aria-label={appDict("clearFormatting")}
          title={appDict("clearFormatting")}
          className="absolute right-2.5 top-2.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-muted-foreground/70 transition-colors hover:bg-muted-foreground/10 hover:text-foreground active:scale-95"
        >
          <Eraser className="h-4 w-4" />
        </button>
      )}
    </div>
  );
}
