package com.ohmz.tday.compose.feature.car

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.ohmz.tday.compose.R

@Composable
fun rememberCarTaskVoiceCreateLauncher(
    onVoiceTitle: (String) -> Unit,
    onVoiceUnavailable: (CarTaskMode) -> Unit,
): (CarTaskMode) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val voiceTitle = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (voiceTitle.isBlank()) {
            Toast.makeText(
                context,
                context.getString(R.string.car_voice_empty_result),
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            onVoiceTitle(voiceTitle)
        }
    }

    return { mode ->
        val prompt = context.getString(mode.voicePromptRes)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            onVoiceUnavailable(mode)
        } else {
            launcher.launch(intent)
        }
    }
}
