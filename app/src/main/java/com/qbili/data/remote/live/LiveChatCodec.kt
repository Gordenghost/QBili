package com.qbili.data.remote.live

import com.qbili.domain.model.LiveChatMessage
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.zip.InflaterInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object LiveChatCodec {
    fun auth(roomId: Long, token: String): ByteArray = packet(
        7, "{\"uid\":0,\"roomid\":$roomId,\"protover\":2,\"platform\":\"web\",\"type\":2,\"key\":${Json.encodeToString(kotlinx.serialization.serializer<String>(), token)}}".toByteArray(),
    )

    fun heartbeat(): ByteArray = packet(2, "[object Object]".toByteArray())

    fun messages(bytes: ByteArray): List<LiveChatMessage> = parse(bytes, 0)

    private fun packet(operation: Int, payload: ByteArray): ByteArray = ByteBuffer.allocate(16 + payload.size)
        .putInt(16 + payload.size).putShort(16).putShort(1).putInt(operation).putInt(1)
        .put(payload).array()

    private fun parse(bytes: ByteArray, depth: Int): List<LiveChatMessage> {
        if (depth > 2 || bytes.size > 1_048_576) return emptyList()
        val messages = mutableListOf<LiveChatMessage>()
        var position = 0
        while (position + 16 <= bytes.size) {
            val header = ByteBuffer.wrap(bytes, position, 16)
            val length = header.int
            val headerLength = header.short.toInt() and 0xffff
            val version = header.short.toInt() and 0xffff
            val operation = header.int
            if (length < 16 || headerLength < 16 || headerLength > length || position + length > bytes.size) break
            val body = bytes.copyOfRange(position + headerLength, position + length)
            if (operation == 5) {
                if (version == 2) {
                    val decompressed = runCatching {
                        InflaterInputStream(ByteArrayInputStream(body)).use { it.readNBytes(1_048_577) }
                    }.getOrNull()
                    if (decompressed != null && decompressed.size <= 1_048_576) {
                        messages += parse(decompressed, depth + 1)
                    }
                } else if (version == 0 || version == 1) {
                    val event = runCatching { Json.parseToJsonElement(body.decodeToString()) as? JsonObject }.getOrNull()
                    if ((event?.get("cmd") as? JsonPrimitive)?.contentOrNull?.startsWith("DANMU_MSG") == true) {
                        val info = event["info"] as? JsonArray
                        val text = (info?.getOrNull(1) as? JsonPrimitive)?.contentOrNull.orEmpty()
                        val sender = ((info?.getOrNull(2) as? JsonArray)?.getOrNull(1) as? JsonPrimitive)
                            ?.contentOrNull.orEmpty()
                        if (text.isNotBlank()) messages += LiveChatMessage(sender, text)
                    }
                }
            }
            position += length
        }
        return messages
    }
}
