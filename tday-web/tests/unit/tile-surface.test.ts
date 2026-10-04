import { describe, expect, it } from "vitest";
import { tileSurface } from "@/lib/tileSurface";

/**
 * The tile background both home grids draw. It used to be written twice — once in
 * the scheduled-task dashboard and once in the floater one — so this covers the
 * shared shape and the no-colour fallback, which is what the two copies could
 * silently drift apart on.
 */
describe("tileSurface", () => {
  it("mixes the accent over the theme's muted surface", () => {
    expect(tileSurface("#D98F4B")).toBe(
      "color-mix(in srgb, hsl(var(--card-muted)) 34%, #D98F4B 66%)",
    );
  });

  it("falls back to the neutral accent when the tile has no colour", () => {
    expect(tileSurface(undefined)).toBe(
      "color-mix(in srgb, hsl(var(--card-muted)) 34%, #68717A 66%)",
    );
  });
});
