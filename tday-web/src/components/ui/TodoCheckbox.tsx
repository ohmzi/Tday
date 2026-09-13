import clsx from "clsx";
import React, { useEffect, useRef, useState } from "react";
import { RefreshCcw } from "lucide-react";
import { hapticSuccess } from "@/lib/haptics";
import { DURATION_MS } from "@/lib/motion";

export default function TodoCheckbox({
  complete,
  onChange,
  checked,
  icon: Icon,
  variant = "outline-solid"
}: {
  complete: boolean;
  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  checked: boolean;
  icon: React.ElementType;
  variant?: "repeat" | "outline-solid";
}) {
  const [expand, setExpand] = useState(false);
  const popAudio = useRef<HTMLAudioElement | null>(null);
  const unpopAudio = useRef<HTMLAudioElement | null>(null);

  useEffect(() => {
    popAudio.current = new Audio("/task-complete.wav");
    unpopAudio.current = new Audio("/task-uncomplete.wav");
  }, []);

  // The pop goes out as long as it came in. Quick is the rung for the app answering a finger
  // that is still on the control (`docs/motion.md`), and the number is read from the vocabulary
  // rather than typed here so the hold and the transition below cannot drift apart.
  useEffect(() => {
    if (expand) {
      const timeout = setTimeout(() => setExpand(false), DURATION_MS.quick);
      return () => clearTimeout(timeout);
    }
  }, [complete, expand]);

  return (
    <label onPointerDown={(e) => e.stopPropagation()}>
      <input
        onPointerDown={(e) => e.stopPropagation()}
        type="checkbox"
        className="peer hidden"
        onChange={(e) => {
          onChange(e);
        }}
        onClick={() => {
          if (!complete) {
            if (popAudio.current) popAudio.current.currentTime = 0;
            popAudio.current?.play();
            hapticSuccess();
          } else {
            if (unpopAudio.current) unpopAudio.current.currentTime = 0;
            unpopAudio.current?.play();
          }
        }}
        checked={checked}
      />

      {variant === "outline-solid" ? (
        <div
          // Pointer, not mouse, for the pop. This is the most-tapped control in the app and the
          // squash was armed from `onMouseDown`, an event a touch browser either synthesises
          // several hundred milliseconds late — after the tap has already been dispatched — or
          // never sends at all. The phone got the sound, the haptic and the strike, and the one
          // piece of feedback that belongs to the finger itself was the piece it did not get.
          onPointerDown={(e) => {
            e.stopPropagation();
            setExpand(true);
          }}
          // `mousedown` stays stopped as well, and for a different reason: the rows this
          // checkbox sits in are dnd-kit draggables whose `MouseSensor` activates on `mousedown`,
          // not on `pointerdown`. `TaskActionButtons.tsx` writes that lesson up at length — a
          // click with a few pixels of travel in it gets read as a drag-start and swallowed.
          // Arming the pop from the pointer is about when the squash is drawn; this is about
          // whether the tap survives at all, so one does not replace the other.
          onMouseDown={(e) => e.stopPropagation()}
          className={clsx(
            "relative group w-5 h-5 rounded-full flex items-center justify-center border-[2.23px]",
            // One transition for the squash and the fill, on one curve, because they are one
            // event: the tick landing under the finger. Quick and the Gesture curve are what
            // `docs/motion.md` names for press feedback — the same pair `globals.css` gives
            // every other pressable surface.
            "hover:cursor-pointer transition-all duration-quick ease-gesture",
            // Empty outline when incomplete; solid green fill + white check when complete.
            checked
              ? "border-accent-lime bg-accent-lime"
              : "border-foreground hover:border-transparent",
            expand && "scale-125",
          )}
        >
          <Icon
            className={clsx(
              "pointer-events-none absolute bottom-1/2 translate-y-1/2 right-1/2 translate-x-1/2",
              "stroke-3 w-5 h-5",
              checked ? "block text-white" : "hidden group-hover:block text-foreground",
            )}
          />
        </div>
      ) : (
        <div className="relative group">
          <RefreshCcw
            strokeWidth={2.35}
            onPointerDown={(e) => {
              e.stopPropagation();
              setExpand(true);
            }}
            // The drag-sensor guard the outline variant carries, for the same reason.
            onMouseDown={(e) => e.stopPropagation()}
            className={clsx(
              "group w-[1.35rem] h-[1.35rem] flex items-center justify-center",
              checked ? "text-accent-lime" : "text-foreground",
              "hover:cursor-pointer hover:stroke-transparent",
            )}
          />

          <Icon
            className={clsx(
              "pointer-events-none absolute bottom-1/2 translate-y-1/2 right-1/2 translate-x-1/2 transition-transform duration-quick ease-gesture",
              "stroke-3 w-5 h-5",
              expand && "scale-125",
              checked ? "block text-accent-lime" : "hidden group-hover:block text-foreground",
            )}
          />
        </div>
      )}
    </label>
  );
}
