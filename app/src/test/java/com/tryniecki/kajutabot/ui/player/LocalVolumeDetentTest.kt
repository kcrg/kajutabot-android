package com.tryniecki.kajutabot.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalVolumeDetentTest {
    @Test fun `touch drag snaps within three percent and exits freely`() {
        for (value in 97..103) assertEquals(100, snapLocalVolume(value, dragging = true))
        for (value in listOf(0, 50, 96, 104, 150, 200)) {
            assertEquals(value, snapLocalVolume(value, dragging = true))
        }
    }

    @Test fun `accessibility and keyboard preserve fine adjustment near reference`() {
        for (value in 96..104) assertEquals(value, snapLocalVolume(value, dragging = false))
        assertEquals(0, snapLocalVolume(-1, dragging = false))
        assertEquals(200, snapLocalVolume(201, dragging = true))
    }
}
