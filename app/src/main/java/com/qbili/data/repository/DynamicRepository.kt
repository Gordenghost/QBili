package com.qbili.data.repository

import com.qbili.data.remote.api.DynamicApi
import com.qbili.data.remote.dto.requireData
import com.qbili.data.remote.dto.DynamicFeedDto
import com.qbili.domain.model.DynamicPost
import com.qbili.domain.model.OpusBlock
import com.qbili.domain.model.OpusDetail
import com.qbili.domain.model.VideoItem
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.TimeZone

class DynamicRepository(private val api: DynamicApi) {
    suspend fun following(offset: String? = null): DynamicPage {
        val timezoneOffset = -TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
        val data = api.following("all", timezoneOffset, OPUS_FEATURE, offset).requireData()
        return data.toPage(offset)
    }

    suspend fun space(mid: Long, offset: String? = null): DynamicPage {
        require(mid > 0)
        return api.space(mid, OPUS_FEATURE, offset).requireData().toPage(offset)
    }

    suspend fun opusDetail(id: String): OpusDetail {
        require(id.isNotBlank() && id.all(Char::isDigit))
        return api.opusDetail(id, "htmlNewStyle,$OPUS_FEATURE").requireData().toOpusDetail(id)
    }

    private suspend fun DynamicFeedDto.toPage(previousOffset: String?): DynamicPage {
        var hydrated = 0
        var detailAvailable = true
        val posts = items.orEmpty().mapNotNull { entry ->
            val post = entry.toPostOrNull() ?: return@mapNotNull null
            if (post.opusId == null || post.text.isNotBlank() || post.images.isNotEmpty() ||
                hydrated >= 3 || !detailAvailable) {
                return@mapNotNull post.withContentFallback()
            }
            hydrated++
            val detail = try {
                withTimeoutOrNull(8_000) { opusDetail(post.opusId) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: com.qbili.core.BiliApiException) {
                if (error.code == -799 || error.code == -509 || error.isRiskControl) detailAvailable = false
                null
            } catch (_: Exception) {
                null
            }
            post.copy(
                text = detail?.blocks?.filterIsInstance<OpusBlock.Paragraph>()
                    ?.joinToString("\n") { it.text }?.ifBlank { detail.title }
                    ?: detail?.title.orEmpty(),
                images = detail?.blocks?.filterIsInstance<OpusBlock.Picture>()
                    ?.map { it.url }.orEmpty(),
            ).withContentFallback()
        }
        return DynamicPage(
            posts = posts,
            next = offset?.takeIf { hasMore && it.isNotBlank() && it != previousOffset },
        )
    }

    data class DynamicPage(val posts: List<DynamicPost>, val next: String?)

    private companion object {
        const val OPUS_FEATURE = "itemOpusStyle"
    }
}

private fun DynamicPost.withContentFallback(): DynamicPost =
    if (opusId != null && text.isBlank() && images.isEmpty()) copy(text = "图文内容暂未返回，点击查看详情")
    else this

internal fun JsonElement.toPostOrNull(): DynamicPost? {
    val entry = this as? JsonObject ?: return null
    val id = entry.text("id_str")?.takeIf { it.isNotBlank() } ?: return null
    val author = entry.obj("modules")?.obj("module_author")
    val authorMid = author?.text("mid")?.toLongOrNull() ?: return null
    val ownContent = entry.obj("modules")?.obj("module_dynamic")
    val ownText = ownContent?.obj("desc")?.text("text").orEmpty()
    val original = entry.obj("orig")
    val originalContent = original?.obj("modules")?.obj("module_dynamic")
    val originalText = originalContent?.obj("desc")?.text("text").orEmpty()
    val major = (originalContent ?: ownContent)?.obj("major")
    val archive = major?.obj("archive")
    val bvid = archive?.text("bvid").orEmpty()
    val aid = archive?.text("aid")?.toLongOrNull() ?: 0
    val title = archive?.text("title").orEmpty()
    val opus = major?.obj("opus")
    val article = major?.obj("article")
    val articleId = article?.text("id")?.toLongOrNull()
        ?: Regex("""/read/cv(\d+)""").find(article?.text("jump_url").orEmpty())
            ?.groupValues?.get(1)?.toLongOrNull()
        ?: entry.obj("basic")?.takeIf { it.text("comment_type") == "12" }
            ?.text("comment_id_str")?.toLongOrNull()
    val images = (major?.obj("draw")?.array("items")?.mapNotNull { it.objOrNull()?.text("src") }
        ?: opus?.array("pics")?.mapNotNull { it.objOrNull()?.text("url") ?: it.objOrNull()?.text("src") }
        ?: article?.array("covers")?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull })
        .orEmpty().filter { it.isNotBlank() }
    val cover = archive?.text("cover") ?: images.firstOrNull().orEmpty()
    val video = if (bvid.isNotBlank() || aid > 0) VideoItem(
        aid = aid, bvid = bvid, title = title, cover = cover,
        authorMid = original?.obj("modules")?.obj("module_author")?.text("mid")?.toLongOrNull()
            ?: authorMid,
        authorName = original?.obj("modules")?.obj("module_author")?.text("name")
            ?: author.text("name").orEmpty(),
    ) else null
    return DynamicPost(
        id = id,
        authorMid = authorMid,
        authorName = author.text("name").orEmpty(),
        authorFace = author.text("face").orEmpty(),
        text = listOf(
            ownText.ifBlank { ownContent?.obj("major")?.obj("opus")?.obj("summary")?.text("text").orEmpty() },
            originalText.ifBlank { originalContent?.obj("major")?.obj("opus")?.obj("summary")?.text("text").orEmpty() },
        ).filter { it.isNotBlank() }.joinToString("\n")
            .ifBlank { opus?.text("title") ?: article?.text("title") ?: title },
        cover = cover,
        publishedAt = author.text("pub_ts")?.toLongOrNull() ?: 0,
        video = video,
        images = if (video == null) images else emptyList(),
        opusId = if (video == null && (opus != null || article != null ||
                entry.text("type") in listOf("DYNAMIC_TYPE_ARTICLE", "DYNAMIC_TYPE_DRAW"))) {
            opus?.text("jump_url")?.let { Regex("/opus/(\\d+)").find(it)?.groupValues?.get(1) } ?: id
        } else null,
        articleId = articleId?.takeIf { video == null && it > 0 },
    )
}

private fun JsonObject?.obj(name: String): JsonObject? = this?.get(name) as? JsonObject
private fun JsonElement?.objOrNull(): JsonObject? = this as? JsonObject
private fun JsonObject?.array(name: String) = this?.get(name) as? kotlinx.serialization.json.JsonArray
private fun JsonObject?.text(name: String): String? =
    (this?.get(name) as? JsonPrimitive)?.contentOrNull
