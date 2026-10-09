package com.qbili.data.remote.dto

import com.qbili.domain.model.VideoInteraction
import com.qbili.data.repository.likeRequestValue
import com.qbili.data.repository.dislikeRequestValue
import com.qbili.domain.model.FavFolder
import com.qbili.domain.model.favoriteChanges
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoRelationDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `布尔关注和 coin 字段不阻断点赞状态解析`() {
        val data = json.decodeFromString<VideoRelationDto>(
            """{"like":true,"dislike":false,"favorite":false,"attention":true,"coin":1}""",
        )
        assertTrue(data.like)
        assertTrue(data.attention)
        assertEquals(1, data.coins)
    }

    @Test
    fun `旧版数字关注及 coin_number 仍兼容`() {
        val notFollowing = json.decodeFromString<VideoRelationDto>(
            """{"attention":-1,"coin_number":2}""",
        )
        val following = json.decodeFromString<VideoRelationDto>(
            """{"attention":0,"coin":0,"coin_number":1}""",
        )
        assertFalse(notFollowing.attention)
        assertEquals(2, notFollowing.coins)
        assertTrue(following.attention)
        assertEquals(1, following.coins)
    }

    @Test
    fun `成功点赞后状态立即更新且取消点赞可再次切换`() {
        val liked = VideoInteraction(disliked = true).withLike(true)
        assertTrue(liked.liked)
        assertFalse(liked.disliked)
        assertFalse(liked.withLike(false).liked)
        val disliked = liked.withDislike(true)
        assertTrue(disliked.disliked)
        assertFalse(disliked.liked)
        assertFalse(disliked.withDislike(false).disliked)
        assertEquals(1, likeRequestValue(true))
        assertEquals(2, likeRequestValue(false))
        assertEquals(0, dislikeRequestValue(true))
        assertEquals(1, dislikeRequestValue(false))
    }

    @Test
    fun `收藏仅对有变化的收藏夹提交完整 ID`() {
        val folders = listOf(
            FavFolder(12345, "原收藏夹", 1, false, true),
            FavFolder(67890, "新收藏夹", 0, false, false),
        )
        val change = favoriteChanges(folders, setOf(67890L, 11111L))
        assertEquals(listOf(67890L), change.addIds)
        assertEquals(listOf(12345L), change.removeIds)
    }
}
