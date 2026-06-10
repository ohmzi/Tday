import { Input } from "@/components/ui/input";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { ChevronDown, Plus, Trash } from "lucide-react";
import React, { SetStateAction, useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import { DropdownMenuSeparator } from "@/components/ui/dropdown-menu";
import ListDot from "@/components/ListDot";
import { cn } from "@/lib/utils";
import ListFormSheet from "@/components/Sidebar/List/ListFormSheet";

type ListDropdownMenuProp = {
  listID: string | null;
  setListID?: React.Dispatch<SetStateAction<string | null>>;
  className?: string;
  variant?: "default" | "compact";
};

export default function ListDropdownMenu({
  listID,
  setListID,
  className,
  variant = "default",
}: ListDropdownMenuProp) {
  const { listMetaData } = useListMetaData();
  const [open, setOpen] = useState(false);
  const [createSheetOpen, setCreateSheetOpen] = useState(false);
  const [search, setSearch] = useState("");
  const normalizedSearch = search.trim();

  const setSelectedList = setListID ?? (() => {});

  const filteredLists = useMemo(() => {
    // Viewers can't create or move tasks into a shared list, so those lists
    // are not offered as targets.
    const writableLists = Object.entries(listMetaData).filter(
      ([, value]) => value.myRole !== "VIEWER",
    );
    if (!search.trim()) return writableLists;
    const lowerSearch = search.toLowerCase();
    return writableLists.filter(([, value]) =>
      value.name.toLowerCase().includes(lowerSearch),
    );
  }, [search, listMetaData]);

  const hasExactMatch = useMemo(() => {
    if (!normalizedSearch) return false;
    return Object.values(listMetaData).some(
      (value) => value.name.trim().toLowerCase() === normalizedSearch.toLowerCase(),
    );
  }, [normalizedSearch, listMetaData]);

  const canCreateList = normalizedSearch.length > 0 && !hasExactMatch;

  return (
    <>
      <Popover modal open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button
            variant="ghost"
            type="button"
            className={cn(
              "h-fit shrink-0 gap-1 px-2! font-normal text-muted-foreground",
              className,
            )}
          >
            {listID ? (
              <>
                <ListDot id={listID} className="pr-0 text-sm" />
                <span className="max-w-14 truncate sm:max-w-24 md:max-w-52 lg:max-w-none">
                  {listMetaData[listID]?.name?.trim()}
                </span>
              </>
            ) : (
              <>
                {variant === "default" && (
                  <span className="inline-block h-2.5 w-2.5 rounded-full bg-muted-foreground/60" />
                )}
                <p>List</p>
              </>
            )}
            <ChevronDown className="h-4 w-4 text-muted-foreground" />
          </Button>
        </PopoverTrigger>

        <PopoverContent className="space-y-1 p-1 text-sm">
          <Input
            placeholder="Search lists..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="mb-1 w-full rounded-sm bg-inherit text-[1.1rem]! brightness-75 outline-0 ring-0 ring-black focus-visible:ring-0 focus-visible:ring-offset-0 md:text-base! lg:text-sm!"
            onKeyDown={(e) => e.stopPropagation()}
            autoFocus
          />

          {canCreateList && (
            <button
              type="button"
              onClick={() => {
                setOpen(false);
                setCreateSheetOpen(true);
              }}
              className="flex w-full items-center gap-2 rounded-sm p-1.5 text-left text-sm hover:bg-popover-accent"
            >
              <Plus className="h-4 w-4" />
              Create list "{normalizedSearch}"
            </button>
          )}

          {filteredLists.length === 0 && !canCreateList && (
            <p className="w-full py-10 text-center text-sm text-muted-foreground">
              No lists...
            </p>
          )}

          {filteredLists.map(([key, value]) => (
            <div
              key={key}
              className="cursor-pointer rounded-sm p-1.5 text-sm hover:bg-popover-accent"
              onClick={() => {
                setSelectedList(key);
                setOpen(false);
              }}
            >
              <ListDot id={key} className="pr-0 text-sm" /> {value.name.trim()}
            </div>
          ))}

          {listID && (
            <>
              <DropdownMenuSeparator />
              <div
                className="flex cursor-pointer gap-2 rounded-sm p-1.5 hover:bg-red/80 hover:text-white"
                onClick={() => {
                  setSelectedList(null);
                  setOpen(false);
                }}
              >
                <Trash strokeWidth={1.7} className="h-4 w-4" />
                Clear
              </div>
            </>
          )}
        </PopoverContent>
      </Popover>
      <ListFormSheet
        open={createSheetOpen}
        onOpenChange={setCreateSheetOpen}
        initialName={normalizedSearch}
        onSaved={(created) => {
          if (created?.id) {
            setSelectedList(created.id);
          }
          setSearch("");
        }}
      />
    </>
  );
}
