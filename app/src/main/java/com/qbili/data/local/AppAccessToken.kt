package com.qbili.data.local

class AppAccessToken(val value: String, val mid: Long? = null, val expiresAt: Long? = null) {
    fun isExpired(now: Long): Boolean = expiresAt?.let { it <= now } ?: false
}
