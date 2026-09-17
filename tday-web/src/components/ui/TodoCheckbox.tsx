import clsx from "clsx";
import React, { useEffect, useRef, useState } from "react";
import { RefreshCcw } from "lucide-react";
import { hapticSuccess } from "@/lib/haptics";
import { isSoundEnabled } from "@/lib/feedbackPreferences";
import { addDiagnosticBreadcrumb } from "@/lib/observability/sentry";
import { DURATION_MS } from "@/lib/motion";

/**
 * The level both cues are played at. The completion clip is the same file on every client, and
 * this is the other half of "the same pop": Android's `TaskCompletionSound` plays it at
 * `VOLUME = 0.5f` and iOS's `SoundManager` at `player.volume = 0.5`, so a bare `HTMLAudioElement`
 * left on its 1.0 default would answer a completion some 6 dB louder here than on the phone.
 */
const PLAYBACK_VOLUME = 0.5;

/**
 * Which half of the pair a tap is asking for — the same question `complete` answers, named so
 * that the element picked and the cue reported to telemetry cannot come apart.
 */
type Cue = "complete" | "uncomplete";

/**
 * Play one cue, and answer the promise the browser hands back.
 *
 * `HTMLMediaElement.play()` returns a promise, and this file used to drop it. Everything a refusal
 * can mean — a media resource the element could not load, a clip that would not decode, a policy
 * that would not let the element start — was reported to nobody: no console line, no rejected
 * promise surfacing anywhere, no breadcrumb. "The switch is on and nothing pops" is exactly what a
 * swallowed refusal looks like, and this is the swallow that made it take an instrumented browser
 * to find.
 *
 * So a refusal is named now, structurally: which cue, and what the browser called it — never a
 * title, a list name or a task id, the same rule `use-bulk-todo-actions` follows.
 *
 * And when the reason is a resource the element could not load, the element is reloaded. A media
 * element whose resource selection has failed stays sourceless — it does not retry on its own, so
 * `play()` asks the same broken element again on every later tap and is refused every time. One
 * fetch that failed at mount would therefore mute that row for the whole of its life, which is a
 * second, quieter version of the same bug. `load()` restarts resource selection, so the next tap
 * has a clip to play.
 *
 * The other refusal a browser names — `NotAllowedError`, the autoplay policy — is reported and
 * deliberately NOT recovered, and that boundary is written down here rather than left implied.
 * This call is made synchronously inside the checkbox's own `onClick`, so the tap asking for the
 * cue is itself the activation the policy wants: a real tap on this control, first or repeated,
 * is answered by both engines the app ships on rather than refused. `TodoCheckbox` is the app's
 * only caller of `play()`, and nothing clicks a checkbox on its behalf, so there is no path that
 * reaches the policy gate without a gesture. If one ever did, neither recovery would help — a
 * retry from inside the catch has no fresh activation to offer and is refused the same way, and
 * `load()` answers it with nothing, the element having had a clip — so the class is not one a
 * change here can cure. What this owes it is the report: the breadcrumb names the reason, so a
 * policy refusal in the field can be told apart from the source-load refusal `load()` does cure.
 */
function playCue(audio: HTMLAudioElement, cue: Cue): void {
  // Rewind first, so two quick taps pop twice rather than the second finding the clip ended.
  audio.currentTime = 0;
  audio.play().catch((error: unknown) => {
    const reason = error instanceof Error ? error.name : "unknown";
    addDiagnosticBreadcrumb("sound.play_refused", {
      cue,
      reason,
      // Chromium and WebKit put the decode/load verdict on the element itself, as a number:
      // 4 is `MEDIA_ERR_SRC_NOT_SUPPORTED`, the one a missing or non-audio source lands on.
      media_error: audio.error?.code ?? 0,
    });
    if (reason === "NotSupportedError") {
      audio.load();
    }
  });
}

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
    // Set once, on the elements, rather than at the tap: the level is a property of the player,
    // not of the moment, and the phone sets it the same way (`SoundManager` at construction,
    // `TaskCompletionSound` at its constant). Both cues take it so the pair stays matched.
    popAudio.current.volume = PLAYBACK_VOLUME;
    unpopAudio.current.volume = PLAYBACK_VOLUME;
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
          // One question for both clips, asked at the tap rather than at the
          // `new Audio` above: the preference is about whether this browser makes
          // a noise, and it can be turned off in Settings while a list is on
          // screen — a clip decided at mount would keep popping until the row
          // remounted. `playCue` rewinds first either way, so two quick taps pop twice.
          //
          // `complete` is the state BEFORE the tap — the row's persisted flag, which is what the
          // callers pass — so a completing tap reads `complete === false` and gets the completing
          // cue. The cue names the element too, so the clip, the breadcrumb and the haptic below
          // all answer the same value and cannot drift apart.
          const cue: Cue = complete ? "uncomplete" : "complete";
          const audio = cue === "complete" ? popAudio.current : unpopAudio.current;
          if (audio && isSoundEnabled()) {
            playCue(audio, cue);
          }
          // The haptic stays on the completing half only, and reaches the
          // vibrator through `haptics.ts`, which asks the user the same question.
          if (!complete) hapticSuccess();
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
