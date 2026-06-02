import { Moon, Sun } from "lucide-react";
import { Link, usePathname } from "@/lib/navigation";
import { cn } from "@/lib/utils";

export default function NativeAppBrandButton({
  className,
}: {
  className?: string;
}) {
  const pathname = usePathname();
  const isHome = pathname.includes("/app/tday");
  const currentHour = new Date().getHours();
  const isDaytime = currentHour >= 6 && currentHour < 18;

  return (
    <Link
      href="/app/tday"
      aria-label="T'Day home"
      aria-current={isHome ? "page" : undefined}
      className={cn(
        // inline-flex + w-fit keeps the box (and the press ripple, which fills it)
        // hugging just the icon + text instead of stretching across the header.
        "group inline-flex w-fit max-w-full items-center gap-2 rounded-2xl transition-opacity duration-200",
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
