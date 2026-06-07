import { useEffect, useMemo, useState } from "react";
import { Flag, List as ListIcon } from "lucide-react";
import { useTranslation } from "react-i18next";
import AppBottomSheet from "@/components/ui/AppBottomSheet";
import FloaterListDot from "@/features/floaterList/component/FloaterListDot";
import {
  SheetCard,
  SheetDivider,
  SheetSectionTitle,
  SheetSelectorRow,
} from "@/components/ui/sheet-chrome";
import {
  CenteredSelectorOverlay,
  SelectorDivider,
  SelectorRow,
} from "@/components/ui/sheet-chrome/CenteredSelectorOverlay";
import { prioritySwatchClass } from "@/components/ui/sheet-chrome/swatches";
import { getPriorityFlag } from "@/lib/priority";
import { useToast } from "@/hooks/use-toast";
import { useCreateFloater } from "@/features/floater/query/create-floater";
import { useEditFloater } from "@/features/floater/query/update-floater";
import { useFloaterListMetaData } from "@/features/floaterList/query/get-floater-list-meta";
import type { FloaterItemType } from "@/types";

type FloaterFormSheetProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  floater?: FloaterItemType;
  overrideFields?: { listID?: string };
};

const priorityOptions: Array<{
  value: FloaterItemType["priority"];
  labelKey: "normal" | "important" | "urgent";
}> = [
  { value: "Low", labelKey: "normal" },
  { value: "Medium", labelKey: "important" },
  { value: "High", labelKey: "urgent" },
];

export default function FloaterFormSheet({
  open,
  onOpenChange,
  floater,
  overrideFields,
}: FloaterFormSheetProps) {
  const { t: appDict } = useTranslation("app");
  const { toast } = useToast();
  const { createMutateFn, createStatus } = useCreateFloater();
  const { editTodoMutateFn, editTodoStatus } = useEditFloater();
  const { floaterListMetaData } = useFloaterListMetaData();
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState<FloaterItemType["priority"]>("Low");
  const [listID, setListID] = useState<string | null>(null);
  const [activeSelector, setActiveSelector] = useState<"priority" | "list" | null>(
    null,
  );

  useEffect(() => {
    if (!open) return;
    setTitle(floater?.title ?? "");
    setDescription(floater?.description ?? "");
    setPriority(floater?.priority ?? "Low");
    setListID(overrideFields?.listID ?? floater?.listID ?? null);
    setActiveSelector(null);
  }, [floater, open, overrideFields?.listID]);

  useEffect(() => {
    if (!floater && createStatus === "success") {
      onOpenChange(false);
    }
  }, [createStatus, floater, onOpenChange]);

  useEffect(() => {
    if (floater && editTodoStatus === "success") {
      onOpenChange(false);
    }
  }, [editTodoStatus, floater, onOpenChange]);

  const lists = useMemo(
    () =>
      Object.entries(floaterListMetaData)
        .filter(([, list]) => Boolean(list.name?.trim()))
        .map(([id, list]) => ({ id, ...list }))
        .sort((a, b) => a.name.localeCompare(b.name)),
    [floaterListMetaData],
  );

  const selectedListName = listID ? floaterListMetaData[listID]?.name?.trim() : null;
  const canSubmit = title.trim().length > 0;
  const isEditing = Boolean(floater?.id);

  const handleSubmit = () => {
    const normalizedTitle = title.trim();
    if (!normalizedTitle) return;

    try {
      const payload: FloaterItemType = {
        id: floater?.id ?? "-1",
        title: normalizedTitle,
        description: description.trim() ? description : null,
        priority,
        listID: listID ?? null,
        pinned: floater?.pinned ?? false,
        completed: floater?.completed ?? false,
        createdAt: floater?.createdAt ?? new Date(),
        updatedAt: floater?.updatedAt ?? null,
        order: floater?.order ?? Number.MAX_VALUE,
        userID: floater?.userID ?? null,
      };
      if (isEditing) {
        editTodoMutateFn(payload);
      } else {
        setTitle("");
        setDescription("");
        createMutateFn(payload);
      }
    } catch (error) {
      if (error instanceof Error) {
        toast({ description: error.message, variant: "destructive" });
      }
    }
  };

  return (
    <AppBottomSheet
      variant="native"
      open={open}
      onOpenChange={onOpenChange}
      title={isEditing ? appDict("editFloater") : appDict("newFloater")}
      onClose={() => onOpenChange(false)}
      onConfirm={handleSubmit}
      confirmDisabled={!canSubmit}
      confirmLabel={appDict("save")}
      closeLabel={appDict("cancel")}
      bodyClassName="pb-6"
    >
      <div className="flex flex-col gap-3 pb-2">
        <SheetCard>
          <div className="px-[18px] pb-2 pt-3">
            <textarea
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              enterKeyHint="done"
              onKeyDown={(event) => {
                if (event.key === "Enter" && !event.shiftKey) {
                  // Enter dismisses the keyboard (like native); Shift+Enter adds a newline.
                  event.preventDefault();
                  event.currentTarget.blur();
                }
              }}
              placeholder={appDict("floaterTitlePlaceholder")}
              className="min-h-12 w-full resize-none bg-transparent text-lg font-black text-foreground placeholder:text-muted-foreground/60 focus:outline-hidden"
            />
          </div>
          <SheetDivider />
          <input
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            name="description"
            placeholder={appDict("notes")}
            className="w-full bg-transparent px-[18px] py-3 text-base font-bold text-foreground placeholder:font-bold placeholder:text-muted-foreground/60 focus:outline-hidden"
          />
        </SheetCard>

        <SheetSectionTitle>{appDict("details")}</SheetSectionTitle>
        <SheetCard>
          <SheetSelectorRow
            icon={<ListIcon className="h-5 w-5" />}
            label={appDict("floaterList")}
            ariaLabel={`${appDict("floaterList")}, ${
              selectedListName ?? appDict("noList")
            }`}
            value={
              listID && selectedListName ? (
                <>
                  <FloaterListDot id={listID} className="h-3.5 w-3.5" />
                  <span className="truncate">{selectedListName}</span>
                </>
              ) : (
                appDict("noList")
              )
            }
            onClick={() => setActiveSelector("list")}
          />
          <SheetDivider />
          <SheetSelectorRow
            icon={<Flag className="h-5 w-5" />}
            label={appDict("priority")}
            ariaLabel={`${appDict("priority")}, ${appDict(
              priorityOptions.find((option) => option.value === priority)?.labelKey ??
                "normal",
            )}`}
            value={
              <>
                {getPriorityFlag(priority) ? (
                  <Flag
                    className={`h-3.5 w-3.5 shrink-0 ${getPriorityFlag(priority)!.className}`}
                  />
                ) : null}
                <span className="truncate">
                  {appDict(
                    priorityOptions.find((option) => option.value === priority)?.labelKey ??
                      "normal",
                  )}
                </span>
              </>
            }
            onClick={() => setActiveSelector("priority")}
          />
        </SheetCard>

        <CenteredSelectorOverlay
          open={activeSelector === "list"}
          onOpenChange={(open) => !open && setActiveSelector(null)}
          title={appDict("floaterList")}
        >
          <SelectorRow
            label={appDict("noList")}
            selected={listID == null}
            onClick={() => {
              setListID(null);
              setActiveSelector(null);
            }}
          />
          {lists.map((list) => (
            <div key={list.id}>
              <SelectorDivider />
              <SelectorRow
                label={list.name.trim()}
                swatchNode={<FloaterListDot id={list.id} className="h-2.5 w-2.5" />}
                selected={listID === list.id}
                onClick={() => {
                  setListID(list.id);
                  setActiveSelector(null);
                }}
              />
            </div>
          ))}
        </CenteredSelectorOverlay>

        <CenteredSelectorOverlay
          open={activeSelector === "priority"}
          onOpenChange={(open) => !open && setActiveSelector(null)}
          title={appDict("priority")}
        >
          {priorityOptions.map((option, index) => (
            <div key={option.value}>
              {index > 0 ? <SelectorDivider /> : null}
              <SelectorRow
                label={appDict(option.labelKey)}
                swatchClass={prioritySwatchClass(option.value)}
                selected={priority === option.value}
                onClick={() => {
                  setPriority(option.value);
                  setActiveSelector(null);
                }}
              />
            </div>
          ))}
        </CenteredSelectorOverlay>
      </div>
    </AppBottomSheet>
  );
}
