import clsx from "clsx";
import React from "react";
import PlusCircle from "@/components/ui/icon/plusCircle";
import { useCreateProject } from "./query/create-project";
import Spinner from "@/components/ui/spinner";
import { Folder } from "lucide-react";
import { Button } from "@/components/ui/button";
import ProjectLoading from "./ProjectLoading";
import { useProjectMetaData } from "./query/get-project-meta";
import ProjectSidebarItem from "./ProjectSidebarItem";
import { useTranslations } from "next-intl";


const ProjectCollapsible = () => {
  const appDict = useTranslations("app")
  const { projectMetaData, isPending } = useProjectMetaData();
  const { createMutateFn, createLoading } = useCreateProject();
  return (
    <div className="group w-full">
      <div className="mb-1 flex w-full items-center justify-start px-3 py-2">
        <div
          className={clsx(
            "flex h-9 w-full items-center gap-2 rounded-lg px-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground/90",
          )}
        >
          <Folder
            className={clsx(
              "h-3.5 w-3.5 stroke-muted-foreground",
            )}
          />
          <p className="select-none">{appDict("project")}</p>
          {createLoading ? (
            <Spinner className="mr-0 ml-auto w-5 h-5" />
          ) : (
            <div
              className="mr-0 ml-auto"
              onClick={(e: React.MouseEvent<HTMLDivElement>) => {
                e.stopPropagation();
                createMutateFn({ name: "new Project" });
              }}
            >
              <Button
                size={"icon"}
                className="hidden h-7 w-7 bg-transparent p-1 text-muted-foreground transition-colors hover:bg-sidebar-accent/70 hover:text-foreground group-hover:flex"
              >
                <PlusCircle className="h-4 w-4" />
              </Button>
            </div>
          )}
        </div>
      </div>
      <div>
        <div>
          {isPending ? (
            <ProjectLoading />
          ) : (
            Object.entries(projectMetaData).map(([key, value]) => {
              return <ProjectSidebarItem key={key} meta={{ id: key, ...value }} />;
            })
          )}
        </div>
      </div>
    </div>
  );
};

export default ProjectCollapsible;
