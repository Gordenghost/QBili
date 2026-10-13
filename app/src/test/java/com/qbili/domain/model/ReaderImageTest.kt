package com.qbili.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderImageTest {
    @Test
    fun `图片地址只接受HTTPS B站图床且拒绝伪造域名或账号信息`() {
        assertEquals("https://i0.hdslb.com/bfs/test.png", readerImageUrl("//i0.hdslb.com/bfs/test.png"))
        assertEquals("https://article.biliimg.com/a.jpg", readerImageUrl("http://article.biliimg.com/a.jpg"))
        listOf("https://hdslb.com.evil.test/a.jpg", "https://evilhdslb.com/a.jpg", "file:///a.png",
            "javascript:alert(1)", "https://user:pass@i0.hdslb.com/a.jpg", "https://127.0.0.1/a.jpg").forEach {
            assertNull(readerImageUrl(it))
        }
    }

    @Test
    fun `图片格式由文件签名判定不依赖URL后缀`() {
        assertEquals("image/jpeg", readerImageFormat(byteArrayOf(-1, -40, -1))?.mimeType)
        assertEquals("png", readerImageFormat(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))?.extension)
        assertEquals("gif", readerImageFormat("GIF89a".toByteArray())?.extension)
        assertEquals("webp", readerImageFormat("RIFF1234WEBP".toByteArray())?.extension)
        assertNull(readerImageFormat("<html>风控响应</html>".toByteArray()))
        assertNull(readerImageFormat(byteArrayOf()))
    }

    @Test
    fun `预览路由仅解析合法图片索引不接受外部地址`() {
        assertEquals(2, readerImagePreviewIndex(readerImagePreviewLink(2), 3))
        assertNull(readerImagePreviewIndex(readerImagePreviewLink(3), 3))
        assertNull(readerImagePreviewIndex("https://evil.test/reader-image/0", 3))
        assertNull(readerImagePreviewIndex("https://qbili.invalid/reader-image/0?url=https://evil.test", 3))
        assertNull(readerImagePreviewIndex("https://qbili.invalid/reader-image/-1", 3))
        assertNull(readerImagePreviewIndex("https://user@qbili.invalid/reader-image/0", 3))
        assertNull(readerImagePreviewIndex("https://qbili.invalid:8080/reader-image/0", 3))
    }

    @Test
    fun `正文图片可点击预览表情保持内联并且每张图片索引正确`() {
        val content = "<p>正文<img class='reader-emoji' src='//i0.hdslb.com/doge.png'></p>" +
            "<figure><img data-src='//article.biliimg.com/a.png' src='placeholder'></figure>" +
            "<img src='https://i0.hdslb.com/b.jpg'>"
        assertEquals(3, articleImageUrls(content).size)
        assertEquals(listOf("https://article.biliimg.com/a.png", "https://i0.hdslb.com/b.jpg"), readerContentImageUrls(content))
        val document = articleDocument("标题", "作者", content, "#000", "#fff", imagePreview = true)
        assertTrue(document.contains("href='${readerImagePreviewLink(0)}'"))
        assertTrue(document.contains("href='${readerImagePreviewLink(1)}'"))
        assertFalse(document.contains("href='${readerImagePreviewLink(2)}'"))
        assertTrue(document.contains("src='https://article.biliimg.com/a.png'"))
        assertFalse(document.contains("javascript:"))
    }

    @Test
    fun `已有图片超链接替换为预览而不是嵌套锚点`() {
        val document = articleDocument("标题", "作者",
            "<a href='https://example.com'><img src='//i0.hdslb.com/a.jpg'></a>",
            "#000", "#fff", imagePreview = true)
        assertFalse(document.contains("href='https://example.com'"))
        assertTrue(document.contains("href='${readerImagePreviewLink(0)}'"))
    }
}
