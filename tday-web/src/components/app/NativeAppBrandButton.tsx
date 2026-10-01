import { Moon, Sun } from "lucide-react";
import { Link, usePathname } from "@/lib/navigation";
import { isDaytimeNow } from "@/lib/timeOfDay";
import { cn } from "@/lib/utils";

export default function NativeAppBrandButton({
  className,
}: {
  className?: string;
}) {
  const pathname = usePathname();
  const isHome = pathname.includes("/app/tday");
  // Read at render and not on a clock: this button only ever sits on a header
  // that is itself re-rendered by navigation, and it is not the surface the
  // day/night boundary is judged on — the root feed's mark is, and that one has
  // the timer (`useIsDaytime`). Same band either way, which is the point of
  // importing it rather than writing the hours again.
  const isDaytime = isDaytimeNow();

  return (
    <Link
      href="/app/tday"
      aria-label="T'Day home"
      aria-current={isHome ? "page" : undefined}
      className={cn(
        // inline-flex + w-fit keeps the box (and the press ripple, which fills it)
        // hugging just the icon + text instead of stretching across the header.
        "group inline-flex w-fit max-w-full items-center gap-2 rounded-lg transition-opacity duration-enter",
        "hover:opacity-90 active:opacity-80",
        className,
      )}
    >
      {isDaytime ? (
        <Sun className="h-7 w-7 shrink-0 fill-[#F4C542] text-[#F4C542]" />
      ) : (
        <Moon className="h-7 w-7 shrink-0 fill-[#A8B8E8] text-[#A8B8E8]" />
      )}
      <span className="truncate text-[2rem] font-black leading-none tracking-normal text-foreground sm:text-[2.35rem]">
        T&apos;Day
      </span>
    </Link>
  );
}
