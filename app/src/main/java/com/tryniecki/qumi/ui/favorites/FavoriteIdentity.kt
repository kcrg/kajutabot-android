package com.tryniecki.qumi.ui.favorites

import com.tryniecki.qumi.api.model.common.PlaybackTrackResponse
import com.tryniecki.qumi.api.model.favorites.FavoriteResponse
import java.util.Locale

private fun favoriteIdentity(contentType: String, contentId: String): String =
    "${contentType.lowercase(Locale.ROOT)}:$contentId"

internal val FavoriteResponse.favoriteIdentity: String get() = favoriteIdentity(contentType, contentId)

internal val PlaybackTrackResponse.favoriteIdentity: String get() = favoriteIdentity(contentType, contentId)

internal val PlaybackTrackResponse.supportsFavorites: Boolean
    get() = !contentType.equals("Jellyfin", ignoreCase = true)
