package com.qbili.data.repository

import com.qbili.data.remote.api.RelatedVideoApi
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.domain.model.VideoItem
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

class RelatedVideoRepository(
    private val api: RelatedVideoApi,
    private val sessions: StateFlow<LoginCookies>,
    private val json: Json,
) {
    suspend fun load(session: LoginCookies, aid: Long, bvid: String): List<VideoItem> {
        require(aid > 0) { "当前视频标识无效" }
        validate(session)
        val values = if (session.isLoggedIn) listOf("SESSDATA" to session.sessData,
            "bili_jct" to session.biliJct, "DedeUserID" to session.mid.toString()) else emptyList()
        require(values.all { (_, value) -> value == null || value.none { it == ';' || it == '\r' || it == '\n' } }) {
            "登录信息无效，请重新登录"
        }
        val cookies = values.mapNotNull { (key, value) -> value?.let { "$key=$it" } }.joinToString("; ")
        val validBvid = bvid.takeIf { it.matches(Regex("BV[0-9A-Za-z]{10}")) }
        val response = api.related(cookies, validBvid, aid.takeIf { validBvid == null })
        validate(session)
        return response.videos(json, aid, validBvid.orEmpty())
    }

    private fun validate(session: LoginCookies) {
        check(sessions.value == session) { "账号已变化，请重新加载相关推荐" }
    }
}
