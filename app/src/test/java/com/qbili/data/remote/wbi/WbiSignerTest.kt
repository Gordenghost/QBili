package com.qbili.data.remote.wbi

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * WBI 签名算法的回归测试。
 *
 * 测试向量与社区文档（bilibili-API-collect）的 Python 参考实现逐字节对齐，
 * 已用 Python 实测确认。签名一旦算错，线上表现是所有接口统一返回 -412，
 * 极难从日志定位，所以这里必须锁死。
 */
class WbiSignerTest {

    private val imgKey = "7cd084941338484aae1ad9425b84077c"
    private val subKey = "4932caff0ff746eab6f01bf08b70ac45"

    @Test
    fun `mixinKey 按索引表重排后取前 32 位`() {
        assertEquals(
            "ea1db124af3c7062474693fa704f4ff8",
            WbiSigner.mixinKey(imgKey, subKey),
        )
    }

    @Test
    fun `signedQuery 参数按 key 升序排序并追加 wts 与 w_rid`() {
        val query = WbiSigner.signedQuery(
            params = mapOf("foo" to "114", "bar" to "514", "zab" to "1919810"),
            mixinKey = WbiSigner.mixinKey(imgKey, subKey),
            wts = 1702204169L,
        )

        assertEquals(
            "bar=514&foo=114&wts=1702204169&zab=1919810" +
                "&w_rid=8f6f2b5b3d485fe1886cec6a0be8c5d4",
            query,
        )
    }

    @Test
    fun `参数值中的特殊字符必须被过滤掉`() {
        val mixinKey = WbiSigner.mixinKey(imgKey, subKey)
        val dirty = WbiSigner.signedQuery(mapOf("keyword" to "a!b'c(d)e*f"), mixinKey, 1L)
        val clean = WbiSigner.signedQuery(mapOf("keyword" to "abcdef"), mixinKey, 1L)
        assertEquals(clean, dirty)
    }

    @Test
    fun `重复签名不会把旧的 wts 与 w_rid 带进签名串`() {
        val mixinKey = WbiSigner.mixinKey(imgKey, subKey)
        val once = WbiSigner.signedQuery(mapOf("mid" to "1"), mixinKey, 1702204169L)
        // 模拟拦截器把已签名的 query 再解析一遍后重新签名
        val twice = WbiSigner.signedQuery(
            mapOf("mid" to "1", "wts" to "1600000000", "w_rid" to "deadbeef"),
            mixinKey,
            1702204169L,
        )
        assertEquals(once, twice)
    }

    @Test
    fun `空格编码为加号 与 Python quote_plus 一致`() {
        val mixinKey = WbiSigner.mixinKey(imgKey, subKey)
        val query = WbiSigner.signedQuery(mapOf("keyword" to "原神 启动"), mixinKey, 1L)
        assertEquals(true, query.contains("keyword=%E5%8E%9F%E7%A5%9E+%E5%90%AF%E5%8A%A8"))
    }
}
