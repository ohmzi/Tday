import { cn } from "@/lib/utils";
import { SetStateAction, useMemo, useState } from "react";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import { Input } from "@/components/ui/input";
import ListDot from "@/components/ListDot";
import { useTranslations } from "next-intl";

type ListDrawerProps = {
  listID: string | null;
  setListID?: React.Dispatch<SetStateAction<string | null>>;
  className?: string;
};

export default function ListDrawer({
  listID,
  setListID,
  className,
}: ListDrawerProps) {
  const appDict = useTranslations("app");

  const { listMetaData } = useListMetaData();
  const [search, setSearch] = useState("");
  const setSelectedList = setListID ?? (() => {});

  const filteredLists = useMemo(() => {
    if (!search.trim()) return Object.entries(listMetaData);
    const lowerSearch = search.toLowerCase();
    return Object.entries(listMetaData).filter(([, value]) =>
      value.name.toLowerCase().includes(lowerSearch),
    );
  }, [search, listMetaData]);

  return (
    <div className={cn("max-h-[92vh]", className)}>
      <div className="mx-auto flex h-full w-full max-w-lg flex-col gap-4">
        <Input
          placeholder="Search lists..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="mb-4 w-full rounded-sm border-popover-border bg-inherit text-base! brightness-75 outline-0 ring-0 ring-black focus:brightness-100 focus-visible:ring-0 focus-visible:ring-offset-0"
          onKeyDown={(e) => e.stopPropagation()}
          autoFocus
        />

        {filteredLists.length === 0 && (
          <p className="w-full py-10 text-center text-xs text-muted-foreground">
            No lists...
          </p>
        )}

        {filteredLists.map(([key, value]) => (
          <div
            data-close-on-click
            key={key}
            className="cursor-pointer rounded-sm p-1.5 hover:bg-accent/50"
            onClick={() => {
              setSelectedList(key);
            }}
          >
            <ListDot id={key} className="pr-0 text-sm" /> {value.name}
          </div>
        ))}

        {listID !== null && (
          <div
            data-close-on-click
            className="flex cursor-pointer items-center justify-center rounded-sm border p-1.5 text-red hover:bg-red/40 hover:text-foreground!"
            onClick={() => {
              setSelectedList(null);
            }}
          >
            {appDict("clear")}
          </div>
        )}
      </div>
    </div>
  );
}
