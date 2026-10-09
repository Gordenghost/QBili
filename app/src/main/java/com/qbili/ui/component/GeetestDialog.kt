package com.qbili.ui.component

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qbili.core.QBiliLog
import com.qbili.data.remote.ApiConstants
import kotlinx.coroutines.delay

private const val TAG = "Geetest"

/** 桥建立的超时：只是几行同步 JS，超过这个时间基本就是没通 */
private const val BRIDGE_TIMEOUT_MILLIS = 5_000L

/** 极验组件就绪的超时：要从 static.geetest.com 拉 JS 和图片资源 */
private const val CAPTCHA_TIMEOUT_MILLIS = 20_000L

private val ALLOWED_HOSTS = listOf("geetest.com", "bilibili.com", "hdslb.com")

/**
 * 承载页的地址。请求会被 shouldInterceptRequest 本地拦下并返回内嵌 HTML，
 * 不会真的发到 B 站；用真实 https 地址只为了让页面拥有正常的 origin 与协议。
 */
private val hostPageUrl = ApiConstants.PASSPORT_BASE + "qbili-geetest-host"

/**
 * 极验的渲染形态。
 *
 * - [BIND]：不放内嵌按钮，就绪后直接调 verify() 让极验弹出自己的居中浮层。
 *   浮层尺寸由极验自己决定，不会被我们的容器裁掉，所以作为默认。
 * - [EMBED]：内嵌渲染。容器不够高时会把滑块图片的上半部分裁掉，
 *   留作备选是因为个别 WebView 内核下浮层模式可能不弹。
 */
private enum class ProductMode(val jsValue: String, val label: String) {
    BIND("bind", "浮层模式"),
    EMBED("embed", "内嵌模式"),
}

private sealed interface Phase {
    data object LoadingPage : Phase
    data object BridgeAlive : Phase
    data object CaptchaReady : Phase
    data object Submitting : Phase
    data class Failed(val detail: String) : Phase
}

/**
 * 极验 v3 验证码。登录（短信/密码）和搜索的 Gaia 风控都用它。
 *
 * 这个组件踩过三个坑，都在代码里留了防线：
 * 1. 匿名 object 做 JS 桥在部分 ROM 上反射调用静默失败 -> 改用命名类 [GeetestBridge]；
 * 2. 容器高度固定导致滑块图上半部分被裁掉、无法完成验证 -> 改用浮层模式 + 近全屏容器；
 * 3. 老版 gt.js 的部分图片走 http，被 WebView 的混合内容策略静默拦截，
 *    表现为「图片加载不全」-> 放开为 COMPATIBILITY（只放行图片等被动内容，脚本仍拦）。
 */
@Composable
fun GeetestDialog(
    gt: String,
    challenge: String,
    onSuccess: (challenge: String, validate: String, seccode: String) -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var phase by remember { mutableStateOf<Phase>(Phase.LoadingPage) }
    var mode by remember { mutableStateOf(ProductMode.BIND) }
    // 结果已回传后，geetest 自身的 close 回调和点击外部都不应再取消流程
    var delivered by remember { mutableStateOf(false) }

    val successCallback = rememberUpdatedState(onSuccess)
    val errorCallback = rememberUpdatedState(onError)
    val dismissCallback = rememberUpdatedState(onDismiss)

    val bridge = remember {
        GeetestBridge(
            onBridgeAlive = {
                Log.d(TAG, "JS 桥已建立")
                QBiliLog.i("Geetest", "JS 桥已建立")
                if (phase is Phase.LoadingPage) phase = Phase.BridgeAlive
            },
            onReady = {
                Log.d(TAG, "极验组件已就绪")
                QBiliLog.i("Geetest", "极验组件已就绪 (${mode.label})")
                if (phase !is Phase.Failed) phase = Phase.CaptchaReady
            },
            onSuccess = { c, v, s ->
                Log.d(TAG, "验证通过，回传结果 (validate 长度=${v.length})")
                QBiliLog.i("Geetest", "验证通过 challenge=${c.take(12)}… validate长度=${v.length}")
                delivered = true
                phase = Phase.Submitting
                successCallback.value.invoke(c, v, s)
            },
            onFailure = { detail ->
                Log.w(TAG, "极验失败: $detail")
                QBiliLog.w("Geetest", "极验失败: $detail")
                phase = Phase.Failed(detail)
            },
            onClosed = {
                Log.d(TAG, "极验面板关闭 (delivered=$delivered)")
                QBiliLog.i("Geetest", "极验面板关闭 (delivered=$delivered)")
                if (!delivered) dismissCallback.value.invoke()
            },
        )
    }

    // 桥超时：说明 addJavascriptInterface 没生效，或页面根本没加载起来
    LaunchedEffect(mode) {
        delay(BRIDGE_TIMEOUT_MILLIS)
        if (phase is Phase.LoadingPage) {
            phase = Phase.Failed("JS 桥未建立（${BRIDGE_TIMEOUT_MILLIS / 1000} 秒无响应）")
        }
    }

    // 组件超时：桥通了但 geetest 资源没加载出来
    LaunchedEffect(phase) {
        if (phase is Phase.BridgeAlive) {
            delay(CAPTCHA_TIMEOUT_MILLIS)
            if (phase is Phase.BridgeAlive) {
                phase = Phase.Failed("极验组件加载超时，可能无法访问 static.geetest.com")
            }
        }
    }

    Dialog(
        onDismissRequest = {
            // 正在回传结果时不允许被点掉，否则上层会收到 dismiss 而丢掉验证结果
            if (phase !is Phase.Submitting) onDismiss()
        },
        // 关掉平台默认宽度限制：极验浮层需要足够的空间，容器小了图就会被裁
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.88f),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Text("安全验证", style = MaterialTheme.typography.titleMedium)
                Text(
                    "图片显示不全时可双指缩放，或在下方切换渲染方式",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (phase is Phase.LoadingPage || phase is Phase.BridgeAlive) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    // mode 变化时必须重建 WebView，factory 不会因参数变化重跑
                    key(mode) {
                        GeetestWebView(
                            gt = gt,
                            challenge = challenge,
                            mode = mode,
                            bridge = bridge,
                            onConsoleError = { message ->
                                // JS 自己上报不了的错误（比如桥对象不存在）只能从 console 抓
                                if (phase !is Phase.Submitting && !delivered) {
                                    phase = Phase.Failed(message)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                StatusLine(phase, mode)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            mode = if (mode == ProductMode.BIND) ProductMode.EMBED else ProductMode.BIND
                            phase = Phase.LoadingPage
                        },
                        enabled = phase !is Phase.Submitting,
                    ) {
                        Text("换渲染方式")
                    }

                    val failed = phase as? Phase.Failed
                    if (failed != null) {
                        TextButton(onClick = { errorCallback.value.invoke(failed.detail) }) {
                            Text("换登录方式")
                        }
                    }

                    TextButton(
                        onClick = onDismiss,
                        enabled = phase !is Phase.Submitting,
                    ) {
                        Text("取消")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(phase: Phase, mode: ProductMode) {
    val (text, isError) = when (phase) {
        Phase.LoadingPage -> "正在加载验证组件…（${mode.label}）" to false
        Phase.BridgeAlive -> "正在初始化极验…（${mode.label}）" to false
        Phase.CaptchaReady -> "请完成验证（${mode.label}）" to false
        Phase.Submitting -> "验证通过，正在提交…" to false
        is Phase.Failed -> "验证组件异常：${phase.detail}" to true
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (phase is Phase.Submitting) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GeetestWebView(
    gt: String,
    challenge: String,
    mode: ProductMode,
    bridge: GeetestBridge,
    onConsoleError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val consoleErrorCallback = rememberUpdatedState(onConsoleError)
    val html = remember(gt, challenge, mode) { buildGeetestHtml(gt, challenge, mode) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false

                // 老版 gt.js 有部分图片资源走 http，默认策略会静默拦截，
                // 表现就是验证图片缺一块。COMPATIBILITY 只放行图片这类被动内容，脚本仍然拦。
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                // 让页面按视口自适应缩放，宁可整体缩小也不要把验证图裁掉
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true

                // 留一个逃生口：万一还是显示不全，用户可以双指放大看清缺口位置
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false

                setBackgroundColor(android.graphics.Color.TRANSPARENT)

                // 必须在 load 之前注入
                addJavascriptInterface(bridge, GeetestBridge.NAME)

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val host = request.url.host.orEmpty()
                        val allowed = ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }
                        if (!allowed) Log.w(TAG, "拦截非白名单跳转: $host")
                        return !allowed
                    }

                    /**
                     * 用拦截的方式从一个真实 https URL 提供承载页，而不是 loadDataWithBaseURL。
                     *
                     * loadDataWithBaseURL 造出来的文档在部分 WebView 内核里 origin 是不透明的，
                     * 会影响 gt.js 的 `location.protocol` 判断（判断错就会去拉 http 资源）
                     * 以及后续资源的 referer。走真实 URL 能把这一整类不确定性消掉，
                     * 而且请求被本地拦下来，并不会真的访问 B 站。
                     */
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        if (request.url.toString() != hostPageUrl) return null
                        return WebResourceResponse(
                            "text/html",
                            "utf-8",
                            html.byteInputStream(Charsets.UTF_8),
                        )
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        Log.d(
                            TAG,
                            "[JS ${message.messageLevel()}] ${message.message()} " +
                                "(行 ${message.lineNumber()})",
                        )
                        if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                            consoleErrorCallback.value.invoke(message.message())
                        }
                        return true
                    }
                }

                loadUrl(hostPageUrl)
            }
        },
        onRelease = { webView ->
            webView.removeJavascriptInterface(GeetestBridge.NAME)
            webView.destroy()
        },
    )
}

private fun buildGeetestHtml(gt: String, challenge: String, mode: ProductMode): String {
    val bridgeName = GeetestBridge.NAME
    // bind 模式没有内嵌按钮，需要就绪后主动 verify() 把浮层唤起来
    val afterReady = if (mode == ProductMode.BIND) "cap.verify();" else ""
    val appendCall = if (mode == ProductMode.EMBED) "cap.appendTo('#captcha');" else ""
    return """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <!-- 不加 user-scalable=no：显示不全时双指缩放是最后的逃生口 -->
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <style>
    html,body{margin:0;padding:0;background:transparent;height:100%;}
    #wrap{display:flex;align-items:center;justify-content:center;
          min-height:100%;padding:4px;overflow:visible;}
  </style>
</head>
<body>
  <div id="wrap"><div id="captcha"></div></div>
  <script>
    // 第一件事就是确认桥通了。桥不通时这里会抛 ReferenceError，
    // 由 window.onerror -> console.error 让原生侧的 WebChromeClient 抓到。
    $bridgeName.bridgeAlive();

    function fail(msg){
      var text = String(msg);
      console.error('[geetest] ' + text);
      try { $bridgeName.failure(text); } catch (e) {}
    }

    window.onerror = function(m, src, line){ fail(m + ' @' + line); return false; };

    function boot(){
      if (typeof initGeetest !== 'function') { fail('gt.js 未加载成功'); return; }
      initGeetest({
        gt: '${gt.jsEscape()}',
        challenge: '${challenge.jsEscape()}',
        offline: false,
        new_captcha: true,
        product: '${mode.jsValue}',
        width: '100%',
        lang: 'zh-cn'
      }, function(cap){
        $appendCall
        cap.onReady(function(){ $bridgeName.ready(); $afterReady });
        cap.onSuccess(function(){
          var r = cap.getValidate();
          if (!r || !r.geetest_validate) { fail('getValidate 返回空'); return; }
          $bridgeName.success(r.geetest_challenge, r.geetest_validate, r.geetest_seccode);
        });
        cap.onError(function(e){ fail('极验回调错误 ' + JSON.stringify(e)); });
        cap.onClose(function(){ $bridgeName.closed(); });
      });
    }

    var s = document.createElement('script');
    s.src = 'https://static.geetest.com/static/js/gt.0.4.9.js';
    s.onload = boot;
    s.onerror = function(){ fail('无法加载 static.geetest.com 的 gt.js'); };
    document.head.appendChild(s);
  </script>
</body>
</html>
    """.trimIndent()
}

/** gt/challenge 来自接口，理论上是十六进制串，这里仍做转义以防注入 */
private fun String.jsEscape(): String =
    replace("\\", "\\\\").replace("'", "\\'").replace("\n", "")
