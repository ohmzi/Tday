package com.ohmz.tday.compose.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.model.SecurityQuestion

/**
 * Question dropdown + answer field used by both the first-time security-questions gate
 * and the settings "change security questions" editor. `excludeIds` are the questions
 * picked in the sibling pickers, so each of the three stays distinct.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityQuestionPicker(
    label: String,
    questions: List<SecurityQuestion>,
    excludeIds: Set<Int>,
    selectedId: Int?,
    onSelected: (Int) -> Unit,
    answer: String,
    onAnswerChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectable = questions.filter { it.id == selectedId || it.id !in excludeIds }
    val selectedText = questions.firstOrNull { it.id == selectedId }?.text.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                value = selectedText,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                selectable.forEach { question ->
                    DropdownMenuItem(
                        text = { Text(question.text) },
                        onClick = {
                            onSelected(question.id)
                            expanded = false
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = answer,
            onValueChange = onAnswerChange,
            label = { Text(stringResource(R.string.security_questions_answer_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            shape = RoundedCornerShape(22.dp),
        )
    }
}
