package com.ohmz.tday.compose

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TEMPORARY. Removed in the very next commit.
 *
 * A green pipeline only proves the workflow ran, not that it gates. This test exists for
 * exactly one CI run, to show that a failing Android unit test turns the new `Android`
 * workflow red and uploads its report — and is then deleted.
 */
class CiGateProbeTest {

    @Test
    fun `should fail on purpose so the new CI gate can be seen to bite`() {
        assertEquals("temporary CI gate probe — deleted in the next commit", 1, 2)
    }
}
