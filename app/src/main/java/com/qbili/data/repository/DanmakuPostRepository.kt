package com.qbili.data.repository

import com.qbili.data.remote.api.DanmakuPostApi
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.domain.model.DanmakuPost
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

class DanmakuPostRepository(
    private val api: DanmakuPostApi,
    private val sessions: StateFlow<LoginCookies>,
    private val nonce: () -> Long = { System.currentTimeMillis() * 1000 + Random.nextInt(1000) },
) {
    suspend fun send(session: LoginCookies, post: DanmakuPost): Long {
        validate(session)
        val request = post.validated()
        val csrf = requireNotNull(session.biliJct)
        val cookies = listOf("SESSDATA" to session.sessData, "bili_jct" to csrf, "DedeUserID" to session.mid.toString())
        require(cookies.all { (_, value) -> value != null && value.none { it == ';' || it == '\r' || it == '\n' } }) {
            "登录信息无效，请重新登录"
        }
        val result = api.send(cookies.joinToString("; ") { (key, value) -> "$key=$value" }, "1315873", csrf,
            1, request.target.cid, request.target.bvid, request.message, request.progressMillis, request.color,
            25, 0, request.mode.raw, nonce(), csrf)
        validate(session)
        return result.requireSent()
    }

    private fun validate(session: LoginCookies) {
        check(session.isLoggedIn && !session.biliJct.isNullOrBlank() && sessions.value == session) {
            "登录状态已变化或缺少 CSRF，请重新登录后发送"
        }
    }
}
