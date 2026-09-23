package com.tryniecki.kajutabot.api.model.search

import com.tryniecki.kajutabot.api.model.common.SearchTrackResponse
import kotlinx.serialization.Serializable

@Serializable
data class SearchItemResponse(
    val input: String,
    val track: SearchTrackResponse,
    val metricCount: Long,
    val metricCaption: String,
    val dateLabel: String? = null,
)

@Serializable
data class SearchResponse(
    val query: String,
    val items: List<SearchItemResponse>,
)
