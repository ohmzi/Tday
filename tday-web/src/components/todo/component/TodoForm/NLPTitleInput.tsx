import { getCaretOffset } from "@/components/todo/lib/getCaretOffset";
import { setCaretOffset } from "@/components/todo/lib/setCaretOffset";
import { parseRecurrencePriority, type TodoPriority } from "@/lib/todoNlp";
import { useLocale } from "@/lib/navigation";
import { cn, isDesktopPointer } from "@/lib/utils";
import * as chrono from "chrono-node";
import { RRule, type Options } from "rrule";
import React, { SetStateAction, useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";

type FormDateRange = { from: Date; to: Date };

type NLPTitleInputProps = {
  titleRef: React.RefObject<HTMLDivElement | null>;
  title: string;
  setTitle: React.Dispatch<SetStateAction<string>>;
  setDateRange: React.Dispatch<SetStateAction<FormDateRange>>;
  setListID?: React.Dispatch<SetStateAction<string | null>>;
  // Optional: capture "every day"/"weekly" → recurrence and "!"/"high" → priority
  // straight from the title. Omitted by surfaces that don't expose those fields.
  setPriority?: (priority: TodoPriority) => void;
  setRruleOptions?: (options: Partial<Options> | null) => void;
  className?: string;
  onSubmit?: () => void;
};

export default function NLPTitleInput({
  titleRef,
  title,
  setTitle,
  setDateRange,
  setPriority,
  setRruleOptions,
  className,
  onSubmit,
}: NLPTitleInputProps) {
  const locale = useLocale();
  const { t: todayDict } = useTranslation("today");
  const isComposing = useRef(false);

  useEffect(() => {
    const node = titleRef.current;
    if (node && !node.textContent) {
      node.textContent = title;
      moveCursorToEnd(node);
    }
  }, [title, titleRef]);

  const parseDate = (text: string) => {
    switch (locale) {
      case "ja":
        return chrono.ja.parse(text);
      case "fr":
        return chrono.fr.parse(text);
      case "ru":
        return chrono.ru.parse(text);
      case "es":
        return chrono.es.parse(text);
      case "it":
        return chrono.it.parse(text);
      case "de":
        return chrono.de.parse(text);
      case "pt":
        return chrono.pt.parse(text);
      case "zh":
        return chrono.zh.parse(text);
      default:
        return chrono.en.parse(text);
    }
  };

  const applyNLPHighlighting = (text: string) => {
    const parsedResults = parseDate(text);
    let html: string;
    let dateCleaned: string;

    if (!parsedResults.length) {
      html = escapeHtml(text);
      dateCleaned = text;
    } else {
      const parsed = parsedResults[0];
      const idx = parsed.index as number;
      const matched = parsed.text as string;

      // Use the time the user actually named (parsed.start). Only fall back to an
      // explicit end when the text gave a range ("8pm to 10pm"); a single time
      // like "8pm" has no end and must NOT be shifted. This mirrors the backend
      // NLP (Natty) used by iOS/Android: dueDate = dates.size > 1 ? dates[1] : start.
      const from = parsed.start.date();
      const due = parsed.end?.date() ?? from;
      from.setSeconds(0, 0);
      due.setSeconds(0, 0);
      setDateRange({ from: due, to: due });

      const before = text.slice(0, idx);
      const after = text.slice(idx + matched.length);
      html = `${escapeHtml(before)}<span class=\"bg-nlp inline rounded-[2px]\">${escapeHtml(matched)}</span>${escapeHtml(after)}`;
      dateCleaned = `${before}${after}`;
    }

    // Recurrence + priority capture. Only sets the fields when a phrase is present
    // (so it never clobbers a hand-picked recurrence/priority on later keystrokes).
    const rp = parseRecurrencePriority(dateCleaned);
    if (rp.rrule && setRruleOptions) {
      setRruleOptions(RRule.parseString(rp.rrule));
    }
    if (rp.priority && setPriority) {
      setPriority(rp.priority);
    }

    return { html, cleanTitle: rp.cleanTitle };
  };

  const handleNLPInput = () => {
    if (isComposing.current) return;

    const node = titleRef.current;
    if (!node) return;

    const caret = getCaretOffset(node);
    const fullText = node.textContent ?? "";

    const { html, cleanTitle } = applyNLPHighlighting(fullText);

    node.innerHTML = html;
    setTitle(cleanTitle);
    setCaretOffset(node, caret);
  };

  return (
    <div className={cn("relative text-[1.1rem] font-semibold", className)}>
      <div
        contentEditable
        suppressContentEditableWarning
        onCompositionStart={() => (isComposing.current = true)}
        onCompositionEnd={() => (isComposing.current = false)}
        onInput={handleNLPInput}
        onKeyDown={(e) => {
          if (isComposing.current) return;
          if (e.key === "Enter") {
            const plainEnter =
              !e.shiftKey && !e.ctrlKey && !e.metaKey && !e.altKey;
            const nativeComposing =
              e.nativeEvent.isComposing || e.keyCode === 229;
            if (onSubmit && plainEnter && !nativeComposing && isDesktopPointer()) {
              // Desktop: plain Enter submits the form.
              e.preventDefault();
              onSubmit();
              return;
            }
            // Enter dismisses the keyboard (like native) instead of submitting.
            e.preventDefault();
            titleRef.current?.blur();
          }
        }}
        ref={titleRef}
        className="z-50 focus:outline-hidden"
        role="textbox"
        aria-multiline="true"
      />

      {!title.length && !(titleRef.current?.textContent ?? "").length && (
        <span className="select-none pointer-events-none z-10 absolute top-0 left-0 text-muted-foreground">
          {todayDict("titlePlaceholder")}
        </span>
      )}
    </div>
  );
}

export function moveCursorToEnd(node: Node) {
  const range = document.createRange();
  range.selectNodeContents(node);
  range.collapse(false);
  const sel = window.getSelection();
  sel?.removeAllRanges();
  sel?.addRange(range);
}

function escapeHtml(input: string): string {
  return input
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}
