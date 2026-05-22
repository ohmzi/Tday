package com.ohmz.tday.compose.core.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemCredentialRecordsTest {
    @Test
    fun `login credential ignores saved server url records`() {
        val credential = SystemCredentialRecords.loginCredential(
            id = SystemCredentialRecords.SERVER_URL_CREDENTIAL_ID,
            password = "https://tday.example.com",
        )

        assertNull(credential)
    }

    @Test
    fun `login credential accepts normal password records`() {
        val credential = SystemCredentialRecords.loginCredential(
            id = " User@Example.com ",
            password = "Password!1",
        )

        assertEquals(
            SystemCredential(email = "User@Example.com", password = "Password!1"),
            credential,
        )
    }

    @Test
    fun `server url record ignores normal password records`() {
        val serverUrl = SystemCredentialRecords.serverUrl(
            id = "user@example.com",
            password = "Password!1",
        )

        assertNull(serverUrl)
    }

    @Test
    fun `server url record trims saved url`() {
        val serverUrl = SystemCredentialRecords.serverUrl(
            id = SystemCredentialRecords.SERVER_URL_CREDENTIAL_ID,
            password = " https://tday.example.com ",
        )

        assertEquals("https://tday.example.com", serverUrl)
    }
}
