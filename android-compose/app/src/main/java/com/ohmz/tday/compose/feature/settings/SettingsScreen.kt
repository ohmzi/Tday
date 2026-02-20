package com.ohmz.tday.compose.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.core.model.SessionUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    user: SessionUser?,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    Scaffold(
        containerColor = Color(0xFF050507),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF171A22)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = user?.name ?: "Unknown user",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = user?.email ?: "",
                        color = Color(0xFF9AA2B6),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        modifier = Modifier.padding(top = 10.dp),
                        text = "Role: ${user?.role ?: "USER"}",
                        color = Color(0xFF9AA2B6),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            TextButton(onClick = onLogout) {
                Icon(Icons.Rounded.Logout, contentDescription = null)
                Text("Sign out")
            }
        }
    }
}
