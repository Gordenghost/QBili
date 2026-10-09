package com.qbili.core.crypto

import java.net.URLEncoder
import java.security.MessageDigest
import java.util.TreeMap

/**
 * B 站移动端 API 的 appkey + sign 签名。
 *
 * 为什么需要它：本项目是一个安卓 App，但之前一直伪装成桌面 Chrome 去调 Web 接口。
 * 这个身份错位会带来一串问题——短信登录被要求「请在官方 App 内完成」（86103）、
 * 多词搜索被 Gaia 风控拦下。移动端接口本来就是给 App 用的，走它更顺，
 * 也不需要极验。开源第三方客户端（PiliPala / PiliPlus / BiliMiao 等）都是这么做的。
 *
 * 算法：参数加入 appkey 与 ts 后按 key 升序排序，URL-encode 拼成 query，
 * 再对 `query + secret` 取 MD5 作为 sign 追加到末尾（sign 本身不参与签名）。
 */
object AppSigner {

    /**
     * 公开文档化的客户端标识，用于让服务端识别调用方是移动端。
     * 它不是凭据，也不参与用户身份认证——登录仍然要用户自己的验证码。
     */
    data class Credential(val appKey: String, val secret: String)

    val ANDROID = Credential("1d8b6e7d45233436", "560c52ccd288fed045859ed18bffd973")

    /**
     * HD 版（android_hd）客户端标识。实测短信登录必须用它：
     * 同样的流程用 [ANDROID] 身份时接口返回成功、服务端也计入发送冷却，
     * 但短信会被风控静默丢弃（不投递）；换成 HD 身份后正常下发。
     * 参考实现：PiliPlus / PiliPalaX 的 sendSmsCode。
     */
    val ANDROID_HD = Credential("dfca71928277209b", "b5475a8825547a4fc26c7d518eaaa02e")

    /** 移动端接口对 UA 也有校验，必须是 BiliDroid 形态 */
    const val USER_AGENT = "Mozilla/5.0 BiliDroid/7.38.0 (bbcallen@gmail.com) os/android"

    /** 与 [ANDROID_HD] 配套的 UA，逐字段对齐真实 HD 客户端 */
    const val USER_AGENT_HD =
        "Mozilla/5.0 BiliDroid/2.0.1 (bbcallen@gmail.com) os/android " +
            "model/android_hd mobi_app/android_hd build/2001100 channel/master " +
            "innerVer/2001100 osVer/15 network/2"

    /** 每个请求都要带的固定端标识参数 */
    val COMMON_PARAMS: Map<String, String> = mapOf(
        "mobi_app" to "android",
        "platform" to "android",
        "device" to "phone",
        "build" to "7380300",
        "channel" to "bili",
    )

    /** 与 [ANDROID_HD] 配套的端标识。注意不带 device 参数，与参考实现一致 */
    val HD_COMMON_PARAMS: Map<String, String> = mapOf(
        "mobi_app" to "android_hd",
        "platform" to "android",
        "build" to "2001100",
        "channel" to "master",
        "c_locale" to "zh_CN",
        "s_locale" to "zh_CN",
        "disable_rcmd" to "0",
    )

    /**
     * @return 完整的已签名 query（或表单体），形如 `a=1&appkey=..&ts=..&sign=..`
     */
    fun signedQuery(
        params: Map<String, String>,
        credential: Credential = ANDROID,
        ts: Long = System.currentTimeMillis() / 1000,
        commonParams: Map<String, String> = COMMON_PARAMS,
    ): String {
        val sorted = TreeMap<String, String>()
        // 去掉可能残留的旧签名字段，避免重复签名时把它们也算进去
        params.forEach { (key, value) -> if (key != "sign") sorted[key] = value }
        commonParams.forEach { (key, value) -> sorted.putIfAbsent(key, value) }
        sorted["appkey"] = credential.appKey
        sorted["ts"] = ts.toString()

        val query = sorted.entries.joinToString("&") { (key, value) ->
            // 空值只写 key、不带等号——与参考实现一致。写成 `key=` 会算出不同的签名
            if (value.isEmpty()) encode(key) else "${encode(key)}=${encode(value)}"
        }
        return "$query&sign=${md5(query + credential.secret)}"
    }

    /**
     * 与 JS/Dart 的 `encodeURIComponent` 逐字节一致。
     *
     * 不能用 `URLEncoder.encode`：它把空格编码成 `+` 而不是 `%20`，
     * 还会转义 `!~'()`。签名算的是编码后的字符串，差一个字符整个 sign 就废了，
     * 而服务端只会回一个笼统的错误码，极难定位。
     */
    private fun encode(value: String): String {
        val sb = StringBuilder(value.length)
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in UNRESERVED) {
                sb.append(char)
            } else {
                sb.append('%')
                    .append(UPPER_HEX[(code shr 4) and 0xF])
                    .append(UPPER_HEX[code and 0xF])
            }
        }
        return sb.toString()
    }

    /** encodeURIComponent 不转义的符号集合 */
    private const val UNRESERVED = "-_.!~*'()"

    private val UPPER_HEX = "0123456789ABCDEF".toCharArray()

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(32)
        for (b in digest) sb.append(HEX[(b.toInt() shr 4) and 0xF]).append(HEX[b.toInt() and 0xF])
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
