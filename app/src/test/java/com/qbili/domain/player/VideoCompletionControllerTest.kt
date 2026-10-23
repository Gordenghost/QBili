package com.qbili.domain.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCompletionControllerTest {
    private class FakePlayer {
        var mediaId = "cid:1"
        var state = Player.STATE_ENDED
        var requested = true
        val actions = mutableListOf<String>()
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, arguments ->
            when (method.name) {
                "getCurrentMediaItem" -> MediaItem.Builder().setMediaId(mediaId).build()
                "getPlaybackState" -> state
                "getPlayWhenReady" -> requested
                "pause" -> { requested = false; actions += "pause"; null }
                "play" -> { requested = true; actions += "play"; null }
                "seekTo" -> { state = Player.STATE_READY; actions += "seek:${arguments?.last()}"; null }
                else -> null
            }
        } as Player
    }

    private fun readyController() = VideoCompletionController().apply {
        reset("cid:1")
        onPlaying("cid:1", true)
    }

    private fun finish(controller: VideoCompletionController, fake: FakePlayer,
        mode: VideoCompletionMode = VideoCompletionMode.NEXT_PAGE,
        allow: Boolean = true, sleep: Boolean = false, index: Int = 0, count: Int = 3): Boolean =
        controller.onEnded(fake.player, mode, index, count, allow, sleep,
            onNext = { fake.actions += "next:$it" }, onStop = { fake.actions += "stop" })

    @Test
    fun `default and unknown stored modes are manual stop`() {
        assertEquals(VideoCompletionMode.STOP, VideoCompletionMode.fromStored(null))
        assertEquals(VideoCompletionMode.STOP, VideoCompletionMode.fromStored("unknown"))
        VideoCompletionMode.entries.forEach { assertEquals(it, VideoCompletionMode.fromStored(it.name)) }
        val fake = FakePlayer()
        assertTrue(finish(readyController(), fake, VideoCompletionMode.STOP))
        assertEquals(listOf("pause", "stop"), fake.actions)
        assertFalse(fake.requested)
    }

    @Test
    fun `next page keeps real order and does not wrap at end`() {
        val fake = FakePlayer()
        finish(readyController(), fake, index = 1)
        assertEquals(listOf("next:2"), fake.actions)
        fake.actions.clear()
        finish(readyController(), fake, index = 2)
        assertEquals(listOf("pause", "stop"), fake.actions)
    }

    @Test
    fun `one page next mode stops and repeat seeks before playing`() {
        val onePage = FakePlayer()
        finish(readyController(), onePage, count = 1)
        assertEquals(listOf("pause", "stop"), onePage.actions)
        val repeat = FakePlayer()
        finish(readyController(), repeat, VideoCompletionMode.REPEAT_PAGE)
        assertEquals(listOf("seek:0", "play"), repeat.actions)
    }

    @Test
    fun `sleep at video end overrides every automatic mode`() {
        VideoCompletionMode.entries.forEach { mode ->
            val fake = FakePlayer()
            finish(readyController(), fake, mode, sleep = true)
            assertEquals(listOf("pause", "stop"), fake.actions)
        }
    }

    @Test
    fun `foreground session loading error gates stop rather than advance`() {
        VideoCompletionMode.entries.forEach { mode ->
            val fake = FakePlayer()
            finish(readyController(), fake, mode, allow = false)
            assertEquals(listOf("pause", "stop"), fake.actions)
        }
    }

    @Test
    fun `background and account leave clears intent even after entering again`() {
        val intent = SeasonPlaybackIntent().apply { setRequested(true) }
        intent.leave()
        intent.enter()
        val fake = FakePlayer()
        finish(readyController(), fake, allow = intent.shouldPlay)
        assertEquals(listOf("pause", "stop"), fake.actions)
    }

    @Test
    fun `paused ended player cannot automatically replay or advance`() {
        val fake = FakePlayer().apply { requested = false }
        finish(readyController(), fake)
        assertEquals(listOf("pause", "stop"), fake.actions)
    }

    @Test
    fun `media must actually have played before automatic action`() {
        val controller = VideoCompletionController().apply { reset("cid:1") }
        val fake = FakePlayer()
        controller.onPlaying("other", true)
        controller.onPlaying("cid:1", false)
        finish(controller, fake)
        assertEquals(listOf("pause", "stop"), fake.actions)
    }

    @Test
    fun `duplicate end callbacks advance only once`() {
        val controller = readyController()
        val fake = FakePlayer()
        assertTrue(finish(controller, fake))
        assertFalse(finish(controller, fake))
        assertEquals(listOf("next:1"), fake.actions)
    }

    @Test
    fun `replay arms another completion only after playback resumes`() {
        val controller = readyController()
        val fake = FakePlayer()
        finish(controller, fake, VideoCompletionMode.REPEAT_PAGE)
        fake.state = Player.STATE_ENDED
        assertFalse(finish(controller, fake, VideoCompletionMode.REPEAT_PAGE))
        controller.onPlaying("cid:1", true)
        assertTrue(finish(controller, fake, VideoCompletionMode.REPEAT_PAGE))
        assertEquals(listOf("seek:0", "play", "seek:0", "play"), fake.actions)
    }

    @Test
    fun `stream replacements and exit ignore obsolete media ends`() {
        val controller = readyController()
        val fake = FakePlayer()
        controller.reset("cid:2")
        assertFalse(finish(controller, fake))
        fake.mediaId = "cid:2"
        controller.onPlaying("cid:1", true)
        finish(controller, fake)
        assertEquals(listOf("pause", "stop"), fake.actions)
        fake.actions.clear()
        controller.reset(null)
        assertFalse(finish(controller, fake))
        assertTrue(fake.actions.isEmpty())
    }

    @Test
    fun `non ended buffering and ready notifications do nothing`() {
        listOf(Player.STATE_IDLE, Player.STATE_BUFFERING, Player.STATE_READY).forEach { state ->
            val fake = FakePlayer().apply { this.state = state }
            assertFalse(finish(readyController(), fake))
            assertTrue(fake.actions.isEmpty())
        }
    }

    @Test
    fun `invalid page positions stop for all modes`() {
        VideoCompletionMode.entries.forEach { mode ->
            listOf(-1 to 3, 3 to 3, 0 to 0, 0 to -1).forEach { (index, count) ->
                assertEquals(VideoCompletionAction.Stop,
                    videoCompletionAction(mode, index, count, true, false))
            }
        }
    }
}
