import {
  Collapsible,
  CollapsibleTrigger,
  CollapsibleContent,
} from "@radix-ui/react-collapsible";
import clsx from "clsx";
import React, { useState } from "react";
import { useMenu } from "@/providers/MenuProvider";
import { PlusCircle } from "lucide-react";
import { useCreateNote } from "@/features/notes/query/create-note";
import Spinner from "@/components/ui/spinner";
import { FileText } from "lucide-react";
import { Button } from "@/components/ui/button";
import dynamic from "next/dynamic";
import NoteLoading from "./NoteLoading";
import { useTranslations } from "next-intl";
const NoteSidebarItemContainer = dynamic(
  () => import("./NoteSidebarItemContainer"),
  { loading: () => <NoteLoading /> },
);

const NoteCollapsible = () => {
  const sidebarDict = useTranslations("sidebar")

  const { activeMenu, setActiveMenu } = useMenu();
  const [showPlus, setShowPlus] = useState(false);

  const { createNote, createLoading } = useCreateNote();
  return (
    <Collapsible
      className="w-full"
      open={activeMenu.open && activeMenu.name == "Note" === true}
      onOpenChange={(open) => {
        setActiveMenu({ name: "Note", open });
      }}
    >
      <CollapsibleTrigger
        onMouseEnter={() => setShowPlus(true)}
        onMouseLeave={() => setShowPlus(false)}
        onClick={() => { }}
        asChild
        className="w-full items-start justify-start"
      >
        <Button
          variant={"ghost"}
          className={clsx(
            "h-10 w-full justify-start gap-3 rounded-xl border border-transparent px-3 text-sm font-medium text-sidebar-foreground/70 transition-colors hover:bg-sidebar-accent/50 hover:text-sidebar-foreground",
            activeMenu.name === "Note" &&
            "bg-sidebar-accent text-sidebar-accent-foreground",
          )}
        >
          <FileText className="h-4 w-4 shrink-0" />
          <p className="select-none truncate whitespace-nowrap text-foreground">{sidebarDict("note")}</p>
          {createLoading ? (
            <Spinner className="mr-0 ml-auto w-5 h-5" />
          ) : (
            showPlus && (
              <div className="ml-auto  hover:stroke-foreground! w-fit h-fit bg-inherit! cursor-pointer! text-muted-foreground hover:text-foreground "

                onClick={(e: React.MouseEvent<HTMLDivElement>) => {
                  e.stopPropagation();
                  createNote({ name: "new note" });
                  setActiveMenu({ name: "Note", open: true });
                }}
              >
                <PlusCircle className="h-4 w-4" />
              </div>
            )

          )}
        </Button>
      </CollapsibleTrigger>
      <CollapsibleContent>
        {activeMenu.open && <NoteSidebarItemContainer />}
      </CollapsibleContent>
    </Collapsible>
  );
};

export default NoteCollapsible;
