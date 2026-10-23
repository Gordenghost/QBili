package com.qbili.data.repository

import com.qbili.data.remote.api.CourseApi
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.domain.model.CourseEpisode
import com.qbili.domain.model.CourseTarget
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

class CourseRepository(private val api: CourseApi, private val sessions: StateFlow<LoginCookies>, private val json: Json) {
    suspend fun detail(session: LoginCookies, target: CourseTarget) = run {
        require(target.valid) { "请输入有效课程链接、ss课程ID或ep课时ID" }
        val response = api.detail(cookies(session), target.seasonId.takeIf { it > 0 }, target.episodeId.takeIf { it > 0 })
        validate(session)
        response.detail().also { detail ->
            check(target.seasonId == 0L || detail.id == target.seasonId) { "返回课程与请求不一致" }
            check(target.episodeId == 0L || detail.episodes.any { it.id == target.episodeId }) { "返回目录不包含指定课时，请在B站查看完整课程" }
        }
    }

    suspend fun playurl(session: LoginCookies, episode: CourseEpisode, quality: Int) = run {
        require(episode.id > 0 && episode.cid > 0 && episode.video && episode.playable) { episode.accessHint }
        require(quality in setOf(16, 32, 64, 80, 112, 116, 120, 125, 126, 127)) { "画质参数无效" }
        val response = api.playurl(cookies(session), episode.id, episode.cid, quality, 4048, 0, 1)
        validate(session)
        response.playback(json, episode)
    }

    private fun cookies(session: LoginCookies): String {
        validate(session)
        val values = if (session.isLoggedIn) listOf("SESSDATA" to session.sessData,
            "bili_jct" to session.biliJct, "DedeUserID" to session.mid.toString()) else emptyList()
        require(values.all { (_, value) -> value == null || value.none { it == ';' || it == '\r' || it == '\n' } }) { "登录信息无效，请重新登录" }
        return values.mapNotNull { (key, value) -> value?.let { "$key=$it" } }.joinToString("; ")
    }

    private fun validate(session: LoginCookies) { check(sessions.value == session) { "账号已变化，请重新加载课程" } }
}
