import { useEffect, useState } from "react";
import { DAY_NIGHT_TICK_MS, isDaytimeNow } from "@/lib/timeOfDay";

/**
 * `isDaytimeNow`, sampled on a timer rather than during render.
 *
 * A render-time read is only ever as fresh as the last render, and nothing on a
 * feed guarantees one — a session left open across 18:00 keeps the sun up, and
 * the title reading "Today", until something unrelated re-renders. iOS reads the
 * same boundary off `TimelineView(.periodic(from: .now, by: 60))` and Android
 * polls the same minute from the same unaligned start, so all three turn over on
 * the same tick.
 *
 * Nothing animates when it fires and nothing should: the change happens once a
 * day, almost always while nobody is looking at the screen.
 *
 * @param enabled Pass false where the answer cannot matter (a surface with no
 * day/night wording or glyph at all). The timer is not armed, so a screen that
 * does not care does not wake once a minute for the life of the session.
 */
export function useIsDaytime(enabled = true): boolean {
  const [isDaytime, setIsDaytime] = useState(isDaytimeNow);

  useEffect(() => {
    // `undefined`, not a bare `return`: this effect returns a cleanup on its other path, and a
    // function that returns a value on one branch and nothing on another is the inconsistency
    // static analysis flags — rightly, since the two shapes read as a mistake.
    if (!enabled) return undefined;
    // Re-read on arm as well as on tick: `enabled` flipping on (or the component
    // remounting) is the one moment the initial state can already be wrong.
    setIsDaytime(isDaytimeNow());
    const timer = window.setInterval(() => setIsDaytime(isDaytimeNow()), DAY_NIGHT_TICK_MS);
    return () => window.clearInterval(timer);
  }, [enabled]);

  return isDaytime;
}
