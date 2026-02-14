import {
  MenuContainer,
  MenuContent,
  MenuItem,
  MenuTrigger,
} from "@/components/ui/Menu";
import { useDeleteNote } from "@/features/notes/query/delete-note";
import { useRenameNote } from "@/features/notes/query/rename-note";
import { useMenu } from "@/providers/MenuProvider";
import { NoteItemType } from "@/types";
import clsx from "clsx";
import { Link } from "@/i18n/navigation";

import React, { useEffect, useRef, useState } from "react";
import Spinner from "@/components/ui/spinner";
import Meatball from "@/components/ui/icon/meatball";
import useWindowSize from "@/hooks/useWindowSize";
import { useTranslations } from "next-intl";

const NoteSidebarItem = ({ note }: { note: NoteItemType }) => {
  const sidebarDict = useTranslations("sidebar");
  const { renameMutate } = useRenameNote();
  const { width } = useWindowSize();
  //states for renaming
  const [name, setName] = useState(note.name);

  const [isRenaming, setIsRenaming] = useState(false);
  const inputRef = useRef<HTMLInputElement | null>(null);

  const { activeMenu, setActiveMenu, setShowMenu } = useMenu();
  const { deleteMutate, deleteLoading } = useDeleteNote();

  //focus name input on isRenaming
  useEffect(() => {
    const nameInput = inputRef.current;
    if (isRenaming === true && nameInput) {
      nameInput.focus();
    }
  }, [isRenaming]);

  //rename on click outside or enter key
  useEffect(() => {
    const nameInput = inputRef.current;

    function onEnterKeyPress(e: KeyboardEvent) {
      if (e.key === "Enter" && isRenaming) {
        setIsRenaming(false);
        renameMutate({ id: note.id, name });
      }
    }
    function onClickOutside(e: MouseEvent) {
      if (nameInput && !nameInput.contains(e.target as Node)) {
        setIsRenaming(false);
        renameMutate({ id: note.id, name });
      }
    }
    document.addEventListener("keydown", onEnterKeyPress);
    document.addEventListener("mousedown", onClickOutside);
    return () => {
      document.removeEventListener("keydown", onEnterKeyPress);
      document.removeEventListener("mousedown", onClickOutside);
    };
  }, [note.id, renameMutate, name, isRenaming]);

  return (
    <>
      <div className="group relative select-none">
        <Link
          href={`/app/note/${note.id}`}
          className={clsx(
            "mt-1 flex h-9 select-none items-center justify-between gap-2 rounded-lg px-2 py-2 pl-11 pr-2 text-sm text-sidebar-foreground/75 transition-colors hover:cursor-pointer hover:bg-sidebar-accent/65 hover:text-sidebar-foreground",
            activeMenu.children?.name === note.id &&
            "bg-sidebar-primary text-sidebar-primary-foreground",
          )}
          onClick={() => {
            setActiveMenu({
              name: "Note",
              open: true,
              children: { name: note.id },
            });
            if (width <= 766) setShowMenu(false);
          }}
        >
          {isRenaming ? (
            <input
              ref={inputRef}
              type="text"
              title={note.name}
              className={clsx(
                "flex w-[clamp(4rem,50%,10rem)] justify-between truncate bg-transparent text-foreground outline-hidden",
              )}
              value={name}
              onChange={(e) => {
                setName(e.currentTarget.value);
              }}
            />
          ) : (
            <div className="flex justify-between rounded-lg">
              {name}
            </div>
          )}
        </Link>

        <div className="absolute right-1.5 top-1/2 flex -translate-y-1/2 px-1.5 opacity-0 transition-opacity group-hover:opacity-100">
          {deleteLoading ? (
            <Spinner className="w-5 h-5" />
          ) : (
            <MenuContainer>
              <MenuTrigger>
                <Meatball className="h-4 w-4 text-muted-foreground hover:text-foreground" />
              </MenuTrigger>
              <MenuContent>
                <MenuItem onClick={() => setIsRenaming(true)}> {sidebarDict("noteMenu.rename")}</MenuItem>
                <MenuItem onClick={() => deleteMutate({ id: note.id })}>
                  {sidebarDict("noteMenu.delete")}
                </MenuItem>
              </MenuContent>
            </MenuContainer>
          )}
        </div>
      </div>
    </>
  );
};

export default NoteSidebarItem;
