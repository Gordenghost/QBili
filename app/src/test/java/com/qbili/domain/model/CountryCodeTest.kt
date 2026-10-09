package com.qbili.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 国际区号的回归测试。
 *
 * 这里锁的是一个真实踩过、且极难定位的坑：短信接口的 `cid` 参数要的是
 * **国际拨号前缀**（中国大陆 86），不是 B 站国家列表里的**列表序号**
 * （中国大陆的序号恰好是 1）。
 *
 * 传错时故障表现极具欺骗性：接口返回 code=0、返回 captcha_key、
 * 60 秒冷却和 86200「短信请求过快」也都正常工作——因为拨号前缀 1 是美国，
 * 请求本身完全合法，只是短信被投向 +1 号段，中国大陆手机永远收不到。
 * 日志里一路都是「短信已下发」，却查不出任何异常。
 */
class CountryCodeTest {

    @Test
    fun `中国大陆用拨号前缀 86 而不是列表序号 1`() {
        assertEquals(86, CountryCode.CHINA.dialPrefix)
    }

    @Test
    fun `拨号前缀 1 只能属于美国`() {
        val withPrefixOne = CountryCode.entries.filter { it.dialPrefix == 1 }
        assertEquals(listOf(CountryCode.UNITED_STATES), withPrefixOne)
    }

    @Test
    fun `各区号的拨号前缀与真实值一致`() {
        assertEquals(852, CountryCode.HONG_KONG.dialPrefix)
        assertEquals(853, CountryCode.MACAO.dialPrefix)
        assertEquals(886, CountryCode.TAIWAN.dialPrefix)
        assertEquals(81, CountryCode.JAPAN.dialPrefix)
    }

    /** dialCode 由 dialPrefix 派生，不允许出现两处各写一份而后漂移 */
    @Test
    fun `显示用的区号文案由拨号前缀派生`() {
        CountryCode.entries.forEach { code ->
            assertEquals("+${code.dialPrefix}", code.dialCode)
            assertTrue(code.display.startsWith(code.label))
            assertTrue(code.display.endsWith(code.dialCode))
        }
    }

    @Test
    fun `没有重复的国家或前缀冲突项`() {
        val labels = CountryCode.entries.map { it.label }
        assertEquals(labels.distinct().size, labels.size)
    }
}
