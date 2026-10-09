package com.qbili.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TextUtilsTest {

    @Test
    fun `stripHtml 去掉搜索结果的关键词高亮标签`() {
        assertEquals(
            "原神薇斯纳实机演示",
            stripHtml("<em class=\"keyword\">原神</em>薇斯纳实机演示"),
        )
    }

    @Test
    fun `stripHtml 还原 HTML 实体`() {
        assertEquals("A&B <tag> \"quoted\"", stripHtml("A&amp;B &lt;tag&gt; &quot;quoted&quot;"))
    }

    @Test
    fun `stripHtml 处理空值`() {
        assertEquals("", stripHtml(null))
        assertEquals("", stripHtml(""))
    }

    /**
     * 搜索接口的 duration 是 "分:秒" 而不是 "时:分"。
     * 实测样本 "1:1" 对应 61 秒——这个坑如果搞错，所有搜索结果的时长都会差 60 倍。
     */
    @Test
    fun `parseDurationText 两段视为分秒`() {
        assertEquals(61, parseDurationText("1:1"))
        assertEquals(754, parseDurationText("12:34"))
        assertEquals(0, parseDurationText("0:00"))
    }

    @Test
    fun `parseDurationText 三段视为时分秒`() {
        assertEquals(3723, parseDurationText("1:02:03"))
        assertEquals(36000, parseDurationText("10:00:00"))
    }

    @Test
    fun `parseDurationText 兼容纯秒数与非法输入`() {
        assertEquals(444, parseDurationText("444"))
        assertEquals(0, parseDurationText(null))
        assertEquals(0, parseDurationText(""))
        assertEquals(0, parseDurationText("直播中"))
        assertEquals(0, parseDurationText("1:2:3:4"))
    }

    @Test
    fun `normalizeUrl 补全协议相对地址并升级到 https`() {
        assertEquals("https://i1.hdslb.com/a.jpg", normalizeUrl("//i1.hdslb.com/a.jpg"))
        assertEquals("https://i1.hdslb.com/a.jpg", normalizeUrl("http://i1.hdslb.com/a.jpg"))
        assertEquals("https://i1.hdslb.com/a.jpg", normalizeUrl("https://i1.hdslb.com/a.jpg"))
        assertEquals("", normalizeUrl(null))
    }

    @Test
    fun `formatCount 万亿分级并去掉多余的点零`() {
        assertEquals("999", formatCount(999))
        assertEquals("1.2万", formatCount(12_345))
        assertEquals("1万", formatCount(10_000))
        assertEquals("1.2亿", formatCount(123_456_789))
        assertEquals("0", formatCount(null))
    }

    @Test
    fun `formatDuration 超过一小时才显示小时位`() {
        assertEquals("01:01", formatDuration(61))
        assertEquals("1:02:03", formatDuration(3723))
        assertEquals("00:00", formatDuration(0))
        assertEquals("00:00", formatDuration(null))
    }
}
