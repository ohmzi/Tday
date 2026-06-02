import { Moon, Sun } from "lucide-react";
import { Link, usePathname } from "@/lib/navigation";
import { cn } from "@/lib/utils";

export default function NativeAppBrandButton({
  variant = "compact",
  className,
}: {
  variant?: "compact" | "prominent";
  className?: string;
}) {
  const pathname = usePathname();
  const isHome = pathname.includes("/app/tday");
  const currentHour = new Date().getHours();
  const isDaytime = currentHour >= 6 && currentHour < 18;
  const showDaypart = variant === "prominent";

  return (
    <Link
      href="/app/tday"
      aria-label="T'Day home"
      aria-current={isHome ? "page" : undefined}
      className={cn(
        "group flex min-w-0 items-center gap-2 rounded-2xl transition-opacity duration-200",
        "hover:opacity-90 active:opacity-80",
        className,
      )}
    >
      {showDaypart ? (
        isDaytime ? (
          <Sun className="h-7 w-7 shrink-0 fill-[#F4C542] text-[#F4C542]" />
        ) : (
          <Moon className="h-7 w-7 shrink-0 fill-[#A8B8E8] text-[#A8B8E8]" />
        )
      ) : (
        <img
          src="/tday-icon.svg"
          alt=""
          width={32}
          height={32}
          className="h-8 w-8 shrink-0 rounded-lg object-cover shadow-sm"
        />
      )}
      <span
        className={cn(
          "truncate font-black leading-none tracking-normal text-foreground",
          variant === "prominent"
            ? "text-[2rem] sm:text-[2.35rem]"
            : "text-xl sm:text-[1.35rem]",
        )}
      >
        T&apos;Day
      </span>
    </Link>
  );
}
