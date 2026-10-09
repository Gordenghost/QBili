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
