package com.qbili.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.util.Base64
import javax.crypto.Cipher

/**
 * 密码登录的 RSA 加密无法用固定密文断言（PKCS#1 v1.5 每次填充都带随机数），
 * 所以这里在测试内自己生成一对密钥，加密后用私钥解回来比对明文。
 * 这样能真正验证「盐拼接顺序 + 填充方式 + Base64 编码」三件事都对。
 */
class RsaEncryptorTest {

    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair()

    private val pem: String = buildString {
        append("-----BEGIN PUBLIC KEY-----\n")
        append(
            Base64.getMimeEncoder(64, "\n".toByteArray())
                .encodeToString(keyPair.public.encoded),
        )
        append("\n-----END PUBLIC KEY-----\n")
    }

    private fun decrypt(base64Cipher: String): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, keyPair.private)
        return String(cipher.doFinal(Base64.getDecoder().decode(base64Cipher)), Charsets.UTF_8)
    }

    @Test
    fun `encryptPassword 把盐拼在密码前面一起加密`() {
        val salt = "47a7e1f67d0c71a6"
        val password = "hunter2!@#"

        val encrypted = RsaEncryptor.encryptPassword(salt, password, pem)

        assertEquals(salt + password, decrypt(encrypted))
    }

    @Test
    fun `密文是不换行的 Base64`() {
        val encrypted = RsaEncryptor.encryptPassword("abc", "def", pem)

        assertTrue("密文不应包含换行", !encrypted.contains('\n') && !encrypted.contains('\r'))
        // 1024 bit = 128 字节密文 -> Base64 后固定 172 字符
        assertEquals(172, encrypted.length)
    }

    @Test
    fun `中文与特殊字符密码能正确加解密`() {
        val salt = "0123456789abcdef"
        val password = "密码~!@#\$%^&*()_+测试"

        assertEquals(salt + password, decrypt(RsaEncryptor.encryptPassword(salt, password, pem)))
    }

    @Test
    fun `stripPem 去掉头尾与所有空白`() {
        val stripped = RsaEncryptor.stripPem(pem)

        assertTrue("不应残留 PEM 头", !stripped.contains("BEGIN"))
        assertTrue("不应残留 PEM 尾", !stripped.contains("END"))
        assertTrue("不应残留空白字符", stripped.none { it.isWhitespace() })
        // 去掉头尾后应当还能被 Base64 解回原始 DER
        assertTrue(Base64.getDecoder().decode(stripped).contentEquals(keyPair.public.encoded))
    }

    @Test
    fun `没有换行的单行 PEM 同样能解析`() {
        val singleLine = pem.replace("\n", "")

        assertEquals("x" + "y", decrypt(RsaEncryptor.encryptPassword("x", "y", singleLine)))
    }
}
