package com.ohmz.tday.compose.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    uiState: AuthUiState,
    onLogin: (email: String, password: String) -> Unit,
    onNavigateRegister: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uiState.savedEmail, uiState.savedPassword) {
        if (email.isBlank() && uiState.savedEmail.isNotBlank()) {
            email = uiState.savedEmail
        }
        if (password.isBlank() && uiState.savedPassword.isNotBlank()) {
            password = uiState.savedPassword
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF0A0C13),
                        Color(0xFF151929),
                    ),
                ),
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1E2D)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Tday",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                )
                Text(
                    text = "Native Android app",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFA0A8C3),
                )

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Rounded.Mail, contentDescription = null)
                    },
                )

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (true) PasswordVisualTransformation() else VisualTransformation.None,
                    leadingIcon = {
                        Icon(Icons.Rounded.Lock, contentDescription = null)
                    },
                )

                uiState.errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                uiState.infoMessage?.let {
                    Text(
                        text = it,
                        color = Color(0xFF8FD3A8),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading,
                    onClick = { onLogin(email, password) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5EA2F3),
                        contentColor = Color.White,
                    ),
                ) {
                    Text(if (uiState.isLoading) "Signing in..." else "Sign in")
                }

                TextButton(
                    modifier = Modifier.align(Alignment.End),
                    onClick = onNavigateRegister,
                ) {
                    Text("Create account")
                }
            }
        }
    }
}
