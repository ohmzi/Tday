import { cn } from "@/lib/utils";
import { useMenu } from "@/providers/MenuProvider";
import { Menu } from "lucide-react";
import React from "react";
const SidebarToggle = ({
  className,
}: {
  className?: string;
}) => {
  const { setShowMenu } = useMenu();

  return (
    <button
      className={cn(
        "group flex h-10 w-10 cursor-pointer items-center justify-center rounded-xl border border-border/50 bg-card/80 p-0 text-sidebar-foreground/75 shadow-sm backdrop-blur-sm transition-all duration-200 hover:bg-accent/80 hover:text-accent-foreground",
        className,
      )}
      aria-label="Open menu"
      onPointerDown={(e) => {
        e.stopPropagation();
        e.preventDefault();
      }}
      onClick={(e) => {
        e.stopPropagation();
        e.preventDefault();
        setShowMenu(true);
      }}
      onMouseOver={(e) => {
        e.stopPropagation();
      }}
      onMouseLeave={(e) => {
        e.stopPropagation();
      }}
    >
      <span className="sr-only">Open menu</span>

      <Menu className="h-5 w-5" />
    </button>
  );
};

export default SidebarToggle;
