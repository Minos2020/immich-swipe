package com.markvoronin.immichswipe.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markvoronin.immichswipe.R
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource


/**
 * Écran d'authentification.
 */
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(15.dp))
                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                val logoRes = if (isDark) R.drawable.immichswipe_logo_colors_dark else R.drawable.immichswipe_logo_colors_light
                
                Image(
                    painter = painterResource(id = logoRes),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier
                        .height(50.dp)
                        .padding(vertical = 4.dp),
                    contentScale = ContentScale.Fit
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = viewModel::onBaseUrlChange,
                    label = { Text(stringResource(R.string.login_url_label)) },
                    placeholder = { Text(stringResource(R.string.login_url_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = viewModel::onApiKeyChange,
                    label = { Text(stringResource(R.string.login_api_key_label)) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.login() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !state.isLoading,
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.login_button), fontWeight = FontWeight.Bold)
                    }
                }

                AnimatedVisibility(
                    visible = state.error != null,
                    enter = fadeIn() + expandVertically()
                ) {
                    state.error?.let { error ->
                        val errorMessage = when (error) {
                            AuthError.EmptyFields -> stringResource(R.string.login_error_empty)
                            AuthError.Dns -> stringResource(R.string.login_error_dns)
                            AuthError.Timeout -> stringResource(R.string.login_error_timeout)
                            AuthError.Refused -> stringResource(R.string.login_error_refused)
                            AuthError.Auth -> stringResource(R.string.login_error_auth)
                            AuthError.Forbidden -> stringResource(R.string.login_error_forbidden)
                            AuthError.NotFound -> stringResource(R.string.login_error_not_found)
                            AuthError.Ssl -> stringResource(R.string.login_error_ssl)
                            is AuthError.Server -> stringResource(R.string.login_error_server, error.code)
                            is AuthError.Unknown -> error.message ?: stringResource(R.string.login_error_unknown)
                        }
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                if (state.success) {
                    Text(
                        text = stringResource(R.string.login_success),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
