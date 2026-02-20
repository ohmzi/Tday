import React from "react";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import clsx from "clsx";
import { Circle } from "lucide-react";

export default function ListDot({
  id,
  className,
}: {
  id: string;
  className?: string;
}) {
  const { listMetaData } = useListMetaData();

  const colorClass = clsx({
    "text-accent-red": listMetaData[id]?.color === "RED",
    "text-accent-orange": listMetaData[id]?.color === "ORANGE",
    "text-accent-yellow": listMetaData[id]?.color === "YELLOW",
    "text-accent-lime": listMetaData[id]?.color === "LIME",
    "text-accent-blue": listMetaData[id]?.color === "BLUE",
    "text-accent-purple": listMetaData[id]?.color === "PURPLE",
    "text-accent-pink": listMetaData[id]?.color === "PINK",
    "text-accent-teal": listMetaData[id]?.color === "TEAL",
    "text-accent-coral": listMetaData[id]?.color === "CORAL",
    "text-accent-gold": listMetaData[id]?.color === "GOLD",
    "text-accent-deep-blue": listMetaData[id]?.color === "DEEP_BLUE",
    "text-accent-rose": listMetaData[id]?.color === "ROSE",
    "text-accent-light-red": listMetaData[id]?.color === "LIGHT_RED",
    "text-accent-brick": listMetaData[id]?.color === "BRICK",
    "text-accent-slate": listMetaData[id]?.color === "SLATE",
  });

  return (
    <Circle
      className={clsx(
        "inline-flex h-3.5 w-3.5 shrink-0 fill-current",
        colorClass,
        className,
      )}
    />
  );
}
