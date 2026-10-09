package com.qbili.ui.screen.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qbili.core.friendlyMessage
import com.qbili.data.repository.AppSmsSendResult
import com.qbili.data.repository.AuthRepository
import com.qbili.data.repository.SmsGeetestAnswer
import com.qbili.data.session.SessionManager
import com.qbili.di.AppContainer
import com.qbili.domain.model.CaptchaResult
import com.qbili.domain.model.CountryCode
import com.qbili.domain.model.QrLoginState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    enum class Tab(val label: String) {
        QR("扫码"),
        SMS("短信"),
        PASSWORD("密码"),
        COOKIE("Cookie"),
    }

    /**
     * 极验过了之后要接着干什么。
     * - PASSWORD_LOGIN：密码登录提交
     * - APP_SMS_SEND：重发短信验证码请求（App 接口风控命中时返回 recaptcha_url）
     */
    enum class CaptchaPurpose { PASSWORD_LOGIN, APP_SMS_SEND }

    data class PendingCaptcha(
        val token: String,
        val gt: String,
        val challenge: String,
        val purpose: CaptchaPurpose,
    )

    data class UiState(
        val tab: Tab = Tab.QR,
        val qr: QrLoginState = QrLoginState.Idle,
        val countryCode: CountryCode = CountryCode.CHINA,
        val phone: String = "",
        val smsCode: String = "",
        /** > 0 时显示倒计时，禁用「获取验证码」 */
        val smsCountdown: Int = 0,
        val captchaKey: String? = null,
        val username: String = "",
        val password: String = "",
        val cookieText: String = "",
        val busy: Boolean = false,
        val error: String? = null,
        val info: String? = null,
        val pendingCaptcha: PendingCaptcha? = null,
        val loggedIn: Boolean = false,
    ) {
        val canSendSms: Boolean get() = phone.length >= MIN_PHONE_LENGTH && smsCountdown == 0 && !busy
        val canSubmitSms: Boolean get() = captchaKey != null && smsCode.length >= 4 && !busy
        val canSubmitPassword: Boolean
            get() = username.isNotBlank() && password.isNotBlank() && !busy
        val canSubmitCookie: Boolean get() = cookieText.contains("SESSDATA") && !busy
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var qrJob: Job? = null
    private var countdownJob: Job? = null

    /**
     * 与 [UiState.pendingCaptcha] 分开保存。
     *
     * UiState 里那个只负责「要不要显示对话框」，而极验有可能先回调 onClose
     * （对话框被关掉、pendingCaptcha 置空）紧接着才回调 onSuccess。
     * 凭据要是跟着对话框一起清掉，验证结果就被静默丢弃了——
     * 表现就是「过了验证却什么都没发生」。
     */
    private var activeCaptcha: PendingCaptcha? = null

    /** 本轮发送已弹出的极验次数，防止「验证不通过 -> 重发」无限循环 */
    private var smsCaptchaAttempts = 0

    // ---------- Tab ----------

    fun selectTab(tab: Tab) {
        if (_uiState.value.tab == tab) return
        _uiState.update { it.copy(tab = tab, error = null, info = null) }
        if (tab == Tab.QR) {
            startQrLogin()
        } else {
            // 离开扫码页就停掉轮询，别在后台白打接口
            qrJob?.cancel()
            qrJob = null
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun dismissInfo() {
        _uiState.update { it.copy(info = null) }
    }

    // ---------- 扫码登录 ----------

    fun startQrLogin() {
        qrJob?.cancel()
        qrJob = viewModelScope.launch {
            _uiState.update { it.copy(qr = QrLoginState.Loading, error = null) }

            val (content, key) = try {
                authRepository.generateQrCode()
            } catch (e: Exception) {
                _uiState.update { it.copy(qr = QrLoginState.Failed(e.friendlyMessage())) }
                return@launch
            }

            _uiState.update { it.copy(qr = QrLoginState.Ready(content, key)) }

            var elapsed = 0L
            while (isActive && elapsed < QR_TIMEOUT_MILLIS) {
                delay(QR_POLL_INTERVAL_MILLIS)
                elapsed += QR_POLL_INTERVAL_MILLIS

                // 单次轮询失败（网络抖动）不该终止整个流程
                val poll = runCatching { authRepository.pollQrCode(key) }.getOrNull() ?: continue

                when (poll.code) {
                    QR_SUCCESS -> {
                        sessionManager.refresh()
                        _uiState.update { it.copy(qr = QrLoginState.Success, loggedIn = true) }
                        return@launch
                    }

                    QR_SCANNED_WAITING_CONFIRM ->
                        _uiState.update { it.copy(qr = QrLoginState.Scanned(content, key)) }

                    QR_EXPIRED -> {
                        _uiState.update { it.copy(qr = QrLoginState.Expired) }
                        return@launch
                    }

                    QR_NOT_SCANNED -> Unit // 继续等
                    else -> Unit
                }
            }
            // 循环超时也按失效处理，提示用户点刷新
            if (isActive) _uiState.update { it.copy(qr = QrLoginState.Expired) }
        }
    }

    // ---------- 短信登录 ----------

    fun onCountryCodeChange(code: CountryCode) = _uiState.update { it.copy(countryCode = code) }

    fun onPhoneChange(value: String) =
        _uiState.update { it.copy(phone = value.filter(Char::isDigit).take(20)) }

    fun onSmsCodeChange(value: String) =
        _uiState.update { it.copy(smsCode = value.filter(Char::isDigit).take(6)) }

    /**
     * 点「获取验证码」。
     *
     * 移动端接口两阶段流程——风控命中时第一次调用返回
     * recaptcha_url 而不是直接发短信，弹出极验后在 [onCaptchaSuccess] 里重发。
     */
    fun requestSmsCode() {
        val state = _uiState.value
        if (!state.canSendSms) return

        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null, info = null) }
            try {
                smsCaptchaAttempts = 0
                handleAppSmsResult(
                    authRepository.sendSmsCodeViaApp(state.countryCode.dialPrefix, state.phone),
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, error = e.friendlyMessage()) }
            }
        }
    }

    /**
     * 统一处理发送结果。注意服务端在验证未被接受时也会返回非空 captcha_key，
     * 真正的判据是是否携带新的 recaptcha_url——此时要再次弹出极验，
     * 直到通过（recaptcha_url 为空、短信真正下发）或超过尝试次数。
     */
    private fun handleAppSmsResult(result: AppSmsSendResult) {
        when (result) {
            is AppSmsSendResult.Sent -> {
                _uiState.update {
                    it.copy(busy = false, captchaKey = result.captchaKey, info = "验证码已发送")
                }
                startCountdown()
            }

            is AppSmsSendResult.NeedsCaptcha -> {
                smsCaptchaAttempts++
                if (smsCaptchaAttempts > MAX_SMS_CAPTCHA_ATTEMPTS) {
                    _uiState.update {
                        it.copy(
                            busy = false,
                            error = "连续 $smsCaptchaAttempts 次安全验证未通过，请稍后再试，" +
                                "或改用扫码 / Cookie 登录",
                        )
                    }
                    return
                }
                val pending = PendingCaptcha(
                    token = result.recaptchaToken.orEmpty(),
                    gt = result.gt,
                    challenge = result.challenge,
                    purpose = CaptchaPurpose.APP_SMS_SEND,
                )
                activeCaptcha = pending
                _uiState.update { it.copy(busy = false, pendingCaptcha = pending) }
            }
        }
    }

    fun submitSmsLogin() {
        val state = _uiState.value
        if (!state.canSubmitSms) return
        val captchaKey = state.captchaKey ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            try {
                authRepository.loginBySmsViaApp(
                    countryCode = state.countryCode.dialPrefix,
                    tel = state.phone,
                    code = state.smsCode,
                    captchaKey = captchaKey,
                )
                sessionManager.refresh()
                _uiState.update { it.copy(busy = false, loggedIn = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, error = e.friendlyMessage()) }
            }
        }
    }

    // ---------- 密码登录 ----------

    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value.trim()) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value) }

    fun submitPasswordLogin() {
        if (!_uiState.value.canSubmitPassword) return
        launchWithBusy(CaptchaPurpose.PASSWORD_LOGIN)
    }

    // ---------- 备用：Cookie 登录 ----------

    fun onCookieTextChange(value: String) = _uiState.update { it.copy(cookieText = value) }

    fun submitCookieLogin() {
        if (!_uiState.value.canSubmitCookie) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            try {
                authRepository.loginWithCookieString(_uiState.value.cookieText)
                sessionManager.refresh()
                _uiState.update { it.copy(busy = false, cookieText = "", loggedIn = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, error = e.friendlyMessage()) }
            }
        }
    }

    // ---------- 内嵌网页登录 ----------

    /**
     * 官方页登录成功（WebView Cookie 收割完成）后回调。
     * Cookie 已在收割时写入 CookieJar，这里只需刷新资料并通知 UI 返回。
     */
    // ---------- 极验回调 ----------

    fun onCaptchaSuccess(challenge: String, validate: String, seccode: String) {
        val pending = activeCaptcha
        if (pending == null) {
            // 绝不静默失败：宁可给出一句能复述的提示，也不要让用户对着没反应的界面猜
            _uiState.update {
                it.copy(
                    pendingCaptcha = null,
                    busy = false,
                    error = "安全验证已通过，但本地状态已丢失，请重新点击获取验证码",
                )
            }
            return
        }
        activeCaptcha = null
        _uiState.update { it.copy(pendingCaptcha = null, busy = true, error = null) }

        val captcha = CaptchaResult(
            token = pending.token,
            // 用极验回传的 challenge，它可能与请求时的不同
            challenge = challenge.ifBlank { pending.challenge },
            validate = validate,
            seccode = seccode,
        )

        viewModelScope.launch {
            try {
                when (pending.purpose) {
                    CaptchaPurpose.PASSWORD_LOGIN -> {
                        val state = _uiState.value
                        authRepository.loginByPassword(
                            username = state.username,
                            password = state.password,
                            captcha = captcha,
                        )
                        sessionManager.refresh()
                        _uiState.update { it.copy(busy = false, loggedIn = true) }
                    }

                    CaptchaPurpose.APP_SMS_SEND -> {
                        // 带着极验结果重发短信请求；若服务端仍不接受（返回新的
                        // recaptcha_url），handleAppSmsResult 会再次弹出验证
                        val state = _uiState.value
                        val result = authRepository.confirmSmsCodeViaApp(
                            countryCode = state.countryCode.dialPrefix,
                            tel = state.phone,
                            answer = SmsGeetestAnswer(
                                recaptchaToken = pending.token.takeIf { it.isNotBlank() },
                                challenge = captcha.challenge,
                                validate = captcha.validate,
                                seccode = captcha.seccode,
                            ),
                        )
                        handleAppSmsResult(result)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, error = e.friendlyMessage()) }
            }
        }
    }

    fun onCaptchaError(message: String) {
        _uiState.update { it.copy(pendingCaptcha = null, busy = false, error = message) }
    }

    fun onCaptchaDismiss() {
        // 刻意不清 activeCaptcha：onClose 可能先于 onSuccess 到达
        _uiState.update { it.copy(pendingCaptcha = null, busy = false) }
    }

    // ---------- 内部 ----------

    /** 取极验配置并挂起等待用户完成验证 */
    private fun launchWithBusy(purpose: CaptchaPurpose) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null, info = null) }
            try {
                val captcha = authRepository.fetchCaptcha()
                val geetest = captcha.geetest
                if (geetest == null) {
                    _uiState.update { it.copy(busy = false, error = "验证码配置异常，请稍后重试") }
                    return@launch
                }
                val pending = PendingCaptcha(
                    token = captcha.token,
                    gt = geetest.gt,
                    challenge = geetest.challenge,
                    purpose = purpose,
                )
                activeCaptcha = pending
                _uiState.update { it.copy(busy = false, pendingCaptcha = pending) }
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, error = e.friendlyMessage()) }
            }
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            _uiState.update { it.copy(smsCountdown = SMS_COUNTDOWN_SECONDS) }
            while (isActive && _uiState.value.smsCountdown > 0) {
                delay(1000)
                _uiState.update { it.copy(smsCountdown = (it.smsCountdown - 1).coerceAtLeast(0)) }
            }
        }
    }

    override fun onCleared() {
        qrJob?.cancel()
        countdownJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val MIN_PHONE_LENGTH = 6
        private const val SMS_COUNTDOWN_SECONDS = 60
        private const val QR_POLL_INTERVAL_MILLIS = 2_000L
        private const val QR_TIMEOUT_MILLIS = 180_000L
        private const val MAX_SMS_CAPTCHA_ATTEMPTS = 3

        // 轮询返回的业务状态码（外层 code 恒为 0）
        private const val QR_SUCCESS = 0
        private const val QR_EXPIRED = 86038
        private const val QR_SCANNED_WAITING_CONFIRM = 86090
        private const val QR_NOT_SCANNED = 86101

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LoginViewModel(container.authRepository, container.sessionManager) }
        }
    }
}
