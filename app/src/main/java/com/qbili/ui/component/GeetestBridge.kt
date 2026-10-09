package com.qbili.ui.component

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * 极验 JS 与原生层之间的桥。
 *
 * 刻意声明成**顶层命名类**而不是匿名 `object : Any() {}`：
 * WebView 的 `addJavascriptInterface` 是通过反射调用方法的，匿名类本身不是 public，
 * 在部分机型/ROM 上会导致 JS 侧调用静默失败——表现就是「验证通过了但原生毫无反应」，
 * 而且因为异常发生在 JS 侧，连日志都看不到。
 *
 * 方法名也刻意与回调参数名区分开（`success` vs `onSuccess`），
 * 避免方法体内调用同名 lambda 时解析到自身造成无限递归。
 */
class GeetestBridge(
    private val onBridgeAlive: () -> Unit,
    private val onReady: () -> Unit,
    private val onSuccess: (challenge: String, validate: String, seccode: String) -> Unit,
    private val onFailure: (String) -> Unit,
    private val onClosed: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    /** 页面脚本一开始就调用，用来确认桥确实通了 */
    @JavascriptInterface
    fun bridgeAlive() = post(onBridgeAlive)

    @JavascriptInterface
    fun ready() = post(onReady)

    @JavascriptInterface
    fun success(challenge: String, validate: String, seccode: String) =
        post { onSuccess(challenge, validate, seccode) }

    @JavascriptInterface
    fun failure(detail: String) = post { onFailure(detail) }

    @JavascriptInterface
    fun closed() = post(onClosed)

    /** JS 回调发生在 WebView 的 JS 线程，必须切回主线程再改 Compose 状态 */
    private fun post(block: () -> Unit) {
        main.post(block)
    }

    companion object {
        const val NAME = "QBiliGeetest"
    }
}
