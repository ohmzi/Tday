package com.ohmz.tday.compose.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

// Standalone reset screen (reached from Settings). Wraps the same ForgotPasswordPanel the
// login dialog embeds, in its own card so it feels at home on a full screen.
@Composable
fun ForgotPasswordScreen(
    onBackToLogin: () -> Unit,
    onResetComplete: () -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val colorScheme = MaterialTheme.colorScheme

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp),
            shape = RoundedCornerShape(34.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.background),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.08f)),
        ) {
            ForgotPasswordPanel(
                initialUsername = "",
                onBackToLogin = onBackToLogin,
                onResetComplete = { onResetComplete() },
                modifier = Modifier.padding(20.dp),
                viewModel = viewModel,
            )
        }
    }
}
