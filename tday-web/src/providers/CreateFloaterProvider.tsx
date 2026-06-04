import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import FloaterFormSheet from "@/features/floater/component/FloaterFormSheet";

type CreateFloaterOverrides = { listID?: string };

type CreateFloaterContextValue = {
  openCreateFloater: (overrideFields?: CreateFloaterOverrides) => void;
};

const CreateFloaterContext = createContext<CreateFloaterContextValue | null>(null);

export default function CreateFloaterProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const [overrideFields, setOverrideFields] = useState<
    CreateFloaterOverrides | undefined
  >(undefined);

  const openCreateFloater = useCallback((fields?: CreateFloaterOverrides) => {
    setOverrideFields(fields);
    setOpen(true);
  }, []);

  const value = useMemo(() => ({ openCreateFloater }), [openCreateFloater]);

  return (
    <CreateFloaterContext.Provider value={value}>
      {children}
      <FloaterFormSheet
        open={open}
        onOpenChange={setOpen}
        overrideFields={overrideFields}
      />
    </CreateFloaterContext.Provider>
  );
}

export function useCreateFloaterTask() {
  const context = useContext(CreateFloaterContext);
  if (!context) {
    throw new Error("useCreateFloaterTask must be used within CreateFloaterProvider");
  }
  return context;
}
