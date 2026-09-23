package com.tryniecki.kajutabot.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.ui.components.ExpressiveLoadingIndicator
import com.tryniecki.kajutabot.ui.text.UiText
import com.tryniecki.kajutabot.ui.text.asString

@Composable
fun AccessCheckingScreen(isGuest: Boolean = false) {
    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ExpressiveLoadingIndicator(modifier = Modifier.size(48.dp))
                Text(
                    text = stringResource(if (isGuest) R.string.access_checking_guest else R.string.access_checking_discord),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun NoAccessScreen(
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    isGuest: Boolean = false,
) {
    AccessMessageScreen(
        title = stringResource(R.string.access_none_title),
        message = stringResource(if (isGuest) R.string.access_none_guest else R.string.access_none_discord),
        primaryLabel = stringResource(R.string.access_check_again),
        onPrimary = onRetry,
        onLogout = onLogout,
    )
}

@Composable
fun AccessErrorScreen(
    message: UiText?,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    isGuest: Boolean = false,
) {
    AccessMessageScreen(
        title = stringResource(R.string.access_error_title),
        message = message?.asString() ?: stringResource(if (isGuest) R.string.access_error_guest else R.string.access_error_discord),
        primaryLabel = stringResource(R.string.action_retry),
        onPrimary = onRetry,
        onLogout = onLogout,
    )
}

@Composable
private fun AccessMessageScreen(
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onLogout: () -> Unit,
) {
    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_server_outline),
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onPrimary,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(primaryLabel)
                    }
                    TextButton(onClick = onLogout) {
                        Text(stringResource(R.string.action_logout))
                    }
                }
            }
        }
    }
}
