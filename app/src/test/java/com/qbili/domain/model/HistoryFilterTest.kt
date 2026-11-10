package com.qbili.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HistoryFilterTest {
    private val video = WatchHistoryItem(1, "archive", 123, "BV1Test", 10, 2, "进阶教程",
        "Kotlin 入门", "", "Code UP", 300, 10_000, 60, false)
    private val season = video.copy(id = 2, business = "pgc", aid = 0, bvid = "", title = "番剧第二集",
        part = "第二话", author = "动画制作组", viewedAt = 200, episodeId = 2)
    private val article = video.copy(id = 3, business = "article", title = "图文教程", author = "画师", viewedAt = 100)
    private val items = listOf(video, season, article)

    @Test
    fun `空白关键词和全部类型恢复原列表`() {
        assertSame(items, items.filterHistory(" \t\n "))
        assertEquals(items, items.filterHistory("", HistoryContentType.ALL))
    }

    @Test
    fun `标题UP分P与BVAV号支持忽略大小写搜索`() {
        listOf(" kotlin ", "code up", "进阶", "bv1test", "AV123").forEach { query ->
            assertEquals(listOf(video), listOf(video, season).filterHistory(query))
        }
        assertEquals(listOf(season), items.filterHistory("第二话"))
        assertEquals(listOf(article), items.filterHistory("画师"))
    }

    @Test
    fun `多个关键词可跨字段组合且每个都必须匹配`() {
        assertEquals(listOf(video), items.filterHistory("kotlin\tUP  进阶"))
        assertEquals(emptyList<WatchHistoryItem>(), items.filterHistory("kotlin 制作组"))
        assertEquals(emptyList<WatchHistoryItem>(), items.filterHistory("不存在"))
    }

    @Test
    fun `内容类型与关键词同时生效`() {
        assertEquals(listOf(video), items.filterHistory("", HistoryContentType.VIDEO))
        assertEquals(listOf(season), items.filterHistory("", HistoryContentType.PGC))
        assertEquals(listOf(article), items.filterHistory("", HistoryContentType.ARTICLE))
        assertEquals(listOf(article), items.filterHistory("教程", HistoryContentType.ARTICLE))
        assertEquals(emptyList<WatchHistoryItem>(), items.filterHistory("kotlin", HistoryContentType.PGC))
    }

    @Test
    fun `未知历史类型保留在其他分类中而非丢弃`() {
        val live = video.copy(id = 4, business = "live", title = "直播回顾")
        val unknown = video.copy(id = 5, business = "future_type", title = "新内容")
        assertEquals(listOf(unknown), (items + live + unknown).filterHistory("", HistoryContentType.OTHER))
        assertEquals(listOf(live), (items + live + unknown).filterHistory("", HistoryContentType.LIVE))
    }

    @Test
    fun `文集图文和可信链接归入阅读分类且不会混入直播`() {
        val collection = article.copy(id = 4, business = "article-list", cid = 42)
        val opus = article.copy(id = 5, business = "opus", sourceUri = "https://www.bilibili.com/opus/555")
        val linked = article.copy(id = 6, business = "future_type", sourceUri = "https://www.bilibili.com/read/cv66")
        val live = article.copy(id = 7, business = "live", title = "直播")
        val all = items + collection + opus + linked + live
        assertEquals(listOf(article, collection, opus, linked), all.filterHistory("", HistoryContentType.ARTICLE))
        assertEquals(listOf(live), all.filterHistory("", HistoryContentType.LIVE))
        assertEquals(emptyList<WatchHistoryItem>(), all.filterHistory("", HistoryContentType.OTHER))
    }

    @Test
    fun `筛选不改变顺序资源键或原列表追加分页后继续匹配`() {
        val previous = items.toList()
        val olderVideo = video.copy(id = 4, aid = 456, viewedAt = 50)
        assertEquals(listOf(video, olderVideo), (items + olderVideo).filterHistory("Kotlin"))
        assertEquals("archive:1", items.filterHistory("Kotlin").single().key)
        assertEquals(previous, items)
        assertEquals(items, items.filterHistory(""))
    }

    @Test
    fun `本机账号已隔离的输入不会从搜索引入其他记录`() {
        val own = VideoProgress(42, 1, "BV1Own", 10, 1, "", "我的历史", "", "", 1000, 60_000, 300_000)
        val others = own.copy(accountMid = 43, aid = 2, title = "其他账号")
        val scoped = listOf(own, others).filter { it.accountMid == 42L }.map { it.toHistoryItem() }
        assertEquals(emptyList<WatchHistoryItem>(), scoped.filterHistory("其他账号"))
        assertEquals(listOf(own.toHistoryItem()), scoped.filterHistory("历史"))
    }
}
