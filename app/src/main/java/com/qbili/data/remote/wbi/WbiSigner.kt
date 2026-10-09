package com.qbili.data.remote.wbi

import okhttp3.HttpUrl
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.TreeMap

/**
 * B 站 Web 接口的 WBI 签名。
 *
 * 算法：
 * 1. 从 `/x/web-interface/nav` 的 `data.wbi_img.img_url`、`sub_url` 文件名取 img_key、sub_key；
 * 2. `imgKey + subKey` 按 [MIXIN_KEY_ENC_TAB] 索引表重排，取前 32 位得到 mixin_key；
 * 3. 请求参数值过滤掉 `!'()*` 字符，加入当前秒级时间戳 `wts`，按 key 升序排序后
 *    URL-encode 拼成 query；
 * 4. `w_rid = md5(query + mixin_key)`，追加到 query 末尾（w_rid 本身不参与签名）。
 *
 * 注意：签名用的 query 字符串必须与真正发出去的 query **逐字节一致**，
 * 所以这里直接产出最终 encodedQuery，由拦截器整体替换，避免 OkHttp 二次编码导致签名不匹配。
 */
object WbiSigner {

    private val MIXIN_KEY_ENC_TAB = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40, 61,
        26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36,
        20, 34, 44, 52,
    )

    private const val FORBIDDEN_CHARS = "!'()*"

    /** imgKey + subKey 重排取前 32 位 */
    fun mixinKey(imgKey: String, subKey: String): String {
        val raw = imgKey + subKey
        if (raw.length < 64) return raw.take(32)
        val sb = StringBuilder(32)
        for (i in 0 until 32) sb.append(raw[MIXIN_KEY_ENC_TAB[i]])
        return sb.toString()
    }

    /**
     * 对整个 URL 签名：读出现有 query，签名后整体替换 encodedQuery。
     *
     * 抽成纯函数是为了能在 JVM 单测里验证「签名后 URL 里的参数值仍与原值逐字节一致」，
     * 尤其是含空格、中文、加号这类容易被二次编码的关键词。
     */
    fun signUrl(
        url: HttpUrl,
        mixinKey: String,
        wts: Long = System.currentTimeMillis() / 1000,
    ): HttpUrl {
        val params = LinkedHashMap<String, String>()
        for (name in url.queryParameterNames) {
            params[name] = url.queryParameter(name).orEmpty()
        }
        return url.newBuilder().encodedQuery(signedQuery(params, mixinKey, wts)).build()
    }

    /**
     * @return 完整的 encodedQuery，形如 `a=1&b=2&wts=169...&w_rid=abc...`
     */
    fun signedQuery(
        params: Map<String, String>,
        mixinKey: String,
        wts: Long = System.currentTimeMillis() / 1000,
    ): String {
        val sorted = TreeMap<String, String>()
        for ((key, value) in params) {
            // 去掉可能残留的旧签名参数，避免重复签名
            if (key == "w_rid" || key == "wts") continue
            sorted[key] = value.filterNot { it in FORBIDDEN_CHARS }
        }
        sorted["wts"] = wts.toString()

        val query = sorted.entries.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }
        return "$query&w_rid=${md5(query + mixinKey)}"
    }

    /**
     * 与 Python 参考实现的 `urllib.parse.urlencode`（quote_plus）保持一致：
     * 空格编码为 `+`，`~` 保持原样。
     */
    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("%7E", "~")

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(32)
        for (b in digest) sb.append(HEX[(b.toInt() shr 4) and 0xF]).append(HEX[b.toInt() and 0xF])
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
