package com.qbili.data.remote

import java.util.Base64
import kotlin.random.Random

/**
 * 构造 buvid 激活（`ExClimbWuzhi`）用的浏览器指纹载荷。
 *
 * 这些魔法键名（3064 / 39c8 / 3c43 / adca / bfe9）是 B 站 Web 端指纹采集脚本
 * 混淆后的字段名，服务端只认这一套，不能改也不能省。
 *
 * `bfe9` 模拟的是「canvas 指纹」——真实网页会画一张图再取其 PNG 数据尾部；
 * 这里构造一段以 PNG 的 IEND 块结尾的随机字节，取 base64 后的最后 50 个字符，
 * 形态上与真实采集结果一致。
 */
object BuvidPayload {

    fun build(random: Random = Random.Default): String {
        val bytes = ByteArray(32) { random.nextInt(256).toByte() } +
            // 0,0,0,0 + "IEND"：PNG 数据流的结束块
            byteArrayOf(0, 0, 0, 0, 73, 69, 78, 68) +
            ByteArray(4) { random.nextInt(256).toByte() }

        val canvasTail = Base64.getEncoder().encodeToString(bytes).takeLast(CANVAS_TAIL_LENGTH)

        // base64 字符集不含引号或反斜杠，直接内插进 JSON 是安全的
        return """{"3064":1,"39c8":"333.1387.fp.risk","3c43":{"adca":"Linux","bfe9":"$canvasTail"}}"""
    }

    private const val CANVAS_TAIL_LENGTH = 50
}
