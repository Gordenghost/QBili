package com.qbili.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 移动端 appkey + sign 签名的回归测试。
 *
 * 期望值由 Python 参考实现算出，编码规则与 Dart 的 `Uri.encodeComponent`
 * 逐字节对齐（空格 `%20`、不转义 `-_.!~*'()`、十六进制大写、空值只写 key）。
 * 签名一旦算错，所有移动端接口会统一失败，而服务端只回一个笼统的错误码，
 * 几乎无法从日志倒推，所以必须锁死。
 */
class AppSignerTest {

    private val ts = 1_700_000_000L
    private val hd = AppSigner.ANDROID_HD
    private val hdCommon = AppSigner.HD_COMMON_PARAMS

    @Test
    fun `短信发送参数的签名与参考实现一致`() {
        val query = AppSigner.signedQuery(
            params = mapOf("cid" to "1", "tel" to "13800000000"),
            credential = hd,
            ts = ts,
            commonParams = hdCommon,
        )

        assertEquals(
            "appkey=dfca71928277209b&build=2001100&c_locale=zh_CN&channel=master&cid=1" +
                "&disable_rcmd=0&mobi_app=android_hd&platform=android&s_locale=zh_CN" +
                "&tel=13800000000&ts=1700000000" +
                "&sign=faa51db6f471bb2bf26804ee641a3566",
            query,
        )
    }

    /** 空格必须编码成 %20；用 URLEncoder 会得到 `+`，签名随之作废 */
    @Test
    fun `含中文与空格的关键词按 encodeURIComponent 编码`() {
        val query = AppSigner.signedQuery(
            params = mapOf(
                "keyword" to "我的世界 苦力怕",
                "type" to "10",
                "pn" to "1",
                "ps" to "20",
            ),
            credential = hd,
            ts = ts,
            commonParams = hdCommon,
        )

        assertTrue("空格应编码为 %20", query.contains("%E7%95%8C%20%E8%8B%A6"))
        assertTrue("不应出现加号", !query.contains("+"))
        assertEquals(
            "appkey=dfca71928277209b&build=2001100&c_locale=zh_CN&channel=master" +
                "&disable_rcmd=0" +
                "&keyword=%E6%88%91%E7%9A%84%E4%B8%96%E7%95%8C%20%E8%8B%A6%E5%8A%9B%E6%80%95" +
                "&mobi_app=android_hd&platform=android&pn=1&ps=20&s_locale=zh_CN" +
                "&ts=1700000000&type=10" +
                "&sign=6c34d495096c2190dab6ec8e71a4309b",
            query,
        )
    }

    /** 空值写成 `key` 而不是 `key=`，否则签名与服务端算的不一致 */
    @Test
    fun `空值参数只写 key 不带等号`() {
        val query = AppSigner.signedQuery(
            params = mapOf("cid" to "1", "empty" to ""),
            credential = hd,
            ts = ts,
            commonParams = hdCommon,
        )

        assertTrue("空值应只写 key", query.contains("&empty&"))
        assertEquals(
            "appkey=dfca71928277209b&build=2001100&c_locale=zh_CN&channel=master&cid=1" +
                "&disable_rcmd=0&empty&mobi_app=android_hd&platform=android&s_locale=zh_CN" +
                "&ts=1700000000" +
                "&sign=dcd7979225a60ff871eb246f3922aa7e",
            query,
        )
    }

    @Test
    fun `encodeURIComponent 不转义的符号保持原样`() {
        val query = AppSigner.signedQuery(
            params = mapOf("v" to "a-b_c.d!e~f*g'h(i)"),
            credential = hd,
            ts = ts,
            commonParams = emptyMap(),
        )

        assertTrue("这些符号不应被转义: $query", query.contains("v=a-b_c.d!e~f*g'h(i)"))
    }

    @Test
    fun `百分号编码使用大写十六进制`() {
        val query = AppSigner.signedQuery(
            params = mapOf("v" to "中"),
            credential = hd,
            ts = ts,
            commonParams = emptyMap(),
        )

        assertTrue("应为大写 %E4%B8%AD: $query", query.contains("v=%E4%B8%AD"))
    }

    @Test
    fun `参数按 key 升序排列且 sign 在最后`() {
        val query = AppSigner.signedQuery(mapOf("zzz" to "1", "aaa" to "2"), ts = ts)
        val keys = query.split("&").map { it.substringBefore('=') }

        assertEquals("sign", keys.last())
        assertEquals(keys.dropLast(1).sorted(), keys.dropLast(1))
    }

    @Test
    fun `调用方传入的公共参数不会被默认值覆盖`() {
        val query = AppSigner.signedQuery(mapOf("mobi_app" to "android_hd"), ts = ts)

        assertTrue("应保留调用方指定的 mobi_app", query.contains("mobi_app=android_hd"))
        assertTrue("不应同时出现默认值", !query.contains("mobi_app=android&"))
    }

    @Test
    fun `重复签名不会把旧的 sign 算进去`() {
        val once = AppSigner.signedQuery(mapOf("cid" to "1"), ts = ts)
        val twice = AppSigner.signedQuery(
            mapOf("cid" to "1", "sign" to "deadbeef", "ts" to "1600000000"),
            ts = ts,
        )

        assertEquals(once, twice)
    }

    @Test
    fun `HD 与手机版使用不同的 appkey`() {
        assertEquals("dfca71928277209b", AppSigner.ANDROID_HD.appKey)
        assertEquals("1d8b6e7d45233436", AppSigner.ANDROID.appKey)
        assertTrue(
            "HD 端标识必须是 android_hd",
            AppSigner.HD_COMMON_PARAMS["mobi_app"] == "android_hd",
        )
    }
}
