package com.qbili.data.remote.danmaku

import com.qbili.domain.model.DanmakuMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * 弹幕 protobuf 解析的回归测试。
 *
 * 重点不是「能解析出内容」，而是**未知字段必须被正确跳过**——
 * 真实响应里 DanmakuElem 带着 15/20/21/25/26/27 等我们不用的字段，
 * 跳错一个字节后面所有弹幕都会变成乱码，而这种错在真机上极难定位。
 */
class DanmakuParserTest {

    /** 手写一个最小 protobuf 编码器，避免为了测试引入 protoc */
    private class ProtoWriter {
        private val out = ByteArrayOutputStream()

        fun varint(field: Int, value: Long) = apply {
            writeVarint((field shl 3 or 0).toLong())
            writeVarint(value)
        }

        fun string(field: Int, value: String) = apply {
            val bytes = value.toByteArray(Charsets.UTF_8)
            writeVarint((field shl 3 or 2).toLong())
            writeVarint(bytes.size.toLong())
            out.write(bytes)
        }

        fun message(field: Int, bytes: ByteArray) = apply {
            writeVarint((field shl 3 or 2).toLong())
            writeVarint(bytes.size.toLong())
            out.write(bytes)
        }

        fun fixed32(field: Int, value: Int) = apply {
            writeVarint((field shl 3 or 5).toLong())
            repeat(4) { i -> out.write((value shr (i * 8)) and 0xFF) }
        }

        fun fixed64(field: Int, value: Long) = apply {
            writeVarint((field shl 3 or 1).toLong())
            repeat(8) { i -> out.write(((value shr (i * 8)) and 0xFF).toInt()) }
        }

        fun build(): ByteArray = out.toByteArray()

        private fun writeVarint(value: Long) {
            var v = value
            while (true) {
                if (v and 0x7FL.inv() == 0L) {
                    out.write(v.toInt())
                    return
                }
                out.write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
        }
    }

    /** 按实测样本的字段构造一条弹幕，含真实存在的未知字段 */
    private fun sampleElem(
        content: String = "年 代 金 曲",
        progress: Int = 6996,
        mode: Int = 1,
        weight: Int = 9,
        withUnknownFields: Boolean = true,
    ): ByteArray = ProtoWriter().apply {
        varint(1, 31039004496363523L)
        varint(2, progress.toLong())
        varint(3, mode.toLong())
        varint(4, 25)
        varint(5, 16777215)
        string(6, "362b0ee5")
        string(7, content)
        varint(8, 1586344377)
        varint(9, weight.toLong())
        string(12, "31039004496363523")
        if (withUnknownFields) {
            // 实测响应里真实存在的字段，解析器必须原样跳过
            varint(15, 33)
            string(20, "0")
            string(21, "0")
            varint(25, 1)
            varint(26, 137649199)
            varint(27, 1)
        }
    }.build()

    private fun segment(vararg elems: ByteArray, withOuterExtras: Boolean = true): ByteArray =
        ProtoWriter().apply {
            elems.forEach { message(1, it) }
            if (withOuterExtras) {
                // 外层实测还有 4、5 两个字段
                varint(4, 6)
                varint(5, 1)
            }
        }.build()

    @Test
    fun `解析单条弹幕的全部关心字段`() {
        val items = DanmakuParser.parseSegment(segment(sampleElem()))

        assertEquals(1, items.size)
        val item = items.first()
        assertEquals(31039004496363523L, item.id)
        assertEquals(6996, item.progressMillis)
        assertEquals(DanmakuMode.SCROLL, item.mode)
        assertEquals(25, item.fontSize)
        assertEquals(0xFFFFFF, item.color)
        assertEquals("362b0ee5", item.senderHash)
        assertEquals("年 代 金 曲", item.content)
        assertEquals(1586344377L, item.sendTimeSeconds)
        assertEquals(9, item.weight)
    }

    @Test
    fun `带未知字段与不带未知字段解析结果一致`() {
        val withUnknown = DanmakuParser.parseSegment(segment(sampleElem(withUnknownFields = true)))
        val without = DanmakuParser.parseSegment(segment(sampleElem(withUnknownFields = false)))

        assertEquals(without, withUnknown)
    }

    @Test
    fun `未知字段之后的弹幕仍能正确解析`() {
        // 如果跳过逻辑有一个字节的偏差，第二条就会解析失败或变成乱码
        val items = DanmakuParser.parseSegment(
            segment(
                sampleElem(content = "第一条", progress = 1000),
                sampleElem(content = "第二条", progress = 2000),
                sampleElem(content = "第三条", progress = 3000),
            ),
        )

        assertEquals(listOf("第一条", "第二条", "第三条"), items.map { it.content })
    }

    @Test
    fun `fixed32 与 fixed64 类型的未知字段也要能跳过`() {
        val elem = ProtoWriter().apply {
            varint(2, 500)
            string(7, "内容")
            fixed32(30, 0x12345678)
            fixed64(31, 0x1122334455667788L)
            varint(9, 5)
        }.build()

        val items = DanmakuParser.parseSegment(segment(elem))

        assertEquals(1, items.size)
        assertEquals("内容", items.first().content)
        // weight 排在两个 fixed 字段之后，跳错就读不到它
        assertEquals(5, items.first().weight)
    }

    @Test
    fun `外层的非 elems 字段被忽略`() {
        val withExtras = DanmakuParser.parseSegment(segment(sampleElem(), withOuterExtras = true))
        val without = DanmakuParser.parseSegment(segment(sampleElem(), withOuterExtras = false))

        assertEquals(without, withExtras)
    }

    @Test
    fun `mode 1 2 3 都归为滚动弹幕`() {
        listOf(1, 2, 3).forEach { raw ->
            val items = DanmakuParser.parseSegment(segment(sampleElem(mode = raw)))
            assertEquals("mode=$raw 应为滚动", DanmakuMode.SCROLL, items.first().mode)
        }
        assertEquals(
            DanmakuMode.TOP,
            DanmakuParser.parseSegment(segment(sampleElem(mode = 5))).first().mode,
        )
        assertEquals(
            DanmakuMode.BOTTOM,
            DanmakuParser.parseSegment(segment(sampleElem(mode = 4))).first().mode,
        )
    }

    @Test
    fun `结果按出现时间升序排列`() {
        val items = DanmakuParser.parseSegment(
            segment(
                sampleElem(content = "晚", progress = 9000),
                sampleElem(content = "早", progress = 100),
                sampleElem(content = "中", progress = 4000),
            ),
        )

        assertEquals(listOf("早", "中", "晚"), items.map { it.content })
    }

    @Test
    fun `内容为空的弹幕被丢弃`() {
        val items = DanmakuParser.parseSegment(
            segment(sampleElem(content = ""), sampleElem(content = "有内容")),
        )

        assertEquals(listOf("有内容"), items.map { it.content })
    }

    @Test
    fun `异常的字号与颜色回退到默认值`() {
        val elem = ProtoWriter().apply {
            varint(2, 100)
            varint(4, 999)          // 字号明显异常
            varint(5, 0x7FFFFFFF)   // 超出 24 位 RGB
            string(7, "内容")
        }.build()

        val item = DanmakuParser.parseSegment(segment(elem)).first()

        assertEquals(25, item.fontSize)
        assertEquals(0xFFFFFF, item.color)
    }

    @Test
    fun `空响应返回空列表而不是抛异常`() {
        assertEquals(emptyList<Any>(), DanmakuParser.parseSegment(ByteArray(0)))
    }

    @Test
    fun `高级与代码弹幕标记为不可渲染`() {
        val advanced = DanmakuParser.parseSegment(segment(sampleElem(mode = 7))).first()
        val bas = DanmakuParser.parseSegment(segment(sampleElem(mode = 9))).first()
        val scroll = DanmakuParser.parseSegment(segment(sampleElem(mode = 1))).first()

        assertTrue("高级弹幕需要脚本引擎，应跳过", !advanced.isRenderable)
        assertTrue("BAS 弹幕需要脚本引擎，应跳过", !bas.isRenderable)
        assertTrue("普通滚动弹幕应可渲染", scroll.isRenderable)
    }
}
