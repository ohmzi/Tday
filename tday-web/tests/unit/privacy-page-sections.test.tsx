// @vitest-environment jsdom

import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import "@/i18n";
import PrivacyPage from "@/pages/PrivacyPage";

afterEach(cleanup);

describe("PrivacyPage", () => {
  it("lists crash and problem reports as the tenth section, with what is and is not sent", () => {
    render(<PrivacyPage />);

    expect(screen.getByRole("heading", { name: "10. Crash & Problem Reports" })).toBeTruthy();
    expect(screen.getByText(/Optional and off by default\. Nothing is sent unless you turn it on/)).toBeTruthy();
    expect(screen.getByText(/It never holds your name, account, IP address/)).toBeTruthy();
    expect(screen.getByText(/kept for 30 days/)).toBeTruthy();
  });

  it("still lists the nine sections before it", () => {
    render(<PrivacyPage />);

    expect(screen.getAllByRole("heading", { level: 2 })).toHaveLength(10);
    expect(screen.getByRole("heading", { name: "9. Contact" })).toBeTruthy();
  });
});
