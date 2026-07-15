import { useState } from "react";
import { useGlobalHotkeys } from "@/hooks/useGlobalHotkeys";
import { useCreateTask } from "@/providers/CreateTaskProvider";
import CommandPalette from "./CommandPalette";
import ShortcutsOverlay from "./ShortcutsOverlay";
import BrainDumpModal from "@/features/brainDump/BrainDumpModal";

/**
 * Mounts the app-wide keyboard layer inside the authenticated shell:
 * N → quick-add sheet, Cmd/Ctrl+K → command palette, ? → shortcuts overview.
 * Must live under CreateTaskProvider (N and the palette's "new task" action
 * open the shared TaskFormSheet through it).
 */
export default function KeyboardLayer() {
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [shortcutsOpen, setShortcutsOpen] = useState(false);
  const [brainDumpOpen, setBrainDumpOpen] = useState(false);
  const { openCreateTask } = useCreateTask();

  useGlobalHotkeys({
    onNewTask: () => {
      setPaletteOpen(false);
      setShortcutsOpen(false);
      openCreateTask();
    },
    onTogglePalette: () => {
      setShortcutsOpen(false);
      setPaletteOpen((wasOpen) => !wasOpen);
    },
    onShowShortcuts: () => {
      setPaletteOpen(false);
      setShortcutsOpen(true);
    },
  });

  return (
    <>
      <CommandPalette
        open={paletteOpen}
        onOpenChange={setPaletteOpen}
        onOpenBrainDump={() => {
          setPaletteOpen(false);
          setBrainDumpOpen(true);
        }}
      />
      <ShortcutsOverlay open={shortcutsOpen} onOpenChange={setShortcutsOpen} />
      <BrainDumpModal open={brainDumpOpen} onOpenChange={setBrainDumpOpen} />
    </>
  );
}
