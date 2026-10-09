package com.qbili.data.session

/**
 * 保存最近一次 Gaia 验证通过后拿到的凭据。
 *
 * 验证通过时服务端也会下发 Cookie（由 CookieJar 自动接管），
 * 但部分接口要求把凭据作为 `gaia_vtoken` 参数显式带上，所以这里额外留一份。
 */
class GaiaTokenStore {

    @Volatile
    var token: String? = null
        private set

    fun update(value: String?) {
        token = value?.takeIf { it.isNotBlank() }
    }

    fun clear() {
        token = null
    }
}
