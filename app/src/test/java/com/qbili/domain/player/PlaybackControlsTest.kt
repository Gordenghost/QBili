package com.qbili.domain.player

import androidx.media3.common.Player
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackControlsTest {
    private class FakePlayer(var requested: Boolean, var state: Int) {
        val actions = mutableListOf<String>()
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, arguments ->
            when (method.name) {
                "getPlayWhenReady" -> requested
                "getPlaybackState" -> state
                "isPlaying" -> throw AssertionError("是否播放不能代替用户的播放意图")
                "pause" -> { actions += "pause"; requested = false; null }
                "play" -> { actions += "play"; requested = true; null }
                "seekTo" -> { actions += "seek:${arguments?.last()}"; null }
                else -> null
            }
        } as Player
    }

    @Test
    fun `播放中和缓冲中单击都必须暂停`() {
        listOf(Player.STATE_READY, Player.STATE_BUFFERING).forEach { state ->
            val fake = FakePlayer(true, state)
            fake.player.togglePlayback()
            assertFalse(fake.requested)
            assertEquals(listOf("pause"), fake.actions)
        }
    }

    @Test
    fun `暂停后再次单击恢复播放且再次单击又暂停`() {
        val fake = FakePlayer(false, Player.STATE_READY)
        fake.player.togglePlayback()
        assertTrue(fake.requested)
        fake.player.togglePlayback()
        assertFalse(fake.requested)
        assertEquals(listOf("play", "pause"), fake.actions)
    }

    @Test
    fun `播放结束点击播放会从头播放`() {
        val fake = FakePlayer(true, Player.STATE_ENDED)
        fake.player.togglePlayback()
        assertEquals(listOf("seek:0", "play"), fake.actions)
        assertTrue(fake.requested)
    }
}
