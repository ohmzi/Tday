import { useEffect, useRef, useState } from "react";
import { EditorContent, useEditor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import Underline from "@tiptap/extension-underline";
import Placeholder from "@tiptap/extension-placeholder";
import {
  Bold,
  Eraser,
  Italic,
  List,
  ListOrdered,
  Strikethrough,
  Underline as UnderlineIcon,
} from "lucide-react";
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

function FormatButton({
  active,
  disabled,
  label,
  onClick,
  children,
}: {
  active: boolean;
  disabled: boolean;
  label: string;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      aria-pressed={active}
      title={label}
      disabled={disabled}
      // A toolbar button outside the contenteditable area steals focus (and
      // with it, the text selection toggleBold() etc. need to operate on)
      // on mousedown, before the click handler ever runs — the standard
      // Tiptap/ProseMirror fix.
      onMouseDown={(event) => event.preventDefault()}
      onClick={onClick}
      className={cn(
        "flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-muted-foreground/10 hover:text-foreground active:scale-95 disabled:pointer-events-none disabled:opacity-30",
        active && "bg-foreground/10 text-foreground",
      )}
    >
      {children}
    </button>
  );
}

// Rich-text notes field: retains bold/italic/underline/strikethrough and
// bulleted/numbered lists pasted in from elsewhere, discards everything else
// (font size/color/family, links, images, headings, …) by construction —
// those marks/nodes simply aren't part of the editor's schema below, so
// ProseMirror's paste parser drops them automatically. Focusing the field
// also shows a format bar below it with the same six marks/lists so they
// can be applied manually to the current selection — matching Android/iOS's
// format bar (a persistent row, not a selection-triggered popup, so every
// platform's affordance lives in the same place). Shows a "clear
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
  const [isFocused, setIsFocused] = useState(false);
  // Forces a re-render on every transaction, not just content changes —
  // the format bar's active-state highlighting (editor.isActive("bold")
  // etc.) needs to stay current as the selection itself moves, which
  // doesn't otherwise trigger React to re-read it.
  const [, forceRerender] = useState(0);

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
    onFocus: () => setIsFocused(true),
    onBlur: () => setIsFocused(false),
    onTransaction: () => {
      forceRerender((n) => n + 1);
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
    // instead. Unlike those previews, list bullets/numbers are dropped
    // entirely (includeListPrefixes: false) rather than kept as text — the
    // user asked to clear formatting, not to keep a plain-text list.
    const plainText = htmlToPlainText(sanitizeHtml(editor.getHTML()), false);
    editor.commands.setContent(decodeNotesToHtml(plainText), true);
    editor.commands.focus();
    setHasFormatting(false);
  }

  const selectionEmpty = editor?.state.selection.empty ?? true;

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
      {editor && isFocused && (
        <div className="flex items-center gap-0.5 border-t border-border px-2 py-1">
          <FormatButton
            active={editor.isActive("bold")}
            disabled={selectionEmpty}
            label={appDict("bold")}
            onClick={() => editor.chain().focus().toggleBold().run()}
          >
            <Bold className="h-4 w-4" />
          </FormatButton>
          <FormatButton
            active={editor.isActive("italic")}
            disabled={selectionEmpty}
            label={appDict("italic")}
            onClick={() => editor.chain().focus().toggleItalic().run()}
          >
            <Italic className="h-4 w-4" />
          </FormatButton>
          <FormatButton
            active={editor.isActive("underline")}
            disabled={selectionEmpty}
            label={appDict("underline")}
            onClick={() => editor.chain().focus().toggleUnderline().run()}
          >
            <UnderlineIcon className="h-4 w-4" />
          </FormatButton>
          <FormatButton
            active={editor.isActive("strike")}
            disabled={selectionEmpty}
            label={appDict("strikethrough")}
            onClick={() => editor.chain().focus().toggleStrike().run()}
          >
            <Strikethrough className="h-4 w-4" />
          </FormatButton>
          <div className="mx-0.5 h-5 w-px bg-border" aria-hidden="true" />
          <FormatButton
            active={editor.isActive("bulletList")}
            disabled={selectionEmpty}
            label={appDict("bulletedList")}
            onClick={() => editor.chain().focus().toggleBulletList().run()}
          >
            <List className="h-4 w-4" />
          </FormatButton>
          <FormatButton
            active={editor.isActive("orderedList")}
            disabled={selectionEmpty}
            label={appDict("numberedList")}
            onClick={() => editor.chain().focus().toggleOrderedList().run()}
          >
            <ListOrdered className="h-4 w-4" />
          </FormatButton>
        </div>
      )}
    </div>
  );
}
