// @vitest-environment jsdom
import { describe, expect, it, vi } from "vitest";
import { downloadJson, fileTimestamp, readJsonFile } from "@/lib/fileTransfer";

describe("fileTransfer", () => {
  describe("fileTimestamp", () => {
    it("formats a filesystem-safe stamp", () => {
      const stamp = fileTimestamp(new Date(2026, 6, 14, 9, 5)); // 2026-07-14 09:05
      expect(stamp).toBe("2026-07-14-0905");
    });
  });

  describe("readJsonFile", () => {
    it("parses a JSON file's contents", async () => {
      const file = new File(['{"schemaVersion":1,"todos":[]}'], "export.json", {
        type: "application/json",
      });
      const parsed = (await readJsonFile(file)) as { schemaVersion: number };
      expect(parsed.schemaVersion).toBe(1);
    });

    it("rejects malformed JSON", async () => {
      const file = new File(["not json {"], "bad.json", { type: "application/json" });
      await expect(readJsonFile(file)).rejects.toThrow();
    });
  });

  describe("downloadJson", () => {
    it("creates and clicks an anchor with the given filename", () => {
      const createUrl = vi
        .spyOn(URL, "createObjectURL")
        .mockReturnValue("blob:mock");
      const revokeUrl = vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => {});
      const clicked: string[] = [];
      const clickSpy = vi
        .spyOn(HTMLAnchorElement.prototype, "click")
        .mockImplementation(function (this: HTMLAnchorElement) {
          clicked.push(this.download);
        });

      downloadJson("tday-export.json", { schemaVersion: 1 });

      expect(createUrl).toHaveBeenCalledOnce();
      expect(clicked).toEqual(["tday-export.json"]);

      clickSpy.mockRestore();
      createUrl.mockRestore();
      revokeUrl.mockRestore();
    });
  });
});
