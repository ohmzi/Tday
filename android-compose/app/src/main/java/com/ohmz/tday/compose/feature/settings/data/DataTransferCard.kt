package com.ohmz.tday.compose.feature.settings.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.ui.LocalSnackbarManager
import com.ohmz.tday.compose.feature.settings.SettingsDivider
import com.ohmz.tday.compose.feature.settings.SettingsListRow
import com.ohmz.tday.compose.feature.settings.SettingsSectionCard
import com.ohmz.tday.compose.feature.settings.SettingsSectionTitle
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

    // Surface each export/import outcome once via the unified frosted toast.
    val snackbarManager = LocalSnackbarManager.current
    LaunchedEffect(state.message) {
        val message = state.message
        val text = when (message) {
            DataTransferMessage.ExportDone -> context.getString(R.string.settings_data_export_done)
            is DataTransferMessage.ImportDone ->
                context.getString(R.string.settings_data_import_done, message.count)
            is DataTransferMessage.Error ->
                message.detail ?: context.getString(R.string.settings_data_import_failed)
            null -> null
        }
        if (text != null) {
            if (message is DataTransferMessage.Error) {
                snackbarManager?.showError(text)
            } else {
                snackbarManager?.showSuccess(text)
            }
            viewModel.consumeMessage()
        }
    }

    SettingsSectionCard(modifier = modifier) {
        SettingsSectionTitle(
            title = context.getString(R.string.settings_data_title),
            helpTopicId = GuideTopicIds.EXPORT_YOUR_DATA,
        )

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

        // Icon rows, not filled buttons — these two act in place, exactly like the rows in
        // every other Settings card, and the other two platforms draw them the same way
        // (iOS: showChevron false, LucideDownload / LucideUpload).
        SettingsListRow(
            title = context.getString(R.string.settings_data_download),
            value = null,
            onClick = {
                if (!state.busy) exportLauncher.launch("tday-export-${LocalDate.now()}.json")
            },
            icon = R.drawable.ic_lucide_download,
            showChevron = false,
        )

        SettingsDivider()

        // No local-mode branch: the whole card is Server Mode only now, so the
        // "sign in to a server to import" line it used to show in its place could
        // never be reached.
        SettingsListRow(
            title = context.getString(R.string.settings_data_import),
            value = null,
            onClick = {
                if (!state.busy) importLauncher.launch(arrayOf("application/json"))
            },
            icon = R.drawable.ic_lucide_upload,
            showChevron = false,
        )
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
