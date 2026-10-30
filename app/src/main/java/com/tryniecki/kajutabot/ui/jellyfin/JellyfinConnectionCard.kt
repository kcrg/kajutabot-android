package com.tryniecki.kajutabot.ui.jellyfin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.ui.text.asString

/** What a guest sees in place of [JellyfinConnectionCard]: linking a server needs an account. */
@Composable
fun JellyfinDemoCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_server_outline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column {
                Text(
                    text = stringResource(R.string.nav_jellyfin),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.jellyfin_demo_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The Jellyfin section of the More tab: which server is linked, and linking or unlinking it. */
@Composable
fun JellyfinConnectionCard(viewModel: JellyfinViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    SideEffect(viewModel) { viewModel.refreshConnection() }
    SideEffect(ui.isConnected) { if (ui.isConnected) dialogOpen = false }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_server_outline),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ui.connection?.serverUrl ?: stringResource(R.string.nav_jellyfin),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when (ui.status) {
                            JellyfinConnectionStatus.CHECKING -> stringResource(R.string.jellyfin_status_checking)
                            JellyfinConnectionStatus.CONNECTED -> stringResource(
                                R.string.jellyfin_status_connected,
                                ui.connection?.jellyfinUserName.orEmpty(),
                            )
                            JellyfinConnectionStatus.DISCONNECTED -> stringResource(R.string.jellyfin_status_disconnected)
                            JellyfinConnectionStatus.ERROR -> stringResource(R.string.jellyfin_status_error)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ui.isConnected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            ui.disconnectError?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
            when (ui.status) {
                JellyfinConnectionStatus.CHECKING -> Unit
                JellyfinConnectionStatus.CONNECTED -> OutlinedButton(
                    onClick = viewModel::disconnect,
                    enabled = !ui.isDisconnecting,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.jellyfin_disconnect)) }
                JellyfinConnectionStatus.DISCONNECTED -> Button(
                    onClick = { dialogOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.jellyfin_connect)) }
                JellyfinConnectionStatus.ERROR -> OutlinedButton(
                    onClick = viewModel::refreshConnection,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }

    if (dialogOpen) {
        JellyfinConnectDialog(
            link = ui.link,
            onConnect = viewModel::startQuickConnect,
            onDismiss = {
                viewModel.cancelQuickConnect()
                dialogOpen = false
            },
        )
    }
}

@Composable
private fun JellyfinConnectDialog(
    link: JellyfinLinkState,
    onConnect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var serverUrl by rememberSaveable { mutableStateOf("") }
    val isBusy = link == JellyfinLinkState.Starting
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.jellyfin_connect_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (link is JellyfinLinkState.Waiting) {
                    Text(stringResource(R.string.jellyfin_connect_approve))
                    SelectionContainer {
                        Text(
                            text = link.code,
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.jellyfin_connect_waiting))
                    }
                } else {
                    Text(stringResource(R.string.jellyfin_connect_description))
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.jellyfin_server_url)) },
                        placeholder = { Text("https://jellyfin.example.com") },
                        singleLine = true,
                        enabled = !isBusy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    if (link is JellyfinLinkState.Failed) {
                        Text(link.message.asString(), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (link !is JellyfinLinkState.Waiting) {
                TextButton(
                    onClick = { onConnect(serverUrl) },
                    enabled = !isBusy && serverUrl.isNotBlank(),
                ) { Text(stringResource(R.string.jellyfin_connect)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
