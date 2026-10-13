package com.qbili.data.repository

import com.qbili.data.remote.dto.OpusDetailDto
import com.qbili.domain.model.OpusBlock
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpusMapperTest {
    @Test
    fun `完整正文包含图片和格式且可移除外部打开入口`() {
        val payload = Json.decodeFromString<OpusDetailDto>("""{"item":{"modules":[
            {"module_content":{"paragraphs":[{"heading":{"nodes":[{"word":{"words":"完整标题"}}]}},{"pic":{"pics":[{"url":"//i0.hdslb.com/a.jpg"}]}}]}}
        ]}}""")
        val detail = payload.toOpusDetail("123")
        assertTrue(detail.contentComplete)
        assertTrue(detail.contentHtml.contains("<h2>完整标题</h2>"))
        assertTrue(detail.contentHtml.contains("https://i0.hdslb.com/a.jpg"))
    }

    @Test
    fun `未知正文结构不会声称已完整加载`() {
        val payload = Json.decodeFromString<OpusDetailDto>("""{"item":{"modules":[{"module_content":{"paragraphs":[{"para_type":99}]}}]}}""")
        assertFalse(payload.toOpusDetail("123").contentComplete)
    }

    @Test
    fun `图文详情从 basic 和模块读出真实评论号及互动状态`() {
        val payload = Json { ignoreUnknownKeys = true }.decodeFromString<OpusDetailDto>(
            """{"item":{"basic":{"comment_type":12,"comment_id_str":"42"},"modules":[
            {"module_title":{"text":"标题"}},
            {"module_author":{"mid":"7","name":"作者","face":"//face.jpg"}},
            {"module_content":{"paragraphs":[
                {"text":{"nodes":[{"word":{"words":"正文"}}]}},
                {"pic":{"pics":[{"url":"//image.jpg"}]}}]}},
            {"module_stat":{"comment":{"count":5},"like":{"count":10,"status":true},
                "favorite":{"count":3,"status":false}}}
            ]}}""",
        )
        val opus = payload.toOpusDetail("999")

        assertEquals("标题", opus.title)
        assertEquals("作者", opus.author)
        assertEquals(7L, opus.authorMid)
        assertEquals("//face.jpg", opus.authorFace)
        assertEquals(12, opus.commentType)
        assertEquals(42L, opus.commentOid)
        assertEquals(5L, opus.commentCount)
        assertTrue(opus.liked)
        assertFalse(opus.favorited)
        assertEquals(9L, opus.withLike(false).likeCount)
        assertEquals(4L, opus.withFavorite(true).favoriteCount)
        assertEquals(2, opus.blocks.size)
        assertTrue(opus.blocks.last() is OpusBlock.Picture)
    }
}
