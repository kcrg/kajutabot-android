package com.tryniecki.kajutabot.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.ui.components.TrackArtwork
import com.tryniecki.kajutabot.ui.favorites.FavoriteTrackButton
import com.tryniecki.kajutabot.ui.navigation.AppDestination
import com.tryniecki.kajutabot.ui.theme.KbMotion
import com.tryniecki.kajutabot.ui.theme.forwardSharedAxisX

private const val MINI_PLAYER_HEIGHT_DP = 68

/**
 * Visibility rule for the global MiniPlayer. Pure logic, unit-tested.
 *
 * The bar is an active-player affordance: it shows only while signed in, with
 * the bottom navigation visible (no fullscreen modal), off the Player tab,
 * and with an actual track playing. Empty/idle queue states stay hidden.
 */
fun shouldShowMiniPlayer(
    isAuthenticated: Boolean,
    isBottomBarVisible: Boolean,
    destination: AppDestination,
    hasNowPlaying: Boolean,
): Boolean =
    isAuthenticated &&
        isBottomBarVisible &&
        destination != AppDestination.PLAYER &&
        hasNowPlaying

/**
 * Compact global player bar docked directly above the bottom NavigationBar.
 *
 * Presentational only: it reads the shared player and favorites state and forwards actions.
 * No own polling or queue fetch — progress
 * reuses [rememberPlaybackProgress]. Fixed height, single-line title.
 */
@Composable
fun MiniPlayer(
    slide: NowPlayingSlide,
    track: PlaybackTrackResponse,
    isMutating: Boolean,
    activeControlAction: PlayerControlAction?,
    isFavorite: Boolean,
    favoritesBusy: Boolean,
    onToggleFavorite: (PlaybackTrackResponse) -> Unit,
    onOpenPlayer: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val playbackControlsBlocked = isMutating && activeControlAction == null

    val progress = rememberPlaybackProgress(
        playbackKey = slide.identity,
        startedAtRaw = slide.startedAt,
        durationMs = slide.durationMs,
    )

    val animatedFraction = rememberSmoothPlaybackFraction(progress.fraction)

    Surface(
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(MINI_PLAYER_HEIGHT_DP.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = slide,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    transitionSpec = {
                        forwardSharedAxisX(
                            fadeSpec = motion.fastEffectsSpec(),
                            slideSpec = motion.fastSpatialSpec(),
                            distanceFraction = KbMotion.MINI_TRACK_SLIDE_FRACTION,
                        )
                    },
                    label = "miniPlayer",
                ) { current ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                role = Role.Button,
                                onClickLabel = stringResource(R.string.action_open_player),
                                onClick = onOpenPlayer,
                            )
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TrackArtwork(
                            imageUrl = current.thumbnailUrl,
                            modifier = Modifier.size(48.dp),
                            brokenIconSize = 20.dp,
                        )

                        Spacer(Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = current.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Text(
                                text = "${formatPlaybackElapsed(progress.positionMs)} / " +
                                        formatPlaybackTotal(current.durationMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                FavoriteTrackButton(
                    track = track,
                    checked = isFavorite,
                    enabled = !favoritesBusy,
                    onToggle = onToggleFavorite,
                )
                IconButton(
                    onClick = onSkip,
                    enabled = !playbackControlsBlocked,
                ) {
                    Icon(
                        painter = painterResource(
                            com.composables.icons.tabler.outline.R.drawable
                                .tabler_ic_player_skip_forward_outline,
                        ),
                        contentDescription = stringResource(R.string.action_skip_track),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            LinearProgressIndicator(
                progress = { animatedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
            )
        }
    }
}
