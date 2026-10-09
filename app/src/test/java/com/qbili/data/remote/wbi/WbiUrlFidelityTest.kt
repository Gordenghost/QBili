package com.qbili.data.remote.wbi

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

/**
 * 诊断用：把签名后真正会发出去的 URL 打印出来，
 * 确认 OkHttp 的 encodedQuery 有没有把我们签名时用的编码改掉。
 */
class WbiUrlFidelityTest {

    private val mixinKey = WbiSigner.mixinKey(
        "7cd084941338484aae1ad9425b84077c",
        "4932caff0ff746eab6f01bf08b70ac45",
    )

    @Test
    fun `打印含空格关键词签名后的 URL`() {
        val original = "https://api.bilibili.com/x/web-interface/wbi/search/type".toHttpUrl()
            .newBuilder()
            .addQueryParameter("search_type", "video")
            .addQueryParameter("keyword", "我的世界 苦力怕")
            .addQueryParameter("order", "totalrank")
            .addQueryParameter("duration", "0")
            .addQueryParameter("tids", "0")
            .addQueryParameter("page", "1")
            .addQueryParameter("page_size", "20")
            .build()

        println("== Retrofit 构造出的原始 URL ==")
        println(original)
        println("原始 keyword 解码值 = [${original.queryParameter("keyword")}]")

        val signed = WbiSigner.signUrl(original, mixinKey, wts = 1702204169L)

        println("== 签名后真正发出的 URL ==")
        println(signed)
        println("encodedQuery = ${signed.encodedQuery}")
        println("签名后 keyword 解码值 = [${signed.queryParameter("keyword")}]")
    }
}
