package com.ohmz.tday.compose.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.ohmz.tday.compose.core.text.flattenNotesToPlainText
import com.ohmz.tday.compose.core.text.isRichNotes
import com.ohmz.tday.compose.ui.theme.TdayTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// On-device verification that NotesField's real Android ClipData handling —
// the one part of this feature a plain JVM unit test can't exercise — does
// what RichNotesCompose.kt/NotesField.kt assume it does.
class NotesFieldTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val fieldTag = "notesField"

    private fun setClip(html: String, plainText: String) {
        val context = composeTestRule.activity.applicationContext
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newHtmlText("clip", plainText, html))
    }

    private fun setContentWithField() = mutableStateOf("").also { state ->
        composeTestRule.setContent {
            TdayTheme {
                NotesField(
                    value = state.value,
                    onValueChange = { state.value = it },
                    placeholder = "Notes",
                    modifier = Modifier.testTag(fieldTag),
                )
            }
        }
    }

    @Test
    fun plainTypingProducesNoMarkerAndNoClearButton() {
        val state = setContentWithField()

        composeTestRule.onNodeWithTag(fieldTag).performTextInput("Hello world")
        composeTestRule.waitForIdle()

        assertEquals("Hello world", state.value)
        assertFalse(isRichNotes(state.value))
        composeTestRule.onNodeWithTag("clearFormattingButton").assertDoesNotExist()
    }

    @Test
    fun pastingFormattedHtmlRetainsMarksStripsFontSizeAndShowsClearButton() {
        val state = setContentWithField()
        val plainText = "Grocery list for today: bread and eggs"
        setClip(
            html = """
                <div style="font-size:32px;color:red;font-family:Comic Sans MS">
                  <p>Grocery <b>list</b> for <i>today</i>: <u>bread</u> and <s>eggs</s></p>
                </div>
            """.trimIndent(),
            plainText = plainText,
        )

        // Sanity-check the clip itself before blaming the field's diffing.
        val clipCheck = composeTestRule.activity.applicationContext
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipItem = clipCheck.primaryClip?.getItemAt(0)
        android.util.Log.e("NotesFieldTest", "clip coerceToText=${clipItem?.coerceToText(null)} htmlText=${clipItem?.htmlText}")

        // performTextReplacement delivers the whole string as one atomic
        // onValueChange, matching how a real paste's single commitText()
        // call behaves (performTextInput instead simulates chunked/IME-style
        // typing, which enrichPastedRun() correctly does NOT treat as paste).
        composeTestRule.onNodeWithTag(fieldTag).performTextReplacement(plainText)
        composeTestRule.waitForIdle()

        val encoded = state.value
        assertTrue("expected marker+HTML encoding, got: $encoded", isRichNotes(encoded))
        assertFalse("font-size must never survive paste", encoded.contains("font-size"))
        assertFalse("color must never survive paste", encoded.contains("color"))
        assertFalse("font-family must never survive paste", encoded.contains("Comic Sans"))
        assertTrue("bold mark should survive", encoded.contains("<b>list</b>"))
        assertTrue("underline mark should survive", encoded.contains("<u>bread</u>"))
        assertTrue("strike mark should survive", encoded.contains("<s>eggs</s>"))
        assertEquals(plainText, flattenNotesToPlainText(encoded))

        composeTestRule.onNodeWithTag("clearFormattingButton").assertExists()
        composeTestRule.onNodeWithTag("clearFormattingButton").performClick()
        composeTestRule.waitForIdle()

        assertFalse(isRichNotes(state.value))
        assertEquals(plainText, state.value)
        composeTestRule.onNodeWithTag("clearFormattingButton").assertDoesNotExist()
    }
}
