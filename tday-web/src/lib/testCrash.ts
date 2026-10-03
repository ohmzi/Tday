// TEST-CRASH: temporary cross-check of crash reporting on web, Android and iOS. Every touch point
// is tagged TEST-CRASH, so `grep -rn TEST-CRASH` lists them all; the whole feature is removed by
// reverting the one commit that added it. Nothing here weakens consent, the scrubber or a gate:
// a report is only sent when the browser opted in and a DSN is built in.
import { useEffect } from "react";
import { ApiError, api } from "@/lib/api-client";
import { addDiagnosticBreadcrumb, captureUiException } from "@/lib/observability/sentry";

export type TestCrashVia = "timer" | "microtask" | "rejection" | "render" | "capture" | "freeze";

export type TestCrashPlan = {
  /** The error name Sentry shows (for a DOMException, its `name`). */
  kind: string;
  via: TestCrashVia;
  /** Short English description of the screen or action; goes into the message. */
  what: string;
};

export const TEST_CRASH_PLANS = {
  "TC-FEED-SCHED": { kind: "TypeError", via: "timer", what: "scheduled home feed" },
  "TC-FEED-ANY": { kind: "RangeError", via: "timer", what: "anytime feed" },
  "TC-BUILTIN-TODAY": { kind: "ReferenceError", via: "timer", what: "Today list" },
  "TC-BUILTIN-OVERDUE": { kind: "SyntaxError", via: "timer", what: "Overdue list" },
  "TC-BUILTIN-PRIO": { kind: "URIError", via: "timer", what: "Priority list" },
  "TC-BUILTIN-SCHED": { kind: "EvalError", via: "timer", what: "Scheduled list" },
  "TC-BUILTIN-ALL": { kind: "Error", via: "timer", what: "All tasks list" },
  "TC-BUILTIN-DONE": { kind: "InvalidStateError", via: "timer", what: "Completed list" },
  "TC-LIST-SCHED": { kind: "NotFoundError", via: "timer", what: "user scheduled list" },
  "TC-LIST-ANY": { kind: "DataCloneError", via: "timer", what: "user anytime list" },
  "TC-TASK-OPEN": { kind: "TypeError", via: "rejection", what: "first task opened" },
  "TC-TASK-EDIT": { kind: "RangeError", via: "microtask", what: "first task edited" },
  "TC-NEW-LIST": { kind: "QuotaExceededError", via: "timer", what: "create list sheet" },
  "TC-NEW-TASK": { kind: "SyntaxError", via: "rejection", what: "create task sheet" },
  "TC-CALENDAR": { kind: "ReferenceError", via: "rejection", what: "calendar" },
  "TC-SET-CRASH": { kind: "Error", via: "microtask", what: "settings fatal" },
  "TC-SET-ERROR": { kind: "Error", via: "capture", what: "settings handled capture" },
  "TC-SET-FREEZE": { kind: "Error", via: "freeze", what: "settings main thread freeze" },
  "TC-SET-REJECT": { kind: "EvalError", via: "rejection", what: "settings unhandled rejection" },
  "TC-SET-RENDER": { kind: "TypeError", via: "render", what: "settings render error" },
} as const satisfies Record<string, TestCrashPlan>;

export type TestCrashId = keyof typeof TEST_CRASH_PLANS;

export const TEST_CRASH_IDS = Object.keys(TEST_CRASH_PLANS) as TestCrashId[];

// Labels live here, not in JSX, so the i18n guardrails stay quiet about a throwaway control.
export const TEST_CRASH_LABELS = {
  button: (id: string) => `Test crash: ${id}`,
  note: "Reports are sent only if crash reports are on in Settings > Privacy.",
  settingsTitle: "Test crashes",
  freezeBusy: "Freezing for about 6 seconds",
  backendNote:
    "Server-side: reported only when the server's own error reports are on below, and admins only.",
  backendTitle: "Server-side test crashes",
} as const;

/**
 * The triggers the *server* can be asked to fail with, and the endpoints that set them off. The ids
 * are the ones the server names the report with, so the events read `TEST-CRASH TC-BACKEND-CRASH:
 * unhandled admin error` — the same shape Android, iOS and the web put in a client-side report.
 *
 * Admin only: the routes refuse anyone else, and nothing is sent unless the operator has switched
 * server error reports on.
 */
export const BACKEND_TEST_CRASHES = {
  "TC-BACKEND-CRASH": "/api/admin/telemetry/test-crash",
  "TC-BACKEND-ERROR": "/api/admin/telemetry/test-error",
} as const;

export type BackendTestCrashId = keyof typeof BACKEND_TEST_CRASHES;

export const BACKEND_TEST_CRASH_IDS = Object.keys(BACKEND_TEST_CRASHES) as BackendTestCrashId[];

/** The breadcrumb category every trigger leaves, on every client. */
const TEST_CRASH_CATEGORY = "test_crash";

/** Makes the server fail, so the server's own reporting path gets the cross-check the clients get. */
export function fireBackendTestCrash(id: BackendTestCrashId): void {
  addDiagnosticBreadcrumb(TEST_CRASH_CATEGORY, { crash: id });
  void api
    .POST({
      url: BACKEND_TEST_CRASHES[id],
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({}),
    })
    .catch((error: unknown) => {
      // The endpoint answers 500 on purpose: the report belongs to the server, so this is not a
      // failure to hand anyone. Its status is still worth a trail, and saying so keeps the catch from
      // being an empty one.
      addDiagnosticBreadcrumb(TEST_CRASH_CATEGORY, {
        crash: id,
        status: error instanceof ApiError ? error.status : 0,
      });
    });
}

export const FREEZE_MS = 6000;

export function testCrashMessage(id: TestCrashId): string {
  return `TEST-CRASH ${id}: ${TEST_CRASH_PLANS[id].what}`;
}

export function buildTestCrashError(id: TestCrashId): Error {
  const message = testCrashMessage(id);
  switch (TEST_CRASH_PLANS[id].kind) {
    case "TypeError":
      return new TypeError(message);
    case "RangeError":
      return new RangeError(message);
    case "ReferenceError":
      return new ReferenceError(message);
    case "SyntaxError":
      return new SyntaxError(message);
    case "URIError":
      return new URIError(message);
    case "EvalError":
      return new EvalError(message);
    case "InvalidStateError":
    case "NotFoundError":
    case "DataCloneError":
    case "QuotaExceededError":
      return new DOMException(message, TEST_CRASH_PLANS[id].kind);
    default:
      return new Error(message);
  }
}

function freezeMainThread(): void {
  const end = Date.now() + FREEZE_MS;
  while (Date.now() < end) {
    // Busy loop on purpose: this is the freeze being tested.
  }
}

/** Fires the crash for `id` on a path nothing in the app catches. */
export function fireTestCrash(id: TestCrashId): void {
  const plan = TEST_CRASH_PLANS[id];
  addDiagnosticBreadcrumb(TEST_CRASH_CATEGORY, { crash: id });
  if (plan.via === "freeze") {
    freezeMainThread();
    return;
  }
  const error = buildTestCrashError(id);
  switch (plan.via) {
    case "capture":
      captureUiException(error, TEST_CRASH_CATEGORY, { crash: id });
      return;
    case "microtask":
      queueMicrotask(() => {
        throw error;
      });
      return;
    case "rejection":
      void Promise.reject(error);
      return;
    case "render":
      // Handled by the component, which throws while rendering.
      throw error;
    default:
      window.setTimeout(() => {
        throw error;
      }, 0);
  }
}

// First task of any list: the row nearest the top of the document is the first one displayed.
const ROW_ATTRIBUTE = "data-test-crash-row";
export const TEST_CRASH_ROW_PROPS = { [ROW_ATTRIBUTE]: "" } as const;

function isFirstTaskRow(element: Element | null): boolean {
  const row = element?.closest(`[${ROW_ATTRIBUTE}]`) ?? null;
  return row !== null && document.querySelector(`[${ROW_ATTRIBUTE}]`) === row;
}

/** Rows have no open action on web; a plain click on the first row's body is "open". */
export function testCrashRowOpen(event: {
  target: EventTarget | null;
  currentTarget: Element;
}): void {
  const target = event.target as Element | null;
  if (target?.closest("button,a,input,[role=checkbox]")) return;
  if (isFirstTaskRow(event.currentTarget)) fireTestCrash("TC-TASK-OPEN");
}

/** Opening the edit form of the first row is "edit". */
export function useTestCrashRowEdit(
  rowRef: { current: Element | null },
  editOpen: boolean,
): void {
  useEffect(() => {
    if (editOpen && isFirstTaskRow(rowRef.current)) fireTestCrash("TC-TASK-EDIT");
  }, [editOpen, rowRef]);
}
