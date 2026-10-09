package com.qbili.data.session

import android.webkit.CookieManager
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.data.repository.AuthRepository
import com.qbili.domain.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全局登录会话。
 *
 * Cookie 是登录态的唯一真相来源（见 BiliCookieJar.loginState），
 * 这里在其之上缓存一份用户资料，避免每个页面都去打 nav 接口。
 */
class SessionManager(
    private val authRepository: AuthRepository,
    private val scope: CoroutineScope,
) {

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    val loginCookies: StateFlow<LoginCookies> get() = authRepository.loginState

    /** 应用启动时调用一次 */
    fun bootstrap() {
        scope.launch {
            // 先拿到真实 buvid，再去打第一个业务接口，减少 -412
            authRepository.ensureRealBuvid()
            loginCookies
                .map { it.isLoggedIn to it.mid }
                .distinctUntilChanged()
                .collect { refresh() }
        }
    }

    suspend fun refresh() {
        _profile.value = runCatching { authRepository.fetchProfile() }.getOrNull()
    }

    /**
     * 退出登录。
     *
     * 跑在应用级 scope 而不是调用方的 scope：用户点完退出往往立刻离开页面，
     * 如果绑在页面 scope 上，协程会被取消，可能出现「点了退出但本地凭据没清」。
     */
    fun logout() {
        scope.launch {
            authRepository.logout()
            _profile.value = null

            // WebView（极验验证码）的 Cookie 存储独立于应用侧 CookieJar，
            // 退出时一并清掉，避免作废凭据残留在另一个存储里。
            withContext(Dispatchers.Main) {
                runCatching {
                    CookieManager.getInstance().apply {
                        removeAllCookies(null)
                        flush()
                    }
                }
            }
        }
    }
}
