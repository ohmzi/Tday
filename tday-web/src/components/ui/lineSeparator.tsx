import React from "react";
import { cn } from "@/lib/utils";
const LineSeparator = ({ className }: { className?: string }) => {
  return (
    <div className={cn("m-auto h-0 w-full border-b border-border/70", className)}></div>
  );
};

export default LineSeparator;
