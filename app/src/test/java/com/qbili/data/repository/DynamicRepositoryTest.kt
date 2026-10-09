package com.qbili.data.repository

import com.qbili.core.BiliApiException
import com.qbili.data.remote.api.DynamicApi
import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.DynamicFeedDto
import com.qbili.data.remote.dto.OpusDetailDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DynamicRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true }

    private class FakeApi : DynamicApi {
        var response = BiliResponse<DynamicFeedDto>(data = DynamicFeedDto())
        var receivedOffset: String? = null
        var receivedMid: Long? = null
        var detailCalls = 0
        var detailResponse = BiliResponse<OpusDetailDto>(data = OpusDetailDto())

        override suspend fun following(type: String, timezoneOffset: Int, features: String,
            offset: String?): BiliResponse<DynamicFeedDto> {
            assertEquals("all", type)
            assertEquals("itemOpusStyle", features)
            assertEquals(-TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000, timezoneOffset)
            receivedOffset = offset
            return response
        }

        override suspend fun space(mid: Long, features: String, offset: String?): BiliResponse<DynamicFeedDto> {
            assertEquals("itemOpusStyle", features)
            receivedMid = mid
            receivedOffset = offset
            return response
        }

        override suspend fun opusDetail(id: String, features: String): BiliResponse<OpusDetailDto> {
            assertEquals("htmlNewStyle,itemOpusStyle", features)
            detailCalls++
            return detailResponse
        }
    }

    @Test
    fun `video and text dynamics preserve author and next offset`() = runBlocking {
        val api = FakeApi().apply { response = json.decodeFromString(
            """{"code":0,"data":{"has_more":true,"offset":"next-page","items":[
            {"id_str":"123","type":"DYNAMIC_TYPE_AV","modules":{
              "module_author":{"mid":42,"name":"UP主","face":"//face.jpg","pub_ts":100},
              "module_dynamic":{"desc":{"text":"投稿了"},"major":{"archive":
              {"aid":9,"bvid":"BV1test","title":"视频","cover":"//cover.jpg"}}}}},
            {"id_str":"124","type":"DYNAMIC_TYPE_WORD","modules":{
              "module_author":{"mid":43,"name":"另一位"},
              "module_dynamic":{"desc":{"text":"一条文字动态"}}}}]}}""",
        ) }
        val page = DynamicRepository(api).following()
        assertEquals(null, api.receivedOffset)
        assertEquals("next-page", page.next)
        assertEquals(2, page.posts.size)
        assertEquals("BV1test", page.posts.first().video?.key)
        assertEquals("投稿了", page.posts.first().text)
        assertEquals("一条文字动态", page.posts.last().text)
        assertNull(page.posts.last().video)

        val next = DynamicRepository(api).following("next-page")
        assertEquals("next-page", api.receivedOffset)
        assertNull(next.next)
    }

    @Test
    fun `unauthorized feed cannot be treated as empty`() {
        val api = FakeApi().apply { response = BiliResponse(code = -101) }
        assertThrows(BiliApiException::class.java) {
            runBlocking { DynamicRepository(api).following() }
        }
    }

    @Test
    fun `draw and opus dynamics retain all pictures without videos`() = runBlocking {
        val api = FakeApi().apply { response = json.decodeFromString(
            """{"code":0,"data":{"items":[
              {"id_str":"draw","modules":{"module_author":{"mid":42,"name":"画师"},
                "module_dynamic":{"desc":{"text":"画了图"},"major":{"type":"MAJOR_TYPE_DRAW",
                  "draw":{"items":[{"src":"//one.jpg"},{"src":"//two.jpg"}]}}}}},
              {"id_str":"opus","modules":{"module_author":{"mid":43,"name":"作者"},
                "module_dynamic":{"major":{"type":"MAJOR_TYPE_OPUS","opus":{
                  "summary":{"text":"图文动态"},"pics":[{"url":"//a.jpg"},{"url":"//b.jpg"}]}}}}}
            ]}}""",
        ) }
        val posts = DynamicRepository(api).following().posts
        assertEquals(2, posts.size)
        assertEquals(listOf("//one.jpg", "//two.jpg"), posts[0].images)
        assertEquals("画了图", posts[0].text)
        assertNull(posts[0].video)
        assertEquals(listOf("//a.jpg", "//b.jpg"), posts[1].images)
        assertEquals("图文动态", posts[1].text)
        assertNull(posts[1].video)
    }

    @Test
    fun `space feed shows picture only opus and paginates by offset`() = runBlocking {
        val api = FakeApi().apply { response = json.decodeFromString(
            """{"code":0,"data":{"has_more":true,"offset":"next","items":[
              {"id_str":"999","type":"DYNAMIC_TYPE_ARTICLE","modules":{
                "module_author":{"mid":42,"name":"程序员鱼皮"},
                "module_dynamic":{"major":{"type":"MAJOR_TYPE_OPUS","opus":{
                  "summary":{"text":"2万Star，我的AI编程教程被豆包推荐了！"},
                  "pics":[{"url":"//article-cover.jpg"}]}}}}}
            ]}}""",
        ) }
        val repository = DynamicRepository(api)
        val firstPage = repository.space(42)
        assertEquals(42L, api.receivedMid)
        assertEquals("next", firstPage.next)
        assertEquals("2万Star，我的AI编程教程被豆包推荐了！", firstPage.posts.single().text)
        assertEquals(listOf("//article-cover.jpg"), firstPage.posts.single().images)
        assertNull(firstPage.posts.single().video)
        val secondPage = repository.space(42, "next")
        assertEquals("next", api.receivedOffset)
        assertNull(secondPage.next)
    }

    @Test
    fun `space feed errors are not treated as empty posts`() {
        val api = FakeApi().apply { response = BiliResponse(code = -799) }
        assertThrows(BiliApiException::class.java) {
            runBlocking { DynamicRepository(api).space(42) }
        }
    }

    @Test
    fun `blank article is hydrated from opus detail rather than displaying empty card`() = runBlocking {
        val api = FakeApi().apply {
            response = json.decodeFromString("""{"data":{"items":[{"id_str":"999",
                "type":"DYNAMIC_TYPE_ARTICLE","modules":{"module_author":{"mid":42,"name":"画师"},
                "module_dynamic":{"major":{"type":"MAJOR_TYPE_NONE"}}}}]}}""")
            detailResponse = json.decodeFromString("""{"data":{"item":{"modules":[
                {"module_title":{"text":"图文标题"}},
                {"module_content":{"paragraphs":[{"text":{"nodes":[{"word":{"words":"正文"}}]}},
                {"pic":{"pics":[{"url":"//image.jpg"}]}}]}}]}}}""")
        }
        val post = DynamicRepository(api).space(42).posts.single()
        assertEquals("正文", post.text)
        assertEquals(listOf("//image.jpg"), post.images)
        assertEquals("999", post.opusId)
        assertEquals(1, api.detailCalls)
        assertEquals("图文标题", DynamicRepository(api).opusDetail("999").title)
    }

    @Test
    fun `blank article still provides a detail action if hydration is unavailable`() = runBlocking {
        val api = FakeApi().apply { response = json.decodeFromString(
            """{"data":{"items":[{"id_str":"123","type":"DYNAMIC_TYPE_ARTICLE",
            "modules":{"module_author":{"mid":42},"module_dynamic":{}}}]}}""",
        ) }
        val post = DynamicRepository(api).space(42).posts.single()
        assertEquals("图文内容暂未返回，点击查看详情", post.text)
        assertEquals("123", post.opusId)
    }

    @Test
    fun `article title and cover are retained from legacy major`() = runBlocking {
        val api = FakeApi().apply { response = json.decodeFromString(
            """{"data":{"items":[{"id_str":"123","type":"DYNAMIC_TYPE_ARTICLE",
            "modules":{"module_author":{"mid":42},"module_dynamic":{"major":{
            "article":{"title":"旧专栏","covers":["//cover.jpg"]}}}}}]}}""",
        ) }
        val post = DynamicRepository(api).space(42).posts.single()
        assertEquals("旧专栏", post.text)
        assertEquals(listOf("//cover.jpg"), post.images)
        assertEquals(0, api.detailCalls)
    }

    @Test
    fun `动态专栏使用 cv 号进入完整文章`() {
        val article = json.parseToJsonElement(
            """{"id_str":"123","type":"DYNAMIC_TYPE_ARTICLE","modules":{
            "module_author":{"mid":42},"module_dynamic":{"major":{
            "article":{"id":18440570,"title":"专栏"}}}}}""",
        ).toPostOrNull()
        assertEquals(18440570L, article?.articleId)

        val opusArticle = json.parseToJsonElement(
            """{"id_str":"124","type":"DYNAMIC_TYPE_ARTICLE",
            "basic":{"comment_type":12,"comment_id_str":"234"},
            "modules":{"module_author":{"mid":42},"module_dynamic":{"major":{
            "opus":{"summary":{"text":"正文"}}}}}}""",
        ).toPostOrNull()
        assertEquals(234L, opusArticle?.articleId)
    }
}
