package com.qbili.domain.model

import com.qbili.data.remote.dto.ArticleViewDto
import com.qbili.data.remote.dto.BiliResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleOpusTest {
    @Test
    fun `新版专栏从 opus 的段落图片与字体信息生成正文`() {
        val response = Json { ignoreUnknownKeys = true }.decodeFromString<BiliResponse<ArticleViewDto>>(
            """{"code":0,"data":{"id":42,"content":"图片\n正文","opus":{"content":{"paragraphs":[
                {"para_type":1,"text":{"nodes":[{"word":{"words":"新番标题","font_level":"xLarge","style":{"bold":true}}}]}},
                {"para_type":2,"pic":{"pics":[{"url":"//i0.hdslb.com/bfs/new_dyn/example.png"}]}},
                {"para_type":6,"format":{"list_format":{"level":1,"order":1}},"text":{"nodes":[{"word":{"words":"正文内容","font_level":"regular","style":{}}}]}}
            ]}}}}""",
        )
        val html = renderArticleOpus(response.data!!.opus!!)
        assertTrue(html.contains("<h2><strong>新番标题</strong></h2>"))
        assertTrue(html.contains("<img src='https://i0.hdslb.com/bfs/new_dyn/example.png'"))
        assertTrue(html.contains("<li>正文内容</li>"))
        assertEquals(listOf("https://i0.hdslb.com/bfs/new_dyn/example.png"), articleImageUrls(html))
        assertFalse(html.contains("图片\n正文"))
    }

    @Test
    fun `普通文字类型不代表标题且居中与加粗独立保留`() {
        val opus = Json.parseToJsonElement("""{"content":{"paragraphs":[
            {"para_type":1,"align":1,"text":{"nodes":[
                {"word":{"words":"今天是","font_size":17,"font_level":"regular","style":{}}},
                {"word":{"words":"周四","font_size":17,"style":{"bold":true}}}
            ]}},
            {"para_type":1,"align":1,"text":{"nodes":[
                {"word":{"words":"新番来了","font_size":22,"font_level":"xLarge","style":{"bold":true}}}
            ]}}
        ]}}""")
        val html = renderArticleOpus(opus)
        assertTrue(html.contains("<p style='text-align:center'>今天是<strong>周四</strong></p>"))
        assertTrue(html.contains("<h2 style='text-align:center'><strong>新番来了</strong></h2>"))
        assertFalse(html.contains("<h2>今天是"))
    }

    @Test
    fun `没有列表格式的文字不会变成列表且非法图片地址不输出`() {
        val opus = Json.parseToJsonElement("""{"content":{"paragraphs":[
            {"para_type":6,"text":{"nodes":[{"word":{"words":"<script>&正文","font_size":17}}]}},
            {"para_type":2,"pic":{"pics":[{"url":"javascript:alert(1)"},{"url":"http://i0.hdslb.com/a.jpg"}]}}
        ]}}""")
        val html = renderArticleOpus(opus)
        assertTrue(html.contains("<p>&lt;script&gt;&amp;正文</p>"))
        assertFalse(html.contains("<li>"))
        assertFalse(html.contains("javascript:"))
        assertEquals(listOf("https://i0.hdslb.com/a.jpg"), articleImageUrls(html))
    }
}
