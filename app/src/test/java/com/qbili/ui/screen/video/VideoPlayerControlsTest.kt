package com.qbili.ui.screen.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlayerControlsTest {
    @Test
    fun `seek position matches tapped or dragged track location`() {
        assertEquals(60_000L, seekPositionMillis(100f, 200f, 120_000L))
        assertEquals(30_000L, seekPositionMillis(50f, 200f, 120_000L))
    }

    @Test
    fun `seek position stays within video duration`() {
        assertEquals(0L, seekPositionMillis(-25f, 200f, 120_000L))
        assertEquals(120_000L, seekPositionMillis(250f, 200f, 120_000L))
        assertEquals(0L, seekPositionMillis(10f, 0f, 120_000L))
        assertEquals(0L, seekPositionMillis(10f, 200f, 0L))
    }
}
