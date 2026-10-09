package com.qbili.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleContentTest {
    @Test
    fun `HTTP 原生图片不会丢失协议斜线`() {
        val content = """{"ops":[{"insert":{"native-image":{"url":"http://i0.hdslb.com/a.jpg"}}}]}"""
        assertEquals(listOf("https://i0.hdslb.com/a.jpg"), articleImageUrls(renderArticleContent(content)))
    }

    @Test
    fun `新版专栏还原标题换行样式和原生图片`() {
        val content = """{"ops":[
            {"insert":"2026年10月新番导视·周四篇"},{"attributes":{"header":2,"align":"center"},"insert":"\n"},
            {"insert":"今天是周四\n假期结束了"},
            {"insert":{"native-image":{"alt":"图片","url":"//i0.hdslb.com/bfs/new_dyn/a.png"}}},
            {"insert":"\n"},{"attributes":{"bold":true},"insert":"正文"},{"insert":"\n"}
        ]}"""

        val html = renderArticleContent(content)
        assertTrue(html.contains("<h2 style='text-align:center'>2026年10月新番导视·周四篇</h2>"))
        assertTrue(html.contains("<p>今天是周四</p><p>假期结束了</p>"))
        assertTrue(html.contains("<figure><img src='https://i0.hdslb.com/bfs/new_dyn/a.png' alt='图片'></figure>"))
        assertTrue(html.contains("<p><strong>正文</strong></p>"))
        assertFalse(html.contains("<p><br></p><p><strong>正文"))
        assertEquals(listOf("https://i0.hdslb.com/bfs/new_dyn/a.png"), articleImageUrls(html))
    }

    @Test
    fun `标题及居中信息写在文字节点而非换行节点时仍保留排版`() {
        val content = """{"ops":[{"attributes":{"header":2,"align":"center","bold":true},"insert":"今日新番"},
            {"insert":"\n"},{"attributes":{"align":"center","size":"large","underline":true},"insert":"星期四"},
            {"insert":"\n"},{"insert":{"native-image":{"url":"https://i1.hdslb.com/bfs/article/one.jpg"}}},
            {"insert":{"native-image":{"url":"https://i1.hdslb.com/bfs/article/two.jpg"}}}]}"""
        val html = renderArticleContent(content)
        assertTrue(html.contains("<h2 style='text-align:center'><strong>今日新番</strong></h2>"))
        assertTrue(html.contains("<p style='text-align:center'>"))
        assertTrue(html.contains("font-size:1.4em"))
        assertTrue(html.contains("<u>星期四</u>"))
        assertEquals(2, articleImageUrls(html).size)
    }

    @Test
    fun `旧版HTML原样保留普通文本换行并转义`() {
        assertEquals("<p>旧版</p>", renderArticleContent("<p>旧版</p>"))
        assertEquals("<p>段落&amp;文字</p><p>第二行</p>", renderArticleContent("段落&文字\n第二行"))
    }

    @Test
    fun `不受支持的富文本不能当作正文直接输出`() {
        assertThrows(IllegalArgumentException::class.java) { renderArticleContent("""{"other":1}""") }
    }

    @Test
    fun `富文本链接不能执行脚本正文文字保留转义`() {
        val content = """{"ops":[{"attributes":{"link":"javascript:alert(1)"},"insert":"<script>"},
            {"insert":"\n"},{"insert":{"native-image":{"url":"javascript:alert(1)"}}}]}"""

        val html = renderArticleContent(content)
        assertTrue(html.contains("&lt;script&gt;"))
        assertFalse(html.contains("href='javascript:"))
        assertFalse(html.contains("src='javascript:"))
    }
}
