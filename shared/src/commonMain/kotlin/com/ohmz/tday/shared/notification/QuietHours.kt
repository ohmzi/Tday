package com.ohmz.tday.shared.notification

/**
 * Pure quiet-hours window math shared by Android and iOS. A window is a start and end
 * minute-of-day (0..1439). When start == end the window is empty (feature off). A window
 * whose end is <= start spans midnight (e.g. 22:00 → 07:00). Kept platform-agnostic and
 * unit-tested so both reminder schedulers shift identically.
 */
object QuietHours {
    const val MINUTES_PER_DAY = 24 * 60

    /** True when [minuteOfDay] falls inside the [startMinute, endMinute) quiet window. */
    fun contains(minuteOfDay: Int, startMinute: Int, endMinute: Int): Boolean {
        if (startMinute == endMinute) return false
        val t = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return if (startMinute < endMinute) {
            t in startMinute until endMinute
        } else {
            // Spans midnight: inside if at/after start OR before end.
            t >= startMinute || t < endMinute
        }
    }

    /**
     * Given a reminder's [minuteOfDay], returns how many minutes to push it forward so it
     * lands at the window end, or 0 when it isn't inside the window. Never negative.
     */
    fun minutesUntilWindowEnd(minuteOfDay: Int, startMinute: Int, endMinute: Int): Int {
        if (!contains(minuteOfDay, startMinute, endMinute)) return 0
        val t = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val delta = (endMinute - t + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return if (delta == 0) MINUTES_PER_DAY else delta
    }
}
