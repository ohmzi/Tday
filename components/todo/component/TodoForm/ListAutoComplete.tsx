import ListDot from "@/components/ListDot";
import React from "react";

type ListMeta = {
  id: string;
  name: string;
};

type NLPListDropdownProps = {
  lists: ListMeta[];
  onSelect: (list: ListMeta) => void;
  style?: React.CSSProperties;
  selectedIndex: number;
  setSelectedIndex: React.Dispatch<React.SetStateAction<number>>;
};

export function ListAutoComplete({
  lists,
  onSelect,
  style,
  selectedIndex,
  setSelectedIndex,
}: NLPListDropdownProps) {
  if (lists.length === 0) return null;

  return (
    <div
      style={style}
      role="listbox"
      aria-activedescendant={`list-option-${selectedIndex}`}
      className="absolute z-50 mt-1 max-h-52 overflow-auto rounded-md border bg-popover p-1 shadow-md"
    >
      {lists.map((value, i) => (
        <button
          key={value.id}
          id={`list-option-${i}`}
          type="button"
          role="option"
          aria-selected={i === selectedIndex}
          onMouseEnter={() => setSelectedIndex(i)}
          onClick={() => onSelect(value)}
          className={`flex w-full items-center gap-2 rounded px-2 py-1.5 text-left text-sm ${
            i === selectedIndex
              ? "bg-accent text-accent-foreground"
              : "hover:bg-accent/50"
          }`}
        >
          <ListDot id={value.id} className="pr-0 text-sm" /> {value.name}
        </button>
      ))}
    </div>
  );
}
