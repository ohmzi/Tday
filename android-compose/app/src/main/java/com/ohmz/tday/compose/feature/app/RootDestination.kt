package com.ohmz.tday.compose.feature.app

/**
 * Whether this process has yet learned if a workspace is available.
 *
 * `AppUiState.loading` cannot carry this. It is re-armed by every re-bootstrap — after a sign-in,
 * after admin approval, after a version recheck — and the UI has to keep the wizard up through
 * those. This value moves from [UNKNOWN] to [RESOLVED] exactly once per process and never back:
 * signing out and session expiry leave it resolved, because the answer is then known (no
 * workspace), it is not unknown again.
 */
enum class SessionResolution {
    /** Bootstrap has not answered yet: a signed-in user and a signed-out one look identical. */
    UNKNOWN,

    /** Bootstrap has answered at least once; `AppUiState.isWorkspaceAvailable` now means something. */
    RESOLVED,
}

/**
 * Where the root of the app belongs — a pure function of `AppUiState`, see
 * `AppUiState.rootDestination`. Kept as a value rather than a boolean so that "not yet known"
 * is a destination of its own and can never be mistaken for "signed out".
 */
enum class RootDestination {
    /** The session is still [SessionResolution.UNKNOWN]: hold the branded splash, build no nav graph. */
    SPLASH,

    /** A workspace — a server session or Local Mode — is available: the main app. */
    WORKSPACE,

    /** Resolved, and there is no workspace: the onboarding wizard (server setup / sign-in). */
    ONBOARDING,
}
