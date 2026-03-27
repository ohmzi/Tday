// @vitest-environment jsdom

import { describe, expect, it } from "vitest";

import i18n from "@/i18n";
import en from "../../messages/en.json";

type BackendWithParse = {
  options?: {
    parse?: (
      data: string,
      languages?: string | string[],
      namespaces?: string | string[],
    ) => unknown;
  };
};

function getBackendParse() {
  const backend = i18n.services.backendConnector.backend as BackendWithParse | undefined;
  const parse = backend?.options?.parse;

  if (!parse) {
    throw new Error("Expected i18next backend parse function to be configured.");
  }

  return parse;
}

describe("i18n namespace loading", () => {
  it("returns only the requested top-level namespace from translation.json", () => {
    const parse = getBackendParse();

    expect(parse(JSON.stringify(en), "en", "app")).toMatchObject({
      today: "Today",
    });
    expect(parse(JSON.stringify(en), "en", "today")).toMatchObject({
      addATask: "Add a task",
    });
    expect(parse(JSON.stringify(en), "en", "completed")).toMatchObject({
      title: "Completion history",
    });
  });

  it("keeps the default translation namespace as the full locale payload", () => {
    const parse = getBackendParse();

    expect(parse(JSON.stringify(en), "en", "translation")).toMatchObject({
      app: expect.objectContaining({
        today: "Today",
      }),
      today: expect.objectContaining({
        addATask: "Add a task",
      }),
      completed: expect.objectContaining({
        title: "Completion history",
      }),
    });
  });
});
