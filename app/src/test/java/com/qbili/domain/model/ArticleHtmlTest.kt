package com.qbili.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleHtmlTest {
    @Test
    fun `HTTP 图床地址转为完整 HTTPS 地址`() {
        val content = "<img src='http://i0.hdslb.com/a.jpg'>"
        assertEquals(listOf("https://i0.hdslb.com/a.jpg"), articleImageUrls(content))
        assertTrue(articleDocument("标题", "作者", content, "#000", "#fff")
            .contains("src='https://i0.hdslb.com/a.jpg'"))
    }

    @Test
    fun `真实图床地址直接加载且标题字号明确约束`() {
        val html = articleDocument("标题", "作者", "<img src='//i0.hdslb.com/banner.jpg'>", "#000", "#fff")
        assertTrue(html.contains("src='https://i0.hdslb.com/banner.jpg'"))
        assertFalse(html.contains("qbili.local"))
        assertTrue(html.contains("h1 { font-size: 20px;"))
        assertTrue(html.contains("h2 { font-size: 20px;"))
    }

    @Test
    fun `标题转义且正文结构保留`() {
        val html = articleDocument("标题<&", "作者<&", "<p>加粗 <strong>内容</strong></p>",
            "#123456", "#FFFFFF")

        assertTrue(html.contains("<h1>标题&lt;&amp;</h1>"))
        assertTrue(html.contains("<p>作者&lt;&amp;</p>"))
        assertTrue(html.contains("<p>加粗 <strong>内容</strong></p>"))
        assertTrue(html.contains("background: #FFFFFF"))
    }

    @Test
    fun `懒加载图片改为安全的 HTTPS 图片`() {
        val html = articleDocument("图文", "作者",
            "<img data-src='//i0.hdslb.com/a.jpg' src='placeholder.jpg'><img src='https://i0.hdslb.com/b.jpg'>",
            "#000000", "#FFFFFF")

        assertTrue(html.contains("src='https://i0.hdslb.com/a.jpg'"))
        assertTrue(html.contains("src='https://i0.hdslb.com/b.jpg'"))
        assertFalse(html.contains("src='placeholder.jpg'"))
    }

    @Test
    fun `无懒加载属性的协议相对图片也转换为 HTTPS`() {
        val html = articleDocument("图文", "作者", """<p><img src="//i0.hdslb.com/bfs/article/a.jpg" alt="图片"></p>""",
            "#000000", "#FFFFFF")

        assertTrue(html.contains("src='https://i0.hdslb.com/bfs/article/a.jpg'"))
        assertTrue(html.contains("""alt="图片""""))
        assertFalse(html.contains("""src="//i0.hdslb.com""""))
    }

    @Test
    fun `专栏图片预取后内嵌以避开网页图床加载失败`() {
        val content = """<figure><img data-src="//article.biliimg.com/bfs/article/a.png" alt="图片"></figure>"""
        val url = "https://article.biliimg.com/bfs/article/a.png"
        assertTrue(articleImageUrls(content).contains(url))

        val html = articleDocument("文章", "作者", content, "#000000", "#FFFFFF",
            mapOf(url to "data:image/png;base64,AQID"))
        assertTrue(html.contains("src='data:image/png;base64,AQID'"))
        assertTrue(html.contains("loading='eager'"))
        assertFalse(html.contains("src='//article.biliimg.com"))
    }

    @Test
    fun `未加引号的懒加载图片也能被预取`() {
        val html = "<img src=//i0.hdslb.com/bfs/article/b.jpg>"
        assertTrue(articleImageUrls(html).contains("https://i0.hdslb.com/bfs/article/b.jpg"))
        assertTrue(articleDocument("文章", "作者", html, "#000", "#FFF")
            .contains("src='https://i0.hdslb.com/bfs/article/b.jpg'"))
    }

    @Test
    fun `完整长文每张图均可交给图床代理且不被 srcset 抢走`() {
        val content = (0..14).joinToString("") { index ->
            "<img src='//i0.hdslb.com/bfs/article/$index.jpg' srcset='//i0.hdslb.com/other.jpg 2x'>"
        }
        val images = articleImageUrls(content)
        assertEquals(15, images.size)
        val proxies = images.mapIndexed { index, url -> url to "https://qbili.local/article-image/$index" }.toMap()
        val html = articleDocument("标题", "作者", content, "#000", "#fff", proxies)
        assertTrue(html.contains("src='https://qbili.local/article-image/14'"))
        assertFalse(html.contains("srcset="))
    }
}
