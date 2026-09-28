package com.tryniecki.kajutabot.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.ui.components.ExpressiveLoadingIndicator
import com.tryniecki.kajutabot.ui.components.rememberDelayedPending
import com.tryniecki.kajutabot.ui.components.TrackArtwork
import com.tryniecki.kajutabot.ui.text.asString

@Composable
fun SharedTrackRoute(
    viewModel: PlayerViewModel,
    requestId: Long,
    url: String,
    onClose: () -> Unit,
) {
    val enqueueState by viewModel.sharedEnqueueState.collectAsStateWithLifecycle()
    var requestStarted by rememberSaveable(requestId) { mutableStateOf(false) }

    LaunchedEffect(requestId) {
        if (!requestStarted) {
            requestStarted = true
            viewModel.enqueueSharedUrl(requestId, url)
        }
    }

    SharedTrackScreen(
        status = enqueueState.status.takeIf { enqueueState.requestId == requestId },
        unconfirmed = requestStarted && enqueueState.requestId != requestId,
        onClose = onClose,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedTrackScreen(
    status: SharedEnqueueStatus?,
    unconfirmed: Boolean,
    onClose: () -> Unit,
) {
    val loading = !unconfirmed && (status == null || status == SharedEnqueueStatus.Loading)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shared_track_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_x_outline),
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (!loading) {
                Box(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                ) {
                    Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.shared_track_open_app))
                    }
                }
            }
        },
    ) { innerPadding ->
        if (status is SharedEnqueueStatus.Added && status.tracks.size > 1) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "playlist-summary") {
                    Text(
                        stringResource(R.string.shared_tracks_added_count, status.tracks.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                itemsIndexed(
                    status.tracks,
                    key = { index, track -> "${track.contentType}:${track.contentId}:$index" },
                ) { index, track ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        QueueTrackCardContent(track = track, position = index + 1)
                    }
                }
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                unconfirmed -> {
                    Text(
                        stringResource(R.string.shared_track_unconfirmed),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
                loading -> {
                    if (rememberDelayedPending(loading)) {
                        ExpressiveLoadingIndicator(modifier = Modifier.size(48.dp))
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                    Text(
                        stringResource(R.string.shared_track_loading),
                        modifier = Modifier.padding(top = 20.dp),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                status is SharedEnqueueStatus.Added -> {
                    status.tracks.singleOrNull()?.let { track ->
                        TrackArtwork(
                            imageUrl = track.artworkUrl,
                            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(16f / 9f),
                            brokenIconSize = 48.dp,
                        )
                        Text(
                            track.title,
                            modifier = Modifier.padding(top = 20.dp),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Text(
                        stringResource(R.string.shared_track_added),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
                status is SharedEnqueueStatus.Failed -> {
                    Text(
                        stringResource(R.string.shared_track_failed),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        status.message.asString(),
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
