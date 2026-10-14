package com.qbili.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryOpenTargetTest {
    private val item = WatchHistoryItem(99, "archive", 42, "BV1Test", 123, 2, "第二部分",
        "标题", "", "作者", 100, 32_000, 60, false)

    @Test
    fun `普通视频和PGC保留既有续播资源`() {
        assertEquals(HistoryOpenTarget.Video, item.openTarget())
        assertEquals(HistoryOpenTarget.Video, item.copy(bvid = "").openTarget())
        assertEquals(HistoryOpenTarget.Season, item.copy(business = "pgc", episodeId = 7).openTarget())
        assertEquals(VideoResume(123, 2, 32_000), item.resume)
        assertEquals(HistoryOpenTarget.Unsupported, item.copy(aid = 0, bvid = "").openTarget())
        assertEquals(HistoryOpenTarget.Unsupported, item.copy(bvid = "BV1/../../settings").openTarget())
        assertEquals(HistoryOpenTarget.Unsupported, item.copy(business = "pgc", episodeId = 0).openTarget())
    }

    @Test
    fun `专栏使用OID而文集使用CID不把删除KID当文章ID`() {
        assertEquals(HistoryOpenTarget.Article(42), item.copy(business = "article").openTarget())
        assertEquals(HistoryOpenTarget.Article(123), item.copy(business = "article-list").openTarget())
        assertEquals(HistoryOpenTarget.Unsupported, item.copy(business = "article-list", cid = 0).openTarget())
        assertEquals(HistoryOpenTarget.Article(7), item.copy(business = "article-list", cid = 0,
            sourceUri = "//www.bilibili.com/read/cv7/").openTarget())
        assertEquals(HistoryOpenTarget.Article(42), item.copy(business = "article",
            sourceUri = "https://www.bilibili.com/read/cv999").openTarget())
    }

    @Test
    fun `直播使用房间OID不是删除键且可从可信链接回退`() {
        assertEquals(HistoryOpenTarget.Live(42), item.copy(business = "live").openTarget())
        assertEquals(HistoryOpenTarget.Live(777), item.copy(business = "live", aid = 0,
            sourceUri = "https://live.bilibili.com/777?live_from=88001").openTarget())
        assertEquals(HistoryOpenTarget.Unsupported, item.copy(business = "live", aid = 0).openTarget())
    }

    @Test
    fun `图文ID保留字符串精度而普通动态不猜测OID`() {
        val id = "999999999999999999999999"
        assertEquals(HistoryOpenTarget.Opus(id), item.copy(business = "opus",
            sourceUri = "https://www.bilibili.com/opus/$id").openTarget())
        assertEquals(HistoryOpenTarget.Opus("42"), item.copy(business = "opus").openTarget())
        assertEquals(HistoryOpenTarget.Opus("555"), item.copy(business = "dynamic",
            sourceUri = "https://www.bilibili.com/opus/555").openTarget())
        assertEquals(HistoryOpenTarget.Unsupported, item.copy(business = "dynamic").openTarget())
    }

    @Test
    fun `未知业务仅认可精确站内阅读或直播链接`() {
        assertEquals(HistoryOpenTarget.Article(42), item.copy(business = "future_type",
            sourceUri = "https://www.bilibili.com/read/cv42").openTarget())
        assertEquals(HistoryOpenTarget.Opus("42"), item.copy(business = "future_type",
            sourceUri = "//www.bilibili.com/opus/42").openTarget())
        assertEquals(HistoryOpenTarget.Unsupported, item.copy(business = "cheese",
            sourceUri = "https://www.bilibili.com/cheese/play/ep42").openTarget())
        assertEquals(HistoryOpenTarget.Unsupported, item.copy(business = "future_type").openTarget())
    }

    @Test
    fun `可信链接允许协议相对移动域名查询片段和标准端口`() {
        assertEquals(HistoryOpenTarget.Article(42), historyLinkTarget("//www.bilibili.com/read/cv42?from=history#comments"))
        assertEquals(HistoryOpenTarget.Article(42), historyLinkTarget("https://m.bilibili.com/read/cv42/"))
        assertEquals(HistoryOpenTarget.Opus("42"), historyLinkTarget("http://bilibili.com:80/opus/42"))
        assertEquals(HistoryOpenTarget.Live(42), historyLinkTarget("https://live.bilibili.com:443/42"))
        assertEquals(HistoryOpenTarget.Live(42), historyLinkTarget("https://m.live.bilibili.com/42/"))
    }

    @Test
    fun `拒绝外域伪造域名用户信息非HTTP协议及非标准端口`() {
        listOf("https://example.com/read/cv42", "https://www.bilibili.com.evil.test/read/cv42",
            "https://www.bilibili.com@evil.test/read/cv42", "https://user@www.bilibili.com/read/cv42",
            "javascript:alert(1)", "file:///read/cv42", "intent://www.bilibili.com/read/cv42",
            "https://www.bilibili.com:444/read/cv42", "http://www.bilibili.com:443/read/cv42",
            "https://www.bilibili.com/read/cv42\n/99", "\nhttps://www.bilibili.com/read/cv42",
            "https://www.bilibili.com/read/cv42\r").forEach { source ->
            assertEquals(source, HistoryOpenTarget.Unsupported, historyLinkTarget(source))
        }
    }

    @Test
    fun `拒绝无效ID溢出百分号编码与路径注入`() {
        listOf("https://www.bilibili.com/read/cv0", "https://www.bilibili.com/read/cv-42",
            "https://www.bilibili.com/read/cv999999999999999999999999999999",
            "https://www.bilibili.com/read/cv42/more", "https://www.bilibili.com/read/cv42%2Fmore",
            "https://www.bilibili.com/opus/%34%32", "https://www.bilibili.com/opus/42/../43",
            "https://live.bilibili.com/0", "https://live.bilibili.com/42/extra", "not a url").forEach { source ->
            assertEquals(source, HistoryOpenTarget.Unsupported, historyLinkTarget(source))
        }
    }

    @Test
    fun `阅读直播标签不显示伪造的零秒视频进度`() {
        assertEquals("视频", item.contentLabel)
        assertEquals("番剧／影视", item.copy(business = "pgc", episodeId = 1).contentLabel)
        assertEquals("专栏", item.copy(business = "article").contentLabel)
        assertEquals("图文", item.copy(business = "opus").contentLabel)
        assertEquals("直播", item.copy(business = "live").contentLabel)
        assertEquals("其他内容", item.copy(business = "unknown").contentLabel)
    }
}
