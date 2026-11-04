package com.ohmz.tday.shared.floater

import com.ohmz.tday.shared.model.FloaterListDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloaterMainScreenTest {
    @Test
    fun noListIsShown() {
        assertTrue(FloaterMainScreen.isShown(null, setOf("a")))
    }

    @Test
    fun unknownListIsShown() {
        assertTrue(FloaterMainScreen.isShown("b", setOf("a")))
    }

    @Test
    fun hiddenListIsNotShown() {
        assertFalse(FloaterMainScreen.isShown("a", setOf("a")))
    }

    @Test
    fun dtoWithoutFieldDecodesAsShown() {
        val dto = Json { ignoreUnknownKeys = true }
            .decodeFromString<FloaterListDto>("""{"id":"1","name":"x"}""")
        assertTrue(dto.showOnMainScreen)
    }
}
