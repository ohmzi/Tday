import { cn } from "@/lib/utils";
import "./illustrations.generated.css";
import { ILLUSTRATION_SCENES, type IllustrationId } from "./scenes.generated";

export type { IllustrationId };

/**
 * One of the illustrated-state scenes (offline, empty list, nothing scheduled…),
 * drawn from the generated markup in `scenes.generated.ts`. The source of truth
 * is `design/illustrations/`; run `node scripts/illustrations.mjs` after editing
 * it. Colours follow the theme through the `--il-*` tokens on `.tday-illus`, and
 * the scene's own ambient loops stop under reduced motion.
 *
 * Decorative: hidden from assistive tech, because the title and description
 * beside it already say what the state is.
 *
 * The markup is a compile-time constant generated from files in this repo,
 * never user or server content, which is what makes the inner-HTML injection safe.
 */
export default function Illustration({
  id,
  className,
}: {
  id: IllustrationId;
  className?: string;
}) {
  return (
    <svg
      aria-hidden
      focusable="false"
      viewBox="0 0 400 300"
      className={cn("tday-illus block h-auto w-full", className)}
      data-illustration={id}
      dangerouslySetInnerHTML={{ __html: ILLUSTRATION_SCENES[id] }}
    />
  );
}
