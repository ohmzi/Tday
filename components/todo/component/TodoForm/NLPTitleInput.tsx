import { getCaretOffset } from "@/components/todo/lib/getCaretOffset";
import { setCaretOffset } from "@/components/todo/lib/setCaretOffset";
import { cn } from "@/lib/utils";
import { NonNullableDateRange } from "@/types";
import * as chrono from "chrono-node";
import { addHours } from "date-fns";
import React, { SetStateAction, useEffect, useRef } from "react";
import { useTranslations } from "next-intl";
import { useLocale } from "next-intl";

type NLPTitleInputProps = {
  titleRef: React.RefObject<HTMLDivElement | null>;
  title: string;
  setTitle: React.Dispatch<SetStateAction<string>>;
  setDateRange: React.Dispatch<SetStateAction<NonNullableDateRange>>;
  setListID?: React.Dispatch<SetStateAction<string | null>>;
  onSubmit?: () => void;
  className?: string;
};

export default function NLPTitleInput({
  titleRef,
  title,
  setTitle,
  setDateRange,
  onSubmit,
  className,
}: NLPTitleInputProps) {
  const locale = useLocale();
  const todayDict = useTranslations("today");
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
    if (!parsedResults.length) {
      return {
        html: escapeHtml(text),
        cleanTitle: text.trim(),
      };
    }

    const parsed = parsedResults[0];
    const idx = parsed.index as number;
    const matched = parsed.text as string;

    const from = parsed.start.date();
    const to = parsed.end?.date() ?? addHours(from, 3);
    setDateRange({ from, to });

    const before = text.slice(0, idx);
    const after = text.slice(idx + matched.length);

    return {
      html: `${escapeHtml(before)}<span class=\"bg-nlp inline rounded-[2px]\">${escapeHtml(matched)}</span>${escapeHtml(after)}`,
      cleanTitle: `${before}${after}`.replace(/\s{2,}/g, " ").trim(),
    };
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
            e.preventDefault();
            onSubmit?.();
          }
        }}
        ref={titleRef}
        className="z-50 focus:outline-hidden"
        role="textbox"
        aria-multiline="true"
      />

      {!title.length && !(titleRef.current?.textContent ?? "").length && (
        <span className="select-none pointer-events-none z-10 absolute top-0 left-2 font-light text-muted-foreground">
          {todayDict("titlePlaceholder")}
        </span>
      )}
    </div>
  );
}

function moveCursorToEnd(node: Node) {
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
