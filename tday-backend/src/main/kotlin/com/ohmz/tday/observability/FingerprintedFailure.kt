package com.ohmz.tday.observability

/**
 * A failure that asks Sentry for an identity of its own instead of the one its stack gives it.
 *
 * Sentry groups events by exception type and stack, so two unrelated failures raised on the same
 * route — or the same failure raised from two different screens — collapse into a single issue, and
 * the second one is never visible on its own. A failure that implements this declares the keys it
 * wants to be grouped by instead, which keeps its issue separable and its title searchable.
 *
 * Keys must stay structural: an operation name, a failure kind, a route template. Never an id, a
 * path, a URL or any user text.
 */
interface FingerprintedFailure {
    val issueFingerprint: List<String>
}
