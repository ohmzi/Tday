// TEST-CRASH: temporary test controls; see lib/testCrash.ts.
import { useState } from "react";
import {
  TEST_CRASH_LABELS,
  buildTestCrashError,
  fireTestCrash,
  type TestCrashId,
} from "@/lib/testCrash";

const BUTTON_CLASS =
  "rounded-lg bg-destructive px-3 py-2 text-sm font-black text-destructive-foreground";
const NOTE_CLASS = "text-xs font-extrabold text-muted-foreground";

function RenderBomb(): null {
  throw buildTestCrashError("TC-SET-RENDER");
}

export function TestCrashButton({ id }: { id: TestCrashId }) {
  return (
    <div className="flex flex-col items-start gap-1 px-1 py-2">
      <button type="button" className={BUTTON_CLASS} onClick={() => fireTestCrash(id)}>
        {TEST_CRASH_LABELS.button(id)}
      </button>
      <p className={NOTE_CLASS}>{TEST_CRASH_LABELS.note}</p>
    </div>
  );
}

export function TestCrashSettingsControls() {
  const [armed, setArmed] = useState(false);
  const [freezing, setFreezing] = useState(false);
  return (
    <div className="flex flex-col items-start gap-2 px-1 py-2">
      <p className="text-sm font-black text-foreground">{TEST_CRASH_LABELS.settingsTitle}</p>
      <div className="flex flex-wrap gap-2">
        <button type="button" className={BUTTON_CLASS} onClick={() => fireTestCrash("TC-SET-CRASH")}>
          {TEST_CRASH_LABELS.button("TC-SET-CRASH")}
        </button>
        <button type="button" className={BUTTON_CLASS} onClick={() => fireTestCrash("TC-SET-ERROR")}>
          {TEST_CRASH_LABELS.button("TC-SET-ERROR")}
        </button>
        <button type="button" className={BUTTON_CLASS} onClick={() => fireTestCrash("TC-SET-REJECT")}>
          {TEST_CRASH_LABELS.button("TC-SET-REJECT")}
        </button>
        <button type="button" className={BUTTON_CLASS} onClick={() => setArmed(true)}>
          {TEST_CRASH_LABELS.button("TC-SET-RENDER")}
        </button>
        <button
          type="button"
          className={BUTTON_CLASS}
          disabled={freezing}
          onClick={() => {
            setFreezing(true);
            // One frame later, so the disabled state paints before the thread is blocked.
            window.setTimeout(() => {
              fireTestCrash("TC-SET-FREEZE");
              setFreezing(false);
            }, 50);
          }}
        >
          {freezing ? TEST_CRASH_LABELS.freezeBusy : TEST_CRASH_LABELS.button("TC-SET-FREEZE")}
        </button>
      </div>
      <p className={NOTE_CLASS}>{TEST_CRASH_LABELS.note}</p>
      {armed ? <RenderBomb /> : null}
    </div>
  );
}

