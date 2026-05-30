package com.ohmz.tday.compose.core.data.server

import com.ohmz.tday.compose.core.security.ProbeCompatibilityPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompatibilityTest {
    @Test
    fun `older app requires app update`() {
        val result = checkVersionCompatibility(
            ProbeCompatibilityPayload(
                appVersion = "999.0.0",
                updateRequired = true,
                compatibilityMode = "exact",
            ),
        )

        assertEquals(VersionCheckResult.AppUpdateRequired("999.0.0"), result)
    }

    @Test
    fun `newer app requires server update`() {
        val result = checkVersionCompatibility(
            ProbeCompatibilityPayload(
                appVersion = "0.0.1",
                updateRequired = true,
                compatibilityMode = "exact",
            ),
        )

        assertEquals(VersionCheckResult.ServerUpdateRequired("0.0.1"), result)
    }

    @Test
    fun `disabled or non exact compatibility does not block`() {
        assertTrue(
            checkVersionCompatibility(
                ProbeCompatibilityPayload(
                    appVersion = "999.0.0",
                    updateRequired = false,
                    compatibilityMode = "exact",
                ),
            ) is VersionCheckResult.Compatible,
        )
        assertTrue(
            checkVersionCompatibility(
                ProbeCompatibilityPayload(
                    appVersion = "999.0.0",
                    updateRequired = true,
                    compatibilityMode = "minimum",
                ),
            ) is VersionCheckResult.Compatible,
        )
    }
}
