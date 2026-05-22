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

}
