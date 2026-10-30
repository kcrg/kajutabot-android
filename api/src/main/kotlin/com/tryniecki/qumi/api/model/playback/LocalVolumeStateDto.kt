package com.tryniecki.qumi.api.model.playback

import kotlinx.serialization.Serializable

@Serializable
data class LocalVolumeStateDto(
    val installed: Boolean,
    val online: Boolean,
    val available: Boolean,
    val volume: Int?,
) {
    fun isValid(): Boolean = (!online || installed) && (!available || online) &&
        ((volume != null) == available) && (volume == null || volume in 0..200)
}
