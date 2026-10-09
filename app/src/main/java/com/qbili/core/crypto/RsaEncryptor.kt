package com.qbili.core.crypto

import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

/**
 * 密码登录用的 RSA 加密。
 *
 * `/x/passport-login/web/key` 返回一对 (hash, key)：
 * - key 是 PEM 格式的 RSA 公钥
 * - hash 是本次登录的盐，必须拼在密码前面一起加密，防止密文重放
 *
 * 加密方式为 RSA/ECB/PKCS1Padding，结果 Base64（不换行）。
 */
object RsaEncryptor {

    fun encryptPassword(salt: String, password: String, pemPublicKey: String): String =
        encrypt(salt + password, pemPublicKey)

    fun encrypt(plain: String, pemPublicKey: String): String {
        val der = Base64.getDecoder().decode(stripPem(pemPublicKey))
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return Base64.getEncoder().encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    /** 去掉 PEM 头尾与所有空白，得到纯 base64 的 DER 内容 */
    internal fun stripPem(pem: String): String = pem
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replace("-----BEGIN RSA PUBLIC KEY-----", "")
        .replace("-----END RSA PUBLIC KEY-----", "")
        .filterNot { it.isWhitespace() }
}
