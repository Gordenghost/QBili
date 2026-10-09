package com.qbili.ui.screen.article

import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceResponse
import android.webkit.WebResourceError
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.qbili.domain.model.ArticleDetail
import com.qbili.domain.model.articleDocument
import com.qbili.domain.model.articleImageUrls
import com.qbili.core.QBiliLog
import com.qbili.ui.LocalAppContainer
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayInputStream
import java.io.FilterInputStream

@Composable
internal fun ArticleWebView(
    detail: ArticleDetail,
    onAuthorClick: (Long) -> Unit,
    onArticleClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageClient = LocalAppContainer.current.network.imageClient
    val images = remember(detail.contentHtml) { articleImageUrls(detail.contentHtml) }
    val currentImages by rememberUpdatedState(images.toSet())
    val currentArticleId by rememberUpdatedState(detail.id)
    val colors = MaterialTheme.colorScheme
    val document = remember(detail, colors.onSurface, colors.surface) {
        articleDocument(detail.title, detail.authorName, detail.contentHtml,
            colors.onSurface.css(), colors.surface.css())
    }
    AndroidView(
        factory = { host ->
            WebView(host).apply {
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.defaultTextEncodingName = "UTF-8"
                settings.textZoom = 100
                settings.loadsImagesAutomatically = true
                settings.blockNetworkImage = false
                settings.blockNetworkLoads = false
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                setBackgroundColor(colors.surface.toArgb())
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val uri = request.url
                        if (uri.scheme != "https") return null
                        val originalUrl = uri.toString()
                        if (originalUrl !in currentImages) return null
                        val imageHost = originalUrl.toHttpUrlOrNull()?.host.orEmpty()
                        if (!imageHost.endsWith(".hdslb.com") && !imageHost.endsWith(".biliimg.com")) return null
                        // 让 WebView 始终使用统一图片客户端逐张读取，避免预取大小上限和 data URI 兼容问题。
                        return try {
                            val response = imageClient.newCall(Request.Builder().url(originalUrl)
                                .header("Referer", "https://www.bilibili.com/read/cv$currentArticleId/")
                                .build()).execute()
                            val body = response.body
                            if (!response.isSuccessful || body == null) {
                                QBiliLog.w("ArticleImage", "加载失败：HTTP ${response.code}，${originalUrl.take(160)}")
                                response.close()
                                WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf()))
                            } else {
                                val stream = object : FilterInputStream(body.byteStream()) {
                                    override fun close() {
                                        try { super.close() } finally { response.close() }
                                    }
                                }
                                QBiliLog.i("ArticleImage", "加载成功：HTTP ${response.code} 类型=${body.contentType()} 字节=${body.contentLength()} ${originalUrl.take(120)}")
                                WebResourceResponse(body.contentType()?.let { "${it.type}/${it.subtype}" }
                                    ?: "image/jpeg", null, stream)
                            }
                        } catch (error: Exception) {
                            QBiliLog.w("ArticleImage", "加载异常：${originalUrl.take(160)}", error)
                            WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf()))
                        }
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        QBiliLog.w("ArticleImage", "WebView 错误=${error.errorCode} ${error.description} ${request.url.toString().take(160)}")
                    }

                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                        QBiliLog.w("ArticleImage", "WebView HTTP=${errorResponse.statusCode} ${request.url.toString().take(160)}")
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val uri = request.url
                        if (uri.scheme !in listOf("http", "https")) return true
                        val mid = if (uri.host == "space.bilibili.com")
                            uri.pathSegments.firstOrNull()?.toLongOrNull() else null
                        val articleId = if (uri.host == "www.bilibili.com")
                            Regex("""^/read/cv(\d+)/?""").find(uri.path.orEmpty())
                                ?.groupValues?.get(1)?.toLongOrNull() else null
                        when {
                            mid != null -> onAuthorClick(mid)
                            articleId != null -> onArticleClick(articleId)
                            else -> context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }
                        return true
                    }
                }
            }
        },
        update = { webView ->
            if (webView.tag != document) {
                webView.tag = document
                webView.loadDataWithBaseURL("https://www.bilibili.com/read/cv${detail.id}/",
                    document, "text/html", "UTF-8", null)
            }
        },
        onRelease = { webView -> webView.stopLoading(); webView.destroy() },
        modifier = modifier,
    )
}

private fun Color.css(): String = "#%06X".format(toArgb() and 0xFFFFFF)
