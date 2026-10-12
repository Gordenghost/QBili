package com.qbili.core

fun aidToBvid(aid: Long): String {
    val maxAid = 1L shl 51
    if (aid <= 0 || aid >= maxAid) return ""
    val alphabet = "FcwAPNKTMug3GV5Lj7EJnHpWsx4tb8haYeviqBz6rkCy12mUSDQX9RdoZf"
    val result = "BV1000000000".toCharArray()
    var position = result.lastIndex
    var value = (maxAid or aid) xor 23442827791579L
    while (value > 0) {
        result[position--] = alphabet[(value % 58).toInt()]
        value /= 58
    }
    val third = result[3]
    result[3] = result[9]
    result[9] = third
    val fourth = result[4]
    result[4] = result[7]
    result[7] = fourth
    return String(result)
}
