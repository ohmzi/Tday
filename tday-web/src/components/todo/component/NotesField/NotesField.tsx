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

// How far above the selection the menu sits, and roughly how tall it is —
// used to position it before it's actually painted (no ResizeObserver
// round-trip). Matches the button row's own sizing (h-7 buttons + p-1).
const FORMAT_MENU_OFFSET = 44;

function FormatButton({
  active,
  label,
  onClick,
  children,
}: {
  active: boolean;
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
      // Toolbar buttons that live outside the contenteditable area steal
      // focus (and with it, the text selection toggleBold() etc. need to
      // operate on) on mousedown, before the click handler ever runs —
      // this is the standard Tiptap/ProseMirror fix.
      onMouseDown={(event) => event.preventDefault()}
      onClick={onClick}
      className={cn(
        "flex h-7 w-7 shrink-0 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-muted-foreground/10 hover:text-foreground active:scale-95",
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
// ProseMirror's paste parser drops them automatically. Selecting text also
// shows a floating menu with the same six marks/lists so they can be applied
// manually, not just via paste. Shows a "clear formatting" button only once
// real formatting is present.
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
  const wrapperRef = useRef<HTMLDivElement>(null);
  const [hasFormatting, setHasFormatting] = useState(() =>
    htmlHasFormatting(decodeNotesToHtml(value)),
  );
  // Position (relative to wrapperRef) of the floating format menu, or null
  // when there's no non-empty selection to show it for. Deliberately a
  // plain absolute position rather than @tiptap/extension-bubble-menu's
  // <BubbleMenu> (which positions via tippy.js) — tippy.js's default export
  // doesn't unwrap through this project's test runtime's CJS/ESM interop,
  // crashing any test that so much as pastes into an editor with BubbleMenu
  // mounted. This has no such dependency and every button's active state
  // (editor.isActive("bold") etc.) is read fresh on each of these updates.
  const [menuPosition, setMenuPosition] = useState<{ top: number; left: number } | null>(null);

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

  // Tracks the current selection's screen position (for the format menu)
  // across every transaction, not just content changes — this is also what
  // keeps FormatButton's active-state highlighting current as the selection
  // itself moves without the text changing (e.g. arrow-key navigation).
  useEffect(() => {
    if (!editor) return;
    // Narrows `editor` once so the closures below (invoked later, async,
    // from editor.on()) keep TypeScript's non-null narrowing — it doesn't
    // otherwise carry into nested function bodies.
    const activeEditor = editor;

    function updateMenuPosition() {
      if (!activeEditor.isFocused) {
        setMenuPosition(null);
        return;
      }
      const { from, to } = activeEditor.state.selection;
      const domSelection = window.getSelection();
      const wrapperRect = wrapperRef.current?.getBoundingClientRect();
      if (from === to || !domSelection || domSelection.rangeCount === 0 || !wrapperRect) {
        setMenuPosition(null);
        return;
      }
      const selectionRect = domSelection.getRangeAt(0).getBoundingClientRect();
      if (selectionRect.width === 0 && selectionRect.height === 0) {
        setMenuPosition(null);
        return;
      }
      setMenuPosition({
        // Clamped to 0: a selection on the field's first line would
        // otherwise place the menu above the wrapper's own top edge,
        // clipped under whatever sheet chrome sits above it.
        top: Math.max(0, selectionRect.top - wrapperRect.top - FORMAT_MENU_OFFSET),
        left: selectionRect.left - wrapperRect.left + selectionRect.width / 2,
      });
    }

    function hideMenuPosition() {
      setMenuPosition(null);
    }

    activeEditor.on("selectionUpdate", updateMenuPosition);
    activeEditor.on("transaction", updateMenuPosition);
    activeEditor.on("focus", updateMenuPosition);
    activeEditor.on("blur", hideMenuPosition);
    return () => {
      activeEditor.off("selectionUpdate", updateMenuPosition);
      activeEditor.off("transaction", updateMenuPosition);
      activeEditor.off("focus", updateMenuPosition);
      activeEditor.off("blur", hideMenuPosition);
    };
  }, [editor]);

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
    <div ref={wrapperRef} className={cn("relative w-full", className)}>
      <EditorContent
        editor={editor}
        className={cn("w-full px-[18px] py-3", hasFormatting && "pr-11")}
      />
      {editor && menuPosition && (
        <div
          className="absolute z-10 flex -translate-x-1/2 items-center gap-0.5 rounded-xl border border-border bg-popover p-1 shadow-lg"
          style={{ top: menuPosition.top, left: menuPosition.left }}
        >
          <FormatButton
            active={editor.isActive("bold")}
            label={appDict("bold")}
            onClick={() => editor.chain().focus().toggleBold().run()}
          >
            <Bold className="h-4 w-4" />
          </FormatButton>
          <FormatButton
            active={editor.isActive("italic")}
            label={appDict("italic")}
            onClick={() => editor.chain().focus().toggleItalic().run()}
          >
            <Italic className="h-4 w-4" />
          </FormatButton>
          <FormatButton
            active={editor.isActive("underline")}
            label={appDict("underline")}
            onClick={() => editor.chain().focus().toggleUnderline().run()}
          >
            <UnderlineIcon className="h-4 w-4" />
          </FormatButton>
          <FormatButton
            active={editor.isActive("strike")}
            label={appDict("strikethrough")}
            onClick={() => editor.chain().focus().toggleStrike().run()}
          >
            <Strikethrough className="h-4 w-4" />
          </FormatButton>
          <div className="mx-0.5 h-5 w-px bg-border" aria-hidden="true" />
          <FormatButton
            active={editor.isActive("bulletList")}
            label={appDict("bulletedList")}
            onClick={() => editor.chain().focus().toggleBulletList().run()}
          >
            <List className="h-4 w-4" />
          </FormatButton>
          <FormatButton
            active={editor.isActive("orderedList")}
            label={appDict("numberedList")}
            onClick={() => editor.chain().focus().toggleOrderedList().run()}
          >
            <ListOrdered className="h-4 w-4" />
          </FormatButton>
        </div>
      )}
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
