package com.qbili.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoIdsTest {
    @Test
    fun `已知AV转换为标准BV`() {
        assertEquals("BV17x411w7KC", aidToBvid(170001))
        assertEquals("BV1Q541167Qg", aidToBvid(455017605))
    }

    @Test
    fun `新增长AV正确保留全部位数`() {
        val aid = 117364108629613L
        val bvid = aidToBvid(aid)
        assertTrue(bvid.matches(Regex("BV1[0-9a-zA-Z]{9}")))
        val characters = bvid.toCharArray()
        val third = characters[3]
        characters[3] = characters[9]
        characters[9] = third
        val fourth = characters[4]
        characters[4] = characters[7]
        characters[7] = fourth
        val alphabet = "FcwAPNKTMug3GV5Lj7EJnHpWsx4tb8haYeviqBz6rkCy12mUSDQX9RdoZf"
        val value = characters.drop(3).fold(0L) { result, character -> result * 58 + alphabet.indexOf(character) }
        assertEquals(aid, (value and 2251799813685247L) xor 23442827791579L)
        assertEquals("", aidToBvid(0))
        assertEquals("", aidToBvid(-1))
        assertEquals("", aidToBvid(1L shl 51))
    }
}
