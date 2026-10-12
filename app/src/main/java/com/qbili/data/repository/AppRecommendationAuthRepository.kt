package com.qbili.data.repository

import com.qbili.core.AppRecommendationAuthException
import com.qbili.core.BiliApiException
import com.qbili.data.local.AppAccessToken
import com.qbili.data.remote.api.AppAccountApi
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.data.remote.dto.requireData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppRecommendationAuthRepository(
    private val api: AppAccountApi,
    private val session: () -> LoginCookies,
    private val credential: () -> AppAccessToken?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var verifiedToken: String? = null
    private var verifiedSession: LoginCookies? = null
    private var validUntil = 0L

    suspend fun accessKey(): String? = mutex.withLock {
        val cookies = session()
        if (!cookies.isLoggedIn) return@withLock null
        val token = credential() ?: throw AppRecommendationAuthException(
            "当前登录方式没有 App 推荐凭据，请使用短信登录；也可以在设置中切回网页端推荐")
        if (token.isExpired(now())) throw AppRecommendationAuthException("App 登录凭据已过期，请重新短信登录")
        if (token.mid != null && token.mid != cookies.mid) throw AppRecommendationAuthException(
            "App 凭据与当前账号不一致，请重新短信登录，避免推荐到其他账号的内容")
        if (verifiedToken == token.value && verifiedSession == cookies && now() < validUntil) return@withLock token.value
        val status = try {
            api.tokenInfo(token.value).requireData()
        } catch (error: BiliApiException) {
            if (error.isNotLoggedIn) throw AppRecommendationAuthException("App 登录凭据无效或已过期，请重新短信登录")
            throw error
        }
        if (status.mid <= 0 || status.mid != cookies.mid) throw AppRecommendationAuthException(
            "App 登录身份校验失败，请重新短信登录；没有使用游客推荐替代账号推荐")
        if (status.expiresIn != null && status.expiresIn <= 0) throw AppRecommendationAuthException(
            "App 登录凭据已过期，请重新短信登录")
        check(session() == cookies && credential()?.value == token.value) { "登录账号已变化，请重新加载推荐" }
        verifiedToken = token.value
        verifiedSession = cookies
        validUntil = minOf(now() + 5 * 60_000, token.expiresAt ?: Long.MAX_VALUE,
            status.expiresIn?.let { now() + it * 1000 } ?: Long.MAX_VALUE)
        token.value
    }
}
