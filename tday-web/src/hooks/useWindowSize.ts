import { useEffect, useState } from "react";

export type WindowSize = { width: number; height: number };

function readViewport(): WindowSize {
  // The hook is also pulled in by non-DOM render passes (tests, prerender); report zeroes
  // there rather than throwing, and let the mount effect below supply the real numbers.
  if (typeof window === "undefined") return { width: 0, height: 0 };
  return { width: window.innerWidth, height: window.innerHeight };
}

/**
 * Viewport dimensions, re-read on every `resize`.
 *
 * Used where mobile and desktop render genuinely different component trees (drawer vs
 * dropdown, sheet vs popover) and a CSS breakpoint cannot express the difference.
 */
export default function useWindowSize(): WindowSize {
  const [size, setSize] = useState<WindowSize>(readViewport);

  useEffect(() => {
    const sync = () => setSize(readViewport());
    // Measure once on mount too: the first render may have run without a window, and the
    // viewport can change between that render and this effect.
    sync();
    window.addEventListener("resize", sync);
    return () => window.removeEventListener("resize", sync);
  }, []);

  return size;
}
