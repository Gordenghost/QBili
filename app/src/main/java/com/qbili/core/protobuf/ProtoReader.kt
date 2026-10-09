package com.qbili.core.protobuf

/**
 * 极简 protobuf wire format 读取器。
 *
 * 只为解析弹幕的 DmSegMobileReply 而写。刻意不引入 protobuf-javalite +
 * protoc 插件：那要多一个 Gradle 插件、下载 protoc 二进制、维护 .proto 文件，
 * 而我们只需要读一个结构固定的消息，手写 60 行反而更可控。
 *
 * 关键要求是**未知字段必须能正确跳过**：B 站的 DanmakuElem 实测带有
 * 15/20/21/25/26/27 等我们不关心的字段，跳错一个字节后面全部错位。
 */
class ProtoReader(
    private val bytes: ByteArray,
    private var pos: Int = 0,
    private val end: Int = bytes.size,
) {
    init {
        require(pos >= 0 && end <= bytes.size && pos <= end) {
            "非法的读取区间: pos=$pos end=$end size=${bytes.size}"
        }
    }

    fun hasNext(): Boolean = pos < end

    /** @return 字段号 shl 3 or wireType；调用方用 [fieldNumber] / [wireType] 拆开 */
    fun readTag(): Int = readVarint().toInt()

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            check(pos < end) { "读取 varint 时数据意外结束 (pos=$pos)" }
            check(shift < 64) { "varint 超过 64 位，数据已损坏" }
            val b = bytes[pos++].toInt()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
    }

    fun readBytes(): ByteArray {
        val length = readVarint().toInt()
        check(length >= 0 && pos + length <= end) { "长度前缀越界: length=$length" }
        return bytes.copyOfRange(pos, pos + length).also { pos += length }
    }

    fun readString(): String = String(readBytes(), Charsets.UTF_8)

    /** 返回一个只覆盖当前 length-delimited 字段的子读取器，避免额外拷贝 */
    fun readMessage(): ProtoReader {
        val length = readVarint().toInt()
        check(length >= 0 && pos + length <= end) { "嵌套消息长度越界: length=$length" }
        val sub = ProtoReader(bytes, pos, pos + length)
        pos += length
        return sub
    }

    /** 跳过一个不关心的字段。跳错会让后续解析全部错位，所以每种 wire type 都要处理。 */
    fun skip(wireType: Int) {
        when (wireType) {
            WIRE_VARINT -> readVarint()
            WIRE_FIXED64 -> advance(8)
            WIRE_LENGTH_DELIMITED -> advance(readVarint().toInt())
            WIRE_FIXED32 -> advance(4)
            // 3/4 是已废弃的 group，B 站不用；遇到只能放弃剩余部分，
            // 继续往下读只会读出乱码
            else -> pos = end
        }
    }

    private fun advance(count: Int) {
        check(count >= 0 && pos + count <= end) { "跳过 $count 字节会越界" }
        pos += count
    }

    companion object {
        const val WIRE_VARINT = 0
        const val WIRE_FIXED64 = 1
        const val WIRE_LENGTH_DELIMITED = 2
        const val WIRE_FIXED32 = 5

        fun fieldNumber(tag: Int): Int = tag ushr 3
        fun wireType(tag: Int): Int = tag and 0x7
    }
}
