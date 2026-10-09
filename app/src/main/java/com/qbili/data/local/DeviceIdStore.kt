package com.qbili.data.local

import android.content.Context

/**
 * App 端接口用的设备 ID。
 *
 * 为什么不用 Cookie 里的 buvid3：两者格式不同。真实客户端在移动端接口里传的
 * buvid/local_id 是「XY」开头的 34 位十六进制（由随机字节串取 MD5 派生），
 * 而网页版 Cookie buvid3 是 UUID+infoc 形态。把网页形态塞进 App 签名请求，
 * 风控能直接看出流量不是真客户端产生——短信登录被静默丢弃的嫌疑点之一。
 *
 * 生成后持久化，保证同一台设备对外呈现稳定的设备指纹。
 */
class DeviceIdStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getOrCreate(): String =
        prefs.getString(KEY, null) ?: newDeviceId().also { id ->
            prefs.edit().putString(KEY, id).apply()
        }

    /**
     * buvid 是否已在 Gaia 风控系统登记过（ExClimbWuzhi）。
     * 每次安装只需成功激活一次，之后不必重复请求。
     */
    fun isBuvidActivated(): Boolean = prefs.getBoolean(KEY_ACTIVATED, false)

    fun markBuvidActivated() {
        prefs.edit().putBoolean(KEY_ACTIVATED, true).apply()
    }

    /**
     * 移动端登录用的设备 ID（`device_id` / `bili_local_id`）。
     *
     * 与 [getOrCreate] 的 buvid 不是一个东西：这个是 64 位十六进制，
     * 由「16 随机字节 + BCD 编码的当前时间 + 8 随机字节」再附加 1 字节校验和构成。
     */
    fun getOrCreateLoginDeviceId(): String =
        prefs.getString(KEY_LOGIN_DEVICE_ID, null) ?: newLoginDeviceId().also { id ->
            prefs.edit().putString(KEY_LOGIN_DEVICE_ID, id).apply()
        }

    companion object {
        private const val PREF_NAME = "qbili_device"
        private const val KEY = "app_buvid"
        private const val KEY_ACTIVATED = "buvid_activated"
        private const val KEY_LOGIN_DEVICE_ID = "login_device_id"

        /**
         * 与 PiliPlus LoginUtils.generateBuvid 一致：
         * 16 个随机字节取十六进制，结果为 `XY` + hex[2] + hex[12] + hex[22] + hex。
         * 共 35 字符。
         */
        fun newDeviceId(random: kotlin.random.Random = kotlin.random.Random.Default): String {
            val bytes = ByteArray(16) { random.nextInt(256).toByte() }
            val md5 = md5Hex(bytes)
            return "XY" + md5[2] + md5[12] + md5[22] + md5
        }

        /**
         * 与 PiliPlus LoginUtils.genDeviceId 一致：
         * 16 随机字节 + 年高位/年低位/月/日/时/分/秒的 BCD 编码 + 8 随机字节，
         * 取十六进制后附加「全部字节之和的低 8 位」作为 2 位校验和，共 64 字符。
         */
        fun newLoginDeviceId(
            random: kotlin.random.Random = kotlin.random.Random.Default,
            now: java.time.LocalDateTime = java.time.LocalDateTime.now(),
        ): String {
            val bytes = ArrayList<Int>(31)
            repeat(16) { bytes += random.nextInt(256) }
            bytes += dec2bcd(now.year / 100)
            bytes += dec2bcd(now.year % 100)
            bytes += dec2bcd(now.monthValue)
            bytes += dec2bcd(now.dayOfMonth)
            bytes += dec2bcd(now.hour)
            bytes += dec2bcd(now.minute)
            bytes += dec2bcd(now.second)
            repeat(8) { bytes += random.nextInt(256) }

            val hex = bytes.joinToString("") { "%02x".format(it and 0xFF) }
            val checksum = "%02x".format(bytes.sum() and 0xFF)
            return hex + checksum
        }

        /** 十进制转 BCD：42 -> 0x42 */
        private fun dec2bcd(value: Int): Int {
            require(value in 0..99) { "BCD 只支持 0..99，收到 $value" }
            return ((value / 10) shl 4) or (value % 10)
        }

        /** login_session_id 的官方派生方式也依赖它：md5(buvid + 毫秒时间戳) */
        fun md5Hex(input: String): String = md5Hex(input.toByteArray(Charsets.UTF_8))

        fun md5Hex(bytes: ByteArray): String {
            val digest = java.security.MessageDigest.getInstance("MD5").digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
