package com.qbili.data.repository

import android.util.Log
import com.qbili.core.BiliApiException
import com.qbili.core.QBiliLog
import com.qbili.core.crypto.RsaEncryptor
import com.qbili.core.normalizeUrl
import com.qbili.data.local.AccessTokenStore
import com.qbili.data.local.DeviceIdStore
import com.qbili.data.remote.ApiConstants
import com.qbili.data.remote.BuvidPayload
import com.qbili.data.remote.api.AccountApi
import com.qbili.data.remote.api.AppPassportApi
import com.qbili.data.remote.api.PassportApi
import com.qbili.data.remote.cookie.BiliCookieJar
import com.qbili.data.remote.dto.AppCookieInfoDto
import com.qbili.data.remote.dto.CaptchaDto
import com.qbili.data.remote.dto.ExClimbWuzhiBody
import com.qbili.data.remote.dto.QrCodePollDto
import com.qbili.data.remote.dto.requireData
import com.qbili.domain.model.CaptchaResult
import com.qbili.domain.model.UserProfile
import kotlinx.coroutines.flow.StateFlow
import java.net.URLEncoder
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class AuthRepository(
    private val passportApi: PassportApi,
    private val appPassportApi: AppPassportApi,
    private val accountApi: AccountApi,
    private val cookieJar: BiliCookieJar,
    /** App 接口用的设备标识来源（见 DeviceIdStore），与网页版 Cookie buvid3 是两套东西 */
    private val deviceIdStore: DeviceIdStore,
    /** 移动端登录才会下发 access_key，少数只有 App 端的接口（如视频点踩）要用 */
    private val accessTokenStore: AccessTokenStore,
) {

    val loginState: StateFlow<com.qbili.data.remote.cookie.LoginCookies> get() = cookieJar.loginState

    /** App 短信「发送 -> 登录」两步必须携带同一个会话标识 */
    private var smsLoginSessionId: String = ""

    /**
     * 用真实 buvid 覆盖启动时合成的占位值。
     * 失败不影响使用，占位 buvid 大多数接口也能过。
     */
    suspend fun ensureRealBuvid() {
        runCatching {
            val spi = accountApi.fingerSpi().requireData()
            if (spi.buvid3.isNotBlank()) {
                cookieJar.putRaw(ApiConstants.Cookie.BUVID3, spi.buvid3)
            }
            if (spi.buvid4.isNotBlank()) {
                cookieJar.putRaw("buvid4", spi.buvid4)
            }
        }.onFailure { Log.w(TAG, "获取真实 buvid 失败，继续使用占位值", it) }

        activateBuvid()
    }

    /**
     * 把 buvid 登记进 Gaia 风控系统。
     *
     * 这一步的影响比看起来大：**未激活的 buvid3 会让搜索接口频繁返回
     * `v_voucher` 风控挑战**（实测激活后原本必被拦的多词搜索立刻恢复正常）。
     * 每次安装成功一次即可，失败不阻塞启动。
     */
    private suspend fun activateBuvid() {
        if (deviceIdStore.isBuvidActivated()) return
        runCatching {
            val response = accountApi.activateBuvid(
                ExClimbWuzhiBody(payload = BuvidPayload.build()),
            )
            if (response.isSuccess) {
                deviceIdStore.markBuvidActivated()
                QBiliLog.i(TAG, "buvid 激活成功")
            } else {
                QBiliLog.w(TAG, "buvid 激活被拒: code=${response.code} ${response.errorMessage}")
            }
        }.onFailure { QBiliLog.w(TAG, "buvid 激活请求失败: ${it.message}") }
    }

    /** 未登录返回 null（接口会以 code=-101 表示未登录，不视为错误） */
    suspend fun fetchProfile(): UserProfile? {
        val response = accountApi.nav()
        val data = response.data
        if (!response.isSuccess || data == null || !data.isLogin) return null
        return UserProfile(
            mid = data.mid,
            name = data.uname,
            avatar = normalizeUrl(data.face),
            level = data.levelInfo?.currentLevel ?: 0,
            coins = data.money,
            bcoin = data.wallet?.bcoinBalance ?: 0.0,
            isVip = data.vipStatus == 1,
        )
    }

    // ---------- 扫码登录 ----------

    /** @return 二维码内容 to qrcode_key */
    suspend fun generateQrCode(): Pair<String, String> {
        val data = passportApi.generateQrCode(source = SOURCE).requireData()
        if (data.url.isBlank() || data.qrcodeKey.isBlank()) {
            throw BiliApiException(-1, "二维码生成失败，返回内容为空")
        }
        return data.url to data.qrcodeKey
    }

    /**
     * 轮询扫码状态。登录成功时凭据由 Set-Cookie 下发，CookieJar 已自动落库。
     * 注意外层 code 恒为 0，真正的状态码在返回值的 [QrCodePollDto.code]。
     */
    suspend fun pollQrCode(qrcodeKey: String): QrCodePollDto =
        passportApi.pollQrCode(qrcodeKey = qrcodeKey, source = SOURCE).requireData()

    // ---------- 验证码 ----------

    suspend fun fetchCaptcha(): CaptchaDto {
        val data = passportApi.captcha(source = SOURCE).requireData()
        if (data.geetest?.gt.isNullOrBlank() || data.geetest?.challenge.isNullOrBlank()) {
            throw BiliApiException(-1, "验证码配置异常：未返回极验参数（type=${data.type}）")
        }
        return data
    }

    // ---------- 密码登录 ----------

    suspend fun loginByPassword(username: String, password: String, captcha: CaptchaResult) {
        val keyData = passportApi.passwordKey().requireData()
        if (keyData.key.isBlank()) throw BiliApiException(-1, "获取登录公钥失败")

        val encrypted = RsaEncryptor.encryptPassword(
            salt = keyData.hash,
            password = password,
            pemPublicKey = keyData.key,
        )

        val result = passportApi.loginByPassword(
            username = username,
            password = encrypted,
            keep = 0,
            token = captcha.token,
            challenge = captcha.challenge,
            validate = captcha.validate,
            seccode = captcha.seccode,
            source = SOURCE,
            goUrl = ApiConstants.WEB_ORIGIN,
        ).requireData()
        checkLoginResult(result.status, result.message, result.url)
    }

    // ---------- 移动端短信登录（推荐路径） ----------

    /**
     * 走移动端接口发送短信验证码。
     *
     * 不会返回 Web 端的 86103「请在官方 App 内完成登录」，但风控验证是
     * **两阶段**的：第一次调用返回 code=0 与 recaptcha_url（而不是直接发短信），
     * 解析其中的极验参数、用户完成验证后带 gee_* 结果重发同一接口，
     * 才会真正下发短信并返回 captcha_key。低风险设备/网络可能一次通过。
     *
     * @return 短信已下发（拿到 captcha_key），或要求先完成极验
     */
    suspend fun sendSmsCodeViaApp(countryCode: Int, tel: String): AppSmsSendResult =
        sendSmsCodeViaAppInternal(countryCode, tel, geetest = null)

    /**
     * 极验通过后重发短信。[answer] 的 challenge 必须与首次响应里的一致，
     * validate/seccode 来自极验回调；recaptchaToken 同样取自首次响应的 recaptcha_url。
     */
    suspend fun confirmSmsCodeViaApp(
        countryCode: Int,
        tel: String,
        answer: SmsGeetestAnswer,
    ): AppSmsSendResult = sendSmsCodeViaAppInternal(countryCode, tel, geetest = answer)

    /**
     * 提交短信验证码登录。
     *
     * 移动端登录的凭据在响应体里，不走 Set-Cookie，必须手动落库；
     * login_session_id 必须与此前成功发送短信的那次一致。
     */
    suspend fun loginBySmsViaApp(
        countryCode: Int,
        tel: String,
        code: String,
        captchaKey: String,
    ) {
        QBiliLog.i(TAG, "提交短信登录: ${maskTel(tel)} code长度=${code.length}")
        val buvid = deviceIdStore.getOrCreate()
        val result = appPassportApi.loginBySms(
            buvidHeader = buvid,
            countryCode = countryCode,
            tel = tel,
            code = code,
            captchaKey = captchaKey,
            buvid = buvid,
            localId = buvid,
            biliLocalId = deviceIdStore.getOrCreateLoginDeviceId(),
            deviceId = deviceIdStore.getOrCreateLoginDeviceId(),
            device = LOGIN_DEVICE,
            deviceName = LOGIN_DEVICE_NAME,
            devicePlatform = LOGIN_DEVICE_PLATFORM,
            deviceToken = buildDeviceToken(),
            fromPv = LOGIN_FROM_PV,
            fromUrl = LOGIN_FROM_URL,
            statistics = SMS_STATISTICS,
        ).requireData()

        if (result.status != 0) {
            QBiliLog.w(TAG, "短信登录被拒: status=${result.status} msg=${result.message ?: result.hint}")
            checkLoginResult(result.status, result.message ?: result.hint, result.url)
            return
        }

        applyAppCookies(result.cookieInfo)

        if (!cookieJar.loginState.value.isLoggedIn) {
            throw BiliApiException(-1, "登录成功但未能解析出凭据，请改用扫码或 Cookie 导入")
        }
        accessTokenStore.save(result.tokenInfo?.accessToken)
        QBiliLog.i(TAG, "短信登录成功")
    }

    private suspend fun sendSmsCodeViaAppInternal(
        countryCode: Int,
        tel: String,
        geetest: SmsGeetestAnswer?,
    ): AppSmsSendResult {
        // 官方客户端的派生方式：md5(buvid + 毫秒时间戳)，每次发送都重新生成
        val buvid = deviceIdStore.getOrCreate()
        smsLoginSessionId = DeviceIdStore.md5Hex(buvid + System.currentTimeMillis())

        QBiliLog.i(
            TAG,
            "请求发送短信: ${maskTel(tel)} cid=$countryCode buvid=${buvid.take(10)}…" +
                (geetest?.let { " 含极验结果(validate长度=${it.validate.length})" } ?: " 首次调用"),
        )

        val response = appPassportApi.sendSms(
            buvidHeader = buvid,
            countryCode = countryCode,
            tel = tel,
            loginSessionId = smsLoginSessionId,
            channel = SMS_CHANNEL,
            buvid = buvid,
            localId = buvid,
            statistics = SMS_STATISTICS,
            // 缺失时必须省略而不是传空串，否则服务端按无效凭据处理
            recaptchaToken = geetest?.recaptchaToken?.takeIf { it.isNotBlank() },
            geeChallenge = geetest?.challenge?.takeIf { it.isNotBlank() },
            geeValidate = geetest?.validate?.takeIf { it.isNotBlank() },
            geeSeccode = geetest?.seccode?.takeIf { it.isNotBlank() },
        )

        val data = when {
            // 成功与否只看 recaptcha_url：验证未被接受时服务端也会返回 captcha_key，
            // 把它当成功就会出现「倒计时在走、短信没发」的假成功
            response.isSuccess -> response.data ?: throw BiliApiException(
                response.code,
                "接口返回数据为空",
            )

            // 极验结果被拒绝（-105 等）时，data 里会带一份新的 recaptcha_url，
            // 继续走验证流程；没有则按普通接口错误抛出
            response.data?.needsCaptcha == true -> response.data

            else -> {
                QBiliLog.w(TAG, "发送失败: code=${response.code} msg=${response.errorMessage}")
                throw BiliApiException(response.code, response.errorMessage)
            }
        }

        return when {
            data.needsCaptcha -> parseRecaptcha(data.recaptchaUrl.orEmpty())
                ?.also {
                    QBiliLog.w(
                        TAG,
                        "服务端要求极验: gt=${it.gt.take(8)}… challenge=${it.challenge.take(12)}… " +
                            "token存在=${!it.recaptchaToken.isNullOrBlank()}（第 ${if (geetest == null) 1 else 2} 次要求）",
                    )
                }
                ?: run {
                    QBiliLog.w(TAG, "recaptcha_url 无法解析: ${data.recaptchaUrl?.take(120)}")
                    throw BiliApiException(
                        -1,
                        "B 站要求安全验证但返回的验证参数无法解析，请改用扫码或 Cookie 登录",
                    )
                }

            data.captchaKey.isNotBlank() -> {
                QBiliLog.i(TAG, "短信已下发, captcha_key长度=${data.captchaKey.length}")
                AppSmsSendResult.Sent(data.captchaKey)
            }

            else -> {
                QBiliLog.w(TAG, "响应无 captcha_key 也无 recaptcha_url")
                throw BiliApiException(
                    -1,
                    "短信已下发但未返回登录凭据，请稍后重试或改用扫码登录",
                )
            }
        }
    }

    /**
     * 生成 `dt`（设备令牌）：用登录公钥 RSA 加密一段随机串再 URL 编码。
     *
     * 它是风控字段而非认证字段，所以取不到公钥时宁可省略也不要让登录整体失败——
     * 缺它最坏情况是风控评分低一点，缺整个登录流程就是功能不可用。
     */
    private suspend fun buildDeviceToken(): String? = runCatching {
        val key = passportApi.passwordKey().requireData().key
        if (key.isBlank()) return@runCatching null
        val random = (1..16)
            .map { RANDOM_ALPHABET.random() }
            .joinToString("")
        URLEncoder.encode(RsaEncryptor.encrypt(random, key), "UTF-8")
    }.onFailure { QBiliLog.w(TAG, "生成 dt 失败，本次登录省略该字段: ${it.message}") }
        .getOrNull()

    /** 日志里手机号打码，只留能对上号的头尾 */
    private fun maskTel(tel: String): String =
        if (tel.length >= 7) "${tel.take(3)}****${tel.takeLast(4)}" else "tel(${tel.length}位)"

    /** 从 recaptcha_url 的 query 里取极验参数（字段名兼容 gee_ 前缀与裸名两种形态） */
    private fun parseRecaptcha(rawUrl: String): AppSmsSendResult.NeedsCaptcha? {
        val url = rawUrl.toHttpUrlOrNull() ?: return null
        val token = url.queryParameter("recaptcha_token")?.takeIf { it.isNotBlank() }
        val gt = url.queryParameter("gee_gt") ?: url.queryParameter("gt") ?: return null
        val challenge = url.queryParameter("gee_challenge")
            ?: url.queryParameter("challenge")
            ?: return null
        if (gt.isBlank() || challenge.isBlank()) return null
        return AppSmsSendResult.NeedsCaptcha(
            recaptchaToken = token,
            gt = gt,
            challenge = challenge,
        )
    }

    private fun applyAppCookies(info: AppCookieInfoDto?) {
        val cookies = info?.cookies.orEmpty()
        if (cookies.isEmpty()) {
            throw BiliApiException(-1, "接口未返回登录凭据（cookie_info 为空）")
        }
        for (cookie in cookies) {
            if (cookie.name.isNotBlank() && cookie.value.isNotBlank()) {
                cookieJar.putRaw(cookie.name, cookie.value)
            }
        }
    }

    // ---------- 备用：直接导入浏览器 Cookie ----------

    /**
     * 从浏览器复制的 Cookie 串登录。
     *
     * 存在的意义是兜底：极验或短信通道走不通时，用户仍然能把账号用起来，
     * 不至于让登录卡住后面所有需要登录态的功能。
     *
     * 只解析登录必需的三项，其余一律忽略；解析后立刻打一次 nav 验证有效性，
     * 避免存进去一堆无效 Cookie 却显示已登录。
     */
    suspend fun loginWithCookieString(raw: String): UserProfile {
        val pairs = raw.split(';').mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            part.substring(0, separator).trim() to part.substring(separator + 1).trim()
        }.toMap()

        val sessData = pairs[ApiConstants.Cookie.SESSDATA]
        val mid = pairs[ApiConstants.Cookie.DEDE_USER_ID]
        val biliJct = pairs[ApiConstants.Cookie.BILI_JCT]

        if (sessData.isNullOrBlank() || mid.isNullOrBlank()) {
            throw BiliApiException(
                -1,
                "Cookie 里缺少 SESSDATA 或 DedeUserID，请确认复制的是完整的一整行",
            )
        }
        if (biliJct.isNullOrBlank()) {
            throw BiliApiException(
                -1,
                "Cookie 里缺少 bili_jct，缺了它无法点赞/投币/发评论，请复制完整的 Cookie",
            )
        }

        cookieJar.putRaw(ApiConstants.Cookie.SESSDATA, sessData)
        cookieJar.putRaw(ApiConstants.Cookie.DEDE_USER_ID, mid)
        cookieJar.putRaw(ApiConstants.Cookie.BILI_JCT, biliJct)
        pairs[ApiConstants.Cookie.BUVID3]?.takeIf { it.isNotBlank() }
            ?.let { cookieJar.putRaw(ApiConstants.Cookie.BUVID3, it) }

        return fetchProfile() ?: run {
            cookieJar.clear()
            throw BiliApiException(-1, "这份 Cookie 无效或已过期，请重新从浏览器复制")
        }
    }

    // ---------- 退出 ----------

    suspend fun logout() {
        val csrf = cookieJar.csrf()
        if (!csrf.isNullOrBlank()) {
            // 服务端登出是尽力而为：失败也要清掉本地凭据，否则用户点了退出却还是登录态
            runCatching { passportApi.logout(csrf = csrf, goUrl = ApiConstants.WEB_ORIGIN).close() }
                .onFailure { Log.w(TAG, "服务端登出失败，仅清除本地凭据", it) }
        }
        cookieJar.clear()
        accessTokenStore.clear()
    }

    /**
     * status: 0 成功 / 1 账号未注册 / 2 需要二次验证
     *
     * 二次验证（异地登录、风险登录）需要在官方端完成，第三方客户端无法代做，
     * 这里把提示原样抛给用户，让用户知道该去哪解决。
     */
    private fun checkLoginResult(status: Int, message: String?, url: String?) {
        when (status) {
            0 -> {
                if (!cookieJar.loginState.value.isLoggedIn) {
                    throw BiliApiException(-1, "接口返回登录成功但未下发凭据，请重试")
                }
            }
            2 -> throw BiliApiException(
                2,
                buildString {
                    append(message?.takeIf { it.isNotBlank() } ?: "需要安全验证")
                    append("。请先在官方 App 或网页端完成验证后再登录")
                    if (!url.isNullOrBlank()) append("\n验证地址：$url")
                },
            )
            else -> throw BiliApiException(
                status,
                message?.takeIf { it.isNotBlank() } ?: "登录失败（status=$status）",
            )
        }
    }

    private companion object {
        const val TAG = "AuthRepository"
        const val SOURCE = "main_web"

        /**
         * 短信登录走 HD 版身份（见 AppApi 的标记头说明），channel/statistics
         * 必须与 AppSigner.HD_COMMON_PARAMS、ANDROID_HD 保持同一套端标识，
         * 否则签名参数自相矛盾，风控会按异常流量处理。
         */
        const val SMS_CHANNEL = "master"

        /** version 需与 build=2001100 对应；作为整体字符串参与签名 */
        const val SMS_STATISTICS =
            """{"appId":5,"platform":3,"version":"2.0.1","abtest":""}"""
        const val LOGIN_DEVICE = "phone"
        const val LOGIN_DEVICE_NAME = "vivo"
        const val LOGIN_DEVICE_PLATFORM = "Android14vivo"
        const val LOGIN_FROM_PV = "main.my-information.my-login.0.click"
        const val LOGIN_FROM_URL = "bilibili%3A%2F%2Fuser_center%2Fmine"
        const val RANDOM_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    }
}

/** App 端发送短信验证码的结果。 */
sealed interface AppSmsSendResult {
    /** 短信已下发，用 [captchaKey] 提交登录即可 */
    data class Sent(val captchaKey: String) : AppSmsSendResult

    /**
     * 服务端要求先完成极验。三个值都取自响应的 recaptcha_url：
     * 完成验证后调 [AuthRepository.confirmSmsCodeViaApp] 重发。
     * [recaptchaToken] 在部分响应里不存在，为 null 时重发必须省略该参数。
     */
    data class NeedsCaptcha(
        val recaptchaToken: String?,
        val gt: String,
        val challenge: String,
    ) : AppSmsSendResult
}

/** 极验通过后的回传结果，用于重发短信验证码请求 */
data class SmsGeetestAnswer(
    val recaptchaToken: String?,
    val challenge: String,
    val validate: String,
    val seccode: String,
)
