import { useDeleteList } from "./query/delete-list";
import { useRenameList } from "./query/rename-list";
import { useMenu } from "@/providers/MenuProvider";
import { ListItemType } from "@/types";
import clsx from "clsx";
import { Link } from "@/i18n/navigation";

import React, { useEffect, useRef, useState } from "react";
import Spinner from "@/components/ui/spinner";
import Meatball from "@/components/ui/icon/meatball";
import useWindowSize from "@/hooks/useWindowSize";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger, DropdownMenuSub, DropdownMenuSubTrigger, DropdownMenuSubContent } from "@/components/ui/dropdown-menu";
import { useRecolorList } from "./query/update-list-color";
import { listColorMap } from "@/lib/listColorMap";
import ListDot from "@/components/ListDot";
import { useTranslations } from "next-intl";

const ListSidebarItem = ({ meta }: { meta: Pick<ListItemType, "id" | "color" | "name"> }) => {
  const listMenuDict = useTranslations("projectMenu");
  const noteMenu = useTranslations("sidebar.noteMenu");
  const { renameMutateFn } = useRenameList();
  const { recolorMutateFn } = useRecolorList();
  const { width } = useWindowSize();
  //states for renaming
  const [name, setName] = useState(meta.name);

  const [isRenaming, setIsRenaming] = useState(false);
  const inputRef = useRef<HTMLInputElement | null>(null);

  const { activeMenu, setActiveMenu, setShowMenu } = useMenu();
  const { deleteMutateFn, deleteLoading } = useDeleteList();
  //focus name input on isRenaming
  useEffect(() => {
    const nameInput = inputRef.current;

    if (isRenaming && nameInput) {
      setTimeout(() => nameInput.select(), 300)
    }
  }, [isRenaming]);


  //rename on click outside or enter key
  useEffect(() => {
    const nameInput = inputRef.current;

    function onEnterKeyPress(e: KeyboardEvent) {
      if (e.key === "Enter" && isRenaming) {
        setIsRenaming(false);
        renameMutateFn({ id: meta.id, name });
      }
    }
    function onClickOutside(e: MouseEvent) {
      if (nameInput && !nameInput.contains(e.target as Node)) {
        setIsRenaming(false);
      }
    }
    document.addEventListener("keydown", onEnterKeyPress);
    document.addEventListener("mousedown", onClickOutside);
    return () => {
      document.removeEventListener("keydown", onEnterKeyPress);
      document.removeEventListener("mousedown", onClickOutside);
    };
  }, [meta.id, renameMutateFn, name, isRenaming]);

  return (
    <>
      <div className="group relative text-foreground">
        {isRenaming ? (
          <div className="mt-1 flex items-center justify-between rounded-lg px-2 py-1 pl-8">
            <ListDot id={meta.id} />
            <input
              ref={inputRef}
              type="text"
              title={meta.name}
              className="flex h-full w-full justify-between truncate border border-border/70 bg-transparent px-1 text-sm text-foreground outline-hidden ring-1 ring-border"

              value={name}
              onChange={(e) => setName(e.currentTarget.value)}
              onClick={(e) => e.stopPropagation()}
            />
          </div>
        ) : (
          <Link
            href={`/app/list/${meta.id}`}
            className={clsx(
              "mt-1 flex h-9 select-none items-center justify-between gap-2 rounded-lg px-2 py-1 pl-8 pr-2 text-sm text-sidebar-foreground/75 transition-colors hover:cursor-pointer hover:bg-sidebar-accent/65 hover:text-sidebar-foreground",
              activeMenu.children?.name === meta.id &&
              "bg-sidebar-primary text-sidebar-primary-foreground",
            )}
            onClick={() => {
              setActiveMenu({
                name: "List",
                open: true,
                children: { name: meta.id },
              });
              if (width <= 766) setShowMenu(false);
            }}
          >
            <div className="flex items-center justify-between rounded-lg">
              <ListDot id={meta.id} />
              {meta.name}
            </div>
          </Link>
        )}


        <div className="absolute right-1.5 top-1/2 flex -translate-y-1/2 px-1.5 opacity-0 transition-opacity group-hover:opacity-100">
          {deleteLoading ? (
            <Spinner className="w-5 h-5" />
          ) : (
            <DropdownMenu>
              <DropdownMenuTrigger>
                <Meatball className="h-4 w-4 cursor-pointer text-muted-foreground hover:text-foreground" />
              </DropdownMenuTrigger>
              <DropdownMenuContent>
                <DropdownMenuItem onClick={() => setIsRenaming(true)}> {noteMenu("rename")}</DropdownMenuItem>
                <DropdownMenuItem onClick={() => deleteMutateFn({ id: meta.id })}>
                  {noteMenu("delete")}
                </DropdownMenuItem>
                <DropdownMenuSub>
                  <DropdownMenuSubTrigger>
                    {listMenuDict("editColors")}
                  </DropdownMenuSubTrigger>
                  <DropdownMenuSubContent className="max-h-60 overflow-scroll">
                    {listColorMap.map((color) => {
                      // map list color values to static Tailwind classes
                      const colorClassMap: Record<string, string> = {
                        RED: "bg-accent-red",
                        ORANGE: "bg-accent-orange",
                        YELLOW: "bg-accent-yellow",
                        LIME: "bg-accent-lime",
                        BLUE: "bg-accent-blue",
                        PURPLE: "bg-accent-purple",
                        PINK: "bg-accent-pink",
                        TEAL: "bg-accent-teal",
                        CORAL: "bg-accent-coral",
                        GOLD: "bg-accent-gold",
                        DEEP_BLUE: "bg-accent-deep-blue",
                        ROSE: "bg-accent-rose",
                        LIGHT_RED: "bg-accent-light-red",
                        BRICK: "bg-accent-brick",
                        SLATE: "bg-accent-slate",
                      };

                      const bgClass = colorClassMap[color.value];

                      return (
                        <DropdownMenuItem
                          key={color.value}
                          onClick={() => recolorMutateFn({ id: meta.id, color: color.value })}
                        >
                          <span
                            className={`w-5 h-5 ${bgClass} border border-popover-border rounded-sm`}
                          ></span>
                          {color.name == "Deep Blue" ? listMenuDict("deepBlue") : color.name == "Light Red" ? listMenuDict("lightRed") : listMenuDict(color.name.toLowerCase())}
                        </DropdownMenuItem>
                      );
                    })}
                  </DropdownMenuSubContent>

                </DropdownMenuSub>
              </DropdownMenuContent>
            </DropdownMenu>
          )}
        </div>
      </div>
    </>
  );
};

export default ListSidebarItem;
