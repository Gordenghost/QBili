package com.qbili.data.repository

import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.Buffer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReaderImageDownloaderTest {
    @get:Rule val folder = TemporaryFolder()
    private val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 1, 2, 3, 4)

    @Test
    fun `下载保存原始字节及正确扩展名且不携带登录Cookie`() = runBlocking {
        var sent: Request? = null
        val client = OkHttpClient.Builder().cookieJar(object : CookieJar {
            override fun loadForRequest(url: HttpUrl) = listOf(Cookie.Builder().name("SESSDATA").value("secret").hostOnlyDomain(url.host).build())
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
        }).addInterceptor { chain ->
            sent = chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(png.toResponseBody("image/png".toMediaType())).build()
        }.build()
        val image = ReaderImageDownloader(folder.root, client).download("//i0.hdslb.com/image@720w.webp")
        assertArrayEquals(png, image.file.readBytes())
        assertEquals("image/png", image.format.mimeType)
        assertTrue(image.name.endsWith(".png"))
        assertNull(requireNotNull(sent).header("Cookie"))
        assertTrue(image.file.delete())
    }

    @Test
    fun `网络失败或HTML风控响应不保留损坏临时图片`() = runBlocking {
        listOf(403, 200, 302).forEach { code ->
            val directory = folder.newFolder("error$code")
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("response")
                    .body("<html>not an image</html>".toResponseBody("text/html".toMediaType())).build()
            }.build()
            try {
                ReaderImageDownloader(directory, client).download("https://i0.hdslb.com/a.jpg")
                fail("无效图片不能下载成功")
            } catch (error: Exception) { assertTrue(error.message != null) }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun `下载超过上限在读流前拒绝且清理临时文件`() = runBlocking {
        val directory = folder.newFolder("oversized")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(object : ResponseBody() {
                    override fun contentType() = "image/png".toMediaType()
                    override fun contentLength() = 51L * 1024 * 1024
                    override fun source(): BufferedSource = Buffer().write(png)
                }).build()
        }.build()
        try {
            ReaderImageDownloader(directory, client).download("https://i0.hdslb.com/large.png")
            fail("超过50MB不能保存")
        } catch (error: Exception) { assertTrue(error.message.orEmpty().contains("50MB")) }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
}
