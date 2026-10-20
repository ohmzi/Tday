package com.ohmz.tday.compose.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two halves of the hero mark's clock that reading the header cannot settle.
 *
 * The mark used to sample the hour inside a keyless `remember`, so a session opened in the
 * afternoon kept the sun up all evening. That defect is fixed, but nothing held the fix: the
 * band lived inside a `private` predicate that read `Calendar.getInstance()`, which is a
 * clock no test can move, and the poll period was a `private const` no other client could be
 * compared against. Both are `internal` now, and these are what that visibility is for.
 */
class RootFeedHeroMarkClockTest {

    @Test
    fun `the sun comes up at six and is gone by six in the evening`() {
        // Both edges, from both sides. A band written `in 6..17` and a band written
        // `in 6..18` differ by exactly one hour at one end, and every sample that is not a
        // boundary passes under either.
        assertFalse("05:00 is night", isDaytimeHour(5))
        assertTrue("06:00 is the first daytime hour", isDaytimeHour(6))
        assertTrue("17:00 is the last daytime hour", isDaytimeHour(17))
        assertFalse("18:00 is night", isDaytimeHour(18))
    }

    @Test
    fun `midnight and midday are not the same glyph`() {
        // The two hours a reader would picture when they picture the sun and the moon. If
        // these ever agree, the predicate has stopped being a predicate.
        assertFalse("midnight draws the moon", isDaytimeHour(0))
        assertTrue("midday draws the sun", isDaytimeHour(12))
    }

    @Test
    fun `the mark is re-read on the same minute all three clients use`() {
        // The parity assertion, and the reason MARK_CLOCK_TICK_MS is internal at all. The
        // same glyph is driven off three separate clocks:
        //   tday-web/src/components/app/RootFeedHeroHeader.tsx:166 — MARK_CLOCK_TICK_MS
        //   ios-swiftUI/Tday/Core/UI/RootFeedHeroHeader.swift:349  — TimelineView(.periodic(from: .now, by: 60))
        // Nothing links the three, so a client retimed on its own drifts silently: the sun
        // would turn over minutes apart on two phones sitting on the same table. Pinned as a
        // literal rather than derived from anything, because the value is the agreement.
        assertEquals(60_000L, MARK_CLOCK_TICK_MS)
    }
}
