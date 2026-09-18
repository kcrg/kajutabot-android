package com.tryniecki.kajutabot.api.model.search

import com.tryniecki.kajutabot.api.model.common.TrackResponse
import kotlinx.serialization.Serializable

@Serializable
data class SearchItemResponse(
    val input: String,
    val track: TrackResponse,
    val metricCount: Long,
    val metricLabel: String?,
    val metricCaption: String,
    val dateLabel: String? = null,
)

@Serializable
data class SearchResponse(
    val query: String,
    val source: String,
    val items: List<SearchItemResponse>,
)
