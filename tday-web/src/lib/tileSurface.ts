/**
 * The accent a tile falls back to when it has no colour of its own — the same
 * neutral the "All" screen tile wears (`nativeScreenAccentColors.all`).
 */
const TILE_SURFACE_FALLBACK_COLOR = "#68717A";

/**
 * A tile's background, leaning with the theme the way the list tiles already did:
 * the accent mixed over the theme's muted card surface, so it is lighter in light
 * mode and darker in dark mode while the accent still dominates (66%).
 *
 * That is what lets the text on a tile be the ordinary `text-foreground` — dark in
 * light mode, light in dark mode — instead of a hardcoded white. A fixed mid-tone
 * tile plus white text is unreadable in light mode (and on every light accent in
 * dark mode too); a themed surface plus themed text reads in both.
 *
 * Shared by the scheduled-task and floater home tiles so the two grids read as the
 * same surface; they are siblings under `RootFeedDock` and must not drift.
 */
export function tileSurface(color: string | undefined): string {
  return `color-mix(in srgb, hsl(var(--card-muted)) 34%, ${color ?? TILE_SURFACE_FALLBACK_COLOR} 66%)`;
}
