package com.qbili.data.remote.danmaku

import com.qbili.core.protobuf.ProtoReader
import com.qbili.core.protobuf.ProtoReader.Companion.fieldNumber
import com.qbili.core.protobuf.ProtoReader.Companion.wireType
import com.qbili.domain.model.DanmakuItem
import com.qbili.domain.model.DanmakuMode

/**
 * 解析 `/x/v2/dm/web/seg.so` 返回的 DmSegMobileReply。
 *
 * 字段编号是对真实响应逐字段 dump 出来的（样本：1409 条弹幕的分段）：
 *
 * ```
 * DmSegMobileReply { repeated DanmakuElem elems = 1; }   // 外层还有 4、5 两个我们不用的字段
 * DanmakuElem {
 *    1 id          int64      2 progress  int32(ms)   3 mode     int32
 *    4 fontsize    int32      5 color     uint32      6 midHash  string
 *    7 content     string     8 ctime     int64       9 weight   int32
 *   12 idStr       string    // 实测还有 15/20/21/25/26/27 等字段，一律跳过
 * }
 * ```
 *
 * 每段覆盖 6 分钟（segment_index 从 1 开始），所以一个长视频要拉多段。
 */
object DanmakuParser {

    private const val FIELD_ELEMS = 1

    private const val ELEM_ID = 1
    private const val ELEM_PROGRESS = 2
    private const val ELEM_MODE = 3
    private const val ELEM_FONT_SIZE = 4
    private const val ELEM_COLOR = 5
    private const val ELEM_MID_HASH = 6
    private const val ELEM_CONTENT = 7
    private const val ELEM_CTIME = 8
    private const val ELEM_WEIGHT = 9

    private const val DEFAULT_FONT_SIZE = 25
    private const val DEFAULT_COLOR = 0xFFFFFF

    /**
     * @param bytes 一个分段的原始响应体
     * @return 按出现时间升序排列的弹幕；内容为空的条目会被丢弃
     */
    fun parseSegment(bytes: ByteArray): List<DanmakuItem> {
        if (bytes.isEmpty()) return emptyList()

        val result = ArrayList<DanmakuItem>(256)
        val reader = ProtoReader(bytes)

        while (reader.hasNext()) {
            val tag = reader.readTag()
            if (fieldNumber(tag) == FIELD_ELEMS && wireType(tag) == ProtoReader.WIRE_LENGTH_DELIMITED) {
                parseElement(reader.readMessage())?.let(result::add)
            } else {
                reader.skip(wireType(tag))
            }
        }

        result.sortBy { it.progressMillis }
        return result
    }

    private fun parseElement(reader: ProtoReader): DanmakuItem? {
        var id = 0L
        var progress = 0
        var mode = 1
        var fontSize = DEFAULT_FONT_SIZE
        var color = DEFAULT_COLOR
        var midHash = ""
        var content = ""
        var ctime = 0L
        var weight = 0

        while (reader.hasNext()) {
            val tag = reader.readTag()
            val field = fieldNumber(tag)
            val wire = wireType(tag)

            // 类型不匹配就当未知字段跳过，避免服务端改类型直接把整段解析搞崩
            when {
                field == ELEM_ID && wire == ProtoReader.WIRE_VARINT -> id = reader.readVarint()
                field == ELEM_PROGRESS && wire == ProtoReader.WIRE_VARINT ->
                    progress = reader.readVarint().toInt()
                field == ELEM_MODE && wire == ProtoReader.WIRE_VARINT ->
                    mode = reader.readVarint().toInt()
                field == ELEM_FONT_SIZE && wire == ProtoReader.WIRE_VARINT ->
                    fontSize = reader.readVarint().toInt()
                field == ELEM_COLOR && wire == ProtoReader.WIRE_VARINT ->
                    color = reader.readVarint().toInt()
                field == ELEM_MID_HASH && wire == ProtoReader.WIRE_LENGTH_DELIMITED ->
                    midHash = reader.readString()
                field == ELEM_CONTENT && wire == ProtoReader.WIRE_LENGTH_DELIMITED ->
                    content = reader.readString()
                field == ELEM_CTIME && wire == ProtoReader.WIRE_VARINT -> ctime = reader.readVarint()
                field == ELEM_WEIGHT && wire == ProtoReader.WIRE_VARINT ->
                    weight = reader.readVarint().toInt()
                else -> reader.skip(wire)
            }
        }

        if (content.isEmpty()) return null

        return DanmakuItem(
            id = id,
            progressMillis = progress.coerceAtLeast(0),
            mode = DanmakuMode.from(mode),
            fontSize = if (fontSize in 8..64) fontSize else DEFAULT_FONT_SIZE,
            // 服务端给的是 24 位 RGB；异常值退回白色，否则会画出透明或全黑的弹幕
            color = if (color in 0..0xFFFFFF) color else DEFAULT_COLOR,
            senderHash = midHash,
            content = content,
            sendTimeSeconds = ctime,
            weight = weight.coerceIn(0, 10),
        )
    }
}
