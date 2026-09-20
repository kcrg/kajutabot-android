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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
                CircularProgressIndicator()
                Text(
                    text = if (isGuest) "Sprawdzanie serwera demonstracyjnego…" else "Sprawdzanie dostępu do Discorda…",
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
        title = "Brak dostępu",
        message = if (isGuest) "Serwer demonstracyjny jest obecnie niedostępny. Spróbuj ponownie później."
            else "Nie masz dostępu do żadnego serwera Discord, na którym znajduje się KajutaBot. Do aplikacji wejdziesz, gdy pojawi się co najmniej jeden wspólny serwer.",
        primaryLabel = "Sprawdź ponownie",
        onPrimary = onRetry,
        onLogout = onLogout,
    )
}

@Composable
fun AccessErrorScreen(
    message: String?,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    isGuest: Boolean = false,
) {
    AccessMessageScreen(
        title = "Nie udało się sprawdzić dostępu",
        message = message ?: if (isGuest) "Nie można teraz pobrać serwera demonstracyjnego. Sprawdź połączenie i spróbuj ponownie."
            else "Nie można teraz pobrać listy serwerów Discord. Sprawdź połączenie i spróbuj ponownie.",
        primaryLabel = "Spróbuj ponownie",
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
                        Text("Wyloguj się")
                    }
                }
            }
        }
    }
}
