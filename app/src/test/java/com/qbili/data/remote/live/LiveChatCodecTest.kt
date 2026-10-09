package com.qbili.data.remote.live

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.DeflaterOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveChatCodecTest {
    @Test
    fun `auth and heartbeat use correct packet operation`() {
        val auth = LiveChatCodec.auth(123, "quoted\"key")
        assertEquals(auth.size, ByteBuffer.wrap(auth).int)
        assertEquals(7, ByteBuffer.wrap(auth, 8, 4).int)
        assertTrue(auth.decodeToString().contains("quoted\\\"key"))
        assertEquals(2, ByteBuffer.wrap(LiveChatCodec.heartbeat(), 8, 4).int)
    }

    @Test
    fun `multiple and zlib compressed danmaku packets are parsed`() {
        val message = packet(1, """{"cmd":"DANMU_MSG:4:0:2:2:2:0","info":[[],"你好",[1,"观众"]]}""".toByteArray())
        val next = packet(1, """{"cmd":"DANMU_MSG","info":[[],"第二条",[2,"用户"]]}""".toByteArray())
        val compressed = ByteArrayOutputStream().also { output ->
            DeflaterOutputStream(output).use { it.write(message + next) }
        }.toByteArray()
        val messages = LiveChatCodec.messages(packet(2, compressed))
        assertEquals(listOf("你好", "第二条"), messages.map { it.text })
        assertEquals(listOf("观众", "用户"), messages.map { it.sender })
    }

    private fun packet(version: Int, body: ByteArray): ByteArray = ByteBuffer.allocate(16 + body.size)
        .putInt(16 + body.size).putShort(16).putShort(version.toShort()).putInt(5).putInt(1)
        .put(body).array()
}
