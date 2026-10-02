// @vitest-environment jsdom

/**
 * The topic that ships in the running release is listed twice on the Guide screen: once under
 * "What's New" and once in its own section. A deep link (`/app/guide/crash-reports`, which the
 * consent card's "?" uses) must open and scroll to the section copy only, not both.
 */

import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import i18n from "@/i18n";
import GuideScreen from "@/features/guide/GuideScreen";
import { GUIDE_SECTIONS, whatsNewTopics } from "@/features/guide/guideContent";
import { scrollIntoView } from "@/lib/scroll";

vi.mock("@/lib/scroll", () => ({ scrollIntoView: vi.fn() }));

afterEach(cleanup);
beforeEach(() => vi.mocked(scrollIntoView).mockClear());

function renderGuide(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/app/guide" element={<GuideScreen />} />
        <Route path="/app/guide/:topicId" element={<GuideScreen />} />
      </Routes>
    </MemoryRouter>,
  );
}

const topic = whatsNewTopics()[0];

// The row's ref element sits in a row wrapper, then the card's body, then the card with its h2.
function cardTitle(row: HTMLElement): string {
  return row.parentElement!.parentElement!.parentElement!.querySelector("h2")!.textContent ?? "";
}

describe("GuideScreen with a topic listed under What's New", () => {
  it("lists the topic twice: under What's New and in its section", () => {
    renderGuide("/app/guide");
    expect(screen.getAllByText(i18n.t(topic.titleKey))).toHaveLength(2);
  });

  it("expands only the section copy for a deep link", () => {
    const { container } = renderGuide(`/app/guide/${topic.id}`);
    expect(container.querySelectorAll('button[aria-expanded="true"]')).toHaveLength(1);
  });

  it("scrolls to the copy that expanded, under its own section heading", () => {
    const { container } = renderGuide(`/app/guide/${topic.id}`);
    const row = vi.mocked(scrollIntoView).mock.calls.at(-1)![0] as HTMLElement;
    const section = GUIDE_SECTIONS.find((candidate) => candidate.id === topic.section)!;
    expect(cardTitle(row)).toBe(i18n.t(section.titleKey));
    expect(row.querySelector('button[aria-expanded="true"]')).not.toBeNull();
    expect(container.querySelectorAll('button[aria-expanded="true"]')).toHaveLength(1);
  });
});
