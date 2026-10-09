package com.qbili.data.repository

import com.qbili.core.BiliApiException
import com.qbili.data.remote.api.ArticleApi
import com.qbili.data.remote.dto.requireData
import com.qbili.data.remote.dto.requireSuccess
import com.qbili.domain.model.ArticleDetail
import com.qbili.domain.model.ArticleReaction
import com.qbili.domain.model.renderArticleContent
import com.qbili.domain.model.renderArticleOpus
import com.qbili.domain.model.articleImageUrls
import com.qbili.core.QBiliLog

class ArticleRepository(private val api: ArticleApi) {
    suspend fun detail(id: Long): ArticleDetail {
        require(id > 0)
        val data = api.view(id, GAIA_SOURCE, referer(id)).requireData()
        val opusHtml = data.opus?.let(::renderArticleOpus).orEmpty()
        val html = opusHtml.ifBlank { if (data.content.isBlank()) "" else renderArticleContent(data.content) }
        if (html.isBlank()) throw BiliApiException(0, "专栏正文暂不可用")
        QBiliLog.i("ArticleImage", "专栏 id=$id 正文来源=${if (opusHtml.isNotBlank()) "opus" else "content"} 图片数=${articleImageUrls(html).size}")
        return ArticleDetail(
            id = id,
            title = data.title,
            authorName = data.author?.name.orEmpty(),
            authorMid = data.author?.mid ?: 0,
            authorFace = data.author?.face.orEmpty(),
            contentHtml = html,
            publishedAt = data.publishTime,
            viewCount = data.stats?.view ?: 0,
        )
    }

    suspend fun reaction(id: Long): ArticleReaction {
        val data = api.viewInfo(id, "pc", "web", GAIA_SOURCE, referer(id)).requireData()
        return ArticleReaction(
            liked = data.like != 0,
            favorited = data.favorite,
            likeCount = data.stats?.like ?: 0,
            favoriteCount = data.stats?.favorite ?: 0,
            commentCount = data.stats?.reply ?: 0,
        )
    }

    suspend fun setLike(id: Long, like: Boolean) {
        api.like(id, if (like) 1 else 2, referer(id)).requireSuccess()
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) {
        if (favorite) api.addFavorite(id, referer(id)).requireSuccess()
        else api.removeFavorite(id, referer(id)).requireSuccess()
    }

    private fun referer(id: Long) = "https://www.bilibili.com/read/cv$id/"

    private companion object {
        const val GAIA_SOURCE = "main_web"
    }
}
