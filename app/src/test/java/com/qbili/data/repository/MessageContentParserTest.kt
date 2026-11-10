package com.qbili.data.repository

import com.qbili.domain.model.MessageOpenTarget
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageContentParserTest {
    @Test
    fun `分享视频优先BV号保留标题作者并标准化可信封面`() {
        val result = MessageContentParser.parse(7, """{
            "source":5,"id":246551172,"bvid":"BV16v411e7CW","headline":"主标题","title":"副标题",
            "author":"作者","thumb":"http://i2.hdslb.com/a.jpg","url":"https://evil.test"
        }""")
        assertEquals("[视频] 主标题", result.text)
        assertEquals("主标题", result.card?.title)
        assertEquals("作者", result.card?.author)
        assertEquals("https://i2.hdslb.com/a.jpg", result.card?.cover)
        assertEquals(MessageOpenTarget.Video("BV16v411e7CW"), result.card?.links?.single()?.target)
    }

    @Test
    fun `视频推送支持数字字符串AV回退和只有ID的载荷`() {
        val result = MessageContentParser.parse(11, """{"rid":"42","bvid":"BVbad",
            "desc":"简介","attach_msg":{"content":"UP主赠言：你好"}}""")
        assertEquals(MessageOpenTarget.Video("av42"), result.card?.links?.single()?.target)
        assertEquals("简介\nUP主赠言：你好", result.card?.description)
        assertEquals("视频卡片（标题暂未返回）", result.card?.title)
        assertEquals(MessageOpenTarget.Video("av9"),
            MessageContentParser.parse(7, """{"source":9,"id":9}""").card?.links?.single()?.target)
    }

    @Test
    fun `专栏推送解析摘要作者多封面赠言并使用CV号`() {
        val result = MessageContentParser.parse(12, """{"rid":42,"title":"文章","summary":"摘要",
            "author":"UP","image_urls":["https://evil.test/a.jpg","//i0.hdslb.com/a.jpg"],
            "attach_msg":{"content":"赠言"}}""")
        assertEquals("[专栏] 文章", result.text)
        assertEquals("摘要\n赠言", result.card?.description)
        assertEquals("https://i0.hdslb.com/a.jpg", result.card?.cover)
        assertEquals(MessageOpenTarget.Article(42), result.card?.links?.single()?.target)
        assertEquals(MessageOpenTarget.Article(43),
            MessageContentParser.parse(7, """{"source":6,"id":43}""").card?.links?.single()?.target)
    }

    @Test
    fun `番剧season与ep语义分开动态ID不能误当图文ID`() {
        assertEquals(MessageOpenTarget.Season(42),
            MessageContentParser.parse(7, """{"source":7,"id":42}""").card?.links?.single()?.target)
        listOf(16, 17, 11, 2, 3, 10).forEach { source ->
            assertTrue(MessageContentParser.parse(7, """{"source":$source,"id":42}""").card!!.links.isEmpty())
        }
        assertEquals(MessageOpenTarget.Opus("42"), MessageContentParser.parse(7,
            """{"source":11,"id":99,"url":"https://www.bilibili.com/opus/42"}""").card?.links?.single()?.target)
    }

    @Test
    fun `直播分享与未知分享类型仅由明确可信链接提供导航`() {
        val live = MessageContentParser.parse(14, """{"source":"直播","title":"直播中",
            "cover":"https://evil.test/image.jpg","url":"https://live.bilibili.com/42?from=im"}""")
        assertEquals("直播", live.card?.label)
        assertNull(live.card?.cover)
        assertEquals(MessageOpenTarget.Live(42), live.card?.links?.single()?.target)
        assertEquals(MessageOpenTarget.Space(43), MessageContentParser.parse(14,
            """{"title":"主页","url":"https://space.bilibili.com/43"}""").card?.links?.single()?.target)
        assertTrue(MessageContentParser.parse(14,
            """{"title":"外链","url":"https://evil.test/video/av42"}""").card!!.links.isEmpty())
    }

    @Test
    fun `通知模块支持多个站内按钮过滤外链隐藏按钮并去重`() {
        val result = MessageContentParser.parse(10, """{"title":"通知","text":"说明",
            "modules":[{"title":"模块","detail":"详情"},null],
            "jump_uri_config":{"all_uri":"bilibili://video/42","web_uri":"https://www.bilibili.com/video/av42","text":"看视频"},
            "jump_uri_2":"https://space.bilibili.com/43","jump_text_2":"看主页",
            "jump_uri_3":"https://evil.test/read/cv42"}""")
        assertEquals("说明\n模块：详情", result.card?.description)
        assertEquals(listOf("看视频", "看主页"), result.card?.links?.map { it.label })
        assertEquals(listOf(MessageOpenTarget.Video("av42"), MessageOpenTarget.Space(43)),
            result.card?.links?.map { it.target })
        val hidden = MessageContentParser.parse(10, """{
            "jump_uri_config":{"all_uri":"","web_uri":"https://www.bilibili.com/video/av42"},
            "jump_uri":"https://space.bilibili.com/43"}""")
        assertTrue(hidden.card!!.links.isEmpty())
        val duplicate = MessageContentParser.parse(10, """{
            "jump_uri":"https://space.bilibili.com/43","jump_uri_2":"https://space.bilibili.com/43"}""")
        assertEquals(1, duplicate.card?.links?.size)
    }

    @Test
    fun `系统提示反序列化内层数组忽略服务端颜色及链接`() {
        val raw = JsonObject(mapOf("content" to JsonPrimitive(
            """[{"text":"对方回复前只能发一条","color_day":"#ff0000","jump_url":"javascript:bad"},{"text":"提示二"}]""",
        ))).toString()
        val result = MessageContentParser.parse(18, raw)
        assertTrue(result.systemNotice)
        assertEquals("对方回复前只能发一条\n提示二", result.text)
        assertNull(result.card)
        val direct = JsonObject(mapOf("content" to JsonArray(listOf(JsonObject(mapOf("text" to JsonPrimitive("提示")))))))
        assertEquals("提示", MessageContentParser.parse(18, direct.toString()).text)
    }

    @Test
    fun `撤回卡片通知和系统提示都不残留原始内容或可点击目标`() {
        listOf(7, 10, 11, 12, 14, 18).forEach { type ->
            val result = MessageContentParser.parse(type, """{"title":"秘密","rid":42,"url":"https://space.bilibili.com/42"}""", 1)
            assertEquals("[已撤回的消息]", result.text)
            assertNull(result.card)
            assertNull(result.imageUrl)
            assertFalse(result.systemNotice)
        }
    }

    @Test
    fun `非法载荷未知类型和错误字段不输出原始JSON`() {
        listOf("not json", "[]", "null", "{broken", "a".repeat(100_001)).forEach { raw ->
            assertEquals("[分享内容无法显示]", MessageContentParser.parse(7, raw).text)
            assertEquals("[通知消息无法显示]", MessageContentParser.parse(10, raw).text)
            assertEquals("[系统提示暂无法显示]", MessageContentParser.parse(18, raw).text)
        }
        assertEquals("[其他类型的消息]", MessageContentParser.parse(99, """{"secret":"secret"}""").text)
        val wrongFields = MessageContentParser.parse(7, """{"source":5,"id":true,"bvid":[],"title":{},"thumb":{}}""")
        assertTrue(wrongFields.card!!.links.isEmpty())
        assertNull(wrongFields.card.cover)
        assertEquals("[文字消息无法显示]", MessageContentParser.parse(1, """{"content":42}""").text)
    }

    @Test
    fun `非法数字ID不进入路由且展示内容有长度上限`() {
        listOf("0", "-1", "1.5", "99999999999999999999999999999").forEach { id ->
            val card = MessageContentParser.parse(11, """{"rid":$id}""").card!!
            assertTrue(card.links.isEmpty())
        }
        val raw = JsonObject(mapOf("title" to JsonPrimitive("文".repeat(500)),
            "desc" to JsonPrimitive("文".repeat(5000)), "author" to JsonPrimitive("文".repeat(100))))
        val card = MessageContentParser.parse(14, raw.toString()).card!!
        assertEquals(200, card.title.length)
        assertEquals(2000, card.description.length)
        assertEquals(80, card.author.length)
    }

    @Test
    fun `卡片与通知链接不先清除控制字符从而绕过校验`() {
        listOf("\nhttps://space.bilibili.com/42", "https://space.bilibili.com/42\r", "\thttps://space.bilibili.com/42").forEach { link ->
            val share = JsonObject(mapOf("url" to JsonPrimitive(link)))
            val notification = JsonObject(mapOf("jump_uri" to JsonPrimitive(link)))
            val config = JsonObject(mapOf("jump_uri_config" to JsonObject(mapOf(
                "all_uri" to JsonPrimitive("bilibili://space/42"), "web_uri" to JsonPrimitive(link),
            ))))
            assertTrue(MessageContentParser.parse(14, share.toString()).card!!.links.isEmpty())
            assertTrue(MessageContentParser.parse(10, notification.toString()).card!!.links.isEmpty())
            assertTrue(MessageContentParser.parse(10, config.toString()).card!!.links.isEmpty())
        }
    }

    @Test
    fun `普通文字保持空格换行图片消息保持旧行为`() {
        val text = "  原文\n  下一行  "
        val raw = JsonObject(mapOf("content" to JsonPrimitive(text))).toString()
        assertEquals(text, MessageContentParser.parse(1, raw).text)
        val image = MessageContentParser.parse(2, """{"url":"//i0.hdslb.com/a.jpg"}""")
        assertEquals("[图片]", image.text)
        assertEquals("https://i0.hdslb.com/a.jpg", image.imageUrl)
    }
}
