/**
 * How long a day/night read may be stale for. Not a motion value and not on the
 * ladder — nothing moves when it fires. It is a plain minute because that is the
 * period iOS gives the `TimelineView` it reads the same glyph off, and the one
 * Android polls on.
 */
export const DAY_NIGHT_TICK_MS = 60_000;

/**
 * Whether the wall clock says it is daytime right now.
 *
 * The app's ONE day/night boundary. Four surfaces turn on it — the root feed
 * header's sun/moon mark, the brand button's, the onboarding backdrop, and the
 * Today/Tonight wording on the scheduled timeline — and they have to turn on the
 * same minute or the screen contradicts itself: a moon over the word "Today", or
 * a title that says "Tonight" while the mark above it is still a sun. Three
 * separate copies of `hour >= 6 && hour < 18` is precisely how that drifts, and
 * this module exists so there is nowhere for a second band to live. Anything new
 * that wants to know the time of day imports this rather than reading an hour.
 *
 * The band is local wall-clock hours and deliberately not sunrise/sunset: no
 * surface here is worth a location permission, and a fixed band is the one the
 * native clients already hold.
 */
export function isDaytimeNow(): boolean {
  const hour = new Date().getHours();
  return hour >= 6 && hour < 18;
}
