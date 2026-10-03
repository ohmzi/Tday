package com.ohmz.tday.testcrash

// TEST-CRASH: temporary cross-check triggers for the server side. The whole feature is this file,
// the two `/api/admin/telemetry/test-*` routes, the web panel's backend buttons and the lines tagged
// TEST-CRASH elsewhere; `grep -rn "TEST-CRASH"` lists every touch point, and one revert removes them.
//
// Nothing here bypasses the gate, the scrubber or the DSN check: a report is only sent when the
// operator has turned server error reports on (web Settings -> Privacy) and `SENTRY_DSN` is set.

/** What an admin can ask this server to fail with: one entry per way it can report a failure. */
enum class BackendTestCrash(val id: String, val what: String) {
    /** An unhandled exception in a handler: `StatusPages` captures it and answers 500. */
    CRASH("TC-BACKEND-CRASH", "unhandled admin error"),

    /** A failure the handler catches and reports itself, answering 500 without throwing. */
    HANDLED("TC-BACKEND-ERROR", "handled admin error"),
    ;

    /** Identical on every client: `TEST-CRASH <ID>: <description>`. */
    val message: String get() = "TEST-CRASH $id: $what"
}
