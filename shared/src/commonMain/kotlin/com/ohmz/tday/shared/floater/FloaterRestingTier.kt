package com.ohmz.tday.shared.floater

/**
 * How long an "Anytime" floater has sat untouched. Derived purely from the cached
 * `updatedAtEpochMs` (the last-write-wins sync clock) — this is READ-ONLY input; the
 * aging logic must never write that field back. Shared so web/Android/iOS fade and
 * group floaters identically.
 */
enum class FloaterRestingTier {
    /** Fresh — shown normally. */
    ACTIVE,

    /** Untouched a while (>= 30d) — rendered desaturated/dimmed. */
    FADING,

    /** Long dormant (>= 90d) — collapsed into a "Resting" group at the bottom. */
    RESTING,
}

object FloaterResting {
    const val FADING_DAYS = 30L
    const val RESTING_DAYS = 90L
    private const val MS_PER_DAY = 86_400_000L

    /** Tier for a floater last updated at [updatedAtEpochMs], evaluated at [nowEpochMs]. */
    fun tierFor(updatedAtEpochMs: Long?, nowEpochMs: Long): FloaterRestingTier {
        if (updatedAtEpochMs == null || updatedAtEpochMs <= 0) return FloaterRestingTier.ACTIVE
        val ageDays = (nowEpochMs - updatedAtEpochMs) / MS_PER_DAY
        return when {
            ageDays >= RESTING_DAYS -> FloaterRestingTier.RESTING
            ageDays >= FADING_DAYS -> FloaterRestingTier.FADING
            else -> FloaterRestingTier.ACTIVE
        }
    }
}
