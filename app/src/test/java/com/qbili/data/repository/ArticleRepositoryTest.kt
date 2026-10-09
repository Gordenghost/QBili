package com.qbili.data.repository

import com.qbili.core.BiliApiException
import com.qbili.data.remote.api.ArticleApi
import com.qbili.data.remote.dto.ArticleAuthorDto
import com.qbili.data.remote.dto.ArticleStatsDto
import com.qbili.data.remote.dto.ArticleViewDto
import com.qbili.data.remote.dto.ArticleViewInfoDto
import com.qbili.data.remote.dto.BiliResponse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleRepositoryTest {
    @Test
    fun `专栏优先读取有图片的 opus 而不是纯文本摘要`() = runBlocking {
        val api = FakeApi()
        api.contentResponse = BiliResponse(data = ArticleViewDto(
            id = 42, content = "无图摘要",
            opus = Json.parseToJsonElement("""{"content":{"paragraphs":[
                {"para_type":1,"text":{"nodes":[{"word":{"words":"普通正文","font_size":17}}]}},
                {"para_type":2,"pic":{"pics":[{"url":"//i0.hdslb.com/a.jpg"}]}}
            ]}}"""),
        ))
        val html = ArticleRepository(api).detail(42).contentHtml
        assertTrue(html.contains("<p>普通正文</p>"))
        assertTrue(html.contains("src='https://i0.hdslb.com/a.jpg'"))
        assertFalse(html.contains("无图摘要"))
    }

    private class FakeApi : ArticleApi {
        var contentResponse = BiliResponse(data = ArticleViewDto(
            id = 42, title = "文章", content = "<p>正文</p>",
            author = ArticleAuthorDto(mid = 23, name = "作者", face = "//face.jpg"),
            stats = ArticleStatsDto(view = 125),
        ))
        var infoResponse = BiliResponse(data = ArticleViewInfoDto(
            like = 0, favorite = true, stats = ArticleStatsDto(like = 10, favorite = 3, reply = 5),
        ))
        val likeTypes = mutableListOf<Int>()
        val favoriteActions = mutableListOf<String>()

        override suspend fun view(id: Long, gaiaSource: String, referer: String): BiliResponse<ArticleViewDto> {
            assertEquals(42L, id)
            assertEquals("main_web", gaiaSource)
            assertEquals("https://www.bilibili.com/read/cv42/", referer)
            return contentResponse
        }

        override suspend fun viewInfo(id: Long, mobiApp: String, from: String,
            gaiaSource: String, referer: String): BiliResponse<ArticleViewInfoDto> {
            assertEquals(42L, id)
            assertEquals("pc", mobiApp)
            assertEquals("web", from)
            assertEquals("main_web", gaiaSource)
            assertEquals("https://www.bilibili.com/read/cv42/", referer)
            return infoResponse
        }

        override suspend fun like(id: Long, type: Int, referer: String): BiliResponse<JsonElement> {
            assertEquals(42L, id)
            assertEquals("https://www.bilibili.com/read/cv42/", referer)
            likeTypes += type
            return BiliResponse(code = 0)
        }

        override suspend fun addFavorite(id: Long, referer: String): BiliResponse<JsonElement> {
            assertEquals(42L, id)
            assertEquals("https://www.bilibili.com/read/cv42/", referer)
            favoriteActions += "add"
            return BiliResponse(code = 0)
        }

        override suspend fun removeFavorite(id: Long, referer: String): BiliResponse<JsonElement> {
            assertEquals(42L, id)
            assertEquals("https://www.bilibili.com/read/cv42/", referer)
            favoriteActions += "remove"
            return BiliResponse(code = 0)
        }
    }

    @Test
    fun `文章详情和互动状态按 cv 号加载`() = runBlocking {
        val repository = ArticleRepository(FakeApi())
        val detail = repository.detail(42)
        assertEquals("文章", detail.title)
        assertEquals("<p>正文</p>", detail.contentHtml)
        assertEquals(23L, detail.authorMid)
        assertEquals("//face.jpg", detail.authorFace)
        assertEquals(125L, detail.viewCount)
        val reaction = repository.reaction(42)
        assertFalse(reaction.liked)
        assertTrue(reaction.favorited)
        assertEquals(5L, reaction.commentCount)
        assertEquals(11L, reaction.withLike(true).likeCount)
        assertEquals(2L, reaction.withFavorite(false).favoriteCount)
    }

    @Test
    fun `新版专栏富文本在进入WebView前已变为带图片的HTML`() = runBlocking {
        val api = FakeApi()
        api.contentResponse = BiliResponse(data = ArticleViewDto(
            id = 42,
            content = """{"ops":[{"insert":"标题"},{"attributes":{"header":2},"insert":"\n"},
                {"insert":{"native-image":{"url":"//i0.hdslb.com/bfs/new_dyn/a.jpg"}}}]}""",
        ))

        val detail = ArticleRepository(api).detail(42)
        assertTrue(detail.contentHtml.contains("<h2>标题</h2>"))
        assertTrue(detail.contentHtml.contains("src='https://i0.hdslb.com/bfs/new_dyn/a.jpg'"))
        assertFalse(detail.contentHtml.contains("ops"))
    }

    @Test
    fun `点赞取消点赞收藏取消收藏使用对应参数`() = runBlocking {
        val api = FakeApi()
        val repository = ArticleRepository(api)
        repository.setLike(42, true)
        repository.setLike(42, false)
        repository.setFavorite(42, true)
        repository.setFavorite(42, false)
        assertEquals(listOf(1, 2), api.likeTypes)
        assertEquals(listOf("add", "remove"), api.favoriteActions)
    }

    @Test
    fun `空正文和缺失 data 不能伪装成加载成功`() {
        val api = FakeApi()
        api.contentResponse = BiliResponse(data = ArticleViewDto(id = 42, content = ""))
        assertThrows(BiliApiException::class.java) { runBlocking { ArticleRepository(api).detail(42) } }
        api.contentResponse = BiliResponse(code = 0, data = null)
        assertThrows(BiliApiException::class.java) { runBlocking { ArticleRepository(api).detail(42) } }
        api.infoResponse = BiliResponse(code = 0, data = null)
        assertThrows(BiliApiException::class.java) { runBlocking { ArticleRepository(api).reaction(42) } }
    }

    @Test
    fun `文章接口的真实字段可以反序列化`() {
        val json = Json { ignoreUnknownKeys = true }
        val view = json.decodeFromString<BiliResponse<ArticleViewDto>>(
            """{"code":0,"data":{"id":42,"title":"测试","content":"<p>正文</p>",
            "author":{"mid":23,"name":"作者"},"stats":{"view":125}}}""",
        )
        val info = json.decodeFromString<BiliResponse<ArticleViewInfoDto>>(
            """{"code":0,"data":{"like":1,"favorite":true,
            "stats":{"like":10,"favorite":3,"reply":5}}}""",
        )
        assertEquals("<p>正文</p>", view.data?.content)
        assertEquals(23L, view.data?.author?.mid)
        assertTrue(info.data!!.favorite)
        assertEquals(5L, info.data.stats?.reply)
    }
}
