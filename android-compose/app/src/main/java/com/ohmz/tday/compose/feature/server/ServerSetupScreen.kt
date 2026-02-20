package com.ohmz.tday.compose.feature.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ServerSetupScreen(
    errorMessage: String?,
    onSave: (String) -> Unit,
) {
    var serverUrl by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050507)),
    ) {
        AlertDialog(
            onDismissRequest = {},
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Connect to server",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter your Tday server URL (example: tday.ohmz.cloud)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("Server URL") },
                        singleLine = true,
                    )

                    if (!errorMessage.isNullOrBlank()) {
                        Text(
                            modifier = Modifier.padding(top = 10.dp),
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onSave(serverUrl) },
                    enabled = serverUrl.isNotBlank(),
                ) {
                    Text("Save")
                }
            },
        )
    }
}
