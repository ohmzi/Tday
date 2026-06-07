package com.ohmz.tday.compose.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.model.SecurityAnswerInput
import com.ohmz.tday.compose.core.model.SecurityQuestion

/**
 * Blocking prompt shown to accounts created before security questions existed.
 * They must choose two distinct questions before continuing so self-service
 * password reset works for them. The backend clears the flag on success.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetSecurityQuestionsGate(
    onFetchQuestions: suspend () -> List<SecurityQuestion>,
    onSubmit: (List<SecurityAnswerInput>, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    var questions by remember { mutableStateOf<List<SecurityQuestion>>(emptyList()) }
    var questionId1 by rememberSaveable { mutableStateOf<Int?>(null) }
    var questionId2 by rememberSaveable { mutableStateOf<Int?>(null) }
    var questionId3 by rememberSaveable { mutableStateOf<Int?>(null) }
    var answer1 by rememberSaveable { mutableStateOf("") }
    var answer2 by rememberSaveable { mutableStateOf("") }
    var answer3 by rememberSaveable { mutableStateOf("") }
    var saving by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val distinctError = stringResource(R.string.security_questions_distinct_required)
    val answersError = stringResource(R.string.security_questions_answers_required)

    LaunchedEffect(Unit) {
        if (questions.isEmpty()) {
            val fetched = onFetchQuestions()
            questions = fetched
            if (questionId1 == null) questionId1 = fetched.getOrNull(0)?.id
            if (questionId2 == null) questionId2 = fetched.getOrNull(1)?.id
            if (questionId3 == null) questionId3 = fetched.getOrNull(2)?.id
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = stringResource(R.string.security_questions_gate_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.security_questions_gate_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface.copy(alpha = 0.7f),
                )

                GateQuestionPicker(
                    label = stringResource(R.string.security_questions_question_1),
                    questions = questions,
                    excludeIds = setOfNotNull(questionId2, questionId3),
                    selectedId = questionId1,
                    onSelected = {
                        questionId1 = it
                        errorMessage = null
                    },
                    answer = answer1,
                    onAnswerChange = {
                        answer1 = it
                        errorMessage = null
                    },
                )
                GateQuestionPicker(
                    label = stringResource(R.string.security_questions_question_2),
                    questions = questions,
                    excludeIds = setOfNotNull(questionId1, questionId3),
                    selectedId = questionId2,
                    onSelected = {
                        questionId2 = it
                        errorMessage = null
                    },
                    answer = answer2,
                    onAnswerChange = {
                        answer2 = it
                        errorMessage = null
                    },
                )
                GateQuestionPicker(
                    label = stringResource(R.string.security_questions_question_3),
                    questions = questions,
                    excludeIds = setOfNotNull(questionId1, questionId2),
                    selectedId = questionId3,
                    onSelected = {
                        questionId3 = it
                        errorMessage = null
                    },
                    answer = answer3,
                    onAnswerChange = {
                        answer3 = it
                        errorMessage = null
                    },
                )

                errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.error,
                    )
                }

                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = questionId1 != null &&
                            questionId2 != null &&
                            questionId3 != null &&
                            setOfNotNull(questionId1, questionId2, questionId3).size == 3 &&
                            answer1.isNotBlank() &&
                            answer2.isNotBlank() &&
                            answer3.isNotBlank() &&
                            !saving,
                    onClick = {
                        val id1 = questionId1
                        val id2 = questionId2
                        val id3 = questionId3
                        when {
                            id1 == null || id2 == null || id3 == null ||
                                    setOf(id1, id2, id3).size != 3 -> errorMessage = distinctError

                            answer1.isBlank() || answer2.isBlank() || answer3.isBlank() -> errorMessage =
                                answersError
                            else -> {
                                errorMessage = null
                                saving = true
                                onSubmit(
                                    listOf(
                                        SecurityAnswerInput(
                                            questionId = id1,
                                            answer = answer1.trim()
                                        ),
                                        SecurityAnswerInput(
                                            questionId = id2,
                                            answer = answer2.trim()
                                        ),
                                        SecurityAnswerInput(
                                            questionId = id3,
                                            answer = answer3.trim()
                                        ),
                                    ),
                                    { saving = false },
                                    { message ->
                                        saving = false
                                        errorMessage = message
                                    },
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary,
                    ),
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.security_questions_save))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GateQuestionPicker(
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
