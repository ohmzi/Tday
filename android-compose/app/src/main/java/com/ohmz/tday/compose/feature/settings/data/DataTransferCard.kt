package com.ohmz.tday.compose.feature.settings.data

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.feature.guide.GuideHelpLink
import com.ohmz.tday.shared.guide.GuideTopicIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * "Your data" trust card in Settings: shows what lives in the account, exports
 * it to a JSON file via SAF, and imports one back (Server Mode) after an
 * additive-merge preview. Self-contained (its own Hilt VM + SAF launchers) so
 * it drops straight into the Settings column.
 */
@Composable
fun DataTransferCard(modifier: Modifier = Modifier) {
    val viewModel: DataTransferViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            viewModel.export { json ->
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.encodeToByteArray()) }
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                }
                if (text != null) viewModel.preview(text)
            }
        }
    }

    // Surface each export/import outcome once as a toast.
    LaunchedEffect(state.message) {
        val text = when (val message = state.message) {
            DataTransferMessage.ExportDone -> context.getString(R.string.settings_data_export_done)
            is DataTransferMessage.ImportDone ->
                context.getString(R.string.settings_data_import_done, message.count)
            is DataTransferMessage.Error ->
                message.detail ?: context.getString(R.string.settings_data_import_failed)
            null -> null
        }
        if (text != null) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = context.getString(R.string.settings_data_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                GuideHelpLink(GuideTopicIds.EXPORT_YOUR_DATA)
            }

            Text(
                text = context.getString(
                    R.string.settings_data_summary,
                    state.taskCount,
                    state.listCount,
                    state.completedCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { exportLauncher.launch("tday-export-${LocalDate.now()}.json") },
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(context.getString(R.string.settings_data_download), fontWeight = FontWeight.ExtraBold)
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    enabled = !state.busy && !state.isLocalMode,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(context.getString(R.string.settings_data_import), fontWeight = FontWeight.ExtraBold)
                }
            }

            if (state.isLocalMode) {
                Text(
                    text = context.getString(R.string.settings_data_import_local_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }

    val preview = state.preview
    if (preview != null) {
        val added = preview.total()
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text(context.getString(R.string.settings_data_confirm_title), fontWeight = FontWeight.ExtraBold) },
            text = { Text(context.getString(R.string.settings_data_confirm_body, added)) },
            confirmButton = {
                Button(onClick = viewModel::confirmImport) {
                    Text(context.getString(R.string.settings_data_confirm_import), fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelImport) {
                    Text(context.getString(R.string.settings_data_confirm_cancel))
                }
            },
        )
    }
}
