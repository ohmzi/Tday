package com.ohmz.tday.compose.ui.component

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.text.RichNotesListKind
import com.ohmz.tday.compose.core.text.RichNotesMark
import com.ohmz.tday.compose.core.text.applyingMarks
import com.ohmz.tday.compose.core.text.continuingListOnNewline
import com.ohmz.tday.compose.core.text.decodeNotesToAnnotatedString
import com.ohmz.tday.compose.core.text.encodeAnnotatedNotes
import com.ohmz.tday.compose.core.text.isListActive
import com.ohmz.tday.compose.core.text.isMarkActive
import com.ohmz.tday.compose.core.text.isRichNotes
import com.ohmz.tday.compose.core.text.sanitizeHtml
import com.ohmz.tday.compose.core.text.stripToPlainText
import com.ohmz.tday.compose.core.text.togglingList
import com.ohmz.tday.compose.core.text.togglingMark
import com.ohmz.tday.compose.core.text.typedRunRange
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

// Multi-line rich-text notes field: retains bold/italic/underline/strikethrough
// pasted in from elsewhere (font size/color/family are always discarded — see
// RichNotes.kt) and downgrades pasted lists to plain "• "/"1. "-prefixed
// lines. Focusing the field also shows a format bar with the same six marks/
// lists so they can be applied manually, not just via paste — as a persistent
// row rather than a selection popup, which is both what Android editors
// (Docs, Keep) do and what web/iOS now match. A mark applies to the selection
// when there is one, and otherwise arms for whatever gets typed next (see
// pendingMarks). A "clear formatting" button appears only once real
// formatting is present.
@Composable
fun NotesField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    var fieldValue by remember { mutableStateOf(TextFieldValue(decodeNotesToAnnotatedString(value))) }
    var lastEmitted by remember { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }
    // Marks armed at a collapsed caret, waiting to be stamped onto whatever
    // gets typed next. Compose has no typingAttributes equivalent, so this is
    // the "tap Bold, then type" state iOS gets from UIKit for free.
    var pendingMarks by remember { mutableStateOf(emptySet<RichNotesMark>()) }

    // Sync content set from outside (switching which task is being edited) —
    // guarded so it never fires as an echo of this field's own onValueChange.
    if (value != lastEmitted) {
        lastEmitted = value
        fieldValue = TextFieldValue(decodeNotesToAnnotatedString(value))
        pendingMarks = emptySet()
    }

    // Ground truth is the encoded string's marker, not the live spans —
    // spanStyles alone would miss a list-only note (bullets/numbers carry
    // only a string annotation, never a SpanStyle), leaving no way back to
    // plain text after using the format bar's list buttons.
    val hasFormatting = isRichNotes(lastEmitted)

    val onValueChangeState = rememberUpdatedState(onValueChange)

    fun applyFormatEdit(newAnnotated: AnnotatedString, newSelection: TextRange) {
        val updated = TextFieldValue(newAnnotated, newSelection)
        fieldValue = updated
        val encoded = encodeAnnotatedNotes(updated.annotatedString)
        lastEmitted = encoded
        onValueChangeState.value(encoded)
    }

    BasicTextField(
        value = fieldValue,
        onValueChange = { newValue ->
            val previous = fieldValue
            val enriched = enrichPastedRun(clipboard, previous, newValue)
            val typedRange = typedRunRange(previous.text, previous.selection, enriched.text)

            // Enter on a list line continues (or ends) the list rather than
            // dropping an untagged, unprefixed line that silently breaks it.
            val newlineTyped = typedRange != null &&
                enriched.text.substring(typedRange.min, typedRange.max) == "\n"
            val continued = if (newlineTyped) {
                continuingListOnNewline(previous.annotatedString, previous.selection.start)
            } else {
                null
            }
            if (continued != null) {
                val (updatedText, updatedSelection) = continued
                val updated = TextFieldValue(updatedText, updatedSelection)
                fieldValue = updated
                val encodedList = encodeAnnotatedNotes(updated.annotatedString)
                lastEmitted = encodedList
                onValueChangeState.value(encodedList)
                return@BasicTextField
            }

            val stamped = if (pendingMarks.isNotEmpty() && typedRange != null) {
                TextFieldValue(
                    applyingMarks(pendingMarks, enriched.annotatedString, typedRange),
                    enriched.selection,
                    enriched.composition,
                )
            } else {
                enriched
            }
            // A caret that moved with nothing typed means the user tapped or
            // arrowed somewhere else; armed marks belong to the spot they
            // were armed at, not wherever the caret went next.
            if (typedRange == null && stamped.text == previous.text && stamped.selection != previous.selection) {
                pendingMarks = emptySet()
            }
            fieldValue = stamped
            val encoded = encodeAnnotatedNotes(stamped.annotatedString)
            lastEmitted = encoded
            onValueChangeState.value(encoded)
        },
        keyboardOptions = KeyboardOptions.Default,
        textStyle = MaterialTheme.typography.titleMedium.copy(
            color = colorScheme.onSurface,
            fontWeight = FontWeight.ExtraBold,
        ),
        modifier = modifier
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .onFocusChanged {
                isFocused = it.isFocused
                if (!it.isFocused) pendingMarks = emptySet()
            },
        decorationBox = { innerTextField ->
            if (fieldValue.text.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            innerTextField()
        },
    )

    if (hasFormatting) {
        IconButton(
            onClick = {
                val plain = TextFieldValue(stripToPlainText(fieldValue.annotatedString))
                fieldValue = plain
                pendingMarks = emptySet()
                val encoded = encodeAnnotatedNotes(plain.annotatedString)
                lastEmitted = encoded
                onValueChangeState.value(encoded)
            },
            modifier = Modifier.size(32.dp).testTag("clearFormattingButton"),
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_eraser),
                contentDescription = stringResource(R.string.create_task_clear_formatting),
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
    }

    if (isFocused) {
        val selection = fieldValue.selection
        val annotated = fieldValue.annotatedString

        // With a real selection a mark button restyles it; with a collapsed
        // caret it arms the mark for what gets typed next instead of no-oping.
        fun markActive(mark: RichNotesMark): Boolean =
            if (selection.collapsed) mark in pendingMarks else isMarkActive(mark, annotated, selection)

        fun toggleMark(mark: RichNotesMark) {
            if (selection.collapsed) {
                pendingMarks = if (mark in pendingMarks) pendingMarks - mark else pendingMarks + mark
            } else {
                applyFormatEdit(togglingMark(mark, annotated, selection), selection)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("notesFormatBar")
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            FormatBarButton(
                iconRes = R.drawable.ic_lucide_bold,
                contentDescription = stringResource(R.string.create_task_format_bold),
                active = markActive(RichNotesMark.BOLD),
                testTag = "formatBold",
            ) {
                toggleMark(RichNotesMark.BOLD)
            }
            FormatBarButton(
                iconRes = R.drawable.ic_lucide_italic,
                contentDescription = stringResource(R.string.create_task_format_italic),
                active = markActive(RichNotesMark.ITALIC),
                testTag = "formatItalic",
            ) {
                toggleMark(RichNotesMark.ITALIC)
            }
            FormatBarButton(
                iconRes = R.drawable.ic_lucide_underline,
                contentDescription = stringResource(R.string.create_task_format_underline),
                active = markActive(RichNotesMark.UNDERLINE),
                testTag = "formatUnderline",
            ) {
                toggleMark(RichNotesMark.UNDERLINE)
            }
            FormatBarButton(
                iconRes = R.drawable.ic_lucide_strikethrough,
                contentDescription = stringResource(R.string.create_task_format_strikethrough),
                active = markActive(RichNotesMark.STRIKETHROUGH),
                testTag = "formatStrikethrough",
            ) {
                toggleMark(RichNotesMark.STRIKETHROUGH)
            }
            FormatBarButton(
                iconRes = R.drawable.ic_lucide_list,
                contentDescription = stringResource(R.string.create_task_format_bulleted_list),
                active = isListActive(RichNotesListKind.BULLET, annotated, selection),
                testTag = "formatBulletedList",
            ) {
                val (updated, newSelection) = togglingList(RichNotesListKind.BULLET, annotated, selection)
                applyFormatEdit(updated, newSelection)
            }
            FormatBarButton(
                iconRes = R.drawable.ic_lucide_list_ordered,
                contentDescription = stringResource(R.string.create_task_format_numbered_list),
                active = isListActive(RichNotesListKind.ORDERED, annotated, selection),
                testTag = "formatNumberedList",
            ) {
                val (updated, newSelection) = togglingList(RichNotesListKind.ORDERED, annotated, selection)
                applyFormatEdit(updated, newSelection)
            }
        }
    }
}

@Composable
private fun FormatBarButton(
    iconRes: Int,
    contentDescription: String,
    active: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val tint = if (active) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp).testTag(testTag),
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

// Compose's default paste only carries plain text (ClipboardManager.getText()
// coerces everything to plain text). To retain formatting, this detects a
// paste by diffing the old/new value for a multi-character insertion whose
// text matches the system clipboard's current plain-text item, then — if
// that clip also carries an HTML sibling — re-applies it as a styled
// AnnotatedString run in place of the plain text Compose already inserted.
private fun enrichPastedRun(
    clipboard: ClipboardManager,
    old: TextFieldValue,
    new: TextFieldValue,
): TextFieldValue {
    val oldText = old.text
    val newText = new.text

    // The platform text-input connection periodically echoes the field's
    // plain-text buffer back through onValueChange to resync selection —
    // e.g. right after this function styles a paste — and that echo has no
    // spans of its own. Without this guard, that echo silently strips the
    // formatting this function just applied one call earlier. When the text
    // itself hasn't changed, keep the existing (possibly styled) content and
    // just take the new cursor/selection/composition state.
    if (newText == oldText) {
        return TextFieldValue(old.annotatedString, new.selection, new.composition)
    }

    if (newText.length <= oldText.length) return new

    // Find the inserted range via the common prefix/suffix between old and new.
    var prefix = 0
    while (prefix < oldText.length && prefix < newText.length && oldText[prefix] == newText[prefix]) prefix++
    var suffix = 0
    while (
        suffix < oldText.length - prefix &&
        suffix < newText.length - prefix &&
        oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
    ) suffix++
    val insertStart = prefix
    val insertEnd = newText.length - suffix
    val insertedText = newText.substring(insertStart, insertEnd)
    if (insertedText.length <= 1) return new

    val clip = clipboard.primaryClip ?: return new
    if (clip.itemCount == 0) return new
    val item = clip.getItemAt(0)
    val clipPlainText = item.coerceToText(null)?.toString() ?: return new
    if (clipPlainText != insertedText) return new
    val html = item.htmlText ?: return new
    val sanitized = sanitizeHtml(html)
    val pastedAnnotated = buildAnnotatedString { appendInlineRun(Jsoup.parseBodyFragment(sanitized).body()) }
    if (pastedAnnotated.text.isEmpty()) return new

    val merged = buildAnnotatedString {
        append(new.annotatedString.subSequence(0, insertStart))
        append(pastedAnnotated)
        append(new.annotatedString.subSequence(insertEnd, new.annotatedString.length))
    }
    val cursor = insertStart + pastedAnnotated.length
    return TextFieldValue(merged, selection = TextRange(cursor))
}

private val PASTE_BOLD = SpanStyle(fontWeight = FontWeight.Black)
private val PASTE_ITALIC = SpanStyle(fontStyle = FontStyle.Italic)
private val PASTE_UNDERLINE = SpanStyle(textDecoration = TextDecoration.Underline)
private val PASTE_STRIKE = SpanStyle(textDecoration = TextDecoration.LineThrough)

// Same shape as RichNotesCompose's block walker, but flattened for splicing
// a pasted fragment into the middle of an existing line — every block
// (paragraph/list item) becomes one visual line joined by "\n", with list
// items downgraded to plain bullet-prefixed text (see RichNotesCompose.kt).
private fun AnnotatedString.Builder.appendInlineRun(container: Node) {
    var firstBlock = true
    fun appendInline(node: Node) {
        for (child in node.childNodes()) {
            when (child) {
                is TextNode -> append(child.text())
                is Element -> when (child.tagName().lowercase()) {
                    "br" -> append("\n")
                    "b", "strong" -> withStyleCompat(PASTE_BOLD) { appendInline(child) }
                    "i", "em" -> withStyleCompat(PASTE_ITALIC) { appendInline(child) }
                    "u" -> withStyleCompat(PASTE_UNDERLINE) { appendInline(child) }
                    "s", "strike" -> withStyleCompat(PASTE_STRIKE) { appendInline(child) }
                    else -> appendInline(child)
                }
            }
        }
    }
    for (child in container.childNodes()) {
        if (child !is Element) continue
        when (child.tagName().lowercase()) {
            "p" -> {
                if (!firstBlock) append("\n")
                appendInline(child)
                firstBlock = false
            }
            "ul", "ol" -> {
                val ordered = child.tagName().equals("ol", ignoreCase = true)
                var counter = 0
                for (li in child.children()) {
                    if (!li.tagName().equals("li", ignoreCase = true)) continue
                    counter += 1
                    if (!firstBlock) append("\n")
                    append(if (ordered) "$counter. " else "• ")
                    appendInline(li)
                    firstBlock = false
                }
            }
        }
    }
}

private inline fun AnnotatedString.Builder.withStyleCompat(
    style: SpanStyle,
    block: AnnotatedString.Builder.() -> Unit,
) {
    pushStyle(style)
    block()
    pop()
}
